// Small dependency-free SVG charts: price history (min–max band, modal line, 7-day average),
// sparklines and monthly bars. Hover or touch shows a tooltip for the nearest day.
import { rupees, shortDate, longDate, perKg, parseDate, monthName, esc } from "./format.js";

const NS = "http://www.w3.org/2000/svg";
const PAD = { top: 14, right: 16, bottom: 30, left: 62 };

function el(name, attrs = {}, parent) {
  const node = document.createElementNS(NS, name);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  if (parent) parent.appendChild(node);
  return node;
}

/** Rounded tick values covering [min, max]. */
export function niceTicks(min, max, count = 5) {
  if (min === max) { min -= 1; max += 1; }
  const raw = (max - min) / count;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= raw);
  const ticks = [];
  for (let v = Math.floor(min / step) * step; v <= max + step * 0.001; v += step) ticks.push(Math.round(v));
  if (ticks[ticks.length - 1] < max) ticks.push(ticks[ticks.length - 1] + step);
  return ticks;
}

const tip = () => document.getElementById("tooltip");
export function showTip(html, x, y) {
  const t = tip();
  t.innerHTML = html;
  t.hidden = false;
  const r = t.getBoundingClientRect();
  const left = Math.min(window.innerWidth - r.width - 8, Math.max(8, x + 14));
  const top = y - r.height - 12 < 8 ? y + 16 : y - r.height - 12;
  t.style.left = `${left}px`;
  t.style.top = `${top}px`;
}
export function hideTip() { tip().hidden = true; }

/** Main price chart. points: [{date, min, max, modal, ma7}] oldest first. */
export function priceChart(container, points, { threshold } = {}) {
  container.textContent = "";
  if (!points.length) { container.innerHTML = `<p class="muted">No prices in this period.</p>`; return; }
  // Draw at the container's real width so labels stay readable on phones instead of shrinking.
  const W = Math.max(320, Math.round(container.clientWidth || 860));
  const H = W < 600 ? 250 : 320;
  const svg = el("svg", { viewBox: `0 0 ${W} ${H}`, role: "img",
    "aria-label": `Price chart from ${longDate(points[0].date)} to ${longDate(points[points.length - 1].date)}` }, container);
  const t0 = parseDate(points[0].date).getTime();
  const t1 = parseDate(points[points.length - 1].date).getTime();
  const span = Math.max(1, t1 - t0);
  let lo = Math.min(...points.map((p) => p.min));
  let hi = Math.max(...points.map((p) => p.max));
  if (threshold) { lo = Math.min(lo, threshold); hi = Math.max(hi, threshold); }
  const ticks = niceTicks(lo, hi);
  const yMin = ticks[0];
  const yMax = ticks[ticks.length - 1];
  const x = (iso) => PAD.left + ((parseDate(iso).getTime() - t0) / span) * (W - PAD.left - PAD.right);
  const y = (v) => PAD.top + (1 - (v - yMin) / (yMax - yMin)) * (H - PAD.top - PAD.bottom);

  const grid = el("g", { class: "grid" }, svg);
  for (const v of ticks) {
    el("line", { x1: PAD.left, x2: W - PAD.right, y1: y(v), y2: y(v) }, grid);
    el("text", { x: PAD.left - 8, y: y(v) + 4, "text-anchor": "end" }, svg).textContent = rupees(v);
  }
  // x labels: about six evenly spaced dates
  const step = Math.max(1, Math.round(points.length / (W < 600 ? 4 : 6)));
  for (let i = 0; i < points.length; i += step) {
    el("text", { x: x(points[i].date), y: H - 8, "text-anchor": "middle" }, svg).textContent = shortDate(points[i].date);
  }

  const band = points.map((p) => `${x(p.date)},${y(p.max)}`).concat([...points].reverse().map((p) => `${x(p.date)},${y(p.min)}`));
  el("polygon", { points: band.join(" "), fill: "var(--chart-band)" }, svg);
  const line = (key) => {
    const segs = [];
    let cur = [];
    for (const p of points) {
      if (p[key] == null) { if (cur.length) segs.push(cur); cur = []; continue; }
      cur.push(`${x(p.date).toFixed(1)},${y(p[key]).toFixed(1)}`);
    }
    if (cur.length) segs.push(cur);
    return segs.map((s) => `M${s.join("L")}`).join(" ");
  };
  el("path", { d: line("ma7"), fill: "none", stroke: "var(--chart-ma)", "stroke-width": 2, "stroke-dasharray": "6 4" }, svg);
  el("path", { d: line("modal"), fill: "none", stroke: "var(--chart-modal)", "stroke-width": 2.4, "stroke-linejoin": "round" }, svg);
  if (threshold) {
    el("line", { x1: PAD.left, x2: W - PAD.right, y1: y(threshold), y2: y(threshold), stroke: "var(--down)", "stroke-dasharray": "3 4" }, svg);
    el("text", { x: W - PAD.right, y: y(threshold) - 6, "text-anchor": "end" }, svg).textContent = `Alert ${rupees(threshold)}`;
  }

  const cursor = el("line", { y1: PAD.top, y2: H - PAD.bottom, stroke: "var(--muted)", "stroke-width": 1, visibility: "hidden" }, svg);
  const dot = el("circle", { r: 4.5, fill: "var(--chart-modal)", stroke: "var(--surface)", "stroke-width": 2, visibility: "hidden" }, svg);
  const hit = el("rect", { x: PAD.left, y: PAD.top, width: W - PAD.left - PAD.right, height: H - PAD.top - PAD.bottom, fill: "transparent" }, svg);
  const move = (ev) => {
    const box = svg.getBoundingClientRect();
    const sx = ((ev.clientX - box.left) / box.width) * W;
    let best = 0;
    for (let i = 1; i < points.length; i++) if (Math.abs(x(points[i].date) - sx) < Math.abs(x(points[best].date) - sx)) best = i;
    const p = points[best];
    cursor.setAttribute("x1", x(p.date)); cursor.setAttribute("x2", x(p.date)); cursor.setAttribute("visibility", "visible");
    dot.setAttribute("cx", x(p.date)); dot.setAttribute("cy", y(p.modal)); dot.setAttribute("visibility", "visible");
    showTip(`<b>${esc(longDate(p.date))}</b><br>Modal <b>${rupees(p.modal)}</b> · ${perKg(p.modal)}<br>Range ${rupees(p.min)} – ${rupees(p.max)}`
      + (p.ma7 != null ? `<br>7-day avg ${rupees(p.ma7)}` : ""), ev.clientX, ev.clientY);
  };
  const leave = () => { cursor.setAttribute("visibility", "hidden"); dot.setAttribute("visibility", "hidden"); hideTip(); };
  hit.addEventListener("pointermove", move);
  hit.addEventListener("pointerdown", move);
  hit.addEventListener("pointerleave", leave);
}

/** A tiny trend line. Colour follows the overall direction. */
export function sparkline(values, cls = "spark") {
  if (!values || values.length < 2) return `<svg class="${cls}" aria-hidden="true"></svg>`;
  const w = 100;
  const h = 30;
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  const pts = values.map((v, i) => `${((i / (values.length - 1)) * w).toFixed(1)},${(h - 2 - ((v - lo) / (hi - lo || 1)) * (h - 4)).toFixed(1)}`);
  const color = values[values.length - 1] >= values[0] ? "var(--up)" : "var(--down)";
  return `<svg class="${cls}" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" aria-hidden="true"><polyline points="${pts.join(" ")}" fill="none" stroke="${color}" stroke-width="2" vector-effect="non-scaling-stroke" stroke-linejoin="round"/></svg>`;
}

/** Monthly average bars, highlighting the dearest and cheapest months. */
export function monthlyChart(container, months) {
  container.textContent = "";
  if (!months.length) return;
  const w = 560;
  const h = 210;
  const pad = { top: 16, bottom: 26, left: 8, right: 8 };
  const svg = el("svg", { viewBox: `0 0 ${w} ${h}`, role: "img", "aria-label": "Average modal price by month" }, container);
  const max = Math.max(...months.map((m) => m.avgModal));
  const minV = Math.min(...months.map((m) => m.avgModal));
  const bw = (w - pad.left - pad.right) / months.length;
  months.forEach((m, i) => {
    const bh = (m.avgModal / max) * (h - pad.top - pad.bottom);
    const fill = m.avgModal === max ? "var(--chart-ma)" : m.avgModal === minV ? "var(--down)" : "var(--chart-modal)";
    const r = el("rect", { x: pad.left + i * bw + 3, y: h - pad.bottom - bh, width: bw - 6, height: bh, rx: 4, fill, opacity: 0.9 }, svg);
    el("text", { x: pad.left + i * bw + bw / 2, y: h - 8, "text-anchor": "middle" }, svg).textContent = monthName(m.month);
    const label = `<b>${monthName(m.month)} ${m.year}</b><br>Average ${rupees(m.avgModal)}<br>Range ${rupees(m.low)} – ${rupees(m.high)}<br>${m.tradingDays} trading days`;
    r.addEventListener("pointermove", (ev) => showTip(label, ev.clientX, ev.clientY));
    r.addEventListener("pointerleave", hideTip);
  });
}
