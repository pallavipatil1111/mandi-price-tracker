// Thin fetch wrapper: JSON in/out, bearer token, consistent errors.
const KEY = "mandi.token";
let unauthorizedHandler = () => {};

export function getToken() {
  try { return localStorage.getItem(KEY); } catch { return null; }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(KEY, token); else localStorage.removeItem(KEY);
  } catch { /* storage unavailable: token lives for this page only */ }
  memoryToken = token;
}

let memoryToken = getToken();

export function onUnauthorized(fn) {
  unauthorizedHandler = fn;
}

export class ApiError extends Error {
  constructor(status, message, fields) {
    super(message);
    this.status = status;
    this.fields = fields ?? {};
  }
}

export async function api(path, { method = "GET", body, auth = true } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth && memoryToken) headers.Authorization = `Bearer ${memoryToken}`;
  let res;
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, "Can't reach the server. Check your connection.");
  }
  if (res.status === 401 && auth) {
    unauthorizedHandler();
    throw new ApiError(401, "Your session has expired. Please sign in again.");
  }
  if (res.status === 204) return null;
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new ApiError(res.status, data?.message ?? `Request failed (${res.status})`, data?.fields);
  }
  return data;
}
