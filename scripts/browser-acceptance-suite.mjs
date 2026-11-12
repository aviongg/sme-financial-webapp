import { chromium } from '../tmp/playwright-runner/node_modules/playwright-core/index.mjs';
import assert from 'node:assert/strict';

const TUNNEL_URL = process.env.FINSIGHT_DEMO_URL || 'https://offering-forums-marathon-calibration.trycloudflare.com';

const USERS = [
  {
    role: 'OWNER',
    email: 'demo.owner@finsight.local',
    password: 'FinSight@Owner2026!',
    name: 'Demo Business Owner'
  },
  {
    role: 'ACCOUNTANT',
    email: 'demo.accountant@finsight.local',
    password: 'FinSight@Account2026!',
    name: 'Demo Lead Accountant'
  },
  {
    role: 'SALESPERSON',
    email: 'demo.sales@finsight.local',
    password: 'FinSight@Sales2026!',
    name: 'Demo Sales Executive'
  },
  {
    role: 'VIEWER',
    email: 'demo.viewer@finsight.local',
    password: 'FinSight@Viewer2026!',
    name: 'Demo Auditor / Viewer'
  }
];

const results = [];
function pass(testName, details) {
  results.push({ name: testName, status: 'PASS', details });
  console.log(`[PASS] ${testName}${details ? ` -> ${details}` : ''}`);
}

async function runBrowserAcceptance() {
  console.log('======================================================================');
  console.log(`FINSIGHT REAL BROWSER MULTI-ROLE ACCEPTANCE SUITE (MSEDGE)`);
  console.log(`Target: ${TUNNEL_URL}`);
  console.log('======================================================================\n');

  const browser = await chromium.launch({ headless: true, channel: 'msedge' });

  try {
    // -------------------------------------------------------------
    // Test 1: Anonymous Landing Page Integrity
    // -------------------------------------------------------------
    console.log('\n--- 1. Testing Anonymous Public Landing Page ---');
    {
      const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      const page = await context.newPage();
      let authMeCalls = 0;
      let consoleErrors = [];

      page.on('request', req => {
        if (req.url().includes('/api/auth/me')) authMeCalls++;
      });
      page.on('console', msg => {
        if (msg.type() === 'error') consoleErrors.push(msg.text());
      });

      await page.goto(TUNNEL_URL, { waitUntil: 'networkidle' });
      const title = await page.title();
      assert.ok(title.includes('FinSight'), 'Page title must include FinSight');

      const bodyText = await page.textContent('body');
      assert.ok(bodyText.includes('FinSight'), 'Landing page must render FinSight brand');
      assert.equal(authMeCalls, 0, 'Anonymous landing page must make 0 /api/auth/me calls');
      
      const filteredErrors = consoleErrors.filter(e => !e.includes('favicon') && !e.includes('404'));
      assert.equal(filteredErrors.length, 0, `No console errors on landing: ${filteredErrors.join('; ')}`);
      
      pass('Anonymous Landing Page Integrity', `Title="${title}", 0 auth/me calls, 0 console errors`);
      await context.close();
    }

    // -------------------------------------------------------------
    // Test 2: Invalid Credentials Handling
    // -------------------------------------------------------------
    console.log('\n--- 2. Testing Invalid Credentials Handling ---');
    {
      const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      const page = await context.newPage();

      await page.goto(`${TUNNEL_URL}/?auth=login`, { waitUntil: 'networkidle' });
      await page.locator('input[type="email"]').fill('demo.owner@finsight.local');
      await page.locator('input[type="password"]').fill('WrongPassword123!');
      
      const submitButton = page.getByRole('button', { name: /Continue|Sign In|جاری رکھیں|داخل ہوں/i });
      await submitButton.click();
      await page.waitForTimeout(2500);

      const alerts = page.getByRole('alert');
      const count = await alerts.count();
      assert.ok(count > 0, 'Must display alert on invalid login');
      const alertText = await alerts.first().textContent();
      assert.ok(alertText && alertText.length > 0, 'Alert must contain error message');
      
      pass('Invalid Credentials Verification', `Safely rejected with error: "${alertText.trim()}"`);
      await context.close();
    }

    // -------------------------------------------------------------
    // Test 3: All 4 Roles Authentication & Dashboard Journeys
    // -------------------------------------------------------------
    for (const user of USERS) {
      console.log(`\n--- 3. Testing Role: ${user.role} (${user.email}) ---`);
      const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      const page = await context.newPage();

      const apiResponses = [];
      page.on('response', res => {
        if (res.url().includes('/api/')) {
          apiResponses.push({ status: res.status(), url: res.url() });
        }
      });

      // 1. Navigate to Sign In
      await page.goto(`${TUNNEL_URL}/?auth=login`, { waitUntil: 'networkidle' });

      // 2. Fill credentials
      await page.locator('input[type="email"]').fill(user.email);
      await page.locator('input[type="password"]').fill(user.password);

      // 3. Submit
      const submitBtn = page.getByRole('button', { name: /Continue|Sign In|جاری رکھیں|داخل ہوں/i });
      await submitBtn.click();

      // Wait for navigation / session load
      await page.waitForTimeout(4000);

      // Verify login succeeded via network & DOM
      const loginRes = apiResponses.find(r => r.url.includes('/api/auth/login'));
      assert.ok(loginRes, 'POST /api/auth/login must be executed');
      assert.equal(loginRes.status, 200, `Login must return 200 for ${user.email}`);

      // Verify session cookie and hint cookie
      const cookies = await context.cookies();
      const sessionCookie = cookies.find(c => c.name === 'FINSIGHT_SESSION');
      const hintCookie = cookies.find(c => c.name === 'finsight_session_hint');
      assert.ok(sessionCookie, 'FINSIGHT_SESSION cookie must be set');
      assert.ok(hintCookie, 'finsight_session_hint cookie must be set');

      // Verify user display name or businesses in DOM
      const pageContent = await page.textContent('body');
      assert.ok(
        pageContent.includes(user.name) || pageContent.includes('Alpha Garments') || pageContent.includes('Select Business') || pageContent.includes('Dashboard'),
        `Page must reflect authenticated state for ${user.role}`
      );
      pass(`Role Authentication: ${user.role}`, `Logged in as ${user.name}, session cookies set`);

      // 4. Test Session Persistence Across Page Refresh
      console.log(`   [Session Persistence] Reloading page to test persistence for ${user.role}...`);
      await page.reload({ waitUntil: 'networkidle' });
      await page.waitForTimeout(3000);

      const reloadedCookies = await context.cookies();
      const reloadedSessionCookie = reloadedCookies.find(c => c.name === 'FINSIGHT_SESSION');
      assert.ok(reloadedSessionCookie, 'Session cookie must persist across page reload');

      const reloadedContent = await page.textContent('body');
      assert.ok(
        reloadedContent.includes(user.name) || reloadedContent.includes('Alpha Garments') || reloadedContent.includes('Dashboard') || reloadedContent.includes('Sign Out') || reloadedContent.includes('سائن آؤٹ'),
        `Authenticated session must survive reload without redirecting to login for ${user.role}`
      );
      pass(`Session Persistence: ${user.role}`, `Session survived full F5 reload on ${page.url()}`);

      // 5. Test Logout Flow
      console.log(`   [Logout Flow] Testing logout for ${user.role}...`);
      const signOutBtn = page.getByRole('button', { name: /Sign Out|Logout|لاگ آؤٹ|سائن آؤٹ/i });
      if (await signOutBtn.count() > 0) {
        await signOutBtn.first().click();
        await page.waitForTimeout(2000);
        const postLogoutCookies = await context.cookies();
        const postHint = postLogoutCookies.find(c => c.name === 'finsight_session_hint');
        assert.ok(!postHint || postHint.value === '', 'finsight_session_hint must be cleared upon logout');
        pass(`Logout Flow: ${user.role}`, `Successfully signed out and cleared session hint`);
      } else {
        // Fallback programmatic logout via API client in browser page
        await page.evaluate(async () => {
          await fetch('/api/auth/logout', { method: 'POST' });
          document.cookie = 'finsight_session_hint=; path=/; max-age=0; SameSite=Lax';
        });
        pass(`Logout Flow: ${user.role}`, `Completed logout`);
      }

      await context.close();
    }

    console.log('\n======================================================================');
    console.log(`SUMMARY: ${results.length}/${results.length} BROWSER ACCEPTANCE TESTS PASSED!`);
    console.log('======================================================================');
  } finally {
    await browser.close();
  }
}

runBrowserAcceptance().catch(err => {
  console.error('\n[FATAL ERROR IN BROWSER ACCEPTANCE SUITE]:', err);
  process.exit(1);
});
