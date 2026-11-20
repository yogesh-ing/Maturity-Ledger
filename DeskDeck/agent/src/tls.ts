import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { createHash, X509Certificate } from "node:crypto";
import { networkInterfaces } from "node:os";
import { join } from "node:path";
import * as selfsigned from "selfsigned";
import { configDir } from "./config.ts";

/**
 * A locally generated identity for the Wi-Fi listener.
 *
 * Nothing trusts this certificate through a public CA: each phone pins the SHA-256 of the
 * SubjectPublicKeyInfo while it is paired over the trusted USB path, and refuses anything else.
 * That is why regenerating the key is a breaking change and never happens silently.
 */
export type TlsIdentity = {
  certPem: string;
  keyPem: string;
  /** Base64 SHA-256 of the SPKI, byte-for-byte what Android's `publicKey.encoded` hashes to. */
  pin: string;
};

/** Non-loopback IPv4 addresses of this machine, in interface order. */
export function lanAddresses(): string[] {
  const addresses: string[] = [];
  for (const entries of Object.values(networkInterfaces())) {
    for (const entry of entries ?? []) {
      if (entry.family !== "IPv4" || entry.internal) continue;
      addresses.push(entry.address);
    }
  }
  return addresses;
}

export function spkiPin(certPem: string): string | null {
  try {
    const cert = new X509Certificate(certPem);
    const der = cert.publicKey.export({ type: "spki", format: "der" });
    return createHash("sha256").update(der).digest("base64");
  } catch {
    return null;
  }
}

function writePrivate(path: string, contents: string): void {
  writeFileSync(path, contents, { encoding: "utf8", mode: 0o600 });
  if (process.platform !== "win32") chmodSync(path, 0o600);
}

/**
 * Reads the stored identity, or creates one on first use. A stored certificate that no longer
 * parses is replaced, because serving a broken certificate would leave the listener dead anyway.
 */
export function loadOrCreateIdentity(pcName: string): TlsIdentity | null {
  const dir = join(configDir(), "tls");
  const certPath = join(dir, "cert.pem");
  const keyPath = join(dir, "key.pem");

  try {
    mkdirSync(dir, { recursive: true, mode: 0o700 });
    if (existsSync(certPath) && existsSync(keyPath)) {
      const certPem = readFileSync(certPath, "utf8");
      const keyPem = readFileSync(keyPath, "utf8");
      const pin = spkiPin(certPem);
      if (pin !== null) return { certPem, keyPem, pin };
      console.warn("tls: stored certificate is unreadable, generating a new identity");
    }

    const altNames: Array<{ type: number; value?: string; ip?: string }> = [
      { type: 2, value: "localhost" },
      { type: 7, ip: "127.0.0.1" },
    ];
    if (pcName.length > 0) altNames.push({ type: 2, value: pcName });
    for (const address of lanAddresses()) altNames.push({ type: 7, ip: address });

    const created = selfsigned.generate(
      [{ name: "commonName", value: `DeskDeck ${pcName}`.slice(0, 60) }],
      {
        keySize: 2048,
        days: 3650,
        algorithm: "sha256",
        extensions: [
          { name: "basicConstraints", cA: false },
          { name: "keyUsage", digitalSignature: true, keyEncipherment: true },
          { name: "extKeyUsage", serverAuth: true },
          { name: "subjectAltName", altNames },
        ],
      },
    );

    const pin = spkiPin(created.cert);
    if (pin === null) throw new Error("generated certificate did not parse");

    writePrivate(certPath, created.cert);
    writePrivate(keyPath, created.private);
    console.log(`tls: generated identity pinned as ${pin.slice(0, 16)}…`);
    return { certPem: created.cert, keyPem: created.private, pin };
  } catch (error) {
    console.error("tls: cannot prepare an identity:", error instanceof Error ? error.message : error);
    return null;
  }
}
