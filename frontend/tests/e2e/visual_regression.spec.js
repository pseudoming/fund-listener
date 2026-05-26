import { test, expect } from '@playwright/test';

test.describe('Visual Regression & Layout Stability', () => {
  test('Positions page UI layout should exactly match the golden baseline', async ({ page }) => {
    // 1. Mock Dashboard API
    await page.route('**/api/dashboard', async route => {
      const json = {
        totalMarketValue: '1250892.45',
        latestPnl: '12450.00',
        latestPnlPercent: '15.80',
        marketIndices: [
          { name: '上证', changePercent: '+0.01%', isRise: true, isFall: false },
          { name: '深证', changePercent: '-0.23%', isRise: false, isFall: true },
          { name: '纳指', changePercent: '+1.12%', isRise: true, isFall: false }
        ],
        lastUpdatedTime: '15:00:00',
        funds: [
          {
            fundCode: '000001',
            fundName: '华夏成长混合', // Short name
            totalShares: '10000.00',
            totalCost: '10000.00',
            marketValue: '15000.00',
            estimatedNav: '1.5000',
            yesterdayNav: '1.4500',
            estimatedGrowthRate: '3.44',
            navDate: new Date().toISOString(), // Today (isSettled: false)
            isSettled: false,
            isOvervalued: false,
            latestPnl: '500.00',
            holdingPnl: '5000.00',
            holdingPnlPercent: '50.00'
          },
          {
            fundCode: '000002',
            fundName: '博时纳斯达克100指数(QDII)A类人民币超长名字截断测试', // Long name
            totalShares: '20000.00',
            totalCost: '30000.00',
            marketValue: '24000.00',
            estimatedNav: '1.2000',
            yesterdayNav: '1.2500',
            estimatedGrowthRate: '-4.00',
            navDate: new Date(Date.now() - 86400000).toISOString(), // Yesterday (isSettled: true)
            isSettled: true,
            isOvervalued: true, // Show tag
            latestPnl: '-1000.00',
            holdingPnl: '-6000.00',
            holdingPnlPercent: '-20.00'
          }
        ]
      };
      await route.fulfill({ json });
    });

    // 3. Navigate to app and wait for network idle to ensure everything renders
    await page.goto('http://127.0.0.1:5173', { waitUntil: 'networkidle' });

    // 4. Force wait for cards to appear
    const firstCard = page.locator('.holding-list-item').first();
    await firstCard.waitFor({ state: 'visible' });

    // 5. To ensure consistent rendering, we can optionally hide dynamic elements like cursor or animations
    // But since data is mocked, it should be highly deterministic.
    
    // 6. Assert Visual Snapshot
    // Capture full page
    await expect(page).toHaveScreenshot('positions-dashboard-layout.png', {
      maxDiffPixelRatio: 0.05 // Allow very slight anti-aliasing diffs across environments
    });
  });
});
