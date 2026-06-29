package io.raylytics.justmyweather.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.alerts.AlertRule
import io.raylytics.justmyweather.alerts.AlertSettings
import io.raylytics.justmyweather.alerts.AlertSubject
import io.raylytics.justmyweather.alerts.AlertWindow
import io.raylytics.justmyweather.alerts.Comparison

/**
 * Personal alerts: the user's own rules about everyday conditions. Quiet by
 * default — an install with no rules shows a calm empty state and never nags.
 * The builder is one line: pick a field, above/below, a number. These are
 * deliberately not hazard warnings (that's the sibling app) — they're "tell me
 * when it's jacket weather", on the user's terms.
 */
@Composable
fun AlertsScreen(
    rules: List<AlertRule>,
    settings: AlertSettings,
    onAdd: (AlertSubject, Comparison, Double, AlertWindow) -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSetQuietHours: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Alerts", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDone) { Text("Done") }
            }

            if (rules.isEmpty()) {
                Text(
                    text = "No alerts yet. You'll only hear from this app about the conditions " +
                        "you add here — nothing else.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            } else {
                rules.forEach { rule ->
                    RuleRow(
                        rule = rule,
                        onToggle = { onToggle(rule.id) },
                        onDelete = { onDelete(rule.id) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            AddRuleForm(onAdd = onAdd)

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            QuietHoursRow(settings = settings, onSetQuietHours = onSetQuietHours)
        }
    }
}

@Composable
private fun QuietHoursRow(
    settings: AlertSettings,
    onSetQuietHours: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Quiet hours", style = MaterialTheme.typography.bodyMedium)
            Text(
                // Pad to h:mm with a leading zero only where it reads naturally.
                text = "Deliver silently ${settings.quietStartHour}:00–${settings.quietEndHour}:00",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = settings.quietHoursEnabled, onCheckedChange = onSetQuietHours)
    }
}

@Composable
private fun RuleRow(
    rule: AlertRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = rule.summary,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (rule.enabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDelete) { Text("Remove") }
        Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun AddRuleForm(
    onAdd: (AlertSubject, Comparison, Double, AlertWindow) -> Unit,
) {
    var window by remember { mutableStateOf(AlertWindow.NOW) }
    var subject by remember { mutableStateOf(AlertSubject.current.first()) }
    var comparison by remember { mutableStateOf(Comparison.BELOW) }
    var thresholdText by remember { mutableStateOf("") }
    val threshold = thresholdText.toDoubleOrNull()

    // A forecast window narrows the subjects to what the forecast carries
    // (temperature, wind, plus chance of rain); "right now" offers every
    // current reading. Subjects are matched by key so the chip stays selected
    // across the Field/PrecipChance wrapping.
    val subjects = if (window.isForecast) AlertSubject.forecast else AlertSubject.current
    LaunchedEffect(window) {
        if (subjects.none { it.key == subject.key }) subject = subjects.first()
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Add an alert", style = MaterialTheme.typography.labelMedium)

        ChipRow(
            options = AlertWindow.entries,
            selected = window,
            label = { it.label },
            onSelect = { window = it },
        )
        ChipRow(
            options = subjects,
            selected = subject,
            label = { it.label },
            onSelect = { subject = it },
        )
        ChipRow(
            options = Comparison.entries,
            selected = comparison,
            label = { it.word.replaceFirstChar(Char::uppercase) },
            onSelect = { comparison = it },
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it },
                placeholder = { Text("Value") },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    threshold?.let {
                        onAdd(subject, comparison, it, window)
                        thresholdText = ""
                    }
                },
                enabled = threshold != null,
            ) {
                Text("Add")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}
