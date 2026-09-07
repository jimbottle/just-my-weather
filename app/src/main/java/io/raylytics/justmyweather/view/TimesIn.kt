package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.WeatherSnapshot
import java.time.ZoneId

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

    /**
     * The clock a station READING's timestamp reads in. In place mode this is
     * the reading's OWN zone, not the screen's current place: during a place
     * switch the reading on screen is still the previous place's while the
     * new one is in flight, and formatting it at the new place's offset would
     * put a time on it that never happened. [place] is only the fallback for
     * a reading that carries no zone of its own (an older cached one).
     */
    fun observedZone(snapshot: WeatherSnapshot, place: ZoneId, device: ZoneId = ZoneId.systemDefault()): ZoneId =
        when (this) {
            DEVICE -> device
            PLACE -> snapshot.zone ?: place
        }

    companion object {
        val DEFAULT = DEVICE

        fun byKey(key: String): TimesIn? = entries.firstOrNull { it.key == key }
    }
}
