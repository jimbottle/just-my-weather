# Play Store runbook

Detailed reference for Android releases. CLAUDE.md "Play Store Releases" has
the day-to-day commands; this file has the bootstrap, the console quirks, and
the lessons. Store copy and assets live in `store-assets/`
(`play-store-listing.md` is the canonical field-by-field reference).

## Account

- Developer account: **Raylytics, LLC**, an *organization* account (D-U-N-S
  13-903-9930), owner `evan@raylytics.io` with the `dev@raylytics.io` alias.
  Registered for Open Frame in spring 2026 — nothing to register again; this
  app is a new entry under the same account.
- Organization accounts are **exempt from the closed-testing gate** that new
  personal accounts face (12 testers for 14 days). An internal-track build and
  a filled listing can go straight to production review.
- Public contact for this app: `dev@raylytics.io` (already in `strings.xml`
  and in the NWS User-Agent). No per-app alias needed.

## Bootstrap (first time for this app)

### 1. Target API

Since 2026-08-31 Play requires **new apps and updates to target API 36**
(Android 16); an extension to 2026-11-01 can be requested in the console.
`app/build.gradle.kts` targets 35 today, so the first upload is blocked until
the SDK bump lands (see the tracked issue; AGP 8.7.3 only warns at 36, the
bump needs a newer AGP). Native libraries (`androidx.graphics.path`,
`datastore_shared_counter`) are already 16 KB page-aligned — verified on the
2026-10-01 bundle, so the 16 KB requirement is met.

### 2. Create the app entry (console, by hand)

Play Console → Create app: name (≤30 chars, see the listing file), default
language English (US), App, Free. Then **App content** declarations and the
**Main store listing**, all from `store-assets/play-store-listing.md`. Save
even when incomplete; the listing can be edited until the first production
review.

### 3. Privacy policy

Required: the app requests location. URL
`https://raylytics.io/justmyweather/privacy`, served by the `raylytics-site`
repo (`justmyweather/privacy.html`, Netlify pretty URLs strip `.html`). Source
of truth is `store-assets/privacy-policy.md`; change both and re-date both.

### 4. Signing

- `scripts/android/gen-upload-keystore.sh` creates `app/release.keystore`
  (alias `justmyweather-upload`) and `app/keystore.properties`. Both are
  gitignored; `storeFile` in the properties is **relative to `app/`**.
- Back up both files and the password in the password manager immediately.
- Play Console → Setup → App signing: accept **Google-managed app signing**
  (Play App Signing). Google then holds the app-signing key; our key is only
  the *upload* key, and a lost upload key can be reset via support. The first
  AAB uploaded establishes the upload certificate.
- `app/build.gradle.kts` signs the release build only when
  `keystore.properties` exists, so CI and contributors build unsigned. Use
  `scripts/android/bundle-release.sh`, which refuses to produce an unsigned
  bundle, rather than a bare `bundleRelease`.
- Switching a device from a debug build to a release-signed one needs a full
  uninstall first (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` otherwise), and an
  uninstall takes the app's data with it.

### 5. First upload is manual

The Play Developer API refuses bundles for an app that has never had one
uploaded through the console. Upload the first AAB by hand: Testing →
Internal testing → Create release → drop `app-release.aab`. Every upload
after that can go through the scripts.

### 6. Service account for scripted uploads (after the first manual upload)

1. Cloud Console → IAM & Admin → Service Accounts → create "just-my-weather
   ci" (any Raylytics project; Open Frame's project and its existing service
   account can be reused — then only step 4 is needed).
2. Keys → Add key → JSON. Save it as `play-service-account.json` at the repo
   root (gitignored by pattern) — then `play.properties` needs no edit.
3. **Enable the Play Android Developer API** on that project:
   `console.cloud.google.com/apis/library/androidpublisher.googleapis.com`.
   The most commonly missed step: the OAuth exchange still succeeds, every API
   call then returns 403.
4. Play Console → Users and permissions → invite the service-account email →
   Account permissions: **Release manager**, AND App permissions: tick Just My
   Weather. Both are required.
5. `cp play.properties.example play.properties`, then
   `scripts/android/preflight-play.sh`. Its failure messages name the panel to
   fix.

## Versioning

- `versionCode` lives in `app/build.gradle.kts`; `bump-version-code.sh`
  increments it by one. It must increase on **every** upload, failed ones
  included; gaps are fine, reuse is rejected.
- `versionName` is frozen once it reaches production. Work destined for a new
  release bumps `versionName` first, then `versionCode` ticks underneath it
  per upload. v0.1.0 and v0.1.1 shipped as GitHub sideload releases; the
  first Play build should carry the next version (0.2.0: places, the module
  grid, forecast tiles, fourteen-day daily).
- Log every cut in `docs/RELEASES.md` with its versionCode, date, and track.

## Console quirks (inherited from Open Frame; not yet seen here)

- "Version code X has already been used" — bump and rebuild; the number is
  burned even by an aborted draft.
- "This release does not add or remove any app bundles" — the draft has no
  AAB attached; upload to that release, not to the library.
- A production upload through the API lands as a **draft**; roll it out in
  the console, or promote a tested internal build with
  `scripts/android/promote-play.sh` instead of re-uploading.
- Data safety and the Advertising ID declaration are separate forms. This app
  uses neither ads nor the advertising ID; keep both answers "No".

## Store listing assets

See `store-assets/README.md`. Play enforces a **2:1 maximum aspect ratio** on
screenshots; raw 1080×2400 emulator captures are 2.22:1 and must go through
`store-assets/crop-screenshots.sh`. The screenshots in `docs/screenshots/`
predate the module grid and must be recaptured before submission.
