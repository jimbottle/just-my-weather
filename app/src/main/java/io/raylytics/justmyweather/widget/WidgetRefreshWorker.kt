package io.raylytics.justmyweather.widget

import android.content.Context
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
        val configs = container.widgetConfigRepository.all().values
        if (configs.isEmpty()) {
            // Nothing to draw for. The receiver cancels this work when the
            // last widget goes; this is the safety net for a cancel that
            // never ran (a crash between the two, a restored backup).
            cancel(applicationContext)
            return Result.success()
        }

        val repository = container.weatherRepository
        val location = container.locationResolver.resolve()
        val previous = container.widgetDataStore.get()
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
        val hours = if (needs.hours) fetchOr(previous?.hours) { repository.loadForecast(location) } else null
        val periods = if (needs.periods) fetchOr(previous?.periods) { repository.loadDailyForecast(location) } else null
        val extended =
            if (needs.extended) fetchOr(previous?.extended) { repository.loadExtendedDaily(location) } else null

        container.widgetDataStore.put(
            WidgetData(
                location = location,
                snapshot = snapshot,
                hours = hours,
                periods = periods,
                extended = extended,
                fetchedAt = if (reading.isSuccess) Instant.now() else previous?.fetchedAt ?: Instant.now(),
                error = error,
            ),
        )
        WeatherWidget().updateAll(applicationContext)
        // A transient failure retries on WorkManager's backoff; anything else
        // waits for the next tick rather than hammering a broken endpoint.
        return if (reading.exceptionOrNull() is java.io.IOException) Result.retry() else Result.success()
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

        /** KEEP, not UPDATE: the cadence is a constant, and re-enqueueing on
         * every widget placement would reset the timer each time. */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /** One immediate refresh — when a widget is placed or reconfigured,
         * so it shows weather within seconds rather than at the next tick.
         * Unique + KEEP coalesces a burst of placements into one fetch. */
        fun runOnce(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
