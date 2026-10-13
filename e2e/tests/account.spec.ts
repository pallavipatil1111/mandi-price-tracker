import { test, expect } from "@playwright/test";
import { DEMO, newUser, openBoard, signedIn, tokenFor } from "./helpers";

test("watching needs an account; after signing up the star works and the watchlist fills", async ({ page }) => {
  await openBoard(page);
  await page.getByLabel("Filter prices").fill("latur");
  await page.getByRole("button", { name: "Watch Soyabean in Latur" }).click();
  await expect(page).toHaveURL(/#\/login/);

  await page.getByRole("link", { name: "Create an account" }).click();
  const form = page.getByTestId("auth-form");
  await form.getByRole("button", { name: "Create account" }).click();
  await expect(form.locator('[data-for="name"]')).not.toBeEmpty();
  await form.getByLabel("Name").fill("Pallavi");
  await form.getByLabel("Email").fill(`p-${Date.now()}@example.com`);
  await form.getByLabel("Password").fill("secret123");
  await form.getByRole("button", { name: "Create account" }).click();
  await expect(page.locator("#logout")).toBeVisible(); // signed in (token stored) before reloading

  await openBoard(page);
  await page.getByLabel("Filter prices").fill("latur");
  const star = page.getByRole("button", { name: "Watch Soyabean in Latur" });
  await star.click();
  await expect(star).toHaveAttribute("aria-pressed", "true");

  await page.getByRole("link", { name: "Watchlist" }).click();
  await expect(page.getByTestId("watch-card")).toHaveCount(1);
  await expect(page.getByTestId("watch-card")).toContainText("Soyabean");
  await page.getByTestId("watch-card").getByRole("button", { name: /Watch Soyabean/ }).click();
  await page.reload();
  await expect(page.getByText("Nothing watched yet")).toBeVisible();
});

test("wrong password shows one clear error", async ({ page }) => {
  await page.goto("/#/login");
  const form = page.getByTestId("auth-form");
  await form.getByLabel("Email").fill(DEMO.email);
  await form.getByLabel("Password").fill("not-the-password");
  await form.getByRole("button", { name: "Sign in" }).click();
  await expect(form.getByRole("alert")).toHaveText("Invalid email or password");
});

test("demo user sees the notifications their alerts produced and can mark them read", async ({ page, request }) => {
  await signedIn(page, await tokenFor(request, DEMO.email, DEMO.password));
  await page.goto("/#/alerts");
  await expect(page.getByTestId("unread")).toBeVisible();
  await expect(page.getByTestId("alert-list").locator(".alert-row")).toHaveCount(5);
  await expect(page.getByTestId("alert-list")).toContainText("Triggered");
  await expect(page.getByTestId("alert-list")).toContainText("Watching"); // the cotton alert has not fired
  await expect(page.getByTestId("inbox").locator("li").first()).toContainText("/quintal on");

  await page.getByRole("button", { name: "Mark all read" }).click();
  await expect(page.getByTestId("inbox")).toContainText("0 unread");
  await expect(page.getByTestId("unread")).toBeHidden();
});

test("creating, pausing and deleting an alert", async ({ page, request }) => {
  const user = await newUser(request);
  await signedIn(page, user.token);
  await openBoard(page);
  await page.getByLabel("Filter prices").fill("narayangaon");
  await page.getByTestId("board").locator("tbody tr").first().click();
  await page.getByTestId("set-alert").click();

  const form = page.getByTestId("alert-form");
  await expect(form.getByLabel("Commodity")).toHaveValue(/\d+/);
  await expect(form.locator("select[name=marketId] option:checked")).toContainText("Narayangaon");
  await expect(form.locator("#current-hint")).toContainText("Latest:");

  await form.getByRole("button", { name: "Create alert" }).click();
  await expect(form.locator('[data-for="threshold"]')).toHaveText("Enter a target price in rupees per quintal");

  await form.getByLabel("Notify me when the modal price is").selectOption("ABOVE");
  await form.getByLabel("Target (₹ per quintal)").fill("99990");
  await form.getByRole("button", { name: "Create alert" }).click();
  const row = page.getByTestId("alert-list").locator(".alert-row");
  await expect(row).toHaveCount(1);
  await expect(row).toContainText("Tomato at Narayangaon ≥ ₹99,990");
  await expect(row).toContainText("Watching");

  await row.getByRole("button", { name: "Pause" }).click();
  await expect(row).toContainText("Paused");
  page.once("dialog", (d) => d.accept());
  await row.getByRole("button", { name: "Delete" }).click();
  await expect(page.getByTestId("alert-list")).toContainText("No alerts yet");
});
