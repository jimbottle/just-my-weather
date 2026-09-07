package io.raylytics.justmyweather.view

/**
 * How a forecast tile arranges its zones. SPREAD pins the hour to the top,
 * centres the temperature and pins the conditions to the bottom, so the
 * same thing sits at the same height across a row. STACKED keeps the three
 * together as one block in the middle of the tile, which is denser and
 * reads as a label under a number. [key] is the stable persistence token —
 * never rename it; [label] is the chip text.
 */
enum class ForecastTileLayout(val key: String, val label: String) {
    SPREAD("spread", "Spread out"),
    STACKED("stacked", "Stacked"),
    ;

    companion object {
        val DEFAULT = SPREAD

        fun byKey(key: String): ForecastTileLayout? = entries.firstOrNull { it.key == key }
    }
}
