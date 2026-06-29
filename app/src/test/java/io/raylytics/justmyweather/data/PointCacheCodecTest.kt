package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.PointsLookup
import io.raylytics.justmyweather.data.nws.RelativeLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PointCacheCodecTest {
    @Test
    fun `round-trips points with and without a relative location`() {
        val points =
            mapOf(
                "40.71,-74.0" to
                    PointsLookup("OKX", 33, 35, "NYZ072", "KNYC", RelativeLocation("Brooklyn", "NY")),
                "47.6,-122.3" to
                    PointsLookup("SEW", 124, 67, "WAZ558", "KSEA", relativeLocation = null),
            )
        assertEquals(points, PointCacheCodec.decode(PointCacheCodec.encode(points)))
    }

    @Test
    fun `absent or corrupt data decodes to an empty map`() {
        assertEquals(emptyMap<String, PointsLookup>(), PointCacheCodec.decode(null))
        assertEquals(emptyMap<String, PointsLookup>(), PointCacheCodec.decode(""))
        assertEquals(emptyMap<String, PointsLookup>(), PointCacheCodec.decode("{ not json"))
    }
}
