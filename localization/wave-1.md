# Wave 1 — Canada, Australia, New Zealand, United Kingdom, Ireland

Gate run 2026-10-06 (session 2185c006) against main at the region commit and
after. The gate itself is defined in [docs/REGIONS.md](../docs/REGIONS.md).
All five regions went **LIVE on 2026-10-07** with 0.3.0 (versionCode 7).

| # | Gate item | Result |
|---|---|---|
| 1 | Forecast depth: ≥7 full days, 3 cities each | **Pass.** Live MET Locationforecast, folded with the app's rule (3 of 4 six-hour blocks plus the noon block): Toronto 9, Vancouver 8, Halifax 9; Sydney 9, Perth 9, Darwin 9; Auckland 8, Wellington 8, Dunedin 8; London 9, Edinburgh 9, Belfast 9; Dublin 9, Cork 9, Galway 9. |
| 2 | Units, dates, clock | **Pass.** Pinned in `RegionsTest`. CA: °C, km/h, kPa, mm, 12-hour. AU: metric, day-first, 12-hour. NZ: metric. GB: °C, mph, hPa, mm, day-first, 24-hour. IE: metric, 24-hour. |
| 3 | Language | **Pass.** English UI; all five list in English. |
| 4 | Place search | **Pass.** `BundledGazetteerTest`: Toronto, London, Dublin, Sydney and Auckland resolve with the right zones; every bundled row parses. |
| 5 | Ads and billing | CA, AU, NZ: banner served as in the US, Remove Ads offered. GB, IE: banner withheld (`AdConsent.CERTIFIED_CMP_REQUIRED`) and Remove Ads not offered — checked on the emulator with the region set to GB: "No ads in your region". |
| 6 | Consent and privacy | `store-assets/privacy-policy.md` updated for this (worldwide place list, MET abroad, the on-device region). **The hosted copy at raylytics.io must be republished before or with the build.** GB/IE are covered by withholding the banner until 5fm.9 is decided. |
| 7 | Honest degradation | **Pass**, emulator 2026-10-06 (London via place search): "Forecast for 17:00 · MET Norway", a Today tile then whole MET days, and the Alerts screen explaining that official warnings are US-only. |

## Verified on the emulator (Pixel_7_API_35, Android 15)

- Automatic region: "United States, from your phone's mobile network"; the US
  glance unchanged (°F, 12-hour, 10/6, banner).
- Manual UK: 16° for New York's 60°F, "17:00 6/10", "Observed 15:51", no
  banner; App settings shows "United Kingdom · °C, mph" and "No ads in your
  region".
- Alerts in the UK: threshold typed with a °C suffix, rule reads
  "Temperature below 2°", "Alert created" snackbar.
- London from place search: "London, United Kingdom", MET throughout.

## Not verified

- A real phone on a UK, Canadian or Australian network. Automatic detection
  from the network is unit-tested; the emulator's network reports "us".
- The Glance widgets in a non-US region. They draw from the same
  `Conventions`, carried in `WidgetData`, and the codec round-trip is tested.
- AdMob fill in CA, AU and NZ.
