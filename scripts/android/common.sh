#!/usr/bin/env bash
# Shared helpers for scripts/android/* — not executed directly.
#
# Ported from open-frame's scripts/android/common.sh; this repo IS the
# Android project (no android/ subdirectory, Kotlin DSL build file).
#
# Source this in every android/*.sh to get:
#   - $PROJECT_ROOT          repo root absolute path
#   - $BUILD_GRADLE          $PROJECT_ROOT/app/build.gradle.kts
#   - $AAB_PATH              path to the latest release AAB
#   - $PLAY_PROPERTIES       path to play.properties (repo root, gitignored)
#
# After calling load_play_properties:
#   - $PLAY_SERVICE_ACCOUNT  path to Google service-account JSON key
#   - $PLAY_TRACK            default Play track (internal/alpha/beta/production)
#   - $PLAY_PACKAGE_NAME     applicationId (defaults to io.raylytics.justmyweather)
#
# Env vars override the properties file (CI-friendly): if a variable
# is already set in the shell, the file value is ignored.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_GRADLE="$PROJECT_ROOT/app/build.gradle.kts"
AAB_PATH="$PROJECT_ROOT/app/build/outputs/bundle/release/app-release.aab"
PLAY_PROPERTIES="$PROJECT_ROOT/play.properties"

export PROJECT_ROOT BUILD_GRADLE AAB_PATH PLAY_PROPERTIES

die() {
  echo "error: $1" >&2
  exit 1
}

# Read a `key=value` properties file into env vars, skipping any whose env
# var is already set. printf -v + ${!name} rather than eval so a value
# containing shell metacharacters is stored, not executed.
load_play_properties() {
  local mapped_key value file="$PLAY_PROPERTIES"

  if [ ! -f "$file" ]; then
    if [ -z "${PLAY_SERVICE_ACCOUNT:-}" ]; then
      die "$file not found and PLAY_SERVICE_ACCOUNT not set in env. \
Copy play.properties.example to play.properties and fill in your service-account info."
    fi
    return 0
  fi

  while IFS='=' read -r key value || [ -n "$key" ]; do
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"

    case "$key" in
      ''|\#*) continue ;;
    esac

    case "$key" in
      serviceAccountJsonPath) mapped_key="PLAY_SERVICE_ACCOUNT" ;;
      track)                  mapped_key="PLAY_TRACK" ;;
      packageName)            mapped_key="PLAY_PACKAGE_NAME" ;;
      *) continue ;;
    esac

    if [ -z "${!mapped_key:-}" ]; then
      printf -v "$mapped_key" '%s' "$value"
      export "$mapped_key"
    fi
  done < "$file"

  if [ -z "${PLAY_TRACK:-}" ]; then export PLAY_TRACK="internal"; fi
  if [ -z "${PLAY_PACKAGE_NAME:-}" ]; then export PLAY_PACKAGE_NAME="io.raylytics.justmyweather"; fi
}

require_play_vars() {
  for v in "$@"; do
    if [ -z "${!v:-}" ]; then
      die "$v is required but not set. Check $PLAY_PROPERTIES or your env."
    fi
  done
}

# The Kotlin DSL line is `        versionCode = 2` — note the ` = `.
read_version_code() {
  sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$BUILD_GRADLE" | head -1
}

read_version_name() {
  sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$BUILD_GRADLE" | head -1
}

# BSD/GNU sed wrapper.
sedi() {
  if [ "$(uname)" = "Darwin" ]; then
    sed -i '' "$@"
  else
    sed -i "$@"
  fi
}

# One RS256 service-account JWT → OAuth token → Play Developer API helper,
# shared by preflight/upload/promote so the auth code lives in one place.
# Usage: play_node <script-body>; the body sees sa, pkg, base, request(),
# api(token, method, path, body?, contentType?) and getAccessToken().
play_node() {
  SERVICE_KEY="$SERVICE_KEY" PACKAGE_NAME="$PLAY_PACKAGE_NAME" node -e '
    const fs = require("fs");
    const crypto = require("crypto");
    const https = require("https");

    let sa;
    try {
      sa = JSON.parse(fs.readFileSync(process.env.SERVICE_KEY, "utf8"));
    } catch (e) {
      console.error(`fail: service-account JSON did not parse: ${e.message}`);
      process.exit(1);
    }
    if (!sa.client_email || !sa.private_key) {
      console.error("fail: service-account JSON missing client_email or private_key");
      process.exit(1);
    }
    const pkg = process.env.PACKAGE_NAME;
    const base = `/androidpublisher/v3/applications/${encodeURIComponent(pkg)}`;

    function rsaJwt() {
      const now = Math.floor(Date.now() / 1000);
      const header = Buffer.from(JSON.stringify({alg: "RS256", typ: "JWT"})).toString("base64url");
      const payload = Buffer.from(JSON.stringify({
        iss: sa.client_email,
        scope: "https://www.googleapis.com/auth/androidpublisher",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      })).toString("base64url");
      const signingInput = `${header}.${payload}`;
      const sig = crypto.createSign("RSA-SHA256").update(signingInput).sign(sa.private_key);
      return `${signingInput}.${sig.toString("base64url")}`;
    }

    // 120s covers a slow AAB upload; without a timeout a half-open socket
    // hangs the script forever with nothing for a CI runner to see.
    function request(opts, body) {
      return new Promise((resolve, reject) => {
        const r = https.request({timeout: 120000, ...opts}, res => {
          const chunks = [];
          res.on("data", c => chunks.push(c));
          res.on("end", () => resolve({status: res.statusCode, body: Buffer.concat(chunks).toString("utf8")}));
        });
        r.on("error", reject);
        r.on("timeout", () => r.destroy(new Error(`https timed out: ${opts.method} ${opts.path}`)));
        if (body) r.write(body);
        r.end();
      });
    }

    async function getAccessToken() {
      const form = `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${encodeURIComponent(rsaJwt())}`;
      const res = await request({
        hostname: "oauth2.googleapis.com", path: "/token", method: "POST",
        headers: {"Content-Type": "application/x-www-form-urlencoded", "Content-Length": Buffer.byteLength(form)},
      }, form);
      if (res.status !== 200) {
        console.error(`fail: OAuth token exchange returned HTTP ${res.status}`);
        console.error(res.body);
        console.error("");
        console.error("Common causes:");
        console.error("  - Play Android Developer API not enabled on the Cloud project:");
        console.error("    https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com");
        console.error("  - The service-account key was revoked (Cloud Console > IAM > Service Accounts > Keys).");
        process.exit(1);
      }
      return JSON.parse(res.body).access_token;
    }

    function api(token, method, path, body, contentType) {
      const headers = {Authorization: `Bearer ${token}`};
      let payload = body;
      if (body && !contentType) {
        payload = JSON.stringify(body);
        headers["Content-Type"] = "application/json";
      } else if (contentType) {
        headers["Content-Type"] = contentType;
      }
      if (payload) headers["Content-Length"] = Buffer.byteLength(payload);
      return request({hostname: "androidpublisher.googleapis.com", path, method, headers}, payload);
    }

    (async () => {
'"$1"'
    })().catch(err => {
      console.error(`fail: ${err && err.message ? err.message : err}`);
      process.exit(1);
    });
  '
}
