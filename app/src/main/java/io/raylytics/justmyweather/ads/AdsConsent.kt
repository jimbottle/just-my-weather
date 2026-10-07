package io.raylytics.justmyweather.ads

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether ads may run, and the one moment the ads SDK starts. Google's
 * consent SDK decides (see [ConsentGateway]); this keeps its answer where the
 * UI can watch it and makes sure the SDK is started once, and only after
 * Google has said ads may be requested — never before consent where consent
 * is required.
 *
 * [startAds] is the app's hook to initialise the ads SDK (it also skips an
 * owner of Remove Ads). It runs at most once per process.
 */
class AdsConsent(
    private val gateway: ConsentGateway,
    private val startAds: () -> Unit,
) {
    private val canShow = MutableStateFlow(false)
    private val privacyOptions = MutableStateFlow(false)
    private var started = false
    private var asked = false

    /** True once ads may be requested — the banner waits on this. */
    val canShowAds: StateFlow<Boolean> = canShow.asStateFlow()

    /** True where the user must be able to change their choice. */
    val privacyOptionsRequired: StateFlow<Boolean> = privacyOptions.asStateFlow()

    /**
     * Once per launch, from the first activity. A choice made on an earlier
     * launch applies at once ([settle] before the request), so a returning
     * user's banner doesn't wait on the network; then Google is asked again,
     * since what applies can change (the user travelled, the rules moved).
     * [then] runs once that has settled — form shown and dismissed, no form
     * needed, or an error — so a first launch's other prompts can wait.
     */
    fun gather(activity: Activity, then: () -> Unit = {}) {
        settle()
        // Once per PROCESS, as the KDoc says: an activity recreated by a
        // rotation or a theme switch must not ask Google again or reload the
        // form (roborev 5436). Its [then] still runs.
        if (asked) return then()
        asked = true
        gateway.update(activity) {
            settle()
            then()
        }
    }

    fun showPrivacyOptions(activity: Activity) {
        gateway.showPrivacyOptions(activity) { settle() }
    }

    private fun settle() {
        val can = runCatching { gateway.canRequestAds() }.getOrDefault(false)
        canShow.value = can
        privacyOptions.value = runCatching { gateway.privacyOptionsRequired() }.getOrDefault(false)
        if (can && !started) {
            started = true
            startAds()
        }
    }
}
