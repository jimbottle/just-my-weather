package io.raylytics.justmyweather.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.raylytics.justmyweather.JustMyWeatherApp
import io.raylytics.justmyweather.data.WeatherLocation
import kotlinx.coroutines.flow.first
import java.io.IOException
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

        val rules = repository.rules.first().filter { it.enabled }
        if (rules.isEmpty()) {
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

        val previouslyFiring = repository.firingIds()
        val nowFiring = mutableSetOf<String>()
        rules.forEach { rule ->
            val decision = AlertEvaluator.evaluate(rule, snapshot)
            if (decision.fired) {
                nowFiring += rule.id
                if (rule.id !in previouslyFiring) {
                    container.alertNotifier.notify(rule, decision)
                }
            }
        }
        repository.setFiringIds(nowFiring)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "personal-alerts"

        /** Idempotent: KEEP means re-scheduling on each launch doesn't reset the
         * timer. The worker self-guards when there are no rules, so it's safe to
         * always have it scheduled. */
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

        /** A single immediate check, enqueued on app launch so a rule the user
         * just added gives timely feedback instead of waiting for the next hour.
         * Transition dedup still applies, so an already-firing condition won't
         * re-notify. */
        fun runOnce(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<AlertWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
