const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

test('Screenshot Select Dropdown', async ({ page }) => {
  if (!fs.existsSync(path.resolve(process.cwd(), '../test-data'))) {
    console.log('⚠️ 测试数据目录 ../test-data 不存在，请自行放置测试数据。跳过该测试。');
    test.skip();
    return;
  }
  console.log('Navigating to app...');
  await page.goto('http://localhost:5173');
  
  await page.waitForTimeout(2000);

  console.log('Clicking Import Screenshot...');
  const importBtn = page.locator('button', { hasText: '导入截图' });
  if (await importBtn.count() > 0) {
    await importBtn.click();
  }

  console.log('Uploading 1 file...');
  await page.waitForSelector('input[type="file"]', { state: 'attached' });
  
  const testImages = [
    path.resolve(process.cwd(), '../test-data/f44bc37bed11cedf15ff87baea3b610e.png')
  ];
  
  await page.setInputFiles('input[type="file"]', testImages);
  await page.waitForTimeout(1000);

  console.log('Clicking Start Parsing...');
  const startBtn = page.locator('button', { hasText: '开始解析' });
  await startBtn.waitFor({ state: 'visible' });
  await startBtn.click();

  console.log('Waiting for confirmation list...');
  await page.waitForSelector('.ocr-fund-card', { timeout: 60000 });
  await page.waitForTimeout(3000); // Wait for candidates
  
  console.log('Taking screenshot...');
  await page.screenshot({ path: path.resolve(process.cwd(), '../ocr_select_fixed.png'), fullPage: true });

  console.log('DONE');
});
