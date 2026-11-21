import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { randomBytes } from "node:crypto";
import { readFileSync } from "node:fs";
import { hostname } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { loadConfig, saveConfig, type Capability, type DeviceRecord } from "./config.ts";
import { DeviceStore } from "./devices.ts";
import { readJson } from "./http.ts";
import { LayoutStore } from "./layouts.ts";
import { Pairing } from "./pairing.ts";
import { layoutWidgetIds, StateHub } from "./state.ts";
import { pageIdFor } from "./layouts.ts";
import { AdbReverse } from "./adb.ts";
import { lanAddresses } from "./tls.ts";
import { WifiServer } from "./wifi.ts";
import { actionFor, actionIds } from "./actions.ts";
import { DeckStore } from "./deck.ts";
import { ActionRunner } from "./win.ts";
import {
  bearerToken,
  hostAllowed,
  originAllowed,
  remoteIsLoopback,
  safeEqual,
  safeLogPath,
} from "./security.ts";

const VERSION = "0.6.0";
const PAIR_BODY_LIMIT = 1024;
const SETTINGS_SESSION_MS = 12 * 60 * 60_000;
const WEB_DIR = join(dirname(fileURLToPath(import.meta.url)), "web");

const config = loadConfig();
const port = Number(process.env.DESKDECK_PORT ?? config.port);
const pcName = hostname();
const devices = new DeviceStore(config, () => saveConfig(config));
const pairing = new Pairing(config.localToken, port);
const layouts = new LayoutStore();
const hub = new StateHub(layouts);
const adb = new AdbReverse(port);
const wifi = new WifiServer(pcName);
const runner = new ActionRunner();
const deck = new DeckStore();

/** Which listener answered: the loopback one over USB, or the TLS one on the LAN. */
type Listener = { port: number; secure: boolean };

/**
 * Host headers this listener may legitimately see. Loopback names are always allowed by the
 * security helper; a LAN listener adds the exact `address:port` forms of this machine only, so a
 * rebinding hostname still cannot reach the API.
 */
function listenerHosts(listener: Listener): string[] {
  if (!listener.secure) return [];
  return lanAddresses().map((address) => `${address}:${listener.port}`);
}

/** Open state streams. A phone holds one; the cap keeps a buggy client from pinning the agent. */
const MAX_STREAMS = 8;
let activeStreams = 0;
const STREAM_HEARTBEAT_MS = 15_000;

/** Short-lived credentials for the settings page, so the long-lived local token never reaches a browser. */
const settingsSessions = new Map<string, number>();

function send(res: ServerResponse, status: number, body: unknown, headers: Record<string, string> = {}): void {
  const payload = Buffer.from(JSON.stringify(body), "utf8");
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": payload.length,
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "x-deskdeck-agent": VERSION,
    ...headers,
  });
  res.end(payload);
}

function sendText(res: ServerResponse, status: number, type: string, body: string): void {
  const payload = Buffer.from(body, "utf8");
  res.writeHead(status, {
    "content-type": `${type}; charset=utf-8`,
    "content-length": payload.length,
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "x-deskdeck-agent": VERSION,
  });
  res.end(payload);
}

function asset(name: string): string {
  return readFileSync(join(WEB_DIR, name), "utf8");
}

/**
 * Static pages on the loopback listener. `token` marks the two HTML pages that mint a settings
 * session; scripts and styles are inert without one. The panel bundle is served here too, because the
 * editor's preview runs the phone's own page rather than a second renderer that could drift.
 */
const STATIC_PAGES: Record<string, { file: string; type: string; token?: boolean }> = {
  "/": { file: "settings.html", type: "text/html", token: true },
  "/settings.js": { file: "settings.js", type: "text/javascript" },
  "/settings.css": { file: "settings.css", type: "text/css" },
  "/editor": { file: "editor.html", type: "text/html", token: true },
  "/editor.js": { file: "editor.js", type: "text/javascript" },
  "/editor.css": { file: "editor.css", type: "text/css" },
  "/preview": { file: "preview.html", type: "text/html" },
  "/panel/index.html": { file: "panel/index.html", type: "text/html" },
  "/panel/panel.js": { file: "panel/panel.js", type: "text/javascript" },
  "/panel/panel.css": { file: "panel/panel.css", type: "text/css" },
};

function issueSettingsSession(): string {
  const now = Date.now();
  for (const [token, expires] of settingsSessions) {
    if (expires <= now) settingsSessions.delete(token);
  }
  const token = randomBytes(32).toString("base64url");
  settingsSessions.set(token, now + SETTINGS_SESSION_MS);
  return token;
}

type Caller = { kind: "local" } | { kind: "device"; device: DeviceRecord };

/** Identity comes from the credential: a device token resolves to its registry record. */
function callerOf(req: IncomingMessage): Caller | null {
  const token = bearerToken(req.headers.authorization);
  if (!token) return null;
  if (safeEqual(token, config.localToken)) return { kind: "local" };
  const device = devices.authenticate(token);
  return device ? { kind: "device", device } : null;
}

function hasCapability(caller: Caller, needed: Capability): boolean {
  if (caller.kind === "local") return true;
  devices.touch(caller.device);
  return caller.device.capabilities.includes(needed);
}

/**
 * Pressing a tile is cheap to repeat; a stuck phone must not turn into a machine-gun on the
 * desktop. Per credential, at most this many actions in any one second.
 */
const ACTION_BURST = 20;
const actionWindows = new Map<string, { started: number; count: number }>();

function actionAllowed(key: string): boolean {
  const now = Date.now();
  const entry = actionWindows.get(key);
  if (!entry || now - entry.started > 1_000) {
    actionWindows.set(key, { started: now, count: 1 });
    return true;
  }
  entry.count += 1;
  return entry.count <= ACTION_BURST;
}

function settingsAllowed(req: IncomingMessage): boolean {
  const token = bearerToken(req.headers.authorization);
  if (!token) return false;
  if (safeEqual(token, config.localToken)) return true;
  const expires = settingsSessions.get(token);
  if (expires === undefined) return false;
  if (expires <= Date.now()) {
    settingsSessions.delete(token);
    return false;
  }
  return true;
}

async function handle(req: IncomingMessage, res: ServerResponse, listener: Listener): Promise<void> {
  const start = Date.now();
  res.on("finish", () => {
    console.log(`${req.method} ${safeLogPath(req.url)} ${res.statusCode} ${Date.now() - start}ms`);
  });

  const hosts = listenerHosts(listener);
  const origins = hosts.map((host) => `https://${host}`);
  if (!hostAllowed(req.headers.host, listener.port, hosts) || !originAllowed(req.headers.origin, listener.port, origins)) {
    send(res, 403, { error: { code: "forbidden", message: "Host or Origin not allowed" } });
    return;
  }

  const path = (req.url ?? "/").split("?", 1)[0] ?? "/";
  const method = req.method ?? "GET";

  // Loopback-only liveness probe: no credential, no data about the machine.
  if (method === "GET" && path === "/api/health") {
    send(res, 200, { ok: true, agent: VERSION, port: listener.port, pc: pcName });
    return;
  }

  const pageFile = STATIC_PAGES[path];
  if (method === "GET" && pageFile) {
    // Settings, the editor and the panel bundle belong to the loopback listener only, even for the PC.
    if (!remoteIsLoopback(req) || listener.secure) {
      send(res, 404, { error: { code: "not_found", message: "Unknown endpoint" } });
      return;
    }
    const body = asset(pageFile.file);
    sendText(
      res,
      200,
      pageFile.type,
      pageFile.token ? body.replace("__SETTINGS_TOKEN__", issueSettingsSession()) : body,
    );
    return;
  }

  // Pairing claim: reachable without a token, over USB only, one live code, bounded body. Pairing
  // over the network would need its own attempt budget, so a phone pairs on the cable and then
  // remembers the Wi-Fi address and pin it is given here.
  if (method === "POST" && path === "/api/pair/claim") {
    if (!remoteIsLoopback(req) || listener.secure) {
      send(res, 403, { error: { code: "forbidden", message: "Pairing is USB only" } });
      return;
    }
    const body = await readJson(req, PAIR_BODY_LIMIT);
    const view = pairing.view();
    const claim = pairing.claim(extractCode(body));
    if (claim !== "ok") {
      const status = claim === "no-session" ? 409 : claim === "expired" ? 410 : claim === "too-many" ? 429 : 401;
      send(res, status, { error: { code: claim, message: "Pairing code rejected" } });
      return;
    }
    const { record, token } = devices.issue({ model: extractModel(body) });
    console.log(`paired ${record.id} (${record.name})`);
    send(res, 200, {
      token,
      pc: pcName,
      check: view?.check ?? "",
      device: { id: record.id, name: record.name, capabilities: record.capabilities },
    });
    return;
  }

  if (path.startsWith("/api/settings/")) {
    if (listener.secure || !settingsAllowed(req)) {
      send(res, 401, { error: { code: "unauthorized", message: "Missing or invalid settings session" } });
      return;
    }

    if (method === "GET" && path === "/api/settings/state") {
      send(res, 200, {
        agent: VERSION,
        pc: pcName,
        port,
        pairing: pairing.view(),
        devices: devices.list(),
        usb: adb.state,
        wifi: wifi.state(),
        deck: deck.view(),
        layout: layoutSummary(),
        devicePages: config.devicePages,
      });
      return;
    }

    // The editor reads the layout with its revision, then writes it back with `If-Match`. A save
    // built on an older revision is refused instead of quietly replacing someone else's work.
    if (method === "GET" && path === "/api/settings/layout") {
      send(
        res,
        200,
        {
          layout: layouts.get(),
          etag: layouts.etag(),
          history: layouts.history(),
          deck: deckPayload(),
          error: layouts.error,
        },
        { etag: layouts.etag() },
      );
      return;
    }

    if (method === "PUT" && path === "/api/settings/layout") {
      const body = await readJson(req, 256 * 1024);
      if (body === null) {
        send(res, 400, { error: { code: "bad_request", message: "Expected a JSON layout" } });
        return;
      }
      const result = layouts.push(body, headerOf(req, "if-match"));
      if (!result.ok) {
        const status = result.code === "conflict" ? 409 : result.code === "not_found" ? 404 : 400;
        send(
          res,
          status,
          {
            error: {
              code: result.code === "conflict" ? "stale_layout" : "bad_layout",
              message: result.code === "conflict"
                ? "The layout changed since you loaded it"
                : "The layout did not validate",
            },
            layout: result.current,
            etag: layouts.etag(),
          },
          { etag: layouts.etag() },
        );
        return;
      }
      console.log(`layout: pushed v${result.layout.version}`);
      send(
        res,
        200,
        { layout: result.layout, etag: layouts.etag(), history: layouts.history() },
        { etag: layouts.etag() },
      );
      return;
    }

    if (method === "POST" && path === "/api/settings/layout/rollback") {
      const body = await readJson(req, 1024);
      const version = versionOf(body);
      if (version === null) {
        send(res, 400, { error: { code: "bad_request", message: "Expected an integer version" } });
        return;
      }
      const result = layouts.rollback(version);
      if (!result.ok) {
        send(res, 404, {
          error: { code: "unknown_version", message: "That revision is no longer in the history" },
          history: layouts.history(),
        });
        return;
      }
      console.log(`layout: restored v${version} as v${result.layout.version}`);
      send(
        res,
        200,
        { layout: result.layout, etag: layouts.etag(), history: layouts.history() },
        { etag: layouts.etag() },
      );
      return;
    }

    // Re-reads panel.json after a hand edit; the editor's Reload button means "show me the file".
    if (method === "POST" && path === "/api/settings/layout/reload") {
      const layout = layouts.reload();
      send(
        res,
        200,
        { layout, etag: layouts.etag(), history: layouts.history(), error: layouts.error },
        { etag: layouts.etag() },
      );
      return;
    }

    // Which page one phone opens on. The agent keeps this, not the phone: a layout change cannot
    // strand a device on a page it chose itself.
    if (method === "POST" && path.startsWith("/api/settings/devices/") && path.endsWith("/page")) {
      const id = decodeURIComponent(path.slice("/api/settings/devices/".length, -"page".length - 1));
      const body = await readJson(req, 512);
      const wanted = pageOf(body);
      if (wanted === undefined) {
        send(res, 400, { error: { code: "bad_request", message: "Expected page: an id or null" } });
        return;
      }
      const record = devices.list().find((device) => device.id === id);
      if (!record) {
        send(res, 404, { error: { code: "not_found", message: "Unknown device" } });
        return;
      }
      if (wanted === null) delete config.devicePages[id];
      else config.devicePages[id] = wanted;
      saveConfig(config);
      send(res, 200, { ok: true, page: wanted });
      return;
    }

    // Turning the LAN listener on or off. Persisted, so it survives a restart of the agent.
    if (method === "POST" && path === "/api/settings/wifi") {
      const body = await readJson(req, 1024);
      const request = wifiRequest(body);
      if (!request) {
        send(res, 400, { error: { code: "bad_request", message: "Expected enabled and an optional port" } });
        return;
      }
      if (request.enabled) {
        // The Host policy of the LAN listener follows the port it is actually bound to.
        secureListener.port = request.port;
        const started = await wifi.start(request.port, secureHandler);
        config.wifi = { enabled: started, port: request.port };
        saveConfig(config);
        if (!started) {
          send(res, 500, {
            error: { code: "wifi_failed", message: wifi.state().error ?? "The listener did not start" },
            wifi: wifi.state(),
          });
          return;
        }
      } else {
        wifi.stop();
        config.wifi = { enabled: false, port: request.port };
        saveConfig(config);
      }
      send(res, 200, wifi.state());
      return;
    }

    if (method === "POST" && path === "/api/settings/pairing") {
      send(res, 200, pairing.start());
      return;
    }

    if (method === "DELETE" && path === "/api/settings/pairing") {
      pairing.cancel();
      send(res, 200, { ok: true });
      return;
    }

    // Granting or withdrawing control. Pairing issues `read` only, so this is the deliberate step
    // that lets a phone press the deck; it takes effect on the phone's next poll.
    if (method === "POST" && path.startsWith("/api/settings/devices/") && path.endsWith("/capabilities")) {
      const id = decodeURIComponent(path.slice("/api/settings/devices/".length, -"capabilities".length - 1));
      const body = await readJson(req, 1024);
      const wanted = controlRequest(body);
      if (!wanted) {
        send(res, 400, { error: { code: "bad_request", message: "Expected input (and optional power) booleans" } });
        return;
      }
      const record = devices.setCapabilities(id, wanted);
      if (!record) {
        send(res, 404, { error: { code: "not_found", message: "Unknown device" } });
        return;
      }
      console.log(`${wanted.input ? "granted" : "withdrew"} control for ${record.id}`);
      send(res, 200, { ok: true, capabilities: record.capabilities });
      return;
    }

    if (method === "DELETE" && path.startsWith("/api/settings/devices/")) {
      const id = path.slice("/api/settings/devices/".length);
      const removed = devices.remove(decodeURIComponent(id));
      send(res, removed ? 200 : 404, removed ? { ok: true } : { error: { code: "not_found", message: "Unknown device" } });
      return;
    }

    send(res, 404, { error: { code: "not_found", message: "Unknown endpoint" } });
    return;
  }

  const caller = callerOf(req);
  if (!caller) {
    send(res, 401, { error: { code: "unauthorized", message: "Missing or invalid token" } });
    return;
  }

  if (method === "GET" && path === "/api/session") {
    if (!hasCapability(caller, "read")) {
      send(res, 403, { error: { code: "forbidden", message: "Token lacks read capability" } });
      return;
    }
    // A paired phone learns where the LAN listener is and pins its key, then drops the cable.
    const lan = wifi.state();
    send(res, 200, {
      pc: pcName,
      agent: VERSION,
      transport: listener.secure ? "wifi" : "usb",
      wifi: lan.enabled ? { urls: lan.urls, pin: lan.pin } : null,
      device: caller.kind === "device"
        ? { id: caller.device.id, name: caller.device.name, capabilities: caller.device.capabilities }
        : { id: "local", name: "This computer", capabilities: ["read", "input", "power"] },
    });
    return;
  }

  if (method === "GET" && path === "/api/panel") {
    if (!hasCapability(caller, "read")) {
      send(res, 403, { error: { code: "forbidden", message: "Token lacks read capability" } });
      return;
    }
    send(res, 200, { layout: layouts.get(), deck: deckPayload(), startPage: startPageFor(deviceIdOf(caller)) });
    return;
  }

  if (method === "GET" && path === "/api/state") {
    if (!hasCapability(caller, "read")) {
      send(res, 403, { error: { code: "forbidden", message: "Token lacks read capability" } });
      return;
    }
    send(res, 200, hub.snapshot(deviceIdOf(caller), subscribedIds(req)));
    return;
  }

  if (method === "GET" && path === "/api/stream") {
    if (!hasCapability(caller, "read")) {
      send(res, 403, { error: { code: "forbidden", message: "Token lacks read capability" } });
      return;
    }
    openStream(req, res, deviceIdOf(caller));
    return;
  }

  // The phone reports its own battery; it can never name a different device.
  if (method === "POST" && path === "/api/device/self") {
    const device = caller.kind === "device" ? caller.device : null;
    if (!device) {
      send(res, 403, { error: { code: "forbidden", message: "Only a paired phone may report its status" } });
      return;
    }
    const body = await readJson(req, 1024);
    const battery = extractBattery(body);
    if (!battery) {
      send(res, 400, { error: { code: "bad_request", message: "Expected battery.level and battery.charging" } });
      return;
    }
    hub.phones.report(device.id, battery.level, battery.charging);
    send(res, 200, { ok: true });
    return;
  }

  // A phone rotates its own credential in place: same device, same capabilities, new token.
  if (method === "POST" && path === "/api/device/rotate") {
    const device = caller.kind === "device" ? caller.device : null;
    if (!device) {
      send(res, 403, { error: { code: "forbidden", message: "Only a paired phone may rotate its token" } });
      return;
    }
    const token = devices.rotate(device);
    console.log(`rotated token for ${device.id}`);
    send(res, 200, { token, device: { id: device.id, name: device.name, capabilities: device.capabilities } });
    return;
  }

  // The control deck. The phone names an action id from a fixed vocabulary; the capability it needs
  // is a property of that id, not of the request, so a read-only token cannot press anything.
  if (method === "POST" && path.startsWith("/api/action/")) {
    const id = decodeURIComponent(path.slice("/api/action/".length));
    const spec = actionFor(id) ?? deck.specFor(id);
    if (!spec) {
      send(res, 404, { error: { code: "unknown_action", message: "Unknown action" } });
      return;
    }
    if (!hasCapability(caller, spec.capability)) {
      send(res, 403, { error: { code: "forbidden", message: `Token lacks ${spec.capability} capability` } });
      return;
    }
    // A disruptive action needs a second press: the phone arms the tile and re-sends with confirm,
    // so a stray tap in a pocket cannot put the PC to sleep.
    if (spec.confirm) {
      const body = await readJson(req, 512);
      if (!confirmationOf(body)) {
        send(res, 428, {
          error: { code: "confirmation_required", message: "Press again to confirm this action" },
        });
        return;
      }
    }
    const key = caller.kind === "device" ? caller.device.id : "local";
    if (!actionAllowed(key)) {
      send(res, 429, { error: { code: "too_many_actions", message: "Too many actions, slow down" } });
      return;
    }
    const result = await runner.run(spec.command);
    if (result.ok) {
      send(res, 200, { ok: true, action: id });
      return;
    }
    const status = result.code === "unsupported_platform" ? 501 : result.code === "helper_timeout" ? 504 : 502;
    send(res, status, { error: { code: result.code, message: result.message }, action: id });
    return;
  }

  send(res, 404, { error: { code: "not_found", message: "Unknown endpoint" } });
}

function deviceIdOf(caller: Caller): string | null {
  return caller.kind === "device" ? caller.device.id : null;
}

/**
 * `?w=a,b` subscribes to those widget ids only; an empty or unknown list means the whole layout,
 * so a page that scrolls off screen can stop costing anything by dropping its ids.
 */
function subscribedIds(req: IncomingMessage): Set<string> {
  const known = layoutWidgetIds(layouts);
  const raw = new URL(req.url ?? "/", "http://127.0.0.1").searchParams.get("w") ?? "";
  const ids = new Set<string>();
  for (const entry of raw.split(",")) {
    const id = entry.trim();
    if (id && known.has(id)) ids.add(id);
  }
  return ids.size > 0 ? ids : known;
}

function openStream(req: IncomingMessage, res: ServerResponse, deviceId: string | null): void {
  if (activeStreams >= MAX_STREAMS) {
    send(res, 503, { error: { code: "too_many_streams", message: "Too many open streams" } });
    return;
  }
  activeStreams += 1;
  res.writeHead(200, {
    "content-type": "text/event-stream; charset=utf-8",
    "cache-control": "no-store",
    connection: "keep-alive",
    "x-content-type-options": "nosniff",
    "x-deskdeck-agent": VERSION,
  });
  res.flushHeaders();

  const write = (event: string, data: unknown): void => {
    res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  };

  const unsubscribe = hub.subscribe({ deviceId, ids: subscribedIds(req), send: write });
  const heartbeat = setInterval(() => res.write(": ping\n\n"), STREAM_HEARTBEAT_MS);
  heartbeat.unref();

  let closed = false;
  const close = (): void => {
    if (closed) return;
    closed = true;
    clearInterval(heartbeat);
    unsubscribe();
    activeStreams -= 1;
  };
  req.on("close", close);
  res.on("close", close);
  res.on("error", close);
}

function extractBattery(body: unknown): { level: number; charging: boolean } | null {
  if (typeof body !== "object" || body === null) return null;
  const battery = (body as { battery?: unknown }).battery;
  if (typeof battery !== "object" || battery === null) return null;
  const level = (battery as { level?: unknown }).level;
  const charging = (battery as { charging?: unknown }).charging;
  if (typeof level !== "number" || !Number.isFinite(level)) return null;
  if (typeof charging !== "boolean") return null;
  return { level, charging };
}

function extractCode(body: unknown): unknown {
  if (typeof body !== "object" || body === null) return null;
  return (body as { code?: unknown }).code ?? null;
}

function extractModel(body: unknown): string {
  if (typeof body !== "object" || body === null) return "";
  const device = (body as { device?: unknown }).device;
  if (typeof device !== "object" || device === null) return "";
  const model = (device as { model?: unknown }).model;
  return typeof model === "string" ? model : "";
}

/** `{enabled, port?}` from the settings page; anything else is refused rather than guessed at. */
function wifiRequest(body: unknown): { enabled: boolean; port: number } | null {
  if (typeof body !== "object" || body === null) return null;
  const enabled = (body as { enabled?: unknown }).enabled;
  if (typeof enabled !== "boolean") return null;
  const raw = (body as { port?: unknown }).port;
  if (raw === undefined) return { enabled, port: config.wifi.port };
  if (typeof raw !== "number" || !Number.isInteger(raw) || raw < 1024 || raw > 65535) return null;
  return { enabled, port: raw };
}

/** The panel draws this list: built-ins first, then whatever `deck.json` configured. */
function deckPayload(): unknown[] {
  const items: unknown[] = [];
  for (const id of actionIds()) {
    const spec = actionFor(id);
    if (!spec) continue;
    items.push({
      id,
      label: spec.label,
      icon: spec.icon,
      capability: spec.capability,
      confirm: spec.confirm,
    });
  }
  for (const entry of deck.entries()) {
    items.push({
      id: entry.id,
      label: entry.spec.label,
      icon: entry.spec.icon,
      capability: entry.spec.capability,
      confirm: entry.spec.confirm,
    });
  }
  return items;
}

/** The page a phone opens on: its own assignment, else the layout's first page. */
function startPageFor(deviceId: string | null): string {
  return pageIdFor(layouts.get(), deviceId ? config.devicePages[deviceId] : null);
}

/** Page counts and the revision, for the settings card: the full layout belongs to the editor. */
function layoutSummary(): unknown {
  const layout = layouts.get();
  return {
    version: layout.version,
    updatedAt: layout.updatedAt,
    etag: layouts.etag(),
    theme: layout.theme,
    pages: layout.pages.map((page) => ({ id: page.id, title: page.title, widgets: page.widgets.length })),
    error: layouts.error,
  };
}

function headerOf(req: IncomingMessage, name: string): string | null {
  const value = req.headers[name];
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function versionOf(body: unknown): number | null {
  if (typeof body !== "object" || body === null) return null;
  const version = (body as { version?: unknown }).version;
  return typeof version === "number" && Number.isInteger(version) && version >= 0 ? version : null;
}

/** `page` is a page id, `null` to clear the assignment, or undefined when the body is unusable. */
function pageOf(body: unknown): string | null | undefined {
  if (typeof body !== "object" || body === null) return undefined;
  const page = (body as { page?: unknown }).page;
  if (page === null) return null;
  return typeof page === "string" && /^[a-z0-9_-]{1,24}$/.test(page) ? page : undefined;
}

function confirmationOf(body: unknown): boolean {
  if (typeof body !== "object" || body === null) return false;
  return (body as { confirm?: unknown }).confirm === true;
}

/** `{input, power?}` for the settings page; anything else is refused rather than guessed at. */
function controlRequest(body: unknown): { input: boolean; power: boolean } | null {
  if (typeof body !== "object" || body === null) return null;
  const input = (body as { input?: unknown }).input;
  const power = (body as { power?: unknown }).power;
  if (typeof input !== "boolean") return null;
  if (power !== undefined && typeof power !== "boolean") return null;
  return { input, power: power === true };
}

function makeHandler(listener: Listener) {
  return (req: IncomingMessage, res: ServerResponse): void => {
    handle(req, res, listener).catch((error: unknown) => {
      console.error("request failed:", error instanceof Error ? error.message : error);
      if (!res.headersSent) send(res, 500, { error: { code: "internal", message: "Request failed" } });
    });
  };
}

// The LAN listener is created on demand, so its Host policy follows the port it was started on.
const secureListener: Listener = { port: config.wifi.port, secure: true };
const secureHandler = makeHandler(secureListener);

const server = createServer(makeHandler({ port, secure: false }));

server.headersTimeout = 15_000;
server.requestTimeout = 30_000;
server.listen(port, "127.0.0.1", () => {
  console.log(`DeskDeck agent ${VERSION} on http://127.0.0.1:${port}`);
  console.log(`settings: http://127.0.0.1:${port}`);
  console.log(`config: ${process.env.DESKDECK_HOME ?? "%APPDATA%\\DeskDeck"}`);
  adb.start();
  if (config.wifi.enabled) {
    secureListener.port = config.wifi.port;
    void wifi.start(config.wifi.port, secureHandler);
  }
});

let lastUsb = "";
setInterval(() => {
  const snapshot = JSON.stringify(adb.state);
  if (snapshot === lastUsb) return;
  lastUsb = snapshot;
  const { adb: adbPath, devices: attached, reverse } = adb.state;
  if (reverse === "no-adb") console.log("usb: adb not found (pair a phone over USB to use it)");
  else console.log(`usb: adb=${adbPath} devices=${attached.join(",") || "none"} reverse=${reverse}`);
}, 2_000).unref();

function shutdown(signal: string): void {
  console.log(`${signal}: shutting down`);
  adb.stop();
  wifi.stop();
  runner.stop();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 3_000).unref();
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
