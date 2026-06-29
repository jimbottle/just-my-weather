package io.raylytics.justmyweather.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * A deliberately small palette. The resting state is near-monochrome so the one
 * number the user came to read carries all the weight; a single warm accent is
 * the only colour that earns its place. The customization layer will let users
 * swap these, so they live in one obvious file.
 */

// Light
val InkLight = Color(0xFF1A1C1E)
val MutedLight = Color(0xFF5B5F66)
val SurfaceLight = Color(0xFFFCFCFD)
val FaintLight = Color(0xFFEDEEF1)

// Dark
val InkDark = Color(0xFFECEDEF)
val MutedDark = Color(0xFF9DA2AA)
val SurfaceDark = Color(0xFF0E1115)
val FaintDark = Color(0xFF1B1F25)

// Shared warm accent — the sun in the icon. The default of the accent palette
// below; the customization layer lets the user pick another.
val Accent = Color(0xFFF2B705)

// The accent palette the customization layer offers. Each is a single saturated
// hue meant to read against the near-monochrome surface, kept muted enough to
// stay calm. Mapped from AccentChoice in Theme.kt.
val AccentTangerine = Color(0xFFEC6A1E)
val AccentRose = Color(0xFFE5436B)
val AccentSky = Color(0xFF2E9BD6)
val AccentSage = Color(0xFF5FA777)
val AccentViolet = Color(0xFF8B5CF6)
