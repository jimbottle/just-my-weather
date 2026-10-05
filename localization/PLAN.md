# Localization plan

Written 2026-10-04 from a read of the code as it stood then. Sizing is a
read, not a measurement; revise it in the tracker as pieces close.

## 1. Rollout waves

Ordered by Evan's rule — English-speaking and high-population first — then
by the region groups the Play Console uses in its country picker (Africa,
Americas, Asia, Europe, Oceania, each with sub-regions). A wave opens only
when every country in it passes the gate in section 2.

| Wave | Countries | Why this order | Needs beyond wave 0 |
|---|---|---|---|
| 0 | United States | Live. NWS backbone: observations, hourly, 7-day, official alerts. | — |
| 1 | Canada, United Kingdom, Ireland, Australia, New Zealand | English-primary, strong ad markets, well modelled by MET's global data. The UK and Ireland are in MET's best-covered region. | Units setting (°C everywhere; km/h in CA/AU/NZ, mph in UK); MET as the sole source; alerts hidden; gazetteer with these countries; **ad consent check for the UK** (section 4). |
| 2 | India, Philippines, Nigeria, South Africa, Pakistan, Kenya, Ghana, Singapore, Malaysia | English is official or dominant in app use; very large populations. Lower ad yield, and MET's precipitation *probability* is absent outside the Nordics (already handled: the tile shows no chance line). | Wave 1 work; spot-check MET output in tropical cities (monsoon-season highs/lows, symbol wording); confirm Play Billing and AdMob serve in each. |
| 3 | Europe by Play sub-region: Northern and Western first, then Southern, then Eastern | MET's home turf, best data; MeteoAlarm exists for official alerts later. Needs translated UI strings — a separate epic, not this one. | EEA consent (section 4); string translation; `Locale`-aware date formats (several formatters are pinned to `Locale.US`, see section 3). |
| 4 | Remaining Americas, Asia, Oceania, Africa | Everything else, by Play region, as translations land. | Translations; per-country billing/ads availability. |

A country is never added to the console list by an agent. The agent prepares
the wave (section 5) and hands the console change to Evan.

## 2. The per-country gate

Every country in a wave must pass all of these before it is added:

1. **Forecast depth** — MET returns at least seven full days for three
   spread-out cities in the country, folded by `MetNoClient.getDailyForecast`
   (the "three of four blocks plus the noon block" rule can drop a trailing
   day; seven must survive).
2. **Units** — the default unit set for the country is right on first run
   without the user touching anything (temperature, wind, pressure, precip).
3. **Language** — the UI is in a language the country's Play listing is in.
   Waves 1 and 2 are English only.
4. **Place search** — the country's major cities resolve from the bundled
   gazetteer with the right label and timezone.
5. **Ads and billing** — AdMob serves and the `remove_ads` product is
   purchasable there (Play Billing country list), or the banner is hidden
   and the purchase entry does not render.
6. **Consent and privacy** — section 4 answered for the country's regime;
   the privacy policy's claims hold (it currently names the NWS and MET as
   sources; nothing new is sent anywhere).
7. **Honesty of degraded features** — no station observation and no
   official alerts outside NWS coverage: the glance says "forecast for now"
   rather than "observed", the Alerts screen does not offer the safety
   banner, and nothing renders as an empty state that looks broken.

## 3. Engineering work items

These are the children of the tracker epic. Dependencies are in bd; this is
the narrative.

**A. Source routing (foundation).** `WeatherRepository` decides per place
whether NWS covers it: an NWS `/points` 404 means "not NWS territory", and
the verdict is cached with the point so it is decided once per coordinate.
Outside NWS, every load (now, hourly, daily, alerts) goes to MET. The wire
shapes still never leave the data layer; `WeatherSnapshot`, `ForecastPoint`
and `DailyPeriod` are what the UI keeps seeing. Legibility rule from
CLAUDE.md applies: the routing should read as one obvious `when`, not a
strategy hierarchy.

**B. A "now" reading from MET.** MET has no observations. Build the snapshot
from the current hour's `instant` block (temperature, humidity, sea-level
pressure, wind, dewpoint) and the `next_1_hours` symbol for conditions.
Feels-like is not provided: compute heat index / wind chill in `Units` from
the same formulas NWS uses, or leave it null — decide in the issue, with the
reasoning in the code. `observedAt` becomes the forecast hour's start; the
observation-age UI (`ui/home/ObservationAge.kt`) needs a "forecast" wording.

**C. Hourly from MET.** Hourly for roughly 48 hours, then six-hourly to day
nine. `HourlyHours` allows up to 168; cap the setting per source or carry the
coarser points and let the module draw them at their real spacing. The
forecast-window alerts look 24 hours ahead and are unaffected.

**D. Daily from MET into the Daily module.** NWS gives named day/night
half-periods with a prose paragraph; MET gives whole days (`ExtendedDay`,
already folded). Let the Daily module and detail sheet accept day-shaped
items for every day, not just 8 and 9; the extended-day path in
`ForecastGrouping` is the pattern to generalise.

**E. Units setting (largest item, and needed for wave 1).** The app is
Fahrenheit, mph, inHg and inches end to end with no setting (about 80 sites
across 11 files, concentrated in `ForecastGrouping`, `ForecastModule`,
`WeatherField`, `Details`). Add a units choice in Customize (presets:
US, UK, metric; per-dimension override optional), keep the data layer in
one canonical system, and convert at render in one place. Alert thresholds
are stored as bare numbers in °F / mph / in: convert at the edge or store
the unit with the rule, and migrate existing rules (`AlertRulesCodec`). The
Gadgetbridge export has its own unit expectations; check `GadgetbridgeWeather`.
Default the preset from the device locale.

**F. Gazetteer and timezone.** `places.tsv` is the US Census gazetteer and
`Place.state` is a USPS code. Add GeoNames (CC BY 4.0 — add the attribution
to App settings beside MET's) for the wave's countries: `cities15000` is
~30k rows worldwide, a sensible first cut. Labels become "City, Country"
outside the US; `PlaceCatalog.parseQuery`'s state-suffix rule must not
mistake a country code for a state. Timezone: NWS supplied it; abroad take
it from the GeoNames row, and for a GPS fix use the device zone. The
builder script gets a sibling or a second stage, and the asset stays
checked in.

**G. Safety alerts outside NWS coverage.** Hide the banner, the opt-in in
Alerts, and the background poll when the place is not NWS territory; say so
in one line in Alerts. Official alerts abroad (MeteoAlarm for Europe,
Environment Canada CAP) are a later epic, not this one.

**H. Locale-pinned formatting.** Several formatters in `HomeScreen` and
`ForecastGrouping` are built with `Locale.US` or `Locale.getDefault()`
inconsistently; make the choice deliberate (dates and hours follow the
device locale, coordinates stay `Locale.US` — the comment in `PlacesScreen`
explains why). Small, but it is the kind of thing that ships a "3/10" date
to someone who reads it as the 3rd of October.

**I. Console and listing per wave (human).** Country list, listing copy
mentioning MET as the source abroad, privacy policy wording check, release
notes, `versionName` bump. Evan's action, prepared by the agent.

Rough sizing for A–H together: one and a half to two weeks of focused
agent work, with E about a third of it.

## 4. Open question to settle before wave 1: ad consent in the UK and EEA

`AdPolicy` serves every request non-personalized and the comment says that
is "what lets the app skip a consent form". That is the policy as decided
for the US. Google's EU User Consent Policy, enforced for AdMob traffic from
the EEA and the UK, is stricter and may require a Google-certified consent
management platform (the UMP SDK) even for non-personalized or "limited"
ads. **This has to be verified against Google's current policy, not
assumed.** Outcomes, in order of preference:

1. Confirmed not required for NPA-only traffic — note the source and date
   in `AdPolicy`'s comment and move on.
2. Required — integrate UMP (a small, well-documented addition behind the
   existing `ads/` seam) before the UK enters wave 1; Ireland is EEA and
   needs the same answer.
3. Simplest fallback if neither is settled in time — don't serve the banner
   outside the US for the first international build; the `remove_ads`
   purchase then has nothing to remove there and must not be offered.

This is filed as a human decision in the epic because it is a legal and
policy call, not a code one.

## 5. Runbook for the agent that executes this

Not to be started until Evan says so. When that happens:

1. `bd show` the epic; `bd ready` shows what is unblocked. Claim one issue
   at a time with `bd update <id> --claim`.
2. Work in the order the dependencies give: **E (units)** and **F
   (gazetteer)** are independent and can start first; **A (routing)** next;
   then **B, C, D, G** which all sit on A; **H** anywhere.
3. Every piece lands with JVM tests first (`MetNoClientTest` and
   `NwsClientTest` are the patterns; fake the transport). The gate is
   `scripts/verify.sh`. Reach for an emulator only for the visual pieces
   (B's age wording, D's tiles, G's Alerts screen) and follow CLAUDE.md
   "Devices & background processes" to the letter.
4. Verify abroad with real coordinates, on the JVM, against a recorded MET
   response for each wave-1 country (one city each; store the fixtures
   under `app/src/test/resources/metno/`). The gate in section 2 item 1 is
   a test, not a manual check.
5. Commit per piece with a *why* message; do not push without approval.
6. When A–H are closed: write `localization/wave-1.md` (cities checked,
   fixtures used, units defaults per country, what degrades and how it is
   worded), bump `versionName`, cut to internal **only on Evan's explicit
   go-ahead**, and hand the console country change to Evan via
   `wyk handoff` with the wave's checklist as the runbook.
7. Later waves repeat 4–6; wave 3 opens a translations epic first.

Do not: switch the US to MET (NWS observations and alerts are a feature),
drop the NWS point cache, add a geocoding API, or widen the ad or data
footprint in a way the privacy policy does not already describe.
