#!/usr/bin/env bash
# Build a SIGNED release AAB at $AAB_PATH.
#
# app/build.gradle.kts signs the release build only when
# app/keystore.properties exists; otherwise it silently produces an unsigned
# bundle that Play will reject. So this script insists on the keystore and
# its properties up front (scripts/android/gen-upload-keystore.sh creates
# both) rather than letting the upload fail later with a burned versionCode.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

KEYSTORE_PROPS="$PROJECT_ROOT/app/keystore.properties"
[ -f "$KEYSTORE_PROPS" ] || die "$KEYSTORE_PROPS not found. Run scripts/android/gen-upload-keystore.sh (docs/PLAY_STORE.md 'Signing')."
store_file="$(sed -nE 's/^[[:space:]]*storeFile[[:space:]]*=[[:space:]]*(.*)$/\1/p' "$KEYSTORE_PROPS" | head -1)"
store_file="${store_file:-release.keystore}"
case "$store_file" in /*) KEYSTORE_PATH="$store_file" ;; *) KEYSTORE_PATH="$PROJECT_ROOT/app/$store_file" ;; esac
[ -f "$KEYSTORE_PATH" ] || die "upload keystore not found at $KEYSTORE_PATH (storeFile in keystore.properties is relative to app/)."

echo "==> Bundling signed release AAB (versionName $(read_version_name), versionCode $(read_version_code))" >&2
( cd "$PROJECT_ROOT" && ./gradlew :app:bundleRelease )
[ -f "$AAB_PATH" ] || die "bundle failed: $AAB_PATH was not produced"

# Belt and braces: an unsigned bundle has no META-INF signature block.
if ! unzip -l "$AAB_PATH" | grep -qE 'META-INF/.*\.(RSA|EC|DSA)$'; then
  die "$AAB_PATH is NOT signed: check app/keystore.properties (storeFile, storePassword, keyAlias, keyPassword)."
fi

echo "==> AAB ready: $AAB_PATH ($(du -h "$AAB_PATH" | awk '{print $1}'))" >&2
echo "$AAB_PATH"
