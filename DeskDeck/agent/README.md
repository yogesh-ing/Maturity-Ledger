# DeskDeck agent

Node 24 + TypeScript, no framework. Runs on the Windows PC and serves the panel API to the phone.

```
npm install
npm start          # http://127.0.0.1:8741
npm run typecheck
npm test           # node --test: the layout engine, no agent and no Windows needed
```

State lives in `%APPDATA%\DeskDeck\agent.json` (override with `DESKDECK_HOME`). It contains the local
API token; the file is excluded from git.

## What exists in 0.6.0 (phase 4)

- Loopback-only listener with `Host` and `Origin` validation; query strings never reach the log.
- `GET /api/health` (no credential, no machine data).
- `adb reverse` bootstrap: finds adb, reverses `tcp:8741` for every attached device, retries every
  10 s so a replugged cable recovers on its own.
- Pairing: 8-character code (5 min, 5 attempts, single use, constant-time compare, 1 KB body,
  loopback only) → per-phone token with capabilities, check code, device registry.
- `GET /api/session`, `GET /api/panel`, `GET /api/state` behind a device token (capability `read`).
- Layout store: a validated 12×8 grid (`layouts.ts`), built-in page when `panel.json` is absent.
- Layout editor at `/editor` (`web/editor.*`): drag/resize on a snapping grid, up to 8 pages with
  tabs, undo/redo, theme (accent, density, background) and a live preview that runs the phone's own
  `web/panel` bundle in an iframe (`/preview`).
- `GET /api/settings/layout`, `PUT` with `If-Match`, `POST …/rollback`, `POST …/reload`: every push
  becomes a new revision, a stale save is `409`, and the last 10 revisions can be restored.
- `POST /api/settings/devices/{id}/page`: which page one phone opens on, kept on the PC.
- `layouts.test.ts` (`npm test`): validation, revision assignment, conflict, history cap, rollback,
  theme normalization and per-device page resolution.
- State collectors (`state.ts`): CPU from `os.cpus()` deltas, memory, disk via `statfs`, network
  byte rates from `netstat -e`, and now-playing from a Windows Runtime probe (`ps/nowplaying.ps1`).
- `GET /api/stream`: SSE with per-widget subscription (`?w=id,id`), deltas only, 15 s heartbeat,
  at most 8 open streams, and no sampling at all while nobody is listening.
- `POST /api/device/self`: a paired phone reports its own battery for the `phone` widget.
- Settings page on `/`: start/cancel pairing, USB state, Wi-Fi toggle with address and key pin,
  paired phones, revoke. It uses a 12-hour session token embedded in the page, not the local token.
- Wi-Fi listener (`wifi.ts` + `tls.ts`): the same handler over TLS on the LAN, off by default. A
  self-signed identity is generated once into `tls/` and each phone pins the SHA-256 of its SPKI; the
  pin is handed out on `/api/session` over an already trusted transport. Pairing, `/` and the
  settings routes stay loopback-only.
- `POST /api/device/rotate`: a phone replaces its own token in place.
- **Control deck** (`actions.ts`, `win.ts`, `helper/winhelper.ps1`): `POST /api/action/{id}` runs one
  id from a fixed list — `media.prev|playpause|next|stop`, `volume.up|down`, `mute` (capability
  `input`) and `lock` (`power`). Each id carries its own capability, so a read-only token is refused
  before anything runs; more than 20 actions a second for one credential is `429`.
- `POST /api/settings/devices/{id}/capabilities`: the PC grants or withdraws `input`/`power` for one
  phone (pairing always issues `read`). The phone picks the change up on its next `/api/session`.
- The action executor is a long-lived `powershell.exe -File helper/winhelper.ps1` (started on demand,
  stopped after five idle minutes). It loads its P/Invoke block once, reads one JSON command per
  line, executes media/volume/mute and key combos through `keybd_event`, locks through
  `LockWorkStation`, sleeps through `SetSuspendState`, launches through `Start-Process`, and rejects
  anything else with a log line on stderr.
- **Configured deck** (`deck.ts`): `%APPDATA%\DeskDeck\deck.json` adds `launch`, `keys` and `sleep`
  buttons. The file is the allowlist (validated target/keys, at most 24 entries), is re-read when its
  mtime changes, and its errors are listed on the settings page. `GET /api/panel` carries the whole
  deck, so the phone needs no rebuild when a button is added.
- `confirm` actions (sleep) require `{ "confirm": true }` and answer `428` without it.

Files: `main.ts` (routes), `pairing.ts` (codes), `devices.ts` (token registry), `layouts.ts`
(schema and validator), `state.ts` (collectors and the stream hub), `ps/nowplaying.ps1` (media probe),
`adb.ts` (reverse), `tls.ts` (identity and pin), `wifi.ts` (LAN listener), `actions.ts` (the action
vocabulary), `deck.ts` (deck.json allowlist), `win.ts` (helper process and command queue),
`helper/winhelper.ps1` (the executor),
`security.ts` (Host/Origin/token helpers), `http.ts` (bounded JSON bodies), `web/` (settings page).

Dependency: `selfsigned` (pure JS, generates the certificate on first enable). No native build step.

Ports: `8741` on loopback (USB, plain HTTP) and, when enabled, `8742` on the LAN (TLS). Set the LAN
port from the settings page or `DESKDECK_HOME/agent.json`; changing it does not re-pair phones.

Sampling costs: the media probe spawns PowerShell roughly every 5 s and takes about a second; the
network counters run `netstat -e` every 5 s. Both are skipped entirely when no stream is open.

## Next

Launch/macro actions with a per-PC allowlist, the trackpad, absolute volume through Core Audio, and
the layout editor — see `../docs/PLAN.md`; the wire format is in `../docs/PROTOCOL.md`. Pairing over
the network and the QR rendering of `deskdeck://pair` are still open in phase 1b.
