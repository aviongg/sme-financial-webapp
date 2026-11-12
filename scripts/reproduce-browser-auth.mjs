import { chromium } from '../tmp/playwright-runner/node_modules/playwright-core/index.mjs';

const TUNNEL_URL = 'https://offering-forums-marathon-calibration.trycloudflare.com';
const OWNER_EMAIL = 'demo.owner@finsight.local';
const PASSWORD = 'FinSight@Owner2026!'; // updated demo password

console.log('======================================================================');
console.log('REPRODUCING BROWSER AUTHENTICATION VIA REAL MICROSOFT EDGE');
console.log(`URL: ${TUNNEL_URL}`);
console.log('======================================================================\n');

async function reproduce() {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 }
  });

  const page = await context.newPage();

  // Network tracking
  const networkEvents = [];
  page.on('request', req => {
    const url = req.url();
    networkEvents.push({
      type: 'REQ',
      method: req.method(),
      url: url,
      headers: req.headers()
    });
    console.log(`[NET REQ] ${req.method()} ${url}`);
  });

  page.on('response', async res => {
    const url = res.url();
    let bodyText = '';
    try {
      bodyText = await res.text();
    } catch (e) {
      bodyText = '<unreadable>';
    }
    const sanitizedBody = bodyText.length > 500 ? bodyText.slice(0, 500) + '... (truncated)' : bodyText;
    console.log(`[NET RES] ${res.status()} ${url}`);
    if (url.includes('/api/')) {
      console.log(`   Response Body: ${sanitizedBody}`);
      console.log(`   Response Headers:`, JSON.stringify(res.headers(), null, 2));
    }
  });

  // Console tracking
  page.on('console', msg => {
    console.log(`[CONSOLE ${msg.type().toUpperCase()}] ${msg.text()}`);
  });

  page.on('pageerror', err => {
    console.error(`[PAGE ERROR]`, err);
  });

  // 1. Navigate to landing page
  console.log('\n--- Step 1: Navigating to Landing Page ---');
  await page.goto(TUNNEL_URL, { waitUntil: 'networkidle' });
  console.log('Landing page loaded. Title:', await page.title());

  // 2. Click Sign In
  console.log('\n--- Step 2: Clicking Sign In Link ---');
  // Check available buttons/links
  const signInButton = page.getByRole('button', { name: /Sign In|سائن ان/i }).or(page.getByRole('link', { name: /Sign In|سائن ان/i }));
  const count = await signInButton.count();
  console.log(`Found ${count} Sign In link/button(s)`);
  if (count > 0) {
    await signInButton.first().click();
  } else {
    // Navigate with ?auth=login
    await page.goto(`${TUNNEL_URL}/?auth=login`, { waitUntil: 'networkidle' });
  }

  await page.waitForTimeout(2000);
  console.log('URL after clicking Sign In:', page.url());

  // 3. Fill in email and password
  console.log('\n--- Step 3: Entering Credentials ---');
  const emailInput = page.getByLabel(/Email|ای میل/i).or(page.locator('input[type="email"]'));
  const passwordInput = page.getByLabel(/Password|پاس ورڈ/i).or(page.locator('input[type="password"]'));

  await emailInput.fill(OWNER_EMAIL);
  await passwordInput.fill(PASSWORD);
  console.log('Credentials filled in form.');

  // 4. Click Continue / Submit
  console.log('\n--- Step 4: Submitting Login Form ---');
  const submitButton = page.getByRole('button', { name: /Continue|Sign In|جاری رکھیں|داخل ہوں/i });
  await submitButton.click();

  // Wait for response
  await page.waitForTimeout(5000);

  console.log('\n--- Step 5: Inspecting Post-Submit State ---');
  console.log('Current URL:', page.url());

  // Check alert or error messages
  const alerts = page.getByRole('alert');
  const alertCount = await alerts.count();
  console.log(`Found ${alertCount} alert(s):`);
  for (let i = 0; i < alertCount; i++) {
    console.log(` Alert [${i}]:`, await alerts.nth(i).textContent());
  }

  // Check cookies in browser context
  const cookies = await context.cookies();
  console.log('\nCookies in browser context:');
  for (const c of cookies) {
    console.log(` - ${c.name}: domain=${c.domain}, path=${c.path}, httpOnly=${c.httpOnly}, secure=${c.secure}, sameSite=${c.sameSite}`);
  }

  await browser.close();
}

reproduce().catch(console.error);
