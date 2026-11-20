# Architecture

## Goals

- Reuse an old Android phone (Android 6+, low RAM) as an always-on desk panel.
- Full-screen, glanceable panel plus real touch controls for Windows.
- Never expose the PC beyond the local machine unless the user explicitly pairs a phone.
- Layouts are user-editable on the PC, not on the phone.

## Components

```
┌────────────────────────── Windows PC ──────────────────────────┐
│  agent (Node 24, TypeScript)                                   │
│   ├─ http/  loopback :8741  + optional LAN TLS listener        │
│   ├─ auth   token store, capabilities, pairing                 │
│   ├─ panel  layout store, state aggregation, push stream       │
│   ├─ control adapters ──► WinHelper.exe (C#, compiled by csc)   │
│   └─ web/   desktop layout editor + live preview                │
└────────────────────────────────────────────────────────────────┘
             ▲ adb reverse (USB)         ▲ pinned TLS + token (Wi-Fi)
             │                           │
┌────────────┴───────────────────────────┴───────────────────────┐
│  Android app (Kotlin, single Activity)                          │
│   ├─ WebView: local panel UI (assets/panel, https asset host)   │
│   ├─ transport: HTTP + SSE/WebSocket, pinning, reconnect        │
│   └─ pairing: QR → deskdeck://pair, check code, token storage   │
└────────────────────────────────────────────────────────────────┘
```

### Why a WebView panel

The panel is HTML/CSS/JS served from app assets. That gives us:

- one implementation of the panel rendered on the phone and in the desktop "live preview";
- widget layouts as JSON, editable in a browser without shipping new APKs;
- no Compose/AndroidX UI churn on a 10-year-old device, and a small APK.

The reference app does the same and it is the right call here. The trade-off — a native bridge in the
WebView — is handled the same way: the bridge is keyed per process and only injected into the panel
origin, so sandboxed widget frames cannot reach it.

### Why Node for the agent

- Already installed on the target machine; no SDK to bootstrap.
- Node's HTTP stack is enough: no framework, no ORM, small dependency surface.
- Windows integration that Node cannot do itself (SendInput, Core Audio, media keys, screen capture)
  lives in `WinHelper.exe`, a small C# program compiled by the in-box `csc.exe` (see
  `agent/src/win/README.md`). It speaks newline-delimited JSON over stdin/stdout, so the privileged
  surface stays in one auditable file.

### Transport

| Mode | Path | Security |
|---|---|---|
| USB | `adb reverse tcp:8741 tcp:8741`, phone → `http://127.0.0.1:8741` | Host allowlist; cleartext limited to loopback by Android network security config |
| Wi-Fi | phone → `https://<pc>:<port>` | Self-signed cert, SPKI pin, bearer token, path allowlist, no `Origin` |

State is pushed as Server-Sent Events; input events go up as small POSTs. WebSocket is reserved for
the trackpad where full-duplex matters.

## Data model

```jsonc
// layout.json (owned by the PC, pushed to the phone)
{
  "version": 3,
  "pages": [
    {
      "id": "home", "title": "Home",
      "widgets": [
        { "id": "w1", "type": "clock",   "x": 0, "y": 0, "w": 4, "h": 2, "options": {} },
        { "id": "w2", "type": "cpu",     "x": 4, "y": 0, "w": 4, "h": 2, "options": {} },
        { "id": "w3", "type": "button",  "x": 0, "y": 2, "w": 2, "h": 2,
          "options": { "action": "shortcut.mute" } }
      ]
    }
  ]
}
```

Actions referenced by id only (`shortcut.mute`); the executable, arguments and hotkey live in the
agent's config and never travel to the phone.

## Directories

```
android/app/src/main/assets/panel/   panel UI (single source of truth for the front end)
android/app/src/main/java/dev/deskdeck/panel/   Activity + transport + pairing
agent/src/                           API, layout store, adapters
agent/src/win/                       WinHelper source and build script
docs/                                this folder
```

## Non-goals (for now)

- Multi-PC control from one phone (the model allows it later; one pairing per PC).
- macOS/Linux agents. The protocol is portable, the control adapters are not.
- Any cloud service, account, telemetry or analytics.
