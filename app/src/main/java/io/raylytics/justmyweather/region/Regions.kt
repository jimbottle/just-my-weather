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
 * The region registry: **to add a region, add one entry to [PREPARED]** and
 * follow docs/REGIONS.md. Every other country still works — [of] derives
 * sensible defaults for any ISO code — but only a prepared region has a Play
 * status and a first-run place, and only those are ever opened on Play.
 *
 * Pure: no Android, no I/O. For a country not in the registry the date
 * order and clock come from the platform's CLDR locale data, so it reads the
 * local way without a table to keep; a prepared region pins them (see
 * [Prepared]) so it reads identically on every phone.
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
            Prepared(
                "US",
                PlayStatus.LIVE,
                DefaultPlace("New York, NY", 40.7128, -74.006, "America/New_York"),
                DateOrder.MONTH_FIRST,
                clock24 = false,
            ),
            // Wave 1 — English-primary, strong ad markets. READY: gate passed
            // 2026-10-06, evidence in localization/wave-1.md.
            Prepared(
                "CA",
                PlayStatus.READY,
                DefaultPlace("Toronto, Canada", 43.7, -79.42, "America/Toronto"),
                DateOrder.MONTH_FIRST,
                clock24 = false,
            ),
            Prepared(
                "AU",
                PlayStatus.READY,
                DefaultPlace("Sydney, Australia", -33.87, 151.21, "Australia/Sydney"),
                DateOrder.DAY_FIRST,
                clock24 = false,
            ),
            Prepared(
                "NZ",
                PlayStatus.READY,
                DefaultPlace("Auckland, New Zealand", -36.85, 174.76, "Pacific/Auckland"),
                DateOrder.DAY_FIRST,
                clock24 = false,
            ),
            Prepared(
                "GB",
                PlayStatus.READY,
                DefaultPlace("London, United Kingdom", 51.51, -0.13, "Europe/London"),
                DateOrder.DAY_FIRST,
                clock24 = true,
            ),
            Prepared(
                "IE",
                PlayStatus.READY,
                DefaultPlace("Dublin, Ireland", 53.33, -6.25, "Europe/Dublin"),
                DateOrder.DAY_FIRST,
                clock24 = true,
            ),
            // Wave 2 — English official or dominant, very large populations.
            // READY: gate passed 2026-10-06, evidence in localization/wave-2.md.
            Prepared(
                "IN",
                PlayStatus.READY,
                DefaultPlace("New Delhi, India", 28.64, 77.22, "Asia/Kolkata"),
                DateOrder.DAY_FIRST,
                clock24 = false,
            ),
            Prepared(
                "PH",
                PlayStatus.READY,
                DefaultPlace("Manila, Philippines", 14.6, 120.98, "Asia/Manila"),
                DateOrder.MONTH_FIRST,
                clock24 = false,
            ),
            Prepared(
                "NG",
                PlayStatus.READY,
                DefaultPlace("Lagos, Nigeria", 6.45, 3.39, "Africa/Lagos"),
                DateOrder.DAY_FIRST,
                clock24 = true,
            ),
            Prepared(
                "ZA",
                PlayStatus.READY,
                DefaultPlace("Johannesburg, South Africa", -26.2, 28.04, "Africa/Johannesburg"),
                // CLDR's short date here is year-first (y/MM/dd), but South
                // Africa writes day before month (roborev 5409).
                DateOrder.DAY_FIRST,
                clock24 = true,
            ),
            Prepared(
                "PK",
                PlayStatus.READY,
                DefaultPlace("Karachi, Pakistan", 24.86, 67.01, "Asia/Karachi"),
                DateOrder.DAY_FIRST,
                clock24 = false,
            ),
            Prepared(
                "KE",
                PlayStatus.READY,
                DefaultPlace("Nairobi, Kenya", -1.28, 36.82, "Africa/Nairobi"),
                DateOrder.DAY_FIRST,
                clock24 = true,
            ),
            Prepared(
                "GH",
                PlayStatus.READY,
                DefaultPlace("Accra, Ghana", 5.56, -0.2, "Africa/Accra"),
                DateOrder.DAY_FIRST,
                clock24 = false,
            ),
            Prepared(
                "SG",
                PlayStatus.READY,
                DefaultPlace("Singapore, Singapore", 1.29, 103.85, "Asia/Singapore"),
                DateOrder.DAY_FIRST,
                clock24 = false,
            ),
            Prepared(
                "MY",
                PlayStatus.READY,
                DefaultPlace("Kuala Lumpur, Malaysia", 3.14, 101.69, "Asia/Kuala_Lumpur"),
                DateOrder.DAY_FIRST,
                clock24 = false,
            ),
        )

    /**
     * One row of the registry. Units come from the per-country tables below.
     * The date order and clock are written down HERE rather than derived:
     * derivation reads the platform's CLDR data, which changes between
     * versions — Java 17 and 21 disagree for a wave-2 country, and Android
     * carries whatever ICU its OS version shipped — so a region we ship to
     * must read the same on every phone. Values from CLDR 42 (Java 21 /
     * recent Android), 2026-10-06; change one when local feedback says so.
     */
    private data class Prepared(
        val code: String,
        val play: PlayStatus,
        val defaultPlace: DefaultPlace,
        val dateOrder: DateOrder,
        val clock24: Boolean,
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
            conventions =
                conventionsFor(upper).let { derived ->
                    prepared?.let { derived.copy(dateOrder = it.dateOrder, clock24 = it.clock24) } ?: derived
                },
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
        // MEDIUM, not SHORT: a year-first short pattern (South Africa's
        // y/MM/dd) hides whether the day or the month leads, where the
        // medium one spells it out (dd MMM y; Japan's stays y/MM/dd, month
        // first, which is right there).
        val date = shortPattern(locale, FormatStyle.MEDIUM, null)
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
