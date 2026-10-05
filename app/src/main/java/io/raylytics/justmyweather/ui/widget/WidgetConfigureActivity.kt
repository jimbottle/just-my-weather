package io.raylytics.justmyweather.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.raylytics.justmyweather.JustMyWeatherApp
import io.raylytics.justmyweather.ui.theme.JustMyWeatherTheme
import io.raylytics.justmyweather.widget.WeatherWidget
import io.raylytics.justmyweather.widget.WidgetConfig
import io.raylytics.justmyweather.widget.WidgetRefreshWorker
import io.raylytics.justmyweather.widget.WidgetState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The launcher opens this when a widget is placed (and, on a long-press,
 * to reconfigure one). The contract is the platform's: the result starts
 * CANCELED so a back-press leaves no widget behind, and Done saves the
 * config, draws the widget once, asks for a fetch, and reports OK.
 *
 * Themed in the widget's own look rather than the app's, so the chips show
 * what the widget will be.
 */
class WidgetConfigureActivity : ComponentActivity() {
    private val container by lazy { (application as JustMyWeatherApp).container }

    private val widgetId: Int by lazy {
        intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private val viewModel: WidgetConfigureViewModel by viewModels {
        viewModelFactory {
            initializer {
                WidgetConfigureViewModel(
                    widgetId = widgetId,
                    repository = container.widgetConfigRepository,
                    seed = {
                        WidgetConfig.seededFrom(
                            container.viewConfigRepository.config.first(),
                            container.themeConfigRepository.config.first(),
                        )
                    },
                )
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED, resultIntent())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            val config by viewModel.config.collectAsStateWithLifecycle()
            val current = config ?: return@setContent
            JustMyWeatherTheme(current.theme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding()
                        .semantics { testTagsAsResourceId = true },
                ) {
                    WidgetConfigureScreen(
                        config = current,
                        onShow = viewModel::show,
                        onRelabel = viewModel::relabel,
                        onEditView = viewModel::editView,
                        onThemeChange = viewModel::setTheme,
                        onDone = ::done,
                        onCancel = ::finish,
                    )
                }
            }
        }
    }

    private fun done() {
        lifecycleScope.launch {
            val saved = viewModel.save() ?: return@launch finish()
            val manager = GlanceAppWidgetManager(this@WidgetConfigureActivity)
            // Push the new config into the widget's own state, draw it now
            // from the stored data (or its placeholder), then fetch so the
            // first real reading lands within seconds.
            runCatching {
                val id = manager.getGlanceIdBy(widgetId)
                WidgetState.push(this@WidgetConfigureActivity, id, config = saved)
                WeatherWidget().update(this@WidgetConfigureActivity, id)
            }
            WidgetRefreshWorker.sync(this@WidgetConfigureActivity, hasWidgets = true)
            WidgetRefreshWorker.runOnce(this@WidgetConfigureActivity)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
