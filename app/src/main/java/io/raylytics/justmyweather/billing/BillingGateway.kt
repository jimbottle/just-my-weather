package io.raylytics.justmyweather.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/*
 * The seam between the app and Google Play Billing. Everything the app needs
 * to know about a purchase fits in a few plain values, so the decision logic
 * (RemoveAdsManager) tests on the JVM against a fake, and PlayBillingGateway
 * is the only file that imports the Play library.
 */

/** One purchase as Play reports it, stripped to what the app decides on. */
data class PurchaseRecord(
    val products: List<String>,
    /** Play's PURCHASED state — a PENDING purchase (cash at a store) is not
     * owned yet and must not be acknowledged. */
    val purchased: Boolean,
    /** Play refunds an unacknowledged purchase after three days, so every
     * owned purchase is acknowledged once. */
    val acknowledged: Boolean,
    val token: String,
)

/** A product Play is selling, with the price in the user's own currency. */
data class ProductOffer(val productId: String, val formattedPrice: String)

/** What Play says after a purchase flow the user was sent into. */
sealed interface BillingEvent {
    data class Purchases(val purchases: List<PurchaseRecord>) : BillingEvent

    data object Cancelled : BillingEvent

    data class Failed(val message: String) : BillingEvent
}

interface BillingGateway {
    /** The offer for [productId], or null when Play cannot be asked right now
     * (no Play Store, no connection, product not yet created). */
    suspend fun queryOffer(productId: String): ProductOffer?

    /** Every one-time purchase this Google account holds, or null when Play
     * could not answer — which is different from an empty list: empty means
     * "owns nothing", null means "don't know". */
    suspend fun ownedPurchases(): List<PurchaseRecord>?

    suspend fun acknowledge(token: String): Boolean

    /** Opens Play's purchase sheet over [activity]; the outcome arrives on
     * [events]. Must be called on the main thread. */
    fun launchPurchase(activity: Activity, productId: String)

    val events: Flow<BillingEvent>
}
