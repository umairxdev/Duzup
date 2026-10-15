# Duzup: implementation status

_Last updated: 2026-10-15. Everything below was verified by running the commands shown unless it is listed under
"Not verified"._

## Toolchain

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.4.1 (built-in Kotlin) |
| Gradle | 9.8.1 (wrapper) |
| Kotlin / KSP | 2.4.21 / 2.3.12 |
| Compose BOM | 2026.09.00 |
| Hilt / Room | 2.60.1 / 2.8.5 |
| Compile / target / min SDK | 37 / 36 / 26 |
| JDK used | Android Studio JBR 21 |

Hilt 2.60 requires AGP 9, which in turn required Gradle ≥ 9.4 and compileSdk 37 (androidx libraries in the BOM require it).

## Phase status

| Phase | Status |
|---|---|
| 1 Foundation: tokens, theme, icons, Room, DataStore, navigation, tests | Done |
| 2 Core workflow: onboarding, add/edit, recurrence, occurrences, dashboard, details, payments | Done |
| 3 Notifications: permissions, exact/inexact alarms, receivers, actions, snooze, cancel, reconcile | Done (logic verified by tests; device behaviour not verified, see below) |
| 4 Full app: calendar, stats, history/all bills, settings, backup/restore, dark mode | Done |
| 5 Polish and QA: reference comparison, responsive/accessibility checks, docs, build | Done to the extent possible without a device (see below) |

## Feature checklist

- [x] Six kinds: Bill, Event, Task, Note, Birthday, Routine, each with its own form, row, detail screen and defaults
- [x] Quick add parser ("call mom tomorrow 6pm"), "What are we adding?" picker, kind filter chips on Home
- [x] Home feed: Late / Today / Coming up / Done; Notes tab (pinned notes, quick capture); Stats and history reached from Settings and the Home money card
- [x] Bills: one-time and recurring (daily, weekly, every 2 weeks, monthly, quarterly, yearly, custom), optional currency-aware amount, paid/undo/skip, payment history, monthly totals that never mix currencies
- [x] Events (start/end, all-day, place), tasks (priority, late tracking), birthdays (yearly, optional age), routines (several times per day, chosen weekdays, per-time ticks)
- [x] Reminders: per-kind default offsets in minutes/hours/days, quiet hours (never delays past the due moment), snooze choices (10 min, 1 h, 3 h, tomorrow morning), late nudge for bills and tasks
- [x] 8 notification channels, per-kind actions (Paid it / Done / Got it, Snooze, Open), Gen Z wording pools (no emojis), deterministic variant per reminder, no fake zero amounts
- [x] Calendar with per-kind dots, search/history with kind filter, archive and restore, reminder diagnostics, settings, onboarding, intro screen, Duzup bell icon and intro splash
- [x] Backup v2 (v1 Remzup files still import), validated, transactional, replace or merge, reminders rebuilt
- [x] No INTERNET permission, no login, no Firebase, no demo data in the app (fixtures exist only in test sources)

## Verified results (run on this machine, no emulator)

| Command | Result |
|---|---|
| `./gradlew assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL |
| `./gradlew testDebugUnitTest` | 206 tests, 0 failures |
| `./gradlew lintDebug` | 0 errors |
| `./gradlew assembleDebugAndroidTest` | BUILD SUCCESSFUL (compiled, **not run**, no device) |

Highlights: quick-add parser 27 tests, notification wording 5, every kind x phase through the real notification builder,
kind flows (routines, quiet hours, snooze, birthdays on Feb 29), draft validation, planners, backup codec and restore,
Compose behaviour tests and screenshot smoke tests for every screen.

## Manual acceptance checklist (AGENTS.md §12)

| # | Scenario | Evidence |
|---|---|---|
| 1 | One-time bill persists and schedules its reminder | Integration test `oneTimeBill_schedulesTheDefaultFourPlusOverdue`; process restart not exercised on a device |
| 2 | Monthly bill on Jan 31 → Feb 28 → Mar 31 | Unit + integration tests |
| 3 | Pay one recurrence, next stays pending | Integration test |
| 4 | Edit date/offsets cancels old alarms, schedules new | Integration tests |
| 5 | Snooze and pay from a notification, no duplicates | Integration tests drive the same repository calls the receivers use; the receivers themselves were not run |
| 6 | Notifications denied: app usable, limitation explained | Compose tests for the banners; real permission flow not run |
| 7 | Reboot reconciliation | Integration test `afterReboot_reconcileRestoresEveryFutureAlarm` (simulated); a real reboot was not run |
| 8 | Export, clear data, import | Integration tests (round trip, merge, replace, rejection) ; SAF pickers not run |
| 9 | Two currencies and unknown amounts | Unit + UI tests |
| 10 | No Internet permission, login, Firebase or fake data | Merged manifest inspected; no such dependencies |

## Design fidelity (AGENTS.md §3, §10)

Screens were rendered through the real theme with Robolectric native graphics and compared by eye against
`design/reference/duzup-mobile.png` (Home, Add Bill, Calendar match its hierarchy: pixel "Stay ahead." headline,
raised overview card with real mini-bars, ink-navy pill tabs, rounded neumorphic rows, central raised Add button).
Screenshots are produced by `ScreenshotTest` into `app/build/screenshots` (light, dark, 2× font scale, empty and error
states).

Known differences from the mockup:
- No large glass ring/sphere illustration on Home (the spec says to avoid giant 3D rings); a sparse pixel field is used instead.
- No brand-logo icons (Netflix, Spotify, …): category icons are one consistent Material Rounded family.
- Category chip row scrolls horizontally and shows "More" for the full list and custom categories.
- Neumorphic shadows use `Paint.setShadowLayer` (API 28+); API 26-27 fall back to a crisp hairline surface.
- Robolectric draws shadows slightly differently from a GPU device; final shadow tuning should be checked on hardware.

## Not verified (needs a device, deliberately not done: no emulator was started)

- Real AlarmManager delivery at the exact minute, Doze/App Standby behaviour, OEM battery killers.
- Actual reboot / time-zone-change broadcasts, package-replaced reconciliation.
- Notification action buttons from the shade, grouped summary appearance, channel settings shortcuts.
- The Android 13+ runtime permission dialog and the exact-alarm settings screen.
- SAF create/open document pickers with real providers (Drive, Downloads).
- TalkBack on a device (semantics, labels and focus order were asserted in tests only).
- Haptic feedback strength, system "remove animations" behaviour on a device.
- Release signing (no keystore exists; unsigned release verified only to build).
- Instrumented tests (`DatabaseSchemaTest`) are written and compiled but have **not been run**.

## Known limitations and decisions

- Alarms are armed for the nearest 60 days / 400 reminders and re-armed on launch, boot and by a 12-hourly worker.
- A reminder missed by < 6 h (e.g. phone off) fires shortly after recovery; older ones are dropped.
- Bills use the time zone captured when they were created.
- Occurrences older than 90 days that were never generated for a recurring bill are not back-filled.
- Search matches the stored category name (English for built-ins).
- Dialogs that host a text field (custom offset, category editor) cannot be driven by Robolectric Compose tests: even a stock
  Material `AlertDialog` containing a `BasicTextField` never reports idle there. Their logic is covered by unit tests
  (`ReminderPlanner.parseOffset`, draft validation); the dialogs should be exercised on a device.
- Migrations: schema is version 1, so no migration exists yet; the schema is exported and `DatabaseSchemaTest` is ready for future versions.

## Outstanding

- On-device test pass (see "Not verified").
- Git history: the folder is not a repository yet. Commits will be authored as `umairxdev` once a repo is provided.
- Optional: localisation (all strings are in resources), tablet-specific layouts beyond width capping.
