// Opt-in production Next + real Spring/session/CSRF + disposable PostgreSQL.
// Launch through F06PersistedEstimatesBrowserAcceptance only. All accounts/data are synthetic.
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
  'Launch through F06PersistedEstimatesBrowserAcceptance so database write probes are installed');
const out = resolve(process.env.BROWSER_ARTIFACT_DIR || '../tmp/f06b/real-browser');
mkdirSync(out, { recursive: true });
const checks = [], errors = [], externalRequests = [], requests = [], apiRequests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Synthetic-F06B-2026!';
const stamp = Date.now();
const email = `f06b-owner-${stamp}@example.test`, viewerEmail = `f06b-viewer-${stamp}@example.test`, managerEmail = `f06b-manager-${stamp}@example.test`;
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
async function settled(target = page) {
  await target.evaluate(async () => {
    await document.fonts?.ready;
    await Promise.all(document.getAnimations({ subtree: true }).map(animation => animation.finished.catch(() => {})));
    await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
  });
}
async function noOverflow(target = page) {
  await settled(target);
  const dimensions = await target.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  assert.ok(dimensions.document <= dimensions.viewport + 2, JSON.stringify(dimensions));
}
async function capture(name, target = page) {
  await settled(target);
  await target.evaluate(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }));
  await target.screenshot({ path: resolve(out, name), fullPage: true });
}
const digits = value => value.replace(/[,\s‎‏⁦⁧⁨⁩]/g, '');
async function waitText(testId, expected, target = page) {
  await target.waitForFunction(([selector, value]) => {
    const element = document.querySelector(`[data-testid="${selector}"]`);
    return Boolean(element) && element.innerText.replace(/[,\s‎‏⁦⁧⁨⁩]/g, '').includes(value);
  }, [testId, digits(expected)]);
}
async function fillLine(index, group, description, quantity, unit, unitCost) {
  await id(`line-group-${index}`).selectOption(group);
  await id(`line-description-${index}`).fill(description);
  await id(`line-quantity-${index}`).fill(quantity);
  await id(`line-unit-${index}`).fill(unit);
  await id(`line-unit-cost-${index}`).fill(unitCost);
}
async function switchBusiness(businessId) {
  await api(context.request, '/api/businesses/active', { method: 'POST', data: { businessId } });
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
}
async function member(memberEmail, role, businessId) {
  const memberContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } }); watchContext(memberContext);
  await api(memberContext.request, '/api/auth/register', { method: 'POST', status: 201, data: { email: memberEmail, password, fullName: `Synthetic Estimate ${role}` } });
  const invite = await api(context.request, '/api/memberships', { method: 'POST', status: 201, data: { email: memberEmail, role } });
  await api(memberContext.request, '/api/auth/login', { method: 'POST', data: { email: memberEmail, password } });
  await api(memberContext.request, `/api/invitations/${invite.id}/accept`, { method: 'POST', status: 204 });
  await api(memberContext.request, '/api/businesses/active', { method: 'POST', data: { businessId } });
  return memberContext;
}
const resinDraft = {
  lines: [{ costGroup: 'MATERIAL', description: 'Precision resin', quantity: '1.0000001', unit: 'kg', unitCost: '1000' }],
  saleableQuantity: '7', pricingMode: 'TARGET_MARGIN', pricingValue: '0.30'
};
const previewProbe = {
  lines: [{ costGroup: 'MATERIAL', description: 'Unchanged F06 probe', quantity: '1', unit: 'batch', unitCost: '800' }],
  saleableQuantity: '10', pricingMode: 'TARGET_MARGIN', pricingValue: '0.20'
};
const writePaths = /^\/api\/(records|score|scores|transactions|ledger|inventory|funding|invoices|bills|documents)/;

try {
  await api(context.request, '/api/auth/register', { method: 'POST', status: 201, data: { email, password, fullName: 'Synthetic Estimate Owner' } });
  await page.goto(`${base}/estimates`);
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await uiReply('/api/auth/login', 'POST', () => page.locator('form button[type="submit"]').click());
  mark('1. Authenticate through the real sign-in UI and session/CSRF backend');

  // All fixture writes happen before the database baseline; none are produced by F06.
  const second = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: {
    businessName: 'F06B Isolation Business', businessType: 'retail', languagePreference: 'en', whatsappOptIn: false } });
  const business = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: {
    businessName: 'F06B Fabrication Studio', businessType: 'manufacturing', languagePreference: 'en', whatsappOptIn: false } });
  assert.equal((await api(context.request, '/api/businesses/active')).businessId, business.businessId);
  for (const month of ['2026-07', '2026-08', '2026-09']) await api(context.request, '/api/records/monthly', {
    method: 'POST', status: 201, data: { month, cashInflow: 10000, cashOutflow: 8000, revenue: 12000,
      operatingExpenses: 3000, cashBalanceEom: 6000, cogs: 4000, receivablesOutstanding: 1500, payablesOutstanding: 700,
      inventoryValue: null, loanOutstanding: 0, interestExpense: 0, financingType: 'none' }
  });
  const project = await api(context.request, '/api/projects', { method: 'POST', status: 201, data: { code: 'F06B-PRJ', name: 'Fabrication order', kind: 'ORDER' } });
  const otherProject = await api(context.request, '/api/projects', { method: 'POST', status: 201, data: { code: 'F06B-PRJ-2', name: 'Second order', kind: 'ORDER' } });
  await api(context.request, '/api/invoices', { method: 'POST', status: 201, data: { counterpartyName: 'Unchanged F04 probe', lines: [] } });
  const recordsBefore = await api(context.request, '/api/records/monthly');
  await api(context.request, '/api/dashboard'); // Warm existing localized advice before the immutable-table baseline.
  const scoreBefore = await api(context.request, '/api/scores/latest');
  assert.equal(scoreBefore.methodologyVersion, 'health-score-v1');
  const invoicesBefore = await api(context.request, '/api/invoices');
  const previewBefore = await api(context.request, '/api/estimates/preview', { method: 'POST', data: previewProbe });
  const networkBaseline = requests.length, apiBaseline = apiRequests.length;
  writeFileSync(process.env.FINSIGHT_DB_BASELINE_READY, 'Synthetic actual financial, project and invoice fixtures are ready for baseline and write probes.');
  const deadline = Date.now() + 120000;
  while (!existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED) && Date.now() < deadline) await delay(100);
  assert.ok(existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED), 'Java must capture the database baseline before estimate actions');

  await page.goto(`${base}/estimates`);
  await id('estimate-empty-state').waitFor();
  assert.equal(await id('estimate-planning-only').innerText(), 'PLANNING ONLY');
  assert.equal(await id('estimate-planned-label').innerText(), 'PLANNED');
  for (const href of ['/', '/estimates', '/projects', '/invoices', '/bills', '/reports', '/records', '/upload', '/settings']) {
    assert.ok(await page.locator(`aside a[href="${href}"]`).count(), `Navigation ${href}`);
  }
  await id('estimate-quick-preview-link').click();
  await page.waitForURL('**/estimates/preview');
  await id('estimate-preview').waitFor();
  assert.equal(await id('estimate-results').count(), 0);
  await page.goto(`${base}/estimates`);
  await id('estimate-empty-state').waitFor();
  mark('2. Register opens empty with PLANNING ONLY/PLANNED labels; the unchanged Phase A preview remains reachable and navigation is preserved');

  await id('estimate-new-button').click();
  await page.waitForURL('**/estimates/new');
  await id('estimate-code').fill('f06b-order');
  await id('estimate-name').fill('Fabricated steel order');
  await fillLine(0, 'MATERIAL', 'Steel sheet', '2.5', 'sheet', '120.125');
  await id('estimate-add-line').click();
  await id('line-description-1').waitFor();
  await fillLine(1, 'FREIGHT', 'Delivery', '1', 'trip', '99.6875');
  await id('estimate-pricing-mode').selectOption('PROPOSED_PRICE');
  await id('estimate-pricing-value').fill('1400');
  await id('estimate-saleable-quantity').fill('10');
  const previewed = await uiReply('/api/estimates/preview', 'POST', () => id('estimate-preview').click());
  assert.equal(previewed.body.totalPlannedCost, '400.00');
  await waitText('estimate-batch-price', '1400.00');
  await waitText('estimate-total-cost', '400.00');
  await waitText('line-total-0', '300.3125');
  await noOverflow(); await capture('estimate-new-desktop-en.png');
  const created = await uiReply('/api/estimates', 'POST', () => id('estimate-save').click(), 201);
  assert.deepEqual(Object.keys(created.sent).sort(), ['code', 'description', 'draft', 'name']);
  assert.deepEqual(Object.keys(created.sent.draft).sort(), ['lines', 'pricingMode', 'pricingValue', 'saleableQuantity']);
  for (const line of created.sent.draft.lines) assert.deepEqual(Object.keys(line).sort(), ['costGroup', 'description', 'quantity', 'unit', 'unitCost']);
  const estimate = created.body.estimate;
  assert.equal(estimate.code, 'F06B-ORDER'); assert.equal(estimate.businessId, business.businessId); assert.equal(estimate.planningOnly, true);
  assert.deepEqual(created.body.revisions[0].result, previewed.body, 'The stored revision is exactly the server preview');
  mark('3. Create an estimate through the UI and calculate a backend-authoritative preview (exact 300.3125 line cost)');

  await page.waitForURL(`**/estimates/${estimate.id}`);
  await id('estimate-history-status-1').waitFor();
  assert.match(await id('estimate-history-status-1').innerText(), /Draft/);
  await waitText('estimate-batch-price', '1400.00');
  assert.equal(await id('line-quantity-0').inputValue(), '2.5');
  mark('4. Save persists only client-owned identity and assumptions; the DRAFT revision reloads from the server');

  const r1 = created.body.revisions[0];
  await api(context.request, `/api/estimates/${estimate.id}/versions/${r1.id}`, { method: 'PUT', data: { ...created.sent.draft, saleableQuantity: '12', expectedVersion: 0 } });
  await id('line-quantity-0').fill('3');
  await uiReply(`/api/estimates/${estimate.id}/versions/${r1.id}`, 'PUT', () => id('estimate-save').click(), 409);
  await id('estimate-conflict-banner').waitFor();
  assert.equal(await id('line-quantity-0').inputValue(), '3', 'Unsaved edits are preserved on conflict');
  await id('estimate-conflict-reload').click();
  await page.waitForFunction(() => !document.querySelector('[data-testid="estimate-conflict-banner"]'));
  assert.equal(await id('estimate-saleable-quantity').inputValue(), '12');
  assert.equal(await id('line-quantity-0').inputValue(), '2.5');
  await id('estimate-saleable-quantity').fill('10');
  const saved = await uiReply(`/api/estimates/${estimate.id}/versions/${r1.id}`, 'PUT', () => id('estimate-save').click());
  assert.equal(saved.sent.expectedVersion, 1); assert.equal(saved.body.version, 2);
  await id('estimate-notice').getByText('Draft saved.').waitFor();
  mark('5. A stale save shows the 409 reload state without losing edits; the reloaded edit then persists');

  await id('estimate-approve').click();
  await id('estimate-approve-dialog').waitFor();
  assert.match(await id('estimate-approve-dialog').innerText(), /does not post money, issue a quotation, or record actual project performance/);
  await page.keyboard.press('Escape');
  await page.waitForFunction(() => !document.querySelector('[data-testid="estimate-approve-dialog"]'));
  await id('estimate-approve').click();
  await id('estimate-approve-dialog').waitFor();
  await page.waitForFunction(() => document.activeElement?.getAttribute('data-testid') === 'estimate-approve-confirm');
  const approved = await uiReply(`/api/estimates/${estimate.id}/versions/${r1.id}/approve`, 'POST', () => page.keyboard.press('Enter'));
  assert.deepEqual(approved.sent, { expectedVersion: 2 });
  assert.equal(approved.body.status, 'APPROVED');
  await id('estimate-readonly-revision').waitFor();
  assert.equal(await id('line-quantity-0').count(), 0, 'Approved revisions have no editor');
  await waitText('estimate-readonly-line-cost-0', '300.3125');
  mark('6. Approve through a keyboard-operable dialog that states approval posts nothing; the APPROVED revision is read-only');

  const next = await uiReply(`/api/estimates/${estimate.id}/versions`, 'POST', () => id('estimate-next-revision').click(), 201);
  assert.equal(next.body.revision, 2); assert.equal(next.body.status, 'DRAFT');
  await id('estimate-history-status-2').waitFor();
  assert.equal(await id('line-unit-cost-0').inputValue(), '120.125', 'The next revision starts from the approved inputs');
  await id('estimate-pricing-value').fill('1500.25');
  await uiReply(`/api/estimates/${estimate.id}/versions/${next.body.id}`, 'PUT', () => id('estimate-save').click());
  mark('7. Create the next revision from the approved one without changing it');

  await id('estimate-approve').click();
  await uiReply(`/api/estimates/${estimate.id}/versions/${next.body.id}/approve`, 'POST', () => id('estimate-approve-confirm').click());
  await page.waitForFunction(() => /Superseded/.test(document.querySelector('[data-testid="estimate-history-status-1"]')?.textContent ?? ''));
  assert.match(await id('estimate-history-status-2').innerText(), /Approved/);
  mark('8. Approving the replacement atomically supersedes revision 1');
  const r1After = await api(context.request, `/api/estimates/${estimate.id}/versions/${r1.id}`);
  assert.equal(r1After.status, 'SUPERSEDED'); assert.equal(r1After.supersededByVersionId, next.body.id);
  assert.equal(r1After.result.plannedBatchPrice, '1400.00', 'Superseded content is unchanged');
  mark('9. Revision 1 is SUPERSEDED by revision 2 with its approved content intact');

  await id('estimate-conversion-panel').waitFor();
  await id('estimate-conversion-target').selectOption(project.id);
  await id('estimate-conversion-review').click();
  await id('estimate-convert-dialog').waitFor();
  assert.equal(await id('estimate-convert-warning').innerText(),
    'This creates a DRAFT project planning budget only. It does not post money or record actual project performance.');
  assert.match(digits(await id('estimate-convert-source-totals').innerText()), /400\.00.*1500\.25/);
  assert.match(await id('estimate-convert-target').innerText(), /F06B-PRJ/);
  await capture('estimate-convert-review-desktop-en.png');
  const converted = await uiReply(`/api/estimates/${estimate.id}/versions/${next.body.id}/convert-to-project`, 'POST',
    () => id('estimate-convert-confirm').click(), 201);
  assert.deepEqual(converted.sent, { targetProjectId: project.id, expectedProjectVersion: 0 });
  assert.equal(converted.body.financialEffect, 'NONE'); assert.equal(converted.body.planningStatus, 'PLANNED_ONLY');
  await id('estimate-conversion-success').waitFor();
  mark('10. Convert the approved revision into the existing F05 project after a planning-only review');

  const budgets = await api(context.request, `/api/projects/${project.id}/budgets`);
  assert.equal(budgets.length, 1); assert.equal(budgets[0].status, 'DRAFT'); assert.equal(budgets[0].planningOnly, true);
  assert.equal(budgets[0].plannedRevenue, '1500.25'); assert.equal(budgets[0].plannedCost, '400.00');
  assert.deepEqual(budgets[0].lines.map(line => [line.category, line.quantity, line.unitCost]), [['MATERIAL', '2.5', '120.125'], ['FREIGHT', '1', '99.6875']]);
  assert.match(budgets[0].sourceEstimateRef, /^F06\/F06B-ORDER\/R2\//);
  mark('11. F05 holds exactly one DRAFT planning budget with the identical exact values');

  await page.reload();
  await id('estimate-conversion-lineage').waitFor();
  await waitText('estimate-lineage-project', 'F06B-PRJ');
  assert.equal(await id('estimate-lineage-open-project').getAttribute('href'), `/projects/${project.id}`);
  const detail = await api(context.request, `/api/estimates/${estimate.id}`);
  assert.equal(detail.conversions.length, 1); assert.equal(detail.conversions[0].projectBudgetVersionId, budgets[0].id);
  await noOverflow(); await capture('estimate-lineage-desktop-en.png');
  mark('12. Immutable conversion lineage is visible after reload and links to the project');

  const replay = await api(context.request, `/api/estimates/${estimate.id}/versions/${next.body.id}/convert-to-project`, {
    method: 'POST', data: { targetProjectId: project.id, expectedProjectVersion: 0 } });
  assert.equal(replay.replayed, true); assert.equal(replay.budget.id, budgets[0].id);
  await api(context.request, `/api/estimates/${estimate.id}/versions/${next.body.id}/convert-to-project`, {
    method: 'POST', status: 409, data: { targetProjectId: otherProject.id, expectedProjectVersion: 0 } });
  assert.equal((await api(context.request, `/api/projects/${project.id}/budgets`)).length, 1);
  assert.equal((await api(context.request, `/api/projects/${otherProject.id}/budgets`)).length, 0);
  mark('13. A duplicate conversion replays safely and a different target is refused; no second budget is created');

  const resin = await api(context.request, '/api/estimates', { method: 'POST', status: 201, data: { code: 'F06B-RESIN', name: 'Precision resin batch', description: null, draft: resinDraft } });
  const resinRevision = resin.revisions[0];
  await api(context.request, `/api/estimates/${resin.estimate.id}/versions/${resinRevision.id}/approve`, { method: 'POST', data: { expectedVersion: 0 } });
  await page.goto(`${base}/estimates/${resin.estimate.id}`);
  await id('estimate-readonly-revision').waitFor();
  await waitText('estimate-batch-price', '1428.571571428571428571428571');
  assert.match(await id('estimate-selected-status').innerText(), /Approved/);
  mark('14. An estimate with more precision than Project Planning supports remains a valid, readable APPROVED estimate');

  await id('estimate-conversion-limitation').waitFor();
  assert.match(await id('estimate-conversion-limitation').innerText(), /This estimate is valid, but some values have more precision than Project Planning supports/);
  assert.equal(await id('estimate-conversion-issue-value-0').innerText(), '1.0000001');
  assert.equal(await id('estimate-conversion-issue-value-1').innerText(), '1428.571571428571428571428571');
  assert.equal(await id('estimate-conversion-target').count(), 0);
  const rejected = await api(context.request, `/api/estimates/${resin.estimate.id}/versions/${resinRevision.id}/convert-to-project`, {
    method: 'POST', status: 422, data: { targetProjectId: otherProject.id, expectedProjectVersion: 0 } });
  assert.equal(rejected.error, 'conversion_precision_unrepresentable'); assert.equal(rejected.estimateValid, true);
  assert.equal((await api(context.request, `/api/projects/${otherProject.id}/budgets`)).length, 0);
  await noOverflow(); await capture('estimate-precision-limitation-desktop-en.png');
  mark('15. Conversion is blocked with exact line/value details and creates nothing in F05');

  const viewer = await member(viewerEmail, 'VIEWER', business.businessId);
  const manager = await member(managerEmail, 'MANAGER', business.businessId);
  for (const [role, roleContext] of [['VIEWER', viewer], ['MANAGER', manager]]) {
    const rolePage = await roleContext.newPage();
    await rolePage.goto(`${base}/estimates`);
    await rolePage.getByTestId('estimate-readonly-banner').waitFor();
    assert.equal(await rolePage.getByTestId('estimate-new-button').count(), 0, role);
    await rolePage.getByTestId('estimate-open-F06B-ORDER').click();
    await rolePage.getByTestId('estimate-readonly-revision').waitFor();
    for (const control of ['estimate-save', 'estimate-approve', 'estimate-next-revision', 'estimate-conversion-panel', 'estimate-archive', 'estimate-save-details']) {
      assert.equal(await rolePage.getByTestId(control).count(), 0, `${role} has no ${control}`);
    }
    await rolePage.getByTestId('estimate-conversion-lineage').waitFor();
    await rolePage.goto(`${base}/estimates/new`);
    await rolePage.getByTestId('estimate-create-forbidden').waitFor();
    await api(roleContext.request, '/api/estimates', { method: 'POST', status: 403, data: { code: 'X', name: 'X' } });
    await api(roleContext.request, `/api/estimates/${estimate.id}/versions/${next.body.id}/convert-to-project`, {
      method: 'POST', status: 403, data: { targetProjectId: otherProject.id, expectedProjectVersion: 0 } });
    await api(roleContext.request, '/api/estimates/preview', { method: 'POST', data: previewProbe });
  }
  await viewer.close(); await manager.close();
  mark('16. MANAGER and VIEWER can read estimates and lineage but cannot create, edit, approve or convert (UI and API)');

  await page.goto(`${base}/estimates/${estimate.id}`);
  await id('estimate-conversion-lineage').waitFor();
  const languageReply = page.waitForResponse(response => response.url().endsWith('/api/profile/language') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'اردو', exact: true }).first().click();
  assert.equal((await languageReply).status(), 200);
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await page.waitForFunction(() => /[؀-ۿ]/.test(document.querySelector('[data-testid="estimate-planning-banner"]')?.textContent ?? ''));
  assert.match(await id('estimate-history-status-1').innerText(), /منسوخ/);
  mark('17. Urdu language switch persists through the real profile API and localizes the estimate workspace');

  assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('main')).direction), 'rtl');
  assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('[data-testid="estimate-code"]')).direction), 'ltr');
  await waitText('estimate-batch-price', '1500.25');
  await noOverflow(); await capture('estimate-detail-desktop-ur-rtl.png');
  mark('18. RTL layout keeps the estimate code and exact amounts LTR-readable');

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${base}/estimates/${resin.estimate.id}`);
  await id('estimate-conversion-limitation').waitFor();
  assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('[data-testid="estimate-conversion-issue-value-1"]')).direction), 'ltr');
  await noOverflow(); await capture('estimate-precision-mobile-ur-rtl.png');
  await page.goto(`${base}/estimates`);
  await id('estimate-row-F06B-ORDER').waitFor();
  await noOverflow(); await capture('estimate-register-mobile-ur-rtl.png');
  await page.getByRole('button', { name: 'English', exact: true }).first().click();
  await page.waitForFunction(() => document.documentElement.dir === 'ltr');
  await page.goto(`${base}/estimates/${estimate.id}`);
  await id('estimate-conversion-lineage').waitFor();
  await noOverflow(); await capture('estimate-detail-mobile-en.png');
  mark('19. Mobile English and Urdu register, detail and precision views have no horizontal overflow');

  await page.setViewportSize({ width: 1440, height: 1000 });
  await switchBusiness(second.businessId);
  await page.goto(`${base}/estimates`);
  await id('estimate-empty-state').waitFor();
  assert.equal(await id('estimate-row-F06B-ORDER').count(), 0);
  await api(context.request, `/api/estimates/${estimate.id}`, { status: 404 });
  await page.goto(`${base}/estimates/${estimate.id}`);
  await id('estimate-not-found').waitFor();
  await switchBusiness(business.businessId);
  await page.goto(`${base}/estimates`);
  await id('estimate-row-F06B-ORDER').waitFor();
  assert.deepEqual(await api(context.request, '/api/records/monthly'), recordsBefore);
  assert.deepEqual(await api(context.request, '/api/scores/latest'), scoreBefore);
  assert.deepEqual(await api(context.request, '/api/invoices'), invoicesBefore);
  assert.deepEqual(await api(context.request, '/api/estimates/preview', { method: 'POST', data: previewProbe }), previewBefore);
  assert.deepEqual(requests.slice(networkBaseline).filter(request => request.method !== 'GET' && writePaths.test(request.path)), []);
  assert.deepEqual(apiRequests.slice(apiBaseline).filter(request => request.method !== 'GET' && writePaths.test(request.path)), []);
  mark('20. Business switching isolates estimates; MonthlyRecord, health-score-v1, F04 invoices and the Phase A preview are unchanged with zero record/score/cash/invoice write requests (Java verifies rows and statements)');
  assert.deepEqual(errors, []);
  mark('21. Zero runtime, CSP or hydration errors and zero 5xx responses');
  assert.deepEqual(externalRequests, []);
  mark('22. Zero unexpected external requests');
  writeFileSync(resolve(out, 'results.json'), JSON.stringify({
    scope: 'Production Next + real Spring/session/CSRF + disposable PostgreSQL + Edge; synthetic accounts/data; no paid providers',
    checks, errors, externalRequests,
    screenshots: ['estimate-new-desktop-en.png', 'estimate-convert-review-desktop-en.png', 'estimate-lineage-desktop-en.png',
      'estimate-precision-limitation-desktop-en.png', 'estimate-detail-desktop-ur-rtl.png', 'estimate-precision-mobile-ur-rtl.png',
      'estimate-register-mobile-ur-rtl.png', 'estimate-detail-mobile-en.png'],
    databaseBoundaryEvidence: 'database-boundaries.json is emitted only after Java verifies complete table snapshots and zero write statements'
  }, null, 2));
  console.log(`RESULT ${checks.length} checks passed`);
} catch (error) {
  await page.screenshot({ path: resolve(out, 'failure.png'), fullPage: true }).catch(() => {});
  writeFileSync(resolve(out, 'failure.txt'), `${error.stack}\n${await page.locator('body').innerText().catch(() => '')}`);
  throw error;
} finally { await browser.close(); }
