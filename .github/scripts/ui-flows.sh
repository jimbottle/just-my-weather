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

# Capture the retry's status rather than letting `set -e` end the script here.
# The diagnostics below are MOST needed when the flows failed: a bare call made
# a failing retry exit on the spot, so the ANR/crash check ran only when BOTH
# passes had PASSED — the narrow "absorbed by Maestro's wait budgets" case. The
# common case was skipped exactly when it mattered, and since
# hide_error_dialogs removes the dialog, an ANR that genuinely breaks the flows
# surfaces only as "assertion timed out" — the shape this check exists to
# explain. The status is re-applied at the end of the script.
MAESTRO_RC=0
if ! maestro --device "$SERIAL" test .maestro/; then
  echo "::warning::first Maestro pass failed — retrying once"
  : > "$RETRY_MARKER"
  echo "wrote retry marker: $RETRY_MARKER"
  maestro --device "$SERIAL" test .maestro/ || MAESTRO_RC=$?
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
#
# Every "did NOT run" exit below reports $MAESTRO_RC too. The point of
# capturing it was that the flow result and the diagnostics are separate facts;
# dropping one of them here would lose the more actionable half — and a device
# that vanished mid-run is itself a plausible cause of the flow failure.
if ! adb -s "$SERIAL" get-state >/dev/null 2>&1; then
  echo "::error::$SERIAL is gone — the ANR/crash check did NOT run (maestro exited $MAESTRO_RC)"
  exit 1
fi
# mktemp, not a predictable "$$" path: `>` follows a symlink, so a pre-planted
# one on a shared /tmp would have this write somewhere else entirely. The trap
# is what makes cleanup survive an interrupt (Ctrl-C locally, a cancelled CI
# job) — with -G 16M this file is worth cleaning up — and it replaces the
# scattered rm -f calls, which every early exit below had to remember.
#
# stderr goes to its OWN file, not into the dump. Folding it in with 2>&1 let
# adb's failure text BE the evidence: `error: device offline` is ~24 bytes, so
# the dump was non-empty, passed the emptiness gate below, and both greps then
# found no ANR in what was never a log buffer — green on a check that never
# read one. Separating the streams removes that at the source, so "non-empty
# dump" can only mean actual log content, and adb's complaint is still
# available to print in the diagnostic.
ANR_DUMP=$(mktemp "${TMPDIR:-/tmp}/maestro-anr-logcat.XXXXXX")
ANR_ERR=$(mktemp "${TMPDIR:-/tmp}/maestro-anr-stderr.XXXXXX")
trap 'rm -f "${ANR_DUMP:-}" "${ANR_ERR:-}"' EXIT

adb -s "$SERIAL" logcat -d -b all >"$ANR_DUMP" 2>"$ANR_ERR" & LOGCAT_PID=$!
for _ in $(seq 60); do kill -0 "$LOGCAT_PID" 2>/dev/null || break; sleep 1; done
if kill -0 "$LOGCAT_PID" 2>/dev/null; then
  kill -9 "$LOGCAT_PID" 2>/dev/null || true
  echo "::error::logcat read from $SERIAL didn't finish in 60s — the ANR/crash check did NOT run (maestro exited $MAESTRO_RC)"
  exit 1
fi

# A non-zero read is not automatically a dead device: `-b all` also exits
# non-zero when a single buffer is unreadable, AFTER writing everything else —
# and that varies by image, while this script is advertised as runnable against
# whatever device you have. So judge on the DUMP, not the status.
#
# Emptiness is therefore tested FIRST and independently of $READ_RC. Gating it
# on a non-zero status (as this did) left the fail-open the rule exists to
# close: a logcat that exits 0 having written nothing makes both greps below
# report "no match", and the run is declared clean on no evidence at all. An
# empty dump means the check did not run, whatever the status says.
#
# A populated dump is still evidence even when the status is non-zero, so check
# it and say the coverage may be partial. Bounded to tail -50 because at
# -G 16M the whole file could be megabytes pasted into the CI log.
#
# `if`/`fi` rather than `[ -s "$ANR_ERR" ] && ...` for the stderr echoes: a
# failing `&&` list is a non-zero statement, which set -e would treat as the
# script's end — a diagnostic block that exits the script when there happens to
# be nothing to diagnose.
READ_RC=0
wait "$LOGCAT_PID" || READ_RC=$?
if [ ! -s "$ANR_DUMP" ]; then
  echo "::error::logcat from $SERIAL produced no log output (exit $READ_RC) — the ANR/crash check did NOT run (maestro exited $MAESTRO_RC)"
  if [ -s "$ANR_ERR" ]; then echo "adb said:"; tail -20 "$ANR_ERR"; fi
  exit 1
elif [ "$READ_RC" -ne 0 ]; then
  echo "::warning::logcat exited $READ_RC but wrote $(wc -c <"$ANR_DUMP") bytes of log — checking that partial dump; coverage may be incomplete"
  if [ -s "$ANR_ERR" ]; then echo "adb said:"; tail -20 "$ANR_ERR"; fi
  tail -50 "$ANR_DUMP"
fi

# Both halves below use ONE shape: grep the FILE once into a variable, test the
# variable, print a bounded excerpt. Never `grep | grep` or `grep -q` on a
# pipeline — that is the pipefail race fixed two commits ago, where grep exits
# at the first match, adb takes a SIGPIPE, and the non-zero pipeline status
# silently inverts the result. Capturing sidesteps it rather than papering over
# it, so neither half needs an escape hatch to explain.
#
# (`printf | head` still needs `|| true`: head exits early and SIGPIPEs printf,
# and under pipefail set -e would end the script mid-diagnosis — exiting 141
# and skipping the other half's report.)
FOUND=""

ANR=$(grep -n "ANR in io.raylytics.justmyweather" "$ANR_DUMP" || true)
if [ -n "$ANR" ]; then
  echo "::error::the app ANR'd during the run (dialog suppressed; found in logcat)"
  printf '%s\n' "$ANR" | head -5 || true
  FOUND=1
fi

# hide_error_dialogs suppresses CRASH dialogs as well as ANR ones, so restore
# this half too: a Java crash after a flow's last assertion would otherwise
# pass green, and the evidence is already sitting in the dump unread. Matched
# on AndroidRuntime's "Process: <pkg>" header line, which is what scopes this
# to OUR crash: an unrelated app dying on the device must not redden this job.
CRASH=$(grep -A2 "FATAL EXCEPTION" "$ANR_DUMP" || true)
case "$CRASH" in
  *"Process: io.raylytics.justmyweather"*)
    echo "::error::the app CRASHED during the run (dialog suppressed; found in logcat)"
    printf '%s\n' "$CRASH" | head -20 || true
    FOUND=1
    ;;
esac

[ -z "$FOUND" ] || exit 1

# Re-apply Maestro's result, which was captured rather than acted on above so
# these diagnostics could run on the failing path.
exit "$MAESTRO_RC"
