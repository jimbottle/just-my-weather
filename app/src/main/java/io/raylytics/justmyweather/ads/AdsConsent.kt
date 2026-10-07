package io.raylytics.justmyweather.ads

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

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

    /** Where this process's one consent request stands. */
    private enum class Request { IDLE, IN_FLIGHT, DONE }

    private var request = Request.IDLE

    /** Follow-ups waiting for the request to settle, from every activity
     * that asked while it was in flight (a rotation mid-request). */
    private val waiting = mutableListOf<() -> Unit>()

    /** The newest activity to ask: if the form was meant for one that has
     * since been destroyed, it is shown on this one instead. */
    private var newest: WeakReference<Activity>? = null

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
        // Once per PROCESS (roborev 5436), but never "done" before Google has
        // answered (roborev 5439): an activity recreated by a rotation while
        // the request is in flight waits with the others, so its location
        // prompt can't jump ahead of the consent form.
        when (request) {
            Request.DONE -> then()
            Request.IN_FLIGHT -> {
                waiting += then
                newest = WeakReference(activity)
            }
            Request.IDLE -> {
                waiting += then
                ask(activity)
            }
        }
    }

    private fun ask(activity: Activity) {
        request = Request.IN_FLIGHT
        newest = WeakReference(activity)
        gateway.update(activity) {
            settle()
            // The form was meant for an activity that is gone (recreated
            // mid-request), so it never showed: ask again on the live one.
            val live = newest?.get()?.takeIf { it !== activity && !it.isFinishing && !it.isDestroyed }
            if (!canShow.value && live != null) return@update ask(live)
            request = Request.DONE
            val ready = waiting.toList()
            waiting.clear()
            ready.forEach { it() }
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
