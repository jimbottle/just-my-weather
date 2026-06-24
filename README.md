# Just My Weather

A weather app that shows you exactly what you care about, arranged exactly how
you want it, alerts you on exactly the conditions you choose, and nothing else.

Most weather apps decide for you — ten-day forecasts, radar, air quality, pollen,
UV, hourly scrubbers — then bury the one number you opened the app to check. Just
My Weather inverts that. The default is quiet and sparse. The power is letting you
build your own view and your own alerts, and editing everything else away.

It's the personal-alerting sibling to a hazard-alert weather app: same underlying
[National Weather Service](https://www.weather.gov/documentation/services-web-api)
data, different intent. One watches for danger; this one watches for whatever
*you* tell it to.

## Status

Early. The foundation is in place:

- ✅ Project scaffold (Kotlin · Jetpack Compose · Material 3 · single module)
- ✅ Weather data layer over the free NWS API (no API key), ported from a
  battle-tested reference — retries, unit conversion, fully unit-tested
- ✅ The default minimalist home view: location · big temperature · one line of
  conditions
- ⏳ View customization (which data points, order, density, theme)
- ⏳ Personal rule-based alerting (thresholds, quiet by default)

See the issue tracker (beads) for the live plan.

## Build & run

Requires **JDK 17+** and the **Android SDK**. Point Gradle at your SDK:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # adjust path
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # run unit tests
scripts/hooks/install.sh              # optional: install the pre-commit gate
```

Install the debug APK on a device/emulator:

```bash
./gradlew :app:installDebug
```

## Contributing

This app is built to be forked. The code aims to be easy to read and easy to
change: adding a data point, an alert rule type, or a theme should be obvious,
not surgery. Start with [`CLAUDE.md`](CLAUDE.md) for the architecture map and
conventions.

## Data & privacy

Weather comes from the public US National Weather Service API — no key, no
account, US coverage. Location is **coarse** and optional (the platform location
provider, not Google Play Services); you can also set a place by hand. Personal
alerts are evaluated and delivered on-device.
