#!/usr/bin/env bash
# Orchestrator: cut a Play "internal testing" build in one command.
#
#   1. refuse a dirty working tree    — the build must be a committed state
#   2. scripts/verify.sh              — the JVM gate (tests, ktlint, APKs)
#   3. bump-version-code              — Play's monotonic-int rule
#   4. bundle-release                 — signed AAB
#   5. upload-play --track internal   — live for testers in minutes, no review
#
# Afterwards: commit the versionCode bump and log the cut in docs/RELEASES.md.
# Only --notes, --status and --sample-ads are accepted; anything else (a
# different track, an explicit AAB) contradicts the intent of this script —
# run upload-play.sh directly. Without app/admob.properties the release
# build serves Google's SAMPLE ads; that is fine for a build testers will
# look at but must never reach production, so it needs --sample-ads to say
# so out loud (app/build.gradle.kts, store-assets/README.md). Before the app's first production release Play accepts only
# draft releases, so until launch run this with `--status draft` and roll
# the draft out from the console (docs/PLAY_STORE.md).
#
# Agents: CLAUDE.md "Release confirmation gate" — every run needs Evan's
# go-ahead for THIS upload, in the current exchange.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

forwarded_args=()
sample_ads=0
while [ $# -gt 0 ]; do
  case "$1" in
    --sample-ads) sample_ads=1; shift ;;
    --notes) shift; [ $# -gt 0 ] || die "--notes requires a value"; forwarded_args+=(--notes "$1"); shift ;;
    --notes=*) forwarded_args+=("$1"); shift ;;
    --status) shift; [ $# -gt 0 ] || die "--status requires draft or completed"; forwarded_args+=(--status "$1"); shift ;;
    --status=*) forwarded_args+=("$1"); shift ;;
    *) die "release-internal.sh only accepts --notes <text>, --status draft|completed and --sample-ads. For other tracks or AAB paths, run upload-play.sh directly." ;;
  esac
done

cd "$PROJECT_ROOT"
# The file alone proves nothing: a copy of the example, a missing key (Gradle
# falls back to the sample id, silently, because the file exists) or the
# sample ids pasted in would all ship a build with no real ads.
admob_problem=""
if [ ! -f app/admob.properties ]; then
  admob_problem="app/admob.properties is missing"
else
  for key in appId bannerUnitId; do
    value="$(sed -nE "s/^[[:space:]]*$key[[:space:]]*=[[:space:]]*(.*)$/\1/p" app/admob.properties | head -1)"
    case "$value" in
      "") admob_problem="$key is missing from app/admob.properties" ;;
      ca-app-pub-3940256099942544*) admob_problem="$key is Google's sample id" ;;
      *XXXX*|*YYYY*|*ZZZZ*) admob_problem="$key is still the example placeholder" ;;
    esac
    [ -n "$admob_problem" ] && break
  done
fi
if [ -n "$admob_problem" ]; then
  if [ "$sample_ads" = 1 ]; then
    echo "!! $admob_problem: this build serves Google's SAMPLE ads. Internal testing only — do NOT promote it." >&2
  else
    die "$admob_problem, so this build would serve Google's SAMPLE ads. Fix app/admob.properties (see app/admob.properties.example, just-my-weather-1zp) or pass --sample-ads for a testers-only build."
  fi
fi
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  die "working tree has uncommitted changes; commit (or stash) first so the cut is a known commit."
fi
# The Maestro flows are not run here (they need an emulator); CI's ui job
# covers the commit being released. Check it is green before cutting.
scripts/verify.sh

NEW_VC="$(bash "$SCRIPT_DIR/bump-version-code.sh" | tail -n 1)"
echo "==> versionCode bumped to $NEW_VC (versionName $(read_version_name))" >&2

# From here the tree is dirty (the bump). A failure must say how to get back
# to a state this script's own clean-tree guard will accept.
uploaded=0
on_fail() {
  [ "$uploaded" = 1 ] && return
  cat >&2 <<MSG

!! release-internal.sh failed after bumping versionCode to $NEW_VC.
   Look at the output above:
   - If it shows "uploaded: versionCode=$NEW_VC", the number is BURNED even
     though the release failed: commit the bump, then re-run (a fresh bump).
   - Otherwise nothing reached Play: git checkout app/build.gradle.kts
     to undo the bump, fix the cause, and re-run.
   A "draft app" rejection means the app has never been published: re-run
   with --status draft and roll the draft out from Play Console.
MSG
}
trap 'on_fail' EXIT
bash "$SCRIPT_DIR/bundle-release.sh"
bash "$SCRIPT_DIR/upload-play.sh" --track internal ${forwarded_args[@]+"${forwarded_args[@]}"}
uploaded=1
trap - EXIT

cat >&2 <<MSG

==> Internal release pipeline complete: versionCode $NEW_VC is on the internal track.
    Now: git commit app/build.gradle.kts -m "release: versionCode $NEW_VC to internal"
         and add the cut to docs/RELEASES.md.
MSG
