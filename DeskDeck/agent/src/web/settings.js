// DeskDeck settings page. Talks only to the loopback agent and uses the short-lived session token
// embedded in the page, so the agent's long-lived local token never enters the browser.
const token = document.body.dataset.token ?? "";
const headers = { authorization: `Bearer ${token}` };

const el = (id) => document.getElementById(id);

async function api(path, method = "GET", payload = null) {
  const request = { method, headers: { ...headers }, cache: "no-store" };
  if (payload !== null) {
    request.headers["content-type"] = "application/json";
    request.body = JSON.stringify(payload);
  }
  const response = await fetch(path, request);
  const text = await response.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { /* keep null */ }
  return { ok: response.ok, body };
}

function renderPairing(pairing) {
  el("pair-idle").hidden = !!pairing;
  el("pair-live").hidden = !pairing;
  if (!pairing) return;
  el("code").textContent = pairing.code;
  el("check").textContent = pairing.check;
  el("expires").textContent = new Date(pairing.expiresAt).toLocaleTimeString();
}

function renderUsb(usb) {
  el("usb-adb").textContent = usb.adb ?? "not found";
  el("usb-devices").textContent = usb.devices.length ? usb.devices.join(", ") : "none attached";
  const label = { ok: "ready", waiting: "waiting for a device", failed: "failed", "no-adb": "adb not available" };
  el("usb-reverse").textContent = label[usb.reverse] ?? usb.reverse;
}

function renderWifi(wifi) {
  if (!wifi) return;
  const on = wifi.enabled === true;
  el("wifi-toggle").textContent = on ? "Disable Wi-Fi" : "Enable Wi-Fi";
  el("wifi-chip").textContent = on ? `on · port ${wifi.port}` : "off";
  el("wifi-toggle").dataset.enabled = on ? "1" : "0";
  el("wifi-url").textContent = on ? (wifi.urls.length ? wifi.urls.join(" · ") : "no network address") : "turned off";
  el("wifi-pin").textContent = wifi.pin ?? "not generated yet";
  el("wifi-error").hidden = !wifi.error;
  el("wifi-error").textContent = wifi.error ?? "";
}

function renderDevices(list) {
  const root = el("devices");
  if (!list.length) {
    root.replaceChildren(Object.assign(document.createElement("li"), { className: "muted", textContent: "None yet." }));
    return;
  }
  root.replaceChildren(...list.map((device) => {
    const li = document.createElement("li");
    const info = document.createElement("div");
    const name = document.createElement("div");
    name.className = "name";
    name.textContent = device.name;
    const meta = document.createElement("div");
    meta.className = "meta";
    const seen = device.lastSeen ? `last seen ${new Date(device.lastSeen).toLocaleTimeString()}` : "never connected";
    meta.textContent = `${device.id} · ${device.capabilities.join(", ")} · paired ${new Date(device.pairedAt).toLocaleString()} · ${seen}`;
    info.append(name, meta);
    const revoke = document.createElement("button");
    revoke.type = "button";
    revoke.className = "danger";
    revoke.textContent = "Revoke";
    revoke.addEventListener("click", async () => {
      revoke.disabled = true;
      await api(`/api/settings/devices/${encodeURIComponent(device.id)}`, "DELETE");
      await refresh();
    });
    const controls = document.createElement("div");
    controls.className = "controls";
    const control = document.createElement("button");
    control.type = "button";
    control.className = "ghost";
    const allowed = device.capabilities.includes("input");
    control.textContent = allowed ? "Control: on" : "Allow control";
    control.addEventListener("click", async () => {
      control.disabled = true;
      await api(`/api/settings/devices/${encodeURIComponent(device.id)}/capabilities`, "POST", {
        input: !allowed,
        power: !allowed,
      });
      await refresh();
    });
    controls.append(control, revoke);
    li.append(info, controls);
    return li;
  }));
}

function renderDeckState(deck) {
  if (!deck) return;
  el("deck-file").textContent = deck.file;
  const root = el("deck-list");
  if (!deck.buttons.length) {
    root.replaceChildren(Object.assign(document.createElement("li"), {
      className: "muted",
      textContent: "No custom buttons yet.",
    }));
  } else {
    root.replaceChildren(...deck.buttons.map((button) => {
      const li = document.createElement("li");
      const info = document.createElement("div");
      const name = document.createElement("div");
      name.className = "name";
      name.textContent = `${button.icon} ${button.label}`;
      const meta = document.createElement("div");
      meta.className = "meta";
      meta.textContent = `${button.id} · ${button.kind} · needs ${button.capability}${button.confirm ? " · asks twice" : ""}`;
      info.append(name, meta);
      li.append(info);
      return li;
    }));
  }
  const errors = Array.isArray(deck.errors) ? deck.errors : [];
  el("deck-errors").hidden = errors.length === 0;
  el("deck-errors").textContent = errors.join(" · ");
}

async function refresh() {
  const { ok, body } = await api("/api/settings/state");
  if (!ok || !body) return;
  el("agent").textContent = `agent ${body.agent} · ${body.pc}`;
  renderPairing(body.pairing);
  renderUsb(body.usb);
  renderWifi(body.wifi);
  renderDeckState(body.deck);
  renderDevices(body.devices);
}

el("start").addEventListener("click", async () => {
  el("start").disabled = true;
  await api("/api/settings/pairing", "POST");
  el("start").disabled = false;
  await refresh();
});

el("cancel").addEventListener("click", async () => {
  await api("/api/settings/pairing", "DELETE");
  await refresh();
});

el("wifi-toggle").addEventListener("click", async () => {
  const button = el("wifi-toggle");
  const enable = button.dataset.enabled !== "1";
  button.disabled = true;
  const { ok, body } = await api("/api/settings/wifi", "POST", { enabled: enable });
  button.disabled = false;
  if (!ok) {
    el("wifi-error").hidden = false;
    el("wifi-error").textContent = body?.error?.message ?? "The listener could not be started.";
  }
  await refresh();
});

refresh();
setInterval(refresh, 2000);
