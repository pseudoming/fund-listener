// 本文件测试 OCR 批量截屏导入、全量删除旧持仓、以及交易日期覆盖等核心操作完整链路
import { test, expect } from '@playwright/test';
import path from 'path';
import fs from 'fs';

test('Automate OCR batch import, deletion, and re-import', async ({ page }) => {
  if (!fs.existsSync(path.resolve(process.cwd(), '../test-data'))) {
    console.log('⚠️ 测试数据目录不存在，跳过该测试。');
    test.skip();
    return;
  }
  test.setTimeout(180000); // 3 minutes timeout

  console.log('Navigating to app...');
  await page.goto('http://127.0.0.1:5173');
  await page.waitForTimeout(2000);

  // 弱断言：防止出现数据异常导致 NaN / undefined
  const appText = await page.innerText('body');
  expect(appText).not.toMatch(/NaN/i);
  expect(appText).not.toMatch(/undefined/i);

  // 1. 删除现有持仓数据，保证干净的测试环境
  console.log('Scrolling to find batch manage button...');
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  
  const manageBtn = page.locator('.btn-batch-manage');
  if (await manageBtn.count() > 0 && await manageBtn.isVisible()) {
    console.log('Clicking batch manage...');
    await manageBtn.click();
    await page.waitForTimeout(1000);
    
    console.log('Clicking Select All...');
    const selectAllBtn = page.locator('.panel-select-all');
    if (await selectAllBtn.count() > 0) {
      await selectAllBtn.click();
    }
    await page.waitForTimeout(1000);

    console.log('Scrolling to delete button...');
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    const deleteBtn = page.locator('button', { hasText: /删除已选/ });
    if (await deleteBtn.count() > 0 && await deleteBtn.isVisible()) {
      console.log('Clicking Delete...');
      await deleteBtn.click();
      
      console.log('Confirming dialog...');
      const confirmBtn = page.locator('.van-dialog__confirm');
      await confirmBtn.waitFor({ state: 'visible' });
      await confirmBtn.click();
      await page.waitForTimeout(2000);
    }
  }

  // 1.5 删除现有自选数据
  console.log('Navigating to watchlist...');
  await page.goto('http://127.0.0.1:5173/watchlist');
  await page.waitForTimeout(2000);
  console.log('Scrolling to find batch manage button on watchlist...');
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  
  const manageWatchlistBtn = page.locator('.btn-batch-manage');
  if (await manageWatchlistBtn.count() > 0 && await manageWatchlistBtn.isVisible()) {
    console.log('Clicking batch manage on watchlist...');
    await manageWatchlistBtn.click();
    await page.waitForTimeout(1000);
    
    console.log('Clicking Select All...');
    const selectAllBtn = page.locator('.panel-select-all');
    if (await selectAllBtn.count() > 0) {
      await selectAllBtn.click();
    }
    await page.waitForTimeout(1000);

    console.log('Scrolling to delete button...');
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    const deleteBtn = page.locator('button', { hasText: /取消自选/ });
    if (await deleteBtn.count() > 0 && await deleteBtn.isVisible()) {
      console.log('Clicking Delete...');
      await deleteBtn.click();
      
      console.log('Confirming dialog...');
      const confirmBtn = page.locator('.van-dialog__confirm');
      await confirmBtn.waitFor({ state: 'visible' });
      await confirmBtn.click();
      await page.waitForTimeout(2000);
    }
  }

  await page.goto('http://127.0.0.1:5173');
  await page.waitForTimeout(2000);

  // 2. 导入4张截图进行 OCR 解析
  console.log('Clicking Import Screenshot...');
  const importBtn = page.locator('button', { hasText: '导入截图' });
  if (await importBtn.count() > 0) {
    await importBtn.click();
  }

  console.log('Uploading 4 files...');
  await page.waitForSelector('input[type="file"]', { state: 'attached' });
  
  const testImages = [
    path.resolve(process.cwd(), '../test-data/12454ee6e5c064d270388be458653089.jpg'),
    path.resolve(process.cwd(), '../test-data/2a90082dd4df2a02f9f801ad3760ce6b.jpg'),
    path.resolve(process.cwd(), '../test-data/7efc279b861f4c7e29666f9da9a83d1a.png'),
    path.resolve(process.cwd(), '../test-data/f44bc37bed11cedf15ff87baea3b610e.png')
  ];
  
  await page.setInputFiles('input[type="file"]', testImages);
  await page.waitForTimeout(1000);

  console.log('Clicking Start Parsing...');
  const startBtn = page.locator('button', { hasText: '开始解析' });
  await startBtn.waitFor({ state: 'visible' });
  await startBtn.click();

  console.log('Waiting for confirmation list...');
  await page.waitForSelector('.ocr-fund-card', { timeout: 120000 });
  await page.waitForTimeout(3000); // Wait for API fallbacks to render dropdowns

  // 3. 修改交易日期并确认导入
  console.log('将交易日期设置为 2026-05-20...');
  await page.fill('input[type="date"]', '2026-05-20');
  await page.evaluate(() => {
      const dateInput = document.querySelector('input[type="date"]');
      if (dateInput) {
          dateInput.dispatchEvent(new Event('change', { bubbles: true }));
          dateInput.dispatchEvent(new Event('input', { bubbles: true }));
      }
  });
  
  console.log('⏳ 等待历史净值同步...');
  await page.waitForTimeout(3000);

  const importItemCount = await page.locator('.ocr-fund-card').count();
  console.log(`准备导入 ${importItemCount} 个项目...`);

  console.log('确认批量导入...');
  await page.click('button:has-text("确认导入已选项目")');

  console.log('⏳ 等待导入成功提示...');
  await page.waitForSelector('text="批量导入成功"', { timeout: 20000 });
  console.log('✅ 批量导入成功！');

  await page.waitForTimeout(3000); // Wait for positions to render

  // 5. 验证持仓页数量与导航逻辑
  const positionCount = await page.locator('.holding-list-item').count();
  console.log(`持仓页实际渲染数量: ${positionCount}`);
  expect(positionCount).toBe(importItemCount);

  // 4.5 真实环境一条龙验证：组合图表与穿透模块是否渲染成功
  console.log('导航到持仓分析页，验证组合走势图、饼图与市场分类渲染...');
  const analysisBtn = page.locator('.analysis-btn').first();
  await analysisBtn.waitFor({ state: 'visible' });
  await analysisBtn.click();
  await page.waitForTimeout(2000);

  const trendChartCanvas = page.locator('.trend-wrap canvas');
  await expect(trendChartCanvas).toBeVisible({ timeout: 15000 });
  
  const pieChartCanvas = page.locator('.penetration-wrap .chart-container canvas');
  await expect(pieChartCanvas).toBeVisible({ timeout: 15000 });
  
  const penetrationWrap = page.locator('.penetration-wrap');
  // 至少应该有某种市场类型的股票被渲染出来（因为导入了真实基金数据）
  const hasMarketType = await penetrationWrap.evaluate(node => {
    return /A股|港股|美股|台股/.test(node.textContent);
  });
  expect(hasMarketType).toBeTruthy();
  // 确保没有出现之前未修复的 UNKNOWN 状态
  await expect(penetrationWrap).not.toContainText('UNKNOWN');

  // 5. 验证自选数量
  console.log('Navigating to watchlist to verify...');
  await page.goto('http://127.0.0.1:5173/watchlist');
  await page.waitForTimeout(3000); // Wait for watchlist to load
  
  const watchlistCount = await page.locator('.watchlist-card').count();
  console.log(`自选页实际渲染数量: ${watchlistCount}`);
  expect(watchlistCount).toBe(importItemCount);

  console.log('✅ 数量校验通过！');
});
