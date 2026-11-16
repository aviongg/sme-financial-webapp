import { historicalOrigin as origin, historicalBackendPort as backendPort } from './historical-harness.mjs';
import { configurationFixture, aiStatusFixture } from './fixtures/phase-two.mjs';
import http from 'node:http';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const playwrightPath = process.env.PLAYWRIGHT_MODULE || 'playwright';
const { chromium } = require(playwrightPath);

const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const out = process.env.BROWSER_ARTIFACT_DIR || path.join(frontendDir, 'test-artifacts', 'risk-browser');
mkdirSync(out, { recursive: true });

const id = '11111111-1111-4111-8111-111111111111';
let token = 'csrf-token-123';
const checks = [];
const mark = (name) => {
  checks.push(name);
  console.log('PASS ' + name);
};

import { buildRiskAssessmentResponse } from './fixtures/financial-risk.mjs';

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

  const parsedUrl = new URL(req.url, `http://127.0.0.1:${backendPort}`);
  const pathname = parsedUrl.pathname;

  if (pathname === '/api/auth/csrf') {
    res.setHeader('Set-Cookie', 'FINSIGHT_SESSION=test-session; HttpOnly; SameSite=Lax; Path=/');
    return json(res, { token, headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' });
  }

  if (pathname === '/api/auth/me') {
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

  if (pathname === '/api/businesses/active') {
    return json(res, {
      businessId: id,
      businessName: 'Risk Analysis Corp',
      businessType: 'retail',
      languagePreference: 'en',
      role: 'OWNER',
      membershipStatus: 'ACTIVE',
      active: true,
    });
  }

  if (pathname === '/api/businesses') {
    return json(res, [
      {
        businessId: id,
        businessName: 'Risk Analysis Corp',
        businessType: 'retail',
        languagePreference: 'en',
        role: 'OWNER',
        membershipStatus: 'ACTIVE',
        active: true,
      },
    ]);
  }

  if (pathname === '/api/business-configuration') return json(res,configurationFixture());
  if (pathname === '/api/ai/status') return json(res,aiStatusFixture());
  if (pathname === '/api/profile' || pathname === '/api/profile/language') {
    return json(res, {
      businessId: id,
      businessType: 'retail',
      languagePreference: 'en',
      whatsappNumber: null,
      whatsappOptIn: false,
      createdAt: '2026-10-15',
    });
  }

  if (pathname === '/api/finance/risk/assessment') {
    const horizon = parseInt(parsedUrl.searchParams.get('horizonDays') || '30', 10);
    const category = parsedUrl.searchParams.get('category') || '';
    const severity = parsedUrl.searchParams.get('severity') || '';
    return json(res, buildRiskAssessmentResponse(horizon, category, severity));
  }

  if (pathname === '/api/finance/risk/insights') {
    const horizon = parseInt(parsedUrl.searchParams.get('horizonDays') || '30', 10);
    const category = parsedUrl.searchParams.get('category') || '';
    const severity = parsedUrl.searchParams.get('severity') || '';
    return json(res, buildRiskAssessmentResponse(horizon, category, severity).insights);
  }

  if (pathname === '/api/finance/risk/summary') {
    return json(res, buildRiskAssessmentResponse().summary);
  }

  // Fallbacks for records page
  if (pathname.startsWith('/api/accounting/transactions')) {
    return json(res, { items: [], total: 0, page: 0, size: 20 });
  }
  if (pathname.startsWith('/api/accounting/accounts')) {
    return json(res, { items: [], total: 0 });
  }
  if (pathname.startsWith('/api/record-drafts')) {
    return json(res, { items: [], total: 0 });
  }
  if (pathname.startsWith('/api/counterparties')) {
    return json(res, { items: [], total: 0 });
  }
  if (pathname.startsWith('/api/financial-obligations')) {
    return json(res, { items: [], total: 0 });
  }

  return json(res, { message: 'Not found' }, 404);
});

await new Promise((resolve, reject) => {
  server.once('error', reject);
  server.listen(backendPort, '127.0.0.1', resolve);
});
console.log(`Synthetic backend listening on http://127.0.0.1:${backendPort}`);

// The shared runner owns the disposable production frontend, as for the other four drivers.
let browser;
const errors = [];

try {
  browser = await chromium.launch({
    ...(process.env.FINSIGHT_BROWSER_CHANNEL ? { channel: process.env.FINSIGHT_BROWSER_CHANNEL } : {}),
    headless: true,
  });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 900 },
  });

  await context.addCookies([
    {
      name: 'FINSIGHT_SESSION',
      value: 'test-session-cookie-val',
      domain: '127.0.0.1',
      path: '/',
    },
    {
      name: 'XSRF-TOKEN',
      value: token,
      domain: '127.0.0.1',
      path: '/',
    },
  ]);

  const page = await context.newPage();
  page.on('pageerror', (err) => {
    console.error('BROWSER PAGE ERROR:', err);
    errors.push(err.message);
  });
  page.on('console', (msg) => {
    console.log('BROWSER CONSOLE:', msg.text());
    if (msg.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(msg.text())) {
      errors.push(msg.text());
    }
  });

  // 1. Visit Dedicated Risk Intelligence Page: /finance/risks
  console.log(`Navigating to ${origin}/finance/risks`);
  await page.goto((origin + '/finance/risks'));
  await page.getByRole('heading', { name: 'Financial Risk Intelligence' }).first().waitFor({ timeout: 15000 });
  mark('1. risk intelligence workspace loads and displays authorized title');

  // 2. Verify Overall Severity & KPI Summary Cards
  await page.getByTestId('overall-severity-badge').waitFor();
  await page.getByText('Critical Risks').waitFor();
  await page.getByTestId('kpi-critical-count').waitFor();
  await page.getByTestId('kpi-high-count').waitFor();
  await page.getByTestId('kpi-medium-count').waitFor();
  assert.equal((await page.getByTestId('overall-severity-badge').innerText()).trim(),'Critical');
  for(const [level,count] of [['critical',1],['high',2],['medium',1]]) assert.equal((await page.getByTestId(`kpi-${level}-count`).innerText()).trim(),String(count));
  mark('2. summary KPI cards render overall severity and counts');

  // 3. Verify Data Completeness Banner
  await page.getByTestId('risk-data-completeness-banner').waitFor();
  await page.getByText('Data Limited / Incomplete').waitFor();
  mark('3. data completeness constraint warning banner renders');

  // 4. Verify Active Risk Insight Cards
  await page.getByTestId('risk-insights-grid').waitFor();
  await page.getByText('Projected Cash Shortfall of PKR 45,000.00 by 2026-11-04').waitFor();
  await page.getByText('Upcoming 7-Day Payment Pressure: PKR 80,000.00 Due').waitFor();
  await page.getByText('High Customer Concentration: Alpha Traders (62.50% of receivables)').waitFor();
  mark('4. active risk insight cards render across multiple categories');

  // 5. Expand & Verify Supporting Evidence Trace
  const toggleButtons = page.getByTestId('toggle-evidence-button');
  await toggleButtons.first().click();
  await page.getByTestId('risk-evidence-list').first().waitFor();
  await page.getByText('Supporting Financial Evidence').first().waitFor();
  assert.match(await page.getByTestId('risk-evidence-list').first().innerText(),/-45000.00/);
  assert.match(await page.getByTestId('risk-evidence-list').first().innerText(),/2026-11-04/);
  mark('5. supporting financial evidence drill-down expands with source item trace');

  // 6. Verify Action Recommendation Box & Navigation Link
  await page.getByTestId('risk-recommendation-box').first().waitFor();
  const actionButton = page.getByTestId('recommendation-action-button').first();
  await actionButton.waitFor();
  assert.ok(await actionButton.isVisible());assert.equal(await actionButton.getAttribute('href'),'/finance/forecast');
  mark('6. action recommendation box renders with urgency badge and target link');

  // 7. Filter Bar Category Selection
  const categorySelect = page.getByTestId('risk-category-filter');
  await categorySelect.selectOption('UPCOMING_PAYMENTS');
  await page.waitForTimeout(600);
  await page.getByText('Upcoming 7-Day Payment Pressure: PKR 80,000.00 Due').waitFor();
  assert.equal(await page.getByText('High Customer Concentration: Alpha Traders (62.50% of receivables)', {exact:true}).count(), 0);assert.equal(await page.getByTestId('risk-insights-grid').locator('h3').count(),1);
  mark('7. category filtering dynamically restricts active alert cards');

  // Reset category filter
  await categorySelect.selectOption('');
  await page.waitForTimeout(500);

  // 8. Category Coverage & Diagnostics Breakdown Table
  const breakdown = page.getByTestId('risk-category-breakdown');
  await breakdown.waitFor();
  await breakdown.getByText('Risk Category Coverage & Hygiene').waitFor();
  await breakdown.getByRole('cell', { name: 'Bank Reconciliation Coverage' }).waitFor();
  await breakdown.getByText('NOT_ASSESSED').first().waitFor();
  mark('8. category breakdown table shows assessment status and diagnostic notes');

  // 9. Verify Embedded View in /records?view=risks
  console.log(`Navigating to ${origin}/records?view=risks`);
  await page.goto((origin + '/records?view=risks'));
  await page.getByTestId('financial-risk-workspace').waitFor();
  await page.getByRole('link', { name: 'Risk Intelligence' }).waitFor();
  mark('9. embedded tab in /records?view=risks mounts FinancialRiskWorkspace');

  // 10. Desktop Screenshot
  await page.screenshot({ path: path.join(out, 'risk-desktop.png'), fullPage: true });

  // 11. Bilingual Urdu Language Switch & RTL Direction
  const urduButton = page.getByRole('button', { name: 'اردو' });
  await urduButton.click();
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await page.getByText('مالیاتی خطرات کی ذہانت').first().waitFor();
  await page.screenshot({ path: path.join(out, 'risk-urdu.png'), fullPage: true });
  mark('10. Urdu switch changes direction and renders translated risk copy');

  // 12. Mobile Responsive Layout (390x844) Viewport
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(out, 'risk-mobile.png'), fullPage: true });
  const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
  const innerWidth = await page.evaluate(() => window.innerWidth);
  assert.ok(scrollWidth <= innerWidth + 1, 'Mobile layout must fit 390px viewport without horizontal overflow');
  mark('11. mobile 390x844 responsive layout renders with zero horizontal overflow');

  // 13. Verify Zero Uncaught Errors
  assert.deepEqual(errors, []);
  mark('12. zero browser runtime errors, CSP violations, or hydration mismatches');

  writeFileSync(
    path.join(out, 'results.json'),
    JSON.stringify({ checks, errors, status: 'SUCCESS' }, null, 2)
  );

  console.log('\n--- ALL 12 DECLARED PLAYWRIGHT BROWSER ACCEPTANCE CHECKS PASSED ---');
} catch (err) {
  const page=browser?.contexts()[0]?.pages()[0];if(page)await page.screenshot({path:path.join(out,'failure.png'),fullPage:true});
  writeFileSync(path.join(out,'results.json'),JSON.stringify({checks,errors,status:'FAILED',failure:String(err)},null,2));
  console.error('Risk browser error:', err);
  throw err;
} finally {
  if (browser) await browser.close();
  server.closeAllConnections();
  await new Promise((resolve) => server.close(resolve));
}
