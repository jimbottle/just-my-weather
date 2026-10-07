package io.raylytics.justmyweather.region

import io.raylytics.justmyweather.view.Conventions

/**
 * A region is a Google Play country/region: the unit Play distributes by,
 * keyed by the same ISO 3166-1 alpha-2 code Play's console, its API and its
 * supported-locations page use ("GB", not "UK"). Everything the app adapts
 * per place-in-the-world hangs off one of these — units, date order, the
 * clock — so switching region switches all of them at once. (Whether an ad
 * may run is not a region matter: Google's consent SDK decides, ads/AdsConsent.) How the app decides which region it is in, and how to add
 * one, is docs/REGIONS.md.
 */
data class Region(
    /** ISO 3166-1 alpha-2, upper case, exactly as Play spells it. */
    val code: String,
    /** The units and date/time conventions this region reads in by default.
     * The user can override units one by one (RegionSettings); the region
     * itself never changes because of that. */
    val conventions: Conventions,
    /** Where the app stands in this region on Google Play. Null for a
     * country the registry has never been prepared for: the app still
     * adapts to it (derived defaults), it just isn't a region we ship to. */
    val play: PlayStatus?,
    /** Where a fresh install with no location and no chosen place looks:
     * somewhere real in the region, so the first screen is never blank and
     * never another country's weather. Null falls back to the app default. */
    val defaultPlace: DefaultPlace? = null,
) {
    /** The region's English name, from the platform's own data. */
    val name: String get() = Regions.displayName(code)
}

/** A region's first-run place: a label and a coordinate, plus its zone. */
data class DefaultPlace(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
)

/**
 * The region's state on Google Play, which only a human changes (Play
 * Console → Production → Countries/regions). The registry records it so the
 * code and the console can be checked against each other
 * (scripts/check-regions.sh). See docs/REGIONS.md for each transition.
 */
enum class PlayStatus {
    /** Selected in the Play Console's production track: users can install. */
    LIVE,

    /** Passes every gate in docs/REGIONS.md; waiting on the console change. */
    READY,

    /** On the plan, not yet through the gate. */
    PLANNED,
}
