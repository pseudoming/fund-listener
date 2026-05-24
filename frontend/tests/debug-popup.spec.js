import { test, expect } from '@playwright/test';

test('Debug Popup', async ({ page }) => {
  await page.goto('http://localhost:5173/fund/050025');
  await page.waitForTimeout(2000);
  
  // Click buy button
  await page.locator('button:has-text("买入")').click();
  await page.waitForTimeout(1000);
  
  // Dump action sheet html
  const popupHtml = await page.evaluate(() => {
    const popup = document.querySelector('.van-action-sheet');
    const overlay = document.querySelector('.van-overlay');
    return {
      popup: popup ? popup.outerHTML : 'null',
      overlay: overlay ? overlay.outerHTML : 'null',
      popupStyles: popup ? window.getComputedStyle(popup).cssText : 'null'
    };
  });
  
  console.log('--- DUMP START ---');
  console.log(popupHtml.popup);
  console.log('--- DUMP END ---');
});
