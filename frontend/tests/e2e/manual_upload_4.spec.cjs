const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

test('Manual Browser Automation for Upload 4 Files', async ({ page }) => {
  if (!fs.existsSync(path.resolve(process.cwd(), '../test-data'))) {
    console.log('⚠️ 测试数据目录 ../test-data 不存在，请自行放置测试数据。跳过该测试。');
    test.skip();
    return;
  }
  test.setTimeout(180000); // 3 minutes timeout

  console.log('Navigating to app...');
  await page.goto('http://localhost:5173');
  
  await page.waitForTimeout(2000);

  // Scroll to bottom to find batch manage button
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

  // Reload page to be safe
  await page.goto('http://localhost:5173');
  await page.waitForTimeout(2000);

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
  // Wait for the popup/confirmation list to render
  await page.waitForSelector('.ocr-fund-card', { timeout: 60000 });
  
  // Wait an extra few seconds for any API fallbacks to render the <select> dropdowns
  await page.waitForTimeout(5000);
  
  console.log('Taking screenshot...');
  await page.screenshot({ path: path.resolve(process.cwd(), '../ocr_confirmation_list_final.png'), fullPage: true });

  console.log('DONE');
});
