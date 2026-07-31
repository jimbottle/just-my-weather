package io.raylytics.justmyweather.view

/*
 * How the Daily framing draws: which shape each day takes, and which way the
 * strip runs. Both are user choices on the customize screen, persisted in
 * ViewConfig. Add an entry to either enum and the picker, codec, and home
 * rendering pick it up from the entries list.
 */

/** One tile per day (high + low together) or NWS's native half-day periods. */
enum class DailyStyle(
    /** Stable persistence key — never rename once shipped. */
    val key: String,
    val label: String,
) {
    COMBINED("combined", "High & low"),
    HALF_DAY("halfday", "Day & night"),
    ;

    companion object {
        val DEFAULT = COMBINED

        fun byKey(key: String): DailyStyle? = entries.firstOrNull { it.key == key }
    }
}

/** The daily strip's direction: the horizontal scroll, or stacked rows. */
enum class DailyLayout(
    /** Stable persistence key — never rename once shipped. */
    val key: String,
    val label: String,
) {
    ROW("row", "Side by side"),
    COLUMN("column", "Stacked"),
    ;

    companion object {
        val DEFAULT = ROW

        fun byKey(key: String): DailyLayout? = entries.firstOrNull { it.key == key }
    }
}
