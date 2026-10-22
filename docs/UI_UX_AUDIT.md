# UI/UX Validation Pass — Findings & Fixes

Date: 2026-10-22 · Branch: `arena/ca480083-maturity-ledger`
Scope: every Compose screen (`ui/`), the design tokens (`ui/theme/`), and the XML window shell (`res/values*/themes.xml`).

> **Verification caveat.** This sandbox has no JDK, no Android SDK, and no access to
> Google Maven, so the app still cannot be compiled here (see `IMPLEMENTATION_STATUS.md`).
> Everything below was validated by static reading against the PRD §05/§06 spec,
> balanced-syntax checks, import-resolution checks, and measured WCAG contrast
> ratios. The first CI/Android-Studio build remains the real compile gate.

---

## 1. Critical defects found and fixed

| # | Area | Anomaly | Fix |
|---|------|---------|-----|
| C1 | Root composition | `ManualEntrySheet` was composed **outside** `AfecTheme`. The Add/Edit sheet, its date picker and the "Reset Local Data" path fell back to the **default Material 3 purple palette** and ignored dark mode. | Sheet (+ dialog) now composed inside `AfecTheme` under a `Surface`. |
| C2 | Dark mode | Onboarding and Detail were not wrapped in a `Surface`/`Scaffold`; they drew `onBackground` text straight onto the **window** background, which stayed `#F8FAFC` in dark mode → light text on a light surface. No `values-night` theme existed either, so the launch window flashed white. | Root `Surface(color = background)` + new `res/values-night/themes.xml` (dark window/status bar). |
| C3 | Urgency chips | `tierContainer()` always returned the *light* pastel containers. `DarkUrgentRedContainer` / `DarkApproachingAmberContainer` / `DarkComfortableGreenContainer` were defined but never used → bright "sticker" chips on dark cards. Queue's confidence badge and empty state had the same problem. | New `ui/TierUi.kt` single source of truth with dark-aware tier + confidence palettes derived from the rendered surface. |
| C4 | Hero contrast | White 11 sp labels on the teal end of the hero gradient measured **2.2:1** (WCAG AA needs 4.5:1); the gradient also ran under the summary numerals. | Gradient end changed `Accent500 #14B8A6 → Accent700 #0F766E`, label alpha `0.9` → **4.75:1**. Numeral tints lightened (`#FDE68A`, `#BBF7D0`) → all ≥ 3.4:1. |
| C5 | Navigation | The system Back button on Detail **exited the app** instead of returning to the dashboard. | `BackHandler` on the root; Detail's back arrow is now in a fixed header that does not scroll away. |
| C6 | Feedback | Snackbars raised from Detail/onboarding were swallowed (the only `SnackbarHost` lived in the dashboard's Scaffold, which wasn't composed on those screens). | One app-level `Scaffold` owns the snackbar host for **all** screens. |
| C7 | Layout | `DashboardScreen` nested its own `Scaffold` inside the app Scaffold → doubled content padding and an FAB positioned against the inner container. | Dashboard is now pure content; the app Scaffold owns the FAB (Dashboard tab only). |
| C8 | Permissions | `hasSmsPermission` was sampled once at composition; granting SMS access in system Settings left the app stuck in "manual-entry mode" until restart. | Permission re-checked on every `ON_RESUME`, with an automatic rescan on the off→on transition. |

## 2. Design-system deviations

| # | Anomaly | Fix |
|---|---------|-----|
| D1 | Shape scale claimed "sm 8 / md 12 / lg 16 / xl 24" in its own comment but defined **8 / 10 / 14 / 18 / 28**. | `AfecShapes` now matches PRD §06: 8 / 12 / 16 / 24 (+ `ButtonMinHeight = 48.dp`). |
| D2 | Only part of the M3 colour scheme was overridden, so unset roles (`surfaceContainerLow`, `surfaceContainerHighest`, `inverseSurface`, …) fell back to the baseline **purple** palette used by bottom sheets, dialogs and the navigation bar. | Both light and dark schemes now specify every role, including the full surface ramp, inverse colours and scrim. |
| D3 | Dark `onSurfaceVariant` was identical to `onSurface` → no text hierarchy in dark mode. | Dark secondary text is now `Slate300` (9.8:1) with `Slate600` in light (6.9:1 on container surfaces). |
| D4 | Card anatomy drifted from spec: 6 dp accent bar with hard-coded 18 dp corner radii; status chip used `Info`/`NotificationsActive` icons. | 4 dp accent bar, corners clipped by the card shape, chip is a **dot + label** as specified (`● Urgent · 3d`), full radius. |
| D5 | Two different "progress" metaphors: the card used term-elapsed (created→maturity) while Detail used a 90-day horizon. | Shared `countdownProgress()` used by both; both now fill as maturity approaches. |
| D6 | Material You forcibly replaced the brand on Android 12+, so the indigo/teal identity (and urgency palette) was never visible on most devices. | Dynamic colour is now **opt-in** (`Appearance → Use wallpaper colours`), brand tokens by default. |

## 3. Content & interaction anomalies

| # | Anomaly | Fix |
|---|---------|-----|
| U1 | Raw enum names surfaced as UI copy: `FD`, `RD`, `CARD`, `SUBSCRIPTION`, `OTHER`, `Sort: DATE`, `SYSTEM / LIGHT / DARK`. | `displayLabel()` mappings: "Fixed Deposit", "Recurring Deposit", "Card", "Subscription", "Other"; "By date" / "By amount"; "System / Light / Dark". |
| U2 | **Dead sort option**: "Sort: URGENCY" produced exactly the same order as "Sort: DATE". | Removed; the dashboard is already grouped by urgency tier. |
| U3 | Emoji headings (`🔴 Urgent (≤7 days)`, `🟠`, `🟢`, `✓ Completed`) rendered inconsistently across OEMs and were read aloud as "red circle" by TalkBack. | Replaced with coloured dots + plain headings; the urgent section is now labelled "Urgent & overdue" because it includes overdue items. |
| U4 | Filtering to an empty result showed **"Nothing tracked yet — add your first instrument"**, telling users their portfolio was empty when it wasn't. | Distinct "No instruments match these filters" state with a **Clear filters** action. |
| U5 | Archived cards repeated "Recoverable for 30 days from archive date" with no date; "Delete forever" was not styled as destructive. | Shows `Archived dd MMM · restorable until dd MMM` from `deletedAtEpoch`; destructive action uses `colorScheme.error`. |
| U6 | Queue: parsed amount printed as `₹500000.0` (raw `Double`) unlike cards (`₹5,00,000`); sender text could collide with the confidence chip; three action buttons overflowed narrow screens; failed parses were a dead end (Approve disabled, only Dismiss). | `formatInr()` everywhere; sender `weight(1f)` + ellipsis; stacked actions with 48 dp targets; **Add manually** fallback for unparsed SMS. |
| U7 | Opening "Edit" from the queue showed a sheet titled *"Edit instrument"* with a **Save** button that actually approved the item. | Sheet takes `heading` / `saveLabel`; queue uses "Review parsed SMS" / "Approve & Add". |
| U8 | Settings screen titled "Alert Settings" although it also contains Appearance and Data. | Renamed to "Settings" with section headers. |
| U9 | Custom alert offsets sat in a non-wrapping `Row` (overflowed after ~4 chips), chips had no remove affordance, and invalid input was silently discarded. | Horizontal scrolling chip row with a remove icon and content description; inline validation ("Enter a value between 1 and 365") with the Add button disabled until valid. |
| U10 | Onboarding was a fixed, non-scrolling column → clipped on small screens / large font scales; inactive dots used `outlineVariant`, invisible in dark; no way back to a previous slide. | Scrollable content area, dark-safe dot colour, **Back** control, 48 dp CTAs. |
| U11 | Detail title had no `weight`/`maxLines` next to the back arrow (long titles clipped). | Two-line ellipsised title in a fixed header. |
| U12 | FAB/hero copy: "Automated Financial Expiry Countdown" is developer jargon repeated on two screens; hero stat labels ("Approaching", "Comfortable") overflowed on 320 dp devices. | Subtitle is now "Every maturity, in one place"; stats use the horizon labels `Total · ≤7d · 8–30d · 31d+` with spoken descriptions. |

## 4. Accessibility

- **Contrast** (measured, light theme): hero labels 4.75:1 (was 2.2:1); `Approaching` numeral `#B45309` 5.0:1 on white / 4.6:1 on container (was 2.9:1); secondary text 6.9:1; dark tier colours 5.3–10.7:1.
- **TalkBack**: the dashboard summary now exposes one description per stat ("3 urgent, due within 7 days"); navigation items no longer double-announce icon + label; archived actions and offset chips have meaningful labels; card content descriptions include institution, amount, maturity and tier.
- **Touch targets**: primary CTAs are ≥ 48 dp high (PRD §06), including the queue's stacked actions.

## 5. Files touched

| File | Change |
|------|--------|
| `ui/theme/Color.kt` | Dark urgency palette, dark error role, full neutral ramp, darker hero gradient, hero stat tints. |
| `ui/theme/Theme.kt` | Complete light/dark colour roles; opt-in dynamic colour; `ThemeMode.displayLabel()`. |
| `ui/theme/TypeAndShapes.kt` | Shape scale matched to PRD; `ButtonMinHeight`. |
| `ui/TierUi.kt` *(new)* | Tier colour/container/label, section titles, countdown progress, type/status labels, confidence styling, dark-surface detection. |
| `ui/AfecApp.kt` | Single Scaffold + Surface, themed sheet, global snackbar, BackHandler, FAB placement, permission re-check, 99+ badge. |
| `ui/DashboardScreen.kt` | No nested Scaffold; display labels; removed dead sort; filter-empty state; dot section headers; archive recovery dates. |
| `ui/CountdownCard.kt` | Spec-accurate anatomy, dot status chip, shared progress, completion icon, ellipsis, a11y description. |
| `ui/QueueScreen.kt` | INR formatting, stacked 48 dp actions, manual fallback, received date, dark-aware confidence badge. |
| `ui/ManualEntrySheet.kt` | `heading`/`saveLabel` overrides, type labels, keyboard types, IME padding, inheritance hint, 48 dp CTAs. |
| `ui/SettingsScreen.kt` | "Settings" title, scrolling offset chips + validation, Material You toggle, error-coloured battery/reset actions, full-width data buttons. |
| `ui/OnboardingScreen.kt` | Scrollable, Back control, dark-safe dots, 48 dp CTAs. |
| `ui/DetailScreen.kt` | Fixed header, larger action targets, archive wording, status-safe completion, dark-aware ring. |
| `ui/AfecViewModel.kt` | `dynamicColor` preference. |
| `res/values/themes.xml`, `res/values-night/themes.xml` | Light/dark window shell. |

## 6. Recommended next steps (not changed here)

1. **Run the first build** (`ci/build-apk.yml` or Android Studio) — it is still the only real compile gate.
2. Extract the remaining hard-coded copy into `strings.xml` (the file currently has unused, contradictory onboarding strings).
3. Swipe-to-dismiss on the queue (PRD mentions "swipe or button").
4. Sticky dashboard header per PRD §05 (the hero currently scrolls).
5. Home-screen widget: still a static indigo surface with no dark variant.
6. Edge-to-edge/insets work if `targetSdk` moves to 35.
7. Device QA with TalkBack and 200 % font scale on API 26–34.
