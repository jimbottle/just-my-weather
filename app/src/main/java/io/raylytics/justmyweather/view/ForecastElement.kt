package io.raylytics.justmyweather.view

/**
 * The things a forecast tile can show beside its temperature. The hour (or
 * the period's name) and the temperature are the tile; these are what the
 * user adds to it. Off by default for the ones NWS sends per hour but few
 * people plan by, so the tile ships as calm as it was.
 *
 * Hour tiles can show every one of these; day tiles show the ones a period
 * carries (chance, conditions, wind — humidity and dew point are hourly
 * only). [key] is the stable persistence token — never rename it; [label]
 * is the chip text on the customize screen.
 */
enum class ForecastElement(val key: String, val label: String) {
    PRECIP_CHANCE("precip", "Chance of rain"),
    CONDITIONS("conditions", "Conditions"),
    WIND("wind", "Wind"),
    HUMIDITY("humidity", "Humidity"),
    DEW_POINT("dewpoint", "Dew point"),
    ;

    companion object {
        /** What the tiles have always shown. */
        val DEFAULT: Set<ForecastElement> = setOf(PRECIP_CHANCE, CONDITIONS)

        fun byKey(key: String): ForecastElement? = entries.firstOrNull { it.key == key }
    }
}
