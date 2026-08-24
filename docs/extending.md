# Extending the app

The common things you'd want to add each live in one obvious place. Most catalogs
are enums consumed by exhaustive `when` expressions with no `else`, so when you
add an entry **the compiler points at every spot you still need to handle** —
follow the errors and you're done.

> Trap: inside a property accessor, a bare `field` is Kotlin's backing-field
> keyword. When you mean a constructor property named `field`, write `this.field`
> (see `FieldSetting.label` in `view/ViewConfig.kt`).

## Add a data point

Say you want **humidity** on the glance and as something to alert on.

1. **`view/WeatherField.kt`** — add the catalog entry and handle the exhaustive
   `when`s the compiler flags:
   ```kotlin
   HUMIDITY("humidity", "Humidity"),   // key is the stable persistence id — never rename it
   ```
   Add branches to `numericValue`, `formatValue` (e.g. `"${value.roundToInt()}%"`),
   `format` if it needs special wording, `forecastValue` (return `null` unless
   the forecast carries it — also flip `isForecastable` if it does), and
   `defaultSpan` (how wide the module ships on the glance grid — quarter for a
   short number, half for longer text). `isNumeric` is true for everything
   except `CONDITIONS`, so it's alertable automatically.
2. **`data/WeatherSnapshot.kt`** — add `val humidityPct: Double?`.
3. **`data/nws/`** — add the field to `NwsWire.ObservationProps`, project it in
   `NwsModels.CurrentObservation`, and read it in `NwsClient.getObservation`.
4. **`data/WeatherRepository.kt`** — map it onto the `WeatherSnapshot` in `load`.

The home view, the customize screen, and the alert builder all iterate
`WeatherField`, so the new point shows up in each with no further wiring.

## Add an alert subject (a forecast-only metric)

Subjects that aren't view fields — like chance of rain — are `AlertSubject`s.

1. **`alerts/AlertSubject.kt`** — add a subclass/`data object` implementing
   `currentValue` (return `null` if there's no live reading — it then never fires
   on a `NOW` rule), `forecastValue`, and `format`. Register it in `byKey` and in
   the `current` / `forecast` lists so the builder offers it in the right windows.
2. If it comes from the forecast, extend `data/nws/NwsModels.ForecastPoint` (and
   its parsing in `NwsClient.getHourlyForecast` / `NwsWire`) — as
   `precipProbabilityPercent` already does.

The evaluator compares whatever the subject reports against the threshold, so no
evaluator changes are needed.

## Add a forecast window

**`alerts/AlertWindow.kt`** — add an entry with its `key`, chip `label`, and
sentence `phrase`, then add a branch to `contains(time, now, zone)` describing
which forecast hours fall in it. `BELOW` rules watch the window's min, `ABOVE`
its max — that's handled centrally in `AlertEvaluator`.

## Add a theme accent or typeface

- **Accent:** add an `AccentChoice` entry in `view/ThemeConfig.kt`, a `Color` in
  `ui/theme/Color.kt`, and a branch in `accentColor(...)` in `ui/theme/Theme.kt`.
- **Typeface:** add a `TypeChoice` entry and a branch in `fontFamily(...)` in
  `ui/theme/Theme.kt`.

Both appear as chips on the customize screen automatically.

## Add a density level

Add a `Density` entry in `view/Density.kt` and a branch in `Density.spec()` in
`ui/home/DensitySpec.kt` with the full-module value size, the grid gap and the
section spacing. The customize chip row and home view read it from there.

Density controls size and spacing only — not which facts appear. A level that
hid a field would make the same fact discoverable at one setting and not
another, which is how the observation time came to be missing from the calmest
view while the forecast beside it looked like a contradiction.

## A new persisted setting

Mirror the existing config trio: a pure data class + a `*Codec` (JSON by stable
string keys, every field defaulted so old/partial/corrupt blobs degrade to the
default rather than crash) + a `*Repository` over the shared DataStore, wired in
`AppContainer`. `ThemeConfig` / `AlertSettings` are the templates.
