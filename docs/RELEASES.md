# Release notes

Per-version log of what shipped where. Two audiences:

- **Store-facing** copy is what goes in Play Console "What's new in this
  release" (≤500 chars). User language: what they will notice, no issue ids.
- **Internal notes** capture the changeset for bisecting later. Issue ids
  (`just-my-weather-xxx`) and roborev review numbers are fine here.

## Conventions

- `versionName` in `app/build.gradle.kts` is the version; `versionCode` is
  the upload counter (CLAUDE.md "Versioning").
- Group internal notes under **Added / Changed / Fixed / Internal**; skip
  empty groups.
- Date a version when it ships (absolute dates, YYYY-MM-DD), and say where:
  GitHub release (sideload APK), Play internal, Play production.
- Carry "Known issues" forward until resolved.

---

## 0.3.0 — unreleased (next upload: versionCode 6)

Home screen widgets. Built 2026-10-05 on top of 0.2.1 (which is on the
internal track as versionCode 5); nothing cut yet.

### Store-facing (≤500 chars)

> Widgets. Put a tile of your glance on the home screen — the temperature,
> feels-like, conditions, wind, pressure, sunrise and sunset, or the forecast
> — and set it up the way you set up the glance: label, density, light or
> dark, accent, typeface, hourly or daily, how far ahead. Resize it like any
> widget; it fits what it shows. New widgets start from your glance's
> settings, and refresh every 15 minutes.

### Internal

- Added: `widget/` — one Glance `GlanceAppWidget` whose config is a
  `ViewConfig` with one visible module plus a `ThemeConfig`
  (`WidgetConfig`, per-widget in `WidgetConfigRepository`); a 15-minute
  `WidgetRefreshWorker` (WorkManager's floor) fetches once for every widget
  into one `WidgetData`; `WidgetContent` draws readings, the sun and the
  forecast in Glance, reusing `ViewConfig.render` and the forecast's day
  grouping. Config and data are mirrored into Glance's per-widget state
  (`WidgetState`) because a live session recomposes without re-running
  `provideGlance`. `ui/widget/WidgetConfigureActivity` is the launcher's
  configure step and the long-press reconfigure (epic just-my-weather-aqd).
- Changed: the Customize screen's pickers moved to `ui/customize/Pickers.kt`
  (internal) so the widget's configure screen uses the same controls.
- Verified on Pixel_7_API_35 (Pixel launcher): placed from the picker (the
  launcher skips the configure step and offers a pencil; the widget seeds
  itself from the glance), reconfigured to Forecast and to Sun/Dark/Serif,
  resized 2×1 → 3×4; hourly grid scrolls and reflows to the width.
- Known: the Pixel launcher places a `reconfigurable` widget without
  opening the configure activity (it offers "Tap to change widget
  settings"); other launchers open it on drop. Both paths are handled.

## 0.2.1 — Play INTERNAL, versionCode 5 — 2026-10-05 (next upload: versionCode 6)

0.2.0 is live in production; everything since goes here. versionCode 5 went
to the internal track on 2026-10-05 via scripts/android/release-internal.sh.

### Store-facing (≤500 chars)

> Sunrise and sunset can show up to two weeks of days — pick how many on
> Customize, and the tile scrolls. Chips, sliders and switches now take the
> accent you chose. The banner no longer nudges the glance when it loads.
> A Rate link in App settings, for when it has earned a place on your phone.

### Internal

- Added: App settings → Help → "Rate Just My Weather", opening the Play
  listing (the Play Store app, else the web page) (just-my-weather-9uu).
- Added: the sun module's day count is a setting (SunDays, 1–14, default
  2) with a "Sun shows N days" slider under Customize → Sun times; the table
  scrolls inside the tile past its height, except while arranging
  (just-my-weather-eia).
- Fixed: every accent-carrying colour role follows the chosen accent
  (selected chips, slider tracks, switch thumbs were Material's purple);
  Customize's intro mentions height (6lf.5).
- Fixed: the banner reserves its height before the ad loads, so the glance
  no longer jumps a second after launch (dm2's flake, and a visible jump).
- Internal: Maestro flows assert bottom-sheet content rather than a tag in
  another window, wait for destination screens, and scroll to the forecast
  switch; 10/10 locally (dm2, p6t).

## 0.2.0 — Play PRODUCTION, versionCode 4 — LIVE 2026-10-05 (submitted 2026-10-04; internal: 4 on 2026-10-04, 3 on 2026-10-03, 2 on 2026-10-02)

The first Play Store build. Internal testing track, manual upload from the
console (the Developer API needs a prior console upload). Production follows
once the privacy policy is live and the listing is complete (epic
just-my-weather-zi8). versionCode 3 is the launch candidate: the ads build with
the real AdMob ids, cut by scripts/android/release-internal.sh --status draft
(the API only accepts drafts before the first production release) and rolled
out from the console. versionCode 4 is the production release: versionCode 3 plus the fixes listed
below. It was attached to the Production track as a DRAFT through the API on
2026-10-04 (an app that has never been published only accepts drafts, on any
track; `promote-play.sh` cannot do this step because it also requires the
build to have been rolled out on internal first) — the "Send for review"
click is console-only: Evan sent it for review on 2026-10-04, after rolling
versionCode 4 out on the closed (alpha) track to retire versionCode 2 — the
pre-ads build without AD_ID — which Play's pre-review check flagged as an
active artifact contradicting the Advertising ID declaration. Approved and
live on 2026-10-05. versionCodes 2–4 are burned: the next upload is 5, and
from here the API accepts rolled-out releases (release-internal.sh without
--status draft; promote-play.sh for production).

### Store-facing (≤500 chars)

> The first release on Google Play. Build your own glance from a grid of
> weather modules: pick which show, size them, drag to arrange, rename, and
> theme them. Tap a tile for details. Hourly and daily forecasts as tiles, up
> to nine days out. Save places from an offline list of US towns or by
> coordinates. Personal alerts on current conditions and the forecast, quiet
> by default. Free with one small banner ad; a one-time purchase removes it.
> No account needed.

### Internal

- Added: modular lattice with corner-drag resize and long-press arrange;
  forecast tiles (hourly, daily 1–7 days from NWS, 8–9 from MET Norway);
  saved places with the bundled Census gazetteer; tap-for-details; sun
  module; Gadgetbridge hand-off; safety-alert opt-in; configurable alert
  cadence and quiet hours; per-rule fire limit (once, up to 99 times, or
  every time) — a rule that reaches its limit switches itself off and the
  toggle re-arms it (just-my-weather-8di).
- Fixed (after versionCode 3): the bug report named the glance state by its
  R8-renamed class ("Glance: n0"); feels like falls back to the air
  temperature when no heat index / wind chill applies; an unreported
  last-hour precipitation says "Not reported" rather than "—".
- Changed (after versionCode 3): About no longer carries a Source code row;
  the privacy policy no longer describes the app as open source.
- Added: one AdMob banner at the foot of the glance (non-personalized) and
  a one-time Remove Ads purchase (`remove_ads`, $0.99) in App settings, with
  restore; the entitlement is kept on the device and re-checked against Play
  on every start (epic just-my-weather-zi8.3).
- Changed: the extended daily forecast comes from MET Norway (CC BY 4.0,
  commercial use allowed) instead of Open-Meteo, whose free tier is
  non-commercial and the Play build carries ads; the cap drops from fourteen
  days to the nine MET reaches, and a saved fourteen reads back as nine.
- Internal: targetSdk/compileSdk 36 on AGP 8.13.2 (Play requires API 36 for
  new apps since 2026-08-31); Play release tooling under `scripts/android/`,
  `store-assets/`, this file.

## 0.1.1 — GitHub release, 2026-07-31 (versionCode 2)

- Fixed: edge-to-edge insets on Android 15+ (status bar overlapped the
  glance; shipped because UI had only been checked on API 34).

## 0.1.0 — GitHub release, 2026-07-31 (versionCode 1)

First testable release: the glance, view customization, and personal alerts
over the NWS data layer.
