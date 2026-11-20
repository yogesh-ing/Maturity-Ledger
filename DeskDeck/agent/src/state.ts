import { execFile } from "node:child_process";
import { statfsSync } from "node:fs";
import { cpus, freemem, networkInterfaces, totalmem } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import type { LayoutStore, Widget } from "./layouts.ts";

const MEDIA_SCRIPT = join(dirname(fileURLToPath(import.meta.url)), "ps", "nowplaying.ps1");
const MEDIA_REFRESH_MS = 5_000;
const NETWORK_REFRESH_MS = 5_000;
const MEDIA_TIMEOUT_MS = 4_000;
const PHONE_STALE_MS = 5 * 60_000;
const TICK_MS = 2_000;

export type SystemState = { cpu: number; memUsed: number; memTotal: number; diskUsed: number; diskTotal: number };
export type NetworkState = { name: string; ipv4: string; down: number; up: number };
export type NowPlayingState = { playing: boolean; status: string; app: string; title: string; artist: string };
export type PhoneState = { level: number; charging: boolean; updatedAt: number };

export type CoreState = {
  system: SystemState | null;
  network: NetworkState | null;
  nowplaying: NowPlayingState | null;
};

/**
 * Machine state is sampled at most once per source, not once per subscriber: CPU/disk are cheap
 * synchronous reads, while network counters and the media session each spawn a process often
 * enough to matter and are therefore refreshed on a timer with at most one call in flight.
 */
class Collectors {
  private previousCpu = cpus();
  private network: NetworkState | null = null;
  private networkAt = 0;
  private networkBusy = false;
  private readonly networkRates = { down: 0, up: 0, bytes: null as { down: number; up: number; at: number } | null };
  private media: NowPlayingState | null = null;
  private mediaAt = 0;
  private mediaBusy = false;

  collect(): CoreState {
    this.refreshNetwork();
    this.refreshMedia();
    return {
      system: { cpu: this.cpu(), ...memory(), ...disk() },
      network: this.network,
      nowplaying: this.media,
    };
  }

  /** Busy share across all cores since the previous call; the first call after start reads 0. */
  private cpu(): number {
    const current = cpus();
    let busy = 0;
    let total = 0;
    for (let index = 0; index < current.length; index += 1) {
      const now = current[index];
      const before = this.previousCpu[index];
      if (!now || !before) continue;
      const time = now.times;
      const past = before.times;
      const idle = time.idle - past.idle;
      const elapsed = time.user + time.nice + time.sys + time.idle + time.irq
        - (past.user + past.nice + past.sys + past.idle + past.irq);
      if (elapsed <= 0) continue;
      busy += elapsed - idle;
      total += elapsed;
    }
    this.previousCpu = current;
    return total > 0 ? Math.min(100, Math.max(0, Math.round((busy / total) * 100))) : 0;
  }

  private refreshNetwork(): void {
    const now = Date.now();
    if (now - this.networkAt < NETWORK_REFRESH_MS) return;
    this.networkAt = now;

    const link = primaryInterface();
    if (process.platform !== "win32") {
      this.network = link ? { ...link, down: 0, up: 0 } : null;
      return;
    }
    if (this.networkBusy) return;
    this.networkBusy = true;
    execFile("netstat", ["-e"], { timeout: 3_000, windowsHide: true }, (error, stdout) => {
      this.networkBusy = false;
      if (error) return;
      const totals = parseNetstat(stdout);
      if (!totals) return;

      const previous = this.networkRates.bytes;
      if (previous) {
        const seconds = (now - previous.at) / 1000;
        if (seconds > 0.5) {
          this.networkRates.down = Math.max(0, Math.round((totals.down - previous.down) / seconds));
          this.networkRates.up = Math.max(0, Math.round((totals.up - previous.up) / seconds));
        }
      }
      this.networkRates.bytes = { ...totals, at: now };
      const current = primaryInterface();
      this.network = current
        ? { ...current, down: this.networkRates.down, up: this.networkRates.up }
        : null;
    });
  }

  private refreshMedia(): void {
    const now = Date.now();
    if (now - this.mediaAt < MEDIA_REFRESH_MS) return;
    this.mediaAt = now;
    if (this.mediaBusy) return;
    if (process.platform !== "win32") {
      this.media = null;
      return;
    }

    this.mediaBusy = true;
    execFile(
      "powershell.exe",
      ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", MEDIA_SCRIPT],
      { timeout: MEDIA_TIMEOUT_MS, windowsHide: true, maxBuffer: 16 * 1024 },
      (_error, stdout) => {
        this.mediaBusy = false;
        this.media = parseMedia(stdout);
      },
    );
  }
}

function memory(): { memUsed: number; memTotal: number } {
  const memTotal = totalmem();
  return { memUsed: Math.max(0, memTotal - freemem()), memTotal };
}

function disk(): { diskUsed: number; diskTotal: number } {
  try {
    const root = process.platform === "win32" ? `${process.env.SystemDrive ?? "C:"}\\` : "/";
    const stats = statfsSync(root);
    const diskTotal = stats.blocks * stats.bsize;
    const diskFree = stats.bavail * stats.bsize;
    return { diskUsed: Math.max(0, diskTotal - diskFree), diskTotal };
  } catch {
    return { diskUsed: 0, diskTotal: 0 };
  }
}

function primaryInterface(): { name: string; ipv4: string } | null {
  for (const [name, addresses] of Object.entries(networkInterfaces())) {
    if (!addresses) continue;
    for (const address of addresses) {
      if (address.family === "IPv4" && !address.internal) return { name, ipv4: address.address };
    }
  }
  return null;
}

/**
 * `netstat -e` prints its byte totals first. The row labels are localised but the numbers are not,
 * so the parser looks for the first two-column numeric row instead of matching the word "Bytes".
 */
export function parseNetstat(output: string): { down: number; up: number } | null {
  for (const line of output.split(/\r?\n/)) {
    const match = /^\s*[^\d\s][^\s]*\s+(\d+)\s+(\d+)\s*$/.exec(line);
    if (!match) continue;
    const down = Number(match[1]);
    const up = Number(match[2]);
    if (Number.isSafeInteger(down) && Number.isSafeInteger(up)) return { down, up };
  }
  return null;
}

function parseMedia(stdout: string): NowPlayingState | null {
  if (!stdout.trim()) return null;
  let value: unknown;
  try {
    value = JSON.parse(stdout.trim());
  } catch {
    return null;
  }
  if (typeof value !== "object" || value === null) return null;
  const raw = value as Record<string, unknown>;
  if (raw.playing !== true) return null;
  const string = (key: string): string => (typeof raw[key] === "string" ? (raw[key] as string).slice(0, 120) : "");
  const title = string("title");
  if (!title) return null;
  return {
    playing: true,
    status: string("status") || "playing",
    app: string("app"),
    title,
    artist: string("artist"),
  };
}

/** Battery reports the phone sends about itself; keyed by the device id the token resolved to. */
class PhoneStatusStore {
  private readonly phones = new Map<string, PhoneState>();

  report(deviceId: string, level: number, charging: boolean): void {
    this.phones.set(deviceId, {
      level: Math.min(100, Math.max(0, Math.round(level))),
      charging,
      updatedAt: Date.now(),
    });
  }

  get(deviceId: string | null): PhoneState | null {
    if (!deviceId) return null;
    const state = this.phones.get(deviceId);
    if (!state) return null;
    if (Date.now() - state.updatedAt > PHONE_STALE_MS) {
      this.phones.delete(deviceId);
      return null;
    }
    return state;
  }
}

/** A widget's payload, or null when the phone renders that widget from its own clock. */
function payloadFor(
  widget: Widget | undefined,
  core: CoreState,
  phone: PhoneState | null,
): unknown | null {
  if (!widget) return null;
  switch (widget.type) {
    case "clock":
    case "date":
      return null;
    case "system":
      return core.system;
    case "network":
      return core.network;
    case "nowplaying":
      return core.nowplaying;
    case "phone":
      return phone;
  }
}

type Subscriber = {
  /** The device the token resolved to, so a phone widget never shows another phone's battery. */
  deviceId: string | null;
  ids: ReadonlySet<string>;
  send: (event: string, data: unknown) => void;
};

type Live = { subscriber: Subscriber; last: Map<string, string> };

/**
 * Pushes only widget payloads that changed, to subscribers that asked for those widget ids. With no
 * subscribers the tick timer is stopped, so an idle agent samples nothing.
 */
export class StateHub {
  private readonly collectors = new Collectors();
  readonly phones = new PhoneStatusStore();
  private readonly layouts: LayoutStore;
  private readonly live = new Set<Live>();
  private timer: NodeJS.Timeout | null = null;
  private seq = 0;

  constructor(layouts: LayoutStore) {
    this.layouts = layouts;
  }

  subscribe(subscriber: Subscriber): () => void {
    const initial = this.build(subscriber.deviceId, subscriber.ids);
    const last = new Map<string, string>();
    for (const [id, payload] of Object.entries(initial.widgets)) last.set(id, JSON.stringify(payload));
    subscriber.send("state", initial);

    const entry: Live = { subscriber, last };
    this.live.add(entry);
    this.start();
    return () => {
      this.live.delete(entry);
      if (this.live.size === 0) this.stop();
    };
  }

  /** One-off snapshot for `GET /api/state`. */
  snapshot(deviceId: string | null, ids: ReadonlySet<string>): unknown {
    return this.build(deviceId, ids);
  }

  private start(): void {
    if (this.timer) return;
    this.timer = setInterval(() => this.tick(), TICK_MS);
    this.timer.unref();
  }

  private stop(): void {
    if (!this.timer) return;
    clearInterval(this.timer);
    this.timer = null;
  }

  private tick(): void {
    if (this.live.size === 0) return;
    const core = this.collectors.collect();
    const widgets = this.widgetsById();

    for (const entry of this.live) {
      const changed: Record<string, unknown> = {};
      let any = false;
      for (const id of entry.subscriber.ids) {
        const payload = payloadFor(widgets.get(id), core, this.phones.get(entry.subscriber.deviceId));
        if (payload === null) continue;
        const json = JSON.stringify(payload);
        if (entry.last.get(id) === json) continue;
        entry.last.set(id, json);
        changed[id] = payload;
        any = true;
      }
      if (!any) continue;
      this.seq += 1;
      entry.subscriber.send("state", { seq: this.seq, t: Date.now(), widgets: changed });
    }
  }

  private build(deviceId: string | null, ids: ReadonlySet<string>): { seq: number; t: number; widgets: Record<string, unknown> } {
    const core = this.collectors.collect();
    const widgets = this.widgetsById();
    const payloads: Record<string, unknown> = {};
    for (const id of ids) {
      const payload = payloadFor(widgets.get(id), core, this.phones.get(deviceId));
      if (payload !== null) payloads[id] = payload;
    }
    return { seq: this.seq, t: Date.now(), widgets: payloads };
  }

  private widgetsById(): Map<string, Widget> {
    const map = new Map<string, Widget>();
    for (const page of this.layouts.get().pages) {
      for (const widget of page.widgets) map.set(widget.id, widget);
    }
    return map;
  }
}

/** Every widget id a layout defines, for a stream that subscribes to the whole page. */
export function layoutWidgetIds(layouts: LayoutStore): Set<string> {
  const ids = new Set<string>();
  for (const page of layouts.get().pages) {
    for (const widget of page.widgets) ids.add(widget.id);
  }
  return ids;
}
