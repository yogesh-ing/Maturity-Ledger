// DeskDeck panel. The device token and the transport live in the native activity; this script only
// renders the layout it is given and the state the activity pushes. Nothing here holds a credential.
const bridge = window.DeskDeck ?? null;
// The editor's preview host loads this same file and drives it over postMessage instead of the
// native bridge, so the preview cannot drift from what the phone draws.
const previewMode = document.body.classList.contains("preview")
  || new URLSearchParams(location.search).has("preview");

const els = {
  status: document.getElementById("status"),
  panel: document.getElementById("panel"),
  unpaired: document.getElementById("unpaired"),
  settingsUrl: document.getElementById("settings-url"),
  pairing: document.getElementById("pairing"),
  code: document.getElementById("code"),
  pair: document.getElementById("pair"),
  pairError: document.getElementById("pair-error"),
  pairWait: document.getElementById("pair-wait"),
  connected: document.getElementById("connected"),
  pcName: document.getElementById("pc-name"),
  deviceLine: document.getElementById("device-line"),
  capsLine: document.getElementById("caps-line"),
  pager: document.getElementById("pager"),
  deck: document.getElementById("deck"),
  deckNote: document.getElementById("deck-note"),
  foot: document.getElementById("foot"),
  footPc: document.getElementById("foot-pc"),
  rotate: document.getElementById("rotate"),
  forget: document.getElementById("forget"),
};

const TITLES = {
  clock: "Time",
  date: "Today",
  system: "This PC",
  network: "Network",
  nowplaying: "Now playing",
  phone: "Phone",
};

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" });
const dateFormat = new Intl.DateTimeFormat(undefined, { weekday: "long", day: "numeric", month: "long" });

let layout = null;
let deckItems = [];
let capabilities = [];
let deviceState = {};
// Which page is on screen. The agent names a start page per phone; the pager overrides it locally.
let startPage = null;
let pageIndex = 0;
// Confirmation is armed per tile for three seconds; a second press inside that window runs it.
const armed = new Map();
const rendered = new Map();

const ACTION_ERRORS = {
  unreachable: "The PC is not reachable.",
  forbidden: "This phone has no permission for that action.",
  unknown_action: "That action does not exist on this PC's agent.",
  unsupported_platform: "Desktop actions run on the Windows agent only.",
  action_failed: "The PC refused it — a locked desktop ignores synthetic input.",
  helper_unavailable: "The PC's input helper did not start.",
  helper_timeout: "The PC's input helper did not answer.",
  too_many_actions: "Too many presses at once.",
  confirmation_required: "That action needs a second press.",
  failed: "The action failed.",
};

const PAIR_ERRORS = {
  bad_code: "That code was rejected. Start pairing again on the PC and copy the new code.",
  expired: "The code expired. Start pairing again on the PC.",
  too_many: "Too many attempts. Start pairing again on the PC.",
  unreachable: "The PC is not reachable. Check the cable, or the Wi-Fi link, and that the agent is running.",
  bad_answer: "The agent answered unexpectedly. Update it and try again.",
  unpaired: "Forgotten.",
};

function clearArmed(hideNote = true) {
  for (const [id, timer] of armed) {
    clearTimeout(timer);
    const button = els.deck.querySelector(`button[data-id="${id}"]`);
    if (button) button.classList.remove("armed");
  }
  armed.clear();
  if (hideNote) els.deckNote.textContent = "";
}

/**
 * Draws the deck the agent advertises, filtered by the token's capabilities: `lock` and `sleep` need
 * `power`, everything else needs `input`. A phone without control gets no strip at all.
 */
function renderDeck(paired, caps) {
  clearArmed();
  for (const node of [...els.deck.querySelectorAll("button")]) node.remove();

  const visible = deckItems.filter((item) => caps.includes(item.capability));
  for (const item of visible) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "deckbtn";
    button.dataset.id = item.id;
    button.dataset.confirm = item.confirm === true ? "1" : "0";
    button.textContent = item.icon || "▸";
    button.title = item.label;
    button.setAttribute("aria-label", item.label);
    els.deck.append(button);
  }
  els.deck.append(els.deckNote);
  els.deck.hidden = !paired || visible.length === 0;
  if (els.deck.hidden) els.deckNote.textContent = "";
}

function setChip(state, label) {
  els.status.dataset.state = state;
  els.status.textContent = label;
}

let revokedShown = false;

function render(status) {
  const paired = status.paired === true;
  const usb = status.transport === "usb";
  const online = usb || status.transport === "wifi";
  if (paired) revokedShown = false;

  if (paired) {
    const label = status.transport === "wifi" ? "Connected · Wi-Fi" : "Connected";
    setChip(online ? "online" : "offline", online ? label : "PC offline");
  } else {
    setChip(usb ? "ready" : "offline", usb ? "Ready to pair" : "No USB link");
  }

  els.unpaired.hidden = paired;
  els.panel.hidden = !paired;
  els.foot.hidden = !paired;
  els.pairing.hidden = paired;
  els.connected.hidden = !paired;

  if (paired) {
    els.footPc.textContent = status.pc || "Unknown PC";
    // The layout can arrive before or after this status event; build whichever time it is missing.
    if (rendered.size === 0) buildBoard();
  }

  if (!paired) {
    els.deck.hidden = true;
    els.deckNote.textContent = "";
    rendered.clear();
    els.panel.replaceChildren();
    layout = null;
    deviceState = {};
    els.pairWait.hidden = usb;
    els.pair.disabled = !usb;
    if (status.error === "revoked" && !revokedShown) {
      revokedShown = true;
      showError("This PC revoked the phone. Pair again.");
    }
  }

  if (paired) {
    els.pcName.textContent = status.pc || "Unknown PC";
    els.deviceLine.textContent = [status.device, status.model].filter(Boolean).join(" · ");
    const caps = Array.isArray(status.capabilities) ? status.capabilities : [];
    capabilities = caps;
    els.capsLine.textContent = caps.length ? `Capabilities: ${caps.join(", ")}` : "Capabilities: read";
    renderDeck(true, caps);
  }
}

function showError(message) {
  if (!message) {
    els.pairError.hidden = true;
    return;
  }
  els.pairError.textContent = message;
  els.pairError.hidden = false;
}

function megabytes(bytes) {
  const gb = bytes / 1024 ** 3;
  return gb >= 10 ? `${Math.round(gb)} GB` : `${gb.toFixed(1)} GB`;
}

function rate(bytes) {
  if (!bytes) return "0 B/s";
  const units = ["B/s", "KB/s", "MB/s", "GB/s"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value < 10 && unit > 0 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`;
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function meter(label, valueText, fraction) {
  const row = element("div", "meter");
  const top = element("div", "meter-top");
  top.append(element("span", "label", label), element("span", "num", valueText));
  const track = element("div", "track");
  const fill = document.createElement("i");
  fill.style.width = `${Math.round(Math.min(1, Math.max(0, fraction)) * 100)}%`;
  track.append(fill);
  row.append(top, track);
  return row;
}

const THEME_BACKGROUNDS = { dark: "#11161d", darker: "#0b0f14", black: "#000000" };
const THEME_PADDING = { compact: "6px", comfortable: "10px", roomy: "16px" };

/** A theme is three closed choices plus one colour: a layout can restyle the page, never the app. */
function applyTheme(theme) {
  if (!theme) return;
  const root = document.documentElement;
  if (typeof theme.accent === "string" && /^#[0-9a-f]{6}$/i.test(theme.accent)) {
    root.style.setProperty("--accent", theme.accent);
  }
  const background = THEME_BACKGROUNDS[theme.background];
  if (background) root.style.setProperty("--bg", background);
  const padding = THEME_PADDING[theme.density];
  if (padding) root.style.setProperty("--pad", padding);
}

function currentPages() {
  return layout && Array.isArray(layout.pages) ? layout.pages : [];
}

function selectPage(index) {
  const pages = currentPages();
  if (pages.length === 0) return;
  pageIndex = Math.min(pages.length - 1, Math.max(0, index));
  startPage = null;
  buildBoard();
}

/** One tab per page, shown only when the layout has more than one. */
function renderPager() {
  const pages = currentPages();
  if (!els.pager) return;
  els.pager.replaceChildren(...pages.map((entry, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = index === pageIndex ? "pagedot current" : "pagedot";
    button.textContent = entry.title || `Page ${index + 1}`;
    button.setAttribute("aria-current", index === pageIndex ? "true" : "false");
    button.addEventListener("click", () => selectPage(index));
    return button;
  }));
  els.pager.hidden = pages.length < 2;
}

/** Builds one element per widget on the selected page; state only fills the bodies afterwards. */
function buildBoard() {
  const pages = currentPages();
  rendered.clear();
  els.panel.replaceChildren();
  applyTheme(layout && layout.theme);

  if (pages.length === 0) {
    renderPager();
    els.panel.append(element("p", "empty", "Waiting for a layout from the PC…"));
    return;
  }
  // The agent says which page this phone opens on; an id the layout no longer has means page 1.
  if (startPage) {
    const wanted = pages.findIndex((entry) => entry.id === startPage);
    if (wanted >= 0) pageIndex = wanted;
    startPage = null;
  }
  if (pageIndex >= pages.length) pageIndex = 0;
  renderPager();

  const page = pages[pageIndex];
  for (const widget of page.widgets) {
    const card = element("article", "widget");
    card.dataset.type = widget.type;
    card.style.gridArea = `${widget.y + 1} / ${widget.x + 1} / span ${widget.h} / span ${widget.w}`;
    const head = element("h3", null, widget.title || TITLES[widget.type] || widget.type);
    const body = element("div", "wbody");
    card.append(head, body);
    els.panel.append(card);
    rendered.set(widget.id, { widget, body, last: null });
  }
  paint("all");
}

function paint(which) {
  for (const [id, entry] of rendered) {
    const { widget, body } = entry;
    if (which !== "all" && which !== widget.type) continue;
    const value = deviceState[id];
    body.replaceChildren();

    if (widget.type === "clock") {
      const now = new Date();
      const seconds = String(now.getSeconds()).padStart(2, "0");
      const line = element("div", "time");
      line.append(document.createTextNode(timeFormat.format(now)), element("span", "seconds", seconds));
      body.append(line);
      continue;
    }
    if (widget.type === "date") {
      body.append(element("div", "date", dateFormat.format(new Date())));
      continue;
    }
    if (widget.type === "system") {
      if (!value) {
        body.append(element("div", "muted", "Waiting for the PC…"));
        continue;
      }
      body.append(
        meter("CPU", `${value.cpu}%`, value.cpu / 100),
        meter("Memory", `${megabytes(value.memUsed)} / ${megabytes(value.memTotal)}`, value.memTotal ? value.memUsed / value.memTotal : 0),
        meter("Disk C:", `${megabytes(value.diskUsed)} / ${megabytes(value.diskTotal)}`, value.diskTotal ? value.diskUsed / value.diskTotal : 0),
      );
      continue;
    }
    if (widget.type === "network") {
      if (!value) {
        body.append(element("div", "muted", "No network"));
        continue;
      }
      body.append(
        element("div", "big", value.ipv4 || "—"),
        element("div", "sub", value.name || ""),
        element("div", "pair-rates", ""),
      );
      const rates = body.querySelector(".pair-rates");
      rates.append(element("span", null, `↓ ${rate(value.down)}`), element("span", null, `↑ ${rate(value.up)}`));
      continue;
    }
    if (widget.type === "nowplaying") {
      if (!value) {
        body.append(element("div", "muted", "Nothing playing"));
        continue;
      }
      body.append(
        element("div", "big", value.title),
        element("div", "sub", value.artist || value.app || ""),
        element("div", "sub", value.playing ? `▶ ${value.app || "Playing"}` : "Paused"),
      );
      continue;
    }
    if (widget.type === "phone") {
      if (!value) {
        body.append(element("div", "muted", "No report yet"));
        continue;
      }
      body.append(
        element("div", "battery", `${value.level}%`),
        element("div", "sub", value.charging ? "Charging" : "On battery"),
      );
      continue;
    }
    body.append(element("div", "muted", widget.type));
  }
}

window.onDeskDeckEvent = (type, payload) => {
  let data = {};
  try {
    data = typeof payload === "string" ? JSON.parse(payload) : (payload || {});
  } catch {
    data = {};
  }
  if (type === "status") render(data);
  if (type === "panel") {
    layout = data.layout ?? null;
    deckItems = Array.isArray(data.deck) ? data.deck : [];
    if (typeof data.startPage === "string") {
      startPage = data.startPage;
      pageIndex = 0;
    }
    buildBoard();
    renderDeck(true, capabilities);
  }
  if (type === "state") {
    if (data.widgets && typeof data.widgets === "object") Object.assign(deviceState, data.widgets);
    paint("all");
  }
  if (type === "pair") {
    if (data.ok) {
      showError(null);
      els.code.value = "";
    } else {
      showError(PAIR_ERRORS[data.error] || "Pairing failed.");
    }
  }
  if (type === "rotate") {
    els.rotate.disabled = false;
    if (!data.ok) showError("Could not rotate this phone's key. Try again when the PC is reachable.");
  }
  if (type === "action") {
    clearArmed();
    const button = data.id ? els.deck.querySelector(`button[data-id="${data.id}"]`) : null;
    if (button) {
      button.disabled = false;
      button.classList.remove("busy");
    }
    els.deckNote.textContent = data.ok ? "" : (ACTION_ERRORS[data.error] || ACTION_ERRORS.failed);
  }
};

els.pair.addEventListener("click", () => {
  if (!bridge) {
    showError("The native bridge is unavailable in this build.");
    return;
  }
  const raw = els.code.value.trim();
  if (raw.replace(/[^a-zA-Z0-9]/g, "").length !== 8) {
    showError("Type the eight characters shown on the PC.");
    return;
  }
  showError(null);
  bridge.pair(raw);
});

// Keep the field at "XXXX-XXXX": anything else is dropped as it is typed, so Pair never sees a
// code the agent would reject for its length.
els.code.addEventListener("input", () => {
  const characters = els.code.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
  const formatted = characters.length > 4 ? `${characters.slice(0, 4)}-${characters.slice(4)}` : characters;
  if (els.code.value !== formatted) els.code.value = formatted;
  showError(null);
});

els.code.addEventListener("keydown", (event) => {
  if (event.key === "Enter") els.pair.click();
});

els.rotate.addEventListener("click", () => {
  els.rotate.disabled = true;
  bridge?.rotate();
});

els.forget.addEventListener("click", () => bridge?.unpair());

els.deck.addEventListener("click", (event) => {
  const target = event.target;
  if (!(target instanceof Element)) return;
  const button = target.closest("button[data-id]");
  if (!button) return;
  const id = button.dataset.id;
  if (!id) return;
  if (!bridge?.action) {
    els.deckNote.textContent = "Controls need the 0.5.0 app build.";
    return;
  }

  const needsConfirm = button.dataset.confirm === "1";
  if (needsConfirm && !armed.has(id)) {
    clearArmed();
    button.classList.add("armed");
    armed.set(id, setTimeout(() => clearArmed(), 3000));
    els.deckNote.textContent = `Tap ${button.title} again to confirm.`;
    return;
  }

  const confirmed = needsConfirm;
  clearArmed();
  // Press feedback is optimistic and local; the agent's answer clears it or shows the reason.
  button.disabled = true;
  button.classList.add("busy");
  bridge.action(id, confirmed);
});

// Clock and date are rendered from the phone's own time: no round trip, no drift.
setInterval(() => paint("clock"), 1000);
setInterval(() => paint("date"), 30_000);
paint("all");

if (previewMode) {
  // The editor posts its draft here. Nothing is stored, and no credential is involved.
  els.unpaired.hidden = true;
  els.panel.hidden = false;
  setChip("online", "Preview");
  window.addEventListener("message", (event) => {
    if (event.origin !== location.origin) return;
    const data = event.data ?? {};
    if (data.type !== "layout") return;
    layout = data.layout ?? null;
    deckItems = Array.isArray(data.deck) ? data.deck : [];
    startPage = typeof data.startPage === "string" ? data.startPage : null;
    pageIndex = 0;
    capabilities = Array.isArray(data.capabilities) ? data.capabilities : ["read"];
    buildBoard();
    renderDeck(true, capabilities);
  });
} else if (bridge) {
  try {
    render(JSON.parse(bridge.status()));
  } catch {
    /* the first status event will arrive anyway */
  }
  try {
    const cached = bridge.panel();
    if (cached) {
      const parsed = JSON.parse(cached);
      layout = parsed.layout ?? null;
      deckItems = Array.isArray(parsed.deck) ? parsed.deck : [];
      buildBoard();
      renderDeck(true, capabilities);
    }
  } catch {
    /* ignore */
  }
  // Belt and braces: the native poller pushes changes, this catches anything missed.
  setInterval(() => {
    try {
      render(JSON.parse(bridge.status()));
    } catch {
      /* ignore */
    }
  }, 2500);
} else {
  showError("The native bridge is unavailable in this build.");
}

if (location.port) els.settingsUrl.textContent = `http://127.0.0.1:${location.port}`;
