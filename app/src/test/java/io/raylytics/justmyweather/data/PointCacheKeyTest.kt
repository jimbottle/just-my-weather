package io.raylytics.justmyweather.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * The key resolved NWS points are filed under. Pure, so the rounding that
 * makes the cache work is pinned without a store or a network.
 */
class PointCacheKeyTest {
    private fun at(latitude: Double, longitude: Double) =
        PointCacheKey.of(WeatherLocation(latitude, longitude, label = ""))

    @Test
    fun `the jitter that made the cache useless collapses to one key`() {
        // Verbatim from Evan's phone: seventeen keys, one doorstep, every one
        // of them having paid for its own /points and /stations lookup. The
        // latitude is identical because coarse location quantises it; the
        // longitude carries an offset Android regenerates about hourly.
        val observed =
            listOf(
                38.252252252252255 to -85.6806540212635,
                38.252252252252255 to -85.68068173281894,
                38.252252252252255 to -85.6804653663773,
                38.252252252252255 to -85.68063108043715,
                38.252252252252255 to -85.68053474076106,
                38.252252252252255 to -85.68061532791401,
                38.252252252252255 to -85.6807935073005,
                38.252252252252255 to -85.68013327780797,
                38.252252252252255 to -85.68010508460972,
                38.252252252252255 to -85.68082056835561,
                38.252252252252255 to -85.68098307942641,
            )
        val keys = observed.map { (lat, lon) -> at(lat, lon) }.toSet()
        assertEquals(setOf("38.25,-85.68"), keys, "one doorstep should be one key")
    }

    @Test
    fun `a real move still gets its own key`() {
        // The rounding must not be so coarse that the next town shares a grid.
        // 2dp is about 1.1km of latitude, well inside NWS's 2.5km cell.
        assertTrue(at(38.25, -85.68) != at(38.30, -85.68), "5km north is a different place")
        assertTrue(at(38.25, -85.68) != at(40.71, -74.01), "New York is certainly a different place")
    }

    @Test
    fun `rounding is to the nearest, with ties going up`() {
        assertEquals("38.25,-85.68", at(38.2549, -85.6849))
        // Ties round toward positive infinity, which is what roundToLong does
        // — so a negative tie lands on the smaller magnitude. Immaterial for a
        // cache key (a tie is a measure-zero coincidence between two cells
        // that resolve identically anyway); recorded so it is not mistaken
        // for a bug later.
        assertEquals("38.26,-85.68", at(38.2550, -85.6850))
        assertEquals("38.26,-85.69", at(38.2550, -85.6851))
    }

    @Test
    fun `the key does not change with the device's language`() {
        // A German locale formats 38.25 as "38,25", which would split wrongly
        // on the separator AND make every key written under one language
        // unreadable under another — a cache that empties itself when someone
        // changes their phone's language.
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("38.25,-85.68", at(38.2527, -85.6805))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun `a coordinate just below zero does not become negative zero`() {
        // "-0.00" is a key no rounded coordinate would ever match, so a place
        // on the equator or the prime meridian would miss its own cache entry.
        assertEquals("0.00,0.00", at(-0.001, -0.004))
        assertEquals("0.00,-0.01", at(0.0, -0.006))
    }

    @Test
    fun `canonical form recognises its own output and rejects the old format`() {
        assertTrue(PointCacheKey.isCanonical(at(38.2527, -85.6805)))
        assertTrue(PointCacheKey.isCanonical("0.00,0.00"))
        // What older builds wrote — full precision, unreachable now.
        assertFalse(PointCacheKey.isCanonical("38.252252252252255,-85.6806540212635"))
        assertFalse(PointCacheKey.isCanonical("38.25,-85.680"), "three places is not the format")
        assertFalse(PointCacheKey.isCanonical("38.25"), "a key is two coordinates")
        assertFalse(PointCacheKey.isCanonical("38.25,-85.68,7"))
        assertFalse(PointCacheKey.isCanonical("north,west"))
        assertFalse(PointCacheKey.isCanonical(""))
    }

    @Test
    fun `junk that parses as a number is still rejected, without throwing`() {
        // The dangerous shape of junk: strings that ARE valid Doubles but
        // cannot be rounded. "NaN" parses happily and then throws inside
        // roundToLong — and this validator runs across whatever the persisted
        // blob contains, on the launch path, before any write could clear it.
        // A single such key would otherwise break every point resolution for
        // good, which is the one thing the codec promises corrupt data cannot
        // do.
        assertFalse(PointCacheKey.isCanonical("NaN,0.00"))
        assertFalse(PointCacheKey.isCanonical("0.00,NaN"))
        assertFalse(PointCacheKey.isCanonical("-NaN,+NaN"))
        assertFalse(PointCacheKey.isCanonical("Infinity,0.00"))
        assertFalse(PointCacheKey.isCanonical("-Infinity,-Infinity"))
    }
}
