package io.raylytics.justmyweather.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Round-trip and robustness for the remembered reading's JSON. The failure this
 * guards is a launch-path crash: whatever is on disk, decoding must produce a
 * reading or a null, never an exception.
 */
class SnapshotCodecTest {
    private val entry =
        CachedSnapshot(
            snapshot =
                WeatherSnapshot(
                    locationLabel = "Louisville, KY",
                    temperatureF = 78.4,
                    conditions = "Mostly Cloudy",
                    windMph = 7.5,
                    precipitationIn = 0.02,
                    pressureInHg = 30.11,
                    observedAt = Instant.parse("2026-08-06T14:40:00Z"),
                    relativeHumidityPercent = 61.0,
                    windDirectionDegrees = 220.0,
                ),
            latitude = 38.2527,
            longitude = -85.7585,
            savedAt = Instant.parse("2026-08-06T14:52:00Z"),
        )

    @Test
    fun `a full entry survives the round trip`() {
        assertEquals(entry, SnapshotCodec.decode(SnapshotCodec.encode(entry)))
    }

    @Test
    fun `the fields a station omitted stay omitted`() {
        // A station that reports only a temperature must not come back with
        // zeroes standing in for wind, humidity or an observation time.
        val sparse =
            entry.copy(
                snapshot =
                    entry.snapshot.copy(
                        conditions = null,
                        windMph = null,
                        precipitationIn = null,
                        pressureInHg = null,
                        observedAt = null,
                        relativeHumidityPercent = null,
                        windDirectionDegrees = null,
                    ),
            )
        assertEquals(sparse, SnapshotCodec.decode(SnapshotCodec.encode(sparse)))
    }

    @Test
    fun `nothing, junk, and a half-written blob all decode to null`() {
        assertNull(SnapshotCodec.decode(null))
        assertNull(SnapshotCodec.decode(""))
        assertNull(SnapshotCodec.decode("not json"))
        assertNull(SnapshotCodec.decode("""{"latitude":38.25,"""))
        // Well-formed JSON missing a required field is still not a reading.
        assertNull(SnapshotCodec.decode("""{"latitude":38.25,"longitude":-85.75}"""))
    }
}
