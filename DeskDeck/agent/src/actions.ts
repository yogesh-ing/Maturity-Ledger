import type { Capability } from "./config.ts";

/**
 * The only things the agent will ever execute for a phone. Ids are the whole protocol: no request
 * carries a key name, a shell string, a path or a program, so nothing a phone sends can widen this
 * set. The helper validates the same ids again before it touches the desktop.
 *
 * Phase 3b adds two configured kinds — `launch` and `keys` — but their targets come from
 * `deck.json` on the PC, never from a phone. The request still names only an id.
 */
export type ActionCommand =
  | { kind: "media"; action: "playpause" | "next" | "prev" | "stop" }
  | { kind: "volume"; delta: number }
  | { kind: "mute" }
  | { kind: "lock" }
  | { kind: "sleep" }
  | { kind: "launch"; target: string }
  | { kind: "keys"; keys: string[] };

export type ActionSpec = {
  /** The token capability this action needs; a read-only phone cannot press anything. */
  capability: Capability;
  command: ActionCommand;
  /** Short text for the tile, at most a few words. */
  label: string;
  /** One glyph; the panel shows this instead of the label when space is tight. */
  icon: string;
  /** Destructive or disruptive actions need a second, deliberate press. */
  confirm: boolean;
};

const BUILT_INS: Record<string, ActionSpec> = {
  "media.prev": {
    capability: "input",
    command: { kind: "media", action: "prev" },
    label: "Previous track",
    icon: "⏮",
    confirm: false,
  },
  "media.playpause": {
    capability: "input",
    command: { kind: "media", action: "playpause" },
    label: "Play or pause",
    icon: "⏯",
    confirm: false,
  },
  "media.next": {
    capability: "input",
    command: { kind: "media", action: "next" },
    label: "Next track",
    icon: "⏭",
    confirm: false,
  },
  // One step is one key press, which Windows maps to a 2% volume change.
  "volume.down": {
    capability: "input",
    command: { kind: "volume", delta: -1 },
    label: "Volume down",
    icon: "−",
    confirm: false,
  },
  mute: {
    capability: "input",
    command: { kind: "mute" },
    label: "Mute",
    icon: "🔇",
    confirm: false,
  },
  "volume.up": {
    capability: "input",
    command: { kind: "volume", delta: 1 },
    label: "Volume up",
    icon: "+",
    confirm: false,
  },
  // Locking hides the session rather than destroying anything, but it needs the power capability so
  // an input-only token can never lock the user out.
  lock: {
    capability: "power",
    command: { kind: "lock" },
    label: "Lock the PC",
    icon: "🔒",
    confirm: false,
  },
};

const BUILT_IN_ACTIONS = new Map<string, ActionSpec>(Object.entries(BUILT_INS));

/** Ids the panel draws, in the order it should draw them. */
export function actionIds(): string[] {
  return [...BUILT_IN_ACTIONS.keys()];
}

export function actionFor(id: string): ActionSpec | null {
  return BUILT_IN_ACTIONS.get(id) ?? null;
}

const MODIFIER_KEYS = new Set(["ctrl", "shift", "alt", "win"]);

const NAMED_KEYS = new Set([
  "enter",
  "esc",
  "tab",
  "space",
  "backspace",
  "delete",
  "insert",
  "home",
  "end",
  "pageup",
  "pagedown",
  "up",
  "down",
  "left",
  "right",
  "printscreen",
]);

function letterOrDigit(name: string): boolean {
  if (name.length !== 1) return false;
  return (name >= "a" && name <= "z") || (name >= "0" && name <= "9");
}

function functionKey(name: string): boolean {
  if (!/^f([1-9]|1[0-2])$/.test(name)) return false;
  return true;
}

export function isKnownKey(name: string): boolean {
  return MODIFIER_KEYS.has(name) || NAMED_KEYS.has(name) || letterOrDigit(name) || functionKey(name);
}

/**
 * Validates a configured key sequence: one to four names, modifiers first, exactly one real key,
 * no repeats. The helper maps the same names to virtual-key codes and refuses anything else, so a
 * deck entry can never smuggle a script into the press.
 */
export function normalizeKeys(value: unknown): string[] | null {
  if (!Array.isArray(value) || value.length === 0 || value.length > 4) return null;
  const keys: string[] = [];
  for (const entry of value) {
    if (typeof entry !== "string") return null;
    const name = entry.trim().toLowerCase();
    if (!isKnownKey(name)) return null;
    if (keys.includes(name)) return null;
    keys.push(name);
  }
  const last = keys.at(-1);
  if (last === undefined || MODIFIER_KEYS.has(last)) return null;
  for (const name of keys.slice(0, -1)) {
    if (!MODIFIER_KEYS.has(name)) return null;
  }
  return keys;
}
