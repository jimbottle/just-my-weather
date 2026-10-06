package io.raylytics.justmyweather.region

import io.raylytics.justmyweather.view.Conventions

/**
 * A region is a Google Play country/region: the unit Play distributes by,
 * keyed by the same ISO 3166-1 alpha-2 code Play's console, its API and its
 * supported-locations page use ("GB", not "UK"). Everything the app adapts
 * per place-in-the-world hangs off one of these — units, date order, the
 * clock, whether an ad may be shown — so switching region switches all of
 * them at once. How the app decides which region it is in, and how to add
 * one, is docs/REGIONS.md.
 */
data class Region(
    /** ISO 3166-1 alpha-2, upper case, exactly as Play spells it. */
    val code: String,
    /** The units and date/time conventions this region reads in by default.
     * The user can override units one by one (RegionSettings); the region
     * itself never changes because of that. */
    val conventions: Conventions,
    val adConsent: AdConsent,
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
 * Whether the banner may be shown here as the app is built today.
 *
 * The app serves only non-personalized ads and ships no consent dialog.
 * Google's EU User Consent Policy requires a Google-certified consent
 * platform (the UMP SDK) for ad traffic from the EEA, the UK and
 * Switzerland. Until the app has one (a human decision: 5fm J, see
 * docs/REGIONS.md), those regions get NO banner — and so no Remove Ads
 * purchase either, which would have nothing to remove.
 */
enum class AdConsent {
    /** Non-personalized ads need no consent form here. */
    NOT_REQUIRED,

    /** A certified consent platform is required before any ad is served. */
    CERTIFIED_CMP_REQUIRED,
}

/**
 * Whether the banner may be shown — and the ad SDK started — on this phone
 * right now. Three states, because "the law here asks for consent" and "the
 * app can't tell where the phone is" are different facts, and the settings
 * screen says which one applies (roborev 5425).
 */
enum class AdEligibility {
    /** The phone is somewhere no certified consent platform is required. */
    ALLOWED,

    /** The phone is in the EEA, the UK or Switzerland ([AdConsent]). */
    CONSENT_REQUIRED,

    /** No mobile network and no location fix this session: no ads, since
     * the app can't rule out a consent-required country (fail closed). */
    LOCATION_UNKNOWN,
}

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
