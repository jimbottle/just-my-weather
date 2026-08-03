package io.raylytics.justmyweather.ui.alerts

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.alerts.AlertRule
import io.raylytics.justmyweather.alerts.AlertSettings
import io.raylytics.justmyweather.alerts.AlertSubject
import io.raylytics.justmyweather.alerts.AlertWindow
import io.raylytics.justmyweather.alerts.Comparison
import java.util.Locale

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
    onSetQuietWindow: (Int, Int) -> Unit,
    onSetSafetyNotifications: (Boolean) -> Unit,
    onSetPollCadence: (Int) -> Unit,
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
            QuietHoursRow(
                settings = settings,
                onSetQuietHours = onSetQuietHours,
                onSetQuietWindow = onSetQuietWindow,
            )
            CadenceRow(selected = settings.pollMinutes, onSelect = onSetPollCadence)
            SafetyAlertsRow(
                enabled = settings.safetyNotifications,
                onChange = onSetSafetyNotifications,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CadenceRow(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Check every", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlertSettings.POLL_CHOICES.forEach { minutes ->
                FilterChip(
                    selected = minutes == selected,
                    onClick = { onSelect(minutes) },
                    label = { Text(AlertSettings.pollLabel(minutes)) },
                )
            }
        }
    }
}

/**
 * Official NWS safety alerts — tornado, severe storm, hurricane, dangerous
 * heat, poor air quality. Off by default: the banner on the glance is passive,
 * but a notification interrupts, so this is opted into rather than discovered.
 *
 * The copy says these ignore quiet hours, because they do and that is a
 * surprise worth spending a line on: quiet hours exist so a personal rule
 * waits until morning, whereas a tornado warning at 3am is precisely when
 * being woken is the point.
 */
@Composable
private fun SafetyAlertsRow(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Notify me of safety alerts", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Tornado, severe storm, hurricane, dangerous heat, air quality. " +
                    "These ignore quiet hours.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            modifier = Modifier.testTag("safetyNotifications"),
        )
    }
}

@Composable
private fun QuietHoursRow(
    settings: AlertSettings,
    onSetQuietHours: (Boolean) -> Unit,
    onSetQuietWindow: (Int, Int) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Quiet hours", style = MaterialTheme.typography.bodyMedium)
            // The window is the affordance: tapping the range edits it. In the
            // accent because it IS interactive — the one place on this screen
            // where that colour is earned by a line of text.
            //
            // Editable whether or not quiet hours are on, so the window can be
            // set before switching it on rather than only afterwards.
            Text(
                // Zero-padded 24h clock, e.g. "07:00" — reads as a clean time range.
                text =
                    String.format(
                        Locale.US,
                        "Deliver silently %02d:00–%02d:00",
                        settings.quietStartHour,
                        settings.quietEndHour,
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                // labelMedium is 13sp on an 18sp line, so a bare clickable Text
                // is an ~18dp target — well under the 48dp minimum, and sitting
                // directly under the "Quiet hours" label with nothing between
                // them. Every other control here (Switch, chips, buttons) gets
                // Material's minimumInteractiveComponentSize for free; a raw
                // Text does not, so it is applied by hand. This is the ONLY
                // route to the picker, so a tap landing a few dp high must not
                // silently do nothing.
                //
                // Role.Button so TalkBack announces a button rather than
                // generic clickable text. Padding is inside the clickable, so
                // the touch area grows with it rather than around it.
                modifier =
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button) { editing = true }
                        .minimumInteractiveComponentSize()
                        .padding(vertical = 4.dp)
                        .testTag("quietWindow"),
            )
        }
        Switch(
            checked = settings.quietHoursEnabled,
            onCheckedChange = onSetQuietHours,
            modifier = Modifier.testTag("quietHours"),
        )
    }
    if (editing) {
        QuietWindowDialog(
            startHour = settings.quietStartHour,
            endHour = settings.quietEndHour,
            onDismiss = { editing = false },
            onSave = { start, end ->
                onSetQuietWindow(start, end)
                editing = false
            },
        )
    }
}

/**
 * Pick the quiet window as two whole hours. Hours, not a full time picker,
 * because the stored window is hour-granular — offering minutes would let the
 * user set 22:30 and silently keep 22:00.
 *
 * A window that wraps midnight is normal here (22 → 07) and needs no special
 * handling in the UI; [AlertSettings.isQuietAt] already understands it. The one
 * combination refused is start == end, which would silence nothing while the
 * toggle claimed otherwise.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun QuietWindowDialog(
    startHour: Int,
    endHour: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    var start by rememberSaveable { mutableIntStateOf(startHour) }
    var end by rememberSaveable { mutableIntStateOf(endHour) }
    val valid = start != end
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiet hours") },
        text = {
            // A Dialog renders in its OWN window, outside the Box in
            // MainActivity that sets testTagsAsResourceId — so testTags inside
            // here are invisible to UI tests unless the flag is set again on
            // this subtree. Found the hard way: the picker looked perfect on
            // screen while every tag selector reported "element not found".
            // Any future dialog needs this line too.
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .semantics { testTagsAsResourceId = true },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Starts", style = MaterialTheme.typography.labelMedium)
                HourGrid(prefix = "start", selected = start, onSelect = { start = it })
                Text("Ends", style = MaterialTheme.typography.labelMedium)
                HourGrid(prefix = "end", selected = end, onSelect = { end = it })
                Text(
                    text =
                        if (valid) {
                            String.format(Locale.US, "Silent %02d:00–%02d:00", start, end)
                        } else {
                            "Start and end can't be the same hour — that would silence nothing."
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (valid) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
        },
        confirmButton = {
            // Separate slot from the Column above, hence its own flag.
            TextButton(
                onClick = { onSave(start, end) },
                enabled = valid,
                modifier =
                    Modifier
                        .semantics { testTagsAsResourceId = true }
                        .testTag("saveQuietWindow"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * 24 hour chips, labelled on the same 24h clock as the row they edit.
 *
 * [prefix] namespaces the testTags. Both grids are on screen at once, so a bare
 * "hour_23" would match twice and any selector picking "the first" would
 * silently drive the wrong one — the same ambiguity that once installed a build
 * onto the wrong device here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HourGrid(
    prefix: String,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0..23).forEach { hour ->
            FilterChip(
                selected = hour == selected,
                onClick = { onSelect(hour) },
                label = { Text(String.format(Locale.US, "%02d", hour)) },
                modifier = Modifier.testTag("${prefix}_hour_$hour"),
            )
        }
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
        TextButton(
            onClick = onDelete,
            modifier = Modifier.testTag("removeRule"),
        ) { Text("Remove") }
        Switch(
            checked = rule.enabled,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag("toggleRule"),
        )
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
                modifier = Modifier.weight(1f).testTag("thresholdValue"),
            )
            TextButton(
                onClick = {
                    threshold?.let {
                        onAdd(subject, comparison, it, window)
                        thresholdText = ""
                    }
                },
                enabled = threshold != null,
                modifier = Modifier.testTag("addRule"),
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
