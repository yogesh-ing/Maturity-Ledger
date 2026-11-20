import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { configDir } from "./config.ts";

/**
 * A page is a 12x8 grid. The phone lays widgets out with CSS grid, so a widget is just a rectangle
 * plus a type; nothing in a layout can address the filesystem, run a command, or name a widget type
 * the agent does not render. Anything that fails validation is dropped, never coerced.
 */
export const GRID_COLUMNS = 12;
export const GRID_ROWS = 8;
export const MAX_PAGES = 8;
export const MAX_WIDGETS_PER_PAGE = 40;

/** Widget types this build can render. "photo" waits for a scoped media route (see docs/PLAN.md). */
export const WIDGET_TYPES = ["clock", "date", "system", "network", "nowplaying", "phone"] as const;
export type WidgetType = (typeof WIDGET_TYPES)[number];

export type Widget = {
  id: string;
  type: WidgetType;
  /** Grid cell of the top-left corner (0-based) and its size in cells. */
  x: number;
  y: number;
  w: number;
  h: number;
  /** Optional heading; the panel falls back to a name per type. */
  title: string | null;
};

export type Page = { id: string; title: string; widgets: Widget[] };
export type Layout = { version: number; updatedAt: number; pages: Page[] };

const WIDGET_ID = /^[a-z0-9_-]{1,32}$/;
const PAGE_ID = /^[a-z0-9_-]{1,24}$/;

function record(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function int(value: unknown, min: number, max: number, fallback: number): number {
  if (typeof value !== "number" || !Number.isFinite(value)) return fallback;
  return Math.min(max, Math.max(min, Math.round(value)));
}

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed.slice(0, max) : null;
}

function isWidgetType(value: unknown): value is WidgetType {
  return typeof value === "string" && (WIDGET_TYPES as readonly string[]).includes(value);
}

/** Returns null when the widget is unusable; the caller skips it. */
function widget(value: unknown, seen: Set<string>): Widget | null {
  const raw = record(value);
  if (!raw) return null;
  const id = typeof raw.id === "string" ? raw.id.trim().toLowerCase() : "";
  if (!WIDGET_ID.test(id) || seen.has(id)) return null;
  if (!isWidgetType(raw.type)) return null;

  // A rectangle that leaves the page is clipped rather than rejected: it still shows something.
  const x = int(raw.x, 0, GRID_COLUMNS - 1, 0);
  const y = int(raw.y, 0, GRID_ROWS - 1, 0);
  const w = int(raw.w, 1, GRID_COLUMNS - x, 2);
  const h = int(raw.h, 1, GRID_ROWS - y, 2);

  seen.add(id);
  return { id, type: raw.type, x, y, w, h, title: text(raw.title, 40) };
}

function page(value: unknown, index: number, pages: number): Page | null {
  const raw = record(value);
  if (!raw) return null;
  const rawId = typeof raw.id === "string" ? raw.id.trim().toLowerCase() : "";
  const id = PAGE_ID.test(rawId) ? rawId : `page${index + 1}`;
  if (pages > 1 && !Array.isArray(raw.widgets)) return null;

  const widgets: Widget[] = [];
  const seen = new Set<string>();
  if (Array.isArray(raw.widgets)) {
    for (const entry of raw.widgets.slice(0, MAX_WIDGETS_PER_PAGE)) {
      const parsed = widget(entry, seen);
      if (parsed) widgets.push(parsed);
      else console.warn(`layout: skipped widget ${JSON.stringify(entry).slice(0, 120)}`);
    }
  }
  return { id, title: text(raw.title, 40) ?? `Page ${index + 1}`, widgets };
}

export function emptyLayout(): Layout {
  return { version: 0, updatedAt: 0, pages: [] };
}

/**
 * The page every phone shows until layouts are editable (phase 4). The agent builds state for
 * exactly these widget ids, so the default doubles as the contract for the state stream.
 */
export function defaultLayout(): Layout {
  return {
    version: 2,
    updatedAt: 0,
    pages: [
      {
        id: "home",
        title: "Home",
        widgets: [
          { id: "clock", type: "clock", x: 0, y: 0, w: 7, h: 4, title: null },
          { id: "date", type: "date", x: 0, y: 4, w: 7, h: 2, title: null },
          { id: "now", type: "nowplaying", x: 0, y: 6, w: 7, h: 2, title: null },
          { id: "system", type: "system", x: 7, y: 0, w: 5, h: 3, title: "This PC" },
          { id: "network", type: "network", x: 7, y: 3, w: 5, h: 2, title: "Network" },
          { id: "phone", type: "phone", x: 7, y: 5, w: 5, h: 3, title: "Phone" },
        ],
      },
    ],
  };
}

/** Validates an untrusted layout and returns only what the panel can actually display. */
export function validateLayout(value: unknown): Layout | null {
  const raw = record(value);
  if (!raw || !Array.isArray(raw.pages)) return null;

  const pages: Page[] = [];
  const seen = new Set<string>();
  for (const [index, entry] of raw.pages.slice(0, MAX_PAGES).entries()) {
    const parsed = page(entry, index, raw.pages.length);
    if (!parsed) continue;
    if (seen.has(parsed.id)) parsed.id = `${parsed.id}_${index}`;
    seen.add(parsed.id);
    pages.push(parsed);
  }
  if (pages.length === 0) return null;

  return {
    version: int(raw.version, 0, 1_000_000, 0),
    updatedAt: int(raw.updatedAt, 0, Number.MAX_SAFE_INTEGER, 0),
    pages,
  };
}

/**
 * Layouts live beside the credentials so a hand-edited `panel.json` is picked up on the next start.
 * A file that does not validate is reported and ignored; the built-in page is used instead.
 */
export class LayoutStore {
  private current: Layout;
  private readonly path = join(configDir(), "panel.json");

  constructor() {
    this.current = this.read();
  }

  get(): Layout {
    return this.current;
  }

  private read(): Layout {
    try {
      if (!existsSync(this.path)) return defaultLayout();
      return validateLayout(JSON.parse(readFileSync(this.path, "utf8"))) ?? defaultLayout();
    } catch (error) {
      console.warn(`layout: ignoring ${this.path}: ${error instanceof Error ? error.message : error}`);
      return defaultLayout();
    }
  }

  /** Phase 4 pushes layouts from the editor; nothing writes here yet. */
  save(layout: Layout): void {
    const validated = validateLayout(layout);
    if (!validated) throw new Error("invalid layout");
    mkdirSync(configDir(), { recursive: true, mode: 0o700 });
    writeFileSync(this.path, JSON.stringify(validated, null, 2) + "\n", { encoding: "utf8", mode: 0o600 });
    this.current = validated;
  }
}
