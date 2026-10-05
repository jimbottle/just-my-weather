#!/bin/sh
# Crop raw 1080x2400 Pixel 7 captures (2.22:1) to 1080x2160 (2:1, Play's
# maximum aspect ratio) into screenshots/final/. Needs ImageMagick (`magick`).
#
#   store-assets/crop-screenshots.sh               # every PNG in screenshots/
#   store-assets/crop-screenshots.sh middle a.png  # anchor: top | middle | bottom
#
# Default anchor is "top": the banner ad sits at the foot of the glance and
# an emulator can only show labelled test ads, so the store set is cut so the
# banner falls below the 2:1 line (play-store-listing.md "Graphics"). "top"
# also keeps the title row of Alerts, Customize and Places. "middle" and
# "bottom" remain for a one-off capture that wants its centre or its foot.
set -eu
cd "$(dirname "$0")/screenshots"
command -v magick >/dev/null || { echo "magick not found: brew install imagemagick" >&2; exit 1; }
mkdir -p final
anchor=top
case "${1:-}" in top|middle|bottom) anchor=$1; shift ;; esac
[ $# -gt 0 ] || set -- *.png
for f in "$@"; do
    [ -f "$f" ] || continue
    h=$(magick identify -format '%h' "$f"); w=$(magick identify -format '%w' "$f")
    target=$((w * 2))
    [ "$h" -gt "$target" ] || { cp -f "$f" "final/$f"; echo "$f: already within 2:1"; continue; }
    case $anchor in
        top)    y=0 ;;
        bottom) y=$((h - target)) ;;
        *)      y=$(((h - target) / 2)) ;;
    esac
    magick "$f" -crop "${w}x${target}+0+${y}" +repage "final/$f"
    echo "$f: ${w}x${h} -> final/$f ${w}x${target} (anchor $anchor)"
done
