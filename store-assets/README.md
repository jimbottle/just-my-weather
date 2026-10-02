# Store assets

Everything the Google Play listing needs, with sources so it can be regenerated.

| File | Purpose |
|---|---|
| `play-store-listing.md` | **Canonical copy of every Play Console field.** Edit here first. |
| `short-description.txt` | ≤80-char short description (pasted verbatim) |
| `full-description.txt` | ≤4000-char full description (pasted verbatim; plain text, no Markdown) |
| `privacy-policy.md` | Policy source; rendered at https://raylytics.io/justmyweather/privacy from the `raylytics-site` repo |
| `permissions.txt` | Why each merged-manifest permission exists |
| `ASO_NOTES.md` | Search targets ("modular weather app", "custom weather"), where they live in the copy, and the rules that keep it policy-clean |
| `icon-512.svg` → `icon-512.png` | Play Store icon: the window-grid mark (sun, cloud, rain panes) on the dark field — upload the PNG. `icon-512.jpg` is the same art for places that want a JPEG; Play does not. |
| `ic_launcher_foreground.xml` | The mark as an Android vector; copied to `app/src/main/res/drawable/` |
| `feature-graphic-1024x500.svg` → `.png` | Listing hero |
| `generate-graphics.sh` | Renders both PNGs from the SVGs (needs `rsvg-convert`, `brew install librsvg`); first checks the launcher vector copies match |
| `crop-screenshots.sh` | Crops 1080×2400 emulator captures to 1080×2160 (Play's 2:1 limit) into `screenshots/final/` |
| `screenshots/` | Raw captures in; cropped output in `final/` |

The launcher icon the app ships is the vector in
`app/src/main/res/drawable/ic_launcher_foreground.xml` (a copy of
`store-assets/ic_launcher_foreground.xml`); `icon-512.svg` is the same geometry
as SVG, and the feature graphic embeds it, so the store art never drifts from
the launcher. Change the mark in all of them; `generate-graphics.sh` refuses to
run while the two launcher-vector copies differ. Two one-colour derivatives live in
the app only — `ic_launcher_monochrome.xml` (Android 13 themed icons) and
`ic_stat_notify.xml` (the notification icon): the frame and the sun, since the
clouds and rain flatten into a blob as a single tint.
