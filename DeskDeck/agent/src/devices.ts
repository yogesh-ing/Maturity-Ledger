import { randomBytes } from "node:crypto";
import type { AgentConfig, Capability, DeviceRecord } from "./config.ts";
import { newToken } from "./config.ts";
import { hashToken, safeEqual } from "./security.ts";

export type DeviceView = {
  id: string;
  name: string;
  model: string;
  capabilities: Capability[];
  pairedAt: number;
  lastSeen: number | null;
};

const LAST_SEEN_PERSIST_MS = 60_000;

/**
 * The phone registry. A device's identity comes from the token it presents, never from a request
 * header, so a holder of one phone's token cannot act as another phone.
 */
export class DeviceStore {
  private readonly config: AgentConfig;
  private readonly persist: () => void;
  private lastPersist = 0;

  constructor(config: AgentConfig, persist: () => void) {
    this.config = config;
    this.persist = persist;
  }

  issue(input: { model: string }): { record: DeviceRecord; token: string } {
    const token = newToken();
    const record: DeviceRecord = {
      id: `dev_${randomBytes(6).toString("hex")}`,
      name: describe(input.model),
      model: input.model.slice(0, 60),
      // Phase 3 adds "input"/"power"; a fresh phone is read-only until the PC grants more.
      capabilities: ["read"],
      tokenHash: hashToken(token),
      pairedAt: Date.now(),
      lastSeen: null,
    };
    this.config.devices.push(record);
    this.persist();
    return { record, token };
  }

  authenticate(token: string): DeviceRecord | null {
    const hash = hashToken(token);
    for (const device of this.config.devices) {
      if (safeEqual(device.tokenHash, hash)) return device;
    }
    return null;
  }

  /**
   * Replaces a phone's credential in place, keeping its id, name and capabilities. Only the phone
   * can ask for this: it holds the current token, so there is no window where the PC mails a secret
   * to nobody.
   */
  rotate(record: DeviceRecord): string {
    const token = newToken();
    record.tokenHash = hashToken(token);
    this.persist();
    return token;
  }

  /**
   * Grants or withdraws control for one registered phone. Pairing always issues `read`; control is
   * a separate, PC-side decision, so a phone cannot promote itself.
   */
  setCapabilities(id: string, wanted: { input: boolean; power: boolean }): DeviceRecord | null {
    const record = this.config.devices.find((device) => device.id === id);
    if (!record) return null;
    const capabilities: Capability[] = ["read"];
    if (wanted.input) capabilities.push("input");
    if (wanted.power) capabilities.push("power");
    record.capabilities = capabilities;
    this.persist();
    return record;
  }

  touch(record: DeviceRecord): void {
    record.lastSeen = Date.now();
    if (Date.now() - this.lastPersist > LAST_SEEN_PERSIST_MS) {
      this.lastPersist = Date.now();
      this.persist();
    }
  }

  remove(id: string): boolean {
    const index = this.config.devices.findIndex((device) => device.id === id);
    if (index < 0) return false;
    this.config.devices.splice(index, 1);
    this.persist();
    return true;
  }

  forget(record: DeviceRecord): void {
    this.remove(record.id);
  }

  list(): DeviceView[] {
    return this.config.devices.map((device) => ({
      id: device.id,
      name: device.name,
      model: device.model,
      capabilities: [...device.capabilities],
      pairedAt: device.pairedAt,
      lastSeen: device.lastSeen,
    }));
  }
}

function describe(model: string): string {
  const trimmed = model.trim();
  return trimmed.length > 0 ? trimmed.slice(0, 60) : "Android phone";
}
