# Play Store listing — Just My Weather

Living reference for every field of the Google Play Console listing. **Update
it here first, then copy into Play Console** in the same PR that changes the
app. Last synced with the console: _app entry created 2026-10-02; listing and
declarations not yet filled_ (just-my-weather-obf).

Everything below is documentation except the fenced copy blocks, which are
what gets pasted. Play Console fields do **not** accept Markdown: asterisks
and underscores show up literally, so the full description uses ALLCAPS lines
as headers and plain bullets.

## App name (max 30 chars)

Decided 2026-10-02 (Evan): the plain name. The "Modular Weather App" descriptor
from the working title does not fit the 30-char cap, so the search terms it was
carrying live in the descriptions and tags instead — see `ASO_NOTES.md`.

```
Just My Weather
```

## Short description (max 80 chars)

Source of truth: `store-assets/short-description.txt` (78 chars). Carries the
two target phrases, "modular weather app" and "custom".

```
Modular weather app for a custom view: pick what you see, set your own alerts.
```

## Full description (max 4000 chars)

Source of truth: `store-assets/full-description.txt` (about 2300 chars).
Paste that file verbatim.

## Search targets (ASO)

The listing is written to be found for **"modular weather app"** and **"custom
weather"** (and the near variants "customizable weather app", "custom weather
view"). Both phrases appear in the short description and several times in the
full description; the app name stays plain. Rationale, rules, and the review
checklist are in `store-assets/ASO_NOTES.md`.

## Categorization

| Field | Value |
|---|---|
| App or game | App |
| Category | Weather |
| Tags | Pick from Play's fixed list, up to five: Weather, Forecast, and whichever of its entries read closest to Modular / Custom / Customization (the list is only visible in the console; record the exact picks here once chosen). See `ASO_NOTES.md`. |
| Free or paid | Free |
| Contains ads | **Yes** — one AdMob banner at the bottom of the glance (decided 2026-10-03) |
| In-app purchases | **Yes** — `remove_ads`, one-time non-consumable, **USD 0.99** (Monetize with Play → Products → One-time products; just-my-weather-6ii) |

## Contact details

| Field | Value |
|---|---|
| Email (public, required) | dev@raylytics.io |
| Website | https://raylytics.io (AdMob reads app-ads.txt from this domain's root; a /justmyweather landing page can replace it later — same domain, so app-ads.txt still resolves) |
| Phone | — |

## Privacy policy URL

```
https://raylytics.io/justmyweather/privacy
```

Markdown source: `store-assets/privacy-policy.md`. Rendered page: the
`raylytics-site` repo at `justmyweather/privacy.html`. Keep the two in sync and
re-date both when the policy text changes.

## Graphics

| Asset | Spec | File |
|---|---|---|
| App icon | 512 × 512 PNG, 32-bit, ≤1 MB | `store-assets/icon-512.png` |
| Feature graphic | 1024 × 500 PNG or JPG | `store-assets/feature-graphic-1024x500.png` |
| Phone screenshots | 2–8, PNG/JPG, 16:9 or 9:16, each side 320–3840 px, **max aspect ratio 2:1** | `store-assets/screenshots/final/*.png` |
| 7" / 10" tablet screenshots | optional, skip for v1 | — |

Regenerate the icon and feature graphic with
`store-assets/generate-graphics.sh` (sources: `icon-512.svg`,
`feature-graphic-1024x500.svg`). Raw Pixel 7 captures are 1080 × 2400
(2.22:1), over Play's 2:1 limit — crop them with
`store-assets/crop-screenshots.sh`.

Screenshots captured 2026-10-04 from the versionCode 3 release build on the
Pixel_7_API_35 emulator after `pm clear` (Louisville, KY, live NWS data; status
bar in demo mode at 9:41). Cropped with the TOP anchor so the banner ad, which
sits at the foot of the glance, falls below the 2:1 cut: the screenshots are
banner-free on purpose (an emulator can only show labelled test ads). Upload in
filename order — the first two carry the listing:

1. `01-your-glance` — a customized glance, dark look (Conditions + Wind beside
   a 2×2 temperature, Sun, Forecast)
2. `02-arrange` — long-press arrange mode with corner handles
3. `03-details` — tap a tile: the full observation sheet
4. `04-daily` — the forecast tile on Daily
5. `05-alerts` — three personal rules and the builder
6. `06-places` — saved places from the bundled US list
7. `07-customize` — per-tile width/height, light look
8. `08-default-glance` — the out-of-the-box glance, light look

Re-capture when a listed screen changes visibly.

## App content declarations (Policy → App content)

| Declaration | Answer |
|---|---|
| Privacy policy | URL above |
| Ads | **Yes**, the app contains ads (Google AdMob banner, removable with the $0.99 purchase) |
| App access | All functionality is available without special access (no login) |
| Content rating | IARC questionnaire → Utility / Productivity / Communication / Other; no violence, sexuality, language, controlled substances, gambling, or user interaction → **Everyone** |
| Target audience | 13 and older (not directed at children). With ads this matters: choosing any under-13 band pulls the app into the Families policy (certified ad SDKs, no ad ID) — keep it 13+. |
| News app | No |
| COVID-19 contact tracing / status | No |
| Data safety | see below |
| Government app | No |
| Financial features | None |
| Health | No health features |
| Advertising ID | **Yes** — used by the Google Mobile Ads SDK for advertising (and fraud prevention). The SDK merges the `com.google.android.gms.permission.AD_ID` permission; leave it in. |

## Data safety form

| Question | Answer |
|---|---|
| Does the app collect or share any of the required user data types? | **Yes** |
| Is all collected data encrypted in transit? | Yes (HTTPS only) |
| Do you provide a way for users to request deletion? | No account exists; data lives only on the device. Choose "No" and, if asked, explain that clearing app data or uninstalling removes everything. |
| Independent security review | No |

Data types:

| Type | Collected | Shared | Ephemeral | Required / optional | Purpose |
|---|---|---|---|---|---|
| Location → Approximate location | Yes | Yes | No | Required | App functionality; Advertising or marketing (AdMob infers it from the IP address of every user who sees ads, so it is not optional) |
| Device or other IDs | Yes | Yes | No | Required | Advertising or marketing; Fraud prevention, security, and compliance |
| App activity → App interactions | Yes | Yes | No | Required | Advertising or marketing; Analytics (AdMob's own ad-impression and click measurement — the developer runs no analytics, and the policy and listing say exactly that) |
| App info and performance → Diagnostics | Yes | Yes | No | Required | App functionality (bug-report email, not shared); Advertising or marketing, Fraud prevention (AdMob SDK diagnostics, shared) |
| Personal info → Email address | Yes | No | No | Optional | Developer communications |

Notes for the form:

- "Collected" because the coordinates leave the device (Play counts any
  off-device transmission). "Ephemeral" because the developer never stores
  them; they are sent per request to fetch a forecast.
- "Shared" because the recipients (US National Weather Service, MET Norway)
  are third parties, not service providers acting for the developer. This
  matches the answer Almanac Bell gave for the same NWS call. The user-
  initiated-action exception would arguably allow "No"; "Yes" is the honest
  and defensible reading.
- "Optional" because device location can be declined and a saved place used
  instead; the place's coordinates still travel the same path.
- **Diagnostics** and **Email address** cover the in-app bug report and idea
  forms (App settings → Report a bug; the foot of Customize). Nothing is sent
  automatically: the app drafts an email to dev@raylytics.io and the user sends
  it from their own email app, after seeing the full diagnostics block. That
  is arguably not "collection by the app" at all, but the developer does
  receive device model, Android version, a few settings, and up to 50 lines
  of the app's own log, plus the sender's address — declaring them as
  optional, not shared, not ephemeral is the conservative reading and matches
  the privacy policy's "Bug reports and ideas you send" section. (Decided by
  the agent 2026-10-02 as a recommendation; Evan confirms when filling the
  form.)
- **The AdMob rows** follow Google's own disclosure guidance for the Google
  Mobile Ads SDK (https://developers.google.com/admob/android/privacy/play-data-disclosure):
  IP-derived approximate location, the advertising ID / device identifiers,
  ad interactions, and SDK diagnostics, all **shared** with Google, used for
  advertising and fraud prevention. Re-read that page whenever the SDK version
  changes — the list moves. These rows are **Required** ("Users can't
  choose"): a non-paying user cannot decline them (buying Remove Ads stops the
  ads, which is a purchase, not a choice the form recognises). Diagnostics is
  Required because of the AdMob half; the bug-report half alone would be
  optional. Location's "Ephemeral" goes to **No** now that AdMob, not just the
  forecast request, receives it.
- **"Analytics" as a purpose is AdMob's measurement, not ours.** Every
  "no analytics" in the policy, description and app is scoped to the
  developer ("no analytics of our own"); keep it that way if the wording is
  ever revised, or the Data safety form and the policy contradict each
  other.
- **Financial info → Purchase history** is **not** declared: Google Play
  Billing handles the $0.99 purchase and the app only stores an "ads removed"
  flag on the device. Google's guidance treats Play Billing as Google's
  collection, not the developer's.
- Every other data type (financial, health, messages, photos, audio, files,
  calendar, contacts, web browsing, crash logs, and the rest of personal info):
  **not collected**.

## Per-release fields

| Field | Source |
|---|---|
| `versionCode` | `app/build.gradle.kts`, bumped once per upload by `scripts/android/bump-version-code.sh` |
| `versionName` | `app/build.gradle.kts`; frozen once a version reaches production (CLAUDE.md "Versioning") |
| Release notes (≤500 chars) | the version's "Store-facing" block in `docs/RELEASES.md` |
| Track | internal first, promote with `scripts/android/promote-play.sh` |

## Countries

United States only for v1: the forecast backbone is the NWS. MET Norway would
cover elsewhere, but the current-conditions, hourly, and alert paths would not.

## Store presence

- Developer name shown on the listing: Raylytics, LLC (organization account,
  owner evan@raylytics.io, `dev@` alias).
- Pricing: free to install, with an AdMob banner and a one-time $0.99
  `remove_ads` purchase (decided 2026-10-03). That makes the app commercial,
  which is why days 8–9 come from MET Norway (CC BY 4.0, commercial use
  allowed) and not Open-Meteo, whose free tier is non-commercial
  (just-my-weather-t1e, resolved 2026-10-03).
- Play allows free → paid but not paid → free; the app stays Free and
  monetises through ads + the purchase.
- app-ads.txt: raylytics.io/app-ads.txt already carries the Raylytics AdMob
  publisher line (publisher-level, covers every Raylytics app). The listing's
  Website must be on raylytics.io for AdMob to find it.
