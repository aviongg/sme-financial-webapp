// Production frontend browser checks using a synthetic API contract fixture.
// This does not test the deployed Spring service, PostgreSQL, email or OCR providers.
// Requires the current production Next build serving http://127.0.0.1:3100.
import http from 'node:http';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdirSync, writeFileSync} from 'node:fs';

const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const out = process.env.BROWSER_ARTIFACT_DIR || 'test-artifacts/browser-extra';
mkdirSync(out, {recursive: true});
const origin = process.env.FRONTEND_TEST_ORIGIN || 'http://127.0.0.1:3100';
const businessId = '11111111-1111-4111-8111-111111111111';
const documentId = '44444444-4444-4444-8444-444444444444';
const resetToken = 'synthetic-reset-token-DO-NOT-USE';
const password = 'test-password-123';
const events = [];
let user = null;
let csrfToken = 'fixture-csrf-0';
let sequence = 0;
let doc = {
  id: documentId, businessId, fileUrl: 'https://invalid.example/unsafe-file-location',
  originalFilename: 'Synthetic receipt.pdf', contentType: 'application/pdf', fileSizeBytes: 512,
  uploadTimestamp: '2026-09-27T00:00:00', processingStatus: 'needs_review',
  documentTypeHint: 'unknown', extractedData: JSON.stringify({date: '2026-09-15', amount: 1250,
    vendor_or_party: 'Fixture supplier', category: 'sales', document_type_detected: 'invoice'}), confirmedData: null,
  linkedMonth: null, failureReason: null, confirmedAt: null,
};
const records = [{id: '33333333-3333-4333-8333-333333333333', businessId,
  month: '2026-09', cashInflow: 1000, cashOutflow: 400, revenue: 1500, operatingExpenses: 300,
  cashBalanceEom: 600, cogs: null, receivablesOutstanding: 0, payablesOutstanding: 0,
  inventoryValue: null, loanOutstanding: null, interestExpense: null, financingType: 'none',
  updatedAt: '2026-09-27T00:00:00'}];
const business = {businessId, businessType: 'trade', languagePreference: 'en', role: 'OWNER',
  membershipStatus: 'ACTIVE', active: true};
const profile = {businessId, businessType: 'trade', languagePreference: 'en',
  whatsappNumber: null, whatsappOptIn: false, createdAt: '2026-09-27'};
const json = (res, data, status = 200) => {
  res.writeHead(status, {'Content-Type': 'application/json', 'Cache-Control': 'no-store'});
  res.end(JSON.stringify(data));
};
const handler = async (req, res) => {
  let raw = '';
  for await (const chunk of req) raw += chunk;
  let body = {};
  try { body = JSON.parse(raw); } catch {}
  const {url, method} = req;
  events.push({url, method, body});
  if (url === '/api/auth/csrf') {
    res.setHeader('Set-Cookie', 'FINSIGHT_TEST=test-session; HttpOnly; SameSite=Lax; Path=/');
    return json(res, {token: csrfToken, headerName: 'X-XSRF-TOKEN', parameterName: '_csrf'});
  }
  if (!['GET', 'HEAD'].includes(method) && !url.startsWith('/api/auth/password-reset/') &&
      req.headers['x-xsrf-token'] !== csrfToken) return json(res, {message: 'CSRF check failed'}, 403);
  if (url === '/api/auth/register') {
    // The controller creates an account but does not establish a fully authenticated session.
    return json(res, {id: 'new-user', fullName: body.fullName, email: body.email,
      authStage: 'FULLY_AUTHENTICATED'}, 201);
  }
  if (url === '/api/auth/password-reset/request')
    return json(res, {message: 'If an account exists, reset instructions will be sent.'});
  if (url === '/api/auth/password-reset/confirm') {
    user = null;
    return json(res, {message: 'Password updated.'});
  }
  if (url === '/api/auth/login') {
    user = {id: 'synthetic-user', fullName: 'Test Owner', email: body.email,
      authStage: body.email.startsWith('mfa') ? 'MFA_CHALLENGE_REQUIRED' : 'FULLY_AUTHENTICATED',
      platformRole: null, mustChangePassword: false, accountStatus: 'ACTIVE'};
    csrfToken = `fixture-csrf-${++sequence}`;
    return json(res, user);
  }
  if (url === '/api/auth/logout') {
    user = null;
    csrfToken = `fixture-csrf-${++sequence}`;
    res.writeHead(204);
    return res.end();
  }
  if (url === '/api/auth/mfa/challenge') {
    if (body.code !== '123456') return json(res, {message: 'Invalid MFA verification code', error: 'Unauthorized'}, 401);
    user.authStage = 'FULLY_AUTHENTICATED';
    csrfToken = `fixture-csrf-${++sequence}`;
    return json(res, user);
  }
  if (url === '/api/auth/me') return user?.authStage === 'FULLY_AUTHENTICATED' ? json(res, user) : json(res, {message: 'Sign in required', error: 'unauthorized'}, 401);
  if (!user || user.authStage !== 'FULLY_AUTHENTICATED' || !req.headers.cookie?.includes('FINSIGHT_TEST=test-session'))
    return json(res, {message: 'Session expired', error: 'unauthorized'}, 401);
  if (url === '/api/businesses') return json(res, [business]);
  if (url === '/api/businesses/active') return json(res, business);
  if (url === '/api/profile') return json(res, profile);
  if (url === '/api/whatsapp/deliveries') return json(res, []);
  if (url === '/api/documents') return json(res, [doc]);
  if (url === `/api/documents/${documentId}`) {
    if (method === 'PATCH') {
      doc = {...doc, extractedData: JSON.stringify({date: body.date, amount: body.amount,
        vendor_or_party: body.vendorOrParty, category: body.category, document_type_detected: body.documentType})};
    }
    return json(res, doc);
  }
  if (url === `/api/documents/${documentId}/confirm` && method === 'POST') {
    if (doc.processingStatus === 'confirmed') return json(res, {message: 'Already confirmed'}, 409);
    doc = {...doc, processingStatus: 'confirmed', confirmedData: JSON.stringify(body),
      linkedMonth: body.targetMonth, confirmedAt: '2026-09-27T01:00:00'};
    return json(res, doc);
  }
  if (url === '/api/records/monthly') return json(res, records);
  if (url === '/api/records/monthly/query') return json(res, records[0]);
  if (url === '/api/dashboard') return json(res, {businessId, profile, score: null, topInsight: null,
    topRecommendation: null, cashFlowHistory: [], hasHistory: true,
    trendProjection: {projectedNetCashFlow: null, message: 'Need at least 3 months', historicalMonthsCount: 1}});
  if (url === '/api/zakat/monthly/preview' || url === '/api/zakat/preview')
    return json(res, {calculationStatus: 'INCOMPLETE_HAUL_CONFIRMATION_REQUIRED', zakatDue: null,
      nisabValue: 61236, netZakatableAssets: null, grossZakatableAssets: null, deductibleLiabilities: null,
      financingComplianceStatus: 'INCOMPLETE_FINANCING_DATA', missingFields: ['assessment.haulStatus'],
      warnings: ['Inventory valuation has not been supplied'], disclosure: 'Synthetic preview for interface verification only.',
      ruleProfile: 'HANAFI_PK_BUSINESS_V1', ruleVersion: '1.0.0', debtPolicy: 'Fixture policy',
      assetBreakdown: [], liabilityBreakdown: []});
  return json(res, {message: `Fixture does not implement ${method} ${url}`}, 404);
};
const server = http.createServer((req, res) => void handler(req, res).catch(error => {
  console.error(error);
  json(res, {message: 'Fixture failed'}, 500);
}));
await new Promise((resolve, reject) => {server.once('error', reject); server.listen(8080, '127.0.0.1', resolve);});
let browser;
const checks = [], errors = [];
const mark = name => {checks.push(name); console.log('PASS', name);};
const matching = (url, method = 'POST') => events.filter(event => event.url === url && event.method === method);
try {
  browser = await chromium.launch({headless: true, channel: 'msedge'});
  const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(message.text())) errors.push(message.text());
  });

  await page.goto(origin);
  await page.getByRole('heading', {name: 'Sign in to FinSight'}).waitFor();
  await page.getByRole('button', {name: 'Create account', exact: true}).click();
  await page.getByLabel(/^Full name/).fill('New Test Owner');
  await page.getByLabel(/^Email/).fill('new@example.test');
  await page.getByLabel(/^Password/).fill(password);
  await page.getByRole('button', {name: 'Continue', exact: true}).click();
  await page.getByText('Account created. Sign in to continue.', {exact: true}).waitFor();
  await page.getByRole('heading', {name: 'Sign in to FinSight'}).waitFor();
  assert.equal(matching('/api/auth/register').length, 1);
  assert.equal(matching('/api/auth/login').length, 0);
  assert.equal(await page.getByRole('link', {name: 'Dashboard', exact: true}).count(), 0);
  mark('registration creates an account without treating its response as a login');

  await page.getByRole('button', {name: 'Forgot password?', exact: true}).click();
  await page.getByLabel(/^Email/).fill('new@example.test');
  await page.getByRole('button', {name: 'Continue', exact: true}).click();
  await page.getByText('If an account exists, reset instructions will be sent.', {exact: true}).waitFor();
  assert.deepEqual(matching('/api/auth/password-reset/request')[0].body, {email: 'new@example.test'});
  mark('reset request shows the account-neutral server response');

  await page.goto(`${origin}/reset-password#token=${resetToken}`);
  await page.getByRole('heading', {name: 'Choose a new password'}).waitFor();
  assert.equal(new URL(page.url()).hash, '');
  assert.ok(events.every(event => !event.url.includes(resetToken)));
  await page.getByLabel(/^New password/).fill('new-secure-password-456');
  await page.getByRole('button', {name: 'Continue', exact: true}).click();
  await page.getByText('Password reset. Sign in with your new password.', {exact: true}).waitFor();
  assert.deepEqual(matching('/api/auth/password-reset/confirm').map(event => event.body),
    [{token: resetToken, newPassword: 'new-secure-password-456'}]);
  mark('password reset removes the fragment and sends the token once in the confirm body');

  await page.goto(`${origin}/upload/${documentId}`);
  await page.getByRole('heading', {name: 'Sign in to FinSight'}).waitFor();
  await page.getByLabel(/^Email/).fill('owner@example.test');
  await page.getByLabel(/^Password/).fill(password);
  await page.getByRole('button', {name: 'Continue', exact: true}).click();
  await page.getByRole('heading', {name: 'Review document', exact: true}).waitFor();
  await page.getByLabel(/^Confirmed amount/).waitFor();
  assert.equal(await page.getByLabel(/^Confirmed amount/).inputValue(), '1250');
  assert.equal(await page.getByLabel('Document date', {exact: true}).inputValue(), '2026-09-15');
  assert.equal(await page.getByLabel('Vendor or party', {exact: true}).inputValue(), 'Fixture supplier');
  assert.equal(await page.getByRole('link', {name: 'Open original document'}).getAttribute('href'), `/api/documents/${documentId}/file`);
  mark('document review initializes extracted draft values and uses the protected original URL');

  await page.getByLabel('Vendor or party', {exact: true}).fill('Corrected supplier');
  await Promise.all([
    page.waitForResponse(response => response.url().endsWith(`/api/documents/${documentId}`) && response.request().method() === 'PATCH'),
    page.getByRole('button', {name: 'Save draft corrections', exact: true}).click(),
  ]);
  assert.deepEqual(matching(`/api/documents/${documentId}`, 'PATCH').map(event => event.body),
    [{date: '2026-09-15', amount: 1250, vendorOrParty: 'Corrected supplier', category: 'sales', documentType: 'invoice'}]);
  assert.equal(matching(`/api/documents/${documentId}/confirm`).length, 0);
  mark('vendor-only draft correction preserves OCR category and document type and does not confirm financial data');

  await page.getByLabel(/^Target month/).fill('2026-09');
  await page.getByLabel(/^Financial classification/).selectOption('revenue');
  await page.getByLabel(/^Cash-flow impact/).selectOption('cash_inflow');
  await page.getByRole('button', {name: 'Review contribution', exact: true}).click();
  await page.getByRole('group', {name: 'Confirm contribution', exact: true}).waitFor();
  assert.equal(matching(`/api/documents/${documentId}/confirm`).length, 0);
  await page.getByRole('button', {name: 'Confirm and add once', exact: true}).click();
  await page.getByText('Confirmed in 2026-09. This document cannot be added again.', {exact: true}).waitFor();
  assert.deepEqual(matching(`/api/documents/${documentId}/confirm`).map(event => event.body),
    [{targetMonth: '2026-09', confirmedAmount: 1250, confirmedDate: '2026-09-15', confirmedParty: 'Corrected supplier',
      targetClassification: 'revenue', cashFlowImpact: 'cash_inflow', initialCashBalanceEom: null}]);
  await page.reload();
  await page.getByText('Confirmed in 2026-09. This document cannot be added again.', {exact: true}).waitFor();
  assert.equal(await page.getByRole('button', {name: 'Confirm and add once', exact: true}).count(), 0);
  assert.equal(matching(`/api/documents/${documentId}/confirm`).length, 1);
  mark('document contribution requires explicit review, confirms once, and stays confirmed after reload');
  await page.screenshot({path: `${out}/confirmed-document.png`, fullPage: true});

  await page.goto(`${origin}/sharia-zakat`);
  await page.getByLabel(/^Saved month/).selectOption('2026-09');
  await page.getByLabel(/^Assessment date/).fill('2026-09-27');
  await page.getByLabel(/^Silver price per gram/).fill('100');
  await page.getByLabel(/^Price source/).fill('Synthetic quote, not market data');
  await page.getByLabel(/^Price date and time/).fill('2026-09-27T13:00');
  await page.getByRole('button', {name: 'Calculate preview', exact: true}).click();
  await page.getByRole('heading', {name: 'INCOMPLETE HAUL CONFIRMATION REQUIRED', exact: true}).waitFor();
  assert.equal(await page.getByText('Not determined', {exact: true}).count(), 2);
  await page.getByText('assessment.haul Status', {exact: true}).waitFor();
  const monthly = matching('/api/zakat/monthly/preview')[0].body;
  assert.equal(monthly.month, '2026-09');
  assert.equal(monthly.assessment.haulStatus, 'UNKNOWN');
  assert.equal(monthly.assessment.priceTimestamp, '2026-09-27T13:00:00+05:00');
  assert.equal(monthly.inventory.amount, null);
  for (const key of ['receivables', 'currentPayables', 'principalDueWithin12LunarMonths', 'principalExcludedFromPayables', 'unsupportedCategories']) assert.equal(monthly[key], null);
  assert.equal('userId' in monthly || 'businessId' in monthly, false);
  mark('saved-month Zakat sends unknown declarations as null and renders incomplete results without inventing zero');
  await page.screenshot({path: `${out}/incomplete-zakat.png`, fullPage: true});

  await page.getByLabel(/^Balances source/).selectOption('manual');
  assert.equal(await page.getByRole('heading', {name: 'INCOMPLETE HAUL CONFIRMATION REQUIRED', exact: true}).count(), 0);
  await page.getByRole('button', {name: 'Calculate preview', exact: true}).click();
  await page.getByRole('heading', {name: 'INCOMPLETE HAUL CONFIRMATION REQUIRED', exact: true}).waitFor();
  const manual = matching('/api/zakat/preview')[0].body;
  assert.equal(manual.assets.cashAndBankBalances, null);
  assert.equal(manual.liabilities.accountsPayable, null);
  assert.equal(manual.financing.loanOutstanding, null);
  assert.equal(manual.financing.interestExpense, null);
  mark('changing Zakat inputs clears stale results and manual blank balances remain unknown');

  await page.goto(`${origin}/settings`);
  await page.getByRole('button', {name: 'Sign out', exact: true}).click();
  await page.getByRole('heading', {name: 'Sign in to FinSight'}).waitFor();
  await page.getByLabel(/^Email/).fill('mfa@example.test');
  await page.getByLabel(/^Password/).fill(password);
  await page.getByRole('button', {name: 'Continue', exact: true}).click();
  await page.getByRole('heading', {name: 'Verify your sign-in'}).waitFor();
  await page.getByLabel('Authenticator code', {exact: false}).fill('000000');
  await page.getByRole('button', {name: 'Continue', exact: true}).click();
  await page.getByRole('alert').filter({hasText: 'Invalid MFA verification code'}).waitFor();
  await page.getByRole('heading', {name: 'Verify your sign-in'}).waitFor();
  assert.equal(await page.getByRole('link', {name: 'Dashboard', exact: true}).count(), 0);
  await page.getByLabel('Authenticator code', {exact: false}).fill('123456');
  await page.getByRole('button', {name: 'Continue', exact: true}).click();
  await page.getByRole('link', {name: 'Dashboard', exact: true}).first().waitFor();
  mark('invalid MFA code preserves the challenge, shows feedback, and allows a subsequent valid code');

  assert.deepEqual(errors, []);
  mark('additional flows have no browser exceptions, CSP violations or hydration errors');
  writeFileSync(`${out}/results.json`, JSON.stringify({scope: 'Production Next build against synthetic backend contract fixture; not deployed Spring/PostgreSQL or email/OCR provider E2E', checks, errors}, null, 2));
} catch (error) {
  const page = browser?.contexts()[0]?.pages()[0];
  if (page) {
    await page.screenshot({path: `${out}/failure.png`, fullPage: true});
    console.error(await page.locator('body').innerText());
  }
  throw error;
} finally {
  await browser?.close();
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
}
