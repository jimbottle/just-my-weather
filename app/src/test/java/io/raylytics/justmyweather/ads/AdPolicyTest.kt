package io.raylytics.justmyweather.ads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdPolicyTest {
    @Test
    fun `a debug build serves Google's sample unit whatever was configured`() {
        val sample = AdPolicy.SAMPLE_BANNER_UNIT_ID
        assertEquals(sample, AdPolicy.bannerUnitId(debugBuild = true, configured = "ca-app-pub-1/2"))
        assertEquals("ca-app-pub-1/2", AdPolicy.bannerUnitId(debugBuild = false, configured = "ca-app-pub-1/2"))
    }

    @Test
    fun `sample ids are recognised so a release cut can refuse them`() {
        assertTrue(AdPolicy.isSample(AdPolicy.SAMPLE_BANNER_UNIT_ID))
        assertTrue(AdPolicy.isSample(AdPolicy.SAMPLE_APP_ID))
        assertFalse(AdPolicy.isSample("ca-app-pub-8760580453574524/1234567890"))
    }

    @Test
    fun `the non-personalized switch is the SDK's npa=1`() {
        // The privacy policy promises non-personalized ads; this is the pair
        // AdRequests puts on every request.
        assertEquals("npa" to "1", AdPolicy.NPA_KEY to AdPolicy.NPA_VALUE)
    }
}
