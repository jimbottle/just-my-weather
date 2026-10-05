package io.raylytics.justmyweather.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StoreListingTest {
    @Test
    fun `the store and web links point at this package`() {
        assertEquals("market://details?id=io.raylytics.justmyweather", StoreListing.marketUri())
        assertEquals(
            "https://play.google.com/store/apps/details?id=io.raylytics.justmyweather",
            StoreListing.webUrl(),
        )
    }
}
