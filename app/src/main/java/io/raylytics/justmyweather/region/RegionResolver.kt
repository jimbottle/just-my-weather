package io.raylytics.justmyweather.region

import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.PrecipitationUnit
import io.raylytics.justmyweather.view.PressureUnit
import io.raylytics.justmyweather.view.TemperatureUnit
import io.raylytics.justmyweather.view.WindUnit

/**
 * Where each clue to the region came from, in the order they are trusted.
 * Shown in App settings, so the user can see WHY the app thinks it is where
 * it is — and that is the first thing to look at when it is wrong.
 */
enum class RegionSource(val description: String) {
    MANUAL("chosen by you"),
    PHONE_NETWORK("from your phone's mobile network"),
    PHONE_LOCATION("from your phone's location"),
    PLACE("from the place the app is showing"),
    DEVICE_SETTINGS("from your phone's language settings"),
    DEFAULT("the app's default"),
}

/**
 * Every clue to the region, gathered at the edges (region/RegionSignals.kt)
 * and judged here. Each is an ISO alpha-2 code or null when that clue is
 * unavailable — no SIM, no location permission, a place with no country.
 */
data class RegionClues(
    /** The country of the mobile network the phone is ON, which is where it
     * physically is (a roaming phone reports the visited network). */
    val phoneNetwork: String? = null,
    /** The country of the phone's last location fix, via the gazetteer. */
    val phoneLocation: String? = null,
    /** The country of the place the app is showing. */
    val place: String? = null,
    /** The country in the phone's language settings (en-GB → GB). */
    val deviceSettings: String? = null,
)

/** The region in force, and the clue that decided it. */
data class ResolvedRegion(val region: Region, val source: RegionSource)

/** The user's choices: a region of their own, and units one by one. Null
 * means "automatic" for each. */
data class RegionSettings(
    val manualRegion: String? = null,
    val temperature: TemperatureUnit? = null,
    val wind: WindUnit? = null,
    val pressure: PressureUnit? = null,
    val precipitation: PrecipitationUnit? = null,
) {
    companion object {
        val AUTOMATIC = RegionSettings()
    }
}

/**
 * Which region the app is in, decided in one place (Evan, 2026-10-06):
 *
 *  1. the region the user chose, if they chose one — the override;
 *  2. otherwise where the PHONE is: its mobile network's country first (exact
 *     at a border, and needs no permission), then its last location fix;
 *  3. otherwise the country of the place the app is showing;
 *  4. otherwise the phone's language settings;
 *  5. otherwise the United States, the app's home.
 *
 * The phone outranks the place on purpose: region is about the PERSON — the
 * units they think in, the ads they may be shown — and someone in Leeds
 * looking at their sister's weather in Ohio still reads Celsius. (The place
 * still decides the time zone its own hours are shown in; that is the
 * place's, not the region's.)
 *
 * A clue that is not a real ISO country code is skipped, not trusted:
 * networks report "" when there is none, and some report junk.
 */
object RegionResolver {
    const val HOME = "US"

    fun resolve(settings: RegionSettings, clues: RegionClues): ResolvedRegion {
        val ordered =
            listOf(
                settings.manualRegion to RegionSource.MANUAL,
                clues.phoneNetwork to RegionSource.PHONE_NETWORK,
                clues.phoneLocation to RegionSource.PHONE_LOCATION,
                clues.place to RegionSource.PLACE,
                clues.deviceSettings to RegionSource.DEVICE_SETTINGS,
            )
        val (code, source) =
            ordered.firstOrNull { (code, _) -> code != null && Regions.isKnown(code) }
                ?.let { (code, source) -> code!!.uppercase() to source }
                ?: (HOME to RegionSource.DEFAULT)
        return ResolvedRegion(Regions.of(code), source)
    }

    /**
     * Whether an ad may be shown — and the ad SDK started — which follows
     * where the phone physically IS, and fails CLOSED:
     *
     *  - never a region the user picked by hand (roborev 5400): picking
     *    "United States" for its 12-hour clock must not serve an ad from
     *    Berlin without the consent the law requires there;
     *  - never the place being SHOWN or the phone's language settings
     *    (security review, 2026-10-06): a Wi-Fi tablet in Berlin looking at
     *    New York, or set to US English, is still in Berlin;
     *  - and with no physical clue at all — no mobile network, no location
     *    fix — consent is treated as required: no ads, rather than a guess.
     *
     * Units and dates still follow every clue and the manual choice.
     */
    fun adEligibility(clues: RegionClues): AdEligibility {
        val physical =
            listOf(clues.phoneNetwork, clues.phoneLocation)
                .firstOrNull { it != null && Regions.isKnown(it) }
                ?: return AdEligibility.LOCATION_UNKNOWN
        return when (Regions.of(physical).adConsent) {
            AdConsent.NOT_REQUIRED -> AdEligibility.ALLOWED
            AdConsent.CERTIFIED_CMP_REQUIRED -> AdEligibility.CONSENT_REQUIRED
        }
    }

    /** The conventions in force: the region's, with any unit the user chose
     * put in its place. The date order and clock always follow the region —
     * choosing a region is how to change them. */
    fun conventions(resolved: ResolvedRegion, settings: RegionSettings): Conventions {
        val base = resolved.region.conventions
        return base.copy(
            units =
                base.units.copy(
                    temperature = settings.temperature ?: base.units.temperature,
                    wind = settings.wind ?: base.units.wind,
                    pressure = settings.pressure ?: base.units.pressure,
                    precipitation = settings.precipitation ?: base.units.precipitation,
                ),
        )
    }
}
