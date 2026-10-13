// Mandi Price Tracker front end: a hash-routed single page app over the REST API. No build step.
import { api, ApiError, getToken, setToken, onUnauthorized } from "./api.js";
import { rupees, perKg, shortDate, longDate, dateTime, change, direction, esc } from "./format.js";
import { priceChart, sparkline, monthlyChart } from "./charts.js";

const view = document.getElementById("view");
const state = { user: null, catalog: null, watch: new Map() };
const RANGES = [30, 90, 180, 365];

// ---------------------------------------------------------------- helpers
function toast(message, kind = "") {
  const t = document.createElement("div");
  t.className = `toast ${kind}`;
  t.textContent = message;
  document.getElementById("toasts").appendChild(t);
  setTimeout(() => t.remove(), 4000);
}

function parseHash() {
  const raw = location.hash.replace(/^#/, "") || "/";
  const [path, query = ""] = raw.split("?");
  return { parts: path.split("/").filter(Boolean), params: new URLSearchParams(query) };
}

const seriesKey = (c, m) => `${c}:${m}`;
const seriesLink = (c, m, extra = "") => `#/c/${c}?m=${m}${extra}`;

async function catalog(force = false) {
  if (!state.catalog || force) state.catalog = await api("/api/prices/catalog", { auth: false });
  return state.catalog;
}

async function loadWatch() {
  state.watch.clear();
  if (!state.user) return;
  for (const w of await api("/api/watchlist")) {
    if (w.latest) state.watch.set(seriesKey(w.latest.commodityId, w.latest.marketId), w.id);
  }
}

async function refreshUnread() {
  const badge = document.getElementById("unread");
  if (!state.user) { badge.hidden = true; return; }
  try {
    const inbox = await api("/api/notifications");
    badge.textContent = inbox.unread;
    badge.hidden = inbox.unread === 0;
  } catch { /* the badge is optional */ }
}

function requireLogin() {
  if (state.user) return true;
  location.hash = `#/login?next=${encodeURIComponent(location.hash || "#/")}`;
  return false;
}

/** Star button: add or remove a series from the watchlist. */
async function toggleWatch(button) {
  if (!requireLogin()) return;
  const c = Number(button.dataset.c);
  const m = Number(button.dataset.m);
  const key = seriesKey(c, m);
  button.disabled = true;
  try {
    if (state.watch.has(key)) {
      await api(`/api/watchlist/${state.watch.get(key)}`, { method: "DELETE" });
      state.watch.delete(key);
      toast("Removed from your watchlist");
    } else {
      const card = await api("/api/watchlist", { method: "POST", body: { commodityId: c, marketId: m } });
      state.watch.set(key, card.id);
      toast("Added to your watchlist");
    }
    syncStars();
  } catch (e) {
    toast(e.message, "error");
  } finally {
    button.disabled = false;
  }
}

function starButton(c, m, label) {
  return `<button class="star" type="button" data-star data-c="${c}" data-m="${m}" aria-pressed="false" aria-label="Watch ${esc(label)}">☆</button>`;
}

function syncStars() {
  for (const b of view.querySelectorAll("[data-star]")) {
    const on = state.watch.has(seriesKey(b.dataset.c, b.dataset.m));
    b.setAttribute("aria-pressed", String(on));
    b.textContent = on ? "★" : "☆";
    if (b.classList.contains("btn")) b.textContent = on ? "★ Watching" : "☆ Watch";
  }
}

view.addEventListener("click", (ev) => {
  const star = ev.target.closest("[data-star]");
  if (star) { ev.stopPropagation(); toggleWatch(star); }
});

function setActiveNav(name) {
  for (const a of document.querySelectorAll("[data-nav]")) a.classList.toggle("active", a.dataset.nav === name);
}

function updateHeader() {
  document.getElementById("login-link").hidden = !!state.user;
  document.getElementById("logout").hidden = !state.user;
  document.getElementById("admin-link").hidden = state.user?.role !== "ADMIN";
}

function emptyState(title, body, action = "") {
  return `<div class="card empty"><h2>${title}</h2><p>${body}</p>${action}</div>`;
}

// ---------------------------------------------------------------- board
async function showBoard(params) {
  setActiveNav("board");
  view.innerHTML = `<div class="skeleton"></div>`;
  const board = await api("/api/prices/board", { auth: false });
  if (!board.latestDate) {
    view.innerHTML = emptyState("No prices yet", "The first import has not run. An admin can start one from the Imports page.");
    return;
  }
  const imp = board.lastImport;
  const dot = !imp ? "warn" : imp.status === "FAILED" ? "bad" : imp.status === "PARTIAL" ? "warn" : "";
  document.getElementById("source-note").textContent = imp?.source === "agmarknet" ? "Live data from data.gov.in" : "Built-in sample data";
  const categories = [...new Set(board.rows.map((r) => r.category))].sort();
  const cat = params.get("cat") || "";
  const q = params.get("q") || "";

  const mover = (r) => `<a class="mover" href="${seriesLink(r.commodityId, r.marketId)}">
      <span><b>${esc(r.commodity)}</b><small>${esc(r.market)}</small></span>
      <span class="num">${rupees(r.modal)}</span>${change(r.weekChangePct, r.weekChange)}</a>`;

  view.innerHTML = `
    <section class="hero">
      <div><h1>Mandi prices today</h1>
        <p>Modal (most common) prices from ${board.rows.length} commodity–market pairs across Maharashtra APMCs</p></div>
      <span class="updated" data-testid="updated"><span class="dot ${dot}"></span>Prices for ${longDate(board.latestDate)}${imp?.finishedAt ? ` · imported ${dateTime(imp.finishedAt)}` : ""}</span>
    </section>
    <section class="movers">
      <div class="card" data-testid="gainers"><h2><span class="chg up">▲</span> Biggest rises this week</h2>${board.gainers.map(mover).join("") || `<p class="muted">No rises this week.</p>`}</div>
      <div class="card" data-testid="losers"><h2><span class="chg down">▼</span> Biggest falls this week</h2>${board.losers.map(mover).join("") || `<p class="muted">No falls this week.</p>`}</div>
    </section>
    <section class="card">
      <div class="filters">
        <input type="search" id="filter" placeholder="Search commodity, market or district" aria-label="Filter prices" value="${esc(q)}">
        <div class="chips" role="group" aria-label="Category">
          <button class="chip" type="button" data-cat="" aria-pressed="${!cat}">All</button>
          ${categories.map((c) => `<button class="chip" type="button" data-cat="${esc(c)}" aria-pressed="${cat === c}">${esc(c)}</button>`).join("")}
        </div>
      </div>
      <div class="table-wrap"><table data-testid="board">
        <thead><tr><th>Commodity</th><th>Market</th><th class="r">Modal price</th><th class="r hide-sm">Min – max</th>
          <th class="r">Day</th><th class="r hide-sm">Week</th><th class="hide-sm">14 days</th><th><span class="hide-sm">Watch</span></th></tr></thead>
        <tbody></tbody></table></div>
      <p class="note" id="count"></p>
    </section>`;

  const tbody = view.querySelector("tbody");
  const render = () => {
    const text = view.querySelector("#filter").value.trim().toLowerCase();
    const active = view.querySelector(".chip[aria-pressed=true]").dataset.cat;
    const rows = board.rows.filter((r) => (!active || r.category === active)
      && (!text || `${r.commodity} ${r.market} ${r.district}`.toLowerCase().includes(text)));
    tbody.innerHTML = rows.map((r) => `
      <tr tabindex="0" data-href="${seriesLink(r.commodityId, r.marketId)}" class="${r.stale ? "stale" : ""}">
        <td><b>${esc(r.commodity)}</b><small>${esc(r.category)}</small></td>
        <td>${esc(r.market)}<small>${esc(r.district)}${r.stale ? ` · last ${shortDate(r.date)}` : ""}</small></td>
        <td class="r num"><b>${rupees(r.modal)}</b><small>${perKg(r.modal)}</small></td>
        <td class="r num hide-sm">${rupees(r.min)} – ${rupees(r.max)}</td>
        <td class="r">${change(r.dayChangePct, r.dayChange)}</td>
        <td class="r hide-sm">${change(r.weekChangePct, r.weekChange)}</td>
        <td class="hide-sm">${sparkline(r.spark)}</td>
        <td>${starButton(r.commodityId, r.marketId, `${r.commodity} in ${r.market}`)}</td>
      </tr>`).join("") || `<tr><td colspan="8" class="muted">Nothing matches “${esc(text)}”.</td></tr>`;
    view.querySelector("#count").textContent = `${rows.length} of ${board.rows.length} prices`;
    syncStars();
  };
  view.querySelector("#filter").addEventListener("input", render);
  view.querySelectorAll(".chip").forEach((chip) => chip.addEventListener("click", () => {
    view.querySelectorAll(".chip").forEach((c) => c.setAttribute("aria-pressed", String(c === chip)));
    render();
  }));
  tbody.addEventListener("click", (ev) => {
    const tr = ev.target.closest("tr[data-href]");
    if (tr && !ev.target.closest("button")) location.hash = tr.dataset.href;
  });
  tbody.addEventListener("keydown", (ev) => {
    const tr = ev.target.closest("tr[data-href]");
    if (tr && ev.key === "Enter") location.hash = tr.dataset.href;
  });
  render();
}

// ---------------------------------------------------------------- commodity
async function showCommodity(commodityId, params) {
  setActiveNav("board");
  view.innerHTML = `<div class="skeleton"></div>`;
  const cat = await catalog();
  const commodity = cat.commodities.find((c) => c.id === commodityId);
  if (!commodity) {
    view.innerHTML = emptyState("Commodity not found", "It may not have any prices yet.", `<a class="btn primary" href="#/">Back to the board</a>`);
    return;
  }
  const marketId = Number(params.get("m")) || commodity.markets[0].id;
  const days = RANGES.includes(Number(params.get("d"))) ? Number(params.get("d")) : 90;
  const [series, compare, monthly] = await Promise.all([
    api(`/api/prices/series?commodityId=${commodityId}&marketId=${marketId}&days=${days}`, { auth: false }),
    api(`/api/prices/compare?commodityId=${commodityId}`, { auth: false }),
    api(`/api/prices/monthly?commodityId=${commodityId}&marketId=${marketId}`, { auth: false }),
  ]);
  const s = series.stats;
  const trendText = { RISING: "Rising", FALLING: "Falling", STEADY: "Steady" }[s.trend];
  const trendCls = { RISING: "ok", FALLING: "bad", STEADY: "" }[s.trend];
  const label = `${series.commodity} in ${series.market}`;
  const maxModal = Math.max(...compare.markets.map((r) => r.modal));
  const months = monthly.months;
  const best = months.length ? months.reduce((a, b) => (b.avgModal > a.avgModal ? b : a)) : null;
  const worst = months.length ? months.reduce((a, b) => (b.avgModal < a.avgModal ? b : a)) : null;
  const monthLabel = (m) => new Date(Date.UTC(m.year, m.month - 1, 1)).toLocaleString("en-IN", { month: "long", timeZone: "UTC" });

  view.innerHTML = `
    <div class="crumbs"><a href="#/">Board</a> › ${esc(commodity.category)}</div>
    <div class="title-row">
      <h1 data-testid="title">${esc(series.commodity)} <span class="muted">· ${esc(series.market)}</span></h1>
      <span class="pill ${trendCls}" data-testid="trend">${trendText}</span>
      <span class="spacer"></span>
      ${starButton(commodityId, marketId, label).replace('class="star"', 'class="star btn ghost small"')}
      <a class="btn ghost small" href="#/alerts?c=${commodityId}&m=${marketId}" data-testid="set-alert">🔔 Set alert</a>
      <a class="btn ghost small" href="/api/prices/export.csv?commodityId=${commodityId}&marketId=${marketId}&days=${days}" download data-testid="csv">⬇ CSV</a>
    </div>
    <div class="chips" role="group" aria-label="Market">
      ${commodity.markets.map((m) => `<a class="chip ${m.id === marketId ? "on" : ""}" href="${seriesLink(commodityId, m.id, `&d=${days}`)}" ${m.id === marketId ? 'aria-current="page"' : ""}>${esc(m.name)}</a>`).join("")}
    </div>
    <section class="stats" data-testid="stats">
      <div class="card stat"><div class="k">Modal price</div><div class="v num" data-testid="latest">${rupees(s.latestModal)}</div><div class="s">${perKg(s.latestModal)} · ${shortDate(s.latestDate)}</div></div>
      <div class="card stat"><div class="k">Since last trading day</div><div class="v">${change(s.dayChangePct, s.dayChange)}</div><div class="s">${s.dayChange == null ? "" : `${s.dayChange >= 0 ? "+" : "−"}${rupees(Math.abs(s.dayChange))}`}</div></div>
      <div class="card stat"><div class="k">Over a week</div><div class="v">${change(s.weekChangePct, s.weekChange)}</div><div class="s">${s.weekChange == null ? "" : `${s.weekChange >= 0 ? "+" : "−"}${rupees(Math.abs(s.weekChange))}`}</div></div>
      <div class="card stat"><div class="k">30-day range</div><div class="v num">${rupees(s.low30)}–${rupees(s.high30).slice(1)}</div><div class="s">avg ${rupees(s.avg30)}</div></div>
      <div class="card stat"><div class="k">Volatility</div><div class="v num">${s.volatilityPct.toFixed(1)}%</div><div class="s">daily swing, 30 days</div></div>
      <div class="card stat"><div class="k">14-day trend</div><div class="v num">${s.slopePerDay >= 0 ? "+" : "−"}${rupees(Math.abs(Math.round(s.slopePerDay)))}</div><div class="s">per day</div></div>
    </section>
    <section class="card chart-card">
      <div class="chart-head">
        <h2>Price history</h2>
        <div class="legend"><span><i class="l-modal"></i>Modal</span><span><i class="l-ma"></i>7-day average</span><span><i class="l-band"></i>Min–max</span></div>
        <span class="spacer"></span>
        <div class="seg" role="group" aria-label="Range">${RANGES.map((r) => `<button type="button" data-range="${r}" aria-pressed="${r === days}">${r === 365 ? "1Y" : `${r}D`}</button>`).join("")}</div>
      </div>
      <div class="chart" id="chart" data-testid="chart"></div>
    </section>
    <section class="two">
      <div class="card" data-testid="compare">
        <h2>Compare markets</h2>
        <p class="note sub">Latest modal price of ${esc(series.commodity)} in each market${compare.spread ? ` · <b>${esc(compare.highestMarket)}</b> pays ${rupees(compare.spread)} (${compare.spreadPct}%) more than <b>${esc(compare.lowestMarket)}</b>` : ""}</p>
        <div class="bars">${compare.markets.map((r) => `
          <a class="bar ${r.marketId === marketId ? "current" : ""}" href="${seriesLink(commodityId, r.marketId, `&d=${days}`)}">
            <span>${esc(r.market)}<small class="muted"> ${shortDate(r.date)}</small></span>
            <span class="track"><span class="fill" style="width:${((r.modal / maxModal) * 100).toFixed(1)}%"></span></span>
            <span class="val num">${rupees(r.modal)} ${change(r.weekChangePct, r.weekChange)}</span>
          </a>`).join("")}</div>
      </div>
      <div class="card" data-testid="seasonality">
        <h2>Seasonality</h2>
        <p class="note sub">Average modal price by month, last 12 months${best && worst && best !== worst ? ` · dearest in <b>${monthLabel(best)}</b>, cheapest in <b>${monthLabel(worst)}</b>` : ""}</p>
        <div class="chart" id="monthly"></div>
      </div>
    </section>`;

  const chartBox = view.querySelector("#chart");
  priceChart(chartBox, series.points);
  // redraw at the new width when a phone rotates or a window is resized
  let lastWidth = chartBox.clientWidth;
  const onResize = () => {
    if (!chartBox.isConnected) { window.removeEventListener("resize", onResize); return; }
    if (Math.abs(chartBox.clientWidth - lastWidth) > 40) { lastWidth = chartBox.clientWidth; priceChart(chartBox, series.points); }
  };
  window.addEventListener("resize", onResize);
  monthlyChart(view.querySelector("#monthly"), months);
  view.querySelectorAll("[data-range]").forEach((b) => b.addEventListener("click", () => {
    location.hash = seriesLink(commodityId, marketId, `&d=${b.dataset.range}`);
  }));
  syncStars();
}

// ---------------------------------------------------------------- watchlist
async function showWatchlist() {
  setActiveNav("watchlist");
  if (!requireLogin()) return;
  view.innerHTML = `<div class="skeleton"></div>`;
  const items = await api("/api/watchlist");
  state.watch = new Map(items.filter((w) => w.latest).map((w) => [seriesKey(w.latest.commodityId, w.latest.marketId), w.id]));
  if (!items.length) {
    view.innerHTML = `<h1>Your watchlist</h1><br>${emptyState("Nothing watched yet", "Tap ☆ next to any price on the board to follow it here.", `<a class="btn primary" href="#/">Browse prices</a>`)}`;
    return;
  }
  view.innerHTML = `
    <div class="hero"><div><h1>Your watchlist</h1><p>${items.length} ${items.length === 1 ? "price" : "prices"} you follow</p></div></div>
    <section class="cards" data-testid="watchlist">${items.map((w) => {
      const r = w.latest;
      if (!r) return "";
      return `<div class="card watch" data-testid="watch-card">
        <div class="row"><a href="${seriesLink(r.commodityId, r.marketId)}"><b>${esc(r.commodity)}</b></a><span class="muted">${esc(r.market)}</span><span class="spacer"></span>${starButton(r.commodityId, r.marketId, `${r.commodity} in ${r.market}`)}</div>
        <div class="row"><span class="price num">${rupees(r.modal)}</span><span class="muted">${perKg(r.modal)}</span></div>
        ${sparkline(r.spark)}
        <div class="row"><span class="muted">Day</span>${change(r.dayChangePct, r.dayChange)}<span class="muted">Week</span>${change(r.weekChangePct, r.weekChange)}<span class="spacer"></span><span class="muted">${shortDate(r.date)}</span></div>
      </div>`;
    }).join("")}</section>`;
  syncStars();
}

// ---------------------------------------------------------------- alerts
async function showAlerts(params) {
  setActiveNav("alerts");
  if (!requireLogin()) return;
  view.innerHTML = `<div class="skeleton"></div>`;
  const [alerts, inbox, cat] = await Promise.all([api("/api/alerts"), api("/api/notifications"), catalog()]);
  const preC = Number(params.get("c")) || cat.commodities[0]?.id;
  const statusOf = (a) => {
    if (!a.active) return `<span class="pill">Paused</span>`;
    if (!a.armed) return `<span class="pill warn">Triggered${a.lastTriggeredAt ? ` ${dateTime(a.lastTriggeredAt)}` : ""} · waiting for the price to move back</span>`;
    return `<span class="pill ok">Watching</span>`;
  };

  view.innerHTML = `
    <div class="hero"><div><h1>Price alerts</h1><p>Get notified when a price crosses your target. Prices are checked after every import.</p></div></div>
    <div class="layout">
      <form class="card stack" id="alert-form" novalidate data-testid="alert-form">
        <h2>New alert</h2>
        <label>Commodity <select name="commodityId">${cat.commodities.map((c) => `<option value="${c.id}" ${c.id === preC ? "selected" : ""}>${esc(c.name)}</option>`).join("")}</select></label>
        <label>Market <select name="marketId"></select></label>
        <label>Notify me when the modal price is
          <select name="direction"><option value="ABOVE">at or above</option><option value="BELOW">at or below</option></select></label>
        <label>Target (₹ per quintal) <input name="threshold" type="number" min="1" step="10" inputmode="numeric" required>
          <span class="hint" id="current-hint"></span><span class="field-error" data-for="threshold"></span></label>
        <p class="form-error" role="alert"></p>
        <button class="btn primary" type="submit">Create alert</button>
      </form>
      <div class="stack">
        <section class="card inbox" data-testid="inbox">
          <div class="row"><h2>Notifications</h2><span class="pill">${inbox.unread} unread</span><span class="spacer"></span>
            ${inbox.unread ? `<button class="btn ghost small" id="read-all" type="button">Mark all read</button>` : ""}</div>
          ${inbox.items.length ? `<ul>${inbox.items.map((n) => `<li class="${n.read ? "" : "unread"}">${esc(n.message)}<br><small class="muted">${dateTime(n.createdAt)}</small></li>`).join("")}</ul>`
            : `<p class="muted">No notifications yet. They appear here when an alert fires.</p>`}
        </section>
        <section class="card" data-testid="alert-list">
          <h2>Your alerts (${alerts.length})</h2>
          ${alerts.length ? alerts.map((a) => `
            <div class="alert-row" data-alert="${a.id}">
              <div><b>${esc(a.commodity)}</b> at ${esc(a.market)} ${a.direction === "ABOVE" ? "≥" : "≤"} <b class="num">${rupees(a.threshold)}</b>
                <div class="hint">Now ${rupees(a.currentPrice)}${a.currentDate ? ` (${shortDate(a.currentDate)})` : ""} · ${statusOf(a)}</div></div>
              <div class="actions">
                <a class="btn ghost small" href="${seriesLink(a.commodityId, a.marketId)}">Chart</a>
                <button class="btn ghost small" type="button" data-toggle="${a.id}" data-active="${a.active}">${a.active ? "Pause" : "Resume"}</button>
                <button class="btn danger small" type="button" data-delete="${a.id}">Delete</button>
              </div>
            </div>`).join("") : `<p class="muted">No alerts yet. Create one with the form.</p>`}
        </section>
      </div>
    </div>`;

  const form = view.querySelector("#alert-form");
  const marketSelect = form.elements.marketId;
  const fillMarkets = () => {
    const c = cat.commodities.find((x) => x.id === Number(form.elements.commodityId.value));
    const preM = Number(params.get("m"));
    marketSelect.innerHTML = c.markets.map((m) => `<option value="${m.id}" ${m.id === preM ? "selected" : ""}>${esc(m.name)} (${esc(m.district)})</option>`).join("");
    updateHint();
  };
  const updateHint = async () => {
    const hint = view.querySelector("#current-hint");
    hint.textContent = "";
    try {
      const s = await api(`/api/prices/series?commodityId=${form.elements.commodityId.value}&marketId=${marketSelect.value}&days=7`, { auth: false });
      hint.textContent = `Latest: ${rupees(s.stats.latestModal)} on ${shortDate(s.stats.latestDate)} · 30-day range ${rupees(s.stats.low30)}–${rupees(s.stats.high30).slice(1)}`;
      if (!form.elements.threshold.value) form.elements.threshold.placeholder = String(s.stats.latestModal);
    } catch { /* hint is optional */ }
  };
  form.elements.commodityId.addEventListener("change", () => { params.delete("m"); fillMarkets(); });
  marketSelect.addEventListener("change", updateHint);
  fillMarkets();

  form.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    form.querySelector(".form-error").textContent = "";
    form.querySelectorAll(".field-error").forEach((f) => { f.textContent = ""; });
    const threshold = Number(form.elements.threshold.value);
    if (!threshold || threshold < 1) {
      form.querySelector('[data-for="threshold"]').textContent = "Enter a target price in rupees per quintal";
      form.elements.threshold.setAttribute("aria-invalid", "true");
      form.elements.threshold.focus();
      return;
    }
    form.elements.threshold.removeAttribute("aria-invalid");
    try {
      const a = await api("/api/alerts", { method: "POST", body: {
        commodityId: Number(form.elements.commodityId.value), marketId: Number(marketSelect.value),
        direction: form.elements.direction.value, threshold } });
      toast(a.conditionMet
        ? `Alert created. The price is already ${a.direction === "ABOVE" ? "above" : "below"} ${rupees(a.threshold)}, so it will fire on the next fresh crossing.`
        : "Alert created. We'll notify you when the price crosses it.");
      showAlerts(new URLSearchParams());
    } catch (e) {
      form.querySelector(".form-error").textContent = e.message;
    }
  });
  view.querySelector("#read-all")?.addEventListener("click", async () => {
    await api("/api/notifications/read", { method: "POST" });
    refreshUnread();
    showAlerts(params);
  });
  view.querySelectorAll("[data-toggle]").forEach((b) => b.addEventListener("click", async () => {
    await api(`/api/alerts/${b.dataset.toggle}`, { method: "PATCH", body: { active: b.dataset.active !== "true" } });
    showAlerts(params);
  }));
  view.querySelectorAll("[data-delete]").forEach((b) => b.addEventListener("click", async () => {
    if (!confirm("Delete this alert?")) return;
    await api(`/api/alerts/${b.dataset.delete}`, { method: "DELETE" });
    toast("Alert deleted");
    showAlerts(params);
  }));
}

// ---------------------------------------------------------------- admin
function dateRange(first, last) {
  if (!first) return "–";
  if (first === last) return shortDate(first);
  return first.slice(0, 4) === last.slice(0, 4) ? `${shortDate(first)} – ${shortDate(last)}` : `${longDate(first)} – ${longDate(last)}`;
}

async function showAdmin() {
  setActiveNav("admin");
  if (!requireLogin()) return;
  if (state.user.role !== "ADMIN") {
    view.innerHTML = emptyState("Admins only", "Import monitoring is available to market administrators.");
    return;
  }
  const o = await api("/api/admin/imports");
  const pill = (s) => `<span class="pill ${{ SUCCESS: "ok", PARTIAL: "warn", FAILED: "bad" }[s] || ""}">${s}</span>`;
  view.innerHTML = `
    <div class="hero"><div><h1>Imports</h1>
      <p>Source <b>${o.source === "agmarknet" ? "data.gov.in (Agmarknet)" : "built-in sample"}</b> · schedule <code>${esc(o.schedule)}</code> (${esc(o.zone)})${o.scheduleEnabled ? "" : " · <b>schedule off</b>"}</p></div>
      <button class="btn primary" id="import-now" type="button" data-testid="import-now" ${o.running ? "disabled" : ""}>${o.running ? "Import running…" : "Import today's prices"}</button></div>
    <section class="card table-wrap"><table data-testid="runs">
      <thead><tr><th>Started</th><th>Trigger</th><th>Status</th><th class="r">Fetched</th><th class="r">New</th><th class="r">Updated</th><th class="r hide-sm">Unchanged</th><th class="r">Rejected</th><th class="r hide-sm">Alerts</th><th class="hide-sm">Dates</th></tr></thead>
      <tbody>${o.runs.map((r) => `<tr>
        <td>${dateTime(r.startedAt)}</td><td>${r.trigger.toLowerCase()}</td><td>${pill(r.status)}</td>
        <td class="r num">${r.fetched.toLocaleString("en-IN")}</td><td class="r num">${r.inserted.toLocaleString("en-IN")}</td><td class="r num">${r.updated}</td>
        <td class="r num hide-sm">${r.unchanged.toLocaleString("en-IN")}</td><td class="r num">${r.rejected}</td><td class="r num hide-sm">${r.alertsFired}</td>
        <td class="hide-sm">${dateRange(r.firstDate, r.lastDate)}</td></tr>
        ${r.message ? `<tr><td></td><td colspan="9" class="muted"><small>${esc(r.message)}</small></td></tr>` : ""}`).join("")}</tbody></table></section>
    <p class="note">Rows are validated before they are stored: rejected rows (missing prices, a modal price outside its own min–max range, unreadable dates) are counted and explained here instead of failing the import. Re-importing a day updates changed prices and leaves the rest untouched.</p>`;
  view.querySelector("#import-now").addEventListener("click", async (ev) => {
    ev.target.disabled = true;
    ev.target.textContent = "Importing…";
    try {
      const run = await api("/api/admin/imports", { method: "POST" });
      toast(`Import ${run.status.toLowerCase()}: ${run.inserted} new, ${run.updated} updated, ${run.unchanged} unchanged, ${run.rejected} rejected`);
    } catch (e) {
      toast(e.message, "error");
    }
    state.catalog = null;
    showAdmin();
  });
}

// ---------------------------------------------------------------- auth
function showAuth(mode, params) {
  setActiveNav("");
  const next = params.get("next") || "#/";
  const register = mode === "register";
  view.innerHTML = `
    <form class="card stack auth" id="auth-form" novalidate data-testid="auth-form">
      <h1>${register ? "Create an account" : "Sign in"}</h1>
      <p class="muted">${register ? "Free: a watchlist and price alerts for any mandi price." : "Track the prices you care about and get alerts."}</p>
      ${register ? `<label>Name <input name="name" autocomplete="name" required><span class="field-error" data-for="name"></span></label>` : ""}
      <label>Email <input name="email" type="email" autocomplete="email" required><span class="field-error" data-for="email"></span></label>
      <label>Password <input name="password" type="password" autocomplete="${register ? "new-password" : "current-password"}" required><span class="field-error" data-for="password"></span></label>
      <p class="form-error" role="alert"></p>
      <button class="btn primary" type="submit">${register ? "Create account" : "Sign in"}</button>
      ${register ? `<p class="muted">Already registered? <a href="#/login?next=${encodeURIComponent(next)}">Sign in</a></p>`
        : `<p class="muted">New here? <a href="#/register?next=${encodeURIComponent(next)}">Create an account</a></p>
           <div class="demo-hint">Demo account: <b>demo@mandi.in</b> / <b>demo1234</b>
             <button class="btn ghost small" type="button" id="demo-login">Use demo account</button></div>`}
    </form>`;
  const form = view.querySelector("#auth-form");
  view.querySelector("#demo-login")?.addEventListener("click", () => {
    form.elements.email.value = "demo@mandi.in";
    form.elements.password.value = "demo1234";
    form.requestSubmit();
  });
  form.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    form.querySelector(".form-error").textContent = "";
    form.querySelectorAll(".field-error").forEach((f) => { f.textContent = ""; });
    form.querySelectorAll("input").forEach((i) => i.removeAttribute("aria-invalid"));
    const body = Object.fromEntries(new FormData(form));
    try {
      const res = await api(register ? "/api/auth/register" : "/api/auth/login", { method: "POST", body, auth: false });
      setToken(res.token);
      state.user = res.user;
      updateHeader();
      await loadWatch();
      refreshUnread();
      location.hash = next;
    } catch (e) {
      form.querySelector(".form-error").textContent = e.message;
      for (const [field, msg] of Object.entries(e.fields || {})) {
        const slot = form.querySelector(`[data-for="${field}"]`);
        if (slot) slot.textContent = msg;
        form.elements[field]?.setAttribute("aria-invalid", "true");
      }
    }
  });
  form.elements[register ? "name" : "email"].focus();
}

// ---------------------------------------------------------------- router
async function route() {
  const { parts, params } = parseHash();
  window.scrollTo(0, 0);
  try {
    if (parts[0] === "c" && parts[1]) await showCommodity(Number(parts[1]), params);
    else if (parts[0] === "watchlist") await showWatchlist();
    else if (parts[0] === "alerts") await showAlerts(params);
    else if (parts[0] === "admin") await showAdmin();
    else if (parts[0] === "login") showAuth("login", params);
    else if (parts[0] === "register") showAuth("register", params);
    else await showBoard(params);
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return;
    view.innerHTML = emptyState("Something went wrong", esc(e.message), `<a class="btn primary" href="#/">Back to the board</a>`);
  }
  if (state.user) refreshUnread();
}

function signOut() {
  setToken(null);
  state.user = null;
  state.watch.clear();
  updateHeader();
  refreshUnread();
}

let booting = true;
onUnauthorized(() => {
  if (booting) return; // an expired token at start-up just means "signed out"
  signOut();
  location.hash = `#/login?next=${encodeURIComponent(location.hash)}`;
});

document.getElementById("logout").addEventListener("click", () => {
  signOut();
  toast("Signed out");
  location.hash = "#/";
  route();
});

window.addEventListener("hashchange", route);

(async function start() {
  if (getToken()) {
    try {
      state.user = await api("/api/me");
      await loadWatch();
    } catch {
      setToken(null);
      state.user = null;
    }
  }
  booting = false;
  updateHeader();
  route();
})();
