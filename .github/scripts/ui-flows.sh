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
adb -s "$SERIAL" shell settings put global hide_error_dialogs 1

# Install after the device is quiet: a PackageManager scan plus dexopt is one of
# the heavier things to ask of a freshly booted system, so it belongs on the
# settled side of the wait rather than contending with startup.
adb -s "$SERIAL" install -r "$APK"

# Retry once. The flows hit the live NWS API, so absorb a transient blip here
# rather than by suppressing the job's result — a real regression fails both
# passes and the job goes red. The marker below is READ by the workflow's
# upload condition, so the debug output of a failed first pass survives even
# when the retry rescues it — otherwise recurring flakiness is unmeasurable.
if ! maestro --device "$SERIAL" test .maestro/; then
  echo "::warning::first Maestro pass failed — retrying once"
  : > "${RUNNER_TEMP:-/tmp}/maestro-retried"
  maestro --device "$SERIAL" test .maestro/
fi
