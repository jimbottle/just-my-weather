package io.raylytics.justmyweather.view

/**
 * The catalog of modules that can sit on the glance grid.
 *
 * This exists because not every module is a station reading. [Reading] wraps
 * the [WeatherField] catalog — a number or a phrase measured at a station —
 * while [Sun] is worked out on the device from the date and where you are, has
 * no threshold you could sensibly alert on, and draws a table rather than a
 * value. Forcing it into `WeatherField` would have bent that enum's contract
 * ("how to render its value from a WeatherSnapshot") and put a nonsense entry
 * in the alert builder, which iterates `WeatherField` on purpose.
 *
 * **To add a module:** add a subtype here and a branch wherever the compiler
 * points — the `when`s over this type are exhaustive with no `else`. A module
 * that is just another station reading needs nothing here at all: add it to
 * `WeatherField` and it appears through [Reading].
 *
 * [key] is the stable persistence token — never rename one once shipped.
 */
sealed interface ModuleKey {
    val key: String
    val defaultLabel: String
    val defaultSpan: ModuleSpan

    /** A reading from the weather station: temperature, wind, conditions… */
    data class Reading(val field: WeatherField) : ModuleKey {
        // `this.field` throughout: a bare `field` inside an accessor is
        // Kotlin's backing-field keyword, not this constructor property — the
        // same trap docs/extending.md warns about for FieldSetting.label.
        override val key: String get() = this.field.key
        override val defaultLabel: String get() = this.field.defaultLabel
        override val defaultSpan: ModuleSpan get() = this.field.defaultSpan
    }

    /** Sunrise and sunset. Computed, not fetched, so it works with no signal. */
    data object Sun : ModuleKey {
        override val key: String get() = "sun"
        override val defaultLabel: String get() = "Sun"

        /** Full width by default: at that size the module draws the two-day
         * table, which is the form that answers "which sunrise?" without
         * hanging a "tomorrow" off a time. Shrink it and it condenses. */
        override val defaultSpan: ModuleSpan get() = ModuleSpan.FULL
    }

    companion object {
        /** Every module, in the order a fresh config lists them: the station's
         * readings first, then the computed extras. */
        val catalog: List<ModuleKey> = WeatherField.entries.map(::Reading) + Sun

        fun byKey(key: String): ModuleKey? = catalog.firstOrNull { it.key == key }
    }
}

/** The field behind a [ModuleKey.Reading], or null for a module that is not
 * one. Saves callers a `when` when all they want is "is this a reading?". */
val ModuleKey.field: WeatherField?
    get() = (this as? ModuleKey.Reading)?.field
