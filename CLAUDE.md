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
