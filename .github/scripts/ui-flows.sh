#!/usr/bin/env bash
# Install the debug APK and run the Maestro suite against one explicit device.
#
# Lives in a file rather than inline in the workflow because
# reactivecircus/android-emulator-runner executes its `script:` input ONE LINE
# PER `sh -c` — shell state does not carry between lines, so an inline
# `S=$(...)` on one line is empty on the next. (Found the hard way: the serial
# guard below fired in CI with an empty $S. Without the guard, `adb -s ""`
# would have silently fanned out to whatever single device was attached.)
#
# Runnable locally too:  bash .github/scripts/ui-flows.sh
set -euo pipefail

APK=app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] || { echo "::error::missing $APK — build it first"; exit 1; }

# Resolve the device explicitly: a bare `adb install` fans out to every attached
# device, and Maestro ignores ANDROID_SERIAL entirely (it needs --device). CI
# has exactly one emulator, but keeping the honest form means this is safe to
# copy onto a machine that doesn't.
SERIAL=$(adb devices | awk '/^emulator-/ && $2 == "device" { print $1; exit }')
[ -n "$SERIAL" ] || { echo "::error::no booted emulator in 'adb devices'"; exit 1; }
echo "device: $SERIAL"

adb -s "$SERIAL" install -r "$APK"

# Retry once. The flows hit the live NWS API, so absorb a transient blip here
# rather than by suppressing the job's result — a real regression fails both
# passes and the job goes red.
if ! maestro --device "$SERIAL" test .maestro/; then
  echo "::warning::first Maestro pass failed — retrying once"
  maestro --device "$SERIAL" test .maestro/
fi
