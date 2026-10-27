// Opt-in production Next + real Spring/session/CSRF + disposable PostgreSQL.
// Launch through F04CommercialBrowserAcceptance only. All accounts/data are synthetic.
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
  'Launch through F04CommercialBrowserAcceptance so database write probes are installed');
const out = resolve(process.env.BROWSER_ARTIFACT_DIR || '../tmp/f04/real-browser');
mkdirSync(out, { recursive: true });
const checks = [], errors = [], externalRequests = [], requests = [], apiRequests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Synthetic-F04-2026!';
const email = `f04-owner-${Date.now()}@example.test`, viewerEmail = email.replace('owner', 'viewer');
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
const id = name => page.getByTestId(name);
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
async function uiReply(path, method, action, status = 200) {
  const pending = page.waitForResponse(response => new URL(response.url()).pathname === path && response.request().method() === method);
  await action();
  const response = await pending;
  assert.equal(response.status(), status, `${method} ${path}: ${await response.text()}`);
  return { body: response.status() === 204 ? null : await response.json(), sent: response.request().postDataJSON() };
}
async function settled() {
  await page.evaluate(async () => {
    await document.fonts?.ready;
    await Promise.all(document.getAnimations({ subtree: true }).map(animation => animation.finished.catch(() => {})));
    await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
  });
}
async function noOverflow() {
  await settled();
  const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  assert.ok(dimensions.document <= dimensions.viewport + 2, JSON.stringify(dimensions));
}
async function capture(name) {
  await settled();
  await page.evaluate(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }));
  await page.screenshot({ path: resolve(out, name), fullPage: true });
}
const digits = value => value.replace(/[,\s‎‏]/g, '');
async function waitText(testId, expected) {
  await page.waitForFunction(([selector, value]) => {
    const element = document.querySelector(`[data-testid="${selector}"]`);
    return Boolean(element) && element.innerText.replace(/[,\s‎‏]/g, '').includes(value);
  }, [testId, digits(expected)]);
}
async function fillLine(index, description, quantity, unit, unitPrice) {
  await id(`commercial-line-description-${index}`).fill(description);
  await id(`commercial-line-quantity-${index}`).fill(quantity);
  await id(`commercial-line-unit-${index}`).fill(unit);
  await id(`commercial-line-unit-price-${index}`).fill(unitPrice);
}
async function switchBusiness(businessId) {
  await api(context.request, '/api/businesses/active', { method: 'POST', data: { businessId } });
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
}
const estimatePayload = {
  lines: [{ costGroup: 'MATERIAL', description: 'Unchanged F06 probe', quantity: '1', unit: 'batch', unitCost: '800' }],
  saleableQuantity: '10', pricingMode: 'TARGET_MARGIN', pricingValue: '0.20'
};
const forbiddenSettlementKeys = ['amountPaid', 'amountDue', 'outstandingBalance', 'paymentStatus'];

try {
  await api(context.request, '/api/auth/register', { method: 'POST', status: 201, data: { email, password, fullName: 'Synthetic Commercial Owner' } });
  await page.goto(`${base}/invoices`);
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await uiReply('/api/auth/login', 'POST', () => page.locator('form button[type="submit"]').click());
  mark('1. Authenticate through the real sign-in UI and session/CSRF backend');

  // All fixture writes happen before the database baseline; none are produced by F04.
  const second = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: {
    businessName: 'F04 Isolation Business', businessType: 'retail', languagePreference: 'en', whatsappOptIn: false } });
  const business = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: {
    businessName: 'F04 Commercial Studio', businessType: 'manufacturing', languagePreference: 'en', whatsappOptIn: false } });
  assert.equal((await api(context.request, '/api/businesses/active')).businessId, business.businessId);
  for (const month of ['2026-07', '2026-08', '2026-09']) await api(context.request, '/api/records/monthly', {
    method: 'POST', status: 201, data: { month, cashInflow: 10000, cashOutflow: 8000, revenue: 12000,
      operatingExpenses: 3000, cashBalanceEom: 6000, cogs: 4000, receivablesOutstanding: 1500, payablesOutstanding: 700,
      inventoryValue: null, loanOutstanding: 0, interestExpense: 0, financingType: 'none' }
  });
  await api(context.request, '/api/projects', { method: 'POST', status: 201, data: { code: 'F04-PRJ', name: 'Unchanged F05 probe', kind: 'PROJECT' } });
  const recordsBefore = await api(context.request, '/api/records/monthly');
  await api(context.request, '/api/dashboard'); // Warm existing localized advice before the immutable-table baseline.
  const scoreBefore = await api(context.request, '/api/scores/latest');
  assert.equal(scoreBefore.methodologyVersion, 'health-score-v1');
  const projectsBefore = await api(context.request, '/api/projects');
  const estimateBefore = await api(context.request, '/api/estimates/preview', { method: 'POST', data: estimatePayload });
  const networkBaseline = requests.length, apiBaseline = apiRequests.length;
  writeFileSync(process.env.FINSIGHT_DB_BASELINE_READY, 'Synthetic actual financial and project fixtures are ready for full-row baseline and write probes.');
  const deadline = Date.now() + 120000;
  while (!existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED) && Date.now() < deadline) await delay(100);
  assert.ok(existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED), 'Java must capture the database baseline before commercial actions');

  await page.goto(`${base}/invoices`);
  await id('commercial-empty-state').waitFor();
  await id('commercial-record-only-banner').waitFor();
  for (const href of ['/', '/invoices', '/bills', '/projects', '/reports', '/estimates', '/records', '/upload', '/settings']) {
    assert.ok(await page.locator(`aside a[href="${href}"]`).count(), `Navigation ${href}`);
  }
  assert.match(await page.locator('aside a[href="/invoices"]').innerText(), /Invoices/);
  assert.match(await page.locator('aside a[href="/bills"]').innerText(), /Bills/);
  mark('2. Invoices route opens with empty state; Invoices/Bills added while Projects, Reports, Estimates and existing navigation remain');

  await id('commercial-new-button').click();
  await id('commercial-editor').waitFor();
  await id('commercial-counterparty-input').fill('Acme Traders');
  await id('commercial-document-date-input').fill('2026-10-28');
  await id('commercial-due-date-input').fill('2026-11-27');
  await id('commercial-notes-input').fill('Net 30. Synthetic acceptance invoice.');
  await fillLine(0, 'Design work', '2', 'day', '500.00');
  await id('commercial-add-line').click();
  await fillLine(1, 'Cabling', '1.5', 'metre', '333.33');
  await waitText('commercial-line-errors-1', 'Quantity × unit price must be exact');
  assert.equal(await id('commercial-preview-total').innerText(), '—');
  await id('commercial-line-quantity-1').fill('3.5');
  await id('commercial-line-unit-price-1').fill('120.40');
  await id('commercial-add-line').focus();
  await page.keyboard.press('Enter');
  await id('commercial-line-description-2').waitFor();
  await fillLine(2, 'Goodwill visit', '1', '', '0');
  mark('3. Create an invoice with several line items using mouse and keyboard; a non-exact product is rejected by the backend');

  await waitText('commercial-preview-total', '1421.40');
  await waitText('commercial-line-amount-0', '1000.00');
  await waitText('commercial-line-amount-1', '421.40');
  await waitText('commercial-line-amount-2', '0.00');
  assert.equal(await id('commercial-preview-total').getAttribute('dir'), 'ltr');
  await noOverflow(); await capture('invoice-desktop-en.png');
  mark('4. Backend preview supplies exact line amounts and the 1,421.40 total');

  const created = await uiReply('/api/invoices', 'POST', () => id('commercial-save-draft').click(), 201);
  assert.deepEqual(Object.keys(created.sent).sort(), ['counterpartyName', 'documentDate', 'dueDate', 'lines', 'notes']);
  for (const line of created.sent.lines) { assert.deepEqual(Object.keys(line).sort(), ['description', 'quantity', 'unit', 'unitPrice']); assert.equal(typeof line.unitPrice, 'string'); }
  const invoice = created.body;
  assert.equal(invoice.status, 'DRAFT'); assert.equal(invoice.total, '1421.40'); assert.equal(invoice.documentNumber, null);
  assert.equal(invoice.financialEffect, 'NONE'); assert.equal(invoice.businessId, business.businessId);
  for (const key of forbiddenSettlementKeys) assert.ok(!(key in invoice), key);
  await page.waitForURL(`**/invoices/${invoice.id}`);
  await id('commercial-editor').waitFor();
  await waitText('commercial-preview-total', '1421.40');
  mark('5. Save draft persists only client-owned fields; server returns DRAFT, no number, financialEffect NONE');

  // A concurrent server-side edit makes the browser copy stale: 409, edits preserved, explicit reload.
  await api(context.request, `/api/invoices/${invoice.id}`, { method: 'PUT', data: {
    counterpartyName: 'Acme Traders', documentDate: '2026-10-28', dueDate: '2026-11-27', notes: 'Updated by another session',
    lines: created.sent.lines, expectedVersion: 0 } });
  await id('commercial-line-quantity-0').fill('3');
  await uiReply(`/api/invoices/${invoice.id}`, 'PUT', () => id('commercial-save-draft').click(), 409);
  await id('commercial-conflict-banner').waitFor();
  assert.equal(await id('commercial-line-quantity-0').inputValue(), '3', 'Unsaved edits are preserved on conflict');
  await id('commercial-conflict-reload').click();
  await page.waitForFunction(() => !document.querySelector('[data-testid="commercial-conflict-banner"]'));
  assert.equal(await id('commercial-notes-input').inputValue(), 'Updated by another session');
  assert.equal(await id('commercial-line-quantity-0').inputValue(), '2');
  await id('commercial-line-quantity-0').fill('3');
  await waitText('commercial-preview-total', '1921.40');
  const edited = await uiReply(`/api/invoices/${invoice.id}`, 'PUT', () => id('commercial-save-draft').click());
  assert.equal(edited.sent.expectedVersion, 1); assert.equal(edited.body.version, 2); assert.equal(edited.body.total, '1921.40');
  await page.reload();
  await id('commercial-editor').waitFor();
  assert.equal(await id('commercial-line-quantity-0').inputValue(), '3');
  await waitText('commercial-preview-total', '1921.40');
  mark('6. Edit draft: a stale save shows the 409 reload state without losing edits, then the reloaded edit persists');

  await id('commercial-finalize-button').click();
  await id('commercial-finalize-dialog').waitFor();
  const dialogText = await id('commercial-finalize-dialog').innerText();
  for (const phrase of ['Finalizing records and freezes this commercial document only.', 'does NOT', 'post money',
    'create a receivable or payable', 'record a payment', 'alter financial health calculations']) assert.ok(dialogText.includes(phrase), phrase);
  await page.keyboard.press('Escape');
  await page.waitForFunction(() => !document.querySelector('[data-testid="commercial-finalize-dialog"]'));
  await id('commercial-finalize-button').click();
  await id('commercial-finalize-dialog').waitFor();
  await page.waitForFunction(() => document.activeElement?.closest('[data-testid="commercial-finalize-dialog"]'));
  await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), 'commercial-finalize-confirm');
  const finalized = await uiReply(`/api/invoices/${invoice.id}/finalize`, 'POST', () => page.keyboard.press('Enter'));
  assert.deepEqual(finalized.sent, { expectedVersion: 2 });
  mark('7. Finalize through a keyboard-operable confirmation that states the document has no financial effect');

  assert.equal(finalized.body.documentNumber, 'INV-000001'); assert.equal(finalized.body.status, 'FINALIZED');
  await waitText('commercial-document-number', 'INV-000001');
  assert.equal(await id('commercial-document-number').getAttribute('dir'), 'ltr');
  mark('8. The finalized invoice receives INV-000001');

  await id('commercial-readonly-state').waitFor();
  for (const control of ['commercial-editor', 'commercial-save-draft', 'commercial-finalize-button', 'commercial-delete-button', 'commercial-line-editor']) {
    assert.equal(await id(control).count(), 0, `Finalized document has no ${control}`);
  }
  await waitText('commercial-readonly-line-amount-0', '1500.00');
  const localToday = await page.evaluate(() => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; });
  assert.equal(await id('commercial-finalized-date').innerText(), localToday, 'Finalized date is shown in the local calendar, not UTC');
  await waitText('commercial-preview-total', '1921.40');
  await api(context.request, `/api/invoices/${invoice.id}`, { method: 'PUT', status: 409, data: { lines: [], expectedVersion: 3 } });
  await api(context.request, `/api/invoices/${invoice.id}?expectedVersion=3`, { method: 'DELETE', status: 409 });
  await noOverflow(); await capture('invoice-finalized-desktop-en.png');
  mark('9. Finalized invoice is read-only in the UI and immutable through the API');

  await id('commercial-void-button').click();
  await id('commercial-void-dialog').waitFor();
  await id('commercial-void-confirm').click();
  await id('commercial-void-reason-error').waitFor();
  await id('commercial-void-reason').fill('Customer cancelled the order');
  const voided = await uiReply(`/api/invoices/${invoice.id}/void`, 'POST', () => id('commercial-void-confirm').click());
  assert.deepEqual(voided.sent, { expectedVersion: 3, reason: 'Customer cancelled the order' });
  assert.equal(voided.body.status, 'VOID'); assert.equal(voided.body.documentNumber, 'INV-000001');
  await waitText('commercial-void-reason-display', 'Customer cancelled the order');
  assert.equal(await id('commercial-void-button').count(), 0);
  assert.match(await id('commercial-status-badge').innerText(), /Void/);
  mark('10. Void requires a reason, keeps INV-000001 and leaves the document permanently read-only');

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${base}/bills/new`);
  await id('commercial-editor').waitFor();
  await id('commercial-counterparty-input').fill('Steel Supplier Ltd');
  await fillLine(0, 'Steel sheets', '4', 'sheet', '2500.00');
  await id('commercial-add-line').click();
  await fillLine(1, 'Delivery', '1', '', '750.50');
  await waitText('commercial-preview-total', '10750.50');
  await noOverflow(); await capture('bill-editor-mobile-en.png');
  const billCreated = await uiReply('/api/bills', 'POST', () => id('commercial-save-draft').click(), 201);
  await page.waitForURL(`**/bills/${billCreated.body.id}`);
  await id('commercial-finalize-button').click();
  const billFinal = await uiReply(`/api/bills/${billCreated.body.id}/finalize`, 'POST', () => id('commercial-finalize-confirm').click());
  assert.equal(billFinal.body.kind, 'BILL'); assert.equal(billFinal.body.total, '10750.50');
  mark('11. Create and finalize a supplier bill through the mobile UI');

  assert.equal(billFinal.body.documentNumber, 'BILL-000001');
  await waitText('commercial-document-number', 'BILL-000001');
  const nextInvoice = await api(context.request, '/api/invoices', { method: 'POST', status: 201, data: {
    counterpartyName: 'Beta Retail', documentDate: '2026-10-29', lines: [{ description: 'Service', quantity: '1', unitPrice: '99.99' }] } });
  assert.equal((await api(context.request, `/api/invoices/${nextInvoice.id}/finalize`, { method: 'POST', data: { expectedVersion: 0 } })).documentNumber, 'INV-000002');
  await noOverflow(); await capture('bill-mobile-en.png');
  mark('12. BILL numbering starts independently at BILL-000001 while invoices continue at INV-000002');

  const languageReply = page.waitForResponse(response => response.url().endsWith('/api/profile/language') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'اردو', exact: true }).first().click();
  assert.equal((await languageReply).status(), 200);
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await page.waitForFunction(() => /[؀-ۿ]/.test(document.querySelector('[data-testid="commercial-record-only-banner"]')?.textContent ?? ''));
  assert.match(await id('commercial-status-badge').innerText(), /حتمی/);
  mark('13. Urdu language switch persists through the real profile API and localizes the bill');

  assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('main')).direction), 'rtl');
  for (const ltr of ['commercial-document-number', 'commercial-preview-total']) {
    assert.equal(await page.evaluate(selector => getComputedStyle(document.querySelector(`[data-testid="${selector}"]`)).direction, ltr), 'ltr', ltr);
  }
  await waitText('commercial-document-number', 'BILL-000001');
  await noOverflow(); await capture('bill-mobile-ur-rtl.png');
  await page.goto(`${base}/invoices/${invoice.id}`);
  await id('commercial-readonly-state').waitFor();
  assert.match(await id('commercial-status-badge').innerText(), /منسوخ/);
  await waitText('commercial-preview-total', '1921.40');
  await noOverflow(); await capture('invoice-mobile-ur-rtl.png');
  mark('14. Urdu RTL layout keeps document numbers and amounts LTR-readable on mobile');

  await page.goto(`${base}/invoices`);
  await id(`commercial-row-${invoice.id}`).waitFor();
  await noOverflow();
  mark('15. Mobile Urdu invoice list and documents have no horizontal overflow');

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.getByRole('button', { name: 'English', exact: true }).first().click();
  await page.waitForFunction(() => document.documentElement.dir === 'ltr');
  await page.getByRole('button', { name: 'اردو', exact: true }).first().waitFor();
  await id(`commercial-row-${invoice.id}`).waitFor();
  await switchBusiness(second.businessId);
  await id('commercial-empty-state').waitFor();
  assert.equal(await id(`commercial-row-${invoice.id}`).count(), 0);
  await api(context.request, `/api/invoices/${invoice.id}`, { status: 404 });
  await api(context.request, `/api/bills/${billCreated.body.id}`, { status: 404 });
  await page.goto(`${base}/invoices/${invoice.id}`);
  await id('commercial-not-found').waitFor();
  assert.equal((await api(context.request, '/api/bills')).items.length, 0);
  await switchBusiness(business.businessId);
  await page.goto(`${base}/invoices`);
  await id(`commercial-row-${invoice.id}`).waitFor();
  mark('16. Active-business switching hides and denies the other business documents in UI and API');

  const viewer = await browser.newContext({ viewport: { width: 1440, height: 1000 } }); watchContext(viewer);
  await api(viewer.request, '/api/auth/register', { method: 'POST', status: 201, data: { email: viewerEmail, password, fullName: 'Synthetic Commercial Viewer' } });
  const invite = await api(context.request, '/api/memberships', { method: 'POST', status: 201, data: { email: viewerEmail, role: 'VIEWER' } });
  await api(viewer.request, '/api/auth/login', { method: 'POST', data: { email: viewerEmail, password } });
  await api(viewer.request, `/api/invitations/${invite.id}/accept`, { method: 'POST', status: 204 });
  await api(viewer.request, '/api/businesses/active', { method: 'POST', data: { businessId: business.businessId } });
  const viewerPage = await viewer.newPage();
  await viewerPage.goto(`${base}/bills`);
  await viewerPage.getByTestId('commercial-readonly-banner').waitFor();
  assert.equal(await viewerPage.getByTestId('commercial-new-button').count(), 0);
  await viewerPage.getByTestId(`commercial-open-${billCreated.body.id}`).click();
  await viewerPage.getByTestId('commercial-readonly-role-banner').waitFor();
  for (const control of ['commercial-void-button', 'commercial-editor', 'commercial-actions']) {
    assert.equal(await viewerPage.getByTestId(control).count(), 0, `VIEWER has no ${control}`);
  }
  await viewerPage.goto(`${base}/invoices/new`);
  await viewerPage.getByTestId('commercial-create-forbidden').waitFor();
  await api(viewer.request, '/api/invoices', { method: 'POST', status: 403, data: { lines: [] } });
  await api(viewer.request, `/api/bills/${billCreated.body.id}/void`, { method: 'POST', status: 403, data: { expectedVersion: 1, reason: 'x' } });
  await api(viewer.request, '/api/invoices/preview', { method: 'POST', data: { lines: [] } });
  await api(viewer.request, '/api/invoices', { method: 'POST', status: 403, csrf: false, data: { lines: [] } });
  await viewer.close();
  mark('17. VIEWER UI is read-only; mutations are forbidden server-side while preview/read remain allowed');

  const invoices = await api(context.request, '/api/invoices');
  assert.deepEqual(invoices.items.map(item => [item.documentNumber, item.status]).sort(), [['INV-000001', 'VOID'], ['INV-000002', 'FINALIZED']]);
  assert.equal(invoices.financialEffect, 'NONE'); assert.equal(invoices.settlementAvailability, 'NOT_AVAILABLE_PHASE_A');
  for (const item of invoices.items) for (const key of forbiddenSettlementKeys) assert.ok(!(key in item), key);
  mark('18. Persisted register shows exactly INV-000001 (VOID) and INV-000002 with no settlement fields');

  assert.deepEqual(await api(context.request, '/api/records/monthly'), recordsBefore);
  assert.deepEqual(requests.slice(networkBaseline).filter(request => request.method !== 'GET' && /^\/api\/(records|score|transactions|ledger|inventory|funding|projects|estimates)/.test(request.path)), []);
  assert.deepEqual(apiRequests.slice(apiBaseline).filter(request => request.method !== 'GET' && /^\/api\/(records|score|transactions|ledger|inventory|funding|projects|estimates)/.test(request.path)), []);
  mark('19. MonthlyRecord payloads are identical and F04 sent zero record/project/estimate write requests; Java verifies rows and DB statements');
  assert.deepEqual(await api(context.request, '/api/scores/latest'), scoreBefore);
  assert.deepEqual(await api(context.request, '/api/projects'), projectsBefore);
  assert.deepEqual(await api(context.request, '/api/estimates/preview', { method: 'POST', data: estimatePayload }), estimateBefore);
  mark('20. Stored health-score-v1, F05 projects and the F06 preview are unchanged');
  assert.deepEqual(errors, []);
  mark('21. Zero runtime, CSP or hydration errors and zero 5xx responses');
  assert.deepEqual(externalRequests, []);
  mark('22. Zero unexpected external requests');
  writeFileSync(resolve(out, 'results.json'), JSON.stringify({
    scope: 'Production Next + real Spring/session/CSRF + disposable PostgreSQL + Edge; synthetic accounts/data; no paid providers',
    checks, errors, externalRequests,
    screenshots: ['invoice-desktop-en.png', 'invoice-finalized-desktop-en.png', 'bill-editor-mobile-en.png', 'bill-mobile-en.png', 'bill-mobile-ur-rtl.png', 'invoice-mobile-ur-rtl.png'],
    databaseBoundaryEvidence: 'database-boundaries.json is emitted only after Java verifies complete table snapshots and zero write statements'
  }, null, 2));
  console.log(`RESULT ${checks.length} checks passed`);
} catch (error) {
  await page.screenshot({ path: resolve(out, 'failure.png'), fullPage: true }).catch(() => {});
  writeFileSync(resolve(out, 'failure.txt'), `${error.stack}\n${await page.locator('body').innerText().catch(() => '')}`);
  throw error;
} finally { await browser.close(); }
