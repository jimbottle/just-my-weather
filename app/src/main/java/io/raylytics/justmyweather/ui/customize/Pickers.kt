package io.raylytics.justmyweather.ui.customize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.ui.theme.accentColor
import io.raylytics.justmyweather.ui.theme.onAccent
import io.raylytics.justmyweather.view.AccentChoice
import io.raylytics.justmyweather.view.DailyDays
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.ForecastElement
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ForecastTileLayout
import io.raylytics.justmyweather.view.HourlyHours
import io.raylytics.justmyweather.view.SunDays
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ThemeMood
import io.raylytics.justmyweather.view.TimesIn
import io.raylytics.justmyweather.view.TypeChoice

/*
 * The option pickers the Customize screen is built from, shared with the
 * widget's configure screen. A widget is one tile of the glance with the
 * glance's options, so it is set up with the glance's controls — the same
 * chips, sliders and switches, not a second set that would drift. Internal,
 * not private to a screen, for exactly that reason.
 */

@Composable
internal fun ThemePicker(
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
internal fun AccentChipRow(
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
                        selectedLabelColor = onAccent(swatch),
                    ),
            )
        }
    }
}

/** [ChipRow]'s many-of-these twin: each chip toggles on its own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> MultiChipRow(
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    tag: (T) -> String,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = { Text(label(option)) },
                modifier = Modifier.testTag(tag(option)),
            )
        }
    }
}

/** The slider's own stops are fours; a float from it becomes the nearest one. */
internal fun snapToStep(value: Float): Int {
    val stepped = Math.round((value - HourlyHours.MIN) / HourlyHours.STEP) * HourlyHours.STEP + HourlyHours.MIN
    return HourlyHours.clamp(stepped)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    /** Optional stable testTag per chip; chips carry no other stable handle. */
    tag: ((T) -> String)? = null,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                modifier = tag?.let { Modifier.testTag(it(option)) } ?: Modifier,
            )
        }
    }
}

@Composable
internal fun DensityPicker(
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

/** One switch: which clock the screen's times read in. Off is the phone's;
 * on is the place's, for planning a day somewhere else. */
@Composable
internal fun TimesInPicker(
    timesIn: TimesIn,
    onSet: (TimesIn) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Show times in the place's local time", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Off: the forecast hours, sun times and observed time read in this phone's clock",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = timesIn == TimesIn.PLACE,
            onCheckedChange = { onSet(if (it) TimesIn.PLACE else TimesIn.DEVICE) },
            modifier = Modifier.testTag("times-in-toggle"),
        )
    }
}

/**
 * How many days the sun module's table shows. Like the forecast's options,
 * drawn only while the module is on — a slider for a hidden tile is a
 * control that does nothing. The tile scrolls past two days, so the slider
 * sets the horizon, not the tile's size.
 */
@Composable
internal fun SunPicker(
    show: Boolean,
    sunDays: Int,
    onSetSunDays: (Int) -> Unit,
) {
    if (!show) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Sun times", style = MaterialTheme.typography.labelMedium)
        Text(
            text = if (sunDays == 1) "Sun shows 1 day" else "Sun shows $sunDays days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Slider(
            value = sunDays.toFloat(),
            onValueChange = { onSetSunDays(Math.round(it)) },
            valueRange = SunDays.MIN.toFloat()..SunDays.MAX.toFloat(),
            steps = SunDays.MAX - SunDays.MIN - 1,
            modifier = Modifier.testTag("sun-days-slider"),
        )
    }
}

@Composable
internal fun ForecastPicker(
    /** Whether the forecast module is on — switched in the field list below,
     * like every other module. */
    show: Boolean,
    mode: ForecastMode,
    dailyStyle: DailyStyle,
    onSetMode: (ForecastMode) -> Unit,
    onSetDailyStyle: (DailyStyle) -> Unit,
    hourlyHours: Int,
    onSetHourlyHours: (Int) -> Unit,
    dailyDays: Int,
    onSetDailyDays: (Int) -> Unit,
    elements: Set<ForecastElement>,
    onToggleElement: (ForecastElement) -> Unit,
    tileLayout: ForecastTileLayout,
    onSetTileLayout: (ForecastTileLayout) -> Unit,
) {
    // The framing options only mean something when there is a forecast to
    // frame; offered for a hidden module they read as controls that do
    // nothing. Nothing at all is drawn then — the module's own row in the
    // list below is where it is switched back on.
    if (!show) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Forecast", style = MaterialTheme.typography.labelMedium)
        Text(
            text = "Opens on",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = ForecastMode.entries,
            selected = mode,
            label = { it.label },
            onSelect = onSetMode,
            tag = { "forecast_default_${it.key}" },
        )
        // How far the Hourly framing runs. A slider because the range is
        // wide and continuous in the user's head ("a couple of days"), where
        // chips would have to pick the few values that matter for them. It
        // moves in fours and says its value in words, since a thumb on a
        // range of 164 cannot be read from the track alone.
        Text(
            text = "Hourly shows $hourlyHours hours",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Snapped in code rather than with the slider's own `steps`: forty
        // stops draw forty tick marks along the track, which on the phone
        // read as a dotted line rather than a control.
        Slider(
            value = hourlyHours.toFloat(),
            onValueChange = { onSetHourlyHours(snapToStep(it)) },
            valueRange = HourlyHours.MIN.toFloat()..HourlyHours.MAX.toFloat(),
            modifier = Modifier.testTag("hourly-hours-slider"),
        )
        // The Daily framing's reach, in days. Seven stops is few enough for
        // the slider's own ticks to read as stops rather than a dotted line.
        Text(
            text = if (dailyDays == 1) "Daily shows 1 day" else "Daily shows $dailyDays days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Slider(
            value = dailyDays.toFloat(),
            onValueChange = { onSetDailyDays(Math.round(it)) },
            valueRange = DailyDays.MIN.toFloat()..DailyDays.MAX.toFloat(),
            steps = DailyDays.MAX - DailyDays.MIN - 1,
            modifier = Modifier.testTag("daily-days-slider"),
        )
        // What a tile carries beside its temperature. Multi-select: these
        // are independent, and a tile with none of them is a legal, calm
        // choice — the hour and the number.
        Text(
            text = "Tiles show",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        MultiChipRow(
            options = ForecastElement.entries,
            selected = elements,
            label = { it.label },
            onToggle = onToggleElement,
            tag = { "forecast_element_${it.key}" },
        )
        // How a tile arranges what it shows: zones pinned to the edges so a
        // row reads straight across, or one block in the middle.
        Text(
            text = "Tile layout",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        ChipRow(
            options = ForecastTileLayout.entries,
            selected = tileLayout,
            label = { it.label },
            onSelect = onSetTileLayout,
            tag = { "tile_layout_${it.key}" },
        )
        Text(
            text = "Each day shows",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        ChipRow(
            options = DailyStyle.entries,
            selected = dailyStyle,
            label = { it.label },
            onSelect = onSetDailyStyle,
            tag = { "daily_style_${it.key}" },
        )
    }
}
