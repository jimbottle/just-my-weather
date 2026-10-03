package io.raylytics.justmyweather.ads

import android.os.Bundle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/*
 * The only file that draws an ad. The glance puts this at its foot when the
 * user has not bought Remove Ads, and nowhere else: a settings page or a
 * sheet carries no banner. Everything AdMob-specific is in here and in
 * AdPolicy, so swapping the network, or removing ads altogether, touches
 * two files and one call site.
 */

/** The ad request every banner loads: non-personalized, and nothing else.
 * The npa extra is the Google Mobile Ads SDK's switch for it. */
object AdRequests {
    fun nonPersonalized(): AdRequest =
        AdRequest
            .Builder()
            .addNetworkExtrasBundle(
                AdMobAdapter::class.java,
                Bundle().apply { putString(AdPolicy.NPA_KEY, AdPolicy.NPA_VALUE) },
            ).build()
}

/**
 * One anchored adaptive banner, as wide as the space it is given — AdMob
 * picks the height for that width, so nothing here reserves a size and the
 * composable is simply absent when ads are off. Pauses and resumes with the
 * screen and is destroyed when it leaves the composition.
 */
@Composable
fun AdBanner(unitId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BoxWithConstraints(modifier = modifier.fillMaxWidth().testTag("ad-banner")) {
        val widthDp = maxWidth.value.toInt()
        val adView =
            remember(widthDp, unitId) {
                AdView(context).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                    adUnitId = unitId
                }
            }
        DisposableEffect(adView) {
            adView.loadAd(AdRequests.nonPersonalized())
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> adView.pause()
                        Lifecycle.Event.ON_RESUME -> adView.resume()
                        else -> Unit
                    }
                }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                adView.destroy()
            }
        }
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
    }
}
