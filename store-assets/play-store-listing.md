# Play Store listing — Just My Weather

Living reference for every field of the Google Play Console listing. **Update
it here first, then copy into Play Console** in the same PR that changes the
app. Last synced with the console: _never — the app entry has not been created
yet_ (see docs/PLAY_STORE.md "Bootstrap").

Everything below is documentation except the fenced copy blocks, which are
what gets pasted. Play Console fields do **not** accept Markdown: asterisks
and underscores show up literally, so the full description uses ALLCAPS lines
as headers and plain bullets.

## App name (max 30 chars)

> ⚠ **Decision needed.** The working name "Just My Weather - Modular Weather
> App" is **37 chars**; Play caps the app name at 30. Candidates:
>
> | Candidate | Chars |
> |---|---|
> | `Just My Weather - Modular App` | 29 |
> | `Just My Weather: Modular` | 24 |
> | `Just My Weather` | 15 |
>
> The descriptor is only worth keeping if it carries a search term; "modular"
> is the differentiator, "weather app" is already implied by the name and the
> category. Whichever is chosen, `app_name` in `strings.xml` stays
> "Just My Weather" (the launcher label has no reason to carry a descriptor).

Working value until decided:

```
Just My Weather - Modular App
```

## Short description (max 80 chars)

Source of truth: `store-assets/short-description.txt` (78 chars).

```
A modular weather app. Pick what you see, arrange it, and set your own alerts.
```

## Full description (max 4000 chars)

Source of truth: `store-assets/full-description.txt` (about 2300 chars).
Paste that file verbatim.

## Categorization

| Field | Value |
|---|---|
| App or game | App |
| Category | Weather |
| Tags | Weather, Forecast |
| Free or paid | Free |
| Contains ads | No |
| In-app purchases | None |

## Contact details

| Field | Value |
|---|---|
| Email (public, required) | dev@raylytics.io |
| Website | https://raylytics.io/justmyweather (not yet built; the privacy URL exists first) |
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

**Screenshots are stale.** `docs/screenshots/*.png` show the v0.1 glance
(before the module grid, forecast tiles, and places). Capture fresh ones on
the API 35 emulator from the current build before submitting.

## App content declarations (Policy → App content)

| Declaration | Answer |
|---|---|
| Privacy policy | URL above |
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access (no login) |
| Content rating | IARC questionnaire → Utility / Productivity / Communication / Other; no violence, sexuality, language, controlled substances, gambling, or user interaction → **Everyone** |
| Target audience | 13 and older (not directed at children) |
| News app | No |
| COVID-19 contact tracing / status | No |
| Data safety | see below |
| Government app | No |
| Financial features | None |
| Health | No health features |
| Advertising ID | No, the app does not use the advertising ID |

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
| Location → Approximate location | Yes | Yes | Yes | Optional | App functionality |

Notes for the form:

- "Collected" because the coordinates leave the device (Play counts any
  off-device transmission). "Ephemeral" because the developer never stores
  them; they are sent per request to fetch a forecast.
- "Shared" because the recipients (US National Weather Service, Open-Meteo)
  are third parties, not service providers acting for the developer. This
  matches the answer Almanac Bell gave for the same NWS call. The user-
  initiated-action exception would arguably allow "No"; "Yes" is the honest
  and defensible reading.
- "Optional" because device location can be declined and a saved place used
  instead; the place's coordinates still travel the same path.
- Every other data type (personal info, financial, health, messages, photos,
  audio, files, calendar, contacts, app activity, web browsing, app info and
  performance, device IDs): **not collected**.

## Per-release fields

| Field | Source |
|---|---|
| `versionCode` | `app/build.gradle.kts`, bumped once per upload by `scripts/android/bump-version-code.sh` |
| `versionName` | `app/build.gradle.kts`; frozen once a version reaches production (CLAUDE.md "Versioning") |
| Release notes (≤500 chars) | the version's "Store-facing" block in `docs/RELEASES.md` |
| Track | internal first, promote with `scripts/android/promote-play.sh` |

## Countries

United States only for v1: the forecast backbone is the NWS. Open-Meteo would
cover elsewhere, but the current-conditions, hourly, and alert paths would not.

## Store presence

- Developer name shown on the listing: Raylytics, LLC (organization account,
  owner evan@raylytics.io, `dev@` alias).
- Pricing: free, no in-app purchases, no ads. The Open-Meteo free tier is for
  non-commercial use; keep the app free or move to their paid plan.
