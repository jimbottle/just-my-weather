# Project Instructions for AI Agents

This file provides instructions and context for AI coding agents working on this project.

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:7510c1e2 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/SYNC_CONCEPTS.md for details and anti-patterns.

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->


## What this app is

**Just My Weather** is a minimalist, customizable weather app for Android. The
default is calm and sparse — one glance answers "what's it like right now." The
power is letting users build their own view (which data points, order, density,
look) and their own *personal* alerts (rule-based: "tell me when the overnight
low drops below 35°F"). It is the personal-alerting sibling to `almanac-bell`
(official hazard alerts); shared NWS data backbone, distinct intent. Built to be
open-sourced, so legibility of the code is a product requirement, not a nicety.

## Build & Test

Requires JDK 17+ and the Android SDK (`local.properties` → `sdk.dir`). Uses the
Gradle wrapper — no global Gradle needed.

```bash
./gradlew :app:testDebugUnitTest    # fast: JVM unit tests (JUnit 5)
./gradlew :app:ktlintCheck          # style gate
./gradlew :app:ktlintFormat         # auto-fix style
./gradlew :app:assembleDebug        # build the debug APK
scripts/hooks/install.sh            # install the pre-commit gate (test + ktlint)
```

The pre-commit hook runs `:app:testDebugUnitTest :app:ktlintCheck` on any
Kotlin/resource change. Bypass with `--no-verify` only in emergencies.

## Devices & background processes — leave the machine as you found it

An Android emulator costs ~8 CPU cores while it runs, and a forgotten one keeps
costing them for the rest of the session. Gradle/Kotlin daemons idle cheaply but
add up. **Every long-running process you start, you kill before you finish.**

Ownership is the whole game: **snapshot what was already running, and only ever
touch the difference.** Other agent sessions and the developer's Android Studio
own processes that look exactly like yours.

Three constraints shape the commands below. Agent tool calls **share no shell
state** — a variable set in one call is gone in the next, and an empty snapshot
silently means "everything is mine", the exact failure this exists to prevent —
so the snapshot lives in a **file**, and that file is **namespaced per session**
because concurrent agent sessions on one machine would otherwise overwrite each
other's. A warning that doesn't stop execution is not a guard: `adb -s ""` falls
back to "the only attached device", so every step using a serial must be
*gated*. And the boot never returns while the emulator lives, so it gets a call
of its own — the snapshot must be written and flushed *before* it.

```bash
# ── Preamble: run at the TOP OF EVERY CALL. Nothing carries between calls.
SDK=$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null); SDK=${SDK:-$ANDROID_HOME}
ADB="$SDK/platform-tools/adb"
# Per session: with a fixed path, a second session's call 1 would record YOUR
# emulator as "theirs" — your audit then passes while it runs on forever.
THEIRS=/tmp/jmw-theirs-emu-${CLAUDE_CODE_SESSION_ID:-$USER}
live() { "$ADB" devices | awk '/^emulator-/ && $2=="device" {print $1}'; }
mine() { live | grep -vxF -f "$THEIRS"; }        # valid only once $THEIRS exists

# ── Call 1 (foreground, on its own): record who was already here.
if [ ! -x "$ADB" ]; then
  echo "!! no SDK — set sdk.dir in local.properties or export ANDROID_HOME; STOP"
elif [ -f "$THEIRS" ]; then
  echo "!! snapshot already exists — an earlier run never cleaned up. Check it
        before overwriting: [$(tr '\n' ' ' < "$THEIRS")]"
else
  live > "$THEIRS"; echo "not mine, never touch: [$(tr '\n' ' ' < "$THEIRS")]"
fi

# ── Call 2 (background-run mode, never a bare '&'): boot. Alone, because it
#    does not return until the emulator exits.
"$SDK/emulator/emulator" -avd Pixel_7_API_35 -no-snapshot-save -no-audio -no-window

# ── Call 3: identify YOUR emulator, then build and install onto it — or do
#    nothing. The two failure modes are different: a missing snapshot is
#    recoverable by re-running call 1; a missing emulator means the boot failed.
if [ ! -f "$THEIRS" ]; then
  echo "!! no snapshot — re-run call 1 (recoverable; do NOT guess a serial)"
elif [ -z "$(mine | head -1)" ]; then
  echo "!! no new emulator — the boot failed. STOP: adb -s '' targets whatever
        single device is attached, possibly a personal phone."
else
  MINE=$(mine | head -1)
  "$ADB" -s "$MINE" shell getprop ro.build.version.release   # confirm the AVD you meant
  ./gradlew :app:assembleDebug \
    && "$ADB" -s "$MINE" install -r app/build/outputs/apk/debug/app-debug.apk
fi

# ── Call 4: after verifying, kill ONLY yours, wait for it to go, then audit.
if [ ! -f "$THEIRS" ]; then
  echo "!! no snapshot — cannot tell yours from another session's; list
        emulators and resolve by hand rather than killing anything"
else
  MINE=$(mine | head -1); [ -z "$MINE" ] || "$ADB" -s "$MINE" emu kill
  # adb keeps listing a dying emulator for a beat; let it actually go before
  # counting, or the audit cries wolf and you learn to ignore it.
  for _ in 1 2 3 4 5 6 7 8 9 10; do [ -z "$(mine)" ] && break; sleep 2; done
  if [ -z "$(mine)" ]; then
    echo "clean — 0 of mine still running"; rm -f "$THEIRS"   # only now
  else
    echo "!! STILL RUNNING after 20s: $(mine | tr '\n' ' ') — kill it by hand.
          Keeping the snapshot so you can still tell yours from theirs."
  fi
fi
```

**Daemons are deliberately not killed.** Gradle and Kotlin daemons idle near 0%
CPU and reap themselves on an idle timeout, while every way of killing them is
wrong: `./gradlew --stop` and `pkill -f KotlinCompileDaemon` also stop Android
Studio's, costing the developer their warm build; filtering by JDK path
misjudges ownership in both directions; and a before/after PID diff blames you
for any daemon that merely *appeared* later — including one Studio spawned for a
mid-session sync. The emulator is the process worth chasing: it costs ~8 cores,
where a daemon costs some idle RAM. If you do want to look, `pgrep -f
'[G]radleDaemon|[K]otlinCompileDaemon'` lists them — informational only.

Two shell details worth knowing, both verified on this machine. Prefer `pgrep`
over `ps aux | grep`: `ps` truncates to terminal width on a tty and silently
drops daemon matches, because the class name trails a very long classpath (3
daemons via a pipe, only 2 through an 80-column terminal). And if you do use
`ps | grep`, bracket the pattern (`[G]radleDaemon`) so the pipeline doesn't
match its own shell — `ps auxww | grep` matched 3 processes for a sentinel that
existed only in the script text, whereas macOS `pgrep -f` matched 0. Don't rely
on that difference: Linux `pgrep` reads `/proc/*/cmdline` and *does* match the
invoking shell, so bracket everywhere and never pipe such a list into `kill`.

Rules that follow from this:

- **Boot per task, not per session.** A headless AVD boots in ~60s; that is
  cheaper than leaving one running for an hour. Never keep one "warm".
- **Kill only the emulator you started, identified by diff.** Never `xargs` a
  kill across every listed emulator and never hardcode `emulator-5554` — ports
  get reused, and the device on one may be another session's. Ownership comes
  from the before/after snapshot, never from what a process looks like: an agent
  building under Studio's bundled JBR is indistinguishable by JDK path, so such
  a filter both masks your own leak and flags processes that aren't yours.
- **Always target a device explicitly.** A bare `installDebug` or `adb install`
  fans out to *every* attached device, including a developer's personal phone
  and other projects' emulators. The unambiguous path is the two-step build and
  install in call 2 above (`./gradlew :app:assembleDebug`, then
  `adb -s <the serial from mine()> install -r
  app/build/outputs/apk/debug/app-debug.apk`).
  (`ANDROID_SERIAL` steers the `adb` CLI, but AGP's own install tasks talk to
  devices via ddmlib, so don't assume it constrains them; the
  `android.injected.device.serial` property could not be confirmed in the pinned
  AGP 8.7.3 artifacts, and Gradle silently ignores unknown `-P` flags — an
  unverified flag gives false confidence, which is how the fan-out happens.)
- **Prefer the JVM gate.** Unit tests + ktlint + assemble need no device at all;
  reach for an emulator only when the change is genuinely visual or runtime
  (insets, notifications, WorkManager, permissions).
- UI verification runs on **API 35+** (Android 15 enforces edge-to-edge for
  `targetSdk 35`; API 34 does not, and that gap once shipped a broken layout).

## Architecture Overview

Single `app` module, Kotlin + Jetpack Compose + Material 3, MVVM. Manual DI (one
readable `AppContainer` in `JustMyWeatherApp.kt` — no Hilt). Package layout under
`io.raylytics.justmyweather`:

- **`data/nws/`** — the weather backbone, ported from `almanac-bell`. `NwsClient`
  talks to the free public NWS API (api.weather.gov, no key); `HttpTransport`
  isolates the HTTP call so the retry/parse logic is unit-tested with no network;
  `Units` holds the pure conversions (°C→°F, mm→in, →mph, Pa→inHg, wind parsing);
  `NwsWire` is the JSON shapes, `NwsModels` the cleaned-up domain.
- **`data/`** — `WeatherRepository` is the single seam between NWS and the app
  (cache point resolution, fill labels). `WeatherSnapshot` is the one domain
  object the UI depends on — wire shapes never leak past the repository.
- **`location/`** — `LocationProvider` over the platform `LocationManager`
  (coarse only, no Play Services, so it builds from source anywhere).
- **`ui/theme/`** — the small palette + type scale, all in one place so the
  customization layer has obvious files to drive.
- **`ui/home/`** — the default glance: `HomeViewModel` exposes a single sealed
  `HomeUiState`; `HomeScreen` renders it.

Data flow: `HomeViewModel` → `WeatherRepository` → `NwsClient` → `HttpTransport`.

## Conventions & Patterns

- **Kotlin + Compose only.** Material 3. ktlint enforces style (config in
  `.editorconfig`: 120 cols, required trailing commas, Composables may be
  PascalCase). Run `ktlintFormat` before committing.
- **Pure logic stays pure.** Conversions and (later) the alert evaluator take
  data in, return data out — no I/O, no clock reads, no Android types — so they
  test on the JVM. Push side effects to the edges (transport, repository, VM).
- **One seam per concern.** Swapping the weather source or adding a cache happens
  only in `WeatherRepository`; the wire DTOs never escape `data/nws`.
- **Legibility is the spec.** This is going open source. Favor a well-named
  simple piece over a clever abstraction; comment the *why*. Adding a data point,
  an alert rule type, or a theme should be obvious — keep those extension points
  in one file each.
- **Tests:** JUnit 5 (`useJUnitPlatform`), Mockito-Kotlin, `runTest` for
  coroutines. Unit tests mirror the main package under `app/src/test/`.

<!-- BEGIN WYK CONVENTIONS v:1 -->
## wyk — planning & handoff over bd

This repo uses **wyk**, a view + handoff layer over **bd (beads)**. "Plan
it in wyk" = **file the plan as bd issues** (deps via `bd dep add`), not
markdown/TodoWrite. File with **`wyk create`** (same flags as `bd create`,
forwarded verbatim) — it also stamps the Claude session so the TUI's
Session column traces work back to a conversation. A PreToolUse hook
blocks raw `bd create` and tells you to switch; that's expected — just
re-run as `wyk create`.

**Owner column** — whose move it is, label-driven (NOT bd's owner/assignee):
- `human` → **HUMAN** (a human must act).
- `agent-handoff` → **AGENT-HANDOFF**: another agent owns it; don't touch,
  a human coordinates. Excluded from `wyk inbox`.
- agent task blocked by a `human`-flagged dep → **HUMAN-BLOCK** (skip it).
- else → **AGENT** (the default; a null owner is never blank — so a task
  that needs a human MUST be handed off, or the human never sees it).

**Hand off to a human**: `wyk handoff <id>` (or `wyk handoff -create "<title>"`)
sets `human` + writes the runbook. Never hand-roll labels; `-a`/`--claim`
are bd's status, not the badge.

**Pick up work**: `wyk inbox` FIRST (items bounced back to you — WORK them),
then `wyk` / `bd ready`. `wyk conventions` prints the full contract.

**Something wrong? Act — don't shrug.** If a wyk/bd command errors, a
convention looks broken, or the workflow rubs wrong, file a bd issue (with
an owner) and fix or hand it off — don't route around it silently.
Friction with wyk is product data; surfacing it is the job.
<!-- END WYK CONVENTIONS -->
