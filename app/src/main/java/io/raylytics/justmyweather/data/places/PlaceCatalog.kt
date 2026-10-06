package io.raylytics.justmyweather.data.places

import io.raylytics.justmyweather.data.WeatherLocation
import java.text.Normalizer
import java.util.Locale
import kotlin.math.cos

/*
 * Turning a typed place name into a coordinate, with no geocoder.
 *
 * The app has no API key and no network dependency for this on purpose, so the
 * lookup table ships in the APK (see scripts/build-gazetteer.sh for where it
 * comes from and why). Everything here is pure: parsing and searching take
 * strings in and give data out, so the ranking rules below are settled on the
 * JVM rather than by tapping at a phone.
 */

/** One place from the bundled gazetteer. */
data class Place(
    val name: String,
    /** Two-letter USPS code — "KY", and also "PR", "GU", "VI". Empty for a
     * place outside the US, which the country names instead. */
    val state: String,
    val latitude: Double,
    val longitude: Double,
    /** ISO 3166-1 alpha-2 — the code Google Play keys its countries by. */
    val country: String = US,
    /** IANA zone, from GeoNames. Null for a US place: NWS supplies it. */
    val timeZone: String? = null,
) {
    /** How the place reads everywhere in the app: "Louisville, KY" at home,
     * "London, United Kingdom" abroad — the state is what tells two US places
     * apart, and the country what tells two foreign ones apart. */
    val label: String
        get() = if (state.isNotEmpty()) "$name, $state" else "$name, ${countryName(country)}"

    fun toLocation(): WeatherLocation =
        WeatherLocation(
            latitude = latitude,
            longitude = longitude,
            label = label,
            timeZone = timeZone,
            country = country,
        )

    companion object {
        const val US = "US"

        /** NWS territories with an ISO code (and Play listing) of their own. */
        val TERRITORIES = setOf("PR", "GU", "VI", "AS", "MP")

        /** The country's English name ("United Kingdom"), from the JDK's
         * own data — no table to keep, and stable for a given code. */
        fun countryName(code: String): String =
            Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }
    }
}

/**
 * The bundled places, indexed for search.
 *
 * Holds a normalised copy of each name alongside the list, because the
 * alternative is re-normalising thirty-two thousand strings on every keystroke.
 * Build one when the picker opens and let it go when the picker closes — it is
 * a few megabytes that only matter while someone is typing.
 */
class PlaceCatalog(val places: List<Place>) {
    private val searchNames: List<String> = places.map { normalize(it.name) }

    // A typed suffix may be a US state ("springfield, il") or a country
    // ("london, gb" / "london, united kingdom"). "CA" is both California and
    // Canada; it simply matches either, and both kinds of result show.
    private val states: Set<String> =
        places.mapTo(HashSet()) { it.state }.apply {
            remove("")
            addAll(places.map { it.country })
        }
    private val countryNames: Map<String, String> =
        places.map { it.country }.distinct().associateBy { normalize(Place.countryName(it)) }

    /**
     * Places matching [query], best first, at most [limit].
     *
     * Ranking is deliberately simple and explained rather than tuned: a name
     * that STARTS with what you typed comes before one that merely contains it
     * ("Louis" should not offer "St. Louis" ahead of "Louisville"), shorter
     * names come before longer ones at the same rank (you are more likely to
     * want "York" than "Yorktown Heights"), and ties break alphabetically by
     * state so the order never wobbles between keystrokes.
     */
    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<Place> {
        val parsed = parseQuery(query, states, countryNames)
        val filtered = search(parsed.name, parsed.state, limit)
        // "Roi Et" (Thailand) ends in a code (ET, Ethiopia), and so do "Bang
        // Na", "Sơn La" and others now that countries share the suffix slot.
        // So a SPACE-separated code is only a guess: places whose name is the
        // whole query come first, then the guess's (roborev 5398). "london
        // ky" names no place, so it still reads as London, Kentucky. A comma
        // is deliberate and stands alone.
        val whole = parsed.whole ?: return filtered
        return (search(whole, null, limit) + filtered).distinct().take(limit)
    }

    private fun search(needle: String, code: String?, limit: Int): List<Place> {
        if (needle.isEmpty()) return emptyList()
        val matches = mutableListOf<Ranked>()
        for (i in places.indices) {
            val place = places[i]
            if (code != null && place.state != code && place.country != code) continue
            val candidate = searchNames[i]
            val rank =
                when {
                    candidate.startsWith(needle) -> 0
                    // Word-start beats mid-word: typing "york" should find
                    // "New York" before "Yorkana", but neither before "York".
                    candidate.contains(" $needle") -> 1
                    candidate.contains(needle) -> 2
                    else -> continue
                }
            matches += Ranked(rank, candidate.length, place)
        }
        matches.sortWith(compareBy({ it.rank }, { it.length }, { it.place.name }, { it.place.state }))
        return matches.take(limit).map { it.place }
    }

    private data class Ranked(val rank: Int, val length: Int, val place: Place)

    /**
     * The bundled place nearest a coordinate, within [maxKm], or null.
     *
     * How the app tells which COUNTRY (and so which region, and which time
     * zone) a raw GPS fix is in without a geocoder. A linear scan with an
     * equirectangular distance — sixty thousand multiplications, well under a
     * frame — is accurate to a few percent at these ranges, and the answer
     * only needs to name the nearest town. Near a border the nearest town can
     * sit across it; the region resolver therefore asks the phone network
     * first (docs/REGIONS.md).
     */
    fun nearest(latitude: Double, longitude: Double, maxKm: Double = NEAREST_MAX_KM): Place? {
        val scale = cos(Math.toRadians(latitude))
        var best: Place? = null
        var bestKm2 = maxKm * maxKm
        for (place in places) {
            val dy = (place.latitude - latitude) * KM_PER_DEGREE
            var dLon = place.longitude - longitude
            if (dLon > 180) dLon -= 360
            if (dLon < -180) dLon += 360
            val dx = dLon * KM_PER_DEGREE * scale
            val km2 = dx * dx + dy * dy
            if (km2 < bestKm2) {
                bestKm2 = km2
                best = place
            }
        }
        return best
    }

    companion object {
        /** Enough to scroll a little, few enough that the list stays a list.
         * A query matching hundreds of places is a query worth narrowing. */
        const val DEFAULT_LIMIT = 40

        /** Far enough to reach a town from deep countryside, near enough
         * that open ocean answers "nowhere". */
        const val NEAREST_MAX_KM = 150.0
        private const val KM_PER_DEGREE = 111.32

        /**
         * Read the bundled TSV (format: scripts/build-gazetteer.sh). A row with
         * four columns is a US place; six add a country and a zone. Malformed
         * lines are SKIPPED, not fatal: a truncated or hand-edited asset
         * should cost the search a row, never the whole picker.
         */
        fun parse(lines: Sequence<String>): List<Place> =
            lines.mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 4) return@mapNotNull null
                val lat = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val lon = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                val name = parts[0].trim()
                val state = parts[1].trim()
                // Play lists Puerto Rico, Guam and the other territories as
                // countries of their own, so their Census rows take that code.
                val country =
                    parts.getOrNull(4)?.trim()?.takeIf { it.length == 2 }
                        ?: state.takeIf { it in Place.TERRITORIES }
                        ?: Place.US
                val zone = parts.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }
                // A Census place needs its state; a GeoNames one its zone.
                val census = parts.size < 6
                if (name.isEmpty() || (census && state.isEmpty())) return@mapNotNull null
                if (!census && zone == null) return@mapNotNull null
                Place(name, state, lat, lon, country, zone)
            }.toList()

        /**
         * Case- and accent-insensitive form. "Bayamon" has to find "Bayamón",
         * because the keyboard someone is typing on may not offer the accent
         * and the place is no less theirs for that.
         */
        fun normalize(text: String): String =
            Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")

        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        /**
         * Split a typed query into a name and an optional state — or country:
         * the same suffix slot takes a USPS state ("louisville, ky"), an ISO
         * country code ("london, gb") or, after a comma, a country's English
         * name ("london, united kingdom"). The returned code filters on
         * either field.
         *
         * Both shapes people actually type work: "louisville, ky" and
         * "louisville ky". The second is only read as a state when the trailing
         * token is a real state code AND something precedes it, so "New York"
         * stays a place and does not become "New" in state "YO" — and a bare
         * "ky" searches for places named that rather than silently listing
         * every place in Kentucky.
         */
        internal fun parseQuery(
            query: String,
            states: Set<String>,
            countryNames: Map<String, String> = emptyMap(),
        ): ParsedQuery {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return ParsedQuery("", null)
            val comma = trimmed.lastIndexOf(',')
            if (comma > 0) {
                val suffix = trimmed.substring(comma + 1).trim()
                val code = suffix.uppercase().takeIf { it in states } ?: countryNames[normalize(suffix)]
                return ParsedQuery(normalize(trimmed.substring(0, comma)), code)
            }
            val lastSpace = trimmed.lastIndexOf(' ')
            if (lastSpace > 0) {
                val tail = trimmed.substring(lastSpace + 1).uppercase()
                if (tail.length == 2 && tail in states) {
                    return ParsedQuery(normalize(trimmed.substring(0, lastSpace)), tail, whole = normalize(trimmed))
                }
            }
            return ParsedQuery(normalize(trimmed), null)
        }
    }

    /** [whole] is the full query as a name, kept when a space-separated
     * suffix was read as a code — the fallback if that reading finds nothing. */
    internal data class ParsedQuery(val name: String, val state: String?, val whole: String? = null)
}
