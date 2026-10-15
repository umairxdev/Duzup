# REMZUP — Android App Development Agent Specification

> **Status:** Implementation contract for a complete, production-ready native Android application.
> **Reference:** The approved Remzup mobile UI mockup supplied in the project/conversation is the primary visual reference. If the image is not present in the repository, ask the developer to place it at `design/reference/remzup-mobile.png` before final visual polish. Never claim to have seen an unavailable image.

## 0. Mission
Build **Remzup**, a premium, offline-first Android app for tracking bills, subscriptions, installments, and recurring payment deadlines. Users add bills, configure reminders, see upcoming/overdue payments, mark individual occurrences paid, and review their payment history. The app must work without an account, server, Firebase, or internet connection.

**Ship a working app, not a prototype.** No dead buttons, fake data in release builds, decorative controls that do nothing, or unfinished navigation. Implement and test real persistence, recurrence, alarms, and payment state.

## 1. Agent operating rules
1. Inspect the existing project first. Preserve valid working code and conventions; do not overwrite without cause.
2. Create a short implementation plan and task checklist, then implement in vertical slices. Keep a buildable state after each slice.
3. Do not stop after scaffolding or UI screenshots. Complete features, tests, and release-readiness checks.
4. If blocked by missing design assets, create faithful native/vector approximations and record the difference; do not use random stock images, unrelated gradients, or invented branding.
5. Prefer straightforward, maintainable Kotlin over overengineering. No unnecessary networking, analytics, ads, tracking SDKs, AI, or cloud services.
6. Use the latest stable compatible versions available in the development environment; ensure Gradle, Kotlin, Compose, KSP, and AGP versions are mutually compatible. Do not invent version numbers.
7. Before changing behavior, identify edge cases and write or update tests.
8. After major milestones, run compilation and tests. Fix failures instead of reporting them as complete.
9. If the environment cannot build Android apps, explicitly state what was not verified and provide reproducible local build commands.
10. Maintain `IMPLEMENTATION_STATUS.md` with completed features, outstanding items, and test results.

## 2. Product boundaries
### Must support
- One-time and recurring bills, subscriptions, rent, utilities, loans/installments, insurance, education, vehicle costs, and custom categories.
- Optional monetary amount, configurable currency, due date, time, repeat schedule, and reminder offsets.
- Upcoming, paid, overdue, and all-bills views.
- Mark as paid, undo paid, skip occurrence, snooze reminder, and edit/delete bills.
- Per-occurrence payment history and monthly summaries.
- Calendar view, notification actions, settings, and local backup/restore.
- Reliable offline behavior, including after app restarts and device reboots.

### Do not implement unless explicitly requested
- Bank integration, actual bill payments, SMS/email scraping, OCR, receipt scanning, accounts, cloud sync, push notification backend, subscriptions/paywalls, AI chat, financial advice, or automatic payment verification.
- A web frontend. This specification is for the Android app only.

## 3. Visual direction: DIGITAL ICE × NEUMORPHISM × PIXEL

**Brand:** Remzup. Personality: calm, technical, tactile, confident, futuristic, slightly nostalgic. Design quality target: polished consumer-product craftsmanship, not an overdecorated concept render.

### Palette (use exact base tokens)
| Token | Hex | Usage |
|---|---|---|
| Ice White | `#E6ECF0` | Main light background |
| Frost Silver | `#B8C9D3` | Surface depth, dividers, secondary details |
| Neon Cyan | `#00D9FF` | Controlled emphasis, selected states, active accents |
| Deep Navy | `#203345` | Cards, secondary text, dark surfaces |
| Ink Navy | `#071724` | Main text, primary buttons, dark mode foundation |

Supporting semantic colors (must be accessible and distinct from decorative cyan): paid `#087F67`, due soon `#A65A00`, overdue `#B4233B`, muted text `#4C6274`. These may be tuned for contrast. Never rely on color alone to communicate status.

### Visual rules
- Default to a **light, frosted silver** interface. A fully supported dark theme is required; it should feel like navy glass rather than flat black.
- Large clean typography and generous negative space. Use a modern sans-serif for body text (e.g., Inter or a bundled, appropriately licensed equivalent). Use a restrained pixel-inspired display typeface only for short hero phrases, counters, or micro-labels; ensure legibility and provide a standard-font fallback.
- Soft, **physically plausible neumorphic elevation**: subtle light top-left highlights, low-opacity blue-gray shadows bottom-right, restrained inner shadows on inputs. No enormous blurry shadows.
- Use rounded rectangles (typically 18–24dp) and small, crisp strokes. Primary CTA can use a dark navy pill with subtle cyan edge lighting.
- Pixel motif: tiny 2×2 or 3×3 square clusters, sparse pixel transitions, small 8-bit inspired details. Use only in brand marks, empty states, or limited header decorations; never fill whole screens with pixels.
- Chrome/glass effects are accents, not content backgrounds. Avoid giant 3D rings, random metallic ornaments, and excessive gradients inside functional screens.
- Prefer native vector/Compose drawing and carefully designed shapes. Do not use emojis as app icons or primary category icons.
- Follow consistent 8dp spacing rhythm; 16–20dp screen margins; 44–48dp minimum touch targets; appropriate status/navigation bar insets.
- Animations: subtle, responsive (roughly 150–300ms), respect reduced-motion settings, no distracting looping animations.
- Text must remain selectable/readable where appropriate. Maintain accessible contrast, scalable typography, TalkBack labels, and meaningful focus order.

### Design fidelity priorities
1. Approved mockup's information hierarchy, geometry, spacing, and brand character.
2. Functional Android conventions, touch targets, accessibility, and responsive layout.
3. Motion and decorative polish.

Do **not** copy iOS-only chrome, Dynamic Island, or fake iPhone hardware into Android screens. Translate the mockup into a real Android app.

### App icon and branding
- Create adaptive launcher icon (foreground/background + monochrome), using a distinctive **bell silhouette with a subtle cyan-lit lower rim and a small pixel-disintegration motif**. Keep recognizable at 48px.
- Produce vector-compatible assets or Android drawable resources. Do not rasterize the entire UI.
- Wordmark: `Remzup` in clean bold sans-serif; avoid over-stylized or unreadable fonts.
- Splash screen uses Android SplashScreen API with the brand mark and a minimal transition.

## 4. Technical architecture
- **Language:** Kotlin.
- **UI:** Jetpack Compose, Material 3 foundation customized with a full Remzup design system. Do not leave default Material styling untouched.
- **Architecture:** MVVM + unidirectional UI state, repositories, use cases only where complexity warrants them.
- **Dependency injection:** Hilt.
- **Persistence:** Room for bills, occurrences, payments, reminder state; DataStore for preferences.
- **Concurrency:** Kotlin Coroutines + Flow; lifecycle-aware state collection.
- **Scheduling:** AlarmManager for user-visible precise reminders where permitted; WorkManager for non-exact reconciliation and maintenance; BroadcastReceiver for alarm delivery, boot, timezone/time changes, and app updates as appropriate.
- **Navigation:** Navigation Compose with type-safe routes where supported.
- **Date/time:** `java.time` with explicit `LocalDate`, `LocalTime`, `ZoneId`, and `Instant` semantics.
- **Minimum Android:** API 26 unless the existing repository explicitly requires another minimum. Set target/compile SDK to compatible currently supported stable versions.
- **No INTERNET permission** unless a future feature explicitly needs it.

Suggested packages:
```
app/
  core/designsystem/
  core/model/
  core/time/
  core/notifications/
  core/database/
  core/preferences/
  core/backup/
  data/local/
  data/repository/
  domain/recurrence/
  domain/reminders/
  domain/payments/
  feature/home/
  feature/bills/
  feature/addedit/
  feature/calendar/
  feature/history/
  feature/stats/
  feature/settings/
  navigation/
```
A single Gradle app module with these packages is acceptable; don't force unnecessary modularization.

## 5. Data model and invariants
Implement normalized Room entities and migrations. Suggested model:

**Bill**
- `id: UUID` (stored as String), `title`, `categoryId`, `notes?`, `amountMinorUnits?` (integer, never floating-point currency), `currencyCode`, `startDueDate: LocalDate`, `dueLocalTime: LocalTime`, `timeZoneId`, `recurrenceRule`, `customInterval?`, `endDate?`, `reminderOffsets`, `isArchived`, `createdAt`, `updatedAt`.

**BillOccurrence**
- `id`, `billId`, `scheduledDueDate`, `scheduledDueTime`, `status` (PENDING/PAID/SKIPPED), `paidAt?`, `actualAmountMinorUnits?`, `paymentNote?`, `createdAt`, and stable occurrence key (unique bill + schedule date/sequence). Derive OVERDUE from due instant and status rather than permanently storing it.

**ReminderDelivery**
- Stable reminder identity, occurrenceId, offset/type, scheduled instant, delivered instant?, snoozeUntil?, canceled flag. Prevent duplicate notifications.

**Category**
- Stable built-in IDs + user-defined name/icon/color. Seed defaults safely once.

**Preferences**
- Currency, notification time, default reminder offsets, theme, first-day-of-week, optional privacy controls, onboarding completed.

**Critical invariants**
- Recurring *occurrences* are separate from the bill *template*. Marking October paid must not mark November paid.
- Changing a recurring template should not silently rewrite historical payments.
- No duplicate occurrences or notifications across reboots, timezone changes, repeated worker runs, or edits.
- `amountMinorUnits` is optional and currency-aware; don't assume every currency has two decimal places.
- Monthly totals must not double-count recurring templates and generated occurrences.
- Deleted/archived items must not continue to notify.
- Support past-due dates, leap years, month ends, DST transitions, and multiple currencies without invalid calculations.

## 6. Recurrence rules
Implement pure, unit-testable recurrence calculation supporting:
- None (one-time)
- Daily, weekly, every 2 weeks
- Monthly, every N months
- Quarterly
- Yearly
- Custom every N days/weeks/months/years

**Monthly anchor policy:** if an item starts on the 31st, use the last valid day of shorter months, but retain the original anchor (e.g., Jan 31 → Feb 28/29 → Mar 31). Likewise handle leap-day annual recurrences with a documented fallback policy.

Generate occurrences lazily for a bounded horizon (e.g., current month ± a few months) and reconcile idempotently. Historical occurrences are preserved. Editing recurrence offers an explicit choice: change future occurrences only; do not mutate paid history.

For recurring bills, a payment is recorded against a specific occurrence. After marking paid, the next scheduled occurrence remains available. Skipping an occurrence should not remove future cycles.

## 7. Reminder engine: non-negotiable behavior
### Default reminder schedule
- 7 days before, 3 days before, 1 day before, and on due date.
- User can enable/disable or customize offsets per bill and globally.
- A bill may have an optional amount; notification text must not display a fake zero amount.
- On due date, show notification at configured local time. An optional overdue reminder can fire later only if unpaid, with reasonable frequency limits.

### Android scheduling requirements
- Request `POST_NOTIFICATIONS` at a contextual moment on Android 13+ and explain the benefit.
- Exact alarms: use the platform-appropriate permission/eligibility approach, check `canScheduleExactAlarms()` where relevant, and gracefully fall back to inexact scheduling with an honest explanation. Never assume exact timing is universally available.
- Use unique PendingIntent identity and correct mutability flags; updating or deleting a bill must cancel obsolete alarms.
- Reconcile alarms on app launch, device reboot, package replacement, relevant time/timezone changes, and permission changes where detectable.
- Do not rely on a periodic WorkManager job for exact due-time delivery.
- Receivers must perform minimal work and be robust to process death. Respect modern Android background execution restrictions.
- Notification channels: Due Today, Upcoming Reminders, Overdue (user-controllable). Group related notifications thoughtfully.
- Notification actions: **Mark Paid**, **Remind Later**, **Open Bill**. Actions must be idempotent and work when the app is not running. Mark Paid must persist the payment, cancel remaining reminders for that occurrence, and update the UI. Snooze must schedule a new reminder without modifying the actual due date.
- Prevent alert spam, duplicates, and notifications after payment.
- Explain OEM battery restrictions without implying guaranteed delivery under every device setting.

## 8. Screens and interactions

### 8.1 Onboarding (max 3 screens)
- Explain: track bills, get alerts, stay offline/private.
- Let user choose currency and notification defaults.
- Ask notification permission contextually, not immediately at cold launch.
- Include Skip/Continue and an obvious path into the app.

### 8.2 Home dashboard
Match the approved mockup's restrained composition:
- Compact brand mark / greeting, notifications entry point.
- Large typographic `Stay ahead.` headline with tiny pixel detail.
- Raised monthly overview card: total upcoming amount, number of pending bills, restrained mini-bars if real data exists.
- Tabs/filters: Upcoming, Paid, Overdue.
- Bill rows: category icon, title, optional amount, due date, relative days remaining, status; tap opens detail.
- Bottom navigation: Home, Calendar, prominent central Add, Stats, Settings.
- Empty states must be useful, with a clear Add Bill action.
- The total should have a clear meaning: **unpaid occurrences due within the selected calendar month**. For multiple currencies, separate totals by currency rather than adding unlike units.

### 8.3 Add/Edit Bill
- Category selector with consistent custom iconography.
- Name (required), amount (optional), currency, due date, reminder time, recurrence, reminder offsets, notes (optional).
- Validate title, date, amount, recurrence, and reminder offsets. Show inline errors.
- Date picker, recurrence picker, custom interval sheet, reminder chip toggles.
- Save and Cancel are functional; save persists and schedules notifications.
- Editing existing bills clearly communicates future-vs-history implications.

### 8.4 Bill details
- Name, category, amount, next due date, days remaining, recurrence summary, reminder schedule.
- Mark as Paid, Undo, Skip, Edit, Archive/Delete (with confirmation).
- Payment history by occurrence; allow recording actual paid amount/date and a short note.
- Show notification status if reminders are blocked or disabled.

### 8.5 Calendar
- Real month grid, Monday/Sunday preference, swipe or buttons to change month.
- Small, readable status dots with accessible labels.
- Tap a day to see all occurrences for that day; tap a bill to open details.
- Correctly handle month boundaries, multiple bills on one day, and selected-day state.

### 8.6 Statistics
- Actual paid amount this month, pending amount, overdue amount, counts, category breakdown.
- Simple native charts; no fake trends, fake numbers, or unnecessary visual noise.
- Currency-aware summaries and clear definitions. Show empty states when data is absent.

### 8.7 History / All Bills
- Search by name and category, filters for status/category/date, sort by due date/amount/name.
- Past payment history should remain accessible independently of future bills.
- Archive and restore where appropriate.

### 8.8 Settings
- Theme: System / Light / Dark.
- Default currency, reminder time, default reminder intervals.
- Notification permission/channel shortcuts and delivery diagnostics.
- First day of week, optional haptics/reduced motion.
- Export/Import backup using Android Storage Access Framework (user-chosen local file, no broad storage permission).
- About, version, privacy statement, reset data with confirmation.

## 9. Local backup and restore
- Export structured versioned JSON (or equivalent documented portable format) including bills, occurrences, payment history, categories, and settings needed for recovery.
- Use SAF `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`; do not require all-files access.
- Validate schema version and integrity before importing; reject malformed files safely.
- Provide explicit replace-or-merge behavior with duplicate-ID handling. Prefer a transactional restore that cannot leave partial data.
- Reschedule future reminders after successful restore.
- Clearly explain that without backup, uninstalling or clearing app data can remove records. Do not promise automatic cloud recovery.

## 10. UX polish and accessibility
- Edge-to-edge layouts with correct system bar contrast and IME handling.
- Small-screen support (e.g., 360dp wide), larger phones, tablets, landscape, font scaling, and display scaling.
- Touch targets >=48dp where practical, meaningful TalkBack semantics, screen reader announcements for statuses.
- Loading, empty, error, success, permission-denied, and restore-failure states.
- Subtle tactile feedback for completed payments, without disruptive confetti.
- Consistent dialogs, bottom sheets, snackbars, typography, shadows, icon sizes, and spacing tokens.
- Light and dark theme screenshot comparisons against the reference.

## 11. Security and privacy
- Store data locally in app-private storage; no third-party tracking or network dependencies.
- Do not log bill names, payment notes, amounts, or backup contents in production logs.
- Protect exported files through user-controlled SAF flows and explain that unencrypted exports should be stored securely.
- No unnecessary permissions. No claims of bank-grade security or encrypted backups unless implemented and tested.

## 12. Test plan
### Unit tests
- Recurrence: Feb 28/29, 30th/31st, leap years, yearly schedules, custom intervals, future edits.
- Reminder offsets and timezones; duplicate suppression; overdue computation.
- Monthly totals, currencies, optional amounts, mark paid/undo/skip transitions.
- Backup import validation, duplicate IDs, invalid data.

### Integration tests
- Room migrations and DAO operations.
- Add/edit/delete/restore bill → expected reminder schedule.
- Notification action → database state → UI update.
- Reboot/time-change reconciliation (use testable scheduler abstractions).

### Compose UI tests
- Onboarding, dashboard filters, add/edit validation, details, payment actions, calendar navigation, settings, empty states.
- Large fonts, dark theme, small screens, TalkBack labels.

### Manual acceptance checklist
1. Add a one-time bill, restart app, verify persistence and scheduled reminder.
2. Add a monthly bill on Jan 31; verify Feb and Mar dates.
3. Mark one recurrence paid; verify next recurrence remains pending.
4. Edit due date/reminder offsets; verify old alarms canceled and new ones scheduled.
5. Snooze and mark paid directly from notification; verify no duplicates.
6. Disable notification permission; app remains usable and explains limitations.
7. Reboot phone; verify future reminders are reconciled.
8. Export, clear local data, import; verify history and future reminders.
9. Verify totals with two different currencies and unknown amounts.
10. Verify no Internet permission, login, Firebase, or fake data in release.

## 13. Build and release readiness
- Provide Gradle wrapper, reproducible README, app icon/adaptive icon, splash, debug/release configurations.
- `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, and available lint/instrumentation checks should pass; report actual outcomes.
- Use a sensible application ID, e.g. `com.remzup.app` **only if available and appropriate**; package naming does not prove brand ownership.
- Keep signing secrets out of Git. Explain how to generate a release keystore and build an AAB, without embedding credentials.
- Ensure all app-visible strings are in resources; default English, ready for localization.
- Do not invent Play Store ratings, user counts, testimonials, or publishing status.

## 14. Delivery order
**Phase 1 — Foundation:** project inspection, design tokens, theme, icons, Room entities, DataStore, navigation, tests.

**Phase 2 — Core workflow:** onboarding, add/edit bill, recurrence, occurrence generation, dashboard, details, payment actions, persistence.

**Phase 3 — Notifications:** permissions, exact/inexact scheduling, receiver, actions, snooze, cancellation, reconciliation, tests.

**Phase 4 — Full app:** calendar, stats, search/history, settings, backup/restore, dark mode.

**Phase 5 — Polish and QA:** compare reference, fix spacing/shadows/typography, responsive states, accessibility, full test suite, documentation, build artifact.

At the end of each phase, update `IMPLEMENTATION_STATUS.md` with verified work and remaining issues.

## 15. Definition of done
The app is done only when:
- All listed V1 screens and workflows are functional with persistent data.
- Recurrence and reminder behavior is correct for tested edge cases.
- Notification permission restrictions are handled gracefully.
- UI is faithful to the Digital Ice visual direction and approved mockup, with purposeful pixel accents and refined neumorphic surfaces.
- Light/dark themes, accessibility, and small screens work.
- Backup/restore and payment history work.
- Tests and builds are run, and failures or environment limitations are documented truthfully.
- No placeholder interactions, invented metrics, fake payment history, or hardcoded demo data ship in release.

## 16. First agent response
Before coding, report: (a) what exists in the repository, (b) any missing design references, (c) proposed implementation sequence, and (d) likely Android notification constraints. Then begin Phase 1 immediately unless an essential blocker requires user input. Do not ask unnecessary questions.
