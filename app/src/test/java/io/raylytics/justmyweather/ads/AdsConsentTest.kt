package io.raylytics.justmyweather.ads

import android.app.Activity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/** Ads run only after Google's consent SDK allows them, and the ads SDK
 * starts exactly once — never before that answer. */
class AdsConsentTest {
    /** Answers the way UMP does; [settleWith] is what the next update says. */
    private class FakeGateway(var can: Boolean = false, var options: Boolean = false) : ConsentGateway {
        var settleWith: (() -> Unit)? = null
        var pending: (() -> Unit)? = null

        override fun canRequestAds() = can

        override fun privacyOptionsRequired() = options

        override fun update(activity: Activity, onDone: () -> Unit) {
            pending = {
                settleWith?.invoke()
                onDone()
            }
        }

        override fun showPrivacyOptions(activity: Activity, onDone: () -> Unit) = onDone()
    }

    private val activity: Activity = mock()

    @Test
    fun `in a consent region nothing starts until the user has chosen`() {
        val gateway = FakeGateway(can = false, options = true)
        var starts = 0
        val consent = AdsConsent(gateway) { starts++ }
        consent.gather(activity)
        assertFalse(consent.canShowAds.value, "no banner while the form is open")
        assertEquals(0, starts, "and the ads SDK is not started")
        // The user chooses in Google's form.
        gateway.settleWith = { gateway.can = true }
        gateway.pending!!()
        assertTrue(consent.canShowAds.value)
        assertTrue(consent.privacyOptionsRequired.value, "Privacy choices is offered")
        assertEquals(1, starts)
    }

    @Test
    fun `where no consent is needed ads start once, and a returning user's choice applies at once`() {
        val gateway = FakeGateway(can = true)
        var starts = 0
        val consent = AdsConsent(gateway) { starts++ }
        consent.gather(activity)
        // Google's last answer (persisted) applies before the network replies.
        assertTrue(consent.canShowAds.value)
        assertEquals(1, starts)
        gateway.pending!!()
        consent.gather(activity)
        gateway.pending!!()
        assertEquals(1, starts, "never started twice")
        assertFalse(consent.privacyOptionsRequired.value)
    }

    @Test
    fun `a gateway that throws means no ads, not a crash`() {
        val broken =
            object : ConsentGateway {
                override fun canRequestAds(): Boolean = error("broken")

                override fun privacyOptionsRequired(): Boolean = error("broken")

                override fun update(activity: Activity, onDone: () -> Unit) = onDone()

                override fun showPrivacyOptions(activity: Activity, onDone: () -> Unit) = onDone()
            }
        var starts = 0
        val consent = AdsConsent(broken) { starts++ }
        consent.gather(activity)
        assertFalse(consent.canShowAds.value)
        assertEquals(0, starts)
    }

    @Test
    fun `what waits on consent runs once it has settled, not before`() {
        val gateway = FakeGateway(can = false, options = true)
        var prompted = 0
        AdsConsent(gateway) {}.gather(activity) { prompted++ }
        assertEquals(0, prompted, "the consent form is still open")
        gateway.pending!!()
        assertEquals(1, prompted)
    }

    @Test
    fun `a recreated activity does not ask Google again, but its follow-up still runs`() {
        val gateway = FakeGateway(can = true)
        var updates = 0
        val counting =
            object : ConsentGateway by gateway {
                override fun update(activity: Activity, onDone: () -> Unit) {
                    updates++
                    onDone()
                }
            }
        val consent = AdsConsent(counting) {}
        var then = 0
        consent.gather(activity) { then++ }
        consent.gather(activity) { then++ } // a rotation
        assertEquals(1, updates)
        assertEquals(2, then)
    }

    @Test
    fun `a rotation mid-request waits for consent, and the form moves to the live activity`() {
        val gateway = FakeGateway(can = false, options = true)
        var asks = 0
        val counting =
            object : ConsentGateway by gateway {
                override fun update(activity: Activity, onDone: () -> Unit) {
                    asks++
                    gateway.update(activity, onDone)
                }
            }
        val consent = AdsConsent(counting) {}
        val first: Activity = mock()
        val second: Activity = mock()
        var thens = 0
        consent.gather(first) { thens++ }
        consent.gather(second) { thens++ } // recreated before Google answered
        assertEquals(0, thens, "nothing runs before consent has settled")
        // The first request ends with no choice (its form had no activity):
        // the flow is asked again on the live activity rather than given up.
        gateway.pending!!()
        assertEquals(2, asks)
        assertEquals(0, thens)
        // The user chooses on the live activity; both follow-ups now run.
        gateway.settleWith = { gateway.can = true }
        gateway.pending!!()
        assertEquals(2, thens)
        assertTrue(consent.canShowAds.value)
    }
}
