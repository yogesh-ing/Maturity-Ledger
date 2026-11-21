# Build plan

Each phase ends with something runnable on a real phone and a real PC. No phase is "done" until its
acceptance checks pass on Windows 11 + one Android 8 device and one Android 6 device.

## Phase 0 — Scaffold (done)

- [x] Repository layout, Android Gradle project, agent package, docs.
- [x] Placeholder panel UI served from app assets over an https asset origin.
- [x] Agent: loopback HTTP server, persisted token, `GET /api/health`, Host/Origin checks.
- [x] Release signing fails closed.

Acceptance: `./gradlew assembleDebug` succeeds; `npm run typecheck` succeeds; `npm start` answers
`/api/health` and rejects a wrong `Host` header.

## Phase 1 — Connection and pairing

### Phase 1a — USB pairing (done)

- [x] `adb reverse` bootstrap: the agent finds adb, keeps `tcp:8741` reversed for every attached
      device, and recovers after a replug without a restart.
- [x] Pairing code: 8 characters from a 32-symbol alphabet (no I/L/O/0), 5-minute TTL, 5 attempts,
      single use, constant-time compare, 1 KB body, loopback only.
- [x] Per-phone token (32 random bytes) with capabilities; only `read` is issued today. The agent
      stores the SHA-256 of the token and resolves the device from the credential, never a header.
- [x] Check code shown in the settings page and returned to the phone
      (`sha256(code:localToken)[0..8]`), so both screens can be compared.
- [x] Phone: token kept in `SharedPreferences` behind a four-method JS bridge; the WebView never sees
      a credential. Pairing UI, status chip, and backoff reconnect (2 s → 15 s) in the panel.
- [x] Settings page on `127.0.0.1:8741`: start/cancel pairing, check code, USB state, paired phones,
      revoke. Protected by a 12-hour session token minted into the page, not the local token.
- [x] Wi-Fi listener toggle, self-signed cert generation, SPKI pin (1b). The certificate is made
      once with the `selfsigned` package into `%APPDATA%\DeskDeck\tls\`; the phone pins the SHA-256
      of its SubjectPublicKeyInfo and learns that pin over an already trusted transport. The
      listener is off by default, TLS-only, and never serves pairing.
- [ ] Pairing over the network (1b). Deliberately not done: `POST /api/pair/claim` is refused on the
      LAN listener until it has its own per-address attempt budget instead of the current 5-per-code
      counter. Phones pair on the cable and then go wireless.
- [x] Rotate a phone token in place (`POST /api/device/rotate`): the phone asks for a new token with
      its current one, keeps its id and capabilities, and the old token dies immediately.
- [ ] QR rendering of `deskdeck://pair` (1b; the URI is already emitted, pairing is typed for now).

Acceptance (1a): pair from a fresh install in under 60 s; kill the agent and recover; revoke a phone and
confirm its next request is `401`.

Verified 2026-11-19 on the emulator + Windows agent: `adb reverse` applied automatically, wrong code
`401`, claim `200`, a spent code `409`, revoke `200`, revoked token `401`.

Verified 2026-11-19 against the Windows agent over the LAN: enabling the listener answers `200` and
serves `https://192.168.1.7:8742`; `curl --cacert tls/cert.pem` reports `ok`; the SPKI pin the agent
reports equals `openssl x509 -pubkey | sha256 | base64`; a wrong `Host` is `403`, an unauthenticated
`/api/state` is `401`, `/` over TLS is `404`, `POST /api/pair/claim` over TLS is `403`, and plain
HTTP on the TLS port is refused. A paired phone's `/api/session` over TLS reports
`transport: "wifi"` with the URL and pin, its state and stream answers, rotation returns a new
token while the old one becomes `401`, a local token cannot rotate, and turning the listener off
closes the socket. Not verified: certificate regeneration, a second phone pinning independently,
and a real DHCP address change (the listener re-reads its addresses per request, but that path is
untested).

## Phase 2 — Panel engine and first widgets

- [x] Layout schema + validator (server-side, strict types, unknown widget → skip). The built-in page
      is used when `%APPDATA%\DeskDeck\panel.json` is missing or does not validate.
- [x] Widgets: clock, date, CPU/RAM/disk, network, phone battery, media now-playing. `photo` is
      deferred: serving a local image needs a scoped, size-capped media route, not an arbitrary path.
- [x] SSE state stream with delta compression and per-widget subscription (idle pages cost nothing).
- [x] Idle behaviour: dim after an idle delay (default 90 s, `idle_dim_ms` in the app prefs), restore
      brightness on touch. Full screen-off is left to phase 5 where the display policy is decided.

Verified 2026-11-19 against the Windows agent: `GET /api/panel` returns the built-in page, `/api/state`
carries `system`, a self report makes `phone` appear, the stream sends a full first event then only
the widgets that changed, `?w=` narrows it, an unauthenticated stream is `401`, a malformed self
report is `400`, and a revoked token is `401` on the next state call.

Not verified yet: the acceptance items that need the Android 6 device — 60 fps scrolling with eight
widgets, PC CPU under 5% while streaming, and no wake locks with the screen off.

## Phase 3 — Control deck

- [x] Actions that need no configuration: media (prev/play-pause/next/stop), volume, mute, lock.
      `POST /api/action/{id}` with a fixed vocabulary; the capability comes from the id, so a
      read-only token is refused before anything runs.
- [x] Executor with a fixed allowlist: `helper/winhelper.ps1`, kept warm between presses. Shipped as
      a script rather than `WinHelper.exe` so there is no toolchain in the loop; the allowlist is the
      boundary either way, and the .exe can replace it without changing the protocol.
- [x] Buttons: a deck strip on the phone with optimistic press state, the agent's answer clearing
      it, and the reason shown inline when it fails.
- [x] Control is granted by the PC (`POST /api/settings/devices/{id}/capabilities`), so an already
      paired phone gains or loses it without re-pairing.
- [x] Launch app/URL and key combos from `%APPDATA%\DeskDeck\deck.json`, a PC-side allowlist the
      agent re-reads when it changes. A phone still names only an id; the target and keys live here.
- [x] Sleep with a mandatory second press: the panel arms the tile for three seconds and the agent
      refuses the action without `{ "confirm": true }` (`428`).
- [x] The panel draws whatever deck the agent advertises, so adding a button needs no app rebuild.
- [ ] Macros (a sequence of the above in one press).
- [ ] Trackpad: relative movement, tap/right-tap, two-finger scroll (WebSocket, 60 Hz batched).
- [ ] Core Audio for absolute volume, media metadata for the transport buttons' state.
- [ ] Shutdown with the same confirmation path as sleep.

Acceptance: every action works from the phone; no action can be triggered by a read-only token;
helper rejects an unknown command and logs it.

Verified 2026-11-20 against a Linux agent (protocol only, no desktop actions run): pairing issues
`read` only, `POST /api/action/media.next` with that token is `403`, the PC-side grant returns
`read,input,power`, `/api/session` reports them, a routed action answers `501 unsupported_platform`
off Windows, an unknown id is `404`, a malformed grant is `400`, a grant for an unknown phone is
`404`, 7 of 26 rapid presses are `429`, and a revoked token is `401`.

Not verified: anything on Windows itself — the PowerShell helper, real media/volume keys, and
`LockWorkStation` have not been executed on a PC yet, and the deck strip has not been seen on a phone.

## Phase 4 — Customization

- [x] Desktop layout editor in the browser (`/editor`): drag, resize, snap to cells, up to 8 pages,
      undo/redo, add/remove widgets, page titles, keyboard delete.
- [x] Live preview using the same panel bundle the phone loads: `/preview` hosts the phone's own
      `panel.js` and drives it over `postMessage`, so the preview is not a second renderer.
- [x] Layout push with optimistic versioning: `PUT /api/settings/layout` with `If-Match`, `409` and
      the current layout on a stale save, `POST …/rollback` to any of the last 10 revisions.
- [x] Themes (accent, density, background) and per-device pages, kept in `agent.json`.

Acceptance: edit and push a layout without restarting the app; two paired phones keep independent
pages; a stale editor save is rejected instead of overwriting.

Verified 2026-11-20 against the layout engine only (`npm test`, 16 tests, Linux): the built-in page
and default theme, dropping unknown types and duplicate ids (across pages too), clipping rectangles
to the grid, rejecting a layout with no usable page, theme normalization, revision assignment and
persistence, `conflict` on a stale `If-Match`, `*` as an unconditional push, invalid input leaving
the stored layout alone, history length and cap, rollback-as-new-revision, `not_found` for an
unknown revision, per-device page resolution, and the fallback when `panel.json` does not parse.
`tsc --noEmit` passes.

Not verified: the editor has never been opened in a browser (drag, resize, undo, push, rollback and
the preview iframe are unexercised UI), the new routes have never been called over HTTP, and the APK
has not been rebuilt — the panel bundle now lives in `agent/src/web/panel` and is copied into the
APK's assets by Gradle.

## Phase 5 — Hardening and release

- [ ] Capability tokens end-to-end, rate limits, request caps, structured redacted logging.
- [ ] Ed25519-signed update manifest + Authenticode signing; opt-in auto-update.
- [ ] Tests: unit (auth, layout validation, pinning), integration (pair → action → revoke), fuzz the
      pair-claim and layout parsers.
- [ ] Threat-model review against docs/SECURITY.md, plus a dependency audit in CI.
- [ ] Installer, uninstaller, first-run guide, privacy statement.

Acceptance: `npm test` and Gradle checks green in CI; a signed release installs and updates over the
previous signed build; uninstall leaves no credentials behind.

## Open decisions

1. **Widget rendering**: DOM widgets (simple, accessible) vs canvas (smoother on old GPUs). Start with
   DOM + CSS transforms, move the spectrum/chart widgets to canvas only if profiling demands it.
2. **Wi-Fi discovery**: manual address entry + QR for v1; mDNS later, opt-in and rate-limited.
3. **Locked-PC behaviour**: Windows blocks synthetic input on the secure desktop. Phase 3 accepts
   "actions run when the session is unlocked" and documents it; a service-based helper is out of
   scope until needed.
