package io.raylytics.justmyweather.view

/**
 * Where the safety-alert banner sits when there is one to show.
 *
 * Only ever visible while an alert is active, so this is not a layout the user
 * sees most days — it is a choice about where an interruption belongs. [TOP]
 * is the default because a tornado warning should be the first thing read;
 * [BOTTOM] suits anyone who wants the temperature to stay in the same place
 * regardless of the weather.
 *
 * [key] is the stable persistence token — never rename it. [label] is the chip
 * text in the customize screen.
 */
enum class AlertBannerPosition(val key: String, val label: String) {
    TOP("top", "Top"),
    BOTTOM("bottom", "Bottom"),
    ;

    companion object {
        val DEFAULT = TOP

        fun byKey(key: String): AlertBannerPosition? = entries.firstOrNull { it.key == key }
    }
}
