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
