package io.raylytics.justmyweather.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Settings that are about the app, not the canvas. Customize is for what the
 * glance shows and how it looks; anything else — handing readings to another
 * app, getting in touch with the developer — belongs here, so Customize stays
 * a page about the glance.
 */
@Composable
fun AppSettingsScreen(
    gadgetbridgeEnabled: Boolean,
    onSetGadgetbridgeEnabled: (Boolean) -> Unit,
    onReportBug: () -> Unit,
    /** "0.2.0 (build 2)" — the line a bug reply would otherwise ask for. */
    version: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("App settings", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDone) { Text("Done") }
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                GadgetbridgeToggle(enabled = gadgetbridgeEnabled, onChange = onSetGadgetbridgeEnabled)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                HelpSection(onReportBug = onReportBug)
                Text(
                    text = "Just My Weather $version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

/**
 * Opt-in hand-off of each reading to Gadgetbridge, which relays it to a paired
 * watch. Off by default: it sends data to another app, so it stays something
 * you go and switch on rather than something you discover already running.
 */
@Composable
private fun GadgetbridgeToggle(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Watch", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Send to Gadgetbridge", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                modifier = Modifier.testTag("gadgetbridge-toggle"),
            )
        }
        Text(
            text = "Hands each new reading to Gadgetbridge, which passes it to a paired watch. " +
                "Does nothing if Gadgetbridge isn't installed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The way to the bug-report form. Ideas are offered at the bottom of
 * Customize instead, where someone notices the option they wanted is
 * missing. */
@Composable
private fun HelpSection(onReportBug: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Help", style = MaterialTheme.typography.labelMedium)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReportBug)
                    .padding(vertical = 8.dp),
        ) {
            Text("Report a bug", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Something wrong? Email the developer, with your app version and device attached.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
