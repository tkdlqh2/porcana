const puppeteer = require('puppeteer');
const path = require('path');
const fs = require('fs');

const screens = [
  { file: '01-home.html', name: '01-home', width: 375, height: 812 },
  { file: '02-portfolio-list.html', name: '02-portfolio-list', width: 375, height: 812 },
  { file: '03-portfolio-detail.html', name: '03-portfolio-detail', width: 375, height: 812 },
  { file: '03-b-portfolio-detail-with-seed.html', name: '03-b-portfolio-detail-with-seed', width: 375, height: 812 },
  { file: '04-weight-edit.html', name: '04-weight-edit', width: 375, height: 812 },
  { file: '05-create-select.html', name: '05-create-select', width: 375, height: 812 },
  { file: '06-library-select.html', name: '06-library-select', width: 812, height: 375, landscape: true },
  { file: '07-weight-setting.html', name: '07-weight-setting', width: 375, height: 812 },
  { file: '08-create-name.html', name: '08-create-name', width: 375, height: 812 },
  { file: '09-mypage.html', name: '09-mypage', width: 375, height: 812 },
  { file: '10-seed-setting.html', name: '10-seed-setting', width: 375, height: 812 },
  { file: '11-holding-baseline.html', name: '11-holding-baseline', width: 375, height: 812 },
  { file: '12-topup-plan.html', name: '12-topup-plan', width: 375, height: 812 },
  { file: '13-asset-detail.html', name: '13-asset-detail', width: 375, height: 812 },
];

async function takeScreenshots() {
  const screenshotsDir = path.join(__dirname, 'screenshots');

  // Create screenshots directory if not exists
  if (!fs.existsSync(screenshotsDir)) {
    fs.mkdirSync(screenshotsDir, { recursive: true });
  }

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  for (const screen of screens) {
    const filePath = path.join(__dirname, screen.file);

    if (!fs.existsSync(filePath)) {
      console.log(`Skipping ${screen.file} - file not found`);
      continue;
    }

    const page = await browser.newPage();

    await page.setViewport({
      width: screen.width,
      height: screen.height,
      deviceScaleFactor: 2
    });

    const fileUrl = `file://${filePath.replace(/\\/g, '/')}`;
    await page.goto(fileUrl, { waitUntil: 'networkidle0' });

    // Wait a bit for any animations
    await new Promise(r => setTimeout(r, 500));

    const screenshotPath = path.join(screenshotsDir, `${screen.name}.png`);

    await page.screenshot({
      path: screenshotPath,
      fullPage: true
    });

    console.log(`Screenshot saved: ${screen.name}.png`);
    await page.close();
  }

  await browser.close();
  console.log('\nAll screenshots completed!');
  console.log(`Screenshots saved to: ${screenshotsDir}`);
}

takeScreenshots().catch(console.error);