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

## 0.2.0 — unreleased (first Play Store build)

Target: Play internal testing, then production. Blocked on the API 36 bump,
the upload keystore, and the console app entry (see the tracked epic).

### Store-facing (≤500 chars)

> The first release on Google Play. Build your own glance from a grid of
> weather modules: pick which show, size them, drag to arrange, rename, and
> theme them. Tap a tile for details. Hourly and daily forecasts as tiles, up
> to fourteen days out. Save places from an offline list of US towns or by
> coordinates. Personal alerts on current conditions and the forecast, quiet
> by default. No account, no ads, no tracking.

### Internal

- Added: modular lattice with corner-drag resize and long-press arrange;
  forecast tiles (hourly, daily 1–7 days from NWS, 8–14 from Open-Meteo);
  saved places with the bundled Census gazetteer; tap-for-details; sun
  module; Gadgetbridge hand-off; safety-alert opt-in; configurable alert
  cadence and quiet hours; per-rule fire limit (once, up to 99 times, or
  every time) — a rule that reaches its limit switches itself off and the
  toggle re-arms it (just-my-weather-8di).
- Internal: Play release tooling under `scripts/android/`, `store-assets/`,
  this file.

## 0.1.1 — GitHub release, 2026-07-31 (versionCode 2)

- Fixed: edge-to-edge insets on Android 15+ (status bar overlapped the
  glance; shipped because UI had only been checked on API 34).

## 0.1.0 — GitHub release, 2026-07-31 (versionCode 1)

First testable release: the glance, view customization, and personal alerts
over the NWS data layer.
