#!/usr/bin/env bash
# Increment versionCode in app/build.gradle.kts by 1 and print the new value.
# Play rejects any upload whose versionCode has been used before (including
# failed uploads), so every upload gets a bump. Two runs == two bumps.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

[ -f "$BUILD_GRADLE" ] || die "$BUILD_GRADLE not found"
current="$(read_version_code)"
[ -n "$current" ] || die "could not find 'versionCode = N' in $BUILD_GRADLE"
next=$((current + 1))
echo "==> versionCode: $current -> $next" >&2

sedi -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)$current\$/\\1$next/" "$BUILD_GRADLE"

new="$(read_version_code)"
[ "$new" = "$next" ] || die "versionCode bump failed (reads $new, expected $next)"
echo "$next"
