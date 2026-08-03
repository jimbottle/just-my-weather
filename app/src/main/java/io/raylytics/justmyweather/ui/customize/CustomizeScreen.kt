package io.raylytics.justmyweather.ui.customize

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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.ui.theme.accentColor
import io.raylytics.justmyweather.view.AccentChoice
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.FieldSetting
import io.raylytics.justmyweather.view.ForecastLayout
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ThemeMood
import io.raylytics.justmyweather.view.TypeChoice
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.ViewMode
import io.raylytics.justmyweather.view.WeatherField
import kotlinx.coroutines.delay

private const val RELABEL_DEBOUNCE_MS = 400L

/**
 * The customization layer: pick which data points appear, reorder them (the top
 * visible one is the hero), and relabel them. Edits persist immediately and the
 * home view reflects them live. Deliberately a plain list — the power is in
 * editing down, not in a wall of controls.
 */
@Composable
fun CustomizeScreen(
    config: ViewConfig,
    onToggle: (WeatherField) -> Unit,
    onRelabel: (WeatherField, String?) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onSetDensity: (Density) -> Unit,
    onSetDefaultMode: (ViewMode) -> Unit,
    onSetDailyStyle: (DailyStyle) -> Unit,
    onSetDailyLayout: (ForecastLayout) -> Unit,
    onSetHourlyLayout: (ForecastLayout) -> Unit,
    theme: ThemeConfig,
    onThemeChange: (ThemeConfig) -> Unit,
    gadgetbridgeEnabled: Boolean,
    onSetGadgetbridgeEnabled: (Boolean) -> Unit,
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
                Text("Customize view", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDone) { Text("Done") }
            }
            Text(
                text = "Show the fields you care about, in the order you read them. The top " +
                    "shown field is the big one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Everything below the header scrolls as one page — the option
            // sections have outgrown a fixed header on small screens.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DensityPicker(selected = config.density, onSelect = onSetDensity)
                DefaultModePicker(selected = config.defaultMode, onSelect = onSetDefaultMode)
                HourlyViewPicker(layout = config.hourlyLayout, onSetLayout = onSetHourlyLayout)
                DailyViewPicker(
                    style = config.dailyStyle,
                    layout = config.dailyLayout,
                    onSetStyle = onSetDailyStyle,
                    onSetLayout = onSetDailyLayout,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                config.items.forEachIndexed { index, setting ->
                    FieldRow(
                        setting = setting,
                        canMoveUp = index > 0,
                        canMoveDown = index < config.items.lastIndex,
                        onToggle = { onToggle(setting.field) },
                        onRelabel = { onRelabel(setting.field, it) },
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }

                ThemePicker(theme = theme, onChange = onThemeChange)
                GadgetbridgeToggle(enabled = gadgetbridgeEnabled, onChange = onSetGadgetbridgeEnabled)
            }
        }
    }
}

/**
 * Opt-in hand-off of each reading to Gadgetbridge, which relays it to a paired
 * watch. Last in the list and off by default: it sends data to another app, so
 * it stays something you go and switch on rather than something you discover
 * already running.
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

@Composable
private fun ThemePicker(
    theme: ThemeConfig,
    onChange: (ThemeConfig) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Look", style = MaterialTheme.typography.labelMedium)
        ChipRow(
            options = ThemeMood.entries,
            selected = theme.mood,
            label = { it.label },
            onSelect = { onChange(theme.withMood(it)) },
        )
        AccentChipRow(selected = theme.accent, onSelect = { onChange(theme.withAccent(it)) })
        ChipRow(
            options = TypeChoice.entries,
            selected = theme.type,
            label = { it.label },
            onSelect = { onChange(theme.withType(it)) },
        )
    }
}

/** The accent picker wears its own paint: a selected chip's background is the
 * actual accent colour, so the row doubles as a swatch. Label contrast flips
 * black/white by the colour's luminance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentChipRow(
    selected: AccentChoice,
    onSelect: (AccentChoice) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentChoice.entries.forEach { choice ->
            val swatch = accentColor(choice)
            FilterChip(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                label = { Text(choice.label) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = swatch,
                        // 0.179 is the relative-luminance point where black and
                        // white text have equal WCAG contrast — above it black
                        // wins, below it white does. (0.5 would hand most of
                        // this palette the lower-contrast label.)
                        selectedLabelColor = if (swatch.luminance() > 0.179f) Color.Black else Color.White,
                    ),
            )
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

@Composable
private fun DensityPicker(
    selected: Density,
    onSelect: (Density) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Density", style = MaterialTheme.typography.labelMedium)
        // Shares the one FilterChip loop with the theme rows, so spacing and
        // wrap behaviour can't drift between the two.
        ChipRow(
            options = Density.entries,
            selected = selected,
            label = { it.label },
            onSelect = onSelect,
        )
    }
}

/** Which way the hourly list runs: the side-by-side scroll or stacked rows. */
@Composable
private fun HourlyViewPicker(
    layout: ForecastLayout,
    onSetLayout: (ForecastLayout) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
        Text("Hourly view", style = MaterialTheme.typography.labelMedium)
        ChipRow(
            options = ForecastLayout.entries,
            selected = layout,
            label = { it.label },
            onSelect = onSetLayout,
        )
    }
}

/** How the Daily framing draws: one high/low per day or NWS's day-and-night
 * halves, and side-by-side (the scroll) or stacked rows. */
@Composable
private fun DailyViewPicker(
    style: DailyStyle,
    layout: ForecastLayout,
    onSetStyle: (DailyStyle) -> Unit,
    onSetLayout: (ForecastLayout) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
        Text("Daily view", style = MaterialTheme.typography.labelMedium)
        ChipRow(
            options = DailyStyle.entries,
            selected = style,
            label = { it.label },
            onSelect = onSetStyle,
        )
        ChipRow(
            options = ForecastLayout.entries,
            selected = layout,
            label = { it.label },
            onSelect = onSetLayout,
        )
    }
}

/** Which time framing the home screen opens on. The home toggle can still
 * switch away for the session; this sets where it starts. */
@Composable
private fun DefaultModePicker(
    selected: ViewMode,
    onSelect: (ViewMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
        Text("Opens on", style = MaterialTheme.typography.labelMedium)
        ChipRow(
            options = ViewMode.entries,
            selected = selected,
            label = { it.label },
            onSelect = onSelect,
        )
    }
}

@Composable
private fun FieldRow(
    setting: FieldSetting,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onRelabel: (String?) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Reorder controls. Arrows as text keep us off an icon dependency.
        // testTags (per field key) give UI tests a stable handle on controls
        // that otherwise carry only a glyph or no text.
        val key = setting.field.key
        Column {
            TextButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.testTag("moveUp_$key"),
            ) { Text("↑") }
            TextButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.testTag("moveDown_$key"),
            ) { Text("↓") }
        }
        // Local edit state keyed by field so the cursor stays put while the
        // persisted config streams back in; the field's default name shows as
        // the placeholder, so an empty box clearly means "use the default".
        var label by remember(setting.field) { mutableStateOf(setting.customLabel ?: "") }
        // Debounce persistence: typing only writes to DataStore once the user
        // pauses, instead of a disk write per keystroke. The delay is cancelled
        // and restarted on each change; the guard skips the no-op initial write.
        LaunchedEffect(label) {
            delay(RELABEL_DEBOUNCE_MS)
            val normalized = label.ifBlank { null }
            if (normalized != setting.customLabel) onRelabel(normalized)
        }
        // Flush a still-pending edit if the row leaves composition before the
        // debounce fires — tapping Done or pressing back. Without this, the last
        // keystroke is silently dropped. rememberUpdatedState keeps onDispose
        // reading the latest typed value and the latest persisted label, so the
        // guard compares against the current value and skips an already-saved one.
        val latestLabel by rememberUpdatedState(label)
        val latestSaved by rememberUpdatedState(setting.customLabel)
        DisposableEffect(setting.field) {
            onDispose {
                val normalized = latestLabel.ifBlank { null }
                if (normalized != latestSaved) onRelabel(normalized)
            }
        }
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            placeholder = { Text(setting.field.defaultLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = setting.visible,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag("toggle_$key"),
        )
    }
}
