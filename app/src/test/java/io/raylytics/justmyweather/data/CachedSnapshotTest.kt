package io.raylytics.justmyweather.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * The rule that decides whether a remembered reading is still honest enough to
 * paint. Pure, so it tests without a clock, a store, or a network.
 */
class CachedSnapshotTest {
    private val now: Instant = Instant.parse("2026-08-06T15:00:00Z")
    private val here = WeatherLocation(latitude = 38.25, longitude = -85.76, label = "Louisville, KY")

    private fun entry(
        savedAt: Instant = now,
        latitude: Double = here.latitude,
        longitude: Double = here.longitude,
    ) = CachedSnapshot(
        snapshot =
            WeatherSnapshot(
                locationLabel = "Louisville, KY",
                temperatureF = 78.0,
                conditions = "Mostly Cloudy",
                windMph = null,
                precipitationIn = null,
                pressureInHg = null,
                observedAt = savedAt,
            ),
        latitude = latitude,
        longitude = longitude,
        savedAt = savedAt,
    )

    @Test
    fun `a reading from minutes ago at the same place is usable`() {
        assertTrue(entry(savedAt = now.minus(Duration.ofMinutes(20))).isUsableFor(here, now))
    }

    @Test
    fun `the overnight gap is covered — the case a three-hour cap used to reject`() {
        // Measured on a real device: last use 21:49, next use 09:50.
        assertTrue(entry(savedAt = now.minus(Duration.ofHours(12))).isUsableFor(here, now))
    }

    @Test
    fun `a reading older than the cap is not`() {
        val stale = entry(savedAt = now.minus(CachedSnapshot.MAX_AGE).minusSeconds(1))
        assertFalse(stale.isUsableFor(here, now))
        // Exactly at the cap still counts — the boundary is inclusive.
        assertTrue(entry(savedAt = now.minus(CachedSnapshot.MAX_AGE)).isUsableFor(here, now))
    }

    @Test
    fun `a device clock that moved backwards makes the age unknowable, so drop it`() {
        assertFalse(entry(savedAt = now.plusSeconds(60)).isUsableFor(here, now))
    }

    @Test
    fun `a fix that drifted a few metres still matches`() {
        // What a coarse fix actually does between two launches from the same
        // room — exact coordinate equality would reject this.
        assertTrue(entry(latitude = here.latitude + 0.0004, longitude = here.longitude - 0.0007).isUsableFor(here, now))
    }

    @Test
    fun `a reading from another town is not shown here`() {
        val elsewhere = entry(latitude = 40.7128, longitude = -74.0060)
        assertFalse(elsewhere.isUsableFor(here, now))
        // Either axis alone is enough to disqualify it.
        assertFalse(entry(latitude = here.latitude + 1.0).isUsableFor(here, now))
        assertFalse(entry(longitude = here.longitude + 1.0).isUsableFor(here, now))
    }
}
