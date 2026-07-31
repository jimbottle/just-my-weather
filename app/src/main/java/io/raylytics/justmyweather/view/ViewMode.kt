package io.raylytics.justmyweather.view

/*
 * The ways the home screen can slice weather: current conditions, by hour, or
 * by day. THE extension point for new time framings — add an entry here, give
 * it data in HomeViewModel, and render it in HomeScreen; persistence and the
 * pickers follow from the entries list automatically.
 */
enum class ViewMode(
    /** Stable persistence key — never rename once shipped. */
    val key: String,
    val label: String,
) {
    NOW("now", "Now"),
    HOURLY("hourly", "Hourly"),
    DAILY("daily", "Daily"),
    ;

    companion object {
        /** Hourly: the Now hero always sits above the framing, so the default
         * shows current conditions AND the day ahead in one screen. */
        val DEFAULT = HOURLY

        fun byKey(key: String): ViewMode? = entries.firstOrNull { it.key == key }
    }
}
