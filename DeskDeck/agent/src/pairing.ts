import { createHash, randomInt } from "node:crypto";
import { hostname } from "node:os";
import { safeEqual } from "./security.ts";

/** Crockford-style alphabet: no I, L, O or 0, so a code read off the PC cannot be mistyped. */
const ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
const TTL_MS = 5 * 60_000;
const MAX_ATTEMPTS = 5;

export type PairingView = {
  code: string;
  /** The payload a QR code will carry once the Wi-Fi listener exists; nothing secret but the code. */
  uri: string;
  /** Eight characters shown on both screens to confirm the same pairing. */
  check: string;
  expiresAt: number;
};

export type ClaimResult = "ok" | "no-session" | "expired" | "bad-code" | "too-many";

type Session = { code: string; expiresAt: number; attempts: number };

export class Pairing {
  private session: Session | null = null;
  private readonly localToken: string;
  private readonly port: number;

  constructor(localToken: string, port: number) {
    this.localToken = localToken;
    this.port = port;
  }

  /** Starting again always replaces the previous code: only one code is ever live. */
  start(): PairingView {
    let code = "";
    for (let i = 0; i < 8; i += 1) code += ALPHABET[randomInt(ALPHABET.length)];
    this.session = { code, expiresAt: Date.now() + TTL_MS, attempts: 0 };
    return this.view()!;
  }

  cancel(): void {
    this.session = null;
  }

  view(): PairingView | null {
    const session = this.live();
    if (!session) return null;
    const code = session.code;
    return {
      code: `${code.slice(0, 4)}-${code.slice(4)}`,
      uri: `deskdeck://pair?v=1&pc=${encodeURIComponent(hostname())}&port=${this.port}&c=${code}`,
      check: this.checkFor(code),
      expiresAt: session.expiresAt,
    };
  }

  /**
   * One use, five attempts, five minutes. Comparison is constant time and a rejection never says
   * which part was wrong.
   */
  claim(input: unknown): ClaimResult {
    const session = this.session;
    if (!session) return "no-session";
    if (Date.now() > session.expiresAt) {
      this.session = null;
      return "expired";
    }
    const code = typeof input === "string" ? input.toUpperCase().replace(/[^A-Z0-9]/g, "") : "";
    if (code.length !== session.code.length || !safeEqual(code, session.code)) {
      session.attempts += 1;
      if (session.attempts >= MAX_ATTEMPTS) this.session = null;
      return session.attempts >= MAX_ATTEMPTS ? "too-many" : "bad-code";
    }
    this.session = null;
    return "ok";
  }

  private live(): Session | null {
    if (!this.session) return null;
    if (Date.now() > this.session.expiresAt) {
      this.session = null;
      return null;
    }
    return this.session;
  }

  private checkFor(code: string): string {
    const digest = createHash("sha256").update(`${code}:${this.localToken}`, "utf8").digest("hex").toUpperCase();
    return `${digest.slice(0, 4)} ${digest.slice(4, 8)}`;
  }
}
