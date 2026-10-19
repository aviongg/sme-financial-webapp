// Opt-in production Next + real Spring session/CSRF + disposable PostgreSQL.
// Launch through F06BrowserAcceptance only; never use a developer/customer database.
// Financial records below are isolated test fixtures, never a live application fallback.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.FINSIGHT_BROWSER_BASE_URL || 'http://127.0.0.1:3100';
assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/);
const out = resolve(process.env.BROWSER_ARTIFACT_DIR || '../tmp/f06/real-browser');
mkdirSync(out, { recursive: true });
const checks = [], errors = [], externalRequests = [], requests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Synthetic-F06-2026!';
const email = `f06-owner-${Date.now()}@example.test`, viewerEmail = email.replace('owner', 'viewer');
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
page.setDefaultTimeout(20000);
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => {
  if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(message.text())) errors.push(message.text());
});
context.on('request', request => {
  requests.push({ path: new URL(request.url()).pathname, method: request.method() });
  if (/^https?:/.test(request.url()) && new URL(request.url()).hostname !== '127.0.0.1') externalRequests.push(new URL(request.url()).origin);
});
const endpoint = '/api/estimates/preview';
const previewRequests = () => requests.filter(request => request.path === endpoint && request.method === 'POST').length;
const input = id => page.getByTestId(id);
async function api(client, path, { method = 'GET', data, status = 200, csrf = true } = {}) {
  const headers = {};
  if (method !== 'GET' && csrf) {
    const response = await client.get(`${base}/api/auth/csrf`);
    assert.equal(response.status(), 200);
    const token = await response.json(); headers[token.headerName] = token.token;
  }
  const response = await client.fetch(`${base}${path}`, { method, headers, ...(data === undefined ? {} : { data }) });
  assert.equal(response.status(), status, `${method} ${path}: ${await response.text()}`);
  return response.status() === 204 ? null : response.json();
}
async function fillLine(index, { group = 'MATERIAL', description = 'Synthetic planning cost', quantity = '1', unit = 'batch', unitCost = '800' } = {}) {
  await input(`line-group-${index}`).selectOption(group);
  await input(`line-description-${index}`).fill(description);
  await input(`line-quantity-${index}`).fill(quantity);
  await input(`line-unit-${index}`).fill(unit);
  await input(`line-unit-cost-${index}`).fill(unitCost);
}
async function preview({ keyboard = false, status = 200 } = {}) {
  const reply = page.waitForResponse(response => response.url().endsWith(endpoint) && response.request().method() === 'POST');
  if (keyboard) {
    await input('estimate-preview').focus();
    await page.keyboard.press('Enter');
  } else await input('estimate-preview').click();
  const response = await reply;
  const sent = response.request().postDataJSON();
  assert.deepEqual(Object.keys(sent).sort(), ['lines', 'pricingMode', 'pricingValue', 'saleableQuantity']);
  assert.equal(typeof sent.pricingValue, 'string');
  assert.ok(sent.saleableQuantity === null || typeof sent.saleableQuantity === 'string');
  for (const line of sent.lines) {
    assert.equal(typeof line.quantity, 'string'); assert.equal(typeof line.unitCost, 'string');
    assert.deepEqual(Object.keys(line).sort(), ['costGroup', 'description', 'quantity', 'unit', 'unitCost']);
  }
  assert.equal(response.status(), status, await response.text());
  const body = await response.json();
  if (status === 200) await input('estimate-results').waitFor();
  return body;
}
async function metric(id, expected) {
  assert.ok((await input(id).innerText()).replaceAll(',', '').includes(expected), `${id} must show ${expected}`);
}
async function capture(name) {
  // Results receive keyboard focus on the next frame. Let that scroll finish before capturing
  // the whole page, otherwise fixed navigation appears midway through the evidence image.
  await page.evaluate(() => new Promise(resolveFrame => requestAnimationFrame(() => requestAnimationFrame(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' }); resolveFrame();
  }))));
  await page.screenshot({ path: resolve(out, name), fullPage: true });
}
const payload = {
  lines: [{ costGroup: 'MATERIAL', description: 'Synthetic API cost', quantity: '1', unit: 'batch', unitCost: '800' }],
  saleableQuantity: '10', pricingMode: 'TARGET_MARGIN', pricingValue: '0.20'
};
try {
  await api(context.request, '/api/auth/register', { method: 'POST', status: 201, data: { email, password, fullName: 'Synthetic Pricing Owner' } });
  await api(context.request, '/api/auth/login', { method: 'POST', data: { email, password } });
  const business = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: { businessName: 'F06 Planning Studio', businessType: 'manufacturing', languagePreference: 'en', whatsappOptIn: false } });
  await page.goto(`${base}/estimates`);
  await input('estimate-preview').waitFor();
  assert.equal(await input('line-unit-cost-0').inputValue(), '');
  assert.equal(await input('estimate-results').count(), 0);
  assert.deepEqual(await api(context.request, '/api/records/monthly'), []);
  assert.equal((await api(context.request, '/api/dashboard')).score, null);
  assert.match(await page.locator('main').innerText(), /planning/i);
  mark('Empty authorized planning page has no sample financial amounts or invented score');

  // Seed actual records before taking the non-mutation baseline; previews never perform this write.
  for (const month of ['2026-07', '2026-08', '2026-09']) {
    await api(context.request, '/api/records/monthly', { method: 'POST', status: 201, data: {
      month, cashInflow: 10000, cashOutflow: 8000, revenue: 12000, operatingExpenses: 3000,
      cashBalanceEom: 6000, cogs: 4000, receivablesOutstanding: 0, payablesOutstanding: 0,
      inventoryValue: null, loanOutstanding: 0, interestExpense: 0, financingType: 'none'
    } });
  }
  const recordsBefore = await api(context.request, '/api/records/monthly');
  const scoreBefore = (await api(context.request, '/api/dashboard')).score;
  assert.equal(scoreBefore.methodologyVersion, 'health-score-v1');
  const financialUiBaseline = requests.length;

  await fillLine(0);
  await input('estimate-saleable-quantity').fill('10');
  await input('estimate-pricing-mode').selectOption('TARGET_MARGIN');
  await input('estimate-pricing-value').fill('20');
  let result = await preview({ keyboard: true });
  assert.equal(result.businessId, business.businessId);
  assert.equal(result.currency, 'PKR'); assert.equal(result.planningOnly, true);
  assert.equal(result.totalPlannedCost, '800.00'); assert.equal(result.plannedBatchPrice, '1000.00');
  assert.equal(result.plannedContribution, '200.00'); assert.equal(result.plannedPerUnitPrice, '100.00');
  assert.equal(result.calculation.accountingRoundingPolicy, null);
  await metric('estimate-total-cost', '800.00'); await metric('estimate-batch-price', '1000.00');
  await metric('estimate-contribution', '200.00'); await metric('estimate-per-unit', '100.00');
  await metric('estimate-margin', '20'); await metric('estimate-markup', '25');
  await capture('margin-desktop.png');
  mark('Keyboard submit sends decimal strings and renders server 800 / 20% margin = 1000, contribution 200 and unit price 100');

  await input('estimate-pricing-mode').selectOption('TARGET_MARKUP');
  assert.equal(await input('estimate-results').count(), 0);
  await input('estimate-pricing-value').fill('20');
  result = await preview();
  assert.equal(result.plannedBatchPrice, '960.00'); assert.equal(result.plannedContribution, '160.00');
  assert.match(result.plannedMargin, /^0\.166666666/);
  await metric('estimate-batch-price', '960.00'); await metric('estimate-margin', '16.666');
  await metric('estimate-markup', '20');
  await input('estimate-pricing-mode').selectOption('TARGET_MARGIN');
  assert.equal(await input('estimate-pricing-value').inputValue(), '20');
  result = await preview(); assert.equal(result.plannedBatchPrice, '1000.00');
  mark('Margin and markup switches preserve distinct values and server 20% markup yields 960 with 16.666…% margin');

  const groups = ['MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER'];
  const costs = ['100', '150', '50', '75', '25'];
  for (let index = 0; index < groups.length; index++) {
    if (index) {
      await input('estimate-add-line').focus(); await page.keyboard.press('Enter');
    }
    await fillLine(index, { group: groups[index], quantity: '2', unitCost: costs[index], description: `${groups[index]} synthetic cost` });
  }
  result = await preview();
  assert.equal(result.totalPlannedCost, '800.00');
  assert.deepEqual(result.groupSubtotals, { MATERIAL: '200.00', LABOUR: '300.00', OVERHEAD: '100.00', FREIGHT: '150.00', OTHER: '50.00' });
  for (let index = 0; index < groups.length; index++) {
    await metric(`estimate-group-${groups[index]}`, result.groupSubtotals[groups[index]]);
    await metric(`line-total-${index}`, result.lines[index].plannedCost);
  }
  await input('line-remove-4').focus(); await page.keyboard.press('Enter');
  assert.equal(await input('estimate-results').count(), 0);
  result = await preview(); assert.equal(result.totalPlannedCost, '750.00');
  assert.equal(result.groupSubtotals.OTHER, '0.00');
  for (let index = 3; index >= 0; index--) await input(`line-remove-${index}`).click();
  assert.equal(await page.locator('[data-testid^="estimate-line-"]').count(), 0);
  await input('estimate-add-line').click(); await fillLine(0, { quantity: '0.5', unitCost: '1600' });
  result = await preview(); assert.equal(result.lines[0].plannedCost, '800.00');
  mark('All five cost groups reconcile; keyboard add/remove invalidates old results; zero lines can be re-added and fractional quantity works');

  let before = previewRequests();
  await input('line-quantity-0').fill('1e3'); await input('estimate-preview').click();
  assert.equal(await input('line-quantity-0').getAttribute('aria-invalid'), 'true');
  assert.equal(previewRequests(), before);
  await input('line-quantity-0').fill('1'); await input('line-unit-cost-0').fill('-1');
  await input('estimate-preview').click();
  assert.equal(await input('line-unit-cost-0').getAttribute('aria-invalid'), 'true');
  assert.equal(previewRequests(), before);
  await input('line-unit-cost-0').fill('800'); await input('estimate-pricing-value').fill('100');
  await input('estimate-preview').click();
  assert.equal(await input('estimate-pricing-value').getAttribute('aria-invalid'), 'true');
  assert.equal(previewRequests(), before);
  await input('estimate-pricing-value').fill('20');
  mark('Malformed decimal, negative cost and 100% target margin show field validation without submitting invalid amounts');

  // Exercise a real backend 400 through the UI: alter only the outbound request, never fabricate a response.
  await page.route(`**${endpoint}`, async route => {
    const body = route.request().postDataJSON(); body.lines[0].quantity = '1e3';
    await route.continue({ postData: JSON.stringify(body) });
  }, { times: 1 });
  await preview({ status: 400 });
  await page.getByRole('alert').filter({ hasText: /.+/ }).first().waitFor();
  assert.equal(await input('estimate-results').count(), 0);
  assert.equal(await input('line-unit-cost-0').inputValue(), '800');
  result = await preview(); assert.equal(result.plannedBatchPrice, '1000.00');
  mark('A real backend validation error is visible, preserves the draft and permits successful correction/retry');

  // Hold a genuine backend reply so an edit can race it; an older result must never overwrite newer inputs.
  let releaseReply, replyReady;
  const release = new Promise(resolveReply => { releaseReply = resolveReply; });
  const ready = new Promise(resolveReady => { replyReady = resolveReady; });
  let finishRoute;
  const routeFinished = new Promise(resolveFinished => { finishRoute = resolveFinished; });
  await page.route(`**${endpoint}`, async route => {
    const response = await route.fetch(); replyReady();
    await release;
    try { await route.fulfill({ response }); } catch (error) {
      // Aborting the old request is the expected alternative to ignoring its result.
      if (!/closed|cancel|abort|Invalid InterceptionId/i.test(error.message)) throw error;
    } finally { finishRoute(); }
  }, { times: 1 });
  await input('estimate-preview').click(); await ready;
  assert.equal(await input('estimate-preview').isDisabled(), true);
  await input('line-unit-cost-0').fill('801');
  releaseReply(); await routeFinished;
  await page.waitForFunction(() => !document.querySelector('[data-testid="estimate-preview"]')?.disabled);
  await page.evaluate(() => new Promise(resolveFrame => requestAnimationFrame(() => requestAnimationFrame(resolveFrame))));
  assert.equal(await input('estimate-results').count(), 0);
  result = await preview(); assert.equal(result.totalPlannedCost, '801.00');
  mark('Pending state prevents duplicate submit and editing cancels or ignores an old server response');

  await fillLine(0, { unitCost: '0' }); await input('estimate-saleable-quantity').fill('');
  await input('estimate-pricing-mode').selectOption('PROPOSED_PRICE');
  await input('estimate-pricing-value').fill('0');
  result = await preview();
  assert.equal(result.totalPlannedCost, '0.00'); assert.equal(result.plannedBatchPrice, '0.00');
  assert.equal(result.plannedMargin, null); assert.equal(result.plannedMarkup, null); assert.equal(result.plannedPerUnitPrice, null);
  assert.equal(result.unavailableReasons.plannedPerUnitPrice, 'SALEABLE_QUANTITY_UNKNOWN');
  assert.doesNotMatch(await input('estimate-margin').innerText(), /0\s*%/);
  assert.doesNotMatch(await input('estimate-markup').innerText(), /0\s*%/);
  await input('estimate-saleable-quantity').fill('0'); result = await preview();
  assert.equal(result.unavailableReasons.plannedPerUnitPrice, 'ZERO_SALEABLE_QUANTITY');
  await fillLine(0); result = await preview();
  assert.equal(result.plannedMargin, null); assert.equal(result.plannedMarkup, '-1');
  assert.equal(result.plannedContribution, '-800.00');
  mark('Known zero costs/prices remain zero, while zero denominators and unknown saleable quantity stay explicitly unavailable');

  await api(context.request, endpoint, { method: 'POST', data: payload, csrf: false, status: 403 });
  const anonymous = await browser.newContext();
  await api(anonymous.request, endpoint, { method: 'POST', data: payload, status: 401 });
  await anonymous.close();
  for (const data of [
    { ...payload, businessId: business.businessId },
    { ...payload, lines: [{ ...payload.lines[0], quantity: 1 }] },
    { ...payload, lines: [{ ...payload.lines[0], unitCost: '1e3' }] },
    { ...payload, lines: Array.from({ length: 101 }, () => payload.lines[0]) }
  ]) await api(context.request, endpoint, { method: 'POST', data, status: 400 });
  await api(context.request, endpoint, { method: 'POST', data: { ...payload, padding: 'x'.repeat(65536) }, status: 413 });
  mark('Real API enforces authentication, CSRF, decimal strings, closed authority fields, 100-line and raw request-size bounds');

  // FINANCIAL_DATA_READ belongs to every current active role. Test revoked access rather than invent a role grant.
  const viewer = await browser.newContext();
  await api(viewer.request, '/api/auth/register', { method: 'POST', status: 201, data: { email: viewerEmail, password, fullName: 'Synthetic Pricing Viewer' } });
  const invite = await api(context.request, '/api/memberships', { method: 'POST', status: 201, data: { email: viewerEmail, role: 'VIEWER' } });
  await api(viewer.request, '/api/auth/login', { method: 'POST', data: { email: viewerEmail, password } });
  await api(viewer.request, `/api/invitations/${invite.id}/accept`, { method: 'POST', status: 204 });
  await api(viewer.request, '/api/businesses/active', { method: 'POST', data: { businessId: business.businessId } });
  const allowed = await api(viewer.request, endpoint, { method: 'POST', data: payload });
  assert.equal(allowed.plannedBatchPrice, '1000.00');
  await api(context.request, `/api/memberships/${invite.id}`, { method: 'PATCH', data: { status: 'SUSPENDED' } });
  await api(viewer.request, endpoint, { method: 'POST', data: payload, status: 409 });
  await viewer.close();
  mark('Existing viewer financial-read permission supports preview, and suspended membership loses active-business access');

  await input('estimate-pricing-mode').selectOption('TARGET_MARGIN');
  await input('estimate-saleable-quantity').fill('10');
  await input('estimate-pricing-value').fill('20');
  result = await preview();
  await page.setViewportSize({ width: 390, height: 844 });
  const languageReply = page.waitForResponse(response => response.url().endsWith('/api/profile/language') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'اردو', exact: true }).first().click();
  assert.equal((await languageReply).status(), 200);
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  assert.equal(await input('line-unit-cost-0').inputValue(), '800');
  assert.equal(await input('estimate-pricing-value').inputValue(), '20');
  assert.match(await page.locator('main').innerText(), /[\u0600-\u06ff]/);
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2));
  result = await preview({ keyboard: true }); assert.equal(result.plannedBatchPrice, '1000.00');
  await metric('estimate-batch-price', '1000.00');
  assert.equal(await input('line-unit-cost-0').getAttribute('dir'), 'ltr');
  await capture('margin-urdu-mobile.png');
  mark('Urdu switch preserves draft, renders translated RTL mobile without overflow, keeps decimal input LTR and submits by keyboard');

  assert.deepEqual(await api(context.request, '/api/records/monthly'), recordsBefore);
  assert.deepEqual((await api(context.request, '/api/dashboard')).score, scoreBefore);
  const prohibitedWrites = requests.slice(financialUiBaseline).filter(request =>
    request.method !== 'GET' && /^\/api\/(records|score|transactions|ledger|inventory|projects|funding)/.test(request.path));
  assert.deepEqual(prohibitedWrites, []);
  mark('All previews leave MonthlyRecord and health-score-v1 byte-for-byte unchanged and issue no financial posting requests');

  const second = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: { businessName: 'F06 Empty Other Business', businessType: 'services', languagePreference: 'en', whatsappOptIn: false } });
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await page.waitForFunction(() => document.querySelector('[data-testid="line-unit-cost-0"]')?.value === '');
  assert.equal(await input('estimate-results').count(), 0);
  assert.equal((await api(context.request, endpoint, { method: 'POST', data: payload })).businessId, second.businessId);
  assert.deepEqual(await api(context.request, '/api/records/monthly'), []);
  mark('Server business switch clears private estimate draft/results and every preview derives the new business from the session');

  await fillLine(0); await input('estimate-pricing-value').fill('1000');
  await api(context.request, '/api/auth/logout', { method: 'POST', status: 204 });
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await page.locator('input[type="email"]').waitFor();
  assert.equal(await input('estimate-preview').count(), 0);
  assert.equal(await input('estimate-results').count(), 0);
  mark('Real session expiry removes private planning form and results');

  assert.deepEqual(errors, []); assert.deepEqual(externalRequests, []);
  writeFileSync(resolve(out, 'results.json'), JSON.stringify({
    scope: 'Production Next + real Spring/session/CSRF + disposable PostgreSQL; synthetic accounts/data; no paid provider; held or malformed requests still use actual backend responses',
    checks, errors, externalRequests
  }, null, 2));
  console.log(`RESULT ${checks.length} checks passed`);
} catch (error) {
  await page.screenshot({ path: resolve(out, 'failure.png'), fullPage: true }).catch(() => {});
  writeFileSync(resolve(out, 'failure.txt'), `${error.stack}\n${await page.locator('body').innerText().catch(() => '')}`);
  throw error;
} finally { await browser.close(); }
