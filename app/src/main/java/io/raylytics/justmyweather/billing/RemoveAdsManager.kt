package io.raylytics.justmyweather.billing

import android.app.Activity
import io.raylytics.justmyweather.data.AdsEntitlementRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The one product the app sells. The id is fixed in Play Console
 * (just-my-weather-6ii) and can never be changed or reused there. */
const val REMOVE_ADS_PRODUCT_ID = "remove_ads"

/** Where a purchase attempt stands, for the settings row to narrate. */
sealed interface RemoveAdsStatus {
    data object Idle : RemoveAdsStatus

    /** Play's sheet is open, or its answer is being processed. */
    data object Busy : RemoveAdsStatus

    data class Failed(val message: String) : RemoveAdsStatus

    /** A restore ran and Play reported no purchase on this account. */
    data object NothingToRestore : RemoveAdsStatus
}

/** Whether these purchases include an owned Remove Ads. Pure; the one rule
 * everything else here enforces. */
fun ownsRemoveAds(purchases: List<PurchaseRecord>): Boolean =
    purchases.any { REMOVE_ADS_PRODUCT_ID in it.products && it.purchased }

/**
 * Keeps [AdsEntitlementRepository] in step with Google Play. On every start
 * it asks Play what this account owns and writes the answer down: a purchase
 * grants Remove Ads, an answer with no purchase (a refund) takes it away, and
 * no answer at all (Play unreachable) changes nothing — the device's copy
 * stands until Play can be asked. Also acknowledges every owned purchase,
 * because Play refunds the unacknowledged after three days.
 *
 * Decision logic only; the Play library lives behind [BillingGateway].
 */
class RemoveAdsManager(
    private val gateway: BillingGateway,
    private val entitlement: AdsEntitlementRepository,
    private val scope: CoroutineScope,
    /** Play's purchase sheet must be launched on the main thread; injected
     * so the JVM tests need no Android main looper. */
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    val adsRemoved: Flow<Boolean> = entitlement.adsRemoved

    private val _offer = MutableStateFlow<ProductOffer?>(null)

    /** The price to show, once Play has been asked; null until then. */
    val offer: StateFlow<ProductOffer?> = _offer

    private val _status = MutableStateFlow<RemoveAdsStatus>(RemoveAdsStatus.Idle)
    val status: StateFlow<RemoveAdsStatus> = _status

    /** Call once, at app start. */
    fun start() {
        scope.launch { syncWithPlay() }
        scope.launch { loadOffer() }
        scope.launch { gateway.events.collect(::onEvent) }
    }

    /** Opens the purchase sheet. The result lands on [status] and, when it is
     * a purchase, on [adsRemoved]. */
    fun buy(activity: Activity) {
        _status.value = RemoveAdsStatus.Busy
        scope.launch {
            if (_offer.value == null) loadOffer()
            if (_offer.value == null) {
                _status.value = RemoveAdsStatus.Failed("Google Play isn't available right now. Try again later.")
                return@launch
            }
            withContext(mainDispatcher) { gateway.launchPurchase(activity, REMOVE_ADS_PRODUCT_ID) }
        }
    }

    /** Asks Play again, for a new device or a reinstall. */
    fun restore() {
        _status.value = RemoveAdsStatus.Busy
        scope.launch {
            _status.value =
                when (syncWithPlay()) {
                    true -> RemoveAdsStatus.Idle
                    false -> RemoveAdsStatus.NothingToRestore
                    null -> RemoveAdsStatus.Failed("Couldn't reach Google Play. Check your connection and try again.")
                }
        }
    }

    private suspend fun loadOffer() {
        _offer.value = runCatching { gateway.queryOffer(REMOVE_ADS_PRODUCT_ID) }.getOrNull()
    }

    /** True: owned (and now recorded). False: Play says not owned. Null: Play
     * could not be asked, nothing changed. */
    private suspend fun syncWithPlay(): Boolean? {
        val purchases = runCatching { gateway.ownedPurchases() }.getOrNull() ?: return null
        return record(purchases)
    }

    private suspend fun record(purchases: List<PurchaseRecord>): Boolean {
        val owned = ownsRemoveAds(purchases)
        if (owned) {
            purchases
                .filter { REMOVE_ADS_PRODUCT_ID in it.products && it.purchased && !it.acknowledged }
                .forEach { runCatching { gateway.acknowledge(it.token) } }
        }
        entitlement.setAdsRemoved(owned)
        return owned
    }

    private suspend fun onEvent(event: BillingEvent) {
        when (event) {
            is BillingEvent.Purchases -> {
                // Only ever widens: a purchase sheet's answer says what was
                // just bought, not what the account owns, so a pending or
                // unrelated purchase here must not revoke anything.
                if (ownsRemoveAds(event.purchases)) record(event.purchases)
                _status.value = RemoveAdsStatus.Idle
            }
            BillingEvent.Cancelled -> _status.value = RemoveAdsStatus.Idle
            is BillingEvent.Failed -> _status.value = RemoveAdsStatus.Failed(event.message)
        }
    }
}
