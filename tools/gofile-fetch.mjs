// Temporary helper: resolve a public gofile share link to a direct download URL
// by driving a real Chromium browser, then save the file.
import { chromium } from 'playwright-core';
import fs from 'node:fs';
import path from 'node:path';

const SHARE = process.env.SHARE_URL || 'https://gofile.io/d/Z0nbSQmP';
const OUT = process.env.OUT_DIR || 'incoming';
fs.mkdirSync(OUT, { recursive: true });
const log = (msg) => {
  fs.appendFileSync(path.join(OUT, 'browser.log'), msg + '\n');
  console.log(msg);
};

const browser = await chromium.launch({
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
});
const ctx = await browser.newContext({ acceptDownloads: true });
const page = await ctx.newPage();

const links = new Set();
page.on('response', async (res) => {
  const url = res.url();
  if (!/gofile\.(io|is)\/(contents|getContent)/i.test(url)) return;
  try {
    const body = await res.text();
    log(`API ${url}\n${body.slice(0, 4000)}\n`);
    const json = JSON.parse(body);
    const children = json?.data?.children ?? {};
    for (const key of Object.keys(children)) {
      if (children[key]?.link) links.add(children[key].link);
    }
  } catch (err) {
    log(`API parse failed for ${url}: ${err.message}`);
  }
});

page.on('download', async (dl) => {
  const suggested = dl.suggestedFilename() || 'download.bin';
  const dest = path.join(OUT, suggested);
  await dl.saveAs(dest);
  log(`DOWNLOAD event saved: ${dest}`);
});

log(`Opening ${SHARE}`);
try {
  await page.goto(SHARE, { waitUntil: 'domcontentloaded', timeout: 90_000 });
} catch (err) {
  log(`goto warning: ${err.message}`);
}
await page.waitForTimeout(10_000);

// Try clicking through the file row / download button if present.
const candidates = [
  'button:has-text("Download")',
  'a:has-text("Download")',
  '[data-testid="download"]',
  '.btn-primary',
];
for (const sel of candidates) {
  try {
    const el = page.locator(sel).first();
    if (await el.count()) {
      await el.click({ timeout: 5_000 });
      log(`clicked ${sel}`);
      await page.waitForTimeout(5_000);
    }
  } catch (err) {
    log(`click ${sel} failed: ${err.message}`);
  }
}

await page.waitForTimeout(15_000);
log(`Captured links: ${JSON.stringify([...links])}`);
fs.writeFileSync(path.join(OUT, 'links.json'), JSON.stringify([...links], null, 2));

// Fallback: click the file row itself (whole row is often a link/button).
if (links.size === 0) {
  try {
    const row = page.locator('div[role="button"], .item, .file').first();
    if (await row.count()) {
      await row.click({ timeout: 5_000 });
      log('clicked row');
      await page.waitForTimeout(10_000);
    }
  } catch (err) {
    log(`row click failed: ${err.message}`);
  }
}

const downloads = fs.readdirSync(OUT).filter((f) => !f.endsWith('.log') && !f.endsWith('.json'));
log(`Files in ${OUT}: ${JSON.stringify(downloads)}`);

await browser.close();
if (links.size === 0 && downloads.length === 0) {
  log('NO_RESULT');
  process.exit(1);
}
