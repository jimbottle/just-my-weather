package io.raylytics.justmyweather.data.places

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the bundled gazetteer off the APK's assets.
 *
 * The only Android-aware part of place search, so everything worth testing
 * ([PlaceCatalog]) stays on the JVM. Reading and parsing ~32k lines is real
 * work — hundreds of milliseconds on a slow device — so it happens on IO and
 * only when someone opens the picker. Nothing else in the app holds it.
 */
class AssetPlaceSource(context: Context) {
    private val assets = context.applicationContext.assets

    suspend fun load(): PlaceCatalog =
        withContext(Dispatchers.IO) {
            assets.open(ASSET).bufferedReader().useLines { lines ->
                PlaceCatalog(PlaceCatalog.parse(lines))
            }
        }

    private companion object {
        /** Written by scripts/build-gazetteer.sh. */
        const val ASSET = "places.tsv"
    }
}
