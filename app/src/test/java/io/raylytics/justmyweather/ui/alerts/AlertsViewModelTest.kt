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
        val changes = mutableListOf<List<AlertRule>>()
        var checks = 0
        val vm =
            AlertsViewModel(
                AlertRulesRepository(FakePreferencesDataStore()),
                AlertSettingsRepository(FakePreferencesDataStore()),
                onRulesChanged = { changes += it },
                onRuleActivated = { checks++ },
            )

        vm.add(temp, Comparison.BELOW, 40.0)
        advanceUntilIdle()

        assertEquals(1, changes.last().count { it.enabled })
        assertEquals(1, checks) // adding a rule can newly fire → check now
    }

    @Test
    fun `disabling the last enabled rule reports zero enabled and skips the check`() = runTest(dispatcher) {
        val repository = AlertRulesRepository(FakePreferencesDataStore())
        repository.save(listOf(AlertRule("a", temp, Comparison.BELOW, 40.0, enabled = true)))
        val changes = mutableListOf<List<AlertRule>>()
        var checks = 0
        val vm =
            AlertsViewModel(
                repository,
                AlertSettingsRepository(FakePreferencesDataStore()),
                onRulesChanged = { changes += it },
                onRuleActivated = { checks++ },
            )
        // Activate stateIn so rules.value reflects the seeded rule.
        val collector = launch { vm.rules.collect {} }
        advanceUntilIdle()

        vm.toggle("a")
        advanceUntilIdle()

        assertEquals(0, changes.last().count { it.enabled }) // worker should be cancelled
        assertEquals(0, checks) // disabling can't newly fire
        collector.cancel()
    }

    @Test
    fun `deleting a rule reports the empty list and skips the check`() = runTest(dispatcher) {
        val repository = AlertRulesRepository(FakePreferencesDataStore())
        repository.save(listOf(AlertRule("a", temp, Comparison.BELOW, 40.0, enabled = true)))
        val changes = mutableListOf<List<AlertRule>>()
        var checks = 0
        val vm =
            AlertsViewModel(
                repository,
                AlertSettingsRepository(FakePreferencesDataStore()),
                onRulesChanged = { changes += it },
                onRuleActivated = { checks++ },
            )
        val collector = launch { vm.rules.collect {} }
        advanceUntilIdle()

        vm.delete("a")
        advanceUntilIdle()

        assertEquals(emptyList<AlertRule>(), changes.last())
        assertEquals(0, checks)
        collector.cancel()
    }
}
