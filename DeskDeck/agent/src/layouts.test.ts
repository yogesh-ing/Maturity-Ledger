import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import {
  DEFAULT_THEME,
  LayoutStore,
  defaultLayout,
  pageIdFor,
  validateLayout,
  validateTheme,
  MAX_HISTORY,
} from "./layouts.ts";

/**
 * The layout engine has no I/O beyond `panel.json`, so these tests point DESKDECK_HOME at a temp
 * directory and drive the store exactly as the editor does. No agent, no port, no Windows.
 */
let home = "";

before(() => {
  home = mkdtempSync(join(tmpdir(), "deskdeck-layout-"));
  process.env.DESKDECK_HOME = home;
});

after(() => {
  if (home) rmSync(home, { recursive: true, force: true });
});

function fresh(): LayoutStore {
  const path = join(home, "panel.json");
  try {
    rmSync(path);
  } catch {
    /* no file yet */
  }
  return new LayoutStore();
}

function layout(pages: unknown[], extra: Record<string, unknown> = {}): unknown {
  return { version: 0, updatedAt: 0, ...extra, pages };
}

const onePage = [
  {
    id: "home",
    title: "Home",
    widgets: [
      { id: "clock", type: "clock", x: 0, y: 0, w: 6, h: 3 },
      { id: "system", type: "system", x: 6, y: 0, w: 6, h: 3, title: "PC" },
    ],
  },
];

describe("validation", () => {
  it("serves the built-in page when nothing was pushed", () => {
    const store = fresh();
    assert.equal(store.get().pages.length, 1);
    assert.equal(store.get().pages[0]?.id, "home");
    assert.deepEqual(store.get().theme, DEFAULT_THEME);
    assert.equal(store.etag(), `"v${defaultLayout().version}"`);
  });

  it("drops unknown widget types and duplicate ids but keeps the rest", () => {
    const parsed = validateLayout(
      layout([
        {
          id: "home",
          title: "Home",
          widgets: [
            { id: "clock", type: "clock", x: 0, y: 0, w: 4, h: 2 },
            { id: "clock", type: "clock", x: 4, y: 0, w: 4, h: 2 },
            { id: "photo", type: "photo", x: 0, y: 2, w: 4, h: 4 },
            { id: "net", type: "network", x: 4, y: 2, w: 4, h: 2 },
          ],
        },
      ]),
    );
    assert.ok(parsed);
    assert.deepEqual(parsed.pages[0]?.widgets.map((w) => w.id), ["clock", "net"]);
  });

  it("clips a rectangle that leaves the grid instead of rejecting it", () => {
    const parsed = validateLayout(
      layout([{ id: "home", title: "Home", widgets: [{ id: "clock", type: "clock", x: 10, y: 7, w: 9, h: 9 }] }]),
    );
    const widget = parsed?.pages[0]?.widgets[0];
    assert.deepEqual(widget && { x: widget.x, y: widget.y, w: widget.w, h: widget.h }, { x: 10, y: 7, w: 2, h: 1 });
  });

  it("keeps widget ids unique across pages, so two pages cannot share one state slot", () => {
    const parsed = validateLayout(
      layout([
        { id: "a", title: "A", widgets: [{ id: "clock", type: "clock", x: 0, y: 0, w: 4, h: 2 }] },
        { id: "b", title: "B", widgets: [{ id: "clock", type: "clock", x: 0, y: 0, w: 4, h: 2 }] },
      ]),
    );
    assert.equal(parsed?.pages.length, 2);
    assert.equal(parsed?.pages[1]?.widgets.length, 0);
  });

  it("rejects a layout with no usable page", () => {
    assert.equal(validateLayout({ pages: [] }), null);
    assert.equal(validateLayout({ pages: [{ id: "x" }] }), null);
    assert.equal(validateLayout("nope"), null);
  });

  it("normalizes the theme and falls back on anything unknown", () => {
    assert.deepEqual(validateTheme({ accent: "#AABBCC", density: "compact", background: "black" }), {
      accent: "#aabbcc",
      density: "compact",
      background: "black",
    });
    assert.deepEqual(validateTheme({ accent: "red", density: "huge", background: "url(x)" }), DEFAULT_THEME);
    assert.deepEqual(validateTheme(null), DEFAULT_THEME);
  });
});

describe("optimistic push", () => {
  it("assigns the revision itself and persists the result", () => {
    const store = fresh();
    const before = store.get().version;
    const result = store.push(layout(onePage), null);
    assert.ok(result.ok);
    assert.equal(result.layout.version, before + 1);
    assert.equal(store.etag(), `"v${before + 1}"`);

    const onDisk = JSON.parse(readFileSync(join(home, "panel.json"), "utf8")) as { version: number };
    assert.equal(onDisk.version, before + 1);
    assert.equal(new LayoutStore().get().version, before + 1);
  });

  it("refuses a save built on a stale revision and returns the current layout", () => {
    const store = fresh();
    const first = store.push(layout(onePage), "*");
    assert.ok(first.ok);
    const stale = store.etag();

    const second = store.push(layout(onePage), stale);
    assert.ok(second.ok);

    const third = store.push(layout(onePage), stale);
    assert.equal(third.ok, false);
    if (third.ok) return;
    assert.equal(third.code, "conflict");
    assert.equal(third.current.version, second.layout.version);
    assert.equal(store.get().version, second.layout.version);
  });

  it("accepts `*` as an unconditional overwrite", () => {
    const store = fresh();
    assert.ok(store.push(layout(onePage), "*").ok);
    assert.ok(store.push(layout(onePage), "*").ok);
  });

  it("refuses an invalid layout without touching the stored one", () => {
    const store = fresh();
    const kept = store.get().version;
    const result = store.push({ pages: "no" }, "*");
    assert.equal(result.ok, false);
    if (!result.ok) assert.equal(result.code, "invalid");
    assert.equal(store.get().version, kept);
  });
});

describe("history and rollback", () => {
  it("keeps previous revisions and their page counts", () => {
    const store = fresh();
    const base = store.get().version;
    const pushed = store.push(layout(onePage), "*");
    assert.ok(pushed.ok);
    const history = store.history();
    assert.equal(history.length, 1);
    assert.equal(history[0]?.version, base);
  });

  it("restores an earlier revision as a new revision", () => {
    const store = fresh();
    const base = store.get();
    const pushed = store.push(
      layout([{ id: "solo", title: "Solo", widgets: [{ id: "date", type: "date", x: 0, y: 0, w: 4, h: 2 }] }]),
      "*",
    );
    assert.ok(pushed.ok);

    const undone = store.rollback(base.version);
    assert.ok(undone.ok);
    assert.equal(undone.layout.pages[0]?.id, base.pages[0]?.id);
    assert.equal(undone.layout.version, pushed.layout.version + 1);
    assert.equal(store.history().some((entry) => entry.version === pushed.layout.version), true);
  });

  it("answers not_found for a revision it never held", () => {
    const store = fresh();
    const result = store.rollback(999);
    assert.equal(result.ok, false);
    if (!result.ok) assert.equal(result.code, "not_found");
  });

  it("caps the history", () => {
    const store = fresh();
    for (let index = 0; index < MAX_HISTORY + 5; index += 1) {
      assert.ok(store.push(layout(onePage), "*").ok);
    }
    assert.equal(store.history().length, MAX_HISTORY);
  });
});

describe("device pages and file errors", () => {
  it("resolves a phone's page, falling back to the first page", () => {
    const store = fresh();
    const pushed = store.push(
      layout([
        { id: "home", title: "Home", widgets: [{ id: "clock", type: "clock", x: 0, y: 0, w: 4, h: 2 }] },
        { id: "media", title: "Media", widgets: [{ id: "now", type: "nowplaying", x: 0, y: 0, w: 8, h: 4 }] },
      ]),
      "*",
    );
    assert.ok(pushed.ok);
    assert.equal(pageIdFor(pushed.layout, "media"), "media");
    assert.equal(pageIdFor(pushed.layout, "gone"), "home");
    assert.equal(pageIdFor(pushed.layout, null), "home");
  });

  it("reports a panel.json that does not parse and falls back", () => {
    const store = fresh();
    writeFileSync(join(home, "panel.json"), "{ not json", "utf8");
    const reloaded = new LayoutStore();
    assert.ok(reloaded.error);
    assert.equal(reloaded.get().pages[0]?.id, "home");
  });
});
