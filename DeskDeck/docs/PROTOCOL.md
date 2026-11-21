# DeskDeck protocol v1 (draft)

JSON over HTTP, UTF-8, all timestamps in epoch milliseconds. Base path `/api`. The agent version is
reported in `X-DeskDeck-Agent` on every response; the phone sends `X-DeskDeck-App` and
`X-DeskDeck-Device` (an opaque id from the pairing record, never free-form).

## Authentication

```
Authorization: Bearer <token>
```

- Token: 32 random bytes, base64url, issued per phone at pairing.
- Tokens carry capabilities: `read`, `input`, `power`. The agent checks the capability required by
  the route, not just the token.
- Local loopback calls from the settings page use a separate short-lived session token issued by the
  agent at page load, so the long-lived phone token never enters the browser.

Errors never echo credentials:

```json
{ "error": { "code": "unauthorized", "message": "Missing or invalid token" } }
```

## Endpoints

| Method | Path | Capability | Purpose |
|---|---|---|---|
| GET | `/api/health` | — (no credential) | Version, port, PC name. No machine data. |
| POST | `/api/pair/claim` | — (pairing code) | Trade a one-time code for a phone token |
| GET | `/api/session` | read | Which PC, which device, which capabilities |
| GET | `/api/panel` | read | Current layout and the deck buttons this agent offers |
| GET | `/api/state` | read | Widget state snapshot (`?w=id,id` to narrow it) |
| GET | `/api/stream` | read | SSE stream of state deltas (`event: state`) |
| POST | `/api/device/self` | read (paired phone) | The phone reports its own battery |
| POST | `/api/device/rotate` | read (paired phone) | Replace the phone's own token in place |
| POST | `/api/action/{id}` | `input` or `power`, per id | Run one id from the fixed action vocabulary |
| GET | `/api/panel/preview` | — (loopback only) | Live preview for the desktop editor |

`POST` bodies are capped at 64 KB and must be `application/json`. Unknown fields are ignored;
unknown action ids return `404`.

## Settings surface (loopback only)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/` | loopback + session token in page | Settings UI |
| GET | `/api/settings/state` | settings session | Pairing view, USB state, paired phones |
| POST | `/api/settings/pairing` | settings session | Start a pairing code |
| DELETE | `/api/settings/pairing` | settings session | Cancel the live code |
| DELETE | `/api/settings/devices/{id}` | settings session | Revoke a phone |
| POST | `/api/settings/wifi` | settings session | Turn the LAN listener on or off (`{ "enabled": true }`) |
| POST | `/api/settings/devices/{id}/capabilities` | settings session | Grant or withdraw control (`{ "input": true, "power": true }`) |
| GET | `/api/settings/layout` | settings session | Layout, revision, history and deck for the editor |
| PUT | `/api/settings/layout` | settings session + `If-Match` | Push a layout, producing a new revision |
| POST | `/api/settings/layout/rollback` | settings session | Restore an earlier revision as a new one |
| POST | `/api/settings/layout/reload` | settings session | Re-read `panel.json` after a hand edit |
| POST | `/api/settings/devices/{id}/page` | settings session | Choose the page that phone opens on |

Loading `/` mints a 12-hour random session token that is embedded in the page; the long-lived local
token is accepted there too (for tooling) but never given to a browser.

## Pairing (phase 1a: over USB, loopback only)

1. The agent finds `adb` and keeps `adb reverse tcp:8741 tcp:8741` for every attached device, so the
   phone's `http://127.0.0.1:8741` reaches the PC with no manual step.
2. **Start pairing** mints 8 characters from `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (no I/L/O/0) with a
   5-minute TTL, 5 attempts and single use. Type them on the phone.
3. `POST /api/pair/claim` `{ "code": "ABGRGZWD", "device": { "model": "Pixel 8", "app": "0.1.0" } }`
   answers `{ "token": "…", "pc": "…", "check": "DD70 EDA9", "device": { "id": "dev_…",
   "name": "…", "capabilities": ["read"] } }`. The code is consumed whether or not it matched.
4. The check code is `sha256(code + ":" + localToken)[0..8]`, shown in the settings page and compared on
   the phone. A mismatch means a different agent answered.
5. The phone stores the token in `SharedPreferences`; the WebView never receives it. The device id is
   assigned by the agent; the phone cannot choose or change it.
6. Revoking a phone deletes its record, so its next request is `401` and the app clears its token.

Rejections: `401 bad-code`, `409 no-session` (also a spent code), `410 expired`, `429 too-many`.

## Wi-Fi transport (phase 1b)

The phone can talk to the agent without a cable. The agent serves the same handler on a second,
TLS-only listener on the LAN. It is **off by default**: USB opens no port, and this listener is the
only one reachable from the network. Switch it on from the settings page
(`POST /api/settings/wifi { "enabled": true }`); the choice is persisted in `agent.json`.

1. On first use the agent generates a self-signed RSA-2048 certificate in
   `%APPDATA%\DeskDeck\tls\{cert.pem,key.pem}` with SANs for `localhost`, the PC name and every IPv4
   address. Nothing trusts it through a public CA, so its contents are not what protects the link.
2. The identity that matters is the **SPKI pin**: base64 SHA-256 of the certificate's
   `SubjectPublicKeyInfo`. `GET /api/session` returns
   `wifi: { "urls": ["https://192.168.1.7:8742"], "pin": "dQm5stjp…" }` to a paired phone. The phone
   stores it and requires that exact key (`publicKey.encoded`, hashed, compared in constant time) on
   every TLS connection it makes. A hostname mismatch is expected — the phone dials an address, and
   the pin is the stronger statement.
3. The pin only travels on a transport that is already trusted: the USB loopback right after pairing,
   or the phone's own pinned link. Every phone holding it got it from the PC itself.
4. **Pairing stays USB-only.** `POST /api/pair/claim` answers `403` on the LAN listener, so the one
   unauthenticated endpoint is never reachable from the network. A phone pairs on the cable and then
   drops it.
5. The phone tries its stored LAN URL first and `http://127.0.0.1:8741` second; the first transport
   whose `/api/health` answers is used for that cycle, so the same build works docked or untethered.
   The status event reports `transport` as `wifi`, `usb` or `none`.
6. Only `/api/health` answers without a token on the LAN listener. `/`, the settings routes and
   everything else remain loopback-only, and `Host`/`Origin` are still checked against the exact
   `address:port` forms of this machine, so DNS rebinding gains nothing.

Turning the listener off closes the socket; re-enabling reuses the same identity, so phones keep
working. Deleting `tls/` breaks every pin on purpose: those phones see a key they do not know and
pair again over USB.

### Rotating a phone token

`POST /api/device/rotate` with the phone's own token replaces it in place: the device id, name and
capabilities stay, the old token stops working immediately and the response carries the new one. The
phone commits it synchronously before its next request. There is deliberately no PC-side rotate: a
fresh secret would have no safe place to be delivered.

## Control deck (phase 3, first slice)

A paired phone presses a button in the deck strip and the agent executes one id from a fixed
vocabulary. No request carries a key name, a program, a path or an argument, and the helper checks
the same ids again before it touches the desktop.

| Action id | Capability | Effect |
|---|---|---|
| `media.prev`, `media.playpause`, `media.next`, `media.stop` | input | One media key press |
| `volume.up`, `volume.down` | input | One volume step (2% on Windows) |
| `mute` | input | Toggle mute |
| `lock` | power | Lock the desktop (`LockWorkStation`) |

`deck.json` adds user buttons with three more kinds:

| Kind | Capability | Effect |
|---|---|---|
| `launch` | input | Start one program or open one URL, exactly as written in the file |
| `keys` | input | Press one key combination from the fixed key-name list |
| `sleep` | power | Suspend the PC; always asks for a second press, whatever the file says |

```json
{ "version": 1, "buttons": [
  { "id": "spotify", "label": "Spotify", "icon": "🎵", "kind": "launch",
    "target": "C:\\Program Files\\Spotify\\Spotify.exe" },
  { "id": "zoom-mute", "label": "Zoom mute", "icon": "🎤", "kind": "keys",
    "keys": ["ctrl", "shift", "m"] },
  { "id": "sleep", "label": "Sleep", "icon": "🌙", "kind": "sleep" }
] }
```

The file lives next to `agent.json` and is re-read when its mtime changes, so an edit takes effect on
the next press. Validation is strict and per entry: `id` matches `[a-z0-9_-]{1,32}` and may not
reuse a built-in id, `label` is 1–24 characters, `icon` is optional (one glyph), and `launch` needs
an existing absolute path or an `http(s)` URL. `keys` takes 1–4 names, modifiers first, ending in one
real key: `a`–`z`, `0`–`9`, `f1`–`f12`, `enter`, `esc`, `tab`, `space`, `backspace`, `delete`,
`insert`, `home`, `end`, `pageup`, `pagedown`, `up`, `down`, `left`, `right`, `printscreen`, with
`ctrl`, `shift`, `alt`, `win` as modifiers. A bad entry is dropped and reported on the settings page;
the rest of the deck keeps working. At most 24 custom buttons.

`POST /api/action/{id}` answers `{ "ok": true, "action": "media.next" }`, or the usual error shape:
`404 unknown_action`, `403 forbidden` (the token lacks the capability that id needs),
`429 too_many_actions` (more than 20 actions in a second for one credential), `501
unsupported_platform` on a non-Windows agent, `502 action_failed` when the helper refuses, `504
helper_timeout` when it does not answer within 2.5 s. The route ignores a body unless the action is
marked `confirm`: then it must arrive as `{ "confirm": true }` (otherwise `428
confirmation_required`), which is how the panel turns a second tap into a deliberate one.

**Capabilities are granted by the PC, not claimed by the phone.** Pairing still issues `read` only.
The settings page calls `POST /api/settings/devices/{id}/capabilities` with `{ "input": true,
"power": true }`; the phone sees the new list on its next `/api/session` and shows the deck strip.
Withdrawing (`{ "input": false }`) hides the strip and every press becomes `403` again. Pairing never
grants control, so a phone cannot promote itself by asking.

The executor is `helper/winhelper.ps1`, spawned on demand and kept warm for five idle minutes. It
loads its P/Invoke block once, then reads one `{ "id", "cmd" }` JSON line per command on stdin and
answers one JSON line, so a press costs milliseconds rather than a PowerShell start-up. `keybd_event`
covers media, volume and mute; `lock` runs `rundll32.exe user32.dll,LockWorkStation`. Anything
outside the list is rejected and logged on stderr, never passed to a shell. Windows blocks synthetic
input on the secure desktop, so actions do nothing while the PC is locked.

## Layout and widgets (phase 2)

`GET /api/panel` answers the stored layout plus the page this phone should open on; the built-in page
is used until one is pushed:

```json
{ "layout": { "version": 3, "updatedAt": 1763670000000,
  "theme": { "accent": "#4fd1c5", "density": "comfortable", "background": "darker" },
  "pages": [ { "id": "home", "title": "Home",
  "widgets": [ { "id": "clock", "type": "clock", "x": 0, "y": 0, "w": 7, "h": 4, "title": null } ] } ] },
  "deck": [ … ], "startPage": "home" }
```

- The page is a 12×8 grid; `x`/`y` is the top-left cell and `w`/`h` the size in cells.
- `startPage` is the page id that phone opens on (see *Per-phone pages* below); an id the layout no
  longer has means "first page", so a layout edit cannot strand a phone.
- Widget ids are unique across the whole layout, not per page: state is keyed by id, and a duplicate
  would let one page paint another page's numbers.
- Widget types: `clock`, `date`, `system`, `network`, `nowplaying`, `phone`. A widget of any other
  type is dropped by the validator and never reaches the panel (`photo` waits for a scoped media
  route, see docs/PLAN.md).
- Validation is server-side and strict: unknown types and duplicate ids are skipped, rectangles are
  clipped to the page, ids must match `[a-z0-9_-]{1,32}`, at most 8 pages and 40 widgets per page.
  A `panel.json` that does not validate is ignored and the built-in page is served.

`clock` and `date` have no state: the phone renders them from its own clock, so there is no round
 trip and no drift. The other widgets carry:

| Widget | Payload |
|---|---|
| `system` | `{ "cpu": 0-100, "memUsed": bytes, "memTotal": bytes, "diskUsed": bytes, "diskTotal": bytes }` |
| `network` | `{ "name": "Wi-Fi", "ipv4": "192.168.1.4", "down": bytesPerSecond, "up": bytesPerSecond }` |
| `nowplaying` | `{ "playing": true, "status": "playing", "app": "Spotify", "title": "…", "artist": "…" }` |
| `phone` | `{ "level": 0-100, "charging": true, "updatedAt": epochMs }` |

`GET /api/state` returns `{ "seq": N, "t": epochMs, "widgets": { "<id>": payload } }`. A widget with
no data is omitted rather than sent as `null`.

### State stream

```
GET /api/stream?w=system,phone
Authorization: Bearer <token>
Accept: text/event-stream

event: state
data: {"seq":12,"t":1699999999999,"widgets":{"system":{...}}}

: ping
```

- The first event carries every subscribed widget; later events carry only the widgets whose payload
  changed, so a quiet desktop sends nothing between heartbeats.
- `w` limits the subscription to those widget ids. Without it the whole layout is subscribed; ids
  the layout does not define are ignored.
- Sampling runs at 2 s and only while at least one stream is open — an idle agent collects nothing.
- A `: ping` comment is written every 15 s so a dropped USB link is noticed within about a minute.
  Comment lines carry no data and must be ignored by clients.
- At most 8 streams may be open; the ninth gets `503 too_many_streams`.
- The stream is per-credential: the `phone` widget uses the battery reported by *that* device.

### Device self report

```
POST /api/device/self
{ "battery": { "level": 87, "charging": true } }
```

Only a paired phone may call this, and it can only describe itself: the device comes from the token,
never from the body. `level` must be a number and `charging` a boolean, otherwise `400`. A report
older than five minutes stops being served, so a sleeping phone shows "No report yet" instead of a
stale number. Batteries are kept in memory only.

## Layout editing (phase 4)

Layouts are edited in the browser at `http://127.0.0.1:8741/editor` (loopback only, same session token
as the settings page) and pushed to the agent. The phone is never involved in editing — it only sees
the result on its next `/api/panel` poll.

### Revisions and `If-Match`

Every accepted push becomes a new revision: `version` is assigned by the agent, never by the client.
The editor loads the layout and its revision (`"v3"`), then writes it back with that value:

```
PUT /api/settings/layout
If-Match: "v3"
{ "theme": { … }, "pages": [ … ] }
```

| Answer | Meaning |
|---|---|
| `200` `{ layout, etag, history }` | Accepted; `layout.version` is the new revision |
| `409 stale_layout` `{ error, layout, etag }` | Something else saved first; the body carries the current layout |
| `400 bad_layout` | The layout did not validate; nothing was written |
| `400 bad_request` | Body missing, not JSON, or over 256 KB |

`If-Match: *` overwrites unconditionally (tooling only). Omitting the header skips the check, which
is how a rollback writes. `version` and `updatedAt` in the request body are ignored.

### History and rollback

`GET /api/settings/layout` returns `history`: the last 10 revisions, newest first, each `{ version,
updatedAt, pages, theme }`. `POST /api/settings/layout/rollback` `{ "version": 2 }` restores that
revision **as a new revision**, so a rollback is itself undoable; an unknown or expired revision is
`404 unknown_version`. `POST /api/settings/layout/reload` re-reads `panel.json` after a hand edit and
returns the layout plus `error` when the file did not validate.

### Pages and themes

- Up to 8 pages; each page needs a `widgets` array (empty is fine) and an id matching
  `[a-z0-9_-]{1,24}`. Page ids are what a phone is assigned to, so they are stable identifiers.
- `theme` is three closed choices plus one colour: `accent` is `#rrggbb`, `density` is `compact`,
  `comfortable` or `roomy`, and `background` is `dark`, `darker` or `black`. Anything unrecognised
  falls back to the default instead of rejecting the layout; a layout can restyle the page, not the
  app, because the values only ever reach three CSS variables the panel owns.

### Per-phone pages

`POST /api/settings/devices/{id}/page` `{ "page": "media" }` stores which page that phone opens on
in `agent.json` (`devicePages`); `{ "page": null }` clears it. The assignment is kept on the PC, not
on the phone, so revoking a phone or editing the layout cannot leave a device pointing at a page it
chose itself. An assignment to a page that no longer exists is ignored and the first page is used.

### Editor and preview routes

| Route | Purpose |
|---|---|
| `/editor` | The editor page (loopback, mints a session token) |
| `/preview` | Preview host: runs the phone's own `/panel/index.html` bundle |
| `/panel/index.html`, `/panel/panel.js`, `/panel/panel.css` | The panel bundle, served from `agent/src/web/panel` — the same files the APK packages |

The preview is the phone's real renderer, not a second one: the editor posts `{ type: "layout",
layout, startPage, deck, capabilities }` into the iframe and the panel draws it. It accepts messages
from the same origin only, stores nothing, and never sees a device credential.

## Compatibility

Additive fields are allowed in any object. Clients must ignore unknown fields and unknown widget
types. Breaking changes bump `/api/v2`; v1 stays served for at least one release cycle.
