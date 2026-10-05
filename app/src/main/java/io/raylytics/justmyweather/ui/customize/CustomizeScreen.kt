package io.raylytics.justmyweather.ui.customize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import io.raylytics.justmyweather.view.AlertBannerPosition
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.ForecastElement
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ForecastTileLayout
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ModuleSetting
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.TimesIn
import io.raylytics.justmyweather.view.ViewConfig
import kotlinx.coroutines.delay

private const val RELABEL_DEBOUNCE_MS = 400L

/**
 * The customization layer: pick which data points appear, set how wide each
 * sits on the glance grid, reorder and relabel them. Edits persist immediately
 * and the home view reflects them live. Deliberately a plain list — the power
 * is in editing down, not in a wall of controls. Everything here is also the
 * grid's non-gesture path: the arrows and width chips do what arrange mode's
 * drag and tap do, for anyone who can't or won't long-press
 * (docs/modular-v2-evaluation.md, criterion 3).
 */
@Composable
fun CustomizeScreen(
    config: ViewConfig,
    onToggle: (ModuleKey) -> Unit,
    onRelabel: (ModuleKey, String?) -> Unit,
    onResize: (ModuleKey, ModuleSize) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onSetDensity: (Density) -> Unit,
    onSetTapForDetails: (Boolean) -> Unit,
    onSetTimesIn: (TimesIn) -> Unit,
    onSetDefaultForecastMode: (ForecastMode) -> Unit,
    onSetDailyStyle: (DailyStyle) -> Unit,
    onSetHourlyHours: (Int) -> Unit,
    onSetDailyDays: (Int) -> Unit,
    onSetSunDays: (Int) -> Unit,
    onToggleForecastElement: (ForecastElement) -> Unit,
    onSetForecastTileLayout: (ForecastTileLayout) -> Unit,
    onSetAlertBannerPosition: (AlertBannerPosition) -> Unit,
    theme: ThemeConfig,
    onThemeChange: (ThemeConfig) -> Unit,
    onSubmitIdea: () -> Unit,
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
                text = "Show the fields you care about, in the order you read them. Size is " +
                    "prominence: the widest, tallest tile is the big one. You can also " +
                    "long-press any tile on the glance to drag and resize it in place.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Everything below the header scrolls as one page — the option
            // sections have outgrown a fixed header on small screens.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DensityPicker(selected = config.density, onSelect = onSetDensity)
                TapForDetailsPicker(enabled = config.tapForDetails, onSet = onSetTapForDetails)
                TimesInPicker(timesIn = config.timesIn, onSet = onSetTimesIn)
                ForecastPicker(
                    show = config.shows(ModuleKey.Forecast),
                    mode = config.defaultForecastMode,
                    dailyStyle = config.dailyStyle,
                    onSetMode = onSetDefaultForecastMode,
                    onSetDailyStyle = onSetDailyStyle,
                    hourlyHours = config.hourlyHours,
                    onSetHourlyHours = onSetHourlyHours,
                    dailyDays = config.dailyDays,
                    onSetDailyDays = onSetDailyDays,
                    elements = config.forecastElements,
                    onToggleElement = onToggleForecastElement,
                    tileLayout = config.forecastTileLayout,
                    onSetTileLayout = onSetForecastTileLayout,
                )
                SunPicker(show = config.shows(ModuleKey.Sun), sunDays = config.sunDays, onSetSunDays = onSetSunDays)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                config.items.forEachIndexed { index, setting ->
                    FieldRow(
                        setting = setting,
                        canMoveUp = index > 0,
                        canMoveDown = index < config.items.lastIndex,
                        onToggle = { onToggle(setting.module) },
                        onRelabel = { onRelabel(setting.module, it) },
                        onResize = { onResize(setting.module, it) },
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }

                ThemePicker(theme = theme, onChange = onThemeChange)
                // Below the field list, with the other set-once settings: the
                // fields are what people come here to edit, and putting a
                // rarely-touched picker above them pushed the list off the
                // first screenful.
                AlertBannerPicker(
                    position = config.alertBannerPosition,
                    onSelect = onSetAlertBannerPosition,
                )
                IdeaPrompt(onSubmitIdea = onSubmitIdea)
            }
        }
    }
}

/**
 * The last thing on the page, where someone who scrolled the whole list
 * looking for an option lands when it isn't there. Opens the idea form — not
 * the bug report: a missing feature is a wish, and it travels without the
 * diagnostics a bug needs.
 */
@Composable
private fun IdeaPrompt(onSubmitIdea: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Couldn't find what you're looking for?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onSubmitIdea) { Text("Submit your idea to the developer.") }
    }
}

/**
 * Where a safety-alert banner sits. Worth a control even though most users see
 * it rarely: when a tornado warning does appear, whether it lands above or
 * below the temperature is exactly the kind of thing people have a firm
 * opinion about.
 */
@Composable
private fun AlertBannerPicker(
    position: AlertBannerPosition,
    onSelect: (AlertBannerPosition) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Safety alerts", style = MaterialTheme.typography.labelMedium)
        ChipRow(
            options = AlertBannerPosition.entries,
            selected = position,
            label = { it.label },
            onSelect = onSelect,
            tag = { "banner_${it.key}" },
        )
        Text(
            text = "Tornado, severe storm, hurricane, dangerous heat and air quality warnings. " +
                "Only shown while one is active.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One labelled row of cell counts: "Width  1 2 3 4". */
@Composable
private fun SizeChips(
    title: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tag: (Int) -> String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = options,
            selected = selected,
            label = { it.toString() },
            onSelect = onSelect,
            tag = tag,
        )
    }
}

/** One switch: whether a tap on a tile opens the sheet with everything behind
 * it. On by default, because the sheet is where the fields a tile has no
 * room for live; off for a screen that should stay inert. */
@Composable
private fun TapForDetailsPicker(
    enabled: Boolean,
    onSet: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Tap a tile for details", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "The whole observation behind a reading, every field of a forecast hour",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onSet,
            modifier = Modifier.testTag("tap-for-details-toggle"),
        )
    }
}

/**
 * Everything about the forecast module, in one block: whether it appears, which
 * framing it opens on, and how a day is drawn.
 *
 * These used to be three separate sections — "Opens on", "Hourly view", "Daily
 * view" — because the framing was a property of the whole screen. It is a
 * property of the forecast now, so its settings sit together, and the two
 * "which direction does the list run" pickers are gone entirely: the grid flows
 * its tiles, so there is no longer a direction to choose.
 */

@Composable
private fun FieldRow(
    setting: ModuleSetting,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onRelabel: (String?) -> Unit,
    onResize: (ModuleSize) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    // testTags (per field key) give UI tests a stable handle on controls
    // that otherwise carry only a glyph or no text.
    val key = setting.module.key
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Reorder controls. Arrows as text keep us off an icon dependency.
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
            var label by remember(setting.module) { mutableStateOf(setting.customLabel ?: "") }
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
            DisposableEffect(setting.module) {
                onDispose {
                    val normalized = latestLabel.ifBlank { null }
                    if (normalized != latestSaved) onRelabel(normalized)
                }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = { Text(setting.module.defaultLabel) },
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
        // Footprint on the glance grid — the non-gesture twin of dragging the
        // tile's corner in arrange mode. Only for shown fields: a hidden field
        // has no tile for the size to describe. Each row offers only the
        // counts the module can fill: a phrase's width chips start at 2.
        if (setting.visible) {
            val min = setting.module.minSize
            SizeChips(
                title = "Width",
                options = (min.columns..ModuleSize.COLUMNS).toList(),
                selected = setting.size.columns,
                onSelect = { onResize(setting.size.copy(columns = it)) },
                tag = { "width_${key}_$it" },
            )
            SizeChips(
                title = "Height",
                options = (min.rows..ModuleSize.MAX_ROWS).toList(),
                selected = setting.size.rows,
                onSelect = { onResize(setting.size.copy(rows = it)) },
                tag = { "height_${key}_$it" },
            )
        }
    }
}
