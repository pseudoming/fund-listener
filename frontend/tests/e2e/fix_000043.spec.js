import { test, expect } from '@playwright/test';
import path from 'path';
import fs from 'fs';

test('Automate UI deletion and OCR re-import for 000043', async ({ page }) => {
  if (!fs.existsSync(path.resolve(process.cwd(), '../test-data'))) {
    console.log('⚠️ 测试数据目录 ../test-data 不存在，请自行放置测试数据。跳过该测试。');
    test.skip();
    return;
  }
  test.setTimeout(120000);
  console.log('启动页面自动化操作 (000043)...');

  console.log('访问应用首页...');
  await page.goto('http://localhost:5173');
  
  await page.waitForTimeout(2000);

  console.log('查找“嘉实美国成长”...');
  const fundCard = page.locator('.fund-card:has-text("嘉实美国")').first();
  if (await fundCard.count() > 0) {
    await fundCard.click();
    
    console.log('进入详情页，准备删除...');
    await page.waitForSelector('.delete-btn-mini', { state: 'visible' });
    await page.click('.delete-btn-mini');

    console.log('确认删除弹出框...');
    await page.waitForSelector('.van-dialog__confirm', { state: 'visible' });
    await page.click('.van-dialog__confirm');
    
    await page.waitForTimeout(1500);
    console.log('✅ 删除操作完成');
  } else {
    console.log('⚠️ 未在首页找到嘉实美国，可能已被删除');
  }

  console.log('返回首页准备重新导入...');
  await page.goto('http://localhost:5173');
  await page.waitForTimeout(1000);

  console.log('展开 OCR 导入区域...');
  const importBtn = page.locator('button:has-text("导入截图")');
  if (await importBtn.count() > 0) {
      await importBtn.click();
  }

  console.log('上传测试截图...');
  await page.waitForSelector('input[type="file"]', { state: 'attached' });
  
  const testImages = [
    path.resolve(process.cwd(), '../test-data/f44bc37bed11cedf15ff87baea3b610e.png')
  ];
  
  await page.setInputFiles('input[type="file"]', testImages);

  console.log('点击开始解析...');
  await page.waitForSelector('button:has-text("开始解析")');
  await page.click('button:has-text("开始解析")');

  console.log('⏳ 等待 OCR 解析完成...');
  await page.waitForSelector('.ocr-fund-card', { timeout: 30000 });
  
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

  console.log('确认批量导入...');
  await page.click('button:has-text("确认导入已选项目")');

  console.log('⏳ 等待导入成功提示...');
  await page.waitForSelector('text="批量导入成功"', { timeout: 15000 });

  console.log('✅ 页面自动化操作全部顺利完成！');
});
