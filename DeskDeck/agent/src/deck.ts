import { existsSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { configDir, type Capability } from "./config.ts";
import { actionFor, normalizeKeys, type ActionSpec } from "./actions.ts";

/**
 * User-defined buttons, loaded from `%APPDATA%\DeskDeck\deck.json` next to `agent.json`.
 *
 * This file is the allowlist for the two kinds that reach outside the media keys: `launch` may start
 * exactly the program or URL written here, and `keys` may press exactly one validated combination.
 * A phone only ever names the id, so editing this file is the only way to add a capability.
 *
 * The file is re-read when its mtime changes, so an edit takes effect on the next press without
 * restarting the agent; a broken entry is dropped and reported on the settings page instead of
 * taking the deck down.
 */
export type DeckEntry = { id: string; spec: ActionSpec };

export type DeckView = {
  file: string;
  buttons: Array<{
    id: string;
    label: string;
    icon: string;
    kind: string;
    capability: Capability;
    confirm: boolean;
  }>;
  errors: string[];
};

const MAX_BUTTONS = 24;
const MAX_LABEL = 24;
const MAX_ICON = 8;
const ID_PATTERN = /^[a-z0-9_-]{1,32}$/;

export class DeckStore {
  private readonly file: string;
  private readonly buttons = new Map<string, ActionSpec>();
  private errors: string[] = [];
  private stamp = -1;

  constructor(file: string = join(configDir(), "deck.json")) {
    this.file = file;
  }

  entries(): DeckEntry[] {
    this.refresh();
    return [...this.buttons.entries()].map(([id, spec]) => ({ id, spec }));
  }

  specFor(id: string): ActionSpec | null {
    this.refresh();
    return this.buttons.get(id) ?? null;
  }

  view(): DeckView {
    this.refresh();
    return {
      file: this.file,
      buttons: [...this.buttons.entries()].map(([id, spec]) => ({
        id,
        label: spec.label,
        icon: spec.icon,
        kind: spec.command.kind,
        capability: spec.capability,
        confirm: spec.confirm,
      })),
      errors: [...this.errors],
    };
  }

  private refresh(): void {
    let stamp = 0;
    try {
      stamp = statSync(this.file).mtimeMs;
    } catch {
      stamp = 0;
    }
    if (stamp === this.stamp) return;
    this.stamp = stamp;
    this.buttons.clear();
    this.errors = [];
    if (stamp === 0) return;
    this.parse();
  }

  private parse(): void {
    let raw: unknown;
    try {
      raw = JSON.parse(readFileSync(this.file, "utf8"));
    } catch {
      this.errors.push("deck.json is not valid JSON");
      return;
    }
    if (typeof raw !== "object" || raw === null) {
      this.errors.push("deck.json must be an object with a buttons array");
      return;
    }
    const list = (raw as { buttons?: unknown }).buttons;
    if (!Array.isArray(list)) {
      this.errors.push("deck.json has no buttons array");
      return;
    }
    if (list.length > MAX_BUTTONS) {
      this.errors.push(`only the first ${MAX_BUTTONS} buttons are used`);
    }
    for (const entry of list.slice(0, MAX_BUTTONS)) {
      const parsed = parseButton(entry, this.buttons.size);
      if (typeof parsed === "string") {
        this.errors.push(parsed);
        continue;
      }
      if (this.buttons.has(parsed.id)) {
        this.errors.push(`button "${parsed.id}": duplicate id`);
        continue;
      }
      this.buttons.set(parsed.id, parsed.spec);
    }
  }
}

function parseButton(entry: unknown, index: number): DeckEntry | string {
  const where = `button ${index + 1}`;
  if (typeof entry !== "object" || entry === null) return `${where}: not an object`;
  const raw = entry as Record<string, unknown>;

  const id = typeof raw.id === "string" ? raw.id.trim() : "";
  if (!ID_PATTERN.test(id)) return `${where}: id must match a-z, 0-9, _ or - (max 32)`;
  if (actionFor(id)) return `${where}: "${id}" is a built-in id`;

  const label = typeof raw.label === "string" ? raw.label.trim() : "";
  if (label.length === 0 || label.length > MAX_LABEL) {
    return `${where}: label must be 1-${MAX_LABEL} characters`;
  }

  const icon = typeof raw.icon === "string" && raw.icon.trim().length > 0
    ? raw.icon.trim().slice(0, MAX_ICON)
    : "▸";

  const kind = typeof raw.kind === "string" ? raw.kind.trim().toLowerCase() : "";
  const confirm = raw.confirm === true;

  if (kind === "sleep") {
    // Sleeping is disruptive enough that it always asks twice, whatever the file says.
    return { id, spec: { capability: "power", command: { kind: "sleep" }, label, icon, confirm: true } };
  }

  if (kind === "launch") {
    const target = typeof raw.target === "string" ? raw.target.trim() : "";
    if (!launchTargetOk(target)) {
      return `${where}: target must be an existing absolute path or an http(s) URL`;
    }
    return { id, spec: { capability: "input", command: { kind: "launch", target }, label, icon, confirm } };
  }

  if (kind === "keys") {
    const keys = normalizeKeys(raw.keys);
    if (!keys) return `${where}: keys must be 1-4 known names with modifiers first`;
    return { id, spec: { capability: "input", command: { kind: "keys", keys }, label, icon, confirm } };
  }

  return `${where}: kind must be launch, keys or sleep`;
}

function launchTargetOk(target: string): boolean {
  if (target.length === 0 || target.length > 500) return false;
  if (/^https?:\/\//i.test(target)) return true;
  if (!/^[A-Za-z]:\\/.test(target)) return false;
  try {
    return existsSync(target);
  } catch {
    return false;
  }
}
