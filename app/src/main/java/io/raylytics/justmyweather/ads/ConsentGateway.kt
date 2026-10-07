package io.raylytics.justmyweather.ads

import android.app.Activity

/*
 * The seam between the app and Google's consent SDK (UMP), the same shape as
 * billing/BillingGateway: a few plain answers, so the decisions around them
 * (AdsConsent) test on the JVM against a fake, and UmpConsentGateway is the
 * only file that imports the UMP library.
 */
interface ConsentGateway {
    /** Whether ads may be requested now, from what Google last said —
     * persisted by UMP across launches, so a returning user's choice holds
     * from the first frame. False until Google has said so at least once. */
    fun canRequestAds(): Boolean

    /** Whether the user must be offered a way to change their choice (a
     * "Privacy choices" entry): true where a consent form applies. */
    fun privacyOptionsRequired(): Boolean

    /** Ask Google, once per launch, whether consent is needed where the
     * user is, and show its form if it is. [onDone] runs when that settles —
     * a choice made, no form needed, or an error — never more than once. */
    fun update(activity: Activity, onDone: () -> Unit)

    /** Reopen Google's form so the user can change their choice. */
    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit)
}
