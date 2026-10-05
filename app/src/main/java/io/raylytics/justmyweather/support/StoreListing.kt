package io.raylytics.justmyweather.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Where the app lives on Google Play, for the "Rate" row in App settings.
 * The URIs are plain values so they are tested; the hand-off is the only
 * Android part. The Play Store app is asked first — its page opens on the
 * review box — and the web listing is the fallback for a phone without it.
 */
object StoreListing {
    const val PACKAGE = "io.raylytics.justmyweather"

    /** The Play Store app's own scheme; only it can answer this. */
    fun marketUri(packageName: String = PACKAGE): String = "market://details?id=$packageName"

    /** The listing on the web, which any browser opens. Also the URL in
     * README.md and the one the store itself links. */
    fun webUrl(packageName: String = PACKAGE): String = "https://play.google.com/store/apps/details?id=$packageName"

    /** The Play Store app; the market: intent is pinned to it so no chooser appears. */
    const val PLAY_STORE_APP = "com.android.vending"
}

/**
 * Opens this app's Play listing: in the Play Store app when it is installed
 * (pinned to it, so no chooser), otherwise in a browser. Returns false only
 * when neither could take it, which a phone without a browser is about the
 * only way to reach.
 */
fun Context.openStoreListing(): Boolean {
    val store =
        Intent(Intent.ACTION_VIEW, Uri.parse(StoreListing.marketUri(packageName))).apply {
            setPackage(StoreListing.PLAY_STORE_APP)
        }
    val web = Intent(Intent.ACTION_VIEW, Uri.parse(StoreListing.webUrl(packageName)))
    for (intent in listOf(store, web)) {
        try {
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        }
    }
    return false
}
