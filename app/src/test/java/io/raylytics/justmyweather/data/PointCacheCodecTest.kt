package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.PointsLookup
import io.raylytics.justmyweather.data.nws.RelativeLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PointCacheCodecTest {
    /** One point with everything optional filled in, one with none of it. */
    private val points: Map<String, ResolvedPoint> =
        mapOf(
            "40.71,-74.0" to
                ResolvedPoint.Nws(
                    PointsLookup(
                        "OKX", 33, 35, "NYZ072", "KNYC",
                        timeZone = "America/New_York",
                        relativeLocation = RelativeLocation("Brooklyn", "NY"),
                    ),
                ),
            "47.6,-122.3" to
                ResolvedPoint.Nws(PointsLookup("SEW", 124, 67, "WAZ558", "KSEA", relativeLocation = null)),
        )

    private fun Map<String, ResolvedPoint>.nws(key: String) = (this[key] as? ResolvedPoint.Nws)?.point

    @Test
    fun `round-trips points with and without a relative location`() {
        assertEquals(points, PointCacheCodec.decode(PointCacheCodec.encode(points)))
    }

    @Test
    fun `absent or corrupt data decodes to an empty map`() {
        assertEquals(emptyMap<String, ResolvedPoint>(), PointCacheCodec.decode(null))
        assertEquals(emptyMap<String, ResolvedPoint>(), PointCacheCodec.decode(""))
        assertEquals(emptyMap<String, ResolvedPoint>(), PointCacheCodec.decode("{ not json"))
    }

    @Test
    fun `the point's timezone round-trips, and an older cache without one still decodes`() {
        // The zone is persisted so the sun module is right on a cold, offline
        // start — it is asked for cache-only, never as a fetch.
        val restored = PointCacheCodec.decode(PointCacheCodec.encode(points))
        assertEquals("America/New_York", restored.nws("40.71,-74.0")?.timeZone)
        // A point that never carried one is not corrupt, just unknown: the UI
        // falls back to the device's zone until the next resolve fills it in.
        assertNull(restored.nws("47.6,-122.3")?.timeZone)

        val legacy =
            """{"40.71,-74.0":{"gridId":"OKX","gridX":33,"gridY":35,
               "forecastZoneId":"NYZ072","observationStationId":"KNYC"}}"""
        val decoded = PointCacheCodec.decode(legacy)
        assertEquals("OKX", decoded.nws("40.71,-74.0")?.gridId, "the rest of the point survives")
        assertNull(decoded.nws("40.71,-74.0")?.timeZone)
    }

    @Test
    fun `an outside-NWS verdict round-trips beside NWS points, in order`() {
        val mixed = linkedMapOf("51.51,-0.13" to ResolvedPoint.OutsideNws) + points
        val restored = PointCacheCodec.decode(PointCacheCodec.encode(mixed))
        assertEquals(mixed, restored)
        // Order matters: the durable cache evicts oldest-first by it.
        assertEquals(mixed.keys.toList(), restored.keys.toList())
    }

    @Test
    fun `an NWS entry missing a grid field is dropped rather than guessed`() {
        val partial = """{"40.71,-74.0":{"gridId":"OKX","gridX":33}}"""
        assertEquals(emptyMap<String, ResolvedPoint>(), PointCacheCodec.decode(partial))
    }
}
