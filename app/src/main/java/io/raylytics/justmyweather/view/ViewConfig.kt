package io.raylytics.justmyweather.view

/**
 * One module's place in the user's view: whether it shows, an optional label
 * override, and how much of the grid it occupies. The effective [label] falls
 * back to the module's default.
 */
data class ModuleSetting(
    val module: ModuleKey,
    val visible: Boolean,
    val customLabel: String? = null,
    val size: ModuleSize = module.defaultSize,
) {
    val label: String
        get() = customLabel?.takeIf { it.isNotBlank() } ?: module.defaultLabel
}

/**
 * The user's view as data: an ordered list of every module's setting. The
 * visible modules pack onto the glance's grid in this order, each at its
 * [ModuleSetting.size] — size is what makes a module prominent, so promote
 * one by enlarging it or moving it up; remove it by hiding it. All edits are
 * pure transforms returning a new config, so the customize screen and the
 * arrange gesture have no mutable state to get wrong and the logic is testable.
 *
 * The invariant: a ViewConfig always contains exactly one setting per entry in
 * [ModuleKey.catalog]. [normalized] enforces it, so loading an old or partial
 * config (e.g. after an app update adds a module) yields a complete, valid one.
 */
data class ViewConfig(
    val items: List<ModuleSetting>,
    val density: Density = Density.DEFAULT,
    /** Which framing the forecast module opens on; its toggle can still
     * switch away for the session. Whether the forecast shows at all is the
     * module's own visibility, like every other tile's. */
    val defaultForecastMode: ForecastMode = ForecastMode.DEFAULT,
    /** How the Daily framing draws each period. */
    val dailyStyle: DailyStyle = DailyStyle.DEFAULT,
    /** Where a safety-alert banner sits on the days there is one. */
    val alertBannerPosition: AlertBannerPosition = AlertBannerPosition.DEFAULT,
    /**
     * Whether a tap on a tile opens its detail sheet — the whole observation
     * behind a reading, every field of a forecast hour. On by default: the
     * detail is where the fields a tile has no room for live, and a glance
     * that cannot be opened is a glance that hides them. Off is for the user
     * who wants the screen inert.
     */
    val tapForDetails: Boolean = true,
) {
    val visible: List<ModuleSetting> get() = items.filter { it.visible }

    /** Whether a module is on the glance — the question the ViewModel asks
     * before working out data only that module needs. */
    fun shows(module: ModuleKey): Boolean = items.any { it.module == module && it.visible }

    fun toggle(module: ModuleKey): ViewConfig =
        copy(items = items.map { if (it.module == module) it.copy(visible = !it.visible) else it })

    fun setVisible(module: ModuleKey, visible: Boolean): ViewConfig =
        copy(items = items.map { if (it.module == module) it.copy(visible = visible) else it })

    fun relabel(module: ModuleKey, label: String?): ViewConfig =
        copy(items = items.map { if (it.module == module) it.copy(customLabel = label) else it })

    /**
     * Give a module a new footprint — what the corner drag, the Wider/Taller
     * accessibility actions and the customize screen's chips all do. Clamped
     * to the module's own minimum and the lattice, so no caller can request a
     * size the module cannot fill; the drag in particular asks for whatever
     * rectangle the finger draws and relies on this to say no.
     */
    fun resize(module: ModuleKey, size: ModuleSize): ViewConfig =
        copy(items = items.map { if (it.module == module) it.copy(size = size.clamp(module.minSize)) else it })

    /**
     * Move a visible module so it lands at [toVisibleIndex] among the *visible*
     * modules — the drop half of the drag gesture, which only ever sees the
     * tiles actually on the grid. Hidden settings keep their relative order; an
     * unknown module or an out-of-range target clamps to a safe no-op rather
     * than corrupting the list.
     */
    fun moveVisible(module: ModuleKey, toVisibleIndex: Int): ViewConfig {
        val visibleModules = visible.map { it.module }
        val from = visibleModules.indexOf(module)
        if (from == -1 || visibleModules.size < 2) return this
        val target = toVisibleIndex.coerceIn(0, visibleModules.lastIndex)
        if (target == from) return this
        val moving = items.first { it.module == module }
        val without = items.filterNot { it.module == module }
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

    fun setDefaultForecastMode(mode: ForecastMode): ViewConfig = copy(defaultForecastMode = mode)

    fun setDailyStyle(style: DailyStyle): ViewConfig = copy(dailyStyle = style)

    fun setAlertBannerPosition(position: AlertBannerPosition): ViewConfig =
        copy(alertBannerPosition = position)

    fun setTapForDetails(enabled: Boolean): ViewConfig = copy(tapForDetails = enabled)

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
         * it, the forecast under that, everything else is available but
         * hidden. Mirrors the zero-setup glance the app ships with. */
        val DEFAULT =
            ViewConfig(
                ModuleKey.catalog.map { module ->
                    ModuleSetting(
                        module = module,
                        // Temperature, conditions and the forecast are the
                        // shipped glance; everything else is available but
                        // something you go and turn on.
                        visible =
                            module == ModuleKey.Reading(WeatherField.TEMPERATURE) ||
                                module == ModuleKey.Reading(WeatherField.CONDITIONS) ||
                                module == ModuleKey.Forecast,
                    )
                },
            )

        /**
         * Complete a possibly-partial, possibly-stale list of settings into a
         * valid config: keep recognised settings in their stored order, drop
         * duplicates, then append any module the list is missing (a newly-added
         * data point, or one that did not exist in an older build) as hidden,
         * in catalog order.
         */
        fun normalized(
            settings: List<ModuleSetting>,
            density: Density = Density.DEFAULT,
            defaultForecastMode: ForecastMode = ForecastMode.DEFAULT,
            dailyStyle: DailyStyle = DailyStyle.DEFAULT,
            alertBannerPosition: AlertBannerPosition = AlertBannerPosition.DEFAULT,
            tapForDetails: Boolean = true,
        ): ViewConfig {
            val seen = LinkedHashMap<ModuleKey, ModuleSetting>()
            settings.forEach { setting -> seen.putIfAbsent(setting.module, setting) }
            ModuleKey.catalog.forEach { module ->
                seen.putIfAbsent(module, ModuleSetting(module, visible = false))
            }
            return ViewConfig(
                seen.values.toList(),
                density,
                defaultForecastMode,
                dailyStyle,
                alertBannerPosition,
                tapForDetails,
            )
        }
    }
}
