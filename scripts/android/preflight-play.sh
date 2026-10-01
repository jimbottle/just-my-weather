#!/usr/bin/env bash
# Validate the Play Console + service-account setup WITHOUT uploading
# anything or burning a versionCode. Run after the one-time bootstrap
# (docs/PLAY_STORE.md) and whenever the key is rotated. Confirms:
#   1. play.properties exists and points at a real JSON key
#   2. the key parses and the RS256 JWT exchanges for an OAuth token
#      (API enabled on the project, key not revoked)
#   3. an edit can be opened AND discarded for the package (service
#      account invited to Play Console with access to this app, and the
#      app exists under this package name) — net side effect: none.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

load_play_properties
require_play_vars PLAY_SERVICE_ACCOUNT PLAY_PACKAGE_NAME
SERVICE_KEY="${PLAY_SERVICE_ACCOUNT/#\~/$HOME}"
[ -f "$SERVICE_KEY" ] || die "service-account JSON not found at $SERVICE_KEY (configured in $PLAY_PROPERTIES)"

echo "==> Preflight check" >&2
echo "    package=$PLAY_PACKAGE_NAME  track=$PLAY_TRACK" >&2
echo "    serviceAccountJsonPath=$SERVICE_KEY" >&2

play_node '
      console.log(`ok: JSON parses; client_email=${sa.client_email}`);
      const token = await getAccessToken();
      console.log("ok: OAuth token exchange succeeded");

      const editRes = await api(token, "POST", `${base}/edits`);
      if (editRes.status === 200) {
        const editId = JSON.parse(editRes.body).id;
        console.log(`ok: edit created for ${pkg} (id=${editId}); discarding`);
        const dropRes = await api(token, "DELETE", `${base}/edits/${editId}`);
        if (dropRes.status >= 400) console.log(`  (warn: discard returned HTTP ${dropRes.status}; Play GCs uncommitted edits, harmless)`);
      } else if (editRes.status === 401 || editRes.status === 403) {
        console.error(`fail: HTTP ${editRes.status} creating edit: authenticated but not authorized for this app.`);
        console.error(editRes.body);
        console.error("Fix: Play Console > Users and permissions > the service-account row >");
        console.error(`App permissions > check "${pkg}"; Account permissions > Release manager.`);
        process.exit(1);
      } else if (editRes.status === 404) {
        console.error(`fail: HTTP 404: no app with packageName "${pkg}" reachable from this service account.`);
        console.error("Fix: packageName in play.properties must match applicationId in app/build.gradle.kts,");
        console.error("     the app entry must exist in Play Console (first upload is manual), and the");
        console.error("     service account must be granted this app under App permissions.");
        process.exit(1);
      } else {
        console.error(`fail: HTTP ${editRes.status} creating edit`);
        console.error(editRes.body);
        process.exit(1);
      }
      console.log("");
      console.log("Preflight passed: scripts/android/release-internal.sh should work.");
'
