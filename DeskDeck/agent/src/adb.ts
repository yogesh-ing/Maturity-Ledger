import { execFile } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { promisify } from "node:util";

const run = promisify(execFile);

export type UsbState = {
  adb: string | null;
  devices: string[];
  reverse: "ok" | "waiting" | "failed" | "no-adb";
};

const CHECK_INTERVAL_MS = 10_000;

function candidates(): string[] {
  const local = process.env.LOCALAPPDATA;
  const list = [
    process.env.DESKDECK_ADB,
    local ? join(local, "Android", "Sdk", "platform-tools", "adb.exe") : undefined,
    local ? join(local, "Android", "Sdk", "platform-tools", "adb") : undefined,
    "adb",
  ];
  return list.filter((value): value is string => typeof value === "string" && value.length > 0);
}

/**
 * Keeps `adb reverse tcp:<port> tcp:<port>` in place for every attached device, so the phone can
 * reach the loopback agent over the USB cable with no manual step. Runs forever on an interval:
 * replugging a cable must recover without restarting the agent.
 */
export class AdbReverse {
  readonly port: number;
  state: UsbState = { adb: null, devices: [], reverse: "no-adb" };
  private timer: NodeJS.Timeout | null = null;
  private busy = false;
  private resolved: string | null = null;

  constructor(port: number) {
    this.port = port;
  }

  start(): void {
    void this.tick();
    this.timer = setInterval(() => void this.tick(), CHECK_INTERVAL_MS);
    this.timer.unref();
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  /** Returns true when the state changed, so the caller can log transitions only. */
  private async tick(): Promise<boolean> {
    if (this.busy) return false;
    this.busy = true;
    try {
      const adb = await this.locate();
      if (!adb) {
        return this.update({ adb: null, devices: [], reverse: "no-adb" });
      }

      const { stdout } = await run(adb, ["devices"], { timeout: 8_000, windowsHide: true });
      const devices = stdout
        .split(/\r?\n/)
        .slice(1)
        .map((line) => line.trim().split(/\s+/))
        .filter((parts) => parts.length >= 2 && parts[1] === "device")
        .map((parts) => parts[0]!)
        .filter((serial) => serial.length > 0);

      if (devices.length === 0) {
        return this.update({ adb, devices, reverse: "waiting" });
      }

      let ok = 0;
      for (const serial of devices) {
        try {
          await run(adb, ["-s", serial, "reverse", `tcp:${this.port}`, `tcp:${this.port}`], {
            timeout: 8_000,
            windowsHide: true,
          });
          ok += 1;
        } catch {
          // A device that vanished mid-loop is picked up on the next tick.
        }
      }
      return this.update({ adb, devices, reverse: ok === devices.length ? "ok" : "failed" });
    } catch {
      this.resolved = null;
      return this.update({ adb: null, devices: [], reverse: "no-adb" });
    } finally {
      this.busy = false;
    }
  }

  private async locate(): Promise<string | null> {
    if (this.resolved && existsSync(this.resolved)) return this.resolved;
    for (const candidate of candidates()) {
      if (candidate === "adb") {
        try {
          await run("adb", ["version"], { timeout: 4_000, windowsHide: true });
          this.resolved = "adb";
          return "adb";
        } catch {
          continue;
        }
      }
      if (existsSync(candidate)) {
        this.resolved = candidate;
        return candidate;
      }
    }
    this.resolved = null;
    return null;
  }

  private update(next: UsbState): boolean {
    const changed = next.reverse !== this.state.reverse || next.devices.join(",") !== this.state.devices.join(",");
    this.state = next;
    return changed;
  }
}
