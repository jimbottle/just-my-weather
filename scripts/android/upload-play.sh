#!/usr/bin/env bash
# Upload the AAB at $AAB_PATH to Google Play via the Play Android Developer
# API, attach it to a track, and commit the edit.
#
#   1. RS256 JWT from the service-account key -> short-lived OAuth token
#   2. POST .../edits                                 -> edit id
#   3. POST .../edits/{id}/bundles?uploadType=media   -> versionCode
#   4. PUT  .../edits/{id}/tracks/{track}             -> release + notes
#   5. POST .../edits/{id}:commit                     -> live on the track
#
# Usage:
#   scripts/android/upload-play.sh                               # $PLAY_TRACK (default internal)
#   scripts/android/upload-play.sh --track alpha
#   scripts/android/upload-play.sh --notes "What testers will see"
#   scripts/android/upload-play.sh /path/to/explicit.aab
#
# A production upload lands as a DRAFT release; roll it out in the console,
# or use promote-play.sh to move a tested internal build instead.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

override_aab=""; override_track=""; override_notes=""
while [ $# -gt 0 ]; do
  case "$1" in
    --track) shift; override_track="${1:-}"; shift ;;
    --track=*) override_track="${1#--track=}"; shift ;;
    --notes) shift; override_notes="${1:-}"; shift ;;
    --notes=*) override_notes="${1#--notes=}"; shift ;;
    -*) die "unknown flag: $1" ;;
    *) override_aab="$1"; shift ;;
  esac
done

load_play_properties
require_play_vars PLAY_SERVICE_ACCOUNT PLAY_TRACK PLAY_PACKAGE_NAME
SERVICE_KEY="${PLAY_SERVICE_ACCOUNT/#\~/$HOME}"
[ -f "$SERVICE_KEY" ] || die "service-account JSON not found at $SERVICE_KEY"

TRACK="${override_track:-$PLAY_TRACK}"
case "$TRACK" in
  internal|alpha|beta|production) ;;
  *) die "invalid --track '$TRACK': must be internal, alpha, beta, or production" ;;
esac

TARGET_AAB="${override_aab:-$AAB_PATH}"
[ -f "$TARGET_AAB" ] || die "AAB not found at $TARGET_AAB. Run scripts/android/bundle-release.sh first."

# --notes wins; otherwise a placeholder. Several internal builds ship per
# versionName, so docs/RELEASES.md is not parsed automatically.
RELEASE_NOTES="${override_notes:-Routine improvements and bug fixes.}"

echo "==> Uploading $TARGET_AAB to Play track=$TRACK (package=$PLAY_PACKAGE_NAME)" >&2

AAB="$TARGET_AAB" TRACK="$TRACK" RELEASE_NOTES="$RELEASE_NOTES" play_node '
      const token = await getAccessToken();
      console.log("oauth: ok");

      const editRes = await api(token, "POST", `${base}/edits`);
      if (editRes.status !== 200) { console.error("create edit failed", editRes.status, editRes.body); process.exit(1); }
      const editId = JSON.parse(editRes.body).id;
      console.log(`edit: ${editId}`);

      const aabBytes = fs.readFileSync(process.env.AAB);
      const uploadRes = await api(token, "POST",
        `/upload${base}/edits/${editId}/bundles?uploadType=media`, aabBytes, "application/octet-stream");
      if (uploadRes.status !== 200) { console.error("AAB upload failed", uploadRes.status, uploadRes.body); process.exit(1); }
      const versionCode = JSON.parse(uploadRes.body).versionCode;
      console.log(`uploaded: versionCode=${versionCode} (${aabBytes.length} bytes)`);

      const track = process.env.TRACK;
      const trackRes = await api(token, "PUT", `${base}/edits/${editId}/tracks/${encodeURIComponent(track)}`, {
        track,
        releases: [{
          status: track === "production" ? "draft" : "completed",
          versionCodes: [String(versionCode)],
          releaseNotes: [{language: "en-US", text: process.env.RELEASE_NOTES}],
        }],
      });
      if (trackRes.status !== 200) { console.error("attach to track failed", trackRes.status, trackRes.body); process.exit(1); }
      console.log(`attached to track=${track}`);

      const commitRes = await api(token, "POST", `${base}/edits/${editId}:commit`);
      if (commitRes.status !== 200) { console.error("commit edit failed", commitRes.status, commitRes.body); process.exit(1); }
      console.log(`committed edit ${editId}`);
      console.log("");
      console.log(`==> Success: versionCode ${versionCode} is on the ${track} track.`);
      console.log(`    Play Console > Just My Weather > Testing > ${track}`);
'
