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

## Principles

- **Pure logic stays pure** so it tests on the JVM; side effects live at the
  edges. See `CONTRIBUTING.md`.
- **One seam per concern.** A new data source or cache is a change in one file.
- **Legibility is the spec** — this is meant to be forked. The common extension
  points each live in one obvious file; see `docs/extending.md`.
