import type { IncomingMessage } from "node:http";

/**
 * Reads a JSON body, refusing anything larger than `limit` before buffering it.
 * Returns null for an empty body, a non-JSON body, a body over the limit, or a client abort:
 * every caller treats null as "bad request" instead of throwing into the request log.
 */
export async function readJson(req: IncomingMessage, limit: number): Promise<unknown> {
  const declared = Number(req.headers["content-length"] ?? 0);
  if (Number.isFinite(declared) && declared > limit) return null;

  const chunks: Buffer[] = [];
  let size = 0;
  try {
    for await (const chunk of req) {
      const buf = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk as string, "utf8");
      size += buf.length;
      if (size > limit) return null;
      chunks.push(buf);
    }
  } catch {
    return null;
  }

  if (size === 0) return null;
  const type = (req.headers["content-type"] ?? "").split(";", 1)[0]?.trim().toLowerCase();
  if (type !== "application/json") return null;

  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8")) as unknown;
  } catch {
    return null;
  }
}
