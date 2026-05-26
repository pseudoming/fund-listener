import { test, expect } from '@playwright/test'

test.describe('Visual Regression Tests', () => {
  // Use a fixed viewport for consistent screenshots
  test.use({ viewport: { width: 375, height: 812 } })

  test('Dashboard should match golden baseline', async ({ page }) => {
    // Log console and errors
    page.on('console', msg => console.log('PAGE LOG:', msg.text()));
    page.on('pageerror', error => console.error('PAGE ERROR:', error.message));

    // Mock the backend API so we have deterministic data for the visual test
    await page.route('**/api/dashboard', async route => {
      const mockData = {
        totalCost: "100000.00",
        totalMarketValue: "115000.00",
        latestPnl: "250.00",
        latestPnlPercent: "0.25",
        overvaluedCount: 1,
        overvaluedThreshold: 80,
        lastUpdatedTime: "2026-05-26 15:00",
        marketIndices: [
          { name: '上证指数', changePercent: '-0.45', isRise: false, isFall: true },
          { name: '深证成指', changePercent: '-0.32', isRise: false, isFall: true },
          { name: '纳斯达克', changePercent: '0.19', isRise: true, isFall: false }
        ],
        funds: [
          {
            fundCode: '050025',
            fundName: '博时标普500ETF联接A',
            totalShares: '10000.00',
            totalCost: '50000.00',
            avgCostNav: '5.0000',
            estimatedNav: '5.5000',
            estimatedGrowthRate: '0.50',
            pePercentile: '85.0',
            pbPercentile: '70.0',
            isOvervalued: true,
            latestPnl: '100.00',
            yesterdayNav: '5.4500',
            isSettled: true,
            navDate: '2026-05-25'
          },
          {
            fundCode: '006195',
            fundName: '国金量化多因子股票A(特别长的一个名字用来测试两行折叠效果)',
            totalShares: '10000.00',
            totalCost: '20000.00',
            avgCostNav: '2.0000',
            estimatedNav: '2.1000',
            estimatedGrowthRate: '-1.00',
            pePercentile: '40.0',
            pbPercentile: '30.0',
            isOvervalued: false,
            latestPnl: '-50.00',
            yesterdayNav: '2.1500',
            isSettled: false,
            navDate: '2026-05-26'
          },
          {
            fundCode: '486002',
            fundName: '工银全球精选股票(QDII)',
            totalShares: '10000.00',
            totalCost: '30000.00',
            avgCostNav: '3.0000',
            estimatedNav: '3.2000',
            estimatedGrowthRate: '0.20',
            pePercentile: '60.0',
            pbPercentile: '50.0',
            isOvervalued: false,
            latestPnl: '200.00',
            yesterdayNav: '3.1800',
            isSettled: true,
            navDate: '2026-05-25'
          }
        ]
      }
      await route.fulfill({ json: mockData })
    })

    // Navigate to the app
    await page.goto('http://127.0.0.1:5173/')
    
    // Wait for the app to be fully loaded (the holding list to appear)
    await page.waitForSelector('.position-list')
    
    // Wait a brief moment for any animations/loading
    await page.waitForTimeout(1000)

    // Take a screenshot of the main content area (so we don't capture system clocks or dynamic dates if we can avoid it)
    // Actually, taking a screenshot of the whole page is better. We can mask dynamic elements like time/date.
    await expect(page).toHaveScreenshot('dashboard-golden-baseline.png', {
      maxDiffPixels: 50, 
      mask: [
        page.locator('.header-time'), // If there is a clock
        page.locator('.val-market'), // Dynamic values might change, but for tests they should be mocked or stable
        page.locator('.val-pnl'),
        page.locator('.val-holding-amt'),
        page.locator('.val-holding-rate'),
        page.locator('.fund-tags'), // In case estimation tags change dynamically
      ]
    })
  })
})
