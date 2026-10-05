package io.raylytics.justmyweather.ui.widget

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.ui.customize.ChipRow
import io.raylytics.justmyweather.ui.customize.DensityPicker
import io.raylytics.justmyweather.ui.customize.ForecastPicker
import io.raylytics.justmyweather.ui.customize.SunPicker
import io.raylytics.justmyweather.ui.customize.ThemePicker
import io.raylytics.justmyweather.ui.customize.TimesInPicker
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.widget.WidgetConfig

/**
 * Set up one home screen widget: which module it shows, what it is called,
 * and the glance's options for it — density, the forecast's framing and
 * reach, the sun's days, which clock, the look. The pickers are the Customize
 * screen's own, so the two cannot drift. Size is not here: the launcher
 * resizes a widget, and the widget fits what it shows to what it gets.
 */
@Composable
fun WidgetConfigureScreen(
    config: WidgetConfig,
    onShow: (ModuleKey) -> Unit,
    onRelabel: (String) -> Unit,
    onEditView: ((ViewConfig) -> ViewConfig) -> Unit,
    onThemeChange: (ThemeConfig) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = config.view
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Set up widget", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    TextButton(onClick = onDone, modifier = Modifier.testTag("widget_done")) { Text("Done") }
                }
            }
            Text(
                text = "A widget is one tile of your glance. Pick what it shows; resize it on the " +
                    "home screen like any widget. It starts with your glance's settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Shows", style = MaterialTheme.typography.labelMedium)
                ChipRow(
                    options = ModuleKey.catalog,
                    selected = config.module,
                    label = { it.defaultLabel },
                    onSelect = onShow,
                    tag = { "widget_module_${it.key}" },
                )
                // The name over the tile; blank means the module's own, which
                // the field shows as its placeholder.
                OutlinedTextField(
                    value = config.setting.customLabel ?: "",
                    onValueChange = onRelabel,
                    label = { Text("Label") },
                    placeholder = { Text(config.module.defaultLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                )
                DensityPicker(selected = view.density, onSelect = { d -> onEditView { it.setDensity(d) } })
                TimesInPicker(timesIn = view.timesIn, onSet = { t -> onEditView { it.setTimesIn(t) } })
                ForecastPicker(
                    show = config.module == ModuleKey.Forecast,
                    mode = view.defaultForecastMode,
                    dailyStyle = view.dailyStyle,
                    onSetMode = { m -> onEditView { it.setDefaultForecastMode(m) } },
                    onSetDailyStyle = { s -> onEditView { it.setDailyStyle(s) } },
                    hourlyHours = view.hourlyHours,
                    onSetHourlyHours = { h -> onEditView { it.setHourlyHours(h) } },
                    dailyDays = view.dailyDays,
                    onSetDailyDays = { d -> onEditView { it.setDailyDays(d) } },
                    elements = view.forecastElements,
                    onToggleElement = { e -> onEditView { it.toggleForecastElement(e) } },
                    tileLayout = view.forecastTileLayout,
                    onSetTileLayout = { l -> onEditView { it.setForecastTileLayout(l) } },
                )
                SunPicker(
                    show = config.module == ModuleKey.Sun,
                    sunDays = view.sunDays,
                    onSetSunDays = { d -> onEditView { it.setSunDays(d) } },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                ThemePicker(theme = config.theme, onChange = onThemeChange)
            }
        }
    }
}
