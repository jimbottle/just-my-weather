package io.raylytics.justmyweather.alerts

import android.content.Context
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
import io.raylytics.justmyweather.data.WeatherLocation
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The background half of alerting: on each tick, evaluate every enabled rule
 * against fresh weather and notify only the rules that just *entered* their
 * fired state (transition dedup via the persisted firing set). A quiet install
 * has no rules, so this returns immediately — nothing nags.
 *
 * Pure decisions live in [AlertEvaluator]; this worker is only the I/O shell
 * that fetches, calls the evaluator, and dispatches notifications.
 */
class AlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as JustMyWeatherApp).container
        val repository = container.alertRulesRepository

        val rules = repository.rules.first()
        if (rules.none { it.enabled }) {
            repository.setFiringIds(emptySet())
            return Result.success()
        }

        val location = container.locationProvider.lastKnownLocation() ?: WeatherLocation.DEFAULT
        val snapshot =
            try {
                container.weatherRepository.load(location)
            } catch (_: IOException) {
                return Result.retry() // transient network — try again next backoff
            } catch (_: Exception) {
                return Result.success() // don't hammer on a non-transient failure
            }

        // The forecast is only fetched when a rule actually needs it, and is
        // best-effort: if it fails, current-conditions rules still evaluate and
        // forecast-window rules simply hold until the next tick.
        val forecast =
            if (rules.any { it.enabled && it.window.isForecast }) {
                runCatching { container.weatherRepository.loadForecast(location) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

        // Pure transition-dedup: notify only rules that just entered fired. The
        // clock and zone are read here at the edge and handed to the pure path.
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val context = WeatherContext(snapshot, now, forecast, zone)
        val outcome = AlertTransitions.compute(rules, context, repository.firingIds())
        // Quiet hours hush the delivery (silent channel), they don't drop alerts.
        val silent = container.alertSettingsRepository.current().isQuietAt(now.atZone(zone).hour)
        outcome.toNotify.forEach { container.alertNotifier.notify(it.rule, it.decision, silent) }
        repository.setFiringIds(outcome.nowFiring)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "personal-alerts"
        private const val ONCE_NAME = "personal-alerts-once"

        /**
         * Schedule or cancel the hourly check to match whether any rule is live.
         * Called on launch and after every rule edit, so a quiet install (no
         * enabled rules) does zero background work rather than waking hourly only
         * to no-op. The worker still self-guards as a safety net.
         */
        fun sync(context: Context, hasEnabledRules: Boolean) {
            if (hasEnabledRules) schedule(context) else cancel(context)
        }

        /** Idempotent: KEEP means re-scheduling on each launch doesn't reset the
         * timer. */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<AlertWorker>(1, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Stop the hourly check — when the last enabled rule is removed or
         * disabled, so background work tracks the user actually wanting it. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /** A single immediate check — on app launch and after a rule is added or
         * enabled — so a freshly-added rule gives timely feedback instead of
         * waiting for the next hourly tick. Unique + KEEP means a burst of
         * changes coalesces into one run (which reads the latest rules) and the
         * one-time check serialises against itself, avoiding the duplicate-fetch
         * and double-notify race that concurrent runs would risk. Transition
         * dedup still applies, so an already-firing condition won't re-notify. */
        fun runOnce(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<AlertWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
