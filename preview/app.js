/* ─────────────────────────────────────────────────────────────────────────
   Dues — UI preview behaviour.
   Mirrors the Compose screens in app/src/main/java/com/afec/app/ui/.
   Data is local demo data; every counter is derived from today's date.
   ───────────────────────────────────────────────────────────────────────── */

'use strict';

/* ── Icons (inline, stroke based) ───────────────────────────────────────── */
const PATHS = {
  home: '<path d="M4 10.6 12 4l8 6.6V20a1 1 0 0 1-1 1h-4.6v-6H9.6v6H5a1 1 0 0 1-1-1Z"/>',
  inbox: '<path d="M4 5.5h16v13H4z"/><path d="M4 14h4l1.6 2.6h4.8L16 14h4"/>',
  settings: '<circle cx="12" cy="12" r="3.1"/><path d="M12 3.4v2.2M12 18.4v2.2M3.4 12h2.2M18.4 12h2.2M6 6l1.6 1.6M16.4 16.4 18 18M18 6l-1.6 1.6M7.6 16.4 6 18"/>',
  add: '<path d="M12 5v14M5 12h14"/>',
  back: '<path d="M19 12H5M11 6l-6 6 6 6"/>',
  bell: '<path d="M18 15.6V11a6 6 0 1 0-12 0v4.6L4.6 18h14.8Z"/><path d="M9.6 21h4.8"/>',
  shield: '<path d="M12 3.4 5 6v6c0 4.2 2.9 7.4 7 8.6 4.1-1.2 7-4.4 7-8.6V6Z"/><path d="m9 12 2.2 2.2L15.4 10"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.4M12 7.8v.6"/>',
  check: '<circle cx="12" cy="12" r="8.5"/><path d="m8.4 12.2 2.5 2.5 4.7-5"/>',
  warning: '<path d="M12 4.4 21 19.6H3Z"/><path d="M12 9.4v4.6M12 16.8v.6"/>',
  history: '<path d="M4 12a8 8 0 1 0 2.6-5.9"/><path d="M4 4.4V10h5.6"/><path d="M12 8.4V12l2.8 1.7"/>',
  palette: '<path d="M12 3.4a8.6 8.6 0 1 0 0 17.2c1.4 0 2-.9 2-1.8 0-1.6-1.5-1.7-1.5-3.2 0-1 .8-1.8 1.8-1.8H16a4.6 4.6 0 0 0 4.6-4.6c0-3.6-3.8-5.8-8.6-5.8Z"/><circle cx="8.2" cy="10.2" r=".9"/><circle cx="12" cy="7.6" r=".9"/><circle cx="15.8" cy="9.8" r=".9"/>',
  storage: '<ellipse cx="12" cy="6.4" rx="7" ry="2.8"/><path d="M5 6.4v11.2c0 1.6 3.1 2.8 7 2.8s7-1.2 7-2.8V6.4"/><path d="M5 12c0 1.6 3.1 2.8 7 2.8s7-1.2 7-2.8"/>',
  battery: '<rect x="2.8" y="7" width="15" height="10" rx="2.6"/><path d="M21 10.4v3.2"/><path d="m10.6 8.9-2 3.6h3l-2 3.6"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  chevron: '<path d="m7 10 5 5 5-5"/>',
  edit: '<path d="M4 20h4L18 10l-4-4L4 16Z"/><path d="m14.4 5.6 4 4"/>',
  doc: '<path d="M6 3.5h7l5 5v12H6Z"/><path d="M13 3.5v5h5"/>',
  calendar: '<rect x="3.6" y="5.4" width="16.8" height="15" rx="2.6"/><path d="M3.6 10h16.8M8.4 3.4v4M15.6 3.4v4"/>',
};
const icon = (name, cls = 'icon') =>
  `<svg class="${cls}" viewBox="0 0 24 24" aria-hidden="true">${PATHS[name] || ''}</svg>`;

/* ── Date / number helpers ─────────────────────────────────────────────── */
const TODAY = (() => { const d = new Date(); d.setHours(12, 0, 0, 0); return d; })();
const dateFromOffset = (o) => { const d = new Date(TODAY); d.setDate(d.getDate() + o); return d; };
const fmtOffset = (o) => dateFromOffset(o).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
const fmtDate = (d) => d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
const isoFromOffset = (o) => {
  const d = dateFromOffset(o);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
const inr = (n) => new Intl.NumberFormat('en-IN').format(n);
const money = (n) => (n == null ? '' : `₹${inr(n)}`);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

/* ── Demo data ─────────────────────────────────────────────────────────── */
const TYPE_LABEL = {
  FD: 'Fixed Deposit', RD: 'Recurring Deposit', CARD: 'Card',
  SUBSCRIPTION: 'Subscription', OTHER: 'Other',
};

const items = [
  { id: 'hdfc',   title: 'HDFC FD Withdrawal', bank: 'HDFC Bank',    type: 'Fixed Deposit',     amount: 500000, offset: 3,   status: 'active',    source: 'SMS' },
  { id: 'amazon', title: 'Amazon Prime Renewal', bank: 'Amazon Prime', type: 'Subscription',    amount: 1499,   offset: -9,  status: 'active',    source: 'SMS' },
  { id: 'sbi',    title: 'SBI Tax Saver FD',   bank: 'SBI',          type: 'Fixed Deposit',     amount: 150000, offset: 26,  status: 'active',    source: 'SMS' },
  { id: 'icici',  title: 'ICICI Card Renewal', bank: 'ICICI Bank',   type: 'Card',              amount: null,   offset: 108, status: 'active',    source: 'Manual' },
  { id: 'kotak',  title: 'Kotak RD Maturity',  bank: 'Kotak',        type: 'Recurring Deposit', amount: 60000,  offset: -21, status: 'completed', source: 'Manual' },
  { id: 'axis',   title: 'Axis FD (old)',      bank: 'Axis Bank',    type: 'Fixed Deposit',     amount: 200000, offset: -60, status: 'archived',  source: 'Manual', archivedOffset: -19 },
];

const queueSeed = [
  {
    id: 'q1', sender: 'VM-HDFCBK', receivedOffset: 0, confidence: 72,
    raw: 'Dear Customer, your FD No XXXXX6789 maturing on 12-Nov-2026 for Rs.5,00,000 will be auto-renewed at card rate. Visit branch to change instructions. -HDFC Bank',
    parsed: { title: 'HDFC FD Withdrawal', bank: 'HDFC Bank', offset: 21, amount: 500000 },
  },
  {
    id: 'q2', sender: 'AD-ICICIB', receivedOffset: -1, confidence: 58,
    raw: 'ICICI Bank: Your Debit Card XX4412 expires on 05/2027. Please contact your branch for a replacement to avoid decline.',
    parsed: { title: 'ICICI Card Renewal', bank: 'ICICI Bank', offset: 220, amount: null },
  },
  {
    id: 'q3', sender: 'AMAZON', receivedOffset: -2, confidence: 35,
    raw: 'Your Amazon Prime membership renews soon. Manage your plan in the app.',
    parsed: null,
  },
];

/* ── State ─────────────────────────────────────────────────────────────── */
const state = {
  theme: 'system',
  dynamic: false,
  tab: 'dashboard',
  view: 'main',          // main | detail | onboarding
  detailId: null,
  sheet: null,           // { mode, heading, saveLabel, itemId, draft, error, typeMenu }
  dialog: null,          // 'reset'
  resetText: '',
  queueEmpty: false,
  queue: queueSeed.map((q) => ({ ...q, resolved: false })),
  filterType: null,
  filterBank: null,
  sort: 'date',
  onboardingStep: 0,
  showRaw: false,
  prefs: { alert14: true, alert7: true, alert3: true, dayOf: true, offsets: [21, 45] },
  offsetInput: '',
};

const $screen = document.getElementById('screen');
const $nav = document.getElementById('bottom-nav');
const $fab = document.getElementById('fab');
const $sheet = document.getElementById('sheet-root');
const $snack = document.getElementById('snackbar-host');

/* ── Theme ─────────────────────────────────────────────────────────────── */
const systemDark = window.matchMedia('(prefers-color-scheme: dark)');
function applyTheme() {
  const dark = state.theme === 'dark' || (state.theme === 'system' && systemDark.matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.documentElement.dataset.dynamic = String(state.dynamic);
  document.querySelectorAll('[data-theme-set]').forEach((b) => {
    b.setAttribute('aria-pressed', String(b.dataset.themeSet === state.theme));
  });
  const dyn = document.querySelector('[data-flag="dynamic"]');
  if (dyn) dyn.checked = state.dynamic;
}
systemDark.addEventListener('change', () => { if (state.theme === 'system') applyTheme(); });

/* ── Domain helpers ────────────────────────────────────────────────────── */
const tierOf = (item) => {
  if (item.status === 'completed') return 'completed';
  if (item.offset < 0) return 'overdue';
  if (item.offset <= 7) return 'urgent';
  if (item.offset <= 30) return 'approaching';
  return 'comfortable';
};
const tierClass = (tier) => (tier === 'overdue' ? 'tier-urgent' : `tier-${tier}`);
const tierLabel = (tier, days) => {
  switch (tier) {
    case 'overdue': return `Overdue · ${-days}d`;
    case 'urgent': return `Urgent · ${days}d`;
    case 'approaching': return `Approaching · ${days}d`;
    case 'comfortable': return `Comfortable · ${days}d`;
    default: return 'Completed';
  }
};
const dayText = (days) => {
  if (days < 0) return { n: -days, unit: 'days overdue' };
  if (days === 0) return { n: 0, unit: 'due today' };
  if (days === 1) return { n: 1, unit: 'day left' };
  return { n: days, unit: 'days left' };
};
const progressPct = (days) => Math.round((1 - Math.min(Math.max(days, 0), 90) / 90) * 100);
const metaLine = (item) => [item.bank, money(item.amount), `Maturity ${fmtOffset(item.offset)}`].filter(Boolean).join(' · ');

/* ── Shared components ─────────────────────────────────────────────────── */
function statusChip(tier, days, fromSms) {
  return `<span class="status-chip ${tierClass(tier)}"><i class="dot"></i>${tierLabel(tier, days)}${
    fromSms ? '<span class="sms-tag">· SMS</span>' : ''}</span>`;
}

function countdownCard(item) {
  const tier = tierOf(item);
  const t = dayText(item.offset);
  const done = tier === 'completed';
  const label = `${item.title}, ${item.bank}, matures ${fmtOffset(item.offset)}, ${tierLabel(tier, item.offset)}`;
  return `
    <div class="countdown-card ${tierClass(tier)}" role="button" tabindex="0"
         data-action="open-detail:${item.id}" style="--p:${progressPct(item.offset)}%"
         aria-label="${esc(label)}">
      <div class="cc-main">
        <div class="cc-accent"></div>
        <div class="cc-body">
          <div class="cc-title">${esc(item.title)}</div>
          <div class="cc-meta">${esc(metaLine(item))}</div>
          <div style="height:10px"></div>
          ${statusChip(tier, item.offset, item.source === 'SMS')}
        </div>
        <div class="cc-days">
          ${done ? `<span class="done-icon">${icon('check')}</span>` : `<b>${t.n}</b>`}
          <span class="unit">${done ? 'done' : t.unit}</span>
        </div>
      </div>
      <div class="progress"><span class="track"></span><span class="fill"></span></div>
    </div>`;
}

function sectionHead(tier, title, count) {
  return `<div class="section-head ${tierClass(tier)}">
      <span class="dot"></span><h3>${esc(title)}</h3>
      <span class="count-pill">${count}</span>
    </div>`;
}

const chip = (label, pressed, action, check) => `
  <button class="chip" data-action="${action}" aria-pressed="${pressed}">
    ${pressed && check ? icon('check', 'icon-xs') : ''}${esc(label)}
  </button>`;

/* ── Screen: Dashboard ─────────────────────────────────────────────────── */
function dashboardScreen() {
  const activeAll = items.filter((i) => i.status === 'active' || i.status === 'overdue');
  const completedAll = items.filter((i) => i.status === 'completed');
  const visible = items.filter((i) =>
    (!state.filterType || i.type === state.filterType) &&
    (!state.filterBank || i.bank === state.filterBank));
  const active = visible.filter((i) => i.status === 'active' || i.status === 'overdue');
  const completed = visible.filter((i) => i.status === 'completed');
  const archived = visible.filter((i) => i.status === 'archived');
  const sorted = [...active].sort((a, b) =>
    state.sort === 'amount' ? (b.amount || 0) - (a.amount || 0) : a.offset - b.offset);
  const nearest = [...activeAll].sort((a, b) => a.offset - b.offset)[0];
  const banks = [...new Set(items.map((i) => i.bank))].sort();
  const tierCount = (tiers) => activeAll.filter((i) => tiers.includes(tierOf(i))).length;

  const filters = items.length ? `
    <div class="stack gap-8">
      <div class="chip-row" aria-label="Filter by type">
        ${chip('All types', !state.filterType, 'filter-type:all', true)}
        ${Object.values(TYPE_LABEL).map((t) => chip(t, state.filterType === t, `filter-type:${t}`, true)).join('')}
      </div>
      ${banks.length > 1 ? `<div class="chip-row" aria-label="Filter by bank">
        ${chip('All banks', !state.filterBank, 'filter-bank:all', true)}
        ${banks.map((b) => chip(b, state.filterBank === b, `filter-bank:${b}`, true)).join('')}
      </div>` : ''}
      <div class="chip-row" aria-label="Sort">
        ${chip('By date', state.sort === 'date', 'sort:date')}
        ${chip('By amount', state.sort === 'amount', 'sort:amount')}
      </div>
    </div>` : '';

  let body = '';
  if (!items.length) {
    body = emptyState();
  } else if (!visible.length) {
    body = `
      <div class="card card-tonal">
        <div class="empty">
          <h3 style="font-size:16px;line-height:24px">No instruments match these filters</h3>
          <p class="b-medium muted">Try another type or bank, or clear the filters to see everything.</p>
          <button class="btn btn-text" data-action="clear-filters">Clear filters</button>
        </div>
      </div>`;
  } else {
    const groups = [
      ['urgent', 'Urgent & overdue (≤7 days)', sorted.filter((i) => ['urgent', 'overdue'].includes(tierOf(i)))],
      ['approaching', 'Approaching (8–30 days)', sorted.filter((i) => tierOf(i) === 'approaching')],
      ['comfortable', 'Comfortable (31+ days)', sorted.filter((i) => tierOf(i) === 'comfortable')],
      ['completed', 'Completed', completed.sort((a, b) => b.offset - a.offset)],
    ];
    body = groups.filter(([, , list]) => list.length).map(([tier, title, list]) => `
      ${sectionHead(tier, title, list.length)}
      ${list.map(countdownCard).join('')}`).join('');
    if (archived.length) {
      body += `
        <div class="section-head"><span class="dot" style="background:var(--outline)"></span>
          <h3>Archived</h3><span class="count-pill">${archived.length}</span></div>
        ${archived.map((item) => `
          <div class="card card-pad stack gap-2">
            <div class="t-medium">${esc(item.title)}</div>
            <div class="b-medium muted">Archived ${fmtOffset(item.archivedOffset)} · restorable until ${fmtOffset((item.archivedOffset ?? 0) + 30)}</div>
            <div class="row" style="gap:4px;margin-top:4px">
              <button class="btn btn-text" data-action="restore:${item.id}">Restore</button>
              <button class="btn btn-text btn-danger" data-action="delete-forever:${item.id}">Delete forever</button>
            </div>
          </div>`).join('')}`;
    }
  }

  return `
    <section class="hero">
      <div class="row-between" style="align-items:flex-start">
        <div>
          <h1 class="hero-title">Dues</h1>
          <p class="hero-sub">Every maturity, in one place</p>
        </div>
        <button class="hero-icon-btn" data-action="nav:settings" aria-label="Open settings">${icon('settings')}</button>
      </div>
      <div class="hero-stats">
        <div class="hero-stat total"><b>${activeAll.length + completedAll.length}</b><span>Total</span></div>
        <div class="hero-stat urgent"><b>${tierCount(['urgent', 'overdue'])}</b><span>≤7d</span></div>
        <div class="hero-stat approaching"><b>${tierCount(['approaching'])}</b><span>8–30d</span></div>
        <div class="hero-stat comfortable"><b>${tierCount(['comfortable'])}</b><span>31d+</span></div>
      </div>
    </section>

    ${nearest ? `<button class="chip assist" data-action="open-detail:${nearest.id}" style="align-self:flex-start">
        <span class="label">Nearest: ${esc(nearest.title)} · ${nearest.offset < 0
          ? `${-nearest.offset} days overdue`
          : nearest.offset === 0
            ? 'due today'
            : nearest.offset === 1 ? '1 day left' : `${nearest.offset} days left`}</span>
      </button>` : ''}

    ${filters}
    ${body}
    ${items.length ? '<div style="height:56px"></div>' : ''}`;
}

function emptyState() {
  return `
    <div class="card card-tonal">
      <div class="empty">
        <div class="empty-icon">${icon('add')}</div>
        <h3>Nothing tracked yet</h3>
        <p class="b-large muted">Dues watches FD maturities, card expiries and renewals from your
          financial SMS — all on-device, nothing leaves this phone.</p>
        <button class="btn btn-filled" data-action="add" style="margin-top:12px">Add your first instrument</button>
      </div>
    </div>`;
}

/* ── Screen: Queue ─────────────────────────────────────────────────────── */
function queueScreen() {
  const pending = state.queue.filter((q) => !q.resolved);
  const showEmpty = state.queueEmpty || pending.length === 0;

  const card = (q) => {
    const c = q.confidence;
    const tone = c >= 85 ? 'tier-comfortable' : c >= 65 ? 'tier-medium' : 'tier-urgent';
    const level = c >= 85 ? 'High' : c >= 65 ? 'Medium' : 'Low';
    const parsed = q.parsed
      ? esc([q.parsed.title, q.parsed.bank, fmtOffset(q.parsed.offset), money(q.parsed.amount)].filter(Boolean).join(' · '))
      : '';
    return `
      <div class="card card-elev card-pad stack gap-8">
        <div class="row-between">
          <div class="grow">
            <div class="t-medium" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(q.sender)}</div>
            <div class="l-small muted">Received ${fmtOffset(q.receivedOffset)}</div>
          </div>
          <span class="status-chip ${tone}">${c >= 85 ? icon('check', 'icon-xs') : c >= 65 ? icon('info', 'icon-xs') : icon('warning', 'icon-xs')}${level} · ${c}</span>
        </div>
        <div>
          <div class="l-small muted" style="margin-bottom:4px">Raw SMS</div>
          <p class="b-medium">${esc(q.raw)}</p>
        </div>
        <div>
          <div class="l-small muted" style="margin-bottom:4px">Parsed</div>
          ${q.parsed
            ? `<p class="b-large">${parsed}</p>`
            : `<p class="b-medium muted">No reliable date found — add the details manually or dismiss.</p>`}
        </div>
        <div style="height:4px"></div>
        ${q.parsed
          ? `<button class="btn btn-filled btn-block" data-action="approve:${q.id}">Approve &amp; Add</button>
             <div class="btn-row">
               <button class="btn btn-tonal" data-action="edit-queue:${q.id}">Edit</button>
               <button class="btn btn-outlined" data-action="dismiss:${q.id}">Dismiss</button>
             </div>`
          : `<button class="btn btn-filled btn-block" data-action="edit-queue:${q.id}">Add manually</button>
             <button class="btn btn-outlined btn-block" data-action="dismiss:${q.id}">Dismiss</button>`}
      </div>`;
  };

  return `
    <h1 class="h-headline">SMS Verification Queue</h1>
    <p class="b-medium muted" style="margin-top:-6px">Medium-confidence parses wait here so false positives never reach your dashboard.</p>
    ${showEmpty
      ? `<div class="card card-tonal">
           <div class="empty">
             <div class="empty-icon success">${icon('check')}</div>
             <h3>You're all caught up</h3>
             <p class="b-large muted">No SMS are waiting for review. New medium-confidence parses will appear here.</p>
           </div>
         </div>`
      : pending.map(card).join('')}`;
}

/* ── Screen: Detail ────────────────────────────────────────────────────── */
function detailScreen() {
  const item = items.find((i) => i.id === state.detailId) || items[0];
  const tier = tierOf(item);
  const t = dayText(item.offset);
  const done = tier === 'completed';
  const p = progressPct(item.offset);
  const C = 2 * Math.PI * 78;
  const dash = `${C} ${C}`;
  const offset = C * (1 - p / 100);

  return `
    <div class="detail-head">
      <button class="icon-btn" data-action="back" aria-label="Back">${icon('back')}</button>
      <div class="detail-title grow">${esc(item.title)}</div>
    </div>
    <div class="detail-body">
      <div class="card card-elev ${tierClass(tier)}" style="padding:24px 20px">
        <div class="ring-wrap">
          <svg class="ring" viewBox="0 0 180 180" aria-hidden="true">
            <circle class="ring-track" cx="90" cy="90" r="78"></circle>
            <circle class="ring-fill" cx="90" cy="90" r="78"
                    transform="rotate(-90 90 90)"
                    stroke-dasharray="${dash}" stroke-dashoffset="${done ? 0 : offset}"></circle>
          </svg>
          <div class="ring-center">
            ${done ? `${icon('check')}<span>done</span>` : `<b>${t.n}</b><span>${t.unit}</span>`}
          </div>
        </div>
        <div class="stack gap-4" style="align-items:center;margin-top:16px;text-align:center">
          <div class="t-medium">Maturity: ${fmtOffset(item.offset)}</div>
          <div class="b-medium muted">${esc([item.bank, item.type, money(item.amount)].filter(Boolean).join(' · '))}</div>
          <div class="b-medium muted">Source: ${item.source === 'SMS' ? 'SMS-parsed' : 'Manual entry'}${
            item.source === 'SMS' ? ' · Confidence 92' : ''}</div>
        </div>
      </div>

      <div class="card card-tonal card-pad-lg stack gap-2">
        <div class="row gap-8" style="margin-bottom:2px">
          <span style="color:var(--primary)">${icon('history', 'icon-sm')}</span>
          <span class="t-medium">History</span>
        </div>
        <div class="b-medium">Created: ${fmtOffset(-64)}, 10:24</div>
        <div class="b-medium">Last edited: ${fmtOffset(-12)}, 18:02</div>
        ${item.source === 'SMS'
          ? `<button class="btn btn-text" style="align-self:flex-start;padding:0;height:40px" data-action="toggle-raw">
               ${state.showRaw ? 'Hide raw SMS' : 'Show raw SMS (audit trail)'}</button>
             ${state.showRaw
               ? `<p class="b-medium" style="margin:0">Dear Customer, your FD No XXXXX6789 maturing on ${fmtOffset(item.offset)} for Rs.${inr(item.amount || 0)} will be auto-renewed at card rate.</p>
                  <p class="l-small muted" style="margin:0">From VM-HDFCBK · raw SMS is auto-purged after 30 days</p>`
               : ''}`
          : `<p class="b-medium muted" style="margin:0">Manual entry — no source SMS.</p>`}
      </div>

      <div class="btn-row">
        <button class="btn btn-filled" data-action="renew:${item.id}">Renew (+1 yr)</button>
        <button class="btn btn-tonal" data-action="edit:${item.id}">Edit</button>
      </div>
      <div class="btn-row">
        <button class="btn btn-outlined" data-action="complete:${item.id}" ${done ? 'disabled' : ''}>Mark Completed</button>
        <button class="btn btn-text btn-danger" data-action="archive:${item.id}">Archive</button>
      </div>
      <div style="height:8px"></div>
    </div>`;
}

/* ── Screen: Settings ──────────────────────────────────────────────────── */
function settingsScreen() {
  const p = state.prefs;
  const sw = (key) => `<label class="switch"><input type="checkbox" data-pref="${key}" ${p[key] ? 'checked' : ''}><i></i></label>`;
  const offsetChips = p.offsets.length
    ? `<div class="offset-row">${p.offsets.map((o) => `
        <button class="offset-chip" data-action="remove-offset:${o}" aria-label="Remove ${o} day alert">
          ${o} days ${icon('close', 'icon-xs')}
        </button>`).join('')}</div>`
    : `<p class="l-small muted" style="margin:0">None yet — add an interval below.</p>`;
  const valid = /^\d+$/.test(state.offsetInput) && +state.offsetInput >= 1 && +state.offsetInput <= 365;
  const invalid = state.offsetInput !== '' && !valid;

  return `
    <h1 class="h-headline">Settings</h1>

    <div class="settings-card">
      <div class="section-title">${icon('bell')}<h4>Milestone alerts (9:00 AM)</h4></div>
      <div class="toggle-row"><span>14 Days Out</span>${sw('alert14')}</div>
      <div class="toggle-row"><span>7 Days Out</span>${sw('alert7')}</div>
      <div class="toggle-row"><span>3 Days Out</span>${sw('alert3')}</div>
      <div class="toggle-row"><span>Day-Of (9:00 AM)</span>${sw('dayOf')}</div>
      <div style="height:8px"></div>
      <div class="t-small">Custom day offsets</div>
      ${offsetChips}
      <div class="input-row">
        <div class="grow">
          <div class="field ${invalid ? 'invalid' : ''}">
            <label>Add custom day offset</label>
            <input type="text" inputmode="numeric" data-field="offsetInput" value="${esc(state.offsetInput)}" placeholder="e.g. 30" />
          </div>
          <div class="field-help error" data-help="offset" style="display:${invalid ? 'block' : 'none'}">Enter a value between 1 and 365</div>
        </div>
        <button class="btn btn-filled" data-action="add-offset" ${valid ? '' : 'disabled'} style="margin-top:4px">Add</button>
      </div>
      <div style="height:8px"></div>
      <p class="l-small muted" style="margin:0;line-height:16px">
        Channels: Urgent (day-of &amp; ≤3d, chime + vibration) · Standard (7/14d) ·
        Parsing Activity (silent). Same-day alerts are grouped into one summary.
      </p>
    </div>

    <div class="banner error">
      <div class="banner-head">${icon('battery', 'icon-sm')}<h4>Battery optimization may delay alerts</h4></div>
      <p>Whitelist Dues so 9:00 AM milestone alarms fire on time.</p>
      <button class="btn btn-filled" data-action="battery" style="margin-top:12px;background:var(--error);color:var(--on-error)">Fix it</button>
    </div>

    <div class="settings-card">
      <div class="section-title">${icon('palette')}<h4>Appearance</h4></div>
      <div class="chip-row">
        ${['System', 'Light', 'Dark'].map((label) => {
          const key = label.toLowerCase();
          return `<button class="chip" data-action="theme:${key}" aria-pressed="${state.theme === key}">
            ${state.theme === key ? icon('check', 'icon-xs') : ''}${label}</button>`;
        }).join('')}
      </div>
      <div class="toggle-row" style="margin-top:8px">
        <span style="display:block">
          <span class="b-large" style="display:block">Use wallpaper colours</span>
          <span class="b-medium muted">Material You — replaces the indigo/teal palette.</span>
        </span>
        <label class="switch"><input type="checkbox" data-flag="dynamic" ${state.dynamic ? 'checked' : ''}><i></i></label>
      </div>
    </div>

    <div class="settings-card">
      <div class="section-title">${icon('storage')}<h4>Data</h4></div>
      <button class="btn btn-outlined btn-block" data-action="rescan">Rescan last 90 days of SMS</button>
      <button class="btn btn-outlined btn-block" data-action="export-csv">Export to CSV</button>
      <button class="btn btn-outlined btn-block" data-action="export-pdf">Export to PDF</button>
      <p class="l-small muted" style="margin:4px 0 0;line-height:16px">
        Fully offline: Dues requests no internet permission and stores everything in a local
        Room database. Raw SMS are auto-purged after 30 days.
      </p>
      <button class="btn btn-text btn-danger" style="align-self:flex-start" data-action="reset-open">Reset Local Data</button>
    </div>
    <div style="height:8px"></div>`;
}

/* ── Screen: Onboarding ────────────────────────────────────────────────── */
const SLIDES = [
  { icon: 'bell', title: 'Never miss a maturity again', body: 'Dues turns scattered bank SMS into one visual countdown, so an FD never quietly rolls over at a bad rate.' },
  { icon: 'shield', title: 'Private by design', body: 'Everything is parsed and stored only on this phone. No accounts, no cloud sync, no internet permission.' },
  { icon: 'info', title: 'Alerts before it matters', body: 'Cascading reminders 14, 7 and 3 days before — and on the day — each instrument matures or renews.' },
];
const TOTAL_STEPS = SLIDES.length + 1;

function onboardingScreen() {
  const step = state.onboardingStep;
  const isPermission = step >= SLIDES.length;
  const slide = SLIDES[Math.min(step, SLIDES.length - 1)];

  const content = isPermission
    ? `<h2 class="h-headline">Why Dues asks for SMS access</h2>
       <div class="card card-tonal card-pad-lg stack gap-12" style="margin-top:12px">
         <p class="b-large">Bank SMS contain your FD maturity, card expiry and renewal dates. Dues reads
           them only on this device to build your countdown — messages are never uploaded, shared or
           used for anything else. Raw SMS are deleted after 30 days.</p>
         <p class="b-medium muted">You can decline and use Manual Entry instead — it is fully supported.</p>
       </div>`
    : `<h2 class="h-headline">${slide.title}</h2>
       <p class="b-large muted" style="margin-top:12px">${slide.body}</p>`;

  return `
    <div class="stack gap-0" style="flex:1 1 auto">
      <div style="height:8px"></div>
      <div class="hero" style="padding:24px">
        <div class="row gap-16">
          <span style="color:#fff">${icon(isPermission ? 'shield' : slide.icon, 'icon').replace('class="icon"', 'class="icon" style="width:44px;height:44px"')}</span>
          <div>
            <div class="hero-title">Dues</div>
            <div class="hero-sub">Every maturity, in one place</div>
          </div>
        </div>
      </div>
      <div style="height:32px"></div>
      ${content}
      <div style="height:24px"></div>
    </div>

    <div class="stack gap-8">
      <div class="row" style="justify-content:center;gap:8px" aria-hidden="true">
        ${Array.from({ length: TOTAL_STEPS }, (_, i) => `
          <span style="display:block;height:8px;width:${i === step ? 24 : 8}px;border-radius:4px;
            background:${i === step ? 'var(--primary)' : 'var(--on-surface-variant)'};opacity:${i === step ? 1 : 0.35};
            transition:width .3s ease"></span>`).join('')}
      </div>
      <div class="row-between">
        <span class="l-small muted">Step ${Math.min(step + 1, TOTAL_STEPS)} of ${TOTAL_STEPS}</span>
        ${step > 0 ? `<button class="btn btn-text" style="height:40px" data-action="onboarding-back">Back</button>` : ''}
      </div>
      ${isPermission
        ? `<button class="btn btn-filled btn-block" data-action="onboarding-allow">Allow SMS &amp; notifications</button>
           <button class="btn btn-outlined btn-block" data-action="onboarding-skip">Continue without SMS access</button>`
        : `<button class="btn btn-filled btn-block" data-action="onboarding-next">Continue</button>
           ${step === SLIDES.length - 1 ? `<button class="btn btn-text btn-block" data-action="onboarding-skip">Skip — I'll add instruments manually</button>` : ''}`}
    </div>`;
}

/* ── Bottom sheet ──────────────────────────────────────────────────────── */
function openSheet(mode, itemId, extra = {}) {
  const item = items.find((i) => i.id === itemId);
  let draft;
  if (mode === 'new') {
    draft = { title: '', institution: '', type: 'Fixed Deposit', amount: '', notes: '', dateISO: isoFromOffset(30), customAlerts: false, offsetsText: '' };
  } else if (mode === 'review') {
    const q = state.queue.find((x) => x.id === itemId);
    const p = q.parsed || {};
    draft = {
      title: p.title || `Review ${q.sender}`, institution: p.bank || q.sender,
      type: 'Fixed Deposit', amount: p.amount != null ? String(p.amount) : '',
      notes: '', dateISO: isoFromOffset(p.offset ?? 30), customAlerts: false, offsetsText: '',
    };
  } else {
    draft = {
      title: item.title, institution: item.bank, type: item.type,
      amount: item.amount != null ? String(item.amount) : '', notes: '',
      dateISO: isoFromOffset(item.offset), customAlerts: false, offsetsText: '',
    };
  }
  state.sheet = {
    mode, itemId, draft, error: null, typeMenu: false,
    heading: extra.heading || (mode === 'new' ? 'Add instrument' : mode === 'review' ? 'Review parsed SMS' : 'Edit instrument'),
    saveLabel: extra.saveLabel || (mode === 'review' ? 'Approve & Add' : 'Save'),
  };
  render();
}

function closeSheet() {
  const root = $sheet;
  root.classList.remove('open');
  setTimeout(() => { state.sheet = null; render(); }, 220);
}

function sheetHtml() {
  const s = state.sheet;
  if (!s) return '';
  const d = s.draft;
  const types = Object.values(TYPE_LABEL);
  return `
    <div class="sheet-scrim" data-action="sheet-close"></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-label="${esc(s.heading)}">
      <div class="sheet-handle"></div>
      <div class="stack">
        <h3>${esc(s.heading)}</h3>
        <div class="field ${s.error === 'title' ? 'invalid' : ''}">
          <label>Title</label>
          <input value="${esc(d.title)}" data-draft="title" placeholder="e.g. HDFC FD withdrawal" />
        </div>
        ${s.error === 'title' ? '<div class="field-help error">Title is required</div>' : ''}
        <div class="field ${s.error === 'institution' ? 'invalid' : ''}">
          <label>Institution</label>
          <input value="${esc(d.institution)}" data-draft="institution" />
        </div>
        ${s.error === 'institution' ? '<div class="field-help error">Institution is required</div>' : ''}
        <div class="field select" data-action="type-menu">
          <label>Type</label>
          <span class="b-large">${esc(d.type)}</span>
          ${icon('chevron')}
        </div>
        ${s.typeMenu ? `<div class="menu">${types.map((t) =>
          `<button data-action="type-pick:${t}">${esc(t)}</button>`).join('')}</div>` : ''}
        <div class="field ${s.error === 'amount' ? 'invalid' : ''}">
          <label>Principal amount (₹, optional)</label>
          <input value="${esc(d.amount)}" inputmode="decimal" data-draft="amount" />
        </div>
        ${s.error === 'amount' ? '<div class="field-help error">Amount must be a number</div>' : ''}
        <div class="field date">
          <label>Maturity date</label>
          <span class="date-value">${fmtDate(new Date(d.dateISO + 'T12:00:00'))}</span>
          ${icon('calendar', 'icon-xs date-hint')}
          <input type="date" value="${d.dateISO}" data-draft="dateISO" aria-label="Maturity date" />
        </div>
        <div class="field">
          <label>Notes (optional)</label>
          <input value="${esc(d.notes)}" data-draft="notes" />
        </div>
        <div class="row-between" style="gap:12px;padding:4px 0">
          <div class="grow">
            <div class="b-large">Custom alerts for this item</div>
            <div class="l-small muted" style="font-size:12px;letter-spacing:0">
              ${d.customAlerts ? 'Uses the days below instead of the global cascade.' : 'Off — inherits your global milestones.'}
            </div>
          </div>
          <label class="switch"><input type="checkbox" data-draft-toggle="customAlerts" ${d.customAlerts ? 'checked' : ''}><i></i></label>
        </div>
        ${d.customAlerts ? `<div class="field"><label>Alert days before (e.g. 21, 14, 7, 0)</label>
          <input value="${esc(d.offsetsText)}" data-draft="offsetsText" inputmode="numeric" /></div>` : ''}
        <div class="sheet-actions">
          <button class="btn btn-outlined" data-action="sheet-close">Cancel</button>
          <button class="btn btn-filled" data-action="sheet-save">${esc(s.saveLabel)}</button>
        </div>
        <div style="height:8px"></div>
      </div>
    </div>`;
}

function saveSheet() {
  const s = state.sheet;
  const d = s.draft;
  if (!d.title.trim()) { s.error = 'title'; render(); return; }
  if (!d.institution.trim()) { s.error = 'institution'; render(); return; }

  const rawAmount = d.amount.trim();
  const amount = rawAmount === '' ? null : Number(rawAmount.replace(/[^\d.]/g, ''));
  if (rawAmount !== '' && (amount === null || Number.isNaN(amount))) { s.error = 'amount'; render(); return; }
  const offset = Math.round((new Date(`${d.dateISO}T12:00:00`) - TODAY) / 86400000);

  if (s.mode === 'new') {
    items.push({
      id: `n${Date.now()}`, title: d.title.trim(), bank: d.institution.trim(), type: d.type,
      amount: Number.isFinite(amount) ? amount : null, offset, status: 'active', source: 'Manual',
    });
    toast(`Saved ${d.title.trim()}`);
  } else if (s.mode === 'edit') {
    const item = items.find((i) => i.id === s.itemId);
    Object.assign(item, {
      title: d.title.trim(), bank: d.institution.trim(), type: d.type,
      amount: Number.isFinite(amount) ? amount : null, offset,
    });
    toast(`Saved ${d.title.trim()}`);
  } else if (s.mode === 'review') {
    items.push({
      id: `q${Date.now()}`, title: d.title.trim(), bank: d.institution.trim(), type: d.type,
      amount: Number.isFinite(amount) ? amount : null, offset, status: 'active', source: 'SMS',
    });
    const q = state.queue.find((x) => x.id === s.itemId);
    if (q) q.resolved = true;
    toast('Added to Dashboard');
  }
  closeSheet();
}

/* ── Dialog ────────────────────────────────────────────────────────────── */
function dialogHtml() {
  if (state.dialog !== 'reset') return '';
  const ok = state.resetText === 'RESET';
  return `
    <div class="dialog-scrim" data-action="reset-cancel"></div>
    <div class="dialog" role="dialog" aria-modal="true" aria-label="Reset Local Data">
      <h3>Reset Local Data?</h3>
      <p class="b-medium muted">This permanently erases every instrument, SMS audit record and
        alert on this device. Type RESET to confirm.</p>
      <div class="field" style="margin-top:12px"><label>Type RESET</label>
        <input data-field="resetText" value="${esc(state.resetText)}" autocomplete="off" /></div>
      <div class="dialog-actions">
        <button class="btn btn-text" data-action="reset-cancel">Cancel</button>
        <button class="btn btn-text btn-danger" data-action="reset-confirm" ${ok ? '' : 'disabled'}>Erase everything</button>
      </div>
    </div>`;
}

/* ── Snackbar ──────────────────────────────────────────────────────────── */
let snackTimer;
function toast(message) {
  clearTimeout(snackTimer);
  $snack.innerHTML = `<div class="snackbar">${esc(message)}</div>`;
  snackTimer = setTimeout(() => { $snack.innerHTML = ''; }, 2800);
}

/* ── Render ────────────────────────────────────────────────────────────── */
let lastKey = '';
let savedScroll = 0;

function render() {
  const key = `${state.view}|${state.tab}|${state.detailId}`;
  if (key === lastKey) savedScroll = $screen.scrollTop;

  $screen.className = 'screen';
  $screen.innerHTML = state.view === 'onboarding'
    ? onboardingScreen()
    : state.view === 'detail'
      ? detailScreen()
      : state.tab === 'dashboard'
        ? dashboardScreen()
        : state.tab === 'queue'
          ? queueScreen()
          : settingsScreen();

  if (state.view === 'detail') $screen.classList.add('detail-mode');
  if (state.view === 'main' && state.tab === 'dashboard') $screen.classList.add('has-fab');

  const showNav = state.view === 'main';
  $nav.hidden = !showNav;
  if (showNav) {
    const pending = state.queue.filter((q) => !q.resolved).length;
    $nav.innerHTML = `
      <button class="nav-item" data-action="nav:dashboard" ${state.tab === 'dashboard' ? 'aria-current="page"' : ''}>
        <span class="icon-wrap">${icon('home')}</span><span>Dashboard</span>
      </button>
      <button class="nav-item" data-action="nav:queue" ${state.tab === 'queue' ? 'aria-current="page"' : ''}>
        <span class="icon-wrap">${icon('inbox')}${!state.queueEmpty && pending ? `<span class="nav-badge">${pending > 99 ? '99+' : pending}</span>` : ''}</span>
        <span>Inbox</span>
      </button>
      <button class="nav-item" data-action="nav:settings" ${state.tab === 'settings' ? 'aria-current="page"' : ''}>
        <span class="icon-wrap">${icon('settings')}</span><span>Settings</span>
      </button>`;
  }

  $fab.hidden = !(state.view === 'main' && state.tab === 'dashboard');

  // Sheet
  if (state.sheet) {
    $sheet.innerHTML = sheetHtml();
    requestAnimationFrame(() => $sheet.classList.add('open'));
  } else {
    $sheet.classList.remove('open');
    $sheet.innerHTML = '';
  }

  // Dialog lives inside the sheet root-less overlay
  let dialog = document.getElementById('dialog-root');
  if (!dialog) {
    dialog = document.createElement('div');
    dialog.id = 'dialog-root';
    document.getElementById('device').appendChild(dialog);
  }
  dialog.innerHTML = dialogHtml();

  if (key === lastKey) $screen.scrollTop = savedScroll;
  else $screen.scrollTop = 0;
  lastKey = key;
}

/* ── Actions ───────────────────────────────────────────────────────────── */
function act(action) {
  const [cmd, arg = ''] = action.split(':');
  switch (cmd) {
    case 'nav':
      state.tab = arg; state.view = 'main'; state.detailId = null; render(); break;
    case 'open-detail':
      state.detailId = arg; state.view = 'detail'; render(); break;
    case 'back':
      state.view = 'main'; state.detailId = null; render(); break;
    case 'add':
      state.view = 'main'; state.tab = 'dashboard'; state.detailId = null;
      openSheet('new'); break;
    case 'edit':
      openSheet('edit', arg); break;
    case 'edit-queue': {
      const q = state.queue.find((x) => x.id === arg);
      if (q && q.parsed) openSheet('review', arg, { heading: 'Review parsed SMS', saveLabel: 'Approve & Add' });
      else openSheet('new');
      break;
    }
    case 'approve': {
      const q = state.queue.find((x) => x.id === arg);
      if (q) {
        q.resolved = true;
        if (q.parsed) {
          items.push({
            id: `q${Date.now()}`, title: q.parsed.title, bank: q.parsed.bank, type: 'Fixed Deposit',
            amount: q.parsed.amount, offset: q.parsed.offset, status: 'active', source: 'SMS',
          });
        }
        toast('Added to Dashboard');
      }
      render(); break;
    }
    case 'dismiss': {
      const q = state.queue.find((x) => x.id === arg);
      if (q) q.resolved = true;
      render(); break;
    }
    case 'filter-type':
      state.filterType = arg === 'all' ? null : arg; render(); break;
    case 'filter-bank':
      state.filterBank = arg === 'all' ? null : arg; render(); break;
    case 'sort':
      state.sort = arg; render(); break;
    case 'clear-filters':
      state.filterType = null; state.filterBank = null; render(); break;
    case 'restore': {
      const item = items.find((i) => i.id === arg);
      if (item) { item.status = 'active'; delete item.archivedOffset; toast('Restored'); }
      render(); break;
    }
    case 'delete-forever': {
      const idx = items.findIndex((i) => i.id === arg);
      if (idx >= 0) items.splice(idx, 1);
      toast('Deleted permanently'); render(); break;
    }
    case 'complete': {
      const item = items.find((i) => i.id === arg);
      if (item) { item.status = 'completed'; toast('Marked completed'); }
      state.view = 'main'; state.detailId = null; render(); break;
    }
    case 'renew': {
      const item = items.find((i) => i.id === arg);
      if (item) { item.offset += 365; item.status = 'active'; toast('Rolled over to next year'); }
      render(); break;
    }
    case 'archive': {
      const item = items.find((i) => i.id === arg);
      if (item) { item.status = 'archived'; item.archivedOffset = 0; toast('Archived (recoverable for 30 days)'); }
      state.view = 'main'; state.detailId = null; render(); break;
    }
    case 'toggle-raw':
      state.showRaw = !state.showRaw; render(); break;
    case 'onboarding-next':
      state.onboardingStep = Math.min(state.onboardingStep + 1, TOTAL_STEPS - 1); render(); break;
    case 'onboarding-back':
      state.onboardingStep = Math.max(0, state.onboardingStep - 1); render(); break;
    case 'onboarding-skip':
      state.view = 'main'; state.tab = 'dashboard'; state.onboardingStep = 0; render(); break;
    case 'onboarding-allow':
      state.view = 'main'; state.tab = 'dashboard'; state.onboardingStep = 0;
      toast('Scan complete — check Dashboard & Inbox'); render(); break;
    case 'theme':
      state.theme = arg; applyTheme(); render(); break;
    case 'sheet-close':
      closeSheet(); break;
    case 'sheet-save':
      saveSheet(); break;
    case 'type-menu':
      state.sheet.typeMenu = !state.sheet.typeMenu; render(); break;
    case 'type-pick':
      state.sheet.draft.type = arg; state.sheet.typeMenu = false; render(); break;
    case 'add-offset': {
      const n = parseInt(state.offsetInput, 10);
      if (n >= 1 && n <= 365) {
        state.prefs.offsets = [...new Set([...state.prefs.offsets, n])].sort((a, b) => a - b);
        state.offsetInput = '';
        toast('Alert settings saved');
      }
      render(); break;
    }
    case 'remove-offset': {
      const n = parseInt(arg, 10);
      state.prefs.offsets = state.prefs.offsets.filter((o) => o !== n);
      toast('Alert settings saved'); render(); break;
    }
    case 'reset-open':
      state.dialog = 'reset'; state.resetText = ''; render(); break;
    case 'reset-cancel':
      state.dialog = null; state.resetText = ''; render(); break;
    case 'reset-confirm':
      if (state.resetText === 'RESET') {
        items.length = 0;
        state.queue.forEach((q) => { q.resolved = true; });
        state.dialog = null; state.resetText = '';
        toast('All local data erased');
        render();
      }
      break;
    case 'rescan':
      toast('Scan complete — no financial expiry SMS found'); break;
    case 'export-csv':
      toast('Export Dues data'); break;
    case 'export-pdf':
      toast('Export Dues data'); break;
    case 'battery':
      toast('Would open system battery settings'); break;
    default:
      break;
  }
}

/* ── Events ────────────────────────────────────────────────────────────── */
document.addEventListener('keydown', (event) => {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  const el = event.target.closest('[role="button"][data-action]');
  if (!el) return;
  event.preventDefault();
  act(el.dataset.action);
});

document.addEventListener('click', (event) => {
  const el = event.target.closest('[data-action]');
  if (!el) return;
  event.preventDefault();
  act(el.dataset.action);
});

document.addEventListener('change', (event) => {
  const el = event.target;

  if (el.dataset.pref) {
    state.prefs[el.dataset.pref] = el.checked;
    toast('Alert settings saved');
    return;
  }
  if (el.dataset.flag === 'dynamic') {
    state.dynamic = el.checked;
    applyTheme();
    render();
    return;
  }
  if (el.dataset.flag === 'queueEmpty') {
    state.queueEmpty = el.checked;
    render();
    return;
  }
  if (el.dataset.draftToggle === 'customAlerts') {
    state.sheet.draft.customAlerts = el.checked;
    render();
    return;
  }
  if (el.dataset.draft) {
    state.sheet.draft[el.dataset.draft] = el.value;
    return;
  }
  if (el.dataset.field === 'resetText') {
    state.resetText = el.value;
    render();
    return;
  }
  if (el.dataset.field === 'offsetInput') {
    state.offsetInput = el.value.replace(/\D/g, '');
    validateOffsetInput();
  }
});

document.addEventListener('input', (event) => {
  const el = event.target;
  if (el.dataset.draft) {
    state.sheet.draft[el.dataset.draft] = el.value;
    return;
  }
  if (el.dataset.field === 'resetText') {
    state.resetText = el.value;
    const btn = document.querySelector('[data-action="reset-confirm"]');
    if (btn) btn.disabled = state.resetText !== 'RESET';
    return;
  }
  if (el.dataset.field === 'offsetInput') {
    state.offsetInput = el.value.replace(/\D/g, '');
    validateOffsetInput();
  }
});

function validateOffsetInput() {
  const input = document.querySelector('[data-field="offsetInput"]');
  if (!input) return;
  const n = parseInt(state.offsetInput, 10);
  const valid = /^\d+$/.test(state.offsetInput) && n >= 1 && n <= 365;
  const invalid = state.offsetInput !== '' && !valid;
  const field = input.closest('.field');
  field.classList.toggle('invalid', invalid);
  const help = document.querySelector('[data-help="offset"]');
  if (help) help.style.display = invalid ? 'block' : 'none';
  const add = document.querySelector('[data-action="add-offset"]');
  if (add) add.disabled = !valid;
}

/* Meta panel controls */
document.querySelectorAll('[data-theme-set]').forEach((btn) => {
  btn.addEventListener('click', () => { state.theme = btn.dataset.themeSet; applyTheme(); render(); });
});
document.querySelectorAll('[data-jump]').forEach((btn) => {
  btn.addEventListener('click', () => {
    const jump = btn.dataset.jump;
    state.dialog = null;
    if (jump === 'onboarding') { state.view = 'onboarding'; state.onboardingStep = 0; render(); return; }
    if (jump === 'detail') { state.view = 'detail'; state.detailId = 'hdfc'; render(); return; }
    if (jump === 'sheet') { state.view = 'main'; state.tab = 'dashboard'; openSheet('new'); return; }
    state.view = 'main';
    state.tab = jump === 'queue' ? 'queue' : jump === 'settings' ? 'settings' : 'dashboard';
    state.detailId = null;
    render();
  });
});

/* Keyboard escape closes overlays */
document.addEventListener('keydown', (event) => {
  if (event.key !== 'Escape') return;
  if (state.sheet) { closeSheet(); return; }
  if (state.dialog) { state.dialog = null; state.resetText = ''; render(); return; }
  if (state.view === 'detail') { state.view = 'main'; state.detailId = null; render(); }
});

/* ── Device scaling ────────────────────────────────────────────────────── */
function fitDevice() {
  const wrap = document.querySelector('.device-wrap');
  if (!wrap) return;
  if (window.matchMedia('(max-width: 640px), (max-height: 560px)').matches) {
    wrap.style.setProperty('--scale', '1');
    return;
  }
  const metaWidth = window.matchMedia('(max-width: 900px)').matches ? 0 : 416;
  const availableW = window.innerWidth - metaWidth - 48;
  const availableH = window.innerHeight - 96;
  const scale = Math.min(1, availableH / 844, availableW / 390);
  wrap.style.setProperty('--scale', String(Math.max(0.55, scale)));
}
window.addEventListener('resize', fitDevice);

/* ── Boot ──────────────────────────────────────────────────────────────── */
applyTheme();
render();
fitDevice();
