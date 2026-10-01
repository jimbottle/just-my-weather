#!/usr/bin/env bash
# Promote an ALREADY-UPLOADED build from one Play track to another.
#
# Promotion is a track update, not an upload: re-uploading the same AAB to
# production would be rejected ("Version code N has already been used").
# This does what the console's "Promote release" button does:
#   1. POST .../edits                      -> edit
#   2. GET  .../edits/{id}/tracks/{from}   -> assert the versionCode was DISTRIBUTED there
#   3. PUT  .../edits/{id}/tracks/{to}     -> release with that versionCode
#   4. POST .../edits/{id}:commit          -> live (production goes through Play review)
#
# Usage:
#   scripts/android/promote-play.sh [--version-code N] [--from internal] [--to production]
#                                   [--rollout 1.0] [--notes "user-facing text"]
#   --version-code defaults to app/build.gradle.kts; --rollout < 1.0 stages the rollout.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

FROM_TRACK="internal"; TO_TRACK="production"; ROLLOUT="1.0"; VERSION_CODE=""; NOTES=""
while [ $# -gt 0 ]; do
  case "$1" in
    --version-code) shift; VERSION_CODE="${1:-}"; shift ;;
    --from)         shift; FROM_TRACK="${1:-}"; shift ;;
    --to)           shift; TO_TRACK="${1:-}"; shift ;;
    --rollout)      shift; ROLLOUT="${1:-}"; shift ;;
    --notes)        shift; NOTES="${1:-}"; shift ;;
    --notes=*)      NOTES="${1#--notes=}"; shift ;;
    *) die "unknown argument: $1" ;;
  esac
done
for t in "$FROM_TRACK" "$TO_TRACK"; do
  case "$t" in internal|alpha|beta|production) ;; *) die "invalid track '$t'" ;; esac
done
[ "$FROM_TRACK" != "$TO_TRACK" ] || die "--from and --to are both '$TO_TRACK'"
if [ -z "$VERSION_CODE" ]; then
  VERSION_CODE="$(read_version_code)"
  [ -n "$VERSION_CODE" ] || die "could not read versionCode from $BUILD_GRADLE; pass --version-code"
fi
case "$VERSION_CODE" in ''|*[!0-9]*) die "--version-code must be an integer, got '$VERSION_CODE'" ;; esac

load_play_properties
require_play_vars PLAY_SERVICE_ACCOUNT PLAY_PACKAGE_NAME
SERVICE_KEY="${PLAY_SERVICE_ACCOUNT/#\~/$HOME}"
[ -f "$SERVICE_KEY" ] || die "service-account JSON not found at $SERVICE_KEY"

echo "==> Promoting versionCode $VERSION_CODE: $FROM_TRACK -> $TO_TRACK (rollout $ROLLOUT)" >&2

VERSION_CODE="$VERSION_CODE" FROM_TRACK="$FROM_TRACK" TO_TRACK="$TO_TRACK" ROLLOUT="$ROLLOUT" RELEASE_NOTES="$NOTES" play_node '
      const versionCode = process.env.VERSION_CODE;
      const fromTrack = process.env.FROM_TRACK, toTrack = process.env.TO_TRACK;
      const rollout = Number(process.env.ROLLOUT);
      const notes = process.env.RELEASE_NOTES;
      if (!(rollout > 0 && rollout <= 1)) { console.error(`--rollout must be in (0, 1], got ${process.env.ROLLOUT}`); process.exit(1); }

      const token = await getAccessToken();
      console.log("oauth: ok");
      const editRes = await api(token, "POST", `${base}/edits`);
      if (editRes.status !== 200) { console.error("create edit failed", editRes.status, editRes.body); process.exit(1); }
      const editId = JSON.parse(editRes.body).id;

      // A track can hold a "draft" nobody ever ran; only a rolled-out status
      // is evidence a tester had the build.
      const fromRes = await api(token, "GET", `${base}/edits/${editId}/tracks/${encodeURIComponent(fromTrack)}`);
      if (fromRes.status !== 200) { console.error(`read track ${fromTrack} failed`, fromRes.status, fromRes.body); process.exit(1); }
      const DISTRIBUTED = new Set(["completed", "inProgress", "halted"]);
      const releases = JSON.parse(fromRes.body).releases || [];
      const match = releases.find(r => (r.versionCodes || []).includes(String(versionCode)));
      if (!match) { console.error(`versionCode ${versionCode} is not on the ${fromTrack} track; refusing to promote an untested build.`); process.exit(1); }
      if (!DISTRIBUTED.has(match.status)) { console.error(`versionCode ${versionCode} is on ${fromTrack} only as status=${match.status} (never rolled out); refusing.`); process.exit(1); }
      console.log(`verified: versionCode ${versionCode} is on ${fromTrack} (status=${match.status})`);

      const release = {versionCodes: [String(versionCode)], status: rollout < 1 ? "inProgress" : "completed"};
      if (rollout < 1) release.userFraction = rollout;
      if (notes) release.releaseNotes = [{language: "en-US", text: notes}];
      const toRes = await api(token, "PUT", `${base}/edits/${editId}/tracks/${encodeURIComponent(toTrack)}`, {track: toTrack, releases: [release]});
      if (toRes.status !== 200) { console.error(`update track ${toTrack} failed`, toRes.status, toRes.body); process.exit(1); }
      const commitRes = await api(token, "POST", `${base}/edits/${editId}:commit`);
      if (commitRes.status !== 200) { console.error("commit edit failed", commitRes.status, commitRes.body); process.exit(1); }
      console.log(`==> Success: versionCode ${versionCode} promoted ${fromTrack} -> ${toTrack}` + (rollout < 1 ? ` at ${rollout * 100}%` : "") + ".");
      console.log("    Production goes through Play review; watch Play Console > Release > Production.");
'
