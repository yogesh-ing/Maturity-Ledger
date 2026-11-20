import { createServer, type Server } from "node:https";
import type { RequestListener } from "node:http";
import { lanAddresses, loadOrCreateIdentity } from "./tls.ts";

export type WifiState = {
  enabled: boolean;
  port: number;
  /** Every address the listener can be reached on right now. Empty while it is off. */
  urls: string[];
  /** The pin a phone must hold to talk to this listener. Null when no identity exists yet. */
  pin: string | null;
  error: string | null;
};

/**
 * The same request handler the loopback listener uses, served over TLS on the LAN.
 *
 * It is off by default: USB needs no open port, and this listener is the only one exposed to the
 * network. Its certificate is self-signed and pinned by paired phones, so there is no public CA
 * trust to inherit and a hostname mismatch is expected (the phone connects by address).
 */
export class WifiServer {
  private server: Server | null = null;
  private identityPin: string | null = null;
  private activePort = 0;
  private failure: string | null = null;
  private readonly pcName: string;

  constructor(pcName: string) {
    this.pcName = pcName;
  }

  get port(): number {
    return this.activePort;
  }

  /** Resolves true once the socket is listening. A failed bind leaves the listener off. */
  async start(port: number, handler: RequestListener): Promise<boolean> {
    if (this.server) return true;
    const identity = loadOrCreateIdentity(this.pcName);
    if (!identity) {
      this.failure = "No TLS identity: the certificate could not be created";
      return false;
    }

    const server = createServer(
      { cert: identity.certPem, key: identity.keyPem, minVersion: "TLSv1.2" },
      handler,
    );
    server.headersTimeout = 15_000;
    server.requestTimeout = 30_000;

    this.identityPin = identity.pin;
    this.failure = null;

    const listening = await new Promise<boolean>((resolve) => {
      const onError = (error: Error): void => {
        server.off("listening", onListening);
        this.failure = error.message.slice(0, 160);
        try {
          server.close();
        } catch {
          /* already closed */
        }
        resolve(false);
      };
      const onListening = (): void => {
        server.off("error", onError);
        resolve(true);
      };
      server.once("error", onError);
      server.once("listening", onListening);
      try {
        server.listen(port, "0.0.0.0");
      } catch (error) {
        onError(error instanceof Error ? error : new Error("listen failed"));
      }
    });

    if (!listening) {
      console.error(`wifi: cannot listen on port ${port}: ${this.failure ?? "unknown error"}`);
      return false;
    }

    // Keep the process alive through later socket errors instead of crashing on an unhandled one.
    server.on("error", (error) => console.error("wifi: server error:", error.message));
    this.server = server;
    this.activePort = port;
    console.log(`wifi: listening on https://0.0.0.0:${port}`);
    return true;
  }

  stop(): void {
    const server = this.server;
    this.server = null;
    this.activePort = 0;
    if (!server) return;
    server.close(() => undefined);
    server.closeAllConnections?.();
    console.log("wifi: listener stopped");
  }

  state(): WifiState {
    const enabled = this.server !== null;
    return {
      enabled,
      port: this.activePort,
      urls: enabled
        ? lanAddresses().map((address) => `https://${address}:${this.activePort}`)
        : [],
      pin: this.identityPin,
      error: this.failure,
    };
  }
}
