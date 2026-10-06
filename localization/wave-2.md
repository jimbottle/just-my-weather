# Wave 2 — India, Pakistan, Nigeria, Philippines, South Africa, Kenya, Ghana, Malaysia, Singapore

Gate run 2026-10-06 (session 2185c006). The gate is defined in
[docs/REGIONS.md](../docs/REGIONS.md). All nine regions are `READY`; none is
`LIVE`.

| # | Gate item | Result |
|---|---|---|
| 1 | Forecast depth | **Pass.** Live MET, the app's fold rule, 9 full days in every city: Delhi, Mumbai, Chennai; Karachi, Lahore, Peshawar; Lagos, Abuja, Kano; Manila, Cebu, Davao; Johannesburg, Cape Town, Durban; Nairobi, Mombasa, Kisumu; Accra, Kumasi, Tamale; Kuala Lumpur, Penang, Kota Kinabalu; Singapore (three points). |
| 1a | Tropical sanity (from PLAN.md) | **Pass.** Six-hour highs and lows are plausible for early October (for example Mumbai 26–38 °C, Lagos 25–30 °C, Nairobi 15–25 °C). Every weather symbol MET returned for these 27 points maps to wording in `MetNoSymbols`, with none unknown. MET gives no chance of precipitation here, so the tiles show none and chance-of-rain alerts are not offered. |
| 2 | Units, dates, clock | **Pass.** All metric. Date order and clock come from CLDR and are pinned in `RegionsTest`: the Philippines and South Africa are month-first; Nigeria, South Africa and Kenya use the 24-hour clock. |
| 3 | Language | **Pass.** English is an official or the dominant app language in each. |
| 4 | Place search | **Pass.** `BundledGazetteerTest`: Mumbai, Karachi, Lagos, Manila, Johannesburg, Nairobi, Accra, Kuala Lumpur and Singapore resolve with the right zones. |
| 5 | Ads and billing | No consent platform is required in any of them (`AdConsent.NOT_REQUIRED`), so the banner and Remove Ads behave as in the US. AdMob fill and Play Billing availability were not checked live; confirm when opening them. |
| 6 | Consent and privacy | The updated `store-assets/privacy-policy.md` applies. India's DPDP Act rules are being phased in. The app sends only coordinates to the weather services, plus AdMob's non-personalized request, but have this read before India opens. |
| 7 | Honest degradation | The same code paths as wave 1, already verified on the emulator. |
