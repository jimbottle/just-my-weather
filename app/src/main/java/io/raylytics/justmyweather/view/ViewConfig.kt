package io.raylytics.justmyweather.view

/**
 * One field's place in the user's view: whether it shows, an optional label
 * override, and how wide its module sits on the grid. The effective [label]
 * falls back to the field's default.
 */
data class FieldSetting(
    val field: WeatherField,
    val visible: Boolean,
    val customLabel: String? = null,
    val span: ModuleSpan = field.defaultSpan,
) {
    val label: String
        // `this.field` is required: a bare `field` inside an accessor is the
        // backing-field keyword, not the constructor property of the same name.
        get() = customLabel?.takeIf { it.isNotBlank() } ?: this.field.defaultLabel
}

/**
 * The user's view as data: an ordered list of every field's setting. The
 * visible fields flow onto the glance's grid in this order, each as wide as its
 * [FieldSetting.span] — size is what makes a module prominent, so promote a
 * field by widening it or moving it up; remove it by hiding it. All edits are
 * pure transforms returning a new config, so the customize screen and the
 * arrange gesture have no mutable state to get wrong and the logic is testable.
 *
 * The invariant: a ViewConfig always contains exactly one setting per
 * [WeatherField]. [normalized] enforces it, so loading an old or partial config
 * (e.g. after an app update adds a field) yields a complete, valid config.
 */
data class ViewConfig(
    val items: List<FieldSetting>,
    val density: Density = Density.DEFAULT,
    /**
     * Whether the forecast grid appears beneath the glance at all. On by
     * default, which is what the app has always shown; turning it off is the
     * calm minimum — just the glance — and is what the old NOW view mode meant.
     */
    val showForecast: Boolean = true,
    /** Which framing the forecast grid opens on; its toggle can still switch
     * away for the session. */
    val defaultForecastMode: ForecastMode = ForecastMode.DEFAULT,
    /** How the Daily framing draws each period. */
    val dailyStyle: DailyStyle = DailyStyle.DEFAULT,
    /** Where a safety-alert banner sits on the days there is one. */
    val alertBannerPosition: AlertBannerPosition = AlertBannerPosition.DEFAULT,
    /**
     * Whether the next sunrise and sunset show on the glance. Off by default,
     * like every other addition: the shipped view is the calm minimum, and
     * anything extra is something you go and turn on.
     */
    val showSunTimes: Boolean = false,
) {
    val visible: List<FieldSetting> get() = items.filter { it.visible }

    fun toggle(field: WeatherField): ViewConfig =
        copy(items = items.map { if (it.field == field) it.copy(visible = !it.visible) else it })

    fun relabel(field: WeatherField, label: String?): ViewConfig =
        copy(items = items.map { if (it.field == field) it.copy(customLabel = label) else it })

    fun setSpan(field: WeatherField, span: ModuleSpan): ViewConfig =
        copy(items = items.map { if (it.field == field) it.copy(span = span) else it })

    /** Step the field's module to the next size — what tapping a wiggling tile
     * does in arrange mode. */
    fun cycleSpan(field: WeatherField): ViewConfig =
        copy(items = items.map { if (it.field == field) it.copy(span = it.span.next()) else it })

    /**
     * Move a visible field so it lands at [toVisibleIndex] among the *visible*
     * fields — the drop half of the drag gesture, which only ever sees the
     * modules actually on the grid. Hidden settings keep their relative order;
     * an unknown field or an out-of-range target clamps to a safe no-op rather
     * than corrupting the list.
     */
    fun moveVisible(field: WeatherField, toVisibleIndex: Int): ViewConfig {
        val visibleFields = visible.map { it.field }
        val from = visibleFields.indexOf(field)
        if (from == -1 || visibleFields.size < 2) return this
        val target = toVisibleIndex.coerceIn(0, visibleFields.lastIndex)
        if (target == from) return this
        val moving = items.first { it.field == field }
        val without = items.filterNot { it.field == field }
        val remainingVisible = without.filter { it.visible }
        // Insert before the setting that currently holds the target slot; past
        // the last slot means "after the final visible setting".
        val insertAt =
            if (target < remainingVisible.size) {
                without.indexOf(remainingVisible[target])
            } else {
                without.indexOfLast { it.visible } + 1
            }
        val next = without.toMutableList()
        next.add(insertAt, moving)
        return copy(items = next)
    }

    fun setDensity(density: Density): ViewConfig = copy(density = density)

    fun setShowForecast(show: Boolean): ViewConfig = copy(showForecast = show)

    fun setDefaultForecastMode(mode: ForecastMode): ViewConfig = copy(defaultForecastMode = mode)

    fun setDailyStyle(style: DailyStyle): ViewConfig = copy(dailyStyle = style)

    fun setAlertBannerPosition(position: AlertBannerPosition): ViewConfig =
        copy(alertBannerPosition = position)

    fun setShowSunTimes(show: Boolean): ViewConfig = copy(showSunTimes = show)

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
            showForecast: Boolean = true,
            defaultForecastMode: ForecastMode = ForecastMode.DEFAULT,
            dailyStyle: DailyStyle = DailyStyle.DEFAULT,
            alertBannerPosition: AlertBannerPosition = AlertBannerPosition.DEFAULT,
            showSunTimes: Boolean = false,
        ): ViewConfig {
            val seen = LinkedHashMap<WeatherField, FieldSetting>()
            settings.forEach { setting -> seen.putIfAbsent(setting.field, setting) }
            WeatherField.entries.forEach { field ->
                seen.putIfAbsent(field, FieldSetting(field, visible = false))
            }
            return ViewConfig(
                seen.values.toList(),
                density,
                showForecast,
                defaultForecastMode,
                dailyStyle,
                alertBannerPosition,
                showSunTimes,
            )
        }
    }
}
