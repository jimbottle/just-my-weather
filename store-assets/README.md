# Store assets

Everything the Google Play listing needs, with sources so it can be regenerated.

| File | Purpose |
|---|---|
| `play-store-listing.md` | **Canonical copy of every Play Console field.** Edit here first. |
| `short-description.txt` | ≤80-char short description (pasted verbatim) |
| `full-description.txt` | ≤4000-char full description (pasted verbatim; plain text, no Markdown) |
| `privacy-policy.md` | Policy source; rendered at https://raylytics.io/justmyweather/privacy from the `raylytics-site` repo |
| `permissions.txt` | Why each merged-manifest permission exists |
| `icon-512.svg` → `icon-512.png` | Play Store icon (same sun as the launcher icon, full-bleed) |
| `feature-graphic-1024x500.svg` → `.png` | Listing hero |
| `generate-graphics.sh` | Renders both PNGs from the SVGs (needs `rsvg-convert`, `brew install librsvg`) |
| `crop-screenshots.sh` | Crops 1080×2400 emulator captures to 1080×2160 (Play's 2:1 limit) into `screenshots/final/` |
| `screenshots/` | Raw captures in; cropped output in `final/` |

The launcher icon the app ships is the vector in
`app/src/main/res/drawable/ic_launcher_foreground.xml`; `icon-512.svg` is the
same geometry re-expressed as SVG so the store icon never drifts from it.
