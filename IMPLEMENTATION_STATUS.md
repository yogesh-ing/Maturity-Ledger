# Implementation Status — sourced ONLY from `docs/AFEC — Automated Financial Expiry Countdown _ Product Requirements Document.html`

This build was implemented exclusively from the HTML PRD in `docs/`. No other document was used as a source.

Note: the main HTML file is a shell — the real content lives in `docs/AFEC — Automated Financial Expiry Countdown _ Product Requirements Document_files/saved_resource.html`.

## Environment note
This sandbox has no JDK / Android SDK and outbound access to Google/Maven/Gradle hosts is blocked, so the Gradle build could **not be executed here**. Open the project in Android Studio (it will download AGP 8.5.2 / Kotlin 1.9.24 / Compose BOM 2024.06.00) and run:
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest` (parser tests in `app/src/test/.../SmsParserTest.kt`)

The parser's regex/scoring logic was additionally validated outside Android against all 4 sample SMS in PRD §16 Appendix — all parse to the expected dates.

## What is implemented

### Phase 0 — Foundation
- [x] Android project: Kotlin, Jetpack Compose + Material 3, Min SDK 26 / Target 34 (PRD §07)
- [x] MVVM + Repository, ViewModel exposes `StateFlow` (manual DI instead of Hilt to keep the build dependency-light)
- [x] Room DB with the 4 PRD §07 entities: `TrackedInstrument`, `SmsMessage`, `NotificationPreference`, `ParserPattern` (plus `deletedAtEpoch` for FR-08)
- [x] Compose theme from PRD §06 tokens: Indigo primary, Teal accent, urgency Red/Amber/Green, Slate neutrals, dark surfaces, typography scale, radius/elevation
- [ ] SQLCipher DB encryption (§11): **not yet wired** — `AFECDatabase.kt` marks the exact `.openHelperFactory(...)` seam. No `INTERNET` permission is declared, so nothing can leave the device.
- [ ] CI pipeline: not set up

### Phase 1 — Core MVP (FR-01, 02, 03, 06, 09)
- [x] Onboarding: 3-slide carousel + plain-language permission rationale + permission dialog; denied → manual-only mode with dashboard banner (§04)
- [x] 90-day historical SMS scan after `READ_SMS` grant (§04 step 4)
- [x] `SmsReceiver` (SMS_RECEIVED) → enqueues WorkManager `SmsParseWorker`
- [x] Regex date engine: `DD/MM/YYYY`, `DD-MMM-YYYY`, `DDth Month YYYY`, `MM/YYYY` (card), `in N days` (§07/FR-02), India-locale DD/MM (§14)
- [x] Confidence scoring: known sender 30 + keyword 25 + valid date 30 + amount 10 + type 5 → ≥85 auto-add, 50–84 Verification Queue, <50 silent log (FR-09)
- [x] Dashboard with Urgent ≤7d / Approaching 8–30d / Comfortable 31d+ / Completed sections, nearest-expiry chip, FAB (FR-03)
- [x] Verification Queue with raw-vs-parsed, confidence badge, inline edit, Approve/Dismiss, "You're all caught up" empty state
- [x] Manual Entry bottom sheet (add + edit, FR-06/FR-07), duplicate detection, overdue tagging (§12)
- [x] Notification channels Urgent/Standard/Parsing, 9:00 AM milestone alarms, deep-link to Detail, Mark Done + Snooze actions (§10)
- [x] Battery-optimization warning card with "Fix it" deep link

### Phase 2 — Alerts & polish
- [x] Alert Settings: 14/7/3/day-of toggles + custom offsets, global + per-item override (FR-04/FR-10)
- [x] One-click yearly rollover (FR-05)
- [x] `RECEIVE_BOOT_COMPLETED` alarm rescheduling, daily 30-day purge of raw SMS + archived items (FR-08, §11)
- [x] Delete/Archive with 30-day recovery, Reset Local Data with typed `RESET` confirmation (§11)
- [x] Empty states; TalkBack content descriptions and 48dp targets on primary actions

### Phase 3 — Delight (partial)
- [x] Home-screen widget: nearest upcoming expiry (FR-13, classic AppWidget — not Glance)
- [x] Dashboard filter by type + sort by date/amount/urgency (FR-11)
- [x] CSV export via share sheet (FR-12 — CSV only, no PDF)
- [x] Light/Dark/System themes (FR-14; not Material You dynamic color)
- [ ] Play Store build (PRD §14 defers this to Phase 3, sideload first)
- [ ] Device QA matrix + closed beta (§13): needs physical devices / users

## How to try the parser without a phone
Run the unit tests, or add any of the 4 PRD Appendix SMS bodies via **Settings → Rescan** results / Verification Queue on a device with SMS permission. On an emulator, use **Extended Controls → Phone → SMS** to inject a sample message.
