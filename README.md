# Duzup

Duzup is a native Android reminder app for everything you need to remember: **bills, events, tasks, notes,
birthdays and routines**. It is **fully offline**: no account, no server, no analytics, no `INTERNET` permission.
Everything is stored in app-private storage on the device. The copy is friendly and Gen Z-flavoured, but still clear
about money and dates.

## Download

Grab the signed APK from the [latest release](https://github.com/umairxdev/Duzup/releases/latest) and install it
(you may need to allow installs from your browser or file manager). Requires Android 8.0 (API 26) or newer.

- Kotlin, Jetpack Compose (Material 3 customised into the "Digital Ice" design system), Hilt, Room, DataStore,
  Coroutines/Flow, AlarmManager + WorkManager, Navigation Compose with type-safe routes.
- Min SDK 26, compile SDK 37, target SDK 36. Single Gradle module, application id `com.duzup.app`.
- Design reference: `design/reference/remzup-mobile.png` (the original mockup, from before the rename to Duzup). The full product/engineering contract is in `AGENTS.md`.

## Features

- **Six kinds**: Bill (optional currency-aware amount, paid/undo/skip, payment history), Event (start/end, all-day,
  place), Task (priority, late tracking), Note (pin, optional reminder), Birthday (yearly, optional age),
  Routine (several times a day, chosen weekdays, tick off each time).
- **Quick add**: type "call mom tomorrow 6pm" and the offline parser picks the kind, date, time, amount and repeat.
- **Home feed**: Late / Today / Coming up / Done with a kind filter; calendar with per-kind dots; Notes tab; Stats and
  history reached from Settings and the Home money card.
- **Reminders**: per-kind defaults (minutes, hours or days before), quiet hours, snooze choices (10 min, 1 h, 3 h,
  tomorrow morning), 8 notification channels (heads-up banner, sound and vibration on by default, tunable per channel
  from Settings), per-kind actions (Paid it / Done / Got it, Snooze, Open) and wording pools that never change the line
  for the same reminder.
- Repeats: daily, weekly, every 2 weeks, monthly, quarterly, yearly, custom N days/weeks/months/years (month-end
  anchors kept, leap days handled).
- Light/dark/system theme, local JSON backup and restore (v2; v1 "Remzup" files still import) through the system file
  picker.

## Build

Requirements: JDK 17+ (Android Studio's bundled JBR works), Android SDK with platform 37 and build-tools. A
`local.properties` with `sdk.dir=...` is created by Android Studio; otherwise create it (it is git-ignored).

```bash
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # unit, Robolectric integration and Compose UI tests (no emulator needed)
./gradlew lintDebug                # app/build/reports/lint-results-debug.html
./gradlew assembleDebugAndroidTest # compiles the instrumented tests
./gradlew connectedDebugAndroidTest   # runs them on a connected device/emulator
```

On Windows use `gradlew.bat`. If the shell's default JDK is too new or too old, point `JAVA_HOME` at a JDK 17-25.

### Tests

| Layer | Where | What it covers |
|---|---|---|
| Pure unit | `app/src/test/.../domain`, `.../feature` | quick-add parser, notification wording, quiet hours, recurrence (Feb 28/29, 30th/31st, leap days, custom intervals), occurrence planning, reminder math incl. time zones and DST, money, payment transitions, monthly totals, backup validation, draft validation, history filters |
| Robolectric integration | `app/src/test/.../integration` | real Room (in memory) + real engine/repository with fake clock/alarms/notifier: add/edit/delete/archive → exact alarm schedule, idempotent reconcile, reboot recovery, snooze, mark paid, at-most-once delivery, backup round trips |
| Compose UI (Robolectric) | `app/src/test/.../ui` | onboarding, dashboard filters, add/edit validation, details and payment sheet, calendar, settings, empty states, dark theme, 360dp + large fonts, accessibility labels. `ScreenshotTest` writes PNGs to `app/build/screenshots` |
| Instrumented | `app/src/androidTest` | Room schema validation against `app/schemas`, DB constraints (run on a device) |

> **Windows note:** Robolectric cannot locate its jars when the user profile path contains a space. `app/build.gradle.kts`
> redirects its cache to `%SystemDrive%\robolectric-m2` in that case; nothing to configure.

## Release build

Release APKs are published on the [Releases page](https://github.com/umairxdev/Duzup/releases). To build your own:

```bash
keytool -genkeypair -v -keystore duzup-release.jks -alias duzup -keyalg RSA -keysize 4096 -validity 10000
```

Keep the keystore **outside Git** (`*.jks`/`*.keystore` are ignored). Create `keystore.properties` in the repo root
(also ignored):

```properties
storeFile=duzup-release.jks
storePassword=...
keyAlias=duzup
keyPassword=...
```

Then `./gradlew bundleRelease` (AAB for Play) or `./gradlew assembleRelease` (APK). Without `keystore.properties` the
release build is produced unsigned. R8 minification and resource shrinking are on for release.

## Architecture

```
app/src/main/java/com/duzup/app/
  core/designsystem   Digital Ice tokens, typography (Inter + Pixelify Sans), neumorphic modifiers, components
  core/model          domain types, money
  core/database       Room entities, DAOs, mappers (schema v1 exported to app/schemas)
  core/preferences    DataStore preferences
  core/notifications  AlarmManager scheduler, notification channels/actions, receivers, WorkManager worker
  core/backup         versioned JSON format, validation, transactional restore
  domain/             pure logic: recurrence, occurrence planner, reminder planner/engine, payment rules, summaries
  data/repository     BillRepository (single entry point for all state changes)
  feature/*           screens + ViewModels (home, addedit, bills, calendar, stats, history, settings, reminders, onboarding)
  navigation          type-safe routes, bottom bar, NavHost
```

Key design rules (see `AGENTS.md` §5-7):

- A recurring bill is a **template**; each due date is its own **occurrence** (unique per bill + date). Paying October
  never touches November, and editing the template never rewrites paid/skipped history.
- Monthly recurrence is anchored to the original day: Jan 31 → Feb 28/29 → Mar 31. A Feb 29 yearly bill falls back to
  Feb 28 in non-leap years.
- Occurrences are generated lazily for a bounded window (90 days of overdue look-back, 6 months ahead) and reconciled
  idempotently on launch, boot, update, time/zone change and by a 12-hourly WorkManager job.
- Reminder identity is stable (`occurrenceId|offset`); delivery is claimed atomically in SQL so a reminder can never
  be shown twice. Only the nearest 60 days / 400 alarms are armed, well under the AlarmManager limit.
- Exact alarms are used when permitted (`canScheduleExactAlarms()`), with an honest inexact fallback.
- Overdue is derived from the due instant and status; it is never stored.
- Money is integer minor units, currency-aware (JPY has 0 decimals, KWD 3). Totals are never summed across currencies.

## Backup file format

`{ "app": "duzup", "schemaVersion": 2, "exportedAt": <ms>, "settings": {...}, "categories": [...], "bills": [...], "occurrences": [...] }`

Plain, **unencrypted** JSON. Import validates version, ids, dates, enums, currencies, references and uniqueness
*before* touching the database, then restores in one transaction (replace or merge). Reminders are rebuilt afterwards.

## Privacy and permissions

`POST_NOTIFICATIONS` (asked contextually, never at cold launch), `SCHEDULE_EXACT_ALARM` (optional; the app falls back
gracefully), `RECEIVE_BOOT_COMPLETED`. WorkManager adds `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE`
to the merged manifest; there is no `INTERNET` permission and no network code. Android auto-backup is disabled.

## License

Duzup is released under the [MIT License](LICENSE).

### Third-party

Inter and Pixelify Sans are bundled under the SIL Open Font License 1.1 (`app/src/main/assets/licenses`).
