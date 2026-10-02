#!/bin/sh
# Render the Play Store icon and feature graphic from their SVG sources.
# Needs rsvg-convert (brew install librsvg). Output is opaque PNG — Play
# accepts alpha on the icon but a solid field is what the launcher shows.
set -eu
cd "$(dirname "$0")"
command -v rsvg-convert >/dev/null || { echo "rsvg-convert not found: brew install librsvg" >&2; exit 1; }
# The launcher vector is kept here beside its SVG twin and copied into the
# app; a drifted copy would ship one icon and list another.
cmp -s ic_launcher_foreground.xml ../app/src/main/res/drawable/ic_launcher_foreground.xml || {
    echo "ic_launcher_foreground.xml differs from app/src/main/res/drawable/ — copy the new mark across" >&2; exit 1; }
rsvg-convert -w 512  -h 512 icon-512.svg                 -o icon-512.png
rsvg-convert -w 1024 -h 500 feature-graphic-1024x500.svg -o feature-graphic-1024x500.png
for f in icon-512.png feature-graphic-1024x500.png; do
    echo "$f: $(sips -g pixelWidth -g pixelHeight "$f" | awk '/pixel/ {printf "%s ", $2}')"
done
