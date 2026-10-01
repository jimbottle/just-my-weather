# Privacy Policy — Just My Weather

_Last updated: October 1, 2026_

Just My Weather is a free, open-source Android weather app published by
Raylytics, LLC. It shows the weather for a place you choose, lets you build your
own view of it, and sends you notifications for conditions you define. This
policy explains what data the app accesses and what happens to it.

Canonical URL: https://raylytics.io/justmyweather/privacy

## Summary

**Just My Weather does not collect, store, sell, or share your personal
information.** It has no accounts, no backend server of its own, no ads, no
analytics, and no crash reporting. The developer never sees anything you do in
the app.

Everything you set up — which modules show, how they are arranged, your theme,
your saved places, your alert rules — stays on your phone. The only data that
leaves your device is the latitude and longitude of the place you are looking
at, sent to public weather services so the app can fetch the forecast.

## What the app accesses

**Your device's approximate location (optional).** If you grant the coarse
location permission, the app reads your device's approximate position to show
the weather where you are. It never requests precise (GPS-level) location. You
can decline the permission and pick a place by name instead.

Alert checks run in the background on the schedule you set. They watch the
place you chose or, if you follow the device, the last position the app knew.
On Android 9 and earlier that background check may also read your device's
current approximate position to keep that up to date; on Android 10 and later
the system does not give this app location in the background, so it uses the
last position from when the app was open.

**Places you choose.** You can search a list of about 32,000 US towns and
cities that is bundled inside the app, or type coordinates. The search runs
entirely on your phone; nothing you type is sent anywhere.

## Where your data goes

To fetch the weather, the app sends the coordinates of the selected place to:

- **The US National Weather Service** (api.weather.gov), a US federal agency,
  for current conditions and the hourly and seven-day forecasts. As the NWS
  requires, each request identifies the app and includes the developer's
  contact email; it does not include anything that identifies you.
- **Open-Meteo** (api.open-meteo.com), an open-source weather service, only
  when you have set the daily forecast to show more than seven days. Days
  eight onward come from Open-Meteo and are marked as such in the app.

Those services' own privacy practices apply to the requests they receive. The
app sends them coordinates and nothing else: no account, no device identifier,
no advertising ID.

All of the app's network traffic uses HTTPS.

## What stays on your phone

The app stores the following on your device only, in its private app storage:

- your view configuration (modules, sizes, order, labels, density, theme);
- your saved places and the last location the app used;
- your alert rules, whether each has fired, and your quiet-hours setting;
- a short-lived cache of the most recent weather so the app opens instantly.

Clearing the app's data in Android settings, or uninstalling the app, deletes
all of it. If you use Android's device backup, Android may include the app's
settings in your Google account backup under your control; the developer has
no access to that backup.

## Notifications

Personal alerts are evaluated on your phone, by the app, against the forecast
it already fetched. Notifications are generated and delivered locally. No
notification service outside your device is involved.

## Optional watch hand-off

If you turn on "Send to Gadgetbridge" in Customize, the app shares the current
weather with the Gadgetbridge app installed on the same phone, which can relay
it to a paired watch. This is a local hand-off between two apps on your device;
Gadgetbridge's own privacy terms cover what it does from there. The option is
off by default and only appears when Gadgetbridge is installed.

## Permissions

- **Location (approximate)** — optional; to show the weather where you are.
- **Notifications** — to deliver the alerts you set up (Android 13 and later
  ask you first).
- **Internet and network state** — to fetch forecasts from the services above.
- **Run at startup, wake lock, and foreground service** — added by Android's
  WorkManager library so your alert rules keep being checked after a reboot
  and on the schedule you chose. The app never shows a foreground service
  notification of its own.

## What the app does NOT do

- No accounts or sign-in.
- No advertising and no advertising ID.
- No analytics, telemetry, or crash reporting.
- No selling or sharing of personal information.
- No precise location, and no location history beyond the last position used.

## Open source

The app's source code is published under the Apache License 2.0, so anyone can
verify the statements in this policy by reading the code.

## Children

The app is intended for users 13 and older. It does not knowingly collect any
data from children.

## Changes

If this policy changes, the updated version will be published at the same URL
with a new "Last updated" date.

## Contact

Just My Weather is published by Raylytics, LLC. Questions about this policy or
how the app handles data: [dev@raylytics.io](mailto:dev@raylytics.io). This is
the same address the app's support entry uses.
