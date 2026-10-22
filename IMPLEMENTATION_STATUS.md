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
- [x] SQLCipher DB encryption (§11): wired via `SupportOpenHelperFactory` with a random passphrase wrapped by an Android Keystore AES-256-GCM key (`DatabaseKeyProvider.kt`); encrypted DB is `afec-secure-database`. No network permission is declared, so nothing can leave the device. *(Code-complete; compile-verification pending first CI build.)*
- [~] CI pipeline: workflow ready at `ci/build-apk.yml` (+ `ci/README.md` steps). It must be copied to `.github/workflows/build-apk.yml` via the GitHub web UI once — Arena's GitHub token is denied `workflows` permission (git push + API both rejected).

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
- [x] Dashboard filter by type/institution + sort by date/amount (FR-11; the redundant "urgency" sort was removed — it produced the same order as date)
- [x] CSV **and PDF** export via share sheet (FR-12) — `CsvExporter` / `PdfExporter`, both covered by tests/preview code
- [x] Light/Dark/System themes (FR-14) + opt-in Material You dynamic color on API 31+ (brand palette stays the default)
- [ ] Play Store build (PRD §14 defers this to Phase 3, sideload first)
- [ ] Device QA matrix + closed beta (§13): needs physical devices / users

## How to try the parser without a phone
Run the unit tests, or add any of the 4 PRD Appendix SMS bodies via **Settings → Rescan** results / Verification Queue on a device with SMS permission. On an emulator, use **Extended Controls → Phone → SMS** to inject a sample message.

## Second-round completion (this update)
- [x] Grouped summary notification for same-day milestone alerts (§10) + auto-add confirmation notification (§04 happy path; background-safe instead of a Toast)
- [x] Correction → heuristic feedback (§04): approving an edited queue item adjusts that sender's `ParserPattern` weight (-5 edited / +2 clean, clamped), and ingestion actually scores with DB weights
- [x] Dashboard filter by institution in addition to type (FR-11 complete: institution + type + sort date/amount)
- [x] PDF export (FR-12 complete: CSV + PDF)
- [x] Dynamic color (FR-14, opt-in), SQLCipher encryption (§11)

## Still partial / not done (honest list)
- Home-screen widget is a classic `AppWidgetProvider`, not Jetpack Glance as named in Phase 3 (FR-13 behaviour is met)
- Optional biometric/PIN app lock (§11, marked optional in PRD) — not implemented
- Unit-test coverage ≥80% (§09/§13) — parser/date/CSV tests exist; DAO/alarm instrumentation tests and the 50-template corpus are not complete
- Device QA matrix (Pixel/Samsung/Xiaomi/OnePlus, API 26–34), closed beta, Play Store build (§13/§14) — require devices/users and a `READ_SMS`-policy decision
- **The app has still never been compiled**: no JDK/Android SDK exists in this sandbox and Google/Maven hosts are blocked. First build = the CI workflow above (or Android Studio). Compile errors, if any, get fixed on that first run — only then can an APK be exported and committed under `apk/`.

## UI/UX validation pass (2026-10-22)
Full findings and rationale: **[`docs/UI_UX_AUDIT.md`](docs/UI_UX_AUDIT.md)**.

Highlights (all static verification only — see the build caveat below):
- Fixed a **theming escape**: the Add/Edit bottom sheet (and its date picker) was composed outside `AfecTheme` and rendered in default M3 purple, ignoring dark mode.
- Fixed **dark-mode legibility**: onboarding/detail had no `Surface`/night window theme, and urgency chips always used the light pastel containers (the `Dark*Container` tokens existed but were unused). Dark-aware tier palettes now live in `ui/TierUi.kt`.
- Fixed **WCAG contrast** on the gradient hero (2.2:1 → 4.75:1) and on the amber "Approaching" tier (2.9:1 → 4.6:1 on container surfaces).
- Fixed **navigation/feedback**: system Back on Detail exited the app; snackbars raised from Detail were swallowed; the dashboard nested a second `Scaffold`.
- Fixed **content**: raw enum names (`FD`, `Sort: DATE`, `SYSTEM`), emoji section headings, a dead "sort by urgency" option, a wrong empty state under filters, raw `₹500000.0` amounts in the queue, and a queue "Edit" sheet whose Save button actually approved the item.
- **Design-system alignment**: shape scale corrected to PRD §06 (8/12/16/24), full M3 surface-role set defined (no more baseline-purple fallbacks), card anatomy (4dp accent, dot status chip), shared countdown-progress metaphor, 48dp primary CTAs.

## UI preview (browser, not the APK)
`preview/` contains a standalone, interactive HTML/CSS/JS rendering of the Compose
screens — same PRD §06 tokens, same layout rules — for reviewing UI/UX without an
Android toolchain. Serve it and open it:

```
cd preview && python3 -m http.server 8080
# then open http://localhost:8080
```

It covers onboarding (4 steps), dashboard (filters/sort/empty states), verification
queue, detail, add/edit sheet, settings and the reset dialog, with light/dark,
"wallpaper colours" (Material You) and a 360 dp viewport. It is a design preview,
not a substitute for the first real build.
