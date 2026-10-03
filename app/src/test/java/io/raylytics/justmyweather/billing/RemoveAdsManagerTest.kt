package io.raylytics.justmyweather.billing

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.raylytics.justmyweather.data.AdsEntitlementRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The decision logic over a fake Play. What matters here is the one rule
 * the privacy policy and the settings row both lean on: the device's copy of
 * the entitlement follows what Play says, and only what Play says.
 *
 * The manager's coroutines run in backgroundScope (its event collector never
 * ends), and advanceUntilIdle stops when no FOREGROUND task is queued — so
 * it would run none of them. runCurrent drains background work too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoveAdsManagerTest {
    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            state.value = transform(state.value)
            return state.value
        }
    }

    private class FakeGateway : BillingGateway {
        /** Null plays an unreachable Play Store. */
        var owned: List<PurchaseRecord>? = emptyList()
        var offer: ProductOffer? = ProductOffer(REMOVE_ADS_PRODUCT_ID, "$0.99")
        val acknowledged = mutableListOf<String>()
        val launched = mutableListOf<String>()
        override val events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)

        override suspend fun queryOffer(productId: String) = offer

        override suspend fun ownedPurchases() = owned

        override suspend fun acknowledge(token: String): Boolean {
            acknowledged += token
            return true
        }

        override fun launchPurchase(activity: Activity, productId: String) {
            launched += productId
        }
    }

    private val dispatcher = StandardTestDispatcher()

    private class Harness(
        val manager: RemoveAdsManager,
        val gateway: FakeGateway,
        val entitlement: AdsEntitlementRepository,
    ) {
        operator fun component1() = manager

        operator fun component2() = gateway

        operator fun component3() = entitlement
    }

    private fun TestScope.harness(gateway: FakeGateway = FakeGateway()): Harness {
        val entitlement = AdsEntitlementRepository(FakeDataStore())
        val manager = RemoveAdsManager(gateway, entitlement, backgroundScope, mainDispatcher = dispatcher)
        return Harness(manager, gateway, entitlement)
    }

    private fun owned(
        acknowledged: Boolean = true,
        purchased: Boolean = true,
        product: String = REMOVE_ADS_PRODUCT_ID,
    ) = PurchaseRecord(
        listOf(product),
        purchased = purchased,
        acknowledged = acknowledged,
        token = "tok-$product-$acknowledged",
    )

    @Test
    fun `ownership is an owned remove_ads purchase, not a pending one or another product`() {
        assertTrue(ownsRemoveAds(listOf(owned())))
        assertTrue(ownsRemoveAds(listOf(owned(acknowledged = false))))
        assertFalse(ownsRemoveAds(listOf(owned(purchased = false))))
        assertFalse(ownsRemoveAds(listOf(owned(product = "something_else"))))
        assertFalse(ownsRemoveAds(emptyList()))
    }

    @Test
    fun `start records what Play says and acknowledges a fresh purchase`() = runTest(dispatcher) {
        val fresh = FakeGateway().apply { owned = listOf(owned(acknowledged = false)) }
        val (manager, gateway, entitlement) = harness(fresh)
        manager.start()
        runCurrent()
        assertTrue(entitlement.adsRemoved.first())
        assertEquals(listOf("tok-remove_ads-false"), gateway.acknowledged)
        assertEquals("$0.99", manager.offer.value?.formattedPrice)
    }

    @Test
    fun `an unreachable Play changes nothing, an empty answer revokes`() = runTest(dispatcher) {
        val (manager, gateway, entitlement) = harness()
        entitlement.setAdsRemoved(true) // bought on an earlier run
        gateway.owned = null
        manager.start()
        runCurrent()
        assertTrue(entitlement.adsRemoved.first(), "offline: the device's copy stands")

        gateway.owned = emptyList() // refunded since
        manager.restore()
        runCurrent()
        assertFalse(entitlement.adsRemoved.first(), "Play answered and the purchase is gone")
        assertEquals(RemoveAdsStatus.NothingToRestore, manager.status.value)

        gateway.owned = null
        manager.restore()
        assertEquals(RemoveAdsStatus.Restoring, manager.status.value)
        runCurrent()
        assertTrue(manager.status.value is RemoveAdsStatus.Failed, "unreachable on a restore is said so")
    }

    @Test
    fun `a purchase from the sheet grants, a cancelled sheet is just idle again`() = runTest(dispatcher) {
        val (manager, gateway, entitlement) = harness()
        manager.start()
        runCurrent()
        assertFalse(entitlement.adsRemoved.first())

        manager.buy(FAKE_ACTIVITY)
        runCurrent()
        assertEquals(RemoveAdsStatus.Busy, manager.status.value)
        assertEquals(listOf(REMOVE_ADS_PRODUCT_ID), gateway.launched)

        gateway.events.emit(BillingEvent.Cancelled)
        runCurrent()
        assertEquals(RemoveAdsStatus.Idle, manager.status.value)
        assertFalse(entitlement.adsRemoved.first())

        gateway.events.emit(BillingEvent.Purchases(listOf(owned(acknowledged = false))))
        runCurrent()
        assertTrue(entitlement.adsRemoved.first())
        assertEquals(listOf("tok-remove_ads-false"), gateway.acknowledged)
        assertEquals(RemoveAdsStatus.Idle, manager.status.value)
    }

    @Test
    fun `an already-owned refusal asks Play again, which is what restores a stale device`() = runTest(dispatcher) {
        // Reinstalled offline: Play could not be asked at start, so the
        // device thinks ads are on. Tapping Buy gets ITEM_ALREADY_OWNED with
        // no purchase attached; by then Play is reachable and says owned.
        val gateway = FakeGateway().apply { owned = null }
        val (manager, _, entitlement) = harness(gateway)
        manager.start()
        runCurrent()
        assertFalse(entitlement.adsRemoved.first())
        gateway.owned = listOf(owned())
        manager.buy(FAKE_ACTIVITY)
        runCurrent()
        gateway.events.emit(BillingEvent.AlreadyOwned)
        runCurrent()
        assertTrue(entitlement.adsRemoved.first())
        assertEquals(RemoveAdsStatus.Idle, manager.status.value)
    }

    @Test
    fun `a pending purchase from the sheet grants nothing yet and revokes nothing`() = runTest(dispatcher) {
        val (manager, gateway, entitlement) = harness()
        entitlement.setAdsRemoved(true)
        gateway.owned = null
        manager.start()
        runCurrent()
        gateway.events.emit(BillingEvent.Purchases(listOf(owned(purchased = false))))
        runCurrent()
        assertTrue(entitlement.adsRemoved.first())
        assertTrue(gateway.acknowledged.isEmpty(), "a pending purchase must not be acknowledged")
        assertEquals(RemoveAdsStatus.Pending, manager.status.value, "and the buyer is told it is pending")
    }

    @Test
    fun `buying with no offer in hand reports Play unavailable, launches nothing`() = runTest(dispatcher) {
        val (manager, gateway, _) = harness(FakeGateway().apply { offer = null })
        manager.start()
        runCurrent()
        manager.buy(FAKE_ACTIVITY)
        runCurrent()
        assertTrue(manager.status.value is RemoveAdsStatus.Failed)
        assertTrue(gateway.launched.isEmpty())
    }

    private companion object {
        /** Never touched by the fake; only its type is needed. */
        val FAKE_ACTIVITY: Activity = Activity()
    }
}
