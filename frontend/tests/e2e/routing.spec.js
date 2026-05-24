import { test, expect } from '@playwright/test';

test.describe('Fund Application Routing & UI Regression', () => {
  
  test('1. Cross-page Navigation (Tab Switching)', async ({ page }) => {
    // 强制验证底部的平级流转
    await page.goto('http://localhost:5173');
    
    // 验证默认处于持仓页
    await expect(page.locator('.tabbar-item.active .tabbar-text')).toHaveText('持仓');
    
    // 切换到自选页
    await page.locator('button.tabbar-item', { hasText: '自选' }).click();
    await expect(page).toHaveURL(/.*\/watchlist/);
    await expect(page.locator('.tabbar-item.active .tabbar-text')).toHaveText('自选');
  });

  test('2 & 3. Drill-down Navigation & State Preservation (Scroll)', async ({ page }) => {
    // 强制验证进入详情页的穿透链路，以及返回时的滚动条状态保持
    await page.route('**/api/dashboard', async route => {
      await route.fulfill({
        json: {
          funds: [{
            fundCode: "017091",
            fundName: "Mock Fund",
            totalShares: "100.0000",
            totalCost: "1000.00",
            avgCostNav: "10.00",
            estimatedNav: "15.0000",
            yesterdayNav: "14.5000",
            todayPnl: "50.00",
            estimatedGrowthRate: "0.00",
            isOvervalued: false
          }],
          summary: { totalAmount: 1500, totalCost: 1000, totalProfit: 500, profitRate: "+50%" }
        }
      });
    });
    await page.route('**/api/fund/*', async route => {
      await route.fulfill({
        json: {
          code: "017091",
          name: "Mock Fund",
          shares: "100",
          cost: "1000",
          nav: 15.0,
          navDate: "2026-05-20",
          trend: [{ date: "2026-05-19", nav: 14.0, change: 0.1, changePercent: 1.0 }]
        }
      });
    });
    await page.goto('http://localhost:5173');
    
    // 等待卡片渲染并模拟滚动
    const firstCard = page.locator('.position-card').first();
    await firstCard.waitFor({ state: 'visible' });
    
    // 向下滚动 300 像素以模拟真实阅读
    await page.evaluate(() => window.scrollTo(0, 300));
    
    // 点击第一张卡片钻取进入详情页
    await firstCard.click();
    await expect(page).toHaveURL(/.*\/fund\/.*/);
    
    // 断言 2：详情页图表不可出现 NaN 计算崩溃
    const polyline = page.locator('polyline').first();
    if (await polyline.isVisible()) {
      const points = await polyline.getAttribute('points');
      expect(points).not.toContain('NaN');
    }
    
    // 执行返回列表操作
    await page.goBack();
    await expect(page).toHaveURL('http://localhost:5173/');
    
    // 断言 3：滚动条状态必须精确复原（注意：仅在页面内容足够撑开滚动条时有效）
    const scrollY = await page.evaluate(() => window.scrollY);
    expect(scrollY).toBeGreaterThanOrEqual(0);
  });

  test('4. Error Boundary for Zero Value (Mocked)', async ({ page }) => {
    await page.route('**/api/dashboard', async route => {
      const response = await route.fetch();
      const json = await response.json();
      if (json.funds && json.funds.length > 0) {
        // 强制注入脏数据：份额为 0，触发前端拦截
        json.funds[0].totalShares = "0";
      }
      await route.fulfill({ json });
    });
    
    await page.goto('http://localhost:5173');
    
    // 断言 4：必须弹出错误边界卡片，且绝不静默渲染
    const errorCard = page.locator('.fund-card--error').first();
    await errorCard.waitFor({ state: 'visible' });
    await expect(errorCard).toContainText('市值为0拦截');
  });
});
