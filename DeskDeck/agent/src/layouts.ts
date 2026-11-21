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
/** How many previous versions the editor can roll back to without touching `panel.json` by hand. */
export const MAX_HISTORY = 10;

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

export const DENSITIES = ["compact", "comfortable", "roomy"] as const;
export type Density = (typeof DENSITIES)[number];

export const BACKGROUNDS = ["dark", "darker", "black"] as const;
export type Background = (typeof BACKGROUNDS)[number];

/**
 * Themes are three closed choices plus one colour. No CSS, no URL, no font name: whatever the editor
 * sends lands in a style variable the panel already owns, so a layout cannot restyle the phone.
 */
export type Theme = { accent: string; density: Density; background: Background };

export const DEFAULT_THEME: Theme = { accent: "#4fd1c5", density: "comfortable", background: "darker" };

export type Layout = {
  /**
   * Server-assigned revision, incremented on every accepted push. It is the optimistic-concurrency
   * token: the editor sends the version it edited, and a stale save is refused instead of
   * overwriting a newer layout.
   */
  version: number;
  updatedAt: number;
  theme: Theme;
  pages: Page[];
};

export type HistoryEntry = { version: number; updatedAt: number; pages: number; theme: Theme };

/** Why a push was refused. `invalid` and `not_found` are the caller's mistake; `conflict` is a race. */
export type PushFailure = "invalid" | "conflict" | "not_found";

export type PushResult =
  | { ok: true; layout: Layout }
  | { ok: false; code: PushFailure; current: Layout };

const WIDGET_ID = /^[a-z0-9_-]{1,32}$/;
const PAGE_ID = /^[a-z0-9_-]{1,24}$/;
const ACCENT = /^#[0-9a-f]{6}$/i;

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

/** Unknown density or background falls back to the default rather than rejecting the layout. */
export function validateTheme(value: unknown): Theme {
  const raw = record(value);
  if (!raw) return { ...DEFAULT_THEME };
  const accent = typeof raw.accent === "string" && ACCENT.test(raw.accent.trim())
    ? raw.accent.trim().toLowerCase()
    : DEFAULT_THEME.accent;
  const density = (DENSITIES as readonly unknown[]).includes(raw.density)
    ? (raw.density as Density)
    : DEFAULT_THEME.density;
  const background = (BACKGROUNDS as readonly unknown[]).includes(raw.background)
    ? (raw.background as Background)
    : DEFAULT_THEME.background;
  return { accent, density, background };
}

/**
 * Returns null when the widget is unusable; the caller skips it. `seen` is shared by every page so a
 * widget id is unique across the whole layout: state is keyed by id, and a duplicate would make one
 * page paint another page's numbers.
 */
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

function page(value: unknown, index: number, seen: Set<string>): Page | null {
  const raw = record(value);
  if (!raw) return null;
  const rawId = typeof raw.id === "string" ? raw.id.trim().toLowerCase() : "";
  const id = PAGE_ID.test(rawId) ? rawId : `page${index + 1}`;
  // `widgets` must be present even when empty: a page without the array is a malformed layout, not
  // a blank page, and the editor always writes it.
  if (!Array.isArray(raw.widgets)) return null;

  const widgets: Widget[] = [];
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
  return { version: 0, updatedAt: 0, theme: { ...DEFAULT_THEME }, pages: [] };
}

/**
 * The page every phone shows until a layout is pushed. The agent builds state for exactly these
 * widget ids, so the default doubles as the contract for the state stream.
 */
export function defaultLayout(): Layout {
  return {
    version: 2,
    updatedAt: 0,
    theme: { ...DEFAULT_THEME },
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
  const pageIds = new Set<string>();
  const widgetIds = new Set<string>();
  for (const [index, entry] of raw.pages.slice(0, MAX_PAGES).entries()) {
    const parsed = page(entry, index, widgetIds);
    if (!parsed) continue;
    if (pageIds.has(parsed.id)) parsed.id = `${parsed.id}_${index}`;
    pageIds.add(parsed.id);
    pages.push(parsed);
  }
  if (pages.length === 0) return null;

  return {
    version: int(raw.version, 0, 1_000_000, 0),
    updatedAt: int(raw.updatedAt, 0, Number.MAX_SAFE_INTEGER, 0),
    theme: validateTheme(raw.theme),
    pages,
  };
}

/** The page a phone should open on: its own assignment, else the first page of the layout. */
export function pageIdFor(layout: Layout, wanted: string | null | undefined): string {
  if (wanted) {
    const match = layout.pages.find((page) => page.id === wanted);
    if (match) return match.id;
  }
  return layout.pages[0]?.id ?? "";
}

/**
 * Layouts live beside the credentials so a hand-edited `panel.json` is picked up on the next start.
 * A file that does not validate is reported and ignored; the built-in page is used instead.
 *
 * Writes go through `push`, which assigns the revision itself. That is the whole concurrency story:
 * an editor that saved against version 7 cannot silently erase a version 8 written by someone else,
 * it gets `conflict` and the current layout back.
 */
export class LayoutStore {
  private current: Layout;
  private snapshots: Layout[] = [];
  private lastError: string | null = null;
  private readonly path = join(configDir(), "panel.json");

  constructor() {
    this.current = this.read();
  }

  get(): Layout {
    return this.current;
  }

  /** Strong-looking, weak-in-practice ETag: the revision is all a client needs to compare. */
  etag(): string {
    return `"v${this.current.version}"`;
  }

  /** True when `panel.json` could not be read, so the settings page can say why defaults are shown. */
  get error(): string | null {
    return this.lastError;
  }

  history(): HistoryEntry[] {
    return this.snapshots.map((snapshot) => ({
      version: snapshot.version,
      updatedAt: snapshot.updatedAt,
      pages: snapshot.pages.length,
      theme: snapshot.theme,
    }));
  }

  /**
   * Validates, checks the caller's revision, then writes. `ifMatch` is the ETag the editor loaded
   * (`"v7"`), `*` to overwrite unconditionally, or null to skip the check entirely (rollback).
   */
  push(value: unknown, ifMatch: string | null): PushResult {
    const validated = validateLayout(value);
    if (!validated) return { ok: false, code: "invalid", current: this.current };
    if (ifMatch !== null && ifMatch !== "*" && ifMatch !== this.etag()) {
      return { ok: false, code: "conflict", current: this.current };
    }

    const next: Layout = { ...validated, version: this.current.version + 1, updatedAt: Date.now() };
    this.snapshots.unshift(this.current);
    this.snapshots = this.snapshots.slice(0, MAX_HISTORY);
    this.persist(next);
    return { ok: true, layout: next };
  }

  /** Restores an earlier revision as a new revision, so a rollback is itself undoable. */
  rollback(version: number): PushResult {
    const snapshot = this.snapshots.find((entry) => entry.version === version);
    if (!snapshot) return { ok: false, code: "not_found", current: this.current };
    return this.push(snapshot, null);
  }

  /** Reads `panel.json` again, for a file edited by hand while the agent is running. */
  reload(): Layout {
    this.current = this.read();
    return this.current;
  }

  private read(): Layout {
    try {
      this.lastError = null;
      if (!existsSync(this.path)) return defaultLayout();
      const parsed = validateLayout(JSON.parse(readFileSync(this.path, "utf8")));
      if (!parsed) {
        this.lastError = "panel.json did not validate; the built-in page is being served";
        return defaultLayout();
      }
      return parsed;
    } catch (error) {
      this.lastError = `${error instanceof Error ? error.message : error}`;
      console.warn(`layout: ignoring ${this.path}: ${this.lastError}`);
      return defaultLayout();
    }
  }

  private persist(layout: Layout): void {
    mkdirSync(configDir(), { recursive: true, mode: 0o700 });
    writeFileSync(this.path, JSON.stringify(layout, null, 2) + "\n", { encoding: "utf8", mode: 0o600 });
    this.current = layout;
  }
}
