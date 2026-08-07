# Architecture

A single `app` module, Kotlin + Jetpack Compose + Material 3, MVVM, with manual
dependency injection (one readable `AppContainer` in `JustMyWeatherApp.kt` — no
Hilt). Everything lives under `io.raylytics.justmyweather`.

## Data flow

```
HomeViewModel ─┐
CustomizeVM    ├─▶ repositories ─▶ WeatherRepository ─▶ NwsClient ─▶ HttpTransport ─▶ api.weather.gov
AlertsVM       ┘     (DataStore)        (the one seam)     (parse/retry)   (OkHttp)
ThemeVM

AlertWorker (background) ─▶ WeatherRepository + AlertEvaluator ─▶ AlertNotifier
```

The UI depends on exactly one domain object, `WeatherSnapshot`; the NWS wire
shapes never leak past `WeatherRepository`.

## Packages

- **`data/nws/`** — the weather backbone, ported from a reference TypeScript
  client. `NwsClient` talks to the free public NWS API (no key); `HttpTransport`
  is a fun-interface seam isolating the HTTP call so the retry/parse logic is
  unit-tested with a fake (no network). `Units` holds pure conversions; `NwsWire`
  is the raw JSON DTOs, `NwsModels` the cleaned-up domain shapes.
- **`data/`** — `WeatherRepository` is the single seam between NWS and the app
  (cached point resolution, label fill, current + forecast loads). The
  `*Repository` classes persist config via Preferences DataStore; `PointCache`
  (in-memory default, `DataStorePointCache` durable) survives the grid lookup
  across launches. `WeatherSnapshot` / `WeatherLocation` are the domain objects.
- **`location/`** — `LocationProvider` over the platform `LocationManager`
  (coarse only, no Play Services, so it builds from source anywhere).
- **`view/`** — the customization *data* (pure, no Compose): `WeatherField` (the
  data-point catalog), `ViewConfig` + `Density`, `ThemeConfig`, and their JSON
  codecs. `ViewRender` turns a config + snapshot into the rendered hero + rows.
- **`alerts/`** — personal alerting. `AlertRule` (subject + comparison +
  threshold + window), `AlertSubject` (a `WeatherField` or forecast-only
  `PrecipChance`), `AlertWindow` (now / forecast horizons / overnight). The pure
  core is `AlertEvaluator` and `AlertTransitions` (notify-once dedup) over a
  `WeatherContext`; `AlertWorker` is the WorkManager I/O shell, `AlertNotifier`
  posts notifications, `AlertSettings` holds quiet hours.
- **`ui/theme/`** — the palette + type scale and `JustMyWeatherTheme`, which maps
  a pure `ThemeConfig` to a Material color scheme + typography in one place.
- **`ui/home/`**, **`ui/customize/`**, **`ui/alerts/`** — one view model + one
  screen each. `ui/home/DensitySpec` maps a `Density` to concrete sizes/spacing.

## Freshness

Two different clocks meet on the glance, and conflating them is the most
common misreading of this app.

- **Observation time** (`WeatherSnapshot.observedAt`) — when a real thermometer
  at a real station took the reading. This is what the "Observed HH:MM" line
  shows, and it advances only when the station publishes.
- **Fetch time** — when the app last asked. Deliberately not displayed.
- **Observation age** (`ui/home/ObservationAge`) — the gap between the
  observation time and now, printed beside the timestamp ("· 12 min ago"). It
  is derived from the first clock, never the second, so it cannot make an old
  reading look fetched-just-now. `ObservedLine` re-reads the clock on a timer,
  because an age rendered once goes stale while the screen is open.

So the timestamp not changing across a Refresh is normal, not a stale-cache bug
— and the age beside it is what confirms the fetch happened at all.
`WeatherRepository` caches only the coordinate → grid/station resolution;
`getObservation` hits `/observations/latest` every time, and `HttpTransport`
builds its `OkHttpClient` with no HTTP cache. The limits are upstream: station
cadence (5 minutes at KLOU/KSDF, hourly at many sites) and NWS's own
`max-age=183, s-maxage=300` on that endpoint.

The rule this encodes: **never substitute the current clock for a missing or
older observation time.** Doing so renders absent or stale data as freshly
observed. The same rule is why `Density` no longer hides the timestamp — a
number whose age is undiscoverable is what made the hero and the forecast strip
look like they were contradicting each other.

The one deliberate exception is the Gadgetbridge payload, which falls back to
`now` when a station omits its timestamp entirely: that field serialises to 0
otherwise, and a watch would render 1970 — worse than "about now" for a
consumer that keys freshness off it. It is commented as an exception at the
call site.

## Principles

- **Pure logic stays pure** so it tests on the JVM; side effects live at the
  edges. See `CONTRIBUTING.md`.
- **One seam per concern.** A new data source or cache is a change in one file.
- **Legibility is the spec** — this is meant to be forked. The common extension
  points each live in one obvious file; see `docs/extending.md`.
