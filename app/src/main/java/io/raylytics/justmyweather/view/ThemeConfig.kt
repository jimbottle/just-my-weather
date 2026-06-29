package io.raylytics.justmyweather.view

/*
 * The look & feel choices, kept as pure, persistable data — no Compose types,
 * so it serialises cleanly and tests on the JVM. The theme layer maps each
 * choice to a concrete colour scheme / type scale in one place (mirroring how
 * Density maps to a DensitySpec), so the choice stays in the data and the
 * pixels stay in the UI.
 *
 * Every default matches the shipped look, so a fresh install — and any config
 * written before theming existed — renders exactly as before.
 */

/** Light, dark, or follow the system. */
enum class ThemeMood(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    ;

    companion object {
        fun byKey(key: String): ThemeMood? = entries.firstOrNull { it.key == key }

        val DEFAULT = SYSTEM
    }
}

/**
 * The single warm-ish accent — the only colour in the near-monochrome palette.
 * [AMBER] is the shipped sun-in-the-icon accent.
 */
enum class AccentChoice(val key: String, val label: String) {
    AMBER("amber", "Amber"),
    TANGERINE("tangerine", "Tangerine"),
    ROSE("rose", "Rose"),
    SKY("sky", "Sky"),
    SAGE("sage", "Sage"),
    VIOLET("violet", "Violet"),
    ;

    companion object {
        fun byKey(key: String): AccentChoice? = entries.firstOrNull { it.key == key }

        val DEFAULT = AMBER
    }
}

/** Typeface family for the whole scale. [SANS] is the shipped face. */
enum class TypeChoice(val key: String, val label: String) {
    SANS("sans", "Sans"),
    SERIF("serif", "Serif"),
    MONO("mono", "Mono"),
    ;

    companion object {
        fun byKey(key: String): TypeChoice? = entries.firstOrNull { it.key == key }

        val DEFAULT = SANS
    }
}

data class ThemeConfig(
    val mood: ThemeMood = ThemeMood.DEFAULT,
    val accent: AccentChoice = AccentChoice.DEFAULT,
    val type: TypeChoice = TypeChoice.DEFAULT,
) {
    fun withMood(mood: ThemeMood): ThemeConfig = copy(mood = mood)

    fun withAccent(accent: AccentChoice): ThemeConfig = copy(accent = accent)

    fun withType(type: TypeChoice): ThemeConfig = copy(type = type)

    companion object {
        val DEFAULT = ThemeConfig()
    }
}
