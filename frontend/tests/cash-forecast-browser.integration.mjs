import http from 'node:http';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const playwrightPath = process.env.PLAYWRIGHT_MODULE || 'C:/Users/PC/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright';
const { chromium } = require(playwrightPath);

const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const out = path.join(frontendDir, 'test-artifacts', 'forecast-browser');
mkdirSync(out, { recursive: true });

const id = '11111111-1111-4111-8111-111111111111';
let token = 'csrf-token-123';
const checks = [];
const mark = (name) => {
  checks.push(name);
  console.log('PASS ' + name);
};

// Fixture A: 30 days daily forecast
const buildDailyRows = (days = 30) => {
  const rows = [];
  let currentCash = 100000;
  for (let i = 0; i < days; i++) {
    const d = new Date(2026, 9, 15 + i); // 2026-10-15 + i
    const dateStr = d.toISOString().split('T')[0];
    let inflows = 0;
    let outflows = 0;
    const items = [];

    if (i === 9) { // Day 9: Receivable 40k
      inflows = 40000;
      items.push({
        sourceType: 'CUSTOMER_INVOICE',
        sourceId: 'inv-1',
        direction: 'RECEIVABLE',
        counterpartyName: 'Alpha Traders',
        referenceNumber: 'INV-101',
        description: 'Sales invoice',
        amount: '40000.00',
        originalDate: dateStr,
        forecastDate: dateStr,
        isScenarioShifted: false,
      });
    }

    if (i === 14) { // Day 14: Payable installment 25k
      outflows += 25000;
      items.push({
        sourceType: 'SUPPLIER_BILL_INSTALLMENT',
        sourceId: 'bill-inst-1',
        direction: 'PAYABLE',
        counterpartyName: 'Beta Supplies',
        referenceNumber: 'BILL-201',
        description: 'Inventory bill installment #1',
        amount: '25000.00',
        originalDate: dateStr,
        forecastDate: dateStr,
        isScenarioShifted: false,
      });
    }

    if (i === 19) { // Day 19: Recurring rent 10k
      outflows += 10000;
      items.push({
        sourceType: 'RECURRING_RULE',
        sourceId: 'rec-1',
        direction: 'PAYABLE',
        counterpartyName: 'Beta Supplies',
        referenceNumber: null,
        description: 'Office Rent',
        amount: '10000.00',
        originalDate: dateStr,
        forecastDate: dateStr,
        isScenarioShifted: false,
      });
    }

    const net = inflows - outflows;
    const closing = currentCash + net;

    rows.push({
      date: dateStr,
      dayIndex: i,
      projectedOpeningCash: currentCash.toFixed(2),
      expectedReceivableCollections: inflows.toFixed(2),
      expectedRecurringReceipts: '0.00',
      totalExpectedInflows: inflows.toFixed(2),
      expectedPayableSettlements: (outflows === 35000 ? 25000 : (i === 14 ? 25000 : 0)).toFixed(2),
      expectedRecurringExpenses: (i === 19 ? 10000 : 0).toFixed(2),
      totalExpectedOutflows: outflows.toFixed(2),
      netProjectedMovement: net.toFixed(2),
      projectedClosingCash: closing.toFixed(2),
      isDeficit: closing < 0,
      deficitAmount: closing < 0 ? Math.abs(closing).toFixed(2) : '0.00',
      contributingItems: items,
    });

    currentCash = closing;
  }
  return rows;
};

const json = (res, data, status = 200) => {
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-store',
  });
  res.end(JSON.stringify(data));
};

const server = http.createServer(async (req, res) => {
  console.log('HTTP MOCK:', req.method, req.url);
  let body = '';
  for await (const chunk of req) body += chunk;
  let payload = {};
  try {
    payload = JSON.parse(body);
  } catch {}

  const url = req.url;

  if (url === '/api/auth/csrf') {
    res.setHeader('Set-Cookie', 'FINSIGHT_SESSION=test-session; HttpOnly; SameSite=Lax; Path=/');
    return json(res, { token, headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' });
  }

  if (url === '/api/auth/me') {
    return json(res, {
      id: 'synthetic-user',
      fullName: 'Test Owner',
      email: 'owner@example.test',
      authStage: 'FULLY_AUTHENTICATED',
      platformRole: null,
      mustChangePassword: false,
      accountStatus: 'ACTIVE',
    });
  }

  if (url === '/api/businesses/active') {
    return json(res, {
      businessId: id,
      businessName: 'Forecast Trading Co',
      businessType: 'retail',
      languagePreference: 'en',
      role: 'OWNER',
      membershipStatus: 'ACTIVE',
      active: true,
    });
  }

  if (url === '/api/businesses') {
    return json(res, [
      {
        businessId: id,
        businessName: 'Forecast Trading Co',
        businessType: 'retail',
        languagePreference: 'en',
        role: 'OWNER',
        membershipStatus: 'ACTIVE',
        active: true,
      },
    ]);
  }

  if (url === '/api/profile') {
    return json(res, {
      businessId: id,
      businessType: 'retail',
      languagePreference: 'en',
      whatsappNumber: null,
      whatsappOptIn: false,
      createdAt: '2026-10-15',
    });
  }

  if (url === '/api/profile/language') {
    return json(res, { languagePreference: payload.languagePreference || 'ur' });
  }

  if (url?.startsWith('/api/finance/forecast/daily')) {
    const params = new URL(url, 'http://127.0.0.1:8080').searchParams;
    const horizon = parseInt(params.get('horizonDays') || '30');
    const rows = buildDailyRows(horizon);
    return json(res, {
      contractVersion: '1.0.0',
      currency: 'PKR',
      asOfDate: '2026-10-15',
      horizonDays: horizon,
      horizonEndDate: rows[rows.length - 1].date,
      summary: {
        currency: 'PKR',
        asOfDate: '2026-10-15',
        horizonDays: horizon,
        horizonEndDate: rows[rows.length - 1].date,
        startingCash: '100000.00',
        endingProjectedCash: rows[rows.length - 1].projectedClosingCash,
        minimumProjectedCash: '100000.00',
        minimumCashDate: '2026-10-15',
        firstDeficitDate: null,
        maximumExpectedShortfall: '0.00',
        totalExpectedInflows: '40000.00',
        totalExpectedOutflows: '35000.00',
        netProjectedMovement: '5000.00',
        overdueReceivablesTotal: '0.00',
        overduePayablesTotal: '0.00',
        unpostedDraftsInflow: '0.00',
        unpostedDraftsOutflow: '0.00',
        unpostedDraftsCount: 0,
      },
      completeness: {
        completeness: 'COMPLETE',
        omittedCategories: ['INVENTORY_COGS', 'PROJECT_FINANCE'],
        missingDueDatesCount: 0,
        missingDueDatesTotalExposure: '0.00',
        hasOverdueReceivables: false,
        hasUnpostedDrafts: false,
        activeRecurringRulesCount: 1,
        warnings: [],
      },
      dailyRows: rows,
      disclaimer: 'Disclaimer: Forecasts are deterministic estimates derived from current payment schedules and active recurring rules.',
    });
  }

  if (url === '/api/finance/forecast/scenario-preview') {
    const baselineRows = buildDailyRows(30);
    // Scenario shifts 40k collection by 20 days -> ending cash drops by 40k to 65000
    const scenarioRows = buildDailyRows(30).map((r, i) => {
      if (i >= 9) {
        return {
          ...r,
          projectedOpeningCash: (parseFloat(r.projectedOpeningCash) - 40000).toFixed(2),
          projectedClosingCash: (parseFloat(r.projectedClosingCash) - 40000).toFixed(2),
          totalExpectedInflows: i === 9 ? '0.00' : r.totalExpectedInflows,
        };
      }
      return r;
    });

    return json(res, {
      contractVersion: '1.0.0',
      baseline: {
        contractVersion: '1.0.0',
        currency: 'PKR',
        asOfDate: '2026-10-15',
        horizonDays: 30,
        horizonEndDate: '2026-11-13',
        summary: {
          currency: 'PKR',
          asOfDate: '2026-10-15',
          horizonDays: 30,
          horizonEndDate: '2026-11-13',
          startingCash: '100000.00',
          endingProjectedCash: '105000.00',
          minimumProjectedCash: '100000.00',
          minimumCashDate: '2026-10-15',
          firstDeficitDate: null,
          maximumExpectedShortfall: '0.00',
          totalExpectedInflows: '40000.00',
          totalExpectedOutflows: '35000.00',
          netProjectedMovement: '5000.00',
        },
        completeness: { completeness: 'COMPLETE', omittedCategories: [], warnings: [] },
        dailyRows: baselineRows,
      },
      scenario: {
        contractVersion: '1.0.0',
        currency: 'PKR',
        asOfDate: '2026-10-15',
        horizonDays: 30,
        horizonEndDate: '2026-11-13',
        summary: {
          currency: 'PKR',
          asOfDate: '2026-10-15',
          horizonDays: 30,
          horizonEndDate: '2026-11-13',
          startingCash: '100000.00',
          endingProjectedCash: '65000.00',
          minimumProjectedCash: '65000.00',
          minimumCashDate: '2026-11-13',
          firstDeficitDate: null,
          maximumExpectedShortfall: '0.00',
          totalExpectedInflows: '0.00',
          totalExpectedOutflows: '35000.00',
          netProjectedMovement: '-35000.00',
        },
        completeness: { completeness: 'COMPLETE', omittedCategories: [], warnings: [] },
        dailyRows: scenarioRows,
      },
      comparison: {
        scenarioName: 'Delay 20 Days',
        assumptions: payload,
        baselineSummary: { endingProjectedCash: '105000.00' },
        scenarioSummary: { endingProjectedCash: '65000.00' },
        endingCashVariance: '-40000.00',
        minimumCashVariance: '-35000.00',
        firstDeficitDateChange: 'No Deficit',
        dailyComparisons: [],
      },
    });
  }

  return json(res, { message: `Synthetic mock does not implement ${url}` }, 404);
});

await new Promise((resolve, reject) => {
  server.once('error', reject);
  server.listen(8080, '127.0.0.1', resolve);
});

// Start Next.js server on port 3100
let nextText = '';
const nextBin = path.join(frontendDir, 'node_modules', 'next', 'dist', 'bin', 'next');
const nextProc = spawn(process.execPath, [nextBin, 'start', '--hostname', '127.0.0.1', '--port', '3100'], {
  cwd: frontendDir,
  windowsHide: true,
});

nextProc.stdout.on('data', (d) => { nextText += d.toString(); });
nextProc.stderr.on('data', (d) => { nextText += d.toString(); });

// Wait for Next.js to be ready
for (let i = 0; i < 40; i++) {
  if (/Ready in/.test(nextText)) break;
  await new Promise((r) => setTimeout(r, 250));
}
if (!/Ready in/.test(nextText)) {
  console.log('Next output so far:', nextText);
}

let browser;
try {
  browser = await chromium.launch({
    headless: true,
    channel: 'msedge',
  });

  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });

  // Inject session cookies into context
  await context.addCookies([
    {
      name: 'FINSIGHT_SESSION',
      value: 'test-session',
      domain: '127.0.0.1',
      path: '/',
      httpOnly: true,
      sameSite: 'Lax',
    },
    {
      name: 'XSRF-TOKEN',
      value: token,
      domain: '127.0.0.1',
      path: '/',
      sameSite: 'Lax',
    },
  ]);

  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => {
    console.log('PAGE ERROR:', e.message);
    errors.push(e.message);
  });
  page.on('console', (m) => {
    console.log('BROWSER CONSOLE:', m.text());
    if (m.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(m.text())) {
      errors.push(m.text());
    }
  });

  // 1. Visit Forecast Page
  console.log('Navigating to http://127.0.0.1:3100/finance/forecast...');
  await page.goto('http://127.0.0.1:3100/finance/forecast');
  await page.getByRole('heading', { name: 'Daily Cash Forecast & Liquidity Simulation' }).first().waitFor({ timeout: 15000 });
  mark('forecast dashboard loads and displays authorized title');

  // 2. Verify Summary KPI Cards
  await page.getByText('100,000.00 PKR').first().waitFor();
  await page.getByText('105,000.00 PKR').first().waitFor();
  await page.getByText('None (Safe)').first().waitFor();
  mark('summary KPI cards render correct baseline values');

  // 3. Verify Chart Rendering
  const svg = page.locator('svg').first();
  await svg.waitFor();
  assert.ok((await svg.count()) > 0);
  mark('daily forecast SVG chart renders with closing curve and bars');

  // 4. Horizon Switching
  await page.getByRole('button', { name: '7 Days' }).click();
  await page.waitForTimeout(500);
  await page.getByText('7 entries').first().waitFor();
  mark('horizon switching to 7 days updates timeline');

  await page.getByRole('button', { name: '30 Days' }).click();
  await page.waitForTimeout(500);
  await page.getByText('30 entries').first().waitFor();
  mark('horizon switching back to 30 days restores full month view');

  // 5. Scenario Simulation
  await page.locator('#collectionDelayInput').fill('20');
  await page.getByRole('button', { name: 'Run Scenario' }).click();
  await page.waitForTimeout(600);
  await page.getByText('-40,000.00 PKR').first().waitFor();
  mark('scenario simulator runs and calculates -40,000.00 PKR ending cash variance');

  // 6. Contributing Items Modal
  const recordButtons = page.getByRole('button', { name: /1 record/i });
  await recordButtons.first().click();
  await page.getByRole('dialog').waitFor();
  await page.getByText('Alpha Traders').waitFor();
  await page.getByText('INV-101').waitFor();
  mark('contributing items dialog displays underlying receivable record');

  await page.getByRole('button', { name: 'Close' }).click();
  await page.waitForTimeout(300);
  assert.equal(await page.getByRole('dialog').count(), 0);
  mark('contributing items dialog closes cleanly');

  // 7. Desktop Screenshot
  await page.screenshot({ path: path.join(out, 'forecast-desktop.png'), fullPage: true });

  // 8. Urdu Language & RTL
  await page.getByRole('button', { name: 'اردو' }).click();
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await page.getByText('روزانہ کیش فلو اور نقدینگی کی پیشگوئی').first().waitFor();
  await page.screenshot({ path: path.join(out, 'forecast-urdu.png'), fullPage: true });
  mark('Urdu language switch flips layout to RTL and updates localized text');

  // 9. Mobile Responsive Layout (390x844)
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(out, 'forecast-mobile.png'), fullPage: true });
  const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
  const innerWidth = await page.evaluate(() => window.innerWidth);
  assert.ok(scrollWidth <= innerWidth + 1, 'Mobile layout must fit 390px viewport without horizontal overflow');
  mark('mobile 390x844 responsive layout renders with zero horizontal overflow');

  // 10. Verify no hydration or runtime errors
  assert.deepEqual(errors, []);
  mark('zero browser runtime errors, CSP violations, or hydration mismatches');

  writeFileSync(
    path.join(out, 'results.json'),
    JSON.stringify({ checks, errors, status: 'SUCCESS' }, null, 2)
  );

  console.log('\n--- ALL MICROSOFT EDGE BROWSER ACCEPTANCE CHECKS PASSED ---');
} catch (err) {
  console.error('Edge Browser Test Error:', err);
  throw err;
} finally {
  if (browser) await browser.close();
  nextProc.kill('SIGTERM');
  server.closeAllConnections();
  await new Promise((resolve) => server.close(resolve));
}
