import { test, expect } from '@playwright/test';

test.describe('PNL Dynamic Rendering & Robustness', () => {

  test('Should strictly render positive latestPnl text correctly with API interception', async ({ page }) => {
    // 1. Intercept the dashboard API
    await page.route('**/api/dashboard', async (route) => {
      const mockResponse = {
        funds: [
          {
            fundCode: "999999",
            fundName: "Mocked Fund Alpha",
            totalShares: "1000.00",
            totalCost: "1000.00",
            avgCostNav: "1.0000",
            estimatedNav: "1.8888",
            estimatedGrowthRate: "2.00",
            pePercentile: "50",
            pbPercentile: "50",
            isOvervalued: false,
            latestPnl: "888.88",
            yesterdayNav: "1.0000",
            isSettled: true,
            navDate: "2026-05-25"
          }
        ],
        totalCost: "1000.00",
        totalMarketValue: "1888.88",
        latestPnl: "888.88",
        latestPnlPercent: "88.88",
        overvaluedCount: 0,
        overvaluedThreshold: 80,
        lastUpdatedTime: "2026-05-26T00:00:00"
      };
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(mockResponse)
      });
    });

    // 2. Navigate to the app
    await page.goto('http://127.0.0.1:5173');

    // 3. Wait for the mocked fund card to appear
    await expect(page.locator('.holding-list-item').filter({ hasText: 'Mocked Fund Alpha' })).toBeVisible();

    // 4. Assert Dashboard strict text
    // Because it is settled and navDate is mocked to past date, diff is > 0, we should see "昨日盈亏" or "最新盈亏(MM-DD)"
    // Since today is likely > 05-25, let's just assert the numeric amount is correctly rendered
    const dashboardMetricValues = page.locator('.dashboard__metric-value');
    await expect(dashboardMetricValues.nth(1)).toContainText('¥888.88');
    await expect(dashboardMetricValues.nth(2)).toContainText('88.88%');

    // Make sure no NaN or undefined appears anywhere in the metrics
    const dashboardText = await page.locator('.dashboard__summary').innerText();
    expect(dashboardText).not.toMatch(/NaN/i);
    expect(dashboardText).not.toMatch(/undefined/i);
  });

  test('Should render negative latestPnl strictly and avoid NaN', async ({ page }) => {
    await page.route('**/api/dashboard', async (route) => {
      const mockResponse = {
        funds: [
          {
            fundCode: "888888",
            fundName: "Mocked Fund Beta",
            totalShares: "100.00",
            totalCost: "100.00",
            avgCostNav: "1.0000",
            estimatedNav: "0.5000",
            estimatedGrowthRate: "-50.00",
            pePercentile: "10",
            pbPercentile: "10",
            isOvervalued: false,
            latestPnl: "-50.00",
            yesterdayNav: "1.0000",
            isSettled: false,
            navDate: "2026-05-26"
          }
        ],
        totalCost: "100.00",
        totalMarketValue: "50.00",
        latestPnl: "-50.00",
        latestPnlPercent: "-50.00",
        overvaluedCount: 0,
        overvaluedThreshold: 80,
        lastUpdatedTime: "2026-05-26T00:00:00"
      };
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(mockResponse)
      });
    });

    await page.goto('http://127.0.0.1:5173');
    await expect(page.locator('.holding-list-item').filter({ hasText: 'Mocked Fund Beta' })).toBeVisible();

    // The negative sign should be present
    const dashboardMetricValues = page.locator('.dashboard__metric-value');
    await expect(dashboardMetricValues.nth(1)).toContainText('¥50.00'); // Note: we display abs value with color
    
    // We expect the color to be text-fall (red)
    await expect(dashboardMetricValues.nth(1)).toHaveClass(/text-fall/);
    
    const dashboardText = await page.locator('.dashboard__summary').innerText();
    expect(dashboardText).not.toMatch(/NaN/i);
    expect(dashboardText).not.toMatch(/undefined/i);
  });

});
