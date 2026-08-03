package io.raylytics.justmyweather.ui.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.raylytics.justmyweather.alerts.AlertRule
import io.raylytics.justmyweather.alerts.AlertSubject
import io.raylytics.justmyweather.alerts.Comparison
import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.data.AlertSettingsRepository
import io.raylytics.justmyweather.view.WeatherField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Locks in the scheduling-callback contract the background worker depends on:
 * every edit reports the new rule list (so the worker is scheduled/cancelled to
 * match), and only an *activating* change kicks the immediate check.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** Minimal DataStore<Preferences> so a real AlertRulesRepository runs on the JVM. */
    private class FakePreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val flow = MutableStateFlow(initial)
        override val data: Flow<Preferences> = flow

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(flow.value).also { flow.value = it }
    }

    private val temp = AlertSubject.Field(WeatherField.TEMPERATURE)

    @Test
    fun `adding a rule reports one enabled rule and triggers an immediate check`() = runTest(dispatcher) {
        val changes = mutableListOf<Boolean>()
        var checks = 0
        val vm =
            AlertsViewModel(
                AlertRulesRepository(FakePreferencesDataStore()),
                AlertSettingsRepository(FakePreferencesDataStore()),
                onWorkChanged = { hasWork, _ -> changes += hasWork },
                onRuleActivated = { checks++ },
            )

        vm.add(temp, Comparison.BELOW, 40.0)
        advanceUntilIdle()

        assertTrue(changes.last()) // an enabled rule means the worker must run
        assertEquals(1, checks) // adding a rule can newly fire → check now
    }

    @Test
    fun `disabling the last enabled rule reports zero enabled and skips the check`() = runTest(dispatcher) {
        val repository = AlertRulesRepository(FakePreferencesDataStore())
        repository.save(listOf(AlertRule("a", temp, Comparison.BELOW, 40.0, enabled = true)))
        val changes = mutableListOf<Boolean>()
        var checks = 0
        val vm =
            AlertsViewModel(
                repository,
                AlertSettingsRepository(FakePreferencesDataStore()),
                onWorkChanged = { hasWork, _ -> changes += hasWork },
                onRuleActivated = { checks++ },
            )
        // Activate stateIn so rules.value reflects the seeded rule.
        val collector = launch { vm.rules.collect {} }
        advanceUntilIdle()

        vm.toggle("a")
        advanceUntilIdle()

        assertFalse(changes.last()) // nothing left to poll for // worker should be cancelled
        assertEquals(0, checks) // disabling can't newly fire
        collector.cancel()
    }

    @Test
    fun `changing cadence retunes the worker only while a rule is live`() = runTest(dispatcher) {
        val repository = AlertRulesRepository(FakePreferencesDataStore())
        repository.save(listOf(AlertRule("a", temp, Comparison.BELOW, 40.0, enabled = true)))
        val cadences = mutableListOf<Int>()
        val vm =
            AlertsViewModel(
                repository,
                AlertSettingsRepository(FakePreferencesDataStore()),
                onWorkChanged = { _, minutes -> cadences += minutes },
            )
        // Collect both flows so rules.value and settings.value reflect state.
        val rulesCollector = launch { vm.rules.collect {} }
        val settingsCollector = launch { vm.settings.collect {} }
        advanceUntilIdle()

        vm.setPollCadence(180)
        advanceUntilIdle()

        assertEquals(listOf(180), cadences) // retuned while a rule is live
        assertEquals(180, vm.settings.value.pollMinutes) // and persisted
        rulesCollector.cancel()
        settingsCollector.cancel()
    }

    @Test
    fun `enabling safety alerts schedules the worker even with no rules`() = runTest(dispatcher) {
        // The whole point of the setting: someone can want tornado warnings and
        // no personal rules at all. Before this, sync() was fed only
        // "any enabled rule", so turning safety alerts on cancelled the very
        // worker meant to deliver them.
        val syncs = mutableListOf<Boolean>()
        var immediateChecks = 0
        val vm =
            AlertsViewModel(
                AlertRulesRepository(FakePreferencesDataStore()),
                AlertSettingsRepository(FakePreferencesDataStore()),
                onWorkChanged = { hasWork, _ -> syncs += hasWork },
                onRuleActivated = { immediateChecks++ },
            )
        val rulesCollector = launch { vm.rules.collect {} }
        val settingsCollector = launch { vm.settings.collect {} }
        advanceUntilIdle()

        vm.setSafetyNotifications(true)
        advanceUntilIdle()

        assertTrue(vm.settings.value.safetyNotifications)
        assertEquals(1, syncs.size) // the worker was re-synced…
        assertEquals(1, immediateChecks) // …and checked once right away
        rulesCollector.cancel()
        settingsCollector.cancel()
    }

    @Test
    fun `disabling safety alerts re-syncs rather than assuming the worker can stop`() = runTest(dispatcher) {
        // Turning it off must not cancel a worker a live rule still needs, so
        // the callback recomputes from current state instead of deciding here.
        val syncs = mutableListOf<Boolean>()
        var immediateChecks = 0
        val repository = AlertRulesRepository(FakePreferencesDataStore())
        repository.save(listOf(AlertRule("a", temp, Comparison.BELOW, 40.0, enabled = true)))
        val vm =
            AlertsViewModel(
                repository,
                AlertSettingsRepository(FakePreferencesDataStore()),
                onWorkChanged = { hasWork, _ -> syncs += hasWork },
                onRuleActivated = { immediateChecks++ },
            )
        val rulesCollector = launch { vm.rules.collect {} }
        val settingsCollector = launch { vm.settings.collect {} }
        advanceUntilIdle()

        vm.setSafetyNotifications(false)
        advanceUntilIdle()

        assertFalse(vm.settings.value.safetyNotifications)
        assertEquals(1, syncs.size)
        assertTrue(syncs.last()) // still true: a live rule needs the worker
        assertEquals(0, immediateChecks) // no point checking immediately when switching off
        rulesCollector.cancel()
        settingsCollector.cancel()
    }

    @Test
    fun `setting a quiet window persists it, including one that wraps midnight`() = runTest(dispatcher) {
        val vm =
            AlertsViewModel(
                AlertRulesRepository(FakePreferencesDataStore()),
                AlertSettingsRepository(FakePreferencesDataStore()),
            )
        val settingsCollector = launch { vm.settings.collect {} }
        advanceUntilIdle()

        vm.setQuietWindow(23, 6)
        advanceUntilIdle()

        assertEquals(23, vm.settings.value.quietStartHour)
        assertEquals(6, vm.settings.value.quietEndHour)
        settingsCollector.cancel()
    }

    @Test
    fun `a zero-length or out-of-range quiet window is refused`() = runTest(dispatcher) {
        // start == end would make isQuietAt match no hour at all, so the toggle
        // would read "on" while nothing was ever silenced. The window must stay
        // at whatever it was rather than persisting a silent no-op.
        val vm =
            AlertsViewModel(
                AlertRulesRepository(FakePreferencesDataStore()),
                AlertSettingsRepository(FakePreferencesDataStore()),
            )
        val settingsCollector = launch { vm.settings.collect {} }
        advanceUntilIdle()
        val before = vm.settings.value

        vm.setQuietWindow(9, 9)
        vm.setQuietWindow(-1, 6)
        vm.setQuietWindow(22, 24)
        advanceUntilIdle()

        assertEquals(before.quietStartHour, vm.settings.value.quietStartHour)
        assertEquals(before.quietEndHour, vm.settings.value.quietEndHour)
        settingsCollector.cancel()
    }

    @Test
    fun `changing cadence always re-syncs, reporting whether anything needs polling`() = runTest(dispatcher) {
        // This used to assert the opposite — that a cadence change with no
        // rules reported nothing — and that guard was the bug: a safety-alerts
        // user has no rules but DOES have a scheduled worker, so their new
        // cadence never reached WorkManager. The sync is unconditional now and
        // carries the decision, so the false case cancels rather than being
        // silently skipped.
        val calls = mutableListOf<Pair<Boolean, Int>>()
        val vm =
            AlertsViewModel(
                AlertRulesRepository(FakePreferencesDataStore()),
                AlertSettingsRepository(FakePreferencesDataStore()),
                onWorkChanged = { hasWork, minutes -> calls += hasWork to minutes },
            )
        val settingsCollector = launch { vm.settings.collect {} }
        advanceUntilIdle()

        vm.setPollCadence(360)
        advanceUntilIdle()

        assertEquals(listOf(false to 360), calls)
        settingsCollector.cancel()
    }

    @Test
    fun `a safety-alerts user with no rules still retunes the cadence`() = runTest(dispatcher) {
        // The case the old rules-only guard silently dropped, and the reason
        // the predicate is now asserted as a boolean: flipping it back to
        // `rules.any { it.enabled }` fails right here.
        val calls = mutableListOf<Pair<Boolean, Int>>()
        val vm =
            AlertsViewModel(
                AlertRulesRepository(FakePreferencesDataStore()),
                AlertSettingsRepository(FakePreferencesDataStore()),
                onWorkChanged = { hasWork, minutes -> calls += hasWork to minutes },
            )
        val settingsCollector = launch { vm.settings.collect {} }
        advanceUntilIdle()

        vm.setSafetyNotifications(true)
        advanceUntilIdle()
        vm.setPollCadence(30)
        advanceUntilIdle()

        // Both the enable and the cadence change report "yes, keep polling",
        // and the second carries the new interval.
        assertEquals(true to 60, calls.first())
        assertEquals(true to 30, calls.last())
        settingsCollector.cancel()
    }

    @Test
    fun `deleting a rule reports the empty list and skips the check`() = runTest(dispatcher) {
        val repository = AlertRulesRepository(FakePreferencesDataStore())
        repository.save(listOf(AlertRule("a", temp, Comparison.BELOW, 40.0, enabled = true)))
        val changes = mutableListOf<Boolean>()
        var checks = 0
        val vm =
            AlertsViewModel(
                repository,
                AlertSettingsRepository(FakePreferencesDataStore()),
                onWorkChanged = { hasWork, _ -> changes += hasWork },
                onRuleActivated = { checks++ },
            )
        val collector = launch { vm.rules.collect {} }
        advanceUntilIdle()

        vm.delete("a")
        advanceUntilIdle()

        assertFalse(changes.last()) // no rules left, so no reason to poll
        assertEquals(0, checks)
        collector.cancel()
    }
}
