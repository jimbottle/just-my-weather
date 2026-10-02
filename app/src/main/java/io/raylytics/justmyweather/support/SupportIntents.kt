package io.raylytics.justmyweather.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import io.raylytics.justmyweather.BuildConfig

/*
 * The Android edges of the support mails: reading the build and device,
 * reading the app's own recent log, and handing a finished mail to whatever
 * email app the user has. What the mails SAY is in SupportMail.kt.
 */

/** This build on this device. */
fun currentAppInfo(): AppInfo =
    AppInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
    )

/** How many log lines a bug report carries — enough to see the run-up to a
 * failure, few enough that the mail stays readable. */
private const val LOG_LINES = 50

/**
 * The last [LOG_LINES] lines this process wrote to logcat. open-frame keeps
 * its own ring buffer because a JS runtime's console is otherwise lost; on
 * Android the system already keeps one, and since API 16 an app may read its
 * OWN entries without any permission — so there is nothing to install and no
 * call site to remember. Best-effort: a device that refuses gets an empty
 * list, and the report still sends.
 */
fun recentLogLines(): List<String> =
    runCatching {
        val process =
            ProcessBuilder("logcat", "-d", "-t", LOG_LINES.toString(), "--pid", Process.myPid().toString())
                .redirectErrorStream(true)
                .start()
        process.inputStream.bufferedReader().use { it.readLines() }
            // logcat's "--------- beginning of main" banners are noise here.
            .filterNot { it.startsWith("---------") }
            .takeLast(LOG_LINES)
    }.getOrDefault(emptyList())

/**
 * Open the user's email app with [mail] addressed to [SUPPORT_ADDRESS].
 * ACTION_SENDTO with a bare mailto: matches email apps only (not every app
 * that can share text), and the subject and body ride as extras rather than
 * query parameters, which some mail apps mangle at length. Returns false when
 * no email app is installed, so the form can say so instead of failing
 * silently.
 */
fun Context.composeSupportMail(mail: SupportMail): Boolean {
    val intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, mail.subject)
            putExtra(Intent.EXTRA_TEXT, mail.body)
        }
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
