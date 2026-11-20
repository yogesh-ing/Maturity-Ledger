import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { dirname, join } from "node:path";
import { createInterface } from "node:readline";
import { fileURLToPath } from "node:url";
import type { ActionCommand } from "./actions.ts";

const HELPER = join(dirname(fileURLToPath(import.meta.url)), "..", "helper", "winhelper.ps1");
const REPLY_TIMEOUT_MS = 2_500;
const IDLE_STOP_MS = 5 * 60_000;

export type RunResult = { ok: true } | { ok: false; code: string; message: string };

type HelperReply = { id?: unknown; ok?: unknown; error?: unknown };

/**
 * Runs the action vocabulary through `helper/winhelper.ps1`: a long-lived PowerShell process that
 * loads its P/Invoke block once and then answers one JSON line per command, so a button press costs
 * a few milliseconds instead of a PowerShell start-up.
 *
 * Nothing from the request reaches the command line: the helper reads `{id, cmd}` on stdin, checks
 * `cmd` against its own allowlist and logs anything else as rejected.
 */
export class ActionRunner {
  private child: ChildProcessWithoutNullStreams | null = null;
  private readonly waiting = new Map<number, (reply: HelperReply | null) => void>();
  private nextId = 1;
  private queue: Promise<unknown> = Promise.resolve();
  private idle: NodeJS.Timeout | null = null;

  run(command: ActionCommand): Promise<RunResult> {
    if (process.platform !== "win32") {
      return Promise.resolve({
        ok: false,
        code: "unsupported_platform",
        message: "Desktop actions run on the Windows agent only",
      });
    }
    // Serialised: a double tap must not interleave two commands on one stdin pipe.
    const job = this.queue.then(() => this.execute(command), () => this.execute(command));
    this.queue = job.then(() => undefined, () => undefined);
    return job;
  }

  /** Called on shutdown; an in-flight command is failed rather than left hanging. */
  stop(): void {
    this.clearIdle();
    const child = this.child;
    this.child = null;
    if (!child) return;
    try {
      child.stdin.end();
    } catch {
      // The pipe is already gone.
    }
    child.kill();
  }

  private execute(command: ActionCommand): Promise<RunResult> {
    const child = this.ensureHelper();
    if (!child) {
      return Promise.resolve({ ok: false, code: "helper_unavailable", message: "The input helper did not start" });
    }
    const id = this.nextId;
    this.nextId += 1;
    return new Promise<RunResult>((resolve) => {
      const timer = setTimeout(() => {
        this.waiting.delete(id);
        this.stop();
        resolve({ ok: false, code: "helper_timeout", message: "The input helper did not answer" });
      }, REPLY_TIMEOUT_MS);

      this.waiting.set(id, (reply) => {
        clearTimeout(timer);
        if (!reply) {
          resolve({ ok: false, code: "helper_failed", message: "The input helper stopped" });
          return;
        }
        if (reply.ok === true) {
          this.scheduleIdleStop();
          resolve({ ok: true });
          return;
        }
        const message = typeof reply.error === "string" ? reply.error : "The helper rejected the command";
        resolve({ ok: false, code: "action_failed", message });
      });

      try {
        child.stdin.write(`${JSON.stringify({ id, cmd: command })}\n`);
      } catch {
        this.waiting.delete(id);
        clearTimeout(timer);
        resolve({ ok: false, code: "helper_unavailable", message: "The input helper is not accepting commands" });
      }
    });
  }

  private ensureHelper(): ChildProcessWithoutNullStreams | null {
    if (this.child) return this.child;
    let child: ChildProcessWithoutNullStreams;
    try {
      child = spawn(
        "powershell.exe",
        ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", HELPER],
        { windowsHide: true, stdio: ["pipe", "pipe", "pipe"] },
      );
    } catch (error) {
      console.error("helper: cannot start:", error instanceof Error ? error.message : error);
      return null;
    }
    this.child = child;

    const lines = createInterface({ input: child.stdout });
    lines.on("line", (line) => {
      const trimmed = line.trim();
      if (!trimmed.startsWith("{")) return; // start-up chatter is not a reply
      let reply: HelperReply;
      try {
        reply = JSON.parse(trimmed) as HelperReply;
      } catch {
        return;
      }
      if (typeof reply.id !== "number") return;
      const waiter = this.waiting.get(reply.id);
      this.waiting.delete(reply.id);
      waiter?.(reply);
    });

    child.stderr.on("data", (chunk: Buffer) => {
      const text = chunk.toString("utf8").trim();
      if (text) console.error(`helper: ${text}`);
    });
    child.on("error", (error) => {
      console.error("helper:", error.message);
      this.failAll(child);
    });
    child.on("exit", (code) => {
      if (this.child === child) console.error(`helper: exited (${code === null ? "signal" : code})`);
      this.failAll(child);
    });

    return child;
  }

  private failAll(child: ChildProcessWithoutNullStreams): void {
    if (this.child === child) this.child = null;
    this.clearIdle();
    const waiters = [...this.waiting.values()];
    this.waiting.clear();
    for (const waiter of waiters) waiter(null);
  }

  private scheduleIdleStop(): void {
    this.clearIdle();
    this.idle = setTimeout(() => this.stop(), IDLE_STOP_MS);
    this.idle.unref();
  }

  private clearIdle(): void {
    if (!this.idle) return;
    clearTimeout(this.idle);
    this.idle = null;
  }
}
