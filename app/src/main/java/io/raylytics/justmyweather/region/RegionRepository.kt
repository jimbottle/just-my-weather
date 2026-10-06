package io.raylytics.justmyweather.region

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.view.Conventions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The clues, read at the edge. An interface so the repository's rules test
 * on the JVM with plain answers (AndroidRegionSignals is the real one).
 * Every answer is an ISO alpha-2 code or null.
 */
interface RegionSignals {
    /** The mobile network the phone is on. No permission needed. */
    fun phoneNetwork(): String?

    /** The country of the phone's last location fix, if it has one. */
    suspend fun phoneLocation(): String?

    /** The country a place is in, when the place doesn't say. */
    suspend fun countryOf(place: WeatherLocation): String?

    /** The country in the phone's language settings. */
    fun deviceSettings(): String?
}

/**
 * The region in force and the user's region settings, persisted.
 *
 * The clues are gathered on each weather refresh ([refresh]) and REMEMBERED,
 * so a cold start — and a background worker, which may have no location at
 * all — reads in the right units from its first frame rather than flashing
 * the default region's. Gathering stops at the first clue that names a real
 * country: a phone on a mobile network never has to load the gazetteer just
 * to learn where it is.
 */
class RegionRepository(
    private val dataStore: DataStore<Preferences>,
    private val signals: RegionSignals,
) {
    val settings: Flow<RegionSettings> =
        dataStore.data.map { RegionCodec.decodeSettings(it[SETTINGS]) }.distinctUntilChanged()

    private val clues: Flow<RegionClues> =
        dataStore.data
            .map { RegionCodec.decodeClues(it[CLUES]) ?: RegionClues(deviceSettings = signals.deviceSettings()) }
            .distinctUntilChanged()

    /** The region in force, and why. */
    val resolved: Flow<ResolvedRegion> = combine(settings, clues, RegionResolver::resolve)

    /** What "automatic" would choose — shown beside the manual picker. */
    val automatic: Flow<ResolvedRegion> = clues.map { RegionResolver.resolve(RegionSettings.AUTOMATIC, it) }

    /** How everything reads right now: the region's conventions with the
     * user's unit choices applied. What every screen formats through. */
    val conventions: Flow<Conventions> =
        combine(settings, clues) { s, c -> RegionResolver.conventions(RegionResolver.resolve(s, c), s) }
            .distinctUntilChanged()

    suspend fun currentConventions(): Conventions = conventions.first()

    suspend fun currentRegion(): Region = resolved.first().region

    /** Re-read the clues for [place], the place the app is showing. Best
     * effort: a failure keeps the clues already remembered. */
    suspend fun refresh(place: WeatherLocation?) {
        val gathered = runCatching { gather(signals, place) }.getOrNull() ?: return
        runCatching { dataStore.edit { it[CLUES] = RegionCodec.encodeClues(gathered) } }
    }

    suspend fun update(transform: (RegionSettings) -> RegionSettings) {
        dataStore.edit { prefs ->
            prefs[SETTINGS] = RegionCodec.encodeSettings(transform(RegionCodec.decodeSettings(prefs[SETTINGS])))
        }
    }

    companion object {
        private val SETTINGS = stringPreferencesKey("region_settings")
        private val CLUES = stringPreferencesKey("region_clues")

        /**
         * The clues in [RegionResolver]'s order, stopping at the first real
         * country — the later ones could not change the answer, and the
         * location and place clues can cost a gazetteer load.
         */
        internal suspend fun gather(signals: RegionSignals, place: WeatherLocation?): RegionClues {
            fun String?.known(): String? = this?.uppercase()?.takeIf(Regions::isKnown)
            signals.phoneNetwork().known()?.let { return RegionClues(phoneNetwork = it) }
            signals.phoneLocation().known()?.let { return RegionClues(phoneLocation = it) }
            place?.let { (it.country ?: signals.countryOf(it)).known() }?.let { return RegionClues(place = it) }
            return RegionClues(deviceSettings = signals.deviceSettings().known())
        }
    }
}
