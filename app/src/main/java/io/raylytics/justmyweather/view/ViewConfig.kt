package io.raylytics.justmyweather.view

/**
 * One field's place in the user's view: whether it shows, and an optional label
 * override. The effective [label] falls back to the field's default.
 */
data class FieldSetting(
    val field: WeatherField,
    val visible: Boolean,
    val customLabel: String? = null,
) {
    val label: String
        // `this.field` is required: a bare `field` inside an accessor is the
        // backing-field keyword, not the constructor property of the same name.
        get() = customLabel?.takeIf { it.isNotBlank() } ?: this.field.defaultLabel
}

/**
 * The user's view as data: an ordered list of every field's setting. Order is
 * the whole point — the first *visible* field is the hero (rendered large), the
 * rest follow as compact rows. Promote a field by moving it up; remove it by
 * hiding it. All edits are pure transforms returning a new config, so the
 * customize screen has no mutable state to get wrong and the logic is testable.
 *
 * The invariant: a ViewConfig always contains exactly one setting per
 * [WeatherField]. [normalized] enforces it, so loading an old or partial config
 * (e.g. after an app update adds a field) yields a complete, valid config.
 */
data class ViewConfig(
    val items: List<FieldSetting>,
    val density: Density = Density.DEFAULT,
    /** Which time framing the home screen opens on; the toggle there can still
     * switch away for the session. */
    val defaultMode: ViewMode = ViewMode.DEFAULT,
    /** How the Daily framing draws each day, and which way its strip runs. */
    val dailyStyle: DailyStyle = DailyStyle.DEFAULT,
    val dailyLayout: DailyLayout = DailyLayout.DEFAULT,
) {
    val visible: List<FieldSetting> get() = items.filter { it.visible }

    fun toggle(field: WeatherField): ViewConfig =
        copy(items = items.map { if (it.field == field) it.copy(visible = !it.visible) else it })

    fun relabel(field: WeatherField, label: String?): ViewConfig =
        copy(items = items.map { if (it.field == field) it.copy(customLabel = label) else it })

    fun setDensity(density: Density): ViewConfig = copy(density = density)

    fun setDefaultMode(mode: ViewMode): ViewConfig = copy(defaultMode = mode)

    fun setDailyStyle(style: DailyStyle): ViewConfig = copy(dailyStyle = style)

    fun setDailyLayout(layout: DailyLayout): ViewConfig = copy(dailyLayout = layout)

    fun moveUp(index: Int): ViewConfig = swap(index, index - 1)

    fun moveDown(index: Int): ViewConfig = swap(index, index + 1)

    private fun swap(a: Int, b: Int): ViewConfig {
        if (a !in items.indices || b !in items.indices) return this
        val next = items.toMutableList()
        next[a] = items[b]
        next[b] = items[a]
        return copy(items = next)
    }

    companion object {
        /** The calm default: temperature is the hero, conditions sits beneath
         * it, everything else is available but hidden. Mirrors the zero-setup
         * glance the app ships with. */
        val DEFAULT =
            ViewConfig(
                listOf(
                    FieldSetting(WeatherField.TEMPERATURE, visible = true),
                    FieldSetting(WeatherField.CONDITIONS, visible = true),
                    FieldSetting(WeatherField.WIND, visible = false),
                    FieldSetting(WeatherField.PRECIPITATION, visible = false),
                    FieldSetting(WeatherField.PRESSURE, visible = false),
                ),
            )

        /**
         * Complete a possibly-partial, possibly-stale list of settings into a
         * valid config: keep recognised settings in their stored order, drop
         * duplicates, then append any field the list is missing (a newly-added
         * data point) as hidden, in catalog order.
         */
        fun normalized(
            settings: List<FieldSetting>,
            density: Density = Density.DEFAULT,
            defaultMode: ViewMode = ViewMode.DEFAULT,
            dailyStyle: DailyStyle = DailyStyle.DEFAULT,
            dailyLayout: DailyLayout = DailyLayout.DEFAULT,
        ): ViewConfig {
            val seen = LinkedHashMap<WeatherField, FieldSetting>()
            settings.forEach { setting -> seen.putIfAbsent(setting.field, setting) }
            WeatherField.entries.forEach { field ->
                seen.putIfAbsent(field, FieldSetting(field, visible = false))
            }
            return ViewConfig(seen.values.toList(), density, defaultMode, dailyStyle, dailyLayout)
        }
    }
}
