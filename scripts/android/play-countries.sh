#!/usr/bin/env bash
# Print the countries/regions a Play track is distributed in, as Play
# reports them (ISO 3166-1 alpha-2), one per line — read-only. Opens an
# edit, reads edits.countryavailability for the track, and discards the
# edit: net side effect none, exactly like preflight-play.sh.
#
# Used to keep app/src/main/java/.../region/Regions.kt honest against the
# console: scripts/check-regions.sh diffs this list with the registry's
# LIVE regions. See docs/REGIONS.md.
#
#   scripts/android/play-countries.sh [track]   # default: production
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

load_play_properties
require_play_vars PLAY_SERVICE_ACCOUNT PLAY_PACKAGE_NAME
SERVICE_KEY="${PLAY_SERVICE_ACCOUNT/#\~/$HOME}"
[ -f "$SERVICE_KEY" ] || die "service-account JSON not found at $SERVICE_KEY (configured in $PLAY_PROPERTIES)"
export COUNTRIES_TRACK="${1:-production}"

play_node '
      const track = process.env.COUNTRIES_TRACK;
      const token = await getAccessToken();
      const editRes = await api(token, "POST", `${base}/edits`);
      if (editRes.status !== 200) {
        console.error(`fail: HTTP ${editRes.status} creating edit`);
        console.error(editRes.body);
        process.exit(1);
      }
      const editId = JSON.parse(editRes.body).id;
      try {
        const res = await api(token, "GET", `${base}/edits/${editId}/countryAvailability/${encodeURIComponent(track)}`);
        if (res.status !== 200) {
          console.error(`fail: HTTP ${res.status} reading country availability for ${track}`);
          console.error(res.body);
          process.exitCode = 1;
          return;
        }
        const body = JSON.parse(res.body);
        if (body.restOfWorld) console.error(`note: ${track} is also open to "rest of world"`);
        for (const c of (body.countries || []).map(c => c.countryCode).sort()) console.log(c);
      } finally {
        await api(token, "DELETE", `${base}/edits/${editId}`);
      }
'
