package io.raylytics.justmyweather.support

/*
 * The two mails a user can send the developer, and nothing else: no Android
 * types, no clock, no I/O, so every line of what leaves the phone is unit
 * tested. The Android edges — reading the device, the log, and handing the
 * result to an email app — live in SupportIntents.kt.
 *
 * Ported from open-frame's utils/bugReport.ts, which settled the design: one
 * address, two mails built by DIFFERENT helpers. A bug report carries a full
 * diagnostics block and the recent log, because a bug that doesn't reproduce
 * on the developer's phone is undiagnosable without them. An idea carries a
 * short context and nothing that reads like a crash dump — someone sharing a
 * wish should not be shipping their app state to do it.
 */

/** Where both mails go. One inbox; the subject line tells them apart. */
const val SUPPORT_ADDRESS = "dev@raylytics.io"

enum class SupportKind { BUG, IDEA }

/** The build and device a mail came from, gathered at the edge. */
data class AppInfo(
    val versionName: String,
    /** Internal-track builds can share a versionName; this tells them apart. */
    val versionCode: Int,
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
)

/** A ready-to-send mail. */
data class SupportMail(val subject: String, val body: String)

/** Subject per kind — one place, so the two are told apart in the inbox. */
fun supportSubject(kind: SupportKind, app: AppInfo): String =
    when (kind) {
        SupportKind.BUG -> "Just My Weather bug report (${app.versionName})"
        SupportKind.IDEA -> "Just My Weather idea (${app.versionName})"
    }

/**
 * Everything a bug report attaches, in the order the developer reads it:
 * build, device, the app's state as the user had it, then the recent log.
 * [state] is label/value pairs so the caller decides what is worth knowing
 * without this file having to know every repository. Shown to the user on
 * the form before sending, so it must stay readable by a person.
 */
fun bugDiagnostics(
    app: AppInfo,
    sourceScreen: String,
    state: List<Pair<String, String>>,
    logs: List<String>,
    timestamp: String,
): String =
    buildList {
        add("--- App ---")
        add("Version: ${app.versionName} (build ${app.versionCode})")
        add("")
        add("--- Device ---")
        add("Android: ${app.androidRelease} (API ${app.sdkInt})")
        add("Model: ${app.manufacturer} ${app.model}")
        add("")
        add("--- App State ---")
        add("Source screen: $sourceScreen")
        state.forEach { (label, value) -> add("$label: $value") }
        add("Timestamp: $timestamp")
        add("")
        add("--- Recent Logs (last ${logs.size} lines) ---")
        if (logs.isEmpty()) add("(none)") else addAll(logs)
    }.joinToString("\n")

/**
 * The short context an IDEA carries: which build and device, and where the
 * user was. Deliberately no state and no log (Evan, open-frame 2026-09-25:
 * the two mails are built by different helpers).
 */
fun ideaContext(app: AppInfo, sourceScreen: String): String =
    listOf(
        "App: Just My Weather ${app.versionName} (build ${app.versionCode})",
        "Android: ${app.androidRelease}",
        "Device: ${app.manufacturer} ${app.model}",
        "Screen: $sourceScreen",
    ).joinToString("\n")

/**
 * The mail itself: the user's words first, then what is attached. A bug's
 * attachment is labelled "do not remove" because a reply asking for the
 * version is a round-trip the diagnostics exist to save.
 */
fun supportMail(kind: SupportKind, app: AppInfo, message: String, attached: String): SupportMail {
    val text = message.trim()
    val body =
        when (kind) {
            SupportKind.BUG ->
                listOf(
                    "What happened:",
                    text.ifEmpty { "(no description provided)" },
                    "",
                    "---",
                    "Diagnostics (do not remove):",
                    attached,
                )
            SupportKind.IDEA ->
                listOf(
                    text.ifEmpty { "(no message provided)" },
                    "",
                    "---",
                    attached,
                )
        }.joinToString("\n")
    return SupportMail(subject = supportSubject(kind, app), body = body)
}
