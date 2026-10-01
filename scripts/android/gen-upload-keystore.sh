#!/usr/bin/env bash
# Create the Play UPLOAD keystore once, plus the gitignored
# app/keystore.properties that app/build.gradle.kts reads to sign releases.
#
# With Play App Signing (the default for new apps, and what we enable), this
# key only signs what we upload; Google holds the key that signs what users
# install, and a lost upload key can be reset through Play Console support.
# It still must not leak: back up BOTH files and the password in the password
# manager right after running this (docs/PLAY_STORE.md "Signing").
#
# Usage:  scripts/android/gen-upload-keystore.sh
#         KEYSTORE_PASSWORD=... scripts/android/gen-upload-keystore.sh   # non-interactive
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

KEYSTORE="$PROJECT_ROOT/app/release.keystore"
PROPS="$PROJECT_ROOT/app/keystore.properties"
ALIAS="justmyweather-upload"
[ ! -e "$KEYSTORE" ] || die "$KEYSTORE already exists; refusing to overwrite an upload key."
[ ! -e "$PROPS" ] || die "$PROPS already exists; refusing to overwrite."
command -v keytool >/dev/null || die "keytool not found (it ships with the JDK)."
git -C "$PROJECT_ROOT" check-ignore -q app/release.keystore app/keystore.properties \
  || die ".gitignore does not cover the keystore files; fix that before creating secrets."

if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
  printf 'Keystore password (min 6 chars, store it in the password manager): ' >&2
  read -rs KEYSTORE_PASSWORD; echo >&2
fi
[ "${#KEYSTORE_PASSWORD}" -ge 6 ] || die "password must be at least 6 characters."
# keystore.properties is a Java .properties file: a backslash escapes, leading
# whitespace is stripped, a trailing space is invisible, and Gradle reads it
# as ISO-8859-1 while this shell writes UTF-8 — any of those would store a
# password that does not match the keystore, and signing would fail months
# later. Refuse rather than escape: printable ASCII only.
case "$KEYSTORE_PASSWORD" in
  *\\*) die "password must not contain a backslash (Java .properties escape character)." ;;
  [[:space:]]*|*[[:space:]]) die "password must not start or end with whitespace." ;;
esac
if printf '%s' "$KEYSTORE_PASSWORD" | LC_ALL=C grep -q '[^ -~]'; then
  die "password must be printable ASCII: Gradle reads keystore.properties as ISO-8859-1."
fi

# -storepass:env keeps the password out of argv, where any local process
# could read it from the process table while keytool runs.
export KEYSTORE_PASSWORD
keytool -genkeypair -v \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass:env KEYSTORE_PASSWORD -keypass:env KEYSTORE_PASSWORD \
  -dname "CN=Just My Weather, O=Raylytics LLC, L=Louisville, ST=Kentucky, C=US"

umask 077
cat > "$PROPS" <<PROPS
# Release signing — gitignored. storeFile is relative to app/.
storeFile=release.keystore
storePassword=$KEYSTORE_PASSWORD
keyAlias=$ALIAS
keyPassword=$KEYSTORE_PASSWORD
PROPS
chmod 600 "$KEYSTORE" "$PROPS"
echo "==> created $KEYSTORE (alias $ALIAS) and $PROPS" >&2
echo "    Back up both files and the password NOW (password manager), then" >&2
echo "    scripts/android/bundle-release.sh to prove a signed AAB builds." >&2
