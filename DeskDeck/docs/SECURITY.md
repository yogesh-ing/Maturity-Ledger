# Security model

## Assets

1. Control of the PC (input injection, app launches, power actions).
2. Data shown on the panel (window titles, media, system stats, files).
3. Long-lived pairing credentials stored on the phone and in the agent config.

## Adversaries

| Adversary | Capability | Defence |
|---|---|---|
| Website open in a browser on the PC | Can POST to 127.0.0.1 | Host allowlist, Origin/`Sec-Fetch-Site` rejection, session token, no CORS headers |
| Anyone on the same Wi-Fi | Can reach the LAN listener | Listener off by default, TLS with SPKI pin, bearer token, no `Origin`, per-token rate limits |
| Malicious app on the phone | Can read app storage on rooted/backed-up devices | Per-phone tokens with capabilities, revocation, rotation, no secrets in `SharedPreferences` plaintext (use EncryptedSharedPreferences/Keystore) |
| Compromised update channel | Serves a malicious build | Ed25519-signed manifest, Authenticode-signed binaries, no silent auto-apply |
| Malicious widget HTML | Runs inside the panel WebView | Sandboxed iframe with opaque origin, no bridge access, CSP, no `file://` |
| Another local user | Reads files in the profile | Explicit owner-only DACL on `%APPDATA%\DeskDeck`; DPAPI for refresh tokens |

## Non-negotiable implementation rules

1. **Bind loopback by default.** The Wi-Fi listener only starts when the user enables it in settings,
   and the UI shows a persistent "reachable on the network" indicator while it is on.
2. **Verify `Host` and `Origin`.** Local requests must carry `Host: 127.0.0.1|localhost` and either no
   `Origin` or the agent's own origin. LAN requests must carry no `Origin` at all.
3. **Header-only credentials.** No token in a URL, ever. Query strings are logged by too many layers.
4. **Constant-time comparison** for every secret, using `crypto.timingSafeEqual` on equal-length
   buffers.
5. **Capability checks per route.** `read`, `input`, `power`. A read token must not be able to move
   the mouse.
6. **The phone never sends code to execute.** It sends action ids that must exist in the agent's
   config. No shell strings, no arbitrary paths, no arbitrary URLs.
7. **Pairing secrets stay local.** QR/custom scheme only; the one-time code is 128 bits, 5 minutes,
   single use, 10 attempts, compared in constant time.
8. **Pin properly.** SPKI SHA-256 pin, hostname verification enabled, certificate validity checked,
   pin compared with the pairing record before any credential is sent.
9. **Fail closed on release signing.** A release build without a keystore aborts. No debug-key
   fallback.
10. **Bounded input.** 64 KB JSON bodies, per-route rate limits on `/api/input/*`, no unbounded arrays,
    request logging without query strings or headers.
11. **Explicit file permissions.** Owner-only DACL on the config directory; secrets encrypted with
    DPAPI at rest on Windows.
12. **Least privilege in the helper.** `WinHelper.exe` implements a fixed command allowlist; it never
    accepts a program path, shell command, or file location from the network.
13. **Signed updates.** Verify the manifest signature before downloading; verify the artifact hash
    after. Auto-apply is opt-in.

## What we deliberately do not do

- No cloud relay, account system or telemetry. Nothing leaves the two devices except explicit
  user-configured integrations (weather, etc.) — and those are documented per feature.
- No Tailscale/ngrok bundling. If the user wants remote access, that is a VPN decision they make
  outside the app.
- No "convenience" bypass such as `?token=` or a debug port left enabled in release builds.

## Reporting

Once the repository is published, add a `SECURITY.md` at the root with a private reporting address
and a 90-day disclosure window. Until then, this file is the internal reference.
