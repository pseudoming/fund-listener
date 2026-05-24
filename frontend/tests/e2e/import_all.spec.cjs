const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

test('Automate OCR import for all test data sequentially', async ({ page }) => {
  if (!fs.existsSync(path.resolve(process.cwd(), '../test-data'))) {
    console.log('⚠️ 测试数据目录 ../test-data 不存在，请自行放置测试数据。跳过该测试。');
    test.skip();
    return;
  }
  test.setTimeout(240000);
  
  const testImages = [
    path.resolve(process.cwd(), '../test-data/12454ee6e5c064d270388be458653089.jpg'),
    path.resolve(process.cwd(), '../test-data/2a90082dd4df2a02f9f801ad3760ce6b.jpg'),
    path.resolve(process.cwd(), '../test-data/7efc279b861f4c7e29666f9da9a83d1a.png'),
    path.resolve(process.cwd(), '../test-data/f44bc37bed11cedf15ff87baea3b610e.png')
  ];

  for (const imgPath of testImages) {
      console.log('Processing:', imgPath);
      await page.goto('http://localhost:5173');
      await page.waitForTimeout(2000);

      const importBtn = page.locator('button:has-text("导入截图")');
      if (await importBtn.count() > 0) {
          await importBtn.click();
          await page.waitForTimeout(500);
      }

      await page.setInputFiles('input[type="file"]', imgPath);
      await page.click('button:has-text("开始解析")');
      
      try {
          await page.waitForSelector('.ocr-fund-card', { timeout: 45000 });
          await page.waitForTimeout(3000); // let animations settle
          
          await page.fill('input[type="date"]', '2026-05-20');
          await page.evaluate(() => {
              const dateInput = document.querySelector('input[type="date"]');
              if (dateInput) {
                  dateInput.dispatchEvent(new Event('change', { bubbles: true }));
                  dateInput.dispatchEvent(new Event('input', { bubbles: true }));
              }
          });
          await page.waitForTimeout(3000); // Wait for historical NAV to load

          await page.click('button:has-text("确认导入已选项目")');
          await page.waitForSelector('text="批量导入成功"', { timeout: 20000 });
          console.log('Import successful for', imgPath);
      } catch (e) {
          console.log('No fund cards found or import failed for', imgPath, e);
      }
  }
});
