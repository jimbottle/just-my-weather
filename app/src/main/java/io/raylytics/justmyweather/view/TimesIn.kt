package io.raylytics.justmyweather.view

/**
 * Which clock the screen's times read in: the phone's, or the place's.
 *
 * The phone's by default. Someone in Louisville looking at Seattle is
 * usually asking "what is it like there right now, and at 7 my time?" — the
 * times on the screen sit beside the phone's own clock, and a sunset that
 * reads three hours off from that clock is a sum to do. The place's clock is
 * for the traveller planning a day there, and is a switch away.
 *
 * [key] is the stable persistence token — never rename it.
 */
enum class TimesIn(val key: String) {
    DEVICE("device"),
    PLACE("place"),
    ;

    companion object {
        val DEFAULT = DEVICE

        fun byKey(key: String): TimesIn? = entries.firstOrNull { it.key == key }
    }
}
