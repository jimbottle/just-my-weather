package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ModuleSetting
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.WeatherField

/**
 * One home screen widget's settings: a glance with exactly one module on it,
 * and the look it is drawn in.
 *
 * A widget is a tile of the glance, not a second kind of thing — so its
 * options ARE a [ViewConfig], with everything but the chosen module hidden,
 * plus a [ThemeConfig]. That buys the whole customization layer for free: the
 * same transforms edit it, the same codec persists it, `ViewConfig.render`
 * projects weather through it, and the Customize screen's pickers drive it
 * unchanged. The only thing a widget decides for itself is which module it
 * shows; its size is the launcher's.
 *
 * Pure data, no Android types: it persists as JSON and tests on the JVM.
 */
data class WidgetConfig(
    val view: ViewConfig,
    val theme: ThemeConfig,
) {
    /**
     * The module this widget draws. [ViewConfig.showingOnly] keeps exactly
     * one visible; the temperature stands in if a stored config somehow has
     * none, because a widget that draws nothing is worse than one that draws
     * the default.
     */
    val setting: ModuleSetting
        get() = view.visible.firstOrNull() ?: ModuleSetting(DEFAULT_MODULE, visible = true)

    val module: ModuleKey get() = setting.module

    fun show(module: ModuleKey): WidgetConfig = copy(view = view.showingOnly(module))

    fun editView(transform: (ViewConfig) -> ViewConfig): WidgetConfig = copy(view = transform(view))

    fun withTheme(theme: ThemeConfig): WidgetConfig = copy(theme = theme)

    companion object {
        val DEFAULT_MODULE: ModuleKey = ModuleKey.Reading(WeatherField.TEMPERATURE)

        /**
         * A new widget starts as the glance the user already built: its
         * density, framing, labels and look, showing the first module on the
         * glance — the hero, for anyone who kept the default layout (Evan,
         * 2026-10-05: "have it start with the current glance settings").
         */
        fun seededFrom(glance: ViewConfig, theme: ThemeConfig): WidgetConfig =
            WidgetConfig(
                view = glance.showingOnly(glance.visible.firstOrNull()?.module ?: DEFAULT_MODULE),
                theme = theme,
            )
    }
}
