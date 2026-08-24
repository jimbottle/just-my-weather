package io.raylytics.justmyweather.view

/*
 * How the Daily framing draws each period. A user choice on the customize
 * screen, persisted in ViewConfig; add an entry and the picker, codec, and
 * forecast grid pick it up from the entries list.
 *
 * A `ForecastLayout` (side-by-side vs stacked) used to live here too. The grid
 * subsumed it: forecast periods are now tiles that flow into rows like every
 * other tile, so "which direction does this list run" is no longer a question
 * the user has to answer — which is the customization layer getting smaller as
 * the grid got more general, not a feature being dropped.
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
