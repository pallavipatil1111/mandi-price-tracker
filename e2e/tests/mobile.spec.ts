import { test, expect } from "@playwright/test";

test("phone layout: no sideways scrolling and the chart fits the screen", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByTestId("board").locator("tbody tr").first()).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);

  await page.getByTestId("board").locator("tbody tr").first().click();
  const chart = page.getByTestId("chart").locator("svg");
  await expect(chart).toBeVisible();
  const box = (await chart.boundingBox())!;
  expect(box.width).toBeLessThanOrEqual(page.viewportSize()!.width);
  // the chart is drawn at the phone's width, so its labels stay readable
  expect(await chart.getAttribute("viewBox")).not.toContain("860");
});
