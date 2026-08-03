package io.raylytics.justmyweather.data.gadgetbridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Sends a weather payload to whichever Gadgetbridge builds are installed.
 *
 * This is the Android edge of the feature: everything decidable without a
 * device lives in [GadgetbridgeWeather] and [openWeatherMapCodeFor], so this
 * class stays thin enough to read in one go.
 *
 * Two platform details do the real work here, and both fail *silently* when
 * missed, which is why each is stated rather than assumed:
 *
 * 1. The broadcast must be EXPLICIT (`setPackage`). Android 8 stopped
 *    delivering most implicit broadcasts to manifest-declared receivers, and
 *    an implicit one here would simply never arrive.
 * 2. The target packages must be listed in a `<queries>` element in the
 *    manifest. From `targetSdk 30` a package not named there is invisible:
 *    `getPackageInfo` throws NameNotFoundException as if Gadgetbridge were not
 *    installed, so detection reports "none found" on a phone that has it.
 */
class GadgetbridgeBroadcaster(private val context: Context) {
    /**
     * Broadcast [payload] to every installed Gadgetbridge flavour, returning
     * the packages it was sent to (empty when none is installed).
     *
     * Sending to all installed builds rather than picking one: they are
     * separate apps that can be installed side by side, each paired to
     * different hardware, and there is no way to tell from here which one owns
     * the watch the user cares about. A build that is running gets the update;
     * one that is not simply ignores it.
     */
    fun send(payload: String): List<String> {
        val installed = KNOWN_PACKAGES.filter { isInstalled(it) }
        if (installed.isEmpty()) {
            Log.i(TAG, "no Gadgetbridge build installed — nothing to send")
            return emptyList()
        }
        installed.forEach { pkg ->
            val intent =
                Intent(ACTION_GENERIC_WEATHER).apply {
                    setPackage(pkg)
                    // WeatherJson (a plain string) rather than the newer
                    // WeatherGz (gzipped bytes). Both ride the same action.
                    // UNVERIFIED whether the Bangle.js fork — the build this
                    // project actually targets — tracks mainline closely
                    // enough to handle WeatherGz, and the deprecated extra is
                    // still the one every build understands. Revisit if the
                    // fork's base is confirmed recent.
                    putExtra(EXTRA_WEATHER_JSON, payload)
                }
            context.sendBroadcast(intent)
        }
        Log.i(TAG, "sent weather to ${installed.joinToString()}")
        return installed
    }

    private fun isInstalled(pkg: String): Boolean =
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    companion object {
        private const val TAG = "GadgetbridgeWeather"

        const val ACTION_GENERIC_WEATHER = "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER"
        const val EXTRA_WEATHER_JSON = "WeatherJson"

        /**
         * Mainline, its nightly channel, and the Espruino fork bundled for
         * Bangle.js. Keep in sync with the `<queries>` block in
         * AndroidManifest.xml — a package added here but not there is invisible
         * to [isInstalled] and will look uninstalled.
         */
        val KNOWN_PACKAGES =
            listOf(
                "nodomain.freeyourgadget.gadgetbridge",
                "nodomain.freeyourgadget.gadgetbridge.nightly",
                "com.espruino.gadgetbridge.banglejs",
            )
    }
}
