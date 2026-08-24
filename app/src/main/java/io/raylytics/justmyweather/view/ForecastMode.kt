package io.raylytics.justmyweather.view

/*
 * How the forecast grid slices time. THE extension point for new framings —
 * add an entry here, give it data in HomeViewModel, and render it in
 * ForecastGrid; persistence and the toggle follow from the entries list.
 *
 * This replaced a screen-wide `ViewMode` of NOW / HOURLY / DAILY. NOW was never
 * really a third framing: it meant "no forecast at all", which is the absence
 * of this choice rather than one of its values, and modelling it as a sibling
 * put a control for *whether* the forecast shows in the same row as the control
 * for *what it shows*. Whether it shows is now [ViewConfig.showForecast]; this
 * enum is only the option on the forecast itself.
 */
enum class ForecastMode(
    /** Stable persistence key — never rename once shipped. */
    val key: String,
    val label: String,
) {
    HOURLY("hourly", "Hourly"),
    DAILY("daily", "Daily"),
    ;

    companion object {
        val DEFAULT = HOURLY

        fun byKey(key: String): ForecastMode? = entries.firstOrNull { it.key == key }
    }
}
