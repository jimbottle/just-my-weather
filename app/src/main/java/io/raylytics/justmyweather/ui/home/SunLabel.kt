package io.raylytics.justmyweather.ui.home

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * How a sun time reads on the glance. Pure — instants and a zone in, words out
 * — so the day-boundary reasoning tests on the JVM instead of being discovered
 * at 11pm.
 */
object SunLabel {
    /**
     * "6:52 AM", or "6:52 AM tomorrow" when the event falls on a later local
     * day than [now].
     *
     * The qualifier is the whole point of the function. These are the *next*
     * sunrise and sunset, which are routinely on different days: from mid
     * afternoon until midnight the next sunrise belongs to tomorrow, and a
     * bare "6:52 AM" sitting under tonight's sunset reads as a time that has
     * already passed. Days rather than 24-hour spans, because "tomorrow" is a
     * calendar word — at 11pm an event eight hours away is tomorrow, and at
     * 1am one eight hours away is today.
     */
    fun format(event: Instant, now: Instant, zone: ZoneId, formatter: DateTimeFormatter): String {
        val at = event.atZone(zone)
        val time = at.format(formatter)
        val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), at.toLocalDate())
        return when (days) {
            0L -> time
            1L -> "$time tomorrow"
            // Only reachable near the poles, where the next sunrise can be
            // days out. Naming the weekday beats "in 3 days" for a glance.
            else -> "$time ${at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
        }
    }
}
