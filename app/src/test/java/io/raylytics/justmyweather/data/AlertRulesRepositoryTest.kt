package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.raylytics.justmyweather.alerts.AlertRule
import io.raylytics.justmyweather.alerts.AlertSubject
import io.raylytics.justmyweather.alerts.Comparison
import io.raylytics.justmyweather.alerts.FireLimit
import io.raylytics.justmyweather.view.WeatherField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * The rule list has two writers — the Alerts screen and the background
 * worker — and [AlertRulesRepository.update] is what keeps them from
 * overwriting each other. These tests play out the interleavings that a
 * read-then-save would get wrong.
 */
class AlertRulesRepositoryTest {
    private class FakePreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val flow = MutableStateFlow(initial)
        override val data: Flow<Preferences> = flow

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(flow.value).also { flow.value = it }
    }

    private val temp = AlertSubject.Field(WeatherField.TEMPERATURE)
    private val once = AlertRule("once", temp, Comparison.BELOW, 40.0, limit = FireLimit.ONCE)
    private val other = AlertRule("other", temp, Comparison.ABOVE, 90.0)

    /** What the worker does after a rule notifies: the delta, not a write-back. */
    private fun fired(ids: Set<String>): (List<AlertRule>) -> List<AlertRule> =
        { current -> current.map { if (it.id in ids && it.enabled && !it.isSpent) it.afterFiring() else it } }

    @Test
    fun `a rule that reaches its limit is saved switched off`() = runTest {
        val repository = AlertRulesRepository(FakePreferencesDataStore())
        repository.save(listOf(once, other))

        repository.update(fired(setOf("once")))

        val saved = repository.rules.first().associateBy { it.id }
        assertFalse(saved.getValue("once").enabled)
        assertEquals(1, saved.getValue("once").firedCount)
        assertEquals(other, saved.getValue("other"))
    }

    @Test
    fun `a rule the user deleted mid-fetch stays deleted, and the fired rule still updates`() = runTest {
        val repository = AlertRulesRepository(FakePreferencesDataStore())
        repository.save(listOf(once, other))
        // The worker read [once, other] here and went off to fetch weather…
        // …meanwhile the user deletes "other" and adds a new rule.
        val added = AlertRule("new", temp, Comparison.BELOW, 20.0)
        repository.update { current -> current.filterNot { it.id == "other" } + added }
        // …and the worker comes back with "once" having fired.
        repository.update(fired(setOf("once")))

        val saved = repository.rules.first()
        assertEquals(listOf("once", "new"), saved.map { it.id }) // no resurrection, no loss
        assertFalse(saved.first { it.id == "once" }.enabled)
        assertEquals(added, saved.first { it.id == "new" })
    }

    @Test
    fun `a fired rule the user removed or switched off mid-fetch is left alone`() = runTest {
        val repository = AlertRulesRepository(FakePreferencesDataStore())
        repository.save(listOf(once, other))
        repository.update { current -> current.map { if (it.id == "once") it.copy(enabled = false) else it } }

        repository.update(fired(setOf("once", "other")))

        val saved = repository.rules.first().associateBy { it.id }
        assertEquals(0, saved.getValue("once").firedCount) // off before it could count
        assertEquals(1, saved.getValue("other").firedCount)
    }

    @Test
    fun `update returns the list as saved`() = runTest {
        val repository = AlertRulesRepository(FakePreferencesDataStore())
        val result = repository.update { it + once }
        assertEquals(listOf(once), result)
        assertEquals(result, repository.rules.first())
    }
}
