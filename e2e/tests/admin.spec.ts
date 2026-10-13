import { test, expect } from "@playwright/test";
import { ADMIN, newUser, signedIn, tokenFor } from "./helpers";

test("admin sees the import log and a manual re-import changes nothing", async ({ page, request }) => {
  await signedIn(page, await tokenFor(request, ADMIN.email, ADMIN.password));
  await page.goto("/#/admin");
  const runs = page.getByTestId("runs").locator("tbody tr").filter({ has: page.locator(".pill") });
  await expect(runs.first()).toBeVisible(); // count() does not wait for the table to load
  const before = await runs.count();
  expect(before).toBeGreaterThanOrEqual(2);
  await expect(page.getByTestId("runs")).toContainText("backfill");

  await page.getByTestId("import-now").click();
  await expect(page.locator(".toast")).toContainText(/Import (success|partial): 0 new, 0 updated/);
  await expect(runs).toHaveCount(before + 1);
  await expect(runs.first()).toContainText("manual");
});

test("regular users cannot open the imports page", async ({ page, request }) => {
  const user = await newUser(request);
  await signedIn(page, user.token);
  await page.goto("/#/admin");
  await expect(page.getByText("Admins only")).toBeVisible();
  await expect(page.getByRole("link", { name: "Imports" })).toBeHidden();
  const res = await request.post("/api/admin/imports", { headers: { Authorization: `Bearer ${user.token}` } });
  expect(res.status()).toBe(403);
});
