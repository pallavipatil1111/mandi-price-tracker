// Formatting helpers: Indian number grouping (₹1,25,000), dates like "13 Oct", signed changes.
const inr = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 });
const inrKg = new Intl.NumberFormat("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export const rupees = (n) => (n == null ? "–" : `₹${inr.format(n)}`);
/** Agmarknet prices are per quintal (100 kg); shoppers think per kg. */
export const perKg = (n) => (n == null ? "" : `₹${inrKg.format(n / 100)}/kg`);
export const monthName = (m) => MONTHS[m - 1];

export function parseDate(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

export function shortDate(iso) {
  const d = parseDate(iso);
  return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`;
}

export function longDate(iso) {
  const d = parseDate(iso);
  return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
}

export function dateTime(instant) {
  if (!instant) return "–";
  return new Date(instant).toLocaleString("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
}

export function direction(n) {
  if (n == null || n === 0) return "flat";
  return n > 0 ? "up" : "down";
}

/** "▲ 4.2%" style change with a text equivalent for screen readers. */
export function change(pct, abs) {
  if (pct == null) return `<span class="chg flat">–</span>`;
  const dir = direction(pct);
  const arrow = dir === "up" ? "▲" : dir === "down" ? "▼" : "•";
  const title = abs == null ? "" : ` title="${abs > 0 ? "+" : ""}${rupees(abs)}"`;
  return `<span class="chg ${dir}"${title}>${arrow} ${Math.abs(pct).toFixed(1)}%</span>`;
}

export function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}
