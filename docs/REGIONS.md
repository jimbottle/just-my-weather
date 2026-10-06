# Regions

How Just My Weather adapts to where its user is, and the exact steps for
adding, opening, pausing and removing a region. If you are about to touch
the Play Console's country list, or `region/Regions.kt`, read this first.

## What a region is

A **region is a Google Play country/region**. It's keyed by the same ISO 3166-1
alpha-2 code that the Play Console, the Play Developer API and Play's
[supported locations](https://support.google.com/googleplay/android-developer/answer/10532353)
page use: `GB`, not `UK`. Puerto Rico, Guam, the US Virgin Islands,
American Samoa and the Northern Mariana Islands are regions of their own on
Play (`PR`, `GU`, `VI`, `AS`, `MP`), and so here too.

Switching region switches, all at once:

| What | How | Where in code |
|---|---|---|
| Units: temperature, wind, pressure, precipitation | Per-country table (°F in the US and a handful of others; mph in the US and UK; kPa in Canada; m/s in the Nordics; metric elsewhere) | `region/Regions.kt` `conventionsFor` |
| Date order (10/6 or 6/10) and clock (4:05 PM or 16:05) | Written into each prepared region's entry, so it reads the same on every phone; for any other country, the platform's CLDR locale data | `region/Regions.kt` `PREPARED`, `conventionsFor` |
| Whether the ad banner (and so the Remove Ads purchase) may be shown | Google's EU User Consent Policy: no banner in the EEA, UK and Switzerland until the app has a certified consent platform | `region/Region.kt` `AdConsent` |
| Where a first run opens with no location and no saved place | The region's default place | `region/Regions.kt` `PREPARED` |

What a region does **not** decide: where the weather comes from. That is per
**place**. NWS covers its own territory, and MET Norway covers everything
else (`data/WeatherRepository.kt`). Someone in Leeds who looks at Ohio gets
NWS's Ohio forecast, read in Celsius.

A region the user picks by hand changes units and dates only. **Whether an
ad may be shown, and the ad SDK started, follows only where the phone
physically is**: its mobile network or its location fix. It never follows the
manual region, the place being shown, or the language setting. With neither
physical clue, consent is treated as required and no ad is shown (fail
closed). So picking "United States" in Berlin, or viewing New York from a
Wi-Fi tablet there, can never serve an ad without the consent the law
requires. The cost is no banner on a phone with no mobile network and no
location permission, even in the US. Remembered physical clues are never
reused: each decision rests on what the phone can see now
(`RegionRepository.merge`).

The user can pin any unit by hand. A pinned unit overrides the region's,
and the unpinned ones keep following the region. The date order and the
clock always follow the region; choosing a region is how to change them.

## How the app decides which region it is in

`region/RegionResolver.kt`, in this order. The first clue that names a real
country wins:

1. **The user's choice**, if they made one in App settings → Region &
   units. This is the override.
2. **The phone's mobile network country** (`TelephonyManager.networkCountryIso`).
   This is where the phone physically is: a roaming phone reports the
   network it is visiting. It needs no permission. It's empty on Wi-Fi-only
   devices and in airplane mode.
3. **The phone's last location fix**, turned into a country by the nearest
   place in the bundled gazetteer (`data/places/PlaceCatalog.nearest`). No
   geocoder and no network is involved.
4. **The country of the place the app is showing.**
5. **The phone's language settings** (en-GB → GB).
6. **The United States**, the app's home.

The phone outranks the place on purpose. A region is about the *person*:
the units they think in, and the ads they may be shown.

Clues are gathered on every refresh of the glance and of each background
worker, and are remembered. That way a cold start, and a worker running with
no location, reads in the right units from the first frame. Gathering stops
at the first real country, so a phone on a mobile network never loads the
gazetteer just to learn where it is.

App settings → **Region & units** shows the region in force *and the clue
that decided it*. Look there first when someone reports the wrong units.

## The registry

`app/src/main/java/io/raylytics/justmyweather/region/Regions.kt` holds
`PREPARED`, one entry per region we ship to or intend to:

```kotlin
Prepared(
    "GB",
    PlayStatus.PLANNED,
    DefaultPlace("London, United Kingdom", 51.51, -0.13, "Europe/London"),
    DateOrder.DAY_FIRST,
    clock24 = true,
),
```

The date order and clock are written down rather than derived. CLDR data
changes between Java and Android versions: Java 17 and 21 disagree for a
wave-2 country. A region we ship to must read the same on every phone.

| `PlayStatus` | Means | Who sets it |
|---|---|---|
| `PLANNED` | On the plan; not yet through the gate below | An agent or developer, when it's added |
| `READY` | Every gate item passed; waiting on the console | An agent or developer, after the gate |
| `LIVE` | Selected in the Play Console's **production** track | Only after a human has made the console change |

Any country *not* in `PREPARED` still works: `Regions.of` derives its
conventions. It just isn't a region we ship to, and it has no first-run
place.

`scripts/check-regions.sh` compares the registry's `LIVE` set with Play's
production track (read-only, through the Play API) and fails when they
differ. Run it before and after every console change.

## The per-region gate

A region moves from `PLANNED` to `READY` only when every item holds:

1. **Forecast depth.** MET returns at least seven full days for three
   spread-out cities, folded by `MetNoClient.getDailyForecast`. Record
   the cities in the wave notes.
2. **Units.** `RegionsTest` pins the region's conventions, and they are
   right: units, date order, clock. Add the region to that test.
3. **Language.** The UI is in a language the region's Play listing is in.
   Today the UI is English only, so only English-reading regions pass.
4. **Place search.** The region's major cities resolve from the gazetteer
   with the right label and time zone. Add them to `BundledGazetteerTest`.
5. **Ads and billing.** Either AdMob serves there and the `remove_ads`
   product is purchasable (Play Billing's country list), or `AdConsent`
   keeps the banner off, in which case the purchase is not offered.
6. **Consent and privacy.** The region's consent regime is answered (see
   "Ads and consent" below), and the privacy policy's claims hold there.
7. **Honest degradation.** Outside NWS territory the glance says
   "Forecast for …" rather than "Observed", the Alerts screen explains
   that there are no official warnings, and nothing renders as an empty
   state that looks broken. This is all automatic; check it once on the
   emulator with a place in the region.

## Adding a region — step by step

1. **Add the entry** to `PREPARED` in `region/Regions.kt`, with
   `PlayStatus.PLANNED`, a default place (the largest or capital city, with
   its IANA zone), and the region's date order and clock. Take those from
   current CLDR (`Regions.conventionsFor` on Java 21 prints them), then
   check them against how the country actually writes dates.
2. **Check the conventions.** Add the region to `RegionsTest` with the
   units, date order and clock it should read in. If its units are wrong,
   fix the per-country unit tables in `Regions.kt` and say why in a
   comment.
3. **Check its consent regime.** If it requires a certified consent
   platform, add it to `CERTIFIED_CMP_REQUIRED` in `Regions.kt`.
4. **Check place search.** Add two or three of its cities to
   `BundledGazetteerTest`. If they are missing, the gazetteer only carries
   places of 15,000+ people (GeoNames `cities15000`); see
   `scripts/build-gazetteer.sh`.
5. **Run the gate** above. `scripts/verify.sh` covers 2 and 4; 1, 5, 6
   and 7 are manual. Record them in `localization/wave-N.md`.
6. **Set it to `READY`** and commit, with a message that names the gate
   evidence.
7. **Ship a build that contains it.** Bump `versionName`
   (CLAUDE.md "Versioning") and cut to internal **only on Evan's explicit
   go-ahead for that upload**, then promote as usual.
8. **The console change (human only).** Play Console → the app →
   **Production** → **Countries / regions** → **Add countries/regions** →
   tick the region → **Save** → send the change for review in **Publishing
   overview**. Agents never do this; hand it off with `wyk handoff`, and
   include this checklist as the runbook.
9. **Mark it `LIVE`** in `Regions.kt` once the change has been published,
   and run `scripts/check-regions.sh` until it reports that the registry
   and Play agree. Commit.
10. **Update the listing if needed:** `store-assets/play-store-listing.md`
    (it names the data sources), and the release notes.

Order matters. Never mark a region `LIVE` before the console says so, and
never open a region on the console in a build whose registry doesn't have
it at least `READY`: the build would serve the wrong units, or ads where
consent is required.

## Pausing or removing a region

1. **Console first (human only):** Production → Countries / regions →
   untick the region → Save → publish. Existing installs keep working;
   new installs stop.
2. Set the region back to `READY` (paused, still passes the gate) or
   `PLANNED` (it needs work), and say why in the commit message.
3. Run `scripts/check-regions.sh` and commit.

Removing a line from `PREPARED` entirely is rarely right: the country
still resolves through derived conventions either way, and the line is
where its history and default place live.

## Ads and consent

Today the app serves only non-personalized ads and ships no consent form.
Google's EU User Consent Policy requires a Google-certified consent
management platform (the UMP SDK) for ad traffic from the EEA, the UK and
Switzerland. Without one only "limited ads" serve, and the policy
requirement is not met. So the app shows **no banner** in those regions
(`AdConsent.CERTIFIED_CMP_REQUIRED`), and therefore offers no Remove Ads
purchase there.

The decision to integrate UMP, which would allow the banner in those regions, is
tracked as **just-my-weather-5fm.9** and belongs to Evan. If UMP is added,
gate the banner on its consent answer and move the regions out of
`CERTIFIED_CMP_REQUIRED` in the same change.

## Rollout waves

The order regions are prepared in, English-reading and high-population
first. The detail is in `localization/PLAN.md`.

| Wave | Regions |
|---|---|
| 0 | US (live) |
| 1 | CA, AU, NZ (no consent platform needed); GB, IE (open without a banner, or after UMP) |
| 2 | IN, PH, NG, ZA, PK, KE, GH, SG, MY |
| 3+ | Europe by Play's own grouping, after UI translation |

## Files at a glance

- `region/Region.kt`: what a region is (`Region`, `AdConsent`, `PlayStatus`).
- `region/Regions.kt`: **the registry**, plus derived conventions for every country.
- `region/RegionResolver.kt`: the decision order, the user's settings, and the clues.
- `region/RegionRepository.kt`, `AndroidRegionSignals.kt`: gathering and remembering clues.
- `view/Conventions.kt`: every unit conversion and date/time format, in one file.
- `ui/settings/RegionScreen.kt`: the Region & units screen.
- `scripts/android/play-countries.sh`: lists a track's countries from Play (read-only).
- `scripts/check-regions.sh`: registry vs. Play.
