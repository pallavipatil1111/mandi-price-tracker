import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const DEMO = { email: "demo@mandi.in", password: "demo1234" };
export const ADMIN = { email: "admin@mandi.local", password: "Admin@12345" };

export async function tokenFor(request: APIRequestContext, email: string, password: string): Promise<string> {
  const res = await request.post("/api/auth/login", { data: { email, password } });
  expect(res.status()).toBe(200);
  return (await res.json()).token;
}

/** Registers a fresh user through the API and returns its token. */
export async function newUser(request: APIRequestContext): Promise<{ email: string; token: string }> {
  const email = `trader-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
  const res = await request.post("/api/auth/register", { data: { name: "Test Trader", email, password: "secret123" } });
  expect(res.status()).toBe(201);
  return { email, token: (await res.json()).token };
}

/** Starts the page already signed in (the app keeps its JWT in localStorage). */
export async function signedIn(page: Page, token: string) {
  await page.addInitScript((t) => window.localStorage.setItem("mandi.token", t), token);
}

export async function openBoard(page: Page) {
  await page.goto("/");
  await expect(page.getByTestId("board").locator("tbody tr").first()).toBeVisible();
}
