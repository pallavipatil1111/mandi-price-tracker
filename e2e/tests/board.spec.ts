import { test, expect } from "@playwright/test";
import { openBoard } from "./helpers";

test("board lists every price with weekly movers", async ({ page }) => {
  await openBoard(page);
  await expect(page.getByRole("heading", { name: "Mandi prices today" })).toBeVisible();
  await expect(page.getByTestId("board").locator("tbody tr")).toHaveCount(27);
  await expect(page.getByTestId("updated")).toContainText("Prices for");
  await expect(page.getByTestId("gainers").locator(".mover").first()).toBeVisible();
  await expect(page.getByTestId("losers").locator(".mover").first()).toBeVisible();
});

test("search and category filters narrow the board", async ({ page }) => {
  await openBoard(page);
  const rows = page.getByTestId("board").locator("tbody tr");
  await page.getByLabel("Filter prices").fill("onion");
  await expect(rows).toHaveCount(5);
  await page.getByLabel("Filter prices").fill("nashik");
  await expect(rows).toHaveCount(3); // Lasalgaon, Pimpalgaon Baswant (onion) and Nashik (tomato)
  await page.getByLabel("Filter prices").fill("");
  await page.getByRole("button", { name: "Spices" }).click();
  await expect(rows).toHaveCount(2);
  await expect(page.locator("#count")).toHaveText("2 of 27 prices");
  await page.getByLabel("Filter prices").fill("zzz");
  await expect(rows.first()).toContainText("Nothing matches");
});

test("a commodity page shows history, statistics, market comparison and seasonality", async ({ page }) => {
  await openBoard(page);
  await page.getByLabel("Filter prices").fill("lasalgaon");
  await page.getByTestId("board").locator("tbody tr").first().click();

  await expect(page.getByTestId("title")).toHaveText("Onion · Lasalgaon");
  await expect(page.getByTestId("latest")).toContainText("₹");
  await expect(page.getByTestId("chart").locator("svg path")).toHaveCount(2); // modal + 7-day average
  await expect(page.getByTestId("compare").locator(".bar")).toHaveCount(5);
  await expect(page.getByTestId("seasonality").locator("svg rect").first()).toBeVisible();

  // range and market switches are links, so they survive reloads and can be shared
  await page.getByRole("button", { name: "1Y" }).click();
  await expect(page).toHaveURL(/d=365/);
  await expect(page.getByRole("button", { name: "1Y" })).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("link", { name: "Solapur", exact: true }).click();
  await expect(page.getByTestId("title")).toHaveText("Onion · Solapur");
  await expect(page).toHaveURL(/d=365/);
});

test("hovering the chart shows that day's prices", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("Filter prices").fill("turmeric");
  await page.getByTestId("board").locator("tbody tr").first().click();
  const chart = page.getByTestId("chart").locator("svg");
  const box = (await chart.boundingBox())!;
  await page.mouse.move(box.x + box.width * 0.6, box.y + box.height * 0.5);
  await page.mouse.move(box.x + box.width * 0.62, box.y + box.height * 0.5);
  await expect(page.locator("#tooltip")).toBeVisible();
  await expect(page.locator("#tooltip")).toContainText("Modal");
  await expect(page.locator("#tooltip")).toContainText("/kg");
});

test("CSV export downloads the series with a moving average", async ({ page, request }) => {
  await openBoard(page);
  await page.getByLabel("Filter prices").fill("sangli");
  await page.getByTestId("board").locator("tbody tr").filter({ hasText: "Turmeric" }).click();
  await page.getByRole("button", { name: "30D" }).click();
  await expect(page.getByTestId("csv")).toHaveAttribute("href", /days=30/); // wait for the re-render
  const href = await page.getByTestId("csv").getAttribute("href");
  expect(href).toContain("days=30");
  const res = await request.get(href!);
  expect(res.headers()["content-disposition"]).toContain("Turmeric-Sangli-30d.csv");
  const lines = (await res.text()).trim().split("\n");
  expect(lines[0]).toBe("date,commodity,market,district,state,min_price,max_price,modal_price,moving_avg_7");
  expect(lines.length).toBeGreaterThan(20);
  expect(lines[1]).toContain(",Turmeric,Sangli,Sangli,Maharashtra,");
});
