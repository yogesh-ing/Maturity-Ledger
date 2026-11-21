import { randomBytes } from "node:crypto";
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

export type Capability = "read" | "input" | "power";

export type DeviceRecord = {
  /** Opaque id chosen by the agent at pairing, never by the client. */
  id: string;
  name: string;
  model: string;
  capabilities: Capability[];
  /** SHA-256 of the device token; the token itself is only ever sent to the phone once. */
  tokenHash: string;
  pairedAt: number;
  lastSeen: number | null;
};

export type WifiConfig = {
  /** Off by default: the USB path needs no open port. */
  enabled: boolean;
  port: number;
};

export type AgentConfig = {
  version: 1;
  port: number;
  /** Token for local tooling and the settings page; devices get their own. */
  localToken: string;
  devices: DeviceRecord[];
  wifi: WifiConfig;
  /**
   * Which panel page each phone opens on, by device id. Absent means the layout's first page, so a
   * layout edit never strands a phone on a page that no longer exists.
   */
  devicePages: Record<string, string>;
};

export const DEFAULT_WIFI_PORT = 8742;

export function configDir(): string {
  const base = process.env.DESKDECK_HOME
    ?? join(process.env.APPDATA ?? join(homedir(), "AppData", "Roaming"), "DeskDeck");
  return base;
}

function configPath(): string {
  return join(configDir(), "agent.json");
}

/**
 * Writes a file only the current user should read. Windows ignores the mode: profile ACLs apply,
 * and phase 5 replaces this with an explicit owner-only DACL (see docs/SECURITY.md rule 11).
 */
function writePrivate(path: string, contents: string): void {
  writeFileSync(path, contents, { encoding: "utf8", mode: 0o600 });
  if (process.platform !== "win32") chmodSync(path, 0o600);
}

export function newToken(): string {
  return randomBytes(32).toString("base64url");
}

function defaults(): AgentConfig {
  return {
    version: 1,
    port: 8741,
    localToken: newToken(),
    devices: [],
    wifi: { enabled: false, port: DEFAULT_WIFI_PORT },
    devicePages: {},
  };
}

const PAGE_ID = /^[a-z0-9_-]{1,24}$/;

/** Assignments are advisory: an unknown page id is ignored when the phone asks for its layout. */
function normalizePages(value: unknown): Record<string, string> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return {};
  const pages: Record<string, string> = {};
  for (const [device, page] of Object.entries(value as Record<string, unknown>)) {
    if (typeof page !== "string" || !PAGE_ID.test(page)) continue;
    if (!device.startsWith("dev_") || device.length > 40) continue;
    pages[device] = page;
  }
  return pages;
}

function normalizeDevice(value: Partial<DeviceRecord>): DeviceRecord | null {
  if (typeof value.id !== "string" || typeof value.tokenHash !== "string") return null;
  const capabilities = Array.isArray(value.capabilities)
    ? value.capabilities.filter((c): c is Capability => c === "read" || c === "input" || c === "power")
    : [];
  return {
    id: value.id.slice(0, 40),
    name: typeof value.name === "string" ? value.name.slice(0, 60) : "Phone",
    model: typeof value.model === "string" ? value.model.slice(0, 60) : "",
    capabilities: capabilities.length > 0 ? capabilities : ["read"],
    tokenHash: value.tokenHash,
    pairedAt: typeof value.pairedAt === "number" ? value.pairedAt : Date.now(),
    lastSeen: typeof value.lastSeen === "number" ? value.lastSeen : null,
  };
}

export function loadConfig(): AgentConfig {
  mkdirSync(configDir(), { recursive: true, mode: 0o700 });
  const path = configPath();

  if (!existsSync(path)) {
    const fresh = defaults();
    writePrivate(path, JSON.stringify(fresh, null, 2) + "\n");
    return fresh;
  }

  const parsed = JSON.parse(readFileSync(path, "utf8")) as Partial<AgentConfig>;
  // A hand-edited file must not take the agent down or silently disable auth.
  const config: AgentConfig = {
    version: 1,
    port: Number.isInteger(parsed.port) ? parsed.port! : 8741,
    localToken: typeof parsed.localToken === "string" && parsed.localToken.length >= 32
      ? parsed.localToken
      : newToken(),
    devices: Array.isArray(parsed.devices)
      ? parsed.devices.map(normalizeDevice).filter((d): d is DeviceRecord => d !== null)
      : [],
    wifi: {
      enabled: parsed.wifi?.enabled === true,
      port: Number.isInteger(parsed.wifi?.port) ? parsed.wifi!.port! : DEFAULT_WIFI_PORT,
    },
    devicePages: normalizePages(parsed.devicePages),
  };
  if (config.localToken !== parsed.localToken) writePrivate(path, JSON.stringify(config, null, 2) + "\n");
  return config;
}

export function saveConfig(config: AgentConfig): void {
  writePrivate(configPath(), JSON.stringify(config, null, 2) + "\n");
}
