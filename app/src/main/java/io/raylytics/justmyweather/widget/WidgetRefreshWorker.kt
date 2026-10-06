package io.raylytics.justmyweather.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.raylytics.justmyweather.JustMyWeatherApp
import io.raylytics.justmyweather.ui.home.weatherErrorMessage
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * The widgets' refresh: fetch what the widgets on the phone need, store it,
 * redraw them. One fetch for every widget, because they all show the one
 * place the app shows. Scheduled only while a widget exists, so a phone with
 * none does no background work for them.
 *
 * Every [REFRESH_MINUTES] minutes — WorkManager's floor for periodic work.
 * The platform's own widget timer (`updatePeriodMillis`) bottoms out at
 * thirty, so the worker is what drives updates and the provider XML sets
 * that timer to zero. Evan (2026-10-05) asked for the shortest cadence on
 * offer; the fetch is two or three small requests to a free API, and a
 * reading a quarter-hour old is as current as the station is.
 *
 * I/O shell only: what to fetch is [WidgetNeeds], what to draw is
 * [WidgetContent], both pure.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as JustMyWeatherApp).container
        // The launcher is the authority on which widgets exist, not the
        // config store: a widget placed a moment ago has its id before its
        // first draw has seeded a config (verified: the one-off refresh ran
        // 250 ms after placement, read no configs, and would have cancelled
        // the schedule it had just been given). Each id without a config is
        // seeded here, as the draw would seed it.
        val manager = GlanceAppWidgetManager(applicationContext)
        val glanceIds = manager.getGlanceIds(WeatherWidget::class.java)
        val ids = glanceIds.map(manager::getAppWidgetId)
        if (ids.isEmpty()) {
            // The receiver cancels this work when the last widget goes; this
            // is the safety net for a cancel that never ran (a crash between
            // the two, a restored backup).
            cancel(applicationContext)
            return Result.success()
        }
        val stored = container.widgetConfigRepository.all()
        val configs = ids.map { id -> stored[id] ?: container.seedWidgetConfig(id) }
        // Only a config THIS run seeded is pushed into its widget's state
        // below. The others are the configure screen's to push: it can save
        // a new one while this fetch is in flight, and a push of the config
        // read before the fetch would overwrite it with the old module until
        // the next tick (roborev 5372).
        val seeded = ids.filter { it !in stored }.toSet()

        val repository = container.weatherRepository
        val location = container.locationResolver.resolve()
        // The previous data is only worth keeping if it is about THIS place:
        // after a place change the old reading must not outlive the fetch
        // that failed to replace it (roborev 5371).
        val previous = container.widgetDataStore.get()?.takeIf { it.isAbout(location) }
        val needs = WidgetNeeds.of(configs)

        // The reading is the one fetch that can fail the whole tick. A failed
        // fetch keeps the previous data standing with the failure beside it,
        // exactly as the glance keeps its last reading — and when there is no
        // previous data, the app's own remembered reading stands in, so a
        // widget placed offline still shows a number.
        val reading = runCatching { repository.load(location, remember = false) }
        val snapshot =
            reading.getOrNull()
                ?: previous?.snapshot
                ?: runCatching { repository.lastReading(location) }.getOrNull()
        val error = reading.exceptionOrNull()?.let(::weatherErrorMessage)

        // The forecasts are best-effort, each independently: a failed hourly
        // fetch leaves the previous hours in place and does not cost the
        // reading. Fetched only when some widget needs them.
        //
        // A framing nobody needs is CARRIED while it is fresh, not dropped:
        // this tick's needs were read before its fetch, and a widget
        // reconfigured meanwhile may have just had its hours fetched by the
        // one-off refresh that reconfiguration asks for. Writing null would
        // erase them until the next tick (roborev 5376). The carry is bounded
        // by CARRY_WINDOW: that race is over within a tick or two, and an
        // unbounded carry would hand a widget switched back to Hourly a
        // week-old forecast as current (roborev 5377). Same place as this
        // tick, too — `previous` is gated on that above.
        val now = Instant.now()
        val carried = previous?.takeIf { it.isForecastFresh(now, CARRY_WINDOW) }
        val hours =
            if (needs.hours) fetchOr(previous?.hours) { repository.loadForecast(location) } else carried?.hours
        val periods =
            if (needs.periods) {
                fetchOr(previous?.periods) { repository.loadDailyForecast(location) }
            } else {
                carried?.periods
            }
        val extended =
            if (needs.extended) {
                fetchOr(previous?.extended) { repository.loadExtendedDaily(location) }
            } else {
                carried?.extended
            }
        // The forecast's own clock moves only on a fetch that landed; carried
        // or fallen-back lists keep the time they were really fetched at.
        val fetchedForecast =
            (needs.hours && hours !== previous?.hours) ||
                (needs.periods && periods !== previous?.periods) ||
                (needs.extended && extended !== previous?.extended)
        val forecastFetchedAt =
            when {
                fetchedForecast -> now
                hours != null || periods != null || extended != null -> previous?.forecastFetchedAt
                else -> null
            }

        val data =
            WidgetData(
                location = location,
                snapshot = snapshot,
                hours = hours,
                periods = periods,
                extended = extended,
                fetchedAt = if (reading.isSuccess) now else previous?.fetchedAt ?: now,
                error = error,
                forecastFetchedAt = forecastFetchedAt,
            )
        container.widgetDataStore.put(data)
        // Into each widget's own state, then the draw: the composition reads
        // the state, not the stores (WidgetState).
        ids.forEachIndexed { index, id ->
            val config = configs[index].takeIf { id in seeded }
            WidgetState.push(applicationContext, glanceIds[index], config, data)
        }
        WeatherWidget().updateAll(applicationContext)
        // A transient failure retries on WorkManager's backoff, a couple of
        // times; past that — and for anything else — it waits for the next
        // tick rather than hammering a dead network or a broken endpoint.
        val transient = reading.exceptionOrNull() is java.io.IOException
        return if (transient && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    private suspend fun <T> fetchOr(fallback: T?, fetch: suspend () -> T): T? =
        runCatching { fetch() }.getOrNull() ?: fallback

    companion object {
        /** The cadence, in minutes. WorkManager refuses anything shorter. */
        const val REFRESH_MINUTES = 15L

        private const val UNIQUE_NAME = "widget-refresh"
        private const val ONCE_NAME = "widget-refresh-once"

        /** Schedule the periodic refresh while any widget exists, cancel it
         * when none does. */
        fun sync(context: Context, hasWidgets: Boolean) {
            if (hasWidgets) schedule(context) else cancel(context)
        }

        /** How many times a tick retries a dropped connection before it
         * lets the next tick have a go. */
        private const val MAX_RETRIES = 2

        /** How long an unneeded forecast is carried before it is dropped:
         * two ticks, which outlasts the reconfiguration race it is kept for. */
        private val CARRY_WINDOW: Duration = Duration.ofMinutes(REFRESH_MINUTES * 2)

        /**
         * KEEP, not UPDATE: the cadence is a constant, and re-enqueueing on
         * every widget placement would reset the timer each time.
         *
         * NO network constraint. The tick is also what redraws the widgets,
         * and the "Observed … · 12 min ago" age is read at the draw: a tick
         * withheld for lack of network would freeze the age at the moment the
         * phone went offline — understating staleness exactly when the data
         * is stalest (roborev 5371). Offline, the fetch fails in a
         * millisecond, the old data stands, and the age moves on.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_MINUTES, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /**
         * A single immediate refresh — when a widget is placed or
         * reconfigured, so it shows weather within seconds rather than at
         * the next tick. Networked, because this one exists to fetch.
         *
         * Unique + KEEP coalesces a burst of placements into one fetch. A
         * reconfiguration asks for [afterCurrent]: a fetch already running
         * read the OLD configs and will not ask for what the new module
         * needs, so its request is queued behind that run rather than
         * dropped (roborev 5372).
         */
        fun runOnce(context: Context, afterCurrent: Boolean = false) {
            val request =
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            val policy = if (afterCurrent) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE_NAME, policy, request)
        }
    }
}
