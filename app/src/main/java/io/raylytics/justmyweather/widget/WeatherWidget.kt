package io.raylytics.justmyweather.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.raylytics.justmyweather.AppContainer
import io.raylytics.justmyweather.JustMyWeatherApp
import io.raylytics.justmyweather.ui.theme.LocalConventions
import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ViewConfig
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * The home screen widget: one tile of the glance, drawn by the launcher.
 *
 * Glance calls [provideGlance] whenever the widget needs drawing — placed,
 * resized, after a refresh, after a reboot — so it reads everything from the
 * stores and never fetches: the fetch is [WidgetRefreshWorker]'s, and what it
 * stored is what is drawn. [SizeMode.Exact] hands the composition the
 * widget's real size, which is what the text fit and the forecast's column
 * count are worked out from.
 *
 * A widget with no saved config (the launcher placed it without the
 * configure step — the Pixel launcher does, offering a pencil instead — or
 * its config was lost) seeds itself from the glance and saves that, so it
 * draws something true on its first frame and the configure screen later
 * opens on what it is showing.
 */
class WeatherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as JustMyWeatherApp).container
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        // A first draw (or one after a lost state file) mirrors the stores
        // into the widget's state; every later change is pushed by whoever
        // makes it. Read inside the composition, never here — see WidgetState.
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        if (state[WidgetState.CONFIG] == null || state[WidgetState.DATA] == null) {
            WidgetState.push(
                context,
                id,
                config = container.widgetConfigRepository.get(widgetId) ?: container.seedWidgetConfig(widgetId),
                data = container.widgetDataStore.get(),
            )
        }
        provideContent {
            val prefs = currentState<Preferences>()
            val config =
                WidgetConfigCodec.decode(prefs[WidgetState.CONFIG])
                    ?: WidgetConfig(ViewConfig.DEFAULT.showingOnly(WidgetConfig.DEFAULT_MODULE), ThemeConfig.DEFAULT)
            val data = WidgetDataCodec.decode(prefs[WidgetState.DATA])
            // The widget reads in the conventions its last fetch stored —
            // the region's units, dates and clock (WidgetRefreshWorker).
            CompositionLocalProvider(LocalConventions provides (data?.conventions ?: Conventions.US)) {
                WidgetContent(config = config, data = data, now = Instant.now())
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val container = (context.applicationContext as JustMyWeatherApp).container
        container.widgetConfigRepository.delete(listOf(GlanceAppWidgetManager(context).getAppWidgetId(glanceId)))
    }
}

/** A fresh config from the glance's current settings, saved so the widget
 * and its configure screen agree on it from here on. */
internal suspend fun AppContainer.seedWidgetConfig(widgetId: Int): WidgetConfig =
    WidgetConfig
        .seededFrom(viewConfigRepository.config.first(), themeConfigRepository.config.first())
        .also { widgetConfigRepository.save(widgetId, it) }

/**
 * The launcher's side of the widget's life. Glance handles the drawing; this
 * keeps the refresh in step with whether any widget exists, and asks for an
 * immediate fetch when one appears so it is not blank until the next tick.
 */
class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.sync(context, hasWidgets = true)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefreshWorker.runOnce(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.sync(context, hasWidgets = false)
    }
}
