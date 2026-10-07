package io.raylytics.justmyweather.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * [ConsentGateway] over Google's User Messaging Platform. Whether consent is
 * needed is Google's call, made server-side from the request (it knows the
 * EEA/UK/Swiss rules and keeps them current), which is why the app no longer
 * guesses from the phone's network or location. The form itself is the one
 * published for this app in the AdMob console (Privacy & messaging); until
 * one is, UMP has no form to show and [canRequestAds] stays false where
 * consent is required — no ads, never ads without consent.
 */
class UmpConsentGateway(
    context: Context,
    /** "eea" / "not_eea" in a debug build to test the form from anywhere;
     * blank (and always ignored in release) for the real answer. */
    private val debugGeography: String,
    private val debugBuild: Boolean,
) : ConsentGateway {
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(context.applicationContext)

    override fun canRequestAds(): Boolean = info.canRequestAds()

    override fun privacyOptionsRequired(): Boolean =
        info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun update(activity: Activity, onDone: () -> Unit) {
        val params =
            ConsentRequestParameters.Builder().apply { debugSettings(activity)?.let(::setConsentDebugSettings) }
        info.requestConsentInfoUpdate(
            activity,
            params.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    error?.let { Log.w(TAG, "consent form: ${it.errorCode} ${it.message}") }
                    onDone()
                }
            },
            { error ->
                // Offline or Google unreachable: keep whatever was decided
                // last time (canRequestAds reads it), and ask again next launch.
                Log.w(TAG, "consent info update: ${error.errorCode} ${error.message}")
                onDone()
            },
        )
    }

    override fun showPrivacyOptions(activity: Activity, onDone: () -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let { Log.w(TAG, "privacy options: ${it.errorCode} ${it.message}") }
            onDone()
        }
    }

    private fun debugSettings(activity: Activity): ConsentDebugSettings? {
        if (!debugBuild) return null
        val geography =
            when (debugGeography.lowercase()) {
                "eea" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                "not_eea" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_NOT_EEA
                else -> return null
            }
        return ConsentDebugSettings
            .Builder(activity)
            .setDebugGeography(geography)
            // Treat this device as a test device without its hashed id, so
            // an emulator or a developer's phone can see the form.
            .setForceTesting(true)
            .build()
    }

    private companion object {
        const val TAG = "JmwConsent"
    }
}
