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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.view.AccentChoice
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.FieldSetting
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
    theme: ThemeConfig,
    onThemeChange: (ThemeConfig) -> Unit,
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

            DensityPicker(selected = config.density, onSelect = onSetDensity)
            DefaultModePicker(selected = config.defaultMode, onSelect = onSetDefaultMode)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
            }
        }
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
        ChipRow(
            options = AccentChoice.entries,
            selected = theme.accent,
            label = { it.label },
            onSelect = { onChange(theme.withAccent(it)) },
        )
        ChipRow(
            options = TypeChoice.entries,
            selected = theme.type,
            label = { it.label },
            onSelect = { onChange(theme.withType(it)) },
        )
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
