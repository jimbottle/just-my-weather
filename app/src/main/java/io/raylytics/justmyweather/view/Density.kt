package io.raylytics.justmyweather.view

/**
 * Where the user sits on the glance's density spectrum, from a single big number
 * to a fuller dashboard. This is the *semantic* choice only — it carries no
 * Compose types or dp values, so it stays pure and persists cleanly. The home
 * view maps each level to concrete sizes/spacing in one place (its DensitySpec),
 * keeping the look in the UI layer and the choice in the data.
 *
 * [SPACIOUS] is the calmest end: the largest hero and the most generous
 * spacing. [COMPACT] is the dashboard end: a smaller hero and tight rows so
 * more fits at a glance. [COMFORTABLE] is the shipped middle, so the default
 * install looks exactly as it always has.
 *
 * [key] is the stable persistence token — never rename it. [label] is the chip
 * text in the customize screen.
 */
enum class Density(val key: String, val label: String) {
    SPACIOUS("spacious", "Spacious"),
    COMFORTABLE("comfortable", "Comfortable"),
    COMPACT("compact", "Compact"),
    ;

    // There was a `showsTimestamp` here, false for SPACIOUS, which hid the
    // observation time at the calmest end. It is gone: the time is now part of
    // the "Observed …" provenance label beside the hero, and it has to show at
    // every density — a reading whose source and age are undiscoverable is
    // what made the hero and the forecast strip look like a contradiction.

    companion object {
        fun byKey(key: String): Density? = entries.firstOrNull { it.key == key }

        /** The shipped middle — the look the app has always had. */
        val DEFAULT = COMFORTABLE
    }
}
