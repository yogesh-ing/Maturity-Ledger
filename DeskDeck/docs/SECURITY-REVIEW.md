# Security review — PhoneBox reference implementation

Scope: `https://github.com/ersinkoc/phonebox` at `main` (agent v0.9.21, Android v0.9.21).
Method: manual source review of the desktop agent (`agent/PhoneBox.Agent/*.cs`), the Android app
(`android/app/src/main/java/net/oxog/phonebox/deskpanel/*.kt`), the manifest and the install/update path.
This is not a dynamic penetration test and no dependency CVE scan was run (the reference needs the
.NET SDK, which is not installed here); dependency versions are listed in §5 for that follow-up.

Date: 2026-11-19.

---

## 0. Verdict

The security model is unusually strong for a hobby-scale project: loopback-by-default, a Host
allowlist plus `Sec-Fetch-Site`/Origin checks, a separate TLS listener with SHA-256 certificate
pinning and a 256-bit bearer token, a one-time 128-bit pairing code with a 5-minute lifetime and a
10-attempt cap, and no phone-supplied command strings. No hardcoded credentials, private keys or
tracking identifiers were found in the repository.

The issues below are mostly about **supply chain** (unsigned auto-updates, a pairing code that
passes through a third-party web page) and **blast radius** (one token unlocks every high-impact
capability). None of them is a trivial remote compromise of a default install, which binds to
127.0.0.1 only.

---

## 1. Findings

### F1 — Pairing code transits a third-party web page (Medium)

`PairingCodes.Link()` builds `https://ersinkoc.github.io/phonebox/pair/#v=1&pc=…&fp=…&c=<code>`, and the
manifest registers that GitHub Pages URL as a deep link (`android/app/src/main/AndroidManifest.xml`).
The fragment is not sent to GitHub's servers, but it **is** readable by that page's JavaScript. The
released app therefore depends on a GitHub Pages site it does not control at runtime; a compromised
repository, Pages deployment or GitHub account could capture one-time pairing codes (valid 5 minutes,
single use), and the attacker would still need LAN reachability to the PC while the code is live.

Impact: reduces pairing to "attacker with LAN access during a 5-minute window" instead of requiring
physical proximity to the QR code. Also makes silent code regurgitation possible on any future
compromise of that Pages site.

Reference mitigation that does work: the phone also shows a check code derived from the certificate
fingerprint, and the certificate is pinned from the link, so a hijacked code alone does not give a
usable session.

**DeskDeck rule:** never put pairing secrets in a URL handled by remote content. Pairing uses a
QR code containing a `deskdeck://pair` payload (or a local loopback page) only, plus a check code
shown on both screens.

### F2 — Auto-update verifies a checksum, not a signature (Medium)

`UpdateService.cs` downloads a release asset and its `SHA256SUMS-<v>.txt` from the same GitHub
release, compares hashes, and installs. `setup.ps1` / `setup.sh` are additionally fetched and executed
from `raw.githubusercontent.com` (`irm … | iex`).

Both files come from the same trust root, and the agent binary is unsigned (the project documents
that signing via SignPath is still pending). A compromised GitHub account, a malicious release, or a
DNS/TLS-terminating intermediary able to serve both files can therefore execute code on the PC, and
an update runs with the user's full privileges. SHA-256 detects corruption, not tampering.

**DeskDeck rule:** sign the update manifest with an offline Ed25519/minisign key whose public key is
embedded in the agent; verify the signature *before* downloading, then the hash. Auto-apply is opt-in;
the default is "notify me". Release builds must be Authenticode-signed.

### F3 — Android hostname verification disabled, pin covers only `chain[0]` (Low, accepted risk)

`AgentLink.kt:242` and `:447` set `HostnameVerifier { _, _ -> true }`, relying entirely on the
self-signed certificate pin. `pinnedFactory()` hashes `chain[0]` and never calls `checkValidity()`.
This is coherent for a pinned self-signed leaf (the pin *is* the identity), but it means:

- a future move to a CA-issued certificate silently loses hostname checking;
- an expired certificate is still accepted (currently moot: the cert is valid for 20 years).

**DeskDeck rule:** pin the SPKI hash, keep hostname verification on (or use a certificate with SANs
for the addresses), and check validity explicitly in the trust manager.

### F4 — One token, every capability (Low–Medium design risk)

`Lan.AllowedOverLan()` allowlists ~40 path groups behind the same LAN token: panel state, but also
`/api/console`, `/api/dictate`, `/api/trackpad`, `/api/run/{id}`, `/api/docker`, `/api/ha/toggle`,
`/api/approvals`, `/api/procs`. The phone is expected to hold that single token indefinitely, stored
in app preferences.

So a token extracted from the phone (backup, rooted device, malicious app with the right access) is
full remote control of the desktop — keystrokes, mouse, app launches, process kills, smart-home
actions — not just a read-only panel.

To the project's credit: the token is 256-bit, compared in constant time, separated from the webhook
token (with an explicit "shared value is not trusted" guard), and requests carrying an `Origin`
header are refused on the LAN listener, which blocks browser-based token replay.

**DeskDeck rule:** scope tokens by capability (`read`, `input`, `power`), issue a separate token per
phone, allow revocation from the desktop settings page, and require an explicit per-request flag for
destructive actions. Rotate on reconnect after a long absence.

### F5 — Identity headers are client-asserted (Low)

`X-PhoneBox-Device`, `X-PhoneBox-Model`, `X-PhoneBox-App` and `X-PhoneBox-WebView` decide the phone
registry, per-device pages and camera ownership (`Program.cs:213-219`, `:500`). They are trusted on
both listeners, so any holder of the token can impersonate another phone or invent devices.
`PhoneRegistry.Touch()` does validate the id (≤32 ASCII alphanumerics), so this is spoofing, not
injection.

**DeskDeck rule:** derive device identity from the credential (token id → device record), never from
a request header.

### F6 — Request body limits are generous outside pairing (Low)

Only the pairing claim caps the body (1 KB, `Program.cs:177`). Other endpoints rely on Kestrel's 30 MB
default, and some deserialize JSON directly (`/api/controls/radio-directory/check`, `/api/ha/test`,
`/api/obs-test`, `/api/rules/test`). A holder of the token, or a local process on the machine, can
force large allocations.

**DeskDeck rule:** global body cap (64 KB) with opt-in per-route overrides, plus per-token rate
limits on input events.

### F7 — Webhook token accepted in the query string (Low, acknowledged upstream)

`Lan.AuthorizeRequest()` accepts `?token=` for webhook senders "that cannot set headers", and the code
comments note URLs end up in logs. Any proxy, browser history or log aggregator on the path sees a
long-lived credential.

**DeskDeck rule:** header-only authentication. If a sender cannot set headers, use a per-sender URL
that is itself a high-entropy path segment with its own revocation, never a reusable token.

### F8 — Windows credential files rely on inherited profile ACLs (Low)

`AppPaths.CreatePrivate()` sets mode 0600 on macOS/Linux and explicitly notes "Windows keeps the
profile ACLs". That is usually fine on a single-user PC, but it leaves the LAN token, webhook token,
Home Assistant token, Spotify refresh token and Discord token readable by anything running as the
same user, and by administrators, with no explicit owner-only DACL.

**DeskDeck rule:** write secrets under `%APPDATA%` and apply an explicit DACL (current user only) via
`icacls`/`FileSecurity`, and prefer DPAPI (`CryptProtectData`) for refresh tokens.

### F9 — Release build falls back to the debug key (Low)

`android/app/build.gradle.kts` uses `signingConfigs.findByName("release") ?: debug`. A maintainer
without the keystore environment variables produces a release APK that upgrades over, and looks
identical to, a properly signed build, and Android will accept it as an update if the debug key was
ever used for a prior install.

**DeskDeck rule:** fail closed — release tasks abort without a configured keystore (implemented in
`android/app/build.gradle.kts`).

### F10 — No dependency locking (Info)

There is no `packages.lock.json` (NuGet) and no Gradle dependency verification. Package versions are
pinned in the manifests (`QRCoder 1.8.1`, `NAudio 3.3.0`, `androidx.webkit:webkit:1.12.1`), but
transitive resolution is not pinned or verified.

**DeskDeck rule:** `npm ci` with a committed lockfile, Gradle dependency verification metadata, and a
scheduled audit in CI.

### F11 — Unauthenticated LAN surface is discoverable (Info)

`POST /api/pair/claim` is reachable without a token by design, and the TLS listener on `0.0.0.0`
answers a handshake from any LAN host. Pairing itself is well protected (128-bit code, one use,
5 minutes, 10 failures, fixed-time compare, 1 KB body), so this is limited to service discovery and
log noise.

**DeskDeck rule:** same model, plus: listener off by default, bind explicitly, and log a visible
notice in the desktop UI whenever the Wi-Fi listener is enabled.

---

## 2. What the reference gets right (keep these)

- **Loopback first.** `k.Listen(IPAddress.Loopback, Port)`; the LAN listener exists only when enabled
  and has its own certificate.
- **DNS-rebinding defence.** The local listener requires `Host: 127.0.0.1` or `localhost`, so a
  malicious DNS name resolving to 127.0.0.1 cannot reach it (`Program.cs:190-200`).
- **CSRF defence in depth.** Origin allowlist for the local port, `Sec-Fetch-Site`/`Sec-Fetch-Mode`
  rejection via `BrowserGuard`, and `Origin`-bearing requests refused outright on the LAN listener.
- **Constant-time secret comparison** for both bearer and query tokens (`Lan.Matches`, `Lan.MatchesQuery`).
- **One-time pairing with bounded input.** 128 random bits, 5-minute TTL, 10 attempts, single use,
  fixed-time compare, 1 KB body, and a `PairClaim` parser that turns every malformed body into a
  refusal rather than an exception in the log.
- **No remote command strings.** The phone sends a shortcut/widget *id*; the command, arguments and
  working folder live only in `config.json`, and `Config.ToPublic()` strips commands, camera URLs and
  rule match criteria before the phone sees them.
- **Pinned TLS rather than "trust the network".** Self-signed leaf pinned by SHA-256, private key
  stored owner-only, and pairing happens over the pinned channel.
- **Android least privilege.** `INTERNET`, `VIBRATE`, and `RECORD_AUDIO` requested at use time only;
  cleartext permitted solely for `127.0.0.1`/`localhost`; WebView debugging only in debug builds.
- **WebView bridge scoped per frame.** A per-process random `bridgeKey` is injected only into the
  panel origin via `addDocumentStartJavaScript`, so sandboxed custom-HTML widgets cannot reach the
  native bridge (`MainActivity.kt:296-320`). Calls without the key fail closed.
- **Path-traversal guard** on `/panel/{*file}` (regex + `..` check) and owner-only config on Unix.

---

## 3. Areas that deserve a follow-up test (not confirmed bugs)

1. `ControlDeck` accepts `value`, `op`, `lease`, `id` query parameters; verify each adapter validates
   ranges and never feeds them into a shell or file path.
2. `FeedMonitor` and `RadioBrowser` fetch remote URLs from user config — confirm redirect and scheme
   restrictions to avoid SSRF into loopback/LAN services.
3. `AppCatalog`/`ShortcutIcons` launch or iconify arbitrary configured executables and paths — check
   symlink handling and `.png` temp writes.
4. `CameraFeeds` spawns `ffmpeg` with configured arguments — confirm `ArgumentList` usage (no string
   concatenation) and device-string validation.
5. Long-lived WebSocket `/api/state` and `/api/controls/listen`: check per-connection limits and
   cancellation on token revocation (there is no revocation today).

---

## 4. Dependency inventory for the CVE follow-up

| Component | Version | Where |
|---|---|---|
| .NET | `net10.0`, `net10.0-windows10.0.19041.0` | `agent/PhoneBox.Agent/PhoneBox.Agent.csproj` |
| QRCoder | 1.8.1 | agent |
| NAudio | 3.3.0 (Windows only) | agent |
| androidx.webkit | 1.12.1 | Android |
| Android compile/target SDK | 35 / min 21 | Android |
| QRCoder / NAudio transitive graph | unpinned | needs `dotnet list package --vulnerable` |

Run next: `dotnet list package --vulnerable --include-transitive` and a Gradle/OSV scan of the
Android dependency graph.

---

## 5. Summary table

| # | Issue | Severity | Category |
|---|---|---|---|
| F1 | Pairing code passes through a third-party GitHub Pages site | Medium | Credential exposure / supply chain |
| F2 | Update integrity is a same-origin checksum, binaries unsigned | Medium | Supply chain / RCE |
| F3 | Hostname verification off, pin only `chain[0]`, no validity check | Low | TLS |
| F4 | Single token grants all high-impact capabilities | Low–Medium | Authorization |
| F5 | Identity from client headers | Low | Spoofing |
| F6 | 30 MB default body limit on JSON endpoints | Low | DoS |
| F7 | Webhook token in URL query | Low | Credential leakage |
| F8 | Windows secrets rely on inherited ACLs | Low | Local data protection |
| F9 | Release falls back to debug signing | Low | Release integrity |
| F10 | No dependency locking/verification | Info | Supply chain |
| F11 | Discoverable unauthenticated pairing endpoint | Info | Reconnaissance |
