package io.raylytics.justmyweather.ui.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.data.WidgetConfigRepository
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.widget.WidgetConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Backs the widget's configure screen. Unlike the Customize screen, edits
 * are held in memory and written once on Done: the launcher only places the
 * widget when the activity reports success, so a config saved per keystroke
 * would outlive a cancelled placement. Null until the stored config — or,
 * for a new widget, the glance-seeded one — has loaded.
 */
class WidgetConfigureViewModel(
    private val widgetId: Int,
    private val repository: WidgetConfigRepository,
    /** A fresh config for a widget with none saved: the glance's settings. */
    private val seed: suspend () -> WidgetConfig,
) : ViewModel() {
    private val editable = MutableStateFlow<WidgetConfig?>(null)
    val config: StateFlow<WidgetConfig?> = editable

    init {
        viewModelScope.launch { editable.value = repository.get(widgetId) ?: seed() }
    }

    fun show(module: ModuleKey) = edit { it.show(module) }

    /** A blank name means the module's own. */
    fun relabel(
        label: String,
    ) = edit { config -> config.editView { it.relabel(config.module, label.ifBlank { null }) } }

    fun editView(transform: (ViewConfig) -> ViewConfig) = edit { it.editView(transform) }

    fun setTheme(theme: ThemeConfig) = edit { it.withTheme(theme) }

    /** Persist the edits; the saved config, or null if nothing had loaded. */
    suspend fun save(): WidgetConfig? = editable.value?.also { repository.save(widgetId, it) }

    private fun edit(transform: (WidgetConfig) -> WidgetConfig) {
        editable.value = editable.value?.let(transform)
    }
}
