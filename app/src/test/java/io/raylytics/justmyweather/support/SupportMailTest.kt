package io.raylytics.justmyweather.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two support mails. What matters is that they stay DIFFERENT: a bug
 * report carries the diagnostics a developer needs, an idea carries nothing
 * that reads like a crash dump, and the inbox can tell them apart.
 */
class SupportMailTest {
    private val app =
        AppInfo(
            versionName = "0.2.0",
            versionCode = 7,
            androidRelease = "15",
            sdkInt = 35,
            manufacturer = "Google",
            model = "Pixel 7",
        )

    private val diagnostics =
        bugDiagnostics(
            app = app,
            sourceScreen = "App Settings",
            state = listOf("Gadgetbridge" to "true"),
            logs = listOf("10-02 12:00:00.000 I Weather: fetched"),
            timestamp = "2026-10-02T19:00:00Z",
        )

    @Test
    fun `the subjects tell the two mails apart and name the version`() {
        val bug = supportSubject(SupportKind.BUG, app)
        val idea = supportSubject(SupportKind.IDEA, app)
        assertNotEquals(bug, idea)
        assertEquals("Just My Weather bug report (0.2.0)", bug)
        assertEquals("Just My Weather idea (0.2.0)", idea)
    }

    @Test
    fun `diagnostics carry the build, device, state and log`() {
        // The build number, not just the name: internal builds share names.
        assertTrue("Version: 0.2.0 (build 7)" in diagnostics)
        assertTrue("Android: 15 (API 35)" in diagnostics)
        assertTrue("Model: Google Pixel 7" in diagnostics)
        assertTrue("Source screen: App Settings" in diagnostics)
        assertTrue("Gadgetbridge: true" in diagnostics)
        assertTrue("Weather: fetched" in diagnostics)
        assertTrue("Timestamp: 2026-10-02T19:00:00Z" in diagnostics)
    }

    @Test
    fun `an empty log is said, not left as a dangling header`() {
        val noLogs = bugDiagnostics(app, "App Settings", emptyList(), emptyList(), "t")
        assertTrue(noLogs.endsWith("--- Recent Logs (last 0 lines) ---\n(none)"))
    }

    @Test
    fun `an idea carries a short context and no diagnostics`() {
        val context = ideaContext(app, sourceScreen = "Customize")
        assertEquals(
            "App: Just My Weather 0.2.0 (build 7)\nAndroid: 15\nDevice: Google Pixel 7\nScreen: Customize",
            context,
        )
        val mail = supportMail(SupportKind.IDEA, app, "  A pollen tile  ", context)
        assertTrue(mail.body.startsWith("A pollen tile\n"))
        assertFalse("Diagnostics" in mail.body)
        assertFalse("Recent Logs" in mail.body)
    }

    @Test
    fun `a bug report puts the description first and the diagnostics after`() {
        val mail = supportMail(SupportKind.BUG, app, "Refresh spun forever", diagnostics)
        assertTrue(mail.body.startsWith("What happened:\nRefresh spun forever\n"))
        assertTrue(mail.body.endsWith("Diagnostics (do not remove):\n$diagnostics"))
    }

    @Test
    fun `a blank message is marked rather than sent empty`() {
        assertTrue("(no description provided)" in supportMail(SupportKind.BUG, app, " ", diagnostics).body)
        assertTrue("(no message provided)" in supportMail(SupportKind.IDEA, app, "", "ctx").body)
    }
}
