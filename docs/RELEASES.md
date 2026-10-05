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

## 0.2.0 — Play INTERNAL (versionCode 4, uploaded 2026-10-04 as a draft; 3 on 2026-10-03, 2 on 2026-10-02)

The first Play Store build. Internal testing track, manual upload from the
console (the Developer API needs a prior console upload). Production follows
once the privacy policy is live and the listing is complete (epic
just-my-weather-zi8). versionCode 3 is the launch candidate: the ads build with
the real AdMob ids, cut by scripts/android/release-internal.sh --status draft
(the API only accepts drafts before the first production release) and rolled
out from the console. versionCode 4 is the production candidate: versionCode 3 plus the fixes
listed below. versionCodes 2–4 are burned: the next upload is 5.

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
