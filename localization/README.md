# Localization — taking Just My Weather outside the United States

**Status (2026-10-06): engineering items A–H done and in main** — MET as the
sole source abroad, worldwide gazetteer, regions with automatic units/dates/
ad consent. **How regions work and how to add one: `docs/REGIONS.md`.** What
remains is human: the UK/EEA consent decision (5fm.9) and each console
change. The tracker epic is **just-my-weather-5fm**.

## What "outside the US" means here

The weather backbone is the NWS, which forecasts only for US territory. The
app already carries a second, global source — MET Norway's Locationforecast
(`data/metno/MetNoClient.kt`), keyless, CC BY 4.0 with commercial use
allowed, nine days everywhere — but today it supplies only days 8 and 9.
Making the app work abroad means letting MET carry *everything* for a place
NWS does not cover, and being honest about what it cannot carry (station
observations, official hazard alerts).

The availability rule Evan set: **ship only in countries where the app can
show at least a seven-day forecast.** MET clears that bar for the whole
planet, so the data side does not restrict the country list. What restricts
it is language, units, ad and billing availability, and consent law — which
is what the wave plan in `PLAN.md` is organised around.

## Files

- `PLAN.md` — the rollout waves (English-speaking, high-population first,
  then by Play's region groupings), the per-country gate, the engineering
  work items, and the runbook an agent follows to execute it.
- Future: per-wave notes (`wave-1.md` …) as each wave is actually run, with
  what was verified on which cities and what the console was set to.

## Boundaries that still apply

- Country availability is a Play Console setting. Changing it is a release
  action and needs Evan's explicit go-ahead per change, same as an upload
  (CLAUDE.md "Release confirmation gate").
- A released `versionName` is frozen; the first international build gets a
  new one.
- MET's terms: identifying User-Agent (already sent), honour `Expires`
  (OkHttp cache, already wired), attribution in-app (already in the detail
  sheet and App settings). A larger install base must stay under MET's
  request ceiling; if that becomes a concern it is a tracker issue, not a
  silent change of source.
