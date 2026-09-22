package io.raylytics.justmyweather.ui.home

import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ForecastGroupingTest {
    private fun day(name: String, temp: Double) =
        DailyPeriod(name = name, isDaytime = true, temperatureF = temp, shortForecast = "Sunny")

    private fun night(name: String, temp: Double) =
        DailyPeriod(name = name, isDaytime = false, temperatureF = temp, shortForecast = "Clear")

    @Test
    fun `pairs each daytime period with the following night`() {
        val days = combineDays(listOf(day("Today", 81.0), night("Tonight", 72.0), day("Saturday", 85.0)))
        assertEquals(2, days.size)
        assertEquals("Today", days[0].name)
        assertEquals(81.0, days[0].highF)
        assertEquals(72.0, days[0].lowF)
        // A trailing day with no night yet still shows, low unknown.
        assertEquals("Saturday", days[1].name)
        assertEquals(85.0, days[1].highF)
        assertNull(days[1].lowF)
    }

    @Test
    fun `a combined day keeps both halves, and its detail carries both paragraphs`() {
        val today =
            day("Today", 81.0).copy(detailedForecast = "Sunny, with a high near 81.", precipProbabilityPercent = 10.0)
        val tonight =
            night("Tonight", 72.0).copy(
                detailedForecast = "Clear, with a low around 72.",
                precipProbabilityPercent = 30.0,
            )
        val days = combineDays(listOf(today, tonight))
        assertEquals(today, days[0].day)
        assertEquals(tonight, days[0].night)
        val detail = days[0].detail()
        assertEquals("Today", detail.title)
        assertEquals(listOf("81°", "72°", "30%", "—", "Sunny"), detail.rows.map { it.value })
        assertEquals("Sunny, with a high near 81.\n\nTonight: Clear, with a low around 72.", detail.body)
        // A night-only day has no day half and no wind row.
        val evening = combineDays(listOf(tonight))[0]
        assertNull(evening.day)
        assertEquals(
            listOf("High", "Low", "Chance of precipitation", "Conditions"),
            evening.detail().rows.map { it.label },
        )
    }

    @Test
    fun `a leading night borrows today's high from the warmest remaining hour`() {
        val zone = ZoneId.of("America/New_York")

        fun hour(iso: String, temp: Double?) = ForecastPoint(Instant.parse(iso), temp, null)
        // 7 pm, 9 pm, 11 pm today; 1 am tomorrow is warmer but not today's.
        val hours =
            listOf(
                hour("2026-09-06T23:00:00Z", 83.0),
                hour("2026-09-07T01:00:00Z", 79.0),
                hour("2026-09-07T03:00:00Z", null),
                hour("2026-09-07T05:00:00Z", 90.0),
            )
        val days = combineDays(listOf(night("Tonight", 64.0), day("Labor Day", 89.0)), hours, zone)
        assertEquals(83.0, days[0].highF)
        assertEquals(true, days[0].highFromHours)
        assertEquals("High (rest of today)", days[0].detail().rows.first().label)
        // A leading DAY is NWS's own and is left alone; so is a later night.
        assertEquals(89.0, days[1].highF)
        assertEquals(false, days[1].highFromHours)
        // No hours: honest absence, as before.
        assertNull(combineDays(listOf(night("Tonight", 64.0)))[0].highF)
        assertNull(combineDays(listOf(night("Tonight", 64.0)), emptyList(), zone)[0].highF)
    }

    @Test
    fun `a leading night is the rest of today, not a day against the budget`() {
        // NWS's fourteen periods fetched in the evening: Tonight, then six
        // full days, then a trailing afternoon — eight rows.
        val periods =
            listOf(night("Tonight", 60.0)) +
                (1..6).flatMap { listOf(day("Day $it", 70.0 + it), night("Night $it", 50.0 + it)) } +
                listOf(day("Day 7", 77.0))
        val all = combineDays(periods)
        assertEquals(8, all.size)
        // At the default, every period survives — nothing NWS sent is lost.
        val week = visibleDays(all, 7)
        assertEquals(8, week.size)
        assertEquals(periods, week.periods)
        // "3 days" reaches three real days past tonight, and both styles end
        // on the same period.
        val three = visibleDays(all, 3)
        assertEquals(listOf("Tonight", "Day 1", "Day 2", "Day 3"), three.map { it.name })
        assertEquals("Night 3", three.periods.last().name)
        // Fetched in the morning there is no leading night, and n is n.
        val morning = combineDays(periods.drop(1))
        assertEquals(listOf("Day 1", "Day 2", "Day 3"), visibleDays(morning, 3).map { it.name })
        assertEquals(7, visibleDays(morning, 7).size)
    }

    private fun dated(p: DailyPeriod, iso: String) = p.copy(startTime = Instant.parse(iso))

    private fun ext(date: String, high: Double) =
        io.raylytics.justmyweather.data.openmeteo.ExtendedDay(
            LocalDate.parse(date), high, high - 20, 10.0, "Clear", 5.0, "S",
        )

    @Test
    fun `past NWS's reach the extended days fill in, after NWS's last date and never over it`() {
        val zone = ZoneId.of("America/New_York")
        // Two NWS days, the second ending on Sep 30 (a night starting 10pm ET).
        val nws =
            combineDays(
                listOf(
                    dated(day("Monday", 80.0), "2026-09-28T10:00:00Z"),
                    dated(night("Monday Night", 60.0), "2026-09-28T22:00:00Z"),
                    dated(day("Tuesday", 81.0), "2026-09-29T10:00:00Z"),
                    dated(night("Tuesday Night", 61.0), "2026-09-30T02:00:00Z"),
                ),
            )
        // Open-Meteo starts at today like NWS does; Sep 28 and 29 must not repeat.
        val extended =
            listOf(ext("2026-09-28", 1.0), ext("2026-09-29", 2.0), ext("2026-09-30", 90.0), ext("2026-10-01", 91.0))
        val four = forecastDays(nws, extended, dailyDays = 4, zone = zone)
        assertEquals(listOf("Monday", "Tuesday", "Wed 9/30", "Thu 10/1"), four.map { it.name })
        assertEquals(90.0, four[2].highF)
        assertEquals(extended[2], four[2].extended)
        assertEquals("Extended forecast · Open-Meteo.com", four[2].detail().subtitle)
        // The tile's chance and wind come from the extended day too.
        assertEquals(10.0, four[2].precipChance)
        assertEquals("S", four[2].windDirection)
        // Within NWS's reach nothing is borrowed.
        assertEquals(listOf("Monday", "Tuesday"), forecastDays(nws, extended, 2, zone).map { it.name })
        // No extended data, or NWS periods with no dates to align on: NWS only.
        assertEquals(2, forecastDays(nws, null, 4, zone).size)
        val undated = combineDays(listOf(day("Monday", 80.0), night("Monday Night", 60.0)))
        assertEquals(1, forecastDays(undated, extended, 4, zone).size)
    }

    @Test
    fun `a leading night keeps its own name with no high`() {
        // Opening the app in the evening: NWS's first period is "Tonight".
        val days = combineDays(listOf(night("Tonight", 68.0), day("Friday", 82.0), night("Friday Night", 70.0)))
        assertEquals(2, days.size)
        assertEquals("Tonight", days[0].name)
        assertNull(days[0].highF)
        assertEquals(68.0, days[0].lowF)
        assertEquals("Friday", days[1].name)
        assertEquals(70.0, days[1].lowF)
    }

    @Test
    fun `splits hours at the local midnight boundary`() {
        val zone = ZoneId.of("America/New_York")

        fun hourAt(iso: String) = ForecastPoint(startTime = Instant.parse(iso), temperatureF = 70.0, windMph = null)
        // 02:00Z/03:00Z land on Jul 31 (10/11 pm ET); 04:00Z/05:00Z on Aug 1.
        val groups =
            groupHoursByDay(
                listOf(
                    hourAt("2026-08-01T02:00:00Z"),
                    hourAt("2026-08-01T03:00:00Z"),
                    hourAt("2026-08-01T04:00:00Z"),
                    hourAt("2026-08-01T05:00:00Z"),
                ),
                zone,
            )
        assertEquals(2, groups.size)
        assertEquals(LocalDate.of(2026, 7, 31), groups[0].date)
        assertEquals(2, groups[0].hours.size)
        assertEquals(LocalDate.of(2026, 8, 1), groups[1].date)
        assertEquals(2, groups[1].hours.size)
    }

    @Test
    fun `empty inputs group to nothing`() {
        assertEquals(emptyList<DayForecast>(), combineDays(emptyList()))
        assertEquals(emptyList<HourDayGroup>(), groupHoursByDay(emptyList(), ZoneId.of("UTC")))
    }
}
