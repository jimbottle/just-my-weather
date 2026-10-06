package io.raylytics.justmyweather.region

import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.DateOrder
import io.raylytics.justmyweather.view.PrecipitationUnit
import io.raylytics.justmyweather.view.PressureUnit
import io.raylytics.justmyweather.view.TemperatureUnit
import io.raylytics.justmyweather.view.UnitPrefs
import io.raylytics.justmyweather.view.WindUnit
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The region registry: **to add a region, add one line to [PREPARED]** and
 * follow docs/REGIONS.md. Every other country still works — [of] derives
 * sensible defaults for any ISO code — but only a prepared region has a Play
 * status and a first-run place, and only those are ever opened on Play.
 *
 * Pure: no Android, no I/O. The date order and clock come from the JDK's
 * CLDR locale data (the same data Android's ICU carries), so a new region
 * reads its dates the local way without a table here to keep.
 */
object Regions {
    /**
     * Regions prepared for Play, in rollout order (docs/REGIONS.md "Waves").
     * [PlayStatus] here must match the Play Console — scripts/check-regions.sh
     * compares the LIVE set with the production track's countries.
     */
    private val PREPARED: List<Prepared> =
        listOf(
            // Wave 0 — live since 0.2.0.
            Prepared("US", PlayStatus.LIVE, DefaultPlace("New York, NY", 40.7128, -74.0060, "America/New_York")),
            // Wave 1 — English-primary, strong ad markets. READY: gate passed
            // 2026-10-06, evidence in localization/wave-1.md.
            Prepared("CA", PlayStatus.READY, DefaultPlace("Toronto, Canada", 43.70, -79.42, "America/Toronto")),
            Prepared("AU", PlayStatus.READY, DefaultPlace("Sydney, Australia", -33.87, 151.21, "Australia/Sydney")),
            Prepared(
                "NZ",
                PlayStatus.READY,
                DefaultPlace("Auckland, New Zealand", -36.85, 174.76, "Pacific/Auckland"),
            ),
            Prepared("GB", PlayStatus.READY, DefaultPlace("London, United Kingdom", 51.51, -0.13, "Europe/London")),
            Prepared("IE", PlayStatus.READY, DefaultPlace("Dublin, Ireland", 53.33, -6.25, "Europe/Dublin")),
            // Wave 2 — English official or dominant, very large populations.
            Prepared("IN", PlayStatus.PLANNED, DefaultPlace("New Delhi, India", 28.64, 77.22, "Asia/Kolkata")),
            Prepared("PH", PlayStatus.PLANNED, DefaultPlace("Manila, Philippines", 14.60, 120.98, "Asia/Manila")),
            Prepared("NG", PlayStatus.PLANNED, DefaultPlace("Lagos, Nigeria", 6.45, 3.39, "Africa/Lagos")),
            Prepared(
                "ZA",
                PlayStatus.PLANNED,
                DefaultPlace("Johannesburg, South Africa", -26.20, 28.04, "Africa/Johannesburg"),
            ),
            Prepared("PK", PlayStatus.PLANNED, DefaultPlace("Karachi, Pakistan", 24.86, 67.01, "Asia/Karachi")),
            Prepared("KE", PlayStatus.PLANNED, DefaultPlace("Nairobi, Kenya", -1.28, 36.82, "Africa/Nairobi")),
            Prepared("GH", PlayStatus.PLANNED, DefaultPlace("Accra, Ghana", 5.56, -0.20, "Africa/Accra")),
            Prepared("SG", PlayStatus.PLANNED, DefaultPlace("Singapore, Singapore", 1.29, 103.85, "Asia/Singapore")),
            Prepared(
                "MY",
                PlayStatus.PLANNED,
                DefaultPlace("Kuala Lumpur, Malaysia", 3.14, 101.69, "Asia/Kuala_Lumpur"),
            ),
        )

    /** One row of the registry. Conventions are derived like any other
     * country's; pass [conventions] only where the derivation is wrong. */
    private data class Prepared(
        val code: String,
        val play: PlayStatus,
        val defaultPlace: DefaultPlace,
        val conventions: Conventions? = null,
    )

    /** Every prepared region, in rollout order. */
    val prepared: List<Region> get() = PREPARED.map { of(it.code) }

    /** Every ISO country, prepared or not — what the manual picker offers. */
    val all: List<String> get() = Locale.getISOCountries().toList()

    /** Whether [code] is a country code the app can resolve. */
    fun isKnown(code: String): Boolean = code.uppercase() in Locale.getISOCountries()

    /** The region for [code] — the registry's row if it has one, otherwise
     * defaults derived from the country's own conventions. */
    fun of(code: String): Region {
        val upper = code.uppercase()
        val prepared = PREPARED.firstOrNull { it.code == upper }
        return Region(
            code = upper,
            conventions = prepared?.conventions ?: conventionsFor(upper),
            adConsent =
                if (upper in CERTIFIED_CMP_REQUIRED) AdConsent.CERTIFIED_CMP_REQUIRED else AdConsent.NOT_REQUIRED,
            play = prepared?.play,
            defaultPlace = prepared?.defaultPlace,
        )
    }

    fun displayName(code: String): String = Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }

    // ── Derived conventions ────────────────────────────────────────────────

    internal fun conventionsFor(code: String): Conventions {
        val units =
            UnitPrefs(
                temperature = if (code in FAHRENHEIT) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS,
                wind =
                    when (code) {
                        in MILES_PER_HOUR -> WindUnit.MPH
                        in METRES_PER_SECOND -> WindUnit.METRES_PER_SECOND
                        else -> WindUnit.KMH
                    },
                pressure =
                    when (code) {
                        in US_CUSTOMARY -> PressureUnit.INCHES_OF_MERCURY
                        in KILOPASCALS -> PressureUnit.KILOPASCALS
                        in MILLIMETRES_OF_MERCURY -> PressureUnit.MILLIMETRES_OF_MERCURY
                        else -> PressureUnit.HECTOPASCALS
                    },
                precipitation = if (code in INCHES) PrecipitationUnit.INCHES else PrecipitationUnit.MILLIMETRES,
            )
        val locale = localeFor(code)
        val date = shortPattern(locale, FormatStyle.SHORT, null)
        val time = shortPattern(locale, null, FormatStyle.SHORT)
        return Conventions(
            units = units,
            // Day before month in the locale's own short date → day first.
            dateOrder =
                if (date.indexOf('d') in 0 until date.indexOf('M')) DateOrder.DAY_FIRST else DateOrder.MONTH_FIRST,
            clock24 = 'H' in time || 'k' in time,
        )
    }

    /** The locale that best speaks for a country's conventions: its English
     * variant when the platform has one (en-GB, en-IN — how an English UI
     * there writes dates), else the first language the country uses. */
    private fun localeFor(code: String): Locale {
        val candidates = Locale.getAvailableLocales().filter { it.country == code && it.variant.isEmpty() }
        return candidates.firstOrNull { it.language == "en" } ?: candidates.firstOrNull() ?: Locale("en", code)
    }

    private fun shortPattern(locale: Locale, date: FormatStyle?, time: FormatStyle?): String =
        runCatching {
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(date, time, IsoChronology.INSTANCE, locale)
        }.getOrDefault("")

    // NWS territory: everything American.
    private val US_CUSTOMARY = setOf("US", "PR", "GU", "VI", "AS", "MP", "UM")

    // The countries that still read the thermometer in Fahrenheit.
    private val FAHRENHEIT = US_CUSTOMARY + setOf("LR", "BS", "BZ", "KY", "PW", "FM", "MH")

    // Wind in miles per hour: the US, and the UK (road signs, and the Met
    // Office's own default).
    private val MILES_PER_HOUR = US_CUSTOMARY + setOf("GB", "LR")

    // Wind in metres per second, as the national services there forecast it.
    private val METRES_PER_SECOND = setOf("NO", "SE", "FI", "RU", "BY")

    private val KILOPASCALS = setOf("CA") // Environment Canada reports kPa.
    private val MILLIMETRES_OF_MERCURY = setOf("RU", "BY")
    private val INCHES = US_CUSTOMARY + setOf("LR")

    /** Google's EU User Consent Policy: the EEA (EU + IS, LI, NO), the UK and
     * Switzerland. See [AdConsent]. */
    private val CERTIFIED_CMP_REQUIRED =
        setOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT",
            "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
            "IS", "LI", "NO",
            "GB", "CH",
        )
}
