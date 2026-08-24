package io.raylytics.justmyweather.data.places

import io.raylytics.justmyweather.data.WeatherLocation
import java.text.Normalizer

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
    /** Two-letter USPS code — "KY", and also "PR", "GU", "VI". */
    val state: String,
    val latitude: Double,
    val longitude: Double,
) {
    /** How the place reads everywhere in the app: "Louisville, KY". */
    val label: String get() = "$name, $state"

    fun toLocation(): WeatherLocation =
        WeatherLocation(latitude = latitude, longitude = longitude, label = label)
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
    private val states: Set<String> = places.mapTo(HashSet()) { it.state }

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
        val parsed = parseQuery(query, states)
        val needle = parsed.name
        if (needle.isEmpty()) return emptyList()
        val matches = mutableListOf<Ranked>()
        for (i in places.indices) {
            val place = places[i]
            if (parsed.state != null && place.state != parsed.state) continue
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

    companion object {
        /** Enough to scroll a little, few enough that the list stays a list.
         * A query matching hundreds of places is a query worth narrowing. */
        const val DEFAULT_LIMIT = 40

        /**
         * Read the bundled TSV. Malformed lines are SKIPPED, not fatal: a
         * truncated or hand-edited asset should cost the search a row, never
         * the whole picker.
         */
        fun parse(lines: Sequence<String>): List<Place> =
            lines.mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 4) return@mapNotNull null
                val lat = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val lon = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                val name = parts[0].trim()
                val state = parts[1].trim()
                if (name.isEmpty() || state.isEmpty()) return@mapNotNull null
                Place(name, state, lat, lon)
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
         * Split a typed query into a name and an optional state.
         *
         * Both shapes people actually type work: "louisville, ky" and
         * "louisville ky". The second is only read as a state when the trailing
         * token is a real state code AND something precedes it, so "New York"
         * stays a place and does not become "New" in state "YO" — and a bare
         * "ky" searches for places named that rather than silently listing
         * every place in Kentucky.
         */
        internal fun parseQuery(query: String, states: Set<String>): ParsedQuery {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return ParsedQuery("", null)
            val comma = trimmed.lastIndexOf(',')
            if (comma > 0) {
                val state = trimmed.substring(comma + 1).trim().uppercase()
                return ParsedQuery(
                    normalize(trimmed.substring(0, comma)),
                    state.takeIf { it in states },
                )
            }
            val lastSpace = trimmed.lastIndexOf(' ')
            if (lastSpace > 0) {
                val tail = trimmed.substring(lastSpace + 1).uppercase()
                if (tail.length == 2 && tail in states) {
                    return ParsedQuery(normalize(trimmed.substring(0, lastSpace)), tail)
                }
            }
            return ParsedQuery(normalize(trimmed), null)
        }
    }

    internal data class ParsedQuery(val name: String, val state: String?)
}
