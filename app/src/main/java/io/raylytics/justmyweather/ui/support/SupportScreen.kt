package io.raylytics.justmyweather.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.support.SUPPORT_ADDRESS
import io.raylytics.justmyweather.support.SupportKind

/** The words that make the two forms two forms. Kept together so the
 * difference between them reads at a glance. */
private class FormCopy(
    val title: String,
    val intro: String,
    val field: String,
    val placeholder: String,
    val attachedLabel: String,
    val send: String,
)

private fun copyFor(kind: SupportKind) =
    when (kind) {
        SupportKind.BUG ->
            FormCopy(
                title = "Report a bug",
                intro = "Describe what went wrong. Your app version, phone model, settings and recent " +
                    "app log are attached automatically — all of it is shown below, so you can see " +
                    "exactly what is sent.",
                field = "What happened?",
                placeholder = "I tapped Refresh and…",
                attachedLabel = "Attached diagnostics",
                send = "Email bug report",
            )
        SupportKind.IDEA ->
            FormCopy(
                title = "Submit an idea",
                intro = "Couldn't find what you're looking for? Tell the developer what you'd like " +
                    "Just My Weather to do. Only your app version and phone model are attached.",
                field = "Your idea",
                placeholder = "It would be great if…",
                attachedLabel = "Attached app info",
                send = "Email idea",
            )
    }

/**
 * One form per [kind] — a bug report from App Settings, an idea from the
 * bottom of Customize — each opening the user's email app with its own mail
 * (see support/SupportMail.kt). What is attached is shown in full before
 * anything is sent: a report the user can read is one they can trust.
 *
 * [onSend] hands the message to the edge and answers whether an email app
 * took it; false means none is installed, and the form says where to write
 * instead.
 */
@Composable
fun SupportScreen(
    kind: SupportKind,
    attached: String,
    onSend: (message: String) -> Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = copyFor(kind)
    // Saveable: rotating the phone mid-sentence must not eat the sentence.
    var message by rememberSaveable { mutableStateOf("") }
    var noMailApp by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(copy.title, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDone) { Text("Done") }
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = copy.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(copy.field) },
                    placeholder = { Text(copy.placeholder) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("support-message"),
                )
                // Disabled until there is something to send, rather than an
                // after-the-fact "message required" — the button explains
                // itself by not being pressable.
                Button(
                    onClick = { noMailApp = !onSend(message) },
                    enabled = message.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(copy.send) }
                Text(
                    text =
                        if (noMailApp) {
                            "No email app found. Please write to $SUPPORT_ADDRESS directly."
                        } else {
                            "Opens your email app with this addressed to $SUPPORT_ADDRESS."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (noMailApp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(copy.attachedLabel, style = MaterialTheme.typography.labelMedium)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        text = attached,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}
