# DeskDeck

Turn an old Android phone into a customizable desk panel and touch control deck for Windows.

DeskDeck has two halves that talk to each other over USB (`adb reverse`) or Wi-Fi:

| Part | Runs on | What it does |
|---|---|---|
| `agent/` | your Windows PC | Serves panel state, receives touches, performs input/media/system actions |
| `android/` | the phone | Full-screen panel UI, pairing, connection management |

The design is informed by a source review of [PhoneBox](https://github.com/ersinkoc/phonebox) —
see [docs/SECURITY-REVIEW.md](docs/SECURITY-REVIEW.md) for what it does well and where we do it differently.

## Status

Phase 3b (v0.5.0): the app pairs with the agent over USB, keeps a per-phone token, renders a widget
board the PC pushes over an SSE state stream, works with the cable unplugged over a pinned TLS link
on the LAN, and presses media, volume, mute and lock buttons on the PC — plus your own buttons for
launching an app or URL, sending a key combo, and sleeping the PC, all defined in `deck.json` on the
PC. Macros and the trackpad are next in [docs/PLAN.md](docs/PLAN.md).

Working today:

- The agent keeps `adb reverse tcp:8741 tcp:8741` for every attached device, so the phone reaches
  loopback over the cable without a manual step.
- A one-time 8-character code (5 minutes, 5 attempts, single use) trades for a 32-byte token with
  capabilities; the agent stores only its SHA-256 and derives the device from the credential.
- The phone keeps the token in `SharedPreferences`; the WebView never receives it.
- `http://127.0.0.1:8741` serves the settings page: start pairing, compare the check code, see USB
  state, revoke a phone. Revoking makes the phone's next poll `401` and it clears its token.
- The panel is a 12×8 grid defined by a validated layout: clock, date, CPU/RAM/disk, network rates,
  now-playing, and the phone's own battery. Only changed widgets are pushed; an idle agent samples
  nothing at all. The panel dims after 90 s and wakes on touch.
- **Wi-Fi transport** (off by default): the settings page starts a TLS listener on the LAN. Its
  self-signed certificate is pinned by each phone (SHA-256 of the SPKI, learned over USB), so no
  public CA is involved. Pairing, the settings page and every other unauthenticated route stay
  loopback-only. The phone uses Wi-Fi when it can and falls back to the cable on its own.
- A phone can rotate its own token in place (panel footer → *Rotate key*); the PC revokes.
- **Control deck**: media play/pause/next/prev, volume, mute and lock from the panel. Pairing stays
  read-only; the PC grants control per phone from the settings page. Every press is one id from a
  fixed vocabulary, checked against the token's capability and re-checked by the PowerShell helper
  (`agent/helper/winhelper.ps1`) before it reaches the desktop. Windows ignores synthetic input while
  the desktop is locked.
- **Your own buttons**: `%APPDATA%\DeskDeck\deck.json` adds tiles that launch a program or URL, send
  one key combination, or sleep the PC. The agent re-reads the file when it changes; the phone draws
  whatever the agent advertises, so adding a button needs no new APK. Sleep always asks twice.

## Quick start

Check the toolchain first:

```
node --version      # 22 or newer
java -version       # 17 or newer
echo $ANDROID_HOME  # Android SDK with platform 36
```

Android app:

```
cd android
./gradlew installDebug          # phone connected over USB with debugging enabled
```

Agent:

```
cd agent
npm install
npm start                       # http://127.0.0.1:8741/api/health
```

Pair a phone:

1. Plug the phone in with USB debugging enabled (the agent sets up `adb reverse` by itself; the log
   line `usb: ... reverse=ok` confirms it).
2. Open `http://127.0.0.1:8741` on the PC and press **Start pairing**.
3. Type the eight characters into the phone, press **Pair**, and confirm the check code matches.

The agent creates `%APPDATA%\DeskDeck\agent.json` on first run. It holds the local token and the
SHA-256 of each phone token; never commit or share it.

## Layout

```
android/                 Android app (Kotlin, single Activity, local WebView panel)
  app/src/main/assets/panel/   panel UI (HTML/CSS/JS, no remote assets)
agent/                   Windows agent (Node 24 + TypeScript, no framework)
  src/                   HTTP API, config, pairing, state and the action registry
  helper/winhelper.ps1   input helper: media/volume keys, key combos, launch, lock, sleep
docs/                    architecture, protocol, plan, threat model
  SECURITY-REVIEW.md     findings from reviewing the PhoneBox reference
```

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — components, data flow, and why these choices
- [docs/PROTOCOL.md](docs/PROTOCOL.md) — wire format and auth headers
- [docs/SECURITY.md](docs/SECURITY.md) — threat model and non-negotiable rules
- [docs/SECURITY-REVIEW.md](docs/SECURITY-REVIEW.md) — review of the reference implementation
- [docs/BUILD-APK.md](docs/BUILD-APK.md) — prerequisites and exact commands to build/install the APK
- [docs/PLAN.md](docs/PLAN.md) — phased build plan with acceptance criteria

## License

No license file yet; add one before publishing.
