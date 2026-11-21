// DeskDeck layout editor. Runs on the loopback settings surface only: it uses the short-lived session
// token in the page, never the agent's local token, and it cannot reach a phone's LAN listener.
const token = document.body.dataset.token ?? "";
const auth = { authorization: `Bearer ${token}` };
const el = (id) => document.getElementById(id);

const TYPES = ["clock", "date", "system", "network", "nowplaying", "phone"];
const NAMES = {
  clock: "Clock",
  date: "Date",
  system: "This PC",
  network: "Network",
  nowplaying: "Now playing",
  phone: "Phone",
};
const COLS = 12;
const ROWS = 8;
const MAX_HISTORY = 60;

const state = {
  layout: null,
  etag: null,
  history: [],
  devices: [],
  deck: [],
  page: 0,
  selected: null,
  undo: [],
  redo: [],
};

let previewReady = false;
let drag = null;

/* ------------------------------------------------------------------ transport */

/** Returns the HTTP status too: a 409 is not an error to log, it is a conflict to show. */
async function api(path, method = "GET", payload = null, extra = {}) {
  const request = { method, headers: { ...auth, ...extra }, cache: "no-store" };
  if (payload !== null) {
    request.headers["content-type"] = "application/json";
    request.body = JSON.stringify(payload);
  }
  try {
    const response = await fetch(path, request);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { /* leave null */ }
    return { ok: response.ok, code: response.status, body };
  } catch {
    return { ok: false, code: 0, body: null };
  }
}

function status(text, kind = "") {
  const chip = el("status");
  chip.textContent = text;
  chip.dataset.state = kind;
}

/* ------------------------------------------------------------------ draft edits */

const clone = (value) => JSON.parse(JSON.stringify(value));
const page = () => state.layout?.pages[state.page] ?? null;

/** One snapshot per user gesture; the stacks are small and the payoff is a working Ctrl+Z. */
function snapshot() {
  if (!state.layout) return;
  state.undo.push(JSON.stringify(state.layout));
  if (state.undo.length > MAX_HISTORY) state.undo.shift();
  state.redo.length = 0;
}

function undo() {
  if (state.undo.length === 0) return;
  state.redo.push(JSON.stringify(state.layout));
  state.layout = JSON.parse(state.undo.pop());
  state.selected = null;
  render();
}

function redo() {
  if (state.redo.length === 0) return;
  state.undo.push(JSON.stringify(state.layout));
  state.layout = JSON.parse(state.redo.pop());
  state.selected = null;
  render();
}

function idExists(id) {
  return state.layout.pages.some((entry) => entry.widgets.some((widget) => widget.id === id));
}

function newWidgetId(type) {
  for (let index = 1; index < 1000; index += 1) {
    const id = index === 1 ? type : `${type}${index}`;
    if (id.length <= 32 && !idExists(id)) return id;
  }
  return `${type}_${Date.now() % 100000}`;
}

const overlaps = (a, others) => others.some((b) =>
  a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h);

/** First free 4×2 slot, so a new widget never lands on top of an existing one. */
function freeSlot(widgets) {
  for (let y = 0; y <= ROWS - 2; y += 1) {
    for (let x = 0; x <= COLS - 4; x += 1) {
      const candidate = { x, y, w: 4, h: 2 };
      if (!overlaps(candidate, widgets)) return candidate;
    }
  }
  return { x: 0, y: 0, w: 4, h: 2 };
}

function addWidget(type) {
  const current = page();
  if (!current) return;
  snapshot();
  const slot = freeSlot(current.widgets);
  current.widgets.push({ id: newWidgetId(type), type, ...slot, title: null });
  state.selected = current.widgets[current.widgets.length - 1].id;
  render();
}

function removeWidget(id) {
  const current = page();
  if (!current) return;
  const index = current.widgets.findIndex((widget) => widget.id === id);
  if (index < 0) return;
  snapshot();
  current.widgets.splice(index, 1);
  state.selected = null;
  render();
}

function selectedWidget() {
  const current = page();
  if (!current || !state.selected) return null;
  return current.widgets.find((widget) => widget.id === state.selected) ?? null;
}

/* ------------------------------------------------------------------ rendering */

function renderPages() {
  const root = el("pages");
  root.replaceChildren(...state.layout.pages.map((entry, index) => {
    const li = document.createElement("li");
    li.className = index === state.page ? "row-item current" : "row-item";
    const button = document.createElement("button");
    button.type = "button";
    button.className = "link";
    button.textContent = entry.title || entry.id;
    button.addEventListener("click", () => {
      state.page = index;
      state.selected = null;
      render();
    });
    const meta = document.createElement("span");
    meta.className = "muted small";
    meta.textContent = `${entry.id} · ${entry.widgets.length}`;
    li.append(button, meta);
    return li;
  }));
  el("del-page").disabled = state.layout.pages.length <= 1;
  el("page-title").value = page()?.title ?? "";
}

function renderPalette() {
  el("palette").replaceChildren(...TYPES.map((type) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "ghost";
    button.textContent = NAMES[type];
    button.addEventListener("click", () => addWidget(type));
    return button;
  }));
}

function renderBoard() {
  const board = el("board");
  board.replaceChildren();
  const current = page();
  if (!current) return;

  for (const widget of current.widgets) {
    const node = document.createElement("div");
    node.className = widget.id === state.selected ? "w selected" : "w";
    node.dataset.id = widget.id;
    node.dataset.type = widget.type;
    node.style.left = `${(widget.x / COLS) * 100}%`;
    node.style.top = `${(widget.y / ROWS) * 100}%`;
    node.style.width = `${(widget.w / COLS) * 100}%`;
    node.style.height = `${(widget.h / ROWS) * 100}%`;

    const label = document.createElement("span");
    label.className = "w-label";
    label.textContent = widget.title || NAMES[widget.type] || widget.type;
    const size = document.createElement("span");
    size.className = "w-size";
    size.textContent = `${widget.x},${widget.y} · ${widget.w}×${widget.h}`;
    const handle = document.createElement("i");
    handle.className = "handle";
    node.append(label, size, handle);
    board.append(node);
  }
}

function renderInspector() {
  const widget = selectedWidget();
  el("inspector").hidden = !widget;
  if (!widget) return;
  el("w-title").value = widget.title ?? "";
  el("w-meta").textContent = `${widget.id} · ${widget.type} · ${widget.x},${widget.y} ${widget.w}×${widget.h}`;
}

function renderTheme() {
  const theme = state.layout.theme;
  el("accent").value = theme.accent;
  el("density").value = theme.density;
  el("bg").value = theme.background;
}

function renderPhones() {
  const root = el("phones");
  if (state.devices.length === 0) {
    root.replaceChildren(Object.assign(document.createElement("li"), { className: "muted", textContent: "No paired phones." }));
    return;
  }
  root.replaceChildren(...state.devices.map((device) => {
    const li = document.createElement("li");
    li.className = "row-item";
    const name = document.createElement("span");
    name.textContent = device.name;
    const select = document.createElement("select");
    const auto = document.createElement("option");
    auto.value = "";
    auto.textContent = "First page";
    select.append(auto);
    for (const entry of state.layout.pages) {
      const option = document.createElement("option");
      option.value = entry.id;
      option.textContent = entry.title || entry.id;
      select.append(option);
    }
    select.value = device.page ?? "";
    select.addEventListener("change", async () => {
      const answer = await api(`/api/settings/devices/${encodeURIComponent(device.id)}/page`, "POST", {
        page: select.value || null,
      });
      if (answer.ok) {
        device.page = select.value || null;
        status(`page for ${device.name} saved`);
      } else {
        status("could not save that assignment", "error");
      }
    });
    li.append(name, select);
    return li;
  }));
}

function renderHistory() {
  const root = el("history");
  if (state.history.length === 0) {
    root.replaceChildren(Object.assign(document.createElement("li"), { className: "muted", textContent: "Nothing pushed yet." }));
    return;
  }
  root.replaceChildren(...state.history.map((entry) => {
    const li = document.createElement("li");
    li.className = "row-item";
    const label = document.createElement("span");
    const when = entry.updatedAt ? new Date(entry.updatedAt).toLocaleString() : "built-in";
    label.textContent = `v${entry.version} · ${entry.pages} page${entry.pages === 1 ? "" : "s"} · ${when}`;
    const button = document.createElement("button");
    button.type = "button";
    button.className = "ghost";
    button.textContent = "Restore";
    button.addEventListener("click", () => restore(entry.version));
    li.append(label, button);
    return li;
  }));
}

function render() {
  if (!state.layout) return;
  if (state.page >= state.layout.pages.length) state.page = 0;
  renderPages();
  renderPalette();
  renderBoard();
  renderInspector();
  renderTheme();
  renderPhones();
  renderHistory();
  el("undo").disabled = state.undo.length === 0;
  el("redo").disabled = state.redo.length === 0;
  paintPreview();
}

/* ------------------------------------------------------------------ live preview */

function paintPreview() {
  const frame = el("preview");
  if (!previewReady || !state.layout) return;
  frame.contentWindow?.postMessage({
    type: "layout",
    layout: state.layout,
    startPage: page()?.id ?? null,
    deck: state.deck,
    capabilities: ["read", "input", "power"],
  }, location.origin);
}

el("preview").addEventListener("load", () => {
  previewReady = true;
  paintPreview();
});

/* ------------------------------------------------------------------ drag and resize */

function cellSize() {
  const rect = el("board").getBoundingClientRect();
  return { w: rect.width / COLS, h: rect.height / ROWS };
}

el("board").addEventListener("pointerdown", (event) => {
  const target = event.target instanceof Element ? event.target.closest("[data-id]") : null;
  if (!(target instanceof HTMLElement)) {
    state.selected = null;
    render();
    return;
  }
  const widget = page()?.widgets.find((entry) => entry.id === target.dataset.id);
  if (!widget) return;
  state.selected = widget.id;
  drag = {
    id: widget.id,
    resize: event.target instanceof Element && event.target.classList.contains("handle"),
    startX: event.clientX,
    startY: event.clientY,
    origin: { ...widget },
    moved: false,
  };
  el("board").setPointerCapture(event.pointerId);
  event.preventDefault();
  render();
});

el("board").addEventListener("pointermove", (event) => {
  if (!drag) return;
  const widget = page()?.widgets.find((entry) => entry.id === drag.id);
  if (!widget) return;
  const { w: cellW, h: cellH } = cellSize();
  const dx = Math.round((event.clientX - drag.startX) / cellW);
  const dy = Math.round((event.clientY - drag.startY) / cellH);
  const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

  const next = drag.resize
    ? {
        w: clamp(drag.origin.w + dx, 1, COLS - widget.x),
        h: clamp(drag.origin.h + dy, 1, ROWS - widget.y),
      }
    : {
        x: clamp(drag.origin.x + dx, 0, COLS - widget.w),
        y: clamp(drag.origin.y + dy, 0, ROWS - widget.h),
      };

  const changed = drag.resize
    ? next.w !== widget.w || next.h !== widget.h
    : next.x !== widget.x || next.y !== widget.y;
  if (!changed) return;
  if (!drag.moved) {
    // Restore the pre-drag geometry before snapshotting, so undo lands where the drag started.
    Object.assign(widget, drag.origin);
    snapshot();
    drag.moved = true;
  }
  Object.assign(widget, next);
  renderBoard();
  renderInspector();
  paintPreview();
});

const endDrag = () => {
  if (!drag) return;
  drag = null;
  render();
};
el("board").addEventListener("pointerup", endDrag);
el("board").addEventListener("pointercancel", endDrag);

/* ------------------------------------------------------------------ server round trips */

async function load(keepDraft = false) {
  const answer = await api("/api/settings/layout");
  if (!answer.ok || !answer.body) {
    status("could not load the layout", "error");
    return;
  }
  state.layout = clone(answer.body.layout);
  state.etag = answer.body.etag;
  state.history = Array.isArray(answer.body.history) ? answer.body.history : [];
  state.deck = Array.isArray(answer.body.deck) ? answer.body.deck : [];
  if (!keepDraft) {
    state.page = 0;
    state.selected = null;
    state.undo = [];
    state.redo = [];
    el("conflict").hidden = true;
  }
  const settings = await api("/api/settings/state");
  const assigned = settings.ok && settings.body ? settings.body.devicePages ?? {} : {};
  state.devices = (settings.ok && settings.body ? settings.body.devices ?? [] : [])
    .map((device) => ({ ...device, page: assigned[device.id] ?? null }));
  status(`editing ${state.etag}`);
  render();
}

async function push() {
  const button = el("push");
  button.disabled = true;
  const draft = { version: 0, updatedAt: 0, theme: state.layout.theme, pages: state.layout.pages };
  const answer = await api("/api/settings/layout", "PUT", draft, { "if-match": state.etag });
  button.disabled = false;

  if (answer.ok && answer.body) {
    state.layout = clone(answer.body.layout);
    state.etag = answer.body.etag;
    state.history = Array.isArray(answer.body.history) ? answer.body.history : [];
    state.undo = [];
    state.redo = [];
    el("conflict").hidden = true;
    status(`pushed ${answer.body.etag}`, "ok");
    render();
    return;
  }
  if (answer.code === 409) {
    el("conflict").hidden = false;
    status("someone else saved a newer layout", "error");
    return;
  }
  status(answer.body?.error?.message ?? "the layout was rejected", "error");
}

async function restore(version) {
  const answer = await api("/api/settings/layout/rollback", "POST", { version });
  if (answer.ok) {
    status(`restored v${version} as a new version`, "ok");
    await load();
  } else {
    status("could not restore that version", "error");
  }
}

/* ------------------------------------------------------------------ controls */

el("undo").addEventListener("click", undo);
el("redo").addEventListener("click", redo);
el("reload").addEventListener("click", () => load());
el("push").addEventListener("click", push);
el("take-current").addEventListener("click", () => load());

el("add-page").addEventListener("click", () => {
  if (state.layout.pages.length >= 8) {
    status("a layout holds at most 8 pages", "error");
    return;
  }
  snapshot();
  let index = state.layout.pages.length + 1;
  while (state.layout.pages.some((entry) => entry.id === `page${index}`)) index += 1;
  state.layout.pages.push({ id: `page${index}`, title: `Page ${index}`, widgets: [] });
  state.page = state.layout.pages.length - 1;
  state.selected = null;
  render();
});

el("del-page").addEventListener("click", () => {
  if (state.layout.pages.length <= 1) return;
  snapshot();
  state.layout.pages.splice(state.page, 1);
  state.page = Math.max(0, state.page - 1);
  state.selected = null;
  render();
});

el("page-title").addEventListener("input", (event) => {
  const current = page();
  if (!current) return;
  current.title = event.target.value.slice(0, 40);
  renderPages();
  paintPreview();
});

el("w-title").addEventListener("input", (event) => {
  const widget = selectedWidget();
  if (!widget) return;
  widget.title = event.target.value.trim() ? event.target.value.slice(0, 40) : null;
  renderBoard();
  paintPreview();
});

el("w-delete").addEventListener("click", () => {
  if (state.selected) removeWidget(state.selected);
});

el("accent").addEventListener("input", (event) => {
  state.layout.theme.accent = event.target.value;
  paintPreview();
});

el("density").addEventListener("change", (event) => {
  state.layout.theme.density = event.target.value;
  paintPreview();
});

el("bg").addEventListener("change", (event) => {
  state.layout.theme.background = event.target.value;
  paintPreview();
});

document.addEventListener("keydown", (event) => {
  const typing = event.target instanceof HTMLElement && ["INPUT", "SELECT", "TEXTAREA"].includes(event.target.tagName);
  const mod = event.ctrlKey || event.metaKey;
  if (mod && event.key.toLowerCase() === "z") {
    event.preventDefault();
    if (event.shiftKey) redo(); else undo();
    return;
  }
  if (!typing && (event.key === "Delete" || event.key === "Backspace") && state.selected) {
    event.preventDefault();
    removeWidget(state.selected);
  }
});

load();
