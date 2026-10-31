// Opt-in production Next + real Spring/session/CSRF + disposable PostgreSQL.
// Launch through F08InventoryBrowserAcceptance only. All accounts/data are synthetic.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.FINSIGHT_BROWSER_BASE_URL || 'http://127.0.0.1:3100';
assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/);
assert.ok(process.env.FINSIGHT_DB_BASELINE_READY && process.env.FINSIGHT_DB_BASELINE_CAPTURED,
  'Launch through F08InventoryBrowserAcceptance so database write probes are installed');
const out = resolve(process.env.BROWSER_ARTIFACT_DIR || '../tmp/f08/real-browser');
mkdirSync(out, { recursive: true });
const checks = [], errors = [], externalRequests = [], requests = [], apiRequests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Synthetic-F08-2026!';
const stamp = Date.now();
const email = `f08-owner-${stamp}@example.test`;
const viewerEmail = `f08-viewer-${stamp}@example.test`, accountantEmail = `f08-accountant-${stamp}@example.test`;
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
function watchContext(current) {
  current.on('request', request => {
    const url = new URL(request.url());
    requests.push({ path: url.pathname, method: request.method() });
    if (/^https?:/.test(url.protocol) && url.hostname !== '127.0.0.1') externalRequests.push(url.origin);
  });
  current.on('page', currentPage => {
    currentPage.setDefaultTimeout(20000);
    currentPage.on('pageerror', error => errors.push(error.message));
    currentPage.on('console', message => {
      if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/i.test(message.text())) errors.push(message.text());
    });
    currentPage.on('response', response => { if (response.status() >= 500) errors.push(`${response.status()} ${response.url()}`); });
  });
}
watchContext(context);
const page = await context.newPage();
const id = (name, target = page) => target.getByTestId(name);
async function api(client, path, { method = 'GET', data, status = 200, csrf = true } = {}) {
  const headers = {};
  if (method !== 'GET' && csrf) {
    const tokenResponse = await client.get(`${base}/api/auth/csrf`);
    assert.equal(tokenResponse.status(), 200);
    const token = await tokenResponse.json(); headers[token.headerName] = token.token;
  }
  apiRequests.push({ path, method });
  const response = await client.fetch(`${base}${path}`, { method, headers, ...(data === undefined ? {} : { data }) });
  assert.equal(response.status(), status, `${method} ${path}: ${await response.text()}`);
  return response.status() === 204 ? null : response.json();
}
async function uiReply(target, path, method, action, status = 200) {
  const pending = target.waitForResponse(response => new URL(response.url()).pathname === path && response.request().method() === method);
  await action();
  const response = await pending;
  assert.equal(response.status(), status, `${method} ${path}: ${await response.text()}`);
  return { body: await response.json(), sent: response.request().postDataJSON() };
}
async function settled(target = page) {
  await target.evaluate(async () => {
    await document.fonts?.ready;
    await Promise.all(document.getAnimations({ subtree: true }).map(animation => animation.finished.catch(() => {})));
    await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
  });
}
async function noOverflow(label, target = page) {
  await settled(target);
  const dimensions = await target.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  assert.ok(dimensions.document <= dimensions.viewport + 2, `${label}: ${JSON.stringify(dimensions)}`);
}
async function capture(name, target = page) {
  await settled(target);
  await target.evaluate(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }));
  await target.screenshot({ path: resolve(out, name), fullPage: true });
}
async function joinAs(memberEmail, role, businessId) {
  const member = await browser.newContext({ viewport: { width: 1440, height: 1000 } }); watchContext(member);
  await api(member.request, '/api/auth/register', { method: 'POST', status: 201, data: { email: memberEmail, password, fullName: `Synthetic ${role}` } });
  const invite = await api(context.request, '/api/memberships', { method: 'POST', status: 201, data: { email: memberEmail, role } });
  await api(member.request, '/api/auth/login', { method: 'POST', data: { email: memberEmail, password } });
  await api(member.request, `/api/invitations/${invite.id}/accept`, { method: 'POST', status: 204 });
  await api(member.request, '/api/businesses/active', { method: 'POST', data: { businessId } });
  return member;
}
async function fillItem(sku, name, unit, kind, category, reorder) {
  await id('inventory-sku-input').fill(sku);
  await id('inventory-name-input').fill(name);
  await id('inventory-unit-input').fill(unit);
  await id('inventory-kind-input').selectOption(kind);
  await id('inventory-category-input').fill(category);
  await id('inventory-reorder-input').fill(reorder);
}
async function runFractionalPreview(target) {
  await id('valuation-opening-quantity', target).fill('0');
  await id('valuation-opening-value', target).fill('0');
  await id('valuation-step-0-quantity', target).fill('3');
  await id('valuation-step-0-value', target).fill('10.0000');
  for (let index = 1; index <= 3; index++) {
    await id('valuation-add-issue', target).click();
    await id(`valuation-step-${index}-quantity`, target).fill('1');
  }
  const reply = await uiReply(target, '/api/inventory/valuation/preview', 'POST', () => id('valuation-calculate', target).click());
  assert.deepEqual(reply.sent, { openingQuantity: '0', openingValue: '0', steps: [
    { type: 'RECEIPT', quantity: '3', value: '10.0000' },
    { type: 'ISSUE', quantity: '1' }, { type: 'ISSUE', quantity: '1' }, { type: 'ISSUE', quantity: '1' },
  ] }, 'Issue steps never send a browser-computed value');
  assert.equal(reply.body.authoritative, false); assert.equal(reply.body.persisted, false);
  assert.equal(reply.body.financialEffect, 'NONE'); assert.equal(reply.body.policy.version, 'f08-wac-draft-1');
  await id('valuation-results', target).waitFor();
  assert.equal(await id('valuation-result-step-1-movement', target).innerText(), '3.3333');
  assert.equal(await id('valuation-result-step-2-movement', target).innerText(), '3.3334');
  assert.equal(await id('valuation-result-step-3-movement', target).innerText(), '3.3333');
  assert.equal(await id('valuation-closing-quantity', target).innerText(), '0');
  assert.equal(await id('valuation-closing-value', target).innerText(), '0.0000');
  assert.match(await id('valuation-total-issued', target).innerText(), /10\.0000/);
  assert.equal(await id('valuation-policy-version', target).innerText(), 'f08-wac-draft-1');
  return reply.body;
}

try {
  await api(context.request, '/api/auth/register', { method: 'POST', status: 201, data: { email, password, fullName: 'Synthetic Inventory Owner' } });
  await page.goto(`${base}/inventory`);
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await uiReply(page, '/api/auth/login', 'POST', () => page.locator('form button[type="submit"]').click());
  const business = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: {
    businessName: 'F08 Inventory Textiles', businessType: 'manufacturing', languagePreference: 'en', whatsappOptIn: false
  } });
  assert.equal((await api(context.request, '/api/businesses/active')).businessId, business.businessId);
  mark('1. Authenticate through the real sign-in UI with a real active business');

  // Fixture writes occur before the database baseline and are never produced by F08.
  for (const month of ['2026-07', '2026-08', '2026-09']) await api(context.request, '/api/records/monthly', {
    method: 'POST', status: 201, data: { month, cashInflow: 10000, cashOutflow: 8000, revenue: 12000,
      operatingExpenses: 3000, cashBalanceEom: 6000, cogs: 4000, receivablesOutstanding: 0, payablesOutstanding: 0,
      inventoryValue: 2500, loanOutstanding: 0, interestExpense: 0, financingType: 'none' }
  });
  await api(context.request, '/api/projects', { method: 'POST', status: 201, data: { code: 'F08-FIXTURE', name: 'Existing project', kind: 'JOB' } });
  await api(context.request, '/api/invoices', { method: 'POST', status: 201, data: {
    counterpartyName: 'Synthetic customer', documentDate: '2026-10-20', dueDate: null, notes: null,
    lines: [{ description: 'Existing invoice line', quantity: '1', unit: 'pcs', unitPrice: '100.00' }] } });
  const recordsBefore = await api(context.request, '/api/records/monthly');
  await api(context.request, '/api/dashboard'); // Warm existing localized advice before the immutable-table baseline.
  const scoreBefore = await api(context.request, '/api/scores/latest');
  assert.equal(scoreBefore.methodologyVersion, 'health-score-v1');
  const accountant = await joinAs(accountantEmail, 'ACCOUNTANT', business.businessId);
  const viewer = await joinAs(viewerEmail, 'VIEWER', business.businessId);
  const networkBaseline = requests.length, apiBaseline = apiRequests.length;
  writeFileSync(process.env.FINSIGHT_DB_BASELINE_READY, 'Synthetic fixtures are ready for full-row baseline and write probes.');
  const deadline = Date.now() + 120000;
  while (!existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED) && Date.now() < deadline) await delay(100);
  assert.ok(existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED), 'Java must capture the database baseline before inventory actions');
  mark('2. Pre-existing records, score, project and invoice fixtures captured before any F08 action');

  await page.goto(`${base}/inventory`);
  await id('inventory-empty-state').waitFor();
  await id('inventory-catalog-banner').waitFor();
  assert.match(await page.locator('aside a[href="/inventory"]').innerText(), /Inventory/);
  for (const path of ['/', '/records', '/projects', '/invoices', '/bills', '/estimates', '/settings']) {
    assert.ok(await page.locator(`aside a[href="${path}"]`).count(), `Existing navigation ${path}`);
  }
  const configuration = await api(context.request, '/api/business-configuration');
  const inventoryModule = configuration.modules.find(item => item.key === 'inventory');
  assert.ok(inventoryModule); assert.equal(inventoryModule.implemented, false); assert.equal(inventoryModule.available, false);
  mark('3. /inventory is reachable from the sidebar while the configuration module stays unimplemented');

  await id('inventory-create-button').click();
  await fillItem(' rm-cotton-01 ', 'Cotton yarn 30s', 'kg', 'RAW_MATERIAL', 'Yarn', '25.5');
  const created = await uiReply(page, '/api/inventory/items', 'POST', () => id('inventory-save-button').click(), 201);
  assert.deepEqual(Object.keys(created.sent).sort(), ['category', 'itemKind', 'name', 'reorderLevel', 'sku', 'unit']);
  assert.equal(created.body.sku, 'RM-COTTON-01'); assert.equal(created.body.catalogOnly, true);
  assert.equal(created.body.stockTracking, 'NOT_IMPLEMENTED_PHASE_A');
  await id('inventory-item-RM-COTTON-01').waitFor();
  await id('inventory-create-button').click();
  await fillItem('FG-SHIRT-L', 'Finished cotton shirt, large', 'pcs', 'FINISHED_GOOD', '', '');
  await uiReply(page, '/api/inventory/items', 'POST', () => id('inventory-save-button').click(), 201);
  await page.reload();
  await id('inventory-item-RM-COTTON-01').waitFor(); await id('inventory-item-FG-SHIRT-L').waitFor();
  mark('4. OWNER creates RAW_MATERIAL and FINISHED_GOOD items through the browser; they persist across reload');

  await id('inventory-create-button').click();
  await fillItem('Rm-Cotton-01', 'Duplicate', 'kg', 'CONSUMABLE', '', '');
  await uiReply(page, '/api/inventory/items', 'POST', () => id('inventory-save-button').click(), 409);
  assert.match(await id('inventory-form-error').innerText(), /already uses this SKU/);
  await page.getByRole('button', { name: 'Cancel' }).click();
  mark('5. Case-insensitive duplicate SKU is rejected by the server and explained in the UI');

  await id('inventory-edit-RM-COTTON-01').click();
  await id('inventory-name-input').fill('Cotton yarn 30s combed');
  const updated = await uiReply(page, `/api/inventory/items/${created.body.id}`, 'PUT', () => id('inventory-save-button').click());
  assert.equal(updated.sent.expectedVersion, 0); assert.equal(updated.body.version, 1);
  await id('inventory-notice').waitFor();
  page.once('dialog', dialog => dialog.accept());
  const archived = await uiReply(page, `/api/inventory/items/${created.body.id}/archive`, 'POST', () => id('inventory-archive-RM-COTTON-01').click());
  assert.deepEqual(archived.sent, { expectedVersion: 1 }); assert.equal(archived.body.status, 'ARCHIVED');
  await id('inventory-item-RM-COTTON-01').waitFor({ state: 'detached' });
  await id('inventory-include-archived').check();
  await id('inventory-item-RM-COTTON-01').waitFor();
  assert.equal(await id('inventory-edit-RM-COTTON-01').count(), 0, 'Archived items have no edit control');
  mark('6. Edit uses optimistic versioning; archive is confirmed, terminal and leaves the item retrievable');

  const registerText = await id('inventory-register').innerText();
  for (const forbidden of [/on hand:/i, /\bWAC\b/, /\bCOGS\b/, /average unit cost/i, /stock value:/i]) {
    assert.doesNotMatch(registerText, forbidden, `Register must not show ${forbidden}`);
  }
  await noOverflow('register desktop EN'); await capture('inventory-register-desktop-en.png');
  mark('7. Register shows catalog facts only — no fake stock, value, WAC or COGS — without overflow');

  await id('inventory-preview-link').click();
  await id('valuation-preview-sandbox').waitFor();
  assert.match(await id('valuation-preview-banner').innerText(), /PLANNING PREVIEW — NO STOCK IS RECORDED/);
  assert.match(await id('valuation-policy-notice').innerText(), /not approved accounting policy/);
  await runFractionalPreview(page);
  assert.match(await id('valuation-closing-average').innerText(), /No stock/);
  await id('valuation-step-3-quantity').fill('2');
  await id('valuation-stale').waitFor();
  const rejected = await uiReply(page, '/api/inventory/valuation/preview', 'POST', () => id('valuation-calculate').click(), 400);
  assert.equal(rejected.body.errors['steps[3].quantity'], 'insufficient_stock');
  await page.getByText('larger than the stock available').waitFor();
  await id('valuation-step-3-quantity').fill('1');
  await noOverflow('preview desktop EN'); await capture('valuation-preview-desktop-en.png');
  mark('8. Sandbox is labelled non-authoritative, shows each step, clears residual on depletion and rejects over-issue');

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${base}/inventory`); await id('inventory-item-FG-SHIRT-L').waitFor();
  await noOverflow('register mobile EN'); await capture('inventory-register-mobile-en.png');
  await page.locator('button[class*="-mt-5"]').click();
  await id('mobile-inventory-action').waitFor();
  await page.keyboard.press('Escape');
  await page.goto(`${base}/inventory/valuation-preview`); await id('valuation-preview-banner').waitFor();
  await noOverflow('preview mobile EN'); await capture('valuation-preview-mobile-en.png');
  mark('9. Mobile English (390px) register and sandbox have no horizontal overflow; the mobile action sheet lists inventory');

  await page.goto(`${base}/inventory`); await id('inventory-item-FG-SHIRT-L').waitFor();
  const languageReply = page.waitForResponse(response => response.url().endsWith('/api/profile/language') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'اردو', exact: true }).first().click();
  assert.equal((await languageReply).status(), 200);
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  assert.match(await id('inventory-catalog-banner').innerText(), /[؀-ۿ]/);
  await id('inventory-create-button').click();
  await fillItem('CN-TAPE-01', 'پیکنگ ٹیپ', 'رول', 'CONSUMABLE', 'پیکنگ', '12');
  assert.equal(await id('inventory-reorder-input').evaluate(element => getComputedStyle(element).direction), 'ltr');
  assert.equal(await id('inventory-sku-input').evaluate(element => getComputedStyle(element).direction), 'ltr');
  await noOverflow('item form mobile UR'); await capture('inventory-form-mobile-ur-rtl.png');
  await uiReply(page, '/api/inventory/items', 'POST', () => id('inventory-save-button').click(), 201);
  await id('inventory-item-CN-TAPE-01').waitFor();
  await noOverflow('register mobile UR'); await capture('inventory-register-mobile-ur-rtl.png');
  mark('10. Urdu RTL at 390px: Urdu copy, LTR numeric/SKU inputs, item creation and no overflow');

  await page.goto(`${base}/inventory/valuation-preview`); await id('valuation-preview-banner').waitFor();
  assert.match(await id('valuation-preview-banner').innerText(), /منصوبہ بندی کا پیش منظر/);
  assert.equal(await id('valuation-opening-quantity').evaluate(element => getComputedStyle(element).direction), 'ltr');
  assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('main')).direction), 'rtl');
  await runFractionalPreview(page);
  await noOverflow('preview mobile UR'); await capture('valuation-preview-mobile-ur-rtl.png');
  mark('11. Urdu RTL sandbox computes the same server result with LTR numeric inputs and no overflow');

  const accountantPage = await accountant.newPage();
  await accountantPage.goto(`${base}/inventory`); await accountantPage.getByTestId('inventory-item-FG-SHIRT-L').waitFor();
  assert.equal(await accountantPage.getByTestId('inventory-readonly-banner').count(), 0);
  await accountantPage.getByTestId('inventory-create-button').click();
  await accountantPage.getByTestId('inventory-sku-input').fill('RS-BUTTON-01');
  await accountantPage.getByTestId('inventory-name-input').fill('Shirt buttons');
  await accountantPage.getByTestId('inventory-unit-input').fill('box');
  await accountantPage.getByTestId('inventory-kind-input').selectOption('RESALE_GOOD');
  await uiReply(accountantPage, '/api/inventory/items', 'POST', () => accountantPage.getByTestId('inventory-save-button').click(), 201);
  await accountant.close();
  mark('12. ACCOUNTANT can create catalog items through the browser');

  const viewerPage = await viewer.newPage();
  await viewerPage.goto(`${base}/inventory`); await viewerPage.getByTestId('inventory-readonly-banner').waitFor();
  await viewerPage.getByTestId('inventory-item-FG-SHIRT-L').waitFor();
  for (const control of ['inventory-create-button', 'inventory-edit-FG-SHIRT-L', 'inventory-archive-FG-SHIRT-L', 'inventory-item-form']) {
    assert.equal(await viewerPage.getByTestId(control).count(), 0, `VIEWER has no ${control}`);
  }
  await api(viewer.request, '/api/inventory/items', { method: 'POST', status: 403, data: { sku: 'DENIED', name: 'x', unit: 'u', itemKind: 'CONSUMABLE' } });
  const shirt = (await api(viewer.request, '/api/inventory/items')).items.find(item => item.sku === 'FG-SHIRT-L');
  await api(viewer.request, `/api/inventory/items/${shirt.id}`, { method: 'PUT', status: 403,
    data: { sku: 'FG-SHIRT-L', name: 'x', unit: 'pcs', itemKind: 'FINISHED_GOOD', expectedVersion: shirt.version } });
  await api(viewer.request, `/api/inventory/items/${shirt.id}/archive`, { method: 'POST', status: 403, data: { expectedVersion: shirt.version } });
  await viewerPage.getByTestId('inventory-preview-link').click();
  await runFractionalPreview(viewerPage);
  await viewer.close();
  mark('13. VIEWER reads the register, has no write controls, is forbidden server-side, and can use the preview');

  const after = await api(context.request, '/api/inventory/items?includeArchived=true');
  assert.deepEqual(after.items.map(item => item.sku).sort(), ['CN-TAPE-01', 'FG-SHIRT-L', 'RM-COTTON-01', 'RS-BUTTON-01']);
  for (const item of after.items) for (const field of ['quantity', 'onHand', 'value', 'averageUnitCost']) assert.equal(field in item, false);
  assert.deepEqual(await api(context.request, '/api/records/monthly'), recordsBefore);
  assert.deepEqual(await api(context.request, '/api/scores/latest'), scoreBefore);
  const posted = [...requests.slice(networkBaseline), ...apiRequests.slice(apiBaseline)].filter(request => request.method !== 'GET'
    && /^\/api\/(records|score|scores|transactions|ledger|projects|invoices|bills|estimates|funding)/.test(request.path));
  assert.deepEqual(posted, []);
  mark('14. MonthlyRecord and score payloads are identical; F08 sent zero record/score/project/commercial/estimate writes');

  assert.deepEqual(errors, []); assert.deepEqual(externalRequests, []);
  mark('15. No page, hydration, CSP or 5xx errors and no external requests');
  writeFileSync(resolve(out, 'results.json'), JSON.stringify({
    scope: 'Production Next + real Spring/session/CSRF + disposable PostgreSQL; synthetic accounts/data; no paid providers',
    checks, errors, externalRequests,
    screenshots: ['inventory-register-desktop-en.png', 'valuation-preview-desktop-en.png', 'inventory-register-mobile-en.png',
      'valuation-preview-mobile-en.png', 'inventory-form-mobile-ur-rtl.png', 'inventory-register-mobile-ur-rtl.png',
      'valuation-preview-mobile-ur-rtl.png'],
  }, null, 2));
  console.log(`RESULT ${checks.length} checks passed`);
} catch (error) {
  await page.screenshot({ path: resolve(out, 'failure.png'), fullPage: true }).catch(() => {});
  writeFileSync(resolve(out, 'failure.txt'), `${error.stack}\n${await page.locator('body').innerText().catch(() => '')}`);
  throw error;
} finally { await browser.close(); }
