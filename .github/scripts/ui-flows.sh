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
cd "$(git rev-parse --show-toplevel)"

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

adb -s "$SERIAL" install -r "$APK"

# Let the system settle before driving the UI. A freshly booted CI emulator is
# still starting launcher/system services, and if the launcher ANRs its dialog
# covers the app — every flow then fails at "is the app up?", which reads like
# a product bug and isn't one.
adb -s "$SERIAL" wait-for-device
until [ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
sleep 10

# Retry once. The flows hit the live NWS API, so absorb a transient blip here
# rather than by suppressing the job's result — a real regression fails both
# passes and the job goes red. The marker lets the workflow keep the debug
# artifact even when the retry succeeds, so recurring flakiness stays visible.
if ! maestro --device "$SERIAL" test .maestro/; then
  echo "::warning::first Maestro pass failed — retrying once"
  : > "${RUNNER_TEMP:-/tmp}/maestro-retried"
  maestro --device "$SERIAL" test .maestro/
fi
