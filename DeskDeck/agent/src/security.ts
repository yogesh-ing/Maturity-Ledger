import { createHash, timingSafeEqual } from "node:crypto";
import type { IncomingMessage } from "node:http";

/** Constant-time comparison for equal-length secrets. */
export function safeEqual(a: string, b: string): boolean {
  const left = Buffer.from(a, "utf8");
  const right = Buffer.from(b, "utf8");
  if (left.length !== right.length) return false;
  return timingSafeEqual(left, right);
}

export function hashToken(token: string): string {
  return createHash("sha256").update(token, "utf8").digest("hex");
}

export function bearerToken(header: string | undefined): string | null {
  if (!header || !header.startsWith("Bearer ")) return null;
  const token = header.slice("Bearer ".length).trim();
  return token.length > 0 ? token : null;
}

/**
 * Blocks DNS rebinding: the loopback names are always accepted, and a listener may add the exact
 * `host:port` forms it is served on (the Wi-Fi listener adds its own addresses, nothing wildcarded).
 */
export function hostAllowed(
  host: string | undefined,
  port: number,
  extraHosts: readonly string[] = [],
): boolean {
  if (!host) return false;
  if (host === `127.0.0.1:${port}` || host === `localhost:${port}`) return true;
  return extraHosts.includes(host);
}

/** A browser on another origin must never reach the API; a native client sends no Origin. */
export function originAllowed(
  origin: string | undefined,
  port: number,
  extraOrigins: readonly string[] = [],
): boolean {
  if (!origin) return true;
  if (origin === `http://127.0.0.1:${port}` || origin === `http://localhost:${port}`) return true;
  return extraOrigins.includes(origin);
}

/**
 * True when the socket is the loopback interface. The phone arrives here through `adb reverse`,
 * so USB pairing is loopback-only until the Wi-Fi listener exists.
 */
export function remoteIsLoopback(req: IncomingMessage): boolean {
  const address = req.socket.remoteAddress ?? "";
  return address === "127.0.0.1" || address === "::1" || address === "::ffff:127.0.0.1";
}

/** Request paths for logs: no query string, no headers, bounded length. */
export function safeLogPath(url: string | undefined): string {
  if (!url) return "/";
  const path = url.split("?", 1)[0] ?? "/";
  return path.length > 120 ? path.slice(0, 120) + "…" : path;
}
