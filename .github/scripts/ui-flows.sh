#!/usr/bin/env bash
# Install the debug APK and run the Maestro suite against ONE explicit device.
#
#   CI:     bash .github/scripts/ui-flows.sh
#   local:  bash .github/scripts/ui-flows.sh emulator-5556
#
# Lives in a file rather than inline in the workflow because
# reactivecircus/android-emulator-runner executes its `script:` input ONE LINE
# PER `sh -c` — shell state does not carry between lines, so an inline
# `S=$(...)` on one line is empty on the next. (Found the hard way: the serial
# guard below fired in CI with an empty $S. Without it, `adb -s ""` would have
# silently fanned out to whatever single device was attached.)
set -euo pipefail

# Paths below are repo-relative; don't depend on where this was invoked from,
# or a local run from a subdirectory reports a missing APK that is really there.
# Assignment form on purpose: inside `cd "$(...)"` the substitution's exit
# status is discarded, so a missing git or a non-work-tree would leave `cd ""`
# as a silent no-op and we'd carry on from the wrong directory.
ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"

APK=app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] || { echo "::error::missing $APK — build it first"; exit 1; }

# Resolve the device explicitly: a bare `adb install` fans out to every attached
# device, and Maestro ignores ANDROID_SERIAL entirely (it needs --device).
#
# Auto-detect ONLY when exactly one emulator is attached. Picking "the first
# one" is wrong on a developer machine, where the first line may belong to
# another agent session or be a leftover orphan — this project has already had
# a build installed onto a physical phone that way. With several attached, say
# which: pass it as $1, or use the ownership procedure in CLAUDE.md (an
# emulator running this project's AVD and carrying our debug.jmw.owner claim).
SERIAL="${1:-${ANDROID_SERIAL:-}}"
if [ -z "$SERIAL" ]; then
  # No `mapfile`/`readarray` here: macOS ships bash 3.2 and this script is
  # advertised as locally runnable, so keep to POSIX-ish constructs.
  DEVICES=$(adb devices | awk '/^emulator-/ && $2 == "device" { print $1 }')
  COUNT=$(printf '%s' "$DEVICES" | grep -c . || true)
  case "$COUNT" in
    0) echo "::error::no booted emulator in 'adb devices'"; exit 1 ;;
    1) SERIAL="$DEVICES" ;;
    *) echo "::error::$COUNT emulators attached ($(echo $DEVICES)) — pass the one to use: $0 <serial>"; exit 1 ;;
  esac
fi
echo "device: $SERIAL"

# Readiness, bounded and loud. In CI the emulator-runner action has already
# waited for boot before invoking this script, so this is really for local runs
# — but an unbounded `until` would turn a wedged device into a silent hang that
# only ends at the job timeout, instead of a named error here.
booted=""
for _ in $(seq 60); do
  [ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] \
    && { booted=1; break; }
  sleep 2
done
[ -n "$booted" ] || { echo "::error::$SERIAL never reported sys.boot_completed"; exit 1; }

# THE mitigation for the launcher ANR that once covered the app and failed every
# flow at "is the app up?". Deterministic, unlike waiting: sys.boot_completed is
# set early and doesn't correlate with the ANR at all, and a fixed sleep is a
# guess that loses the race on a slow day. This suppresses system ANR/crash
# dialogs outright, so a struggling launcher can never sit on top of the app.
#
# TRADE-OFF: this is global, not launcher-scoped, so OUR app's ANR dialog is
# suppressed too — a main-thread stall that used to fail loudly could now be
# absorbed by Maestro's wait budgets. The logcat check after the run exists to
# put that failure back.
adb -s "$SERIAL" shell settings put global hide_error_dialogs 1

# Install after the readiness check and dialog suppression, so a locally
# invoked run can't install into a half-booted device. (In CI the action has
# already waited, so this ordering is belt-and-braces there.)
adb -s "$SERIAL" install -r "$APK"

# Bound the ANR check's window to THIS run. Without the clear it reaches
# backwards forever: a locally reused emulator carries ANRs from earlier runs
# and reddens an otherwise-clean job. -G widens the ring so a full suite plus a
# retry can't roll past an ANR from the first pass — the same claim in the
# other direction.
#
# `-b all` on BOTH, to match the buffers the check actually reads. A bare
# `logcat -c` only clears the default set (main, system, crash): measured on a
# booted API 35 emulator, a plain clear still left 699 KB visible to
# `-b all`, where `-b all -c` left 5 KB. Clearing less than you read is how the
# window silently stays unbounded.
#
# Tolerated if unsupported (older adb, restricted device): failing to clear
# makes the check over-eager, not blind, and must not kill the run before
# Maestro has even started.
adb -s "$SERIAL" logcat -b all -G 16M >/dev/null 2>&1 || true
adb -s "$SERIAL" logcat -b all -c >/dev/null 2>&1 \
  || echo "note: could not clear logcat; the ANR check may see entries predating this run"

# Retry once. The flows hit the live NWS API, so absorb a transient blip here
# rather than by suppressing the job's result — a real regression fails both
# passes and the job goes red. The marker is read by the workflow's upload
# condition so a failed first pass keeps its evidence even when the retry
# rescues it. It MUST live in the workspace: hashFiles() only hashes paths
# under $GITHUB_WORKSPACE and silently yields '' for anything outside it, which
# is why the previous $RUNNER_TEMP marker was never seen.
#
# Cleared up front so the marker means "THIS run retried". GitHub-hosted
# runners get a clean workspace from actions/checkout, but a self-hosted runner
# or a local invocation would otherwise keep a stale marker forever — and the
# upload condition would be unconditional again, which is the noise that keying
# on the marker removed.
RETRY_MARKER="${GITHUB_WORKSPACE:-$ROOT}/maestro-retried"
rm -f "$RETRY_MARKER"
if ! maestro --device "$SERIAL" test .maestro/; then
  echo "::warning::first Maestro pass failed — retrying once"
  : > "$RETRY_MARKER"
  echo "wrote retry marker: $RETRY_MARKER"
  maestro --device "$SERIAL" test .maestro/
fi

# hide_error_dialogs means an app-side ANR no longer shows a dialog, so check
# for it directly — otherwise a real responsiveness regression passes quietly.
#
# Dump to a file, then match the file — NOT `logcat | grep -q`. Under
# `set -o pipefail` that pipeline reports an ANR only when the match happens to
# sit near the END of the dump: `grep -q` exits at the first hit, `adb` takes a
# SIGPIPE, and pipefail hands the `if` a non-zero status, so the else branch
# runs and the ANR goes UNREPORTED. It's a race, not a constant — on a 400 KB
# device dump it reported correctly, while a 500k-line producer with an early
# match silently passed — which is worse than a plain bug, because it reads as
# verified until the day it matters.
#
# -b all, not crash,main: ActivityManager emits "ANR in <pkg>" through Slog,
# which writes to the SYSTEM buffer (the structured am_anr record goes to
# events). `crash` carries fatal-exception tombstones and `main` the app's own
# Log calls — neither is where this line lands. Reading every buffer is free
# here (measured rc=0, no per-buffer errors on API 35) and ends the guesswork.
#
# Bounded, because adb's logcat BLOCKS when the transport is gone: verified
# that `adb -s <bogus> logcat -d` never returns, while `get-state` and `shell`
# fail in milliseconds. So check presence with a command that fails fast, then
# put a watchdog on the read itself — otherwise a device that vanished after
# Maestro turns this into a silent wait for the job timeout, which is the
# failure mode the bounded boot poll above exists to avoid.
if ! adb -s "$SERIAL" get-state >/dev/null 2>&1; then
  echo "::error::$SERIAL is gone — the ANR check did NOT run"
  exit 1
fi
ANR_DUMP="${TMPDIR:-/tmp}/maestro-anr-logcat.$$"
adb -s "$SERIAL" logcat -d -b all >"$ANR_DUMP" 2>&1 & LOGCAT_PID=$!
for _ in $(seq 60); do kill -0 "$LOGCAT_PID" 2>/dev/null || break; sleep 1; done
if kill -0 "$LOGCAT_PID" 2>/dev/null; then
  kill -9 "$LOGCAT_PID" 2>/dev/null || true
  rm -f "$ANR_DUMP"
  echo "::error::logcat read from $SERIAL didn't finish in 60s — the ANR check did NOT run"
  exit 1
fi
if ! wait "$LOGCAT_PID"; then
  echo "::error::could not read logcat from $SERIAL — the ANR check did NOT run"
  cat "$ANR_DUMP"; rm -f "$ANR_DUMP"
  exit 1
fi
# grep against a FILE, so there is no pipeline for pipefail to misjudge.
if grep -q "ANR in io.raylytics.justmyweather" "$ANR_DUMP"; then
  echo "::error::the app ANR'd during the run (dialog suppressed; found in logcat)"
  # `|| true`: head -5 exits early and SIGPIPEs grep, and pipefail would make
  # set -e end the script here — skipping the cleanup below and exiting 141
  # instead of 1. Same trap as the bug this block fixes, one line further down.
  grep -n "ANR in io.raylytics.justmyweather" "$ANR_DUMP" | head -5 || true
  rm -f "$ANR_DUMP"
  exit 1
fi
rm -f "$ANR_DUMP"
