package io.raylytics.justmyweather.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [BillingGateway] over the Google Play Billing Library. Connects lazily and
 * reconnects on demand — the library drops the connection whenever Play
 * Store updates — and never throws: a Play that cannot answer is a null
 * result, which [RemoveAdsManager] reads as "don't know".
 */
class PlayBillingGateway(context: Context) : BillingGateway, PurchasesUpdatedListener {
    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    override val events: Flow<BillingEvent> = _events

    private val client: BillingClient =
        BillingClient
            .newBuilder(context.applicationContext)
            .setListener(this)
            // Required by the library; it is how a cash purchase (paid later
            // at a store) reaches onPurchasesUpdated once it completes.
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

    /** ProductDetails Play handed back, which the purchase flow needs. */
    private val details = mutableMapOf<String, ProductDetails>()
    private val connecting = Mutex()

    /** Connects if needed. Bounded: a bind that neither finishes nor fails
     * (Play Store killed mid-setup) must not hold the mutex forever, or
     * every later call — and the settings row — would hang on it. */
    private suspend fun connected(): Boolean {
        if (client.isReady) return true
        return connecting.withLock {
            if (client.isReady) return true
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    client.startConnection(
                        object : BillingClientStateListener {
                            override fun onBillingSetupFinished(result: BillingResult) {
                                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                                if (cont.isActive) cont.resume(ok)
                            }

                            override fun onBillingServiceDisconnected() {
                                // Before setup finished: this attempt is over.
                                // After: the next call finds isReady false.
                                if (cont.isActive) cont.resume(false)
                            }
                        },
                    )
                }
            } ?: false
        }
    }

    override suspend fun queryOffer(productId: String): ProductOffer? {
        if (!connected()) return null
        val params =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product
                            .newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                ).build()
        return suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, found ->
                val product =
                    found.productDetailsList
                        .firstOrNull { it.productId == productId }
                        .takeIf { result.responseCode == BillingClient.BillingResponseCode.OK }
                val price = product?.oneTimePurchaseOfferDetails?.formattedPrice
                if (product != null) details[productId] = product
                if (cont.isActive) cont.resume(price?.let { ProductOffer(productId, it) })
            }
        }
    }

    override suspend fun ownedPurchases(): List<PurchaseRecord>? {
        if (!connected()) return null
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        return suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { result, purchases ->
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                val answer = if (ok) purchases.map { it.toRecord() } else null
                if (cont.isActive) cont.resume(answer)
            }
        }
    }

    override suspend fun acknowledge(token: String): Boolean {
        if (!connected()) return false
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        return suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result ->
                if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
        }
    }

    override fun launchPurchase(activity: Activity, productId: String) {
        val product = details[productId]
        if (product == null) {
            _events.tryEmit(BillingEvent.Failed("Google Play isn't available right now. Try again later."))
            return
        }
        val params =
            BillingFlowParams
                .newBuilder()
                .setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()),
                ).build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            val message = result.debugMessage.ifBlank { "Google Play couldn't open the purchase." }
            _events.tryEmit(BillingEvent.Failed(message))
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val event =
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK ->
                    BillingEvent.Purchases(purchases.orEmpty().map { it.toRecord() })
                BillingClient.BillingResponseCode.USER_CANCELED -> BillingEvent.Cancelled
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BillingEvent.AlreadyOwned
                else -> BillingEvent.Failed(result.debugMessage.ifBlank { "The purchase didn't go through." })
            }
        _events.tryEmit(event)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }

    private fun Purchase.toRecord() =
        PurchaseRecord(
            products = products,
            purchased = purchaseState == Purchase.PurchaseState.PURCHASED,
            acknowledged = isAcknowledged,
            token = purchaseToken,
        )
}
