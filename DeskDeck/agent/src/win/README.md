# WinHelper (phase 3)

Node can serve HTTP and read system stats, but it cannot inject input, change the master volume or
read the media session. That work lives in `WinHelper.exe`, a single C# file compiled by the in-box
`.NET Framework` compiler (`C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe`), so no .NET SDK
is required:

```
scripts/build-helper.ps1
```

## Contract

The agent spawns the helper once and talks newline-delimited JSON over stdin/stdout:

```json
{ "id": 1, "op": "mouse.move", "dx": 12, "dy": -4 }
{ "id": 1, "ok": true }
```

## Rules

- **Fixed op allowlist** (`mouse.*`, `key.*`, `media.*`, `volume.*`, `power.*`, `screen.*`). Unknown
  ops are rejected and logged. The helper never accepts a program path, shell command, script or file
  location from the network.
- **Validated payloads**: key names resolved from a table, mouse deltas and volume clamped, no string
  interpolation into a shell.
- **Single instance**, exits when stdin closes, no service, no elevation.
- **No persistence**: nothing is written to disk except an optional log in `%LOCALAPPDATA%`.

## Input on the lock screen

Windows ignores `SendInput` on the secure desktop. Phase 3 documents "actions run while the session is
unlocked"; a privileged service is explicitly out of scope.
