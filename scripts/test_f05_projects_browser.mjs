// Opt-in production Next + real Spring/session/CSRF + disposable PostgreSQL.
// Launch through F05ProjectsBrowserAcceptance only. All accounts/data are synthetic.
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
  'Launch through F05ProjectsBrowserAcceptance so database write probes are installed');
const out = resolve(process.env.BROWSER_ARTIFACT_DIR || '../tmp/f05/real-browser');
mkdirSync(out, { recursive: true });
const checks = [], errors = [], externalRequests = [], requests = [], apiRequests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Synthetic-F05-2026!';
const email = `f05-owner-${Date.now()}@example.test`, viewerEmail = email.replace('owner', 'viewer');
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
  return { body: await response.json(), sent: response.request().postDataJSON() };
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
async function metric(name, value) {
  assert.ok((await id(name).innerText()).replaceAll(',', '').includes(value), `${name} must show server value ${value}`);
}
async function createProject(kind) {
  await id('create-project-button').click();
  await id('project-create-form').waitFor();
  const code = `F05-${kind}`;
  await id('project-code-input').fill(code);
  await id('project-kind-select').selectOption(kind);
  await id('project-name-input').fill(`Synthetic ${kind.toLowerCase()} plan`);
  await id('project-description-input').fill('Isolated acceptance plan; all figures are planned, never actual financial postings.');
  await id('project-start-date-input').fill('2026-10-01');
  await id('project-target-end-date-input').fill('2026-11-30');
  const { body: created, sent } = await uiReply('/api/projects', 'POST', () => id('project-create-submit').click(), 201);
  assert.deepEqual(Object.keys(sent).sort(), ['code', 'description', 'kind', 'name', 'startDate', 'targetEndDate']);
  assert.equal(created.kind, kind); assert.equal(created.code, code); assert.equal(created.status, 'DRAFT');
  assert.equal(created.planningOnly, true); assert.equal(created.baseCurrency, 'PKR');
  await id('project-detail-workspace').waitFor();
  assert.equal(await id('detail-project-code').innerText(), code);
  await id('detail-back-to-projects').click();
  await id(`project-item-${code}`).waitFor();
  await page.reload(); // Persistence must survive an actual page load, not only local UI state.
  await id(`project-item-${code}`).waitFor();
  return created;
}
async function fillBudgetLine(index, category, unitCost) {
  await id(`budget-line-description-${index}`).fill(`Synthetic ${category.toLowerCase()} planning cost`);
  await id(`budget-line-category-${index}`).selectOption(category);
  await id(`budget-line-quantity-${index}`).fill('2');
  await id(`budget-line-unit-${index}`).fill('batch');
  await id(`budget-line-unit-cost-${index}`).fill(unitCost);
}
function assertBudget(budget, revenue, cost, margin, revision, status) {
  assert.equal(budget.plannedRevenue, revenue); assert.equal(budget.plannedCost, cost); assert.equal(budget.plannedMargin, margin);
  assert.equal(budget.plannedMarginPct, '0.2'); assert.equal(budget.planningOnly, true);
  assert.equal(budget.baseCurrency, 'PKR'); assert.equal(budget.calculationVersion, 'f05-plan-v1');
  assert.equal(budget.sourceEstimateRef, null); assert.equal(budget.revision, revision); assert.equal(budget.status, status);
}
async function approve(project, draft) {
  await id('approve-draft-budget-button').click();
  await id('confirm-approve-budget-dialog').waitFor();
  const { body } = await uiReply(`/api/projects/${project.id}/budgets/${draft.id}/approve`, 'POST', () => id('confirm-approve-budget-submit').click());
  await id('summary-planned-revenue').waitFor();
  await id('start-new-budget-revision-button').waitFor();
  return body;
}
const estimatePayload = {
  lines: [{ costGroup: 'MATERIAL', description: 'Unchanged F06 probe', quantity: '1', unit: 'batch', unitCost: '800' }],
  saleableQuantity: '10', pricingMode: 'TARGET_MARGIN', pricingValue: '0.20'
};

try {
  await api(context.request, '/api/auth/register', { method: 'POST', status: 201, data: { email, password, fullName: 'Synthetic Project Owner' } });
  await page.goto(`${base}/projects`);
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await uiReply('/api/auth/login', 'POST', () => page.locator('form button[type="submit"]').click());
  mark('1. Authenticate through the real sign-in UI and session/CSRF backend');

  const business = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: {
    businessName: 'F05 Project Planning Studio', businessType: 'manufacturing', languagePreference: 'en', whatsappOptIn: false
  } });
  assert.equal((await api(context.request, '/api/businesses/active')).businessId, business.businessId);
  mark('2. The authenticated session has the real active business selected');
  // Fixture writes occur before the database baseline and are never produced by F05.
  for (const month of ['2026-07', '2026-08', '2026-09']) await api(context.request, '/api/records/monthly', {
    method: 'POST', status: 201, data: { month, cashInflow: 10000, cashOutflow: 8000, revenue: 12000,
      operatingExpenses: 3000, cashBalanceEom: 6000, cogs: 4000, receivablesOutstanding: 0, payablesOutstanding: 0,
      inventoryValue: null, loanOutstanding: 0, interestExpense: 0, financingType: 'none' }
  });
  const recordsBefore = await api(context.request, '/api/records/monthly');
  await api(context.request, '/api/dashboard'); // Warm existing localized advice before the immutable-table baseline.
  const scoreBefore = await api(context.request, '/api/scores/latest');
  assert.equal(scoreBefore.methodologyVersion, 'health-score-v1');
  const estimateBefore = await api(context.request, '/api/estimates/preview', { method: 'POST', data: estimatePayload });
  assert.equal(estimateBefore.totalPlannedCost, '800.00'); assert.equal(estimateBefore.plannedBatchPrice, '1000.00');
  const networkBaseline = requests.length, apiBaseline = apiRequests.length;
  writeFileSync(process.env.FINSIGHT_DB_BASELINE_READY, 'Synthetic actual financial fixtures are ready for full-row baseline and write probes.');
  const deadline = Date.now() + 120000;
  while (!existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED) && Date.now() < deadline) await delay(100);
  assert.ok(existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED), 'Java must capture the actual database baseline before planning mutations');

  await page.goto(`${base}/projects`);
  await id('projects-empty-state').waitFor();
  assert.match(await page.locator('aside a[href="/projects"]').innerText(), /Projects/);
  assert.match(await page.locator('aside a[href="/reports"]').innerText(), /Reports/);
  for (const path of ['/', '/estimates', '/records', '/upload', '/settings']) {
    assert.ok(await page.locator(`aside a[href="${path}"]`).count(), `Existing navigation ${path}`);
  }
  mark('3. Open the real Projects route; Reports and all existing navigation remain present');
  const projects = [];
  for (const kind of ['PROJECT', 'JOB', 'ORDER']) projects.push(await createProject(kind));
  mark('4. Create PROJECT, JOB and ORDER records through the browser forms');
  const persisted = await api(context.request, '/api/projects');
  assert.deepEqual(persisted.items.map(item => item.kind).sort(), ['JOB', 'ORDER', 'PROJECT']);
  for (const project of projects) {
    assert.ok(persisted.items.some(item => item.id === project.id && item.businessId === business.businessId));
    await id(`project-item-${project.code}`).waitFor();
  }
  await noOverflow(); await capture('projects-list-desktop-en.png');
  mark('5. All three persisted records survive reload and appear in the real server list');
  const project = projects[0], budgetPath = `/api/projects/${project.id}/budgets`;
  await id(`open-project-${project.code}`).click();
  await id('project-detail-workspace').waitFor();
  assert.equal(await id('detail-project-name').innerText(), project.name);
  assert.match(await id('no-approved-budget-msg').innerText(), /No approved budget/);
  mark('6. Open persisted project detail with explicit no-approved-budget state');

  const categories = ['MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER'];
  const costs = ['100', '150', '50', '75', '25'];
  await id('budget-planned-revenue-input').fill('1000');
  for (let i = 0; i < categories.length; i++) {
    if (i) await id('add-budget-line-button').click();
    await fillBudgetLine(i, categories[i], costs[i]);
  }
  // Corrupt only an outbound numeric field: the error response comes from the real backend.
  await page.route(`**${budgetPath}`, async route => {
    if (route.request().method() !== 'POST') return route.continue();
    const body = route.request().postDataJSON(); body.lines[0].quantity = '1e3';
    await route.continue({ postData: JSON.stringify(body) });
  }, { times: 1 });
  await uiReply(budgetPath, 'POST', () => id('save-draft-budget-button').click(), 400);
  await id('detail-action-error').waitFor();
  assert.equal(await id('budget-planned-revenue-input').inputValue(), '1000');
  assert.equal(await id('budget-line-unit-cost-4').inputValue(), '25');
  const first = await uiReply(budgetPath, 'POST', () => id('save-draft-budget-button').click(), 201);
  await id('budget-revision-status-1').waitFor();
  assertBudget(first.body, '1000.00', '800.00', '200.00', 1, 'DRAFT');
  assert.deepEqual(Object.keys(first.sent).sort(), ['expectedProjectVersion', 'lines', 'plannedRevenue']);
  for (const line of first.sent.lines) { assert.equal(typeof line.quantity, 'string'); assert.equal(typeof line.unitCost, 'string'); }
  assert.match(await id('budget-revision-status-1').innerText(), /Draft Plan/);
  mark('7. Create a persisted DRAFT budget after a genuine backend validation error preserves inputs and allows retry');
  assert.deepEqual(first.body.categorySubtotals, { MATERIAL: '200.00', LABOUR: '300.00', OVERHEAD: '100.00', FREIGHT: '150.00', OTHER: '50.00' });
  assert.equal(first.body.lines.length, 5);
  mark('8. Five planned cost categories reconcile to their exact backend subtotals');
  const draftRead = (await api(context.request, budgetPath))[0];
  assert.equal(draftRead.plannedRevenue, '1000.00'); await metric('budget-revision-1', '1000.00');
  mark('9. Persisted backend-authoritative planned revenue is 1000.00 and is rendered in history');
  assert.equal(draftRead.plannedCost, '800.00'); await metric('budget-revision-1', '800.00');
  mark('10. Persisted backend-authoritative planned cost is 800.00');
  assert.equal(draftRead.plannedMargin, '200.00'); assert.equal(draftRead.plannedMarginPct, '0.2'); await metric('budget-revision-1', '200.00');
  mark('11. Persisted backend-authoritative planned margin is 200.00 and margin rate is 0.2');
  const firstApproved = await approve(project, first.body);
  assertBudget(firstApproved, '1000.00', '800.00', '200.00', 1, 'APPROVED');
  await metric('summary-planned-revenue', '1000.00'); await metric('summary-planned-cost', '800.00'); await metric('summary-planned-margin', '200.00');
  assert.match(await id('summary-planned-margin-pct').innerText(), /20\s*%/);
  mark('12. Approve the first budget through the real confirmation UI and render the approved server values');
  await id('start-new-budget-revision-button').click();
  assert.equal(await id('budget-line-unit-cost-0').inputValue(), '100');
  await id('budget-planned-revenue-input').fill('1200');
  const revisionCosts = ['120', '180', '60', '90', '30'];
  for (let i = 0; i < categories.length; i++) await id(`budget-line-unit-cost-${i}`).fill(revisionCosts[i]);
  const second = await uiReply(budgetPath, 'POST', () => id('save-draft-budget-button').click(), 201);
  await id('budget-revision-status-2').waitFor();
  assertBudget(second.body, '1200.00', '960.00', '240.00', 2, 'DRAFT');
  await metric('summary-planned-revenue', '1000.00');
  mark('13. Create a new persisted DRAFT revision while keeping the original approved summary');
  const secondApproved = await approve(project, second.body);
  assertBudget(secondApproved, '1200.00', '960.00', '240.00', 2, 'APPROVED');
  await metric('summary-planned-revenue', '1200.00'); await metric('summary-planned-cost', '960.00'); await metric('summary-planned-margin', '240.00');
  mark('14. Approve the second revision and render its exact server-authoritative planned totals');
  const history = await api(context.request, budgetPath);
  assert.equal(history.length, 2);
  const superseded = history.find(item => item.id === first.body.id);
  assertBudget(superseded, '1000.00', '800.00', '200.00', 1, 'SUPERSEDED');
  assert.equal(superseded.supersededByBudgetId, second.body.id); assert.ok(superseded.supersededAt);
  assert.deepEqual(superseded.lines, firstApproved.lines);
  assert.match(await id('budget-revision-status-1').innerText(), /Superseded Plan/);
  assert.match(await id('budget-revision-status-2').innerText(), /Approved Plan/);
  await api(context.request, `${budgetPath}/${first.body.id}`, { method: 'PUT', status: 409,
    data: { expectedBudgetVersion: superseded.version, plannedRevenue: '999', lines: first.sent.lines } });
  assert.deepEqual((await api(context.request, budgetPath)).find(item => item.id === first.body.id), superseded);
  mark('15. Previous approved revision becomes SUPERSEDED with original financials/lines unchanged and server mutation rejected');
  const summary = await api(context.request, `/api/projects/${project.id}/planning-summary`);
  assert.equal(summary.planningOnly, true); assert.equal(summary.actualFinancialsStatus, 'NOT_IMPLEMENTED_PHASE_A');
  assert.equal(summary.activeApprovedBudget.id, second.body.id); assert.equal(summary.latestDraftBudget, null);
  assert.equal(summary.supersededCount, 1); assert.equal(summary.calculation.accountingRoundingPolicy, null);
  assert.match(await id('detail-planning-banner').innerText(), /PLANNED/);
  // The catalog endpoint checks availability without invoking the unrelated dashboard financial/advice loader.
  const widgetCatalog = await api(context.request, '/api/workspace/widgets');
  assert.ok(!widgetCatalog.some(widget => widget.key === 'project-margin' && widget.available));
  const projectWidget = widgetCatalog.find(widget => widget.key === 'projects');
  assert.ok(projectWidget, 'Assert the real registered future projects widget, not only an absent key');
  assert.equal(projectWidget.implemented, false); assert.equal(projectWidget.available, false);
  assert.equal(projectWidget.reason, 'NOT_IMPLEMENTED');
  const configuration = await api(context.request, '/api/business-configuration');
  for (const key of ['projects', 'reports']) {
    const module = configuration.modules.find(item => item.key === key);
    assert.ok(module); assert.equal(module.implemented, false); assert.equal(module.available, false);
  }
  await noOverflow(); await capture('project-detail-budget-desktop-en.png');
  mark('16. Planning summary remains PLANNED_ONLY, actual financials unimplemented and F13 project-margin unavailable');
  await page.setViewportSize({ width: 390, height: 844 });
  await noOverflow(); await capture('project-mobile-en.png');
  const languageReply = page.waitForResponse(response => response.url().endsWith('/api/profile/language') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'اردو', exact: true }).first().click();
  assert.equal((await languageReply).status(), 200);
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  assert.match(await id('detail-planning-banner').innerText(), /[\u0600-\u06ff]/);
  mark('17. English-to-Urdu language switch persists through the real profile API');
  assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('main')).direction), 'rtl');
  mark('18. Urdu project detail uses actual RTL layout');
  assert.equal(page.viewportSize().width, 390); await metric('summary-planned-revenue', '1200.00');
  mark('19. Approved plan and revision history remain available at the 390px mobile viewport');
  await noOverflow(); await capture('project-detail-mobile-ur-rtl.png');
  mark('20. English desktop/mobile and Urdu mobile have no horizontal document overflow');

  const viewer = await browser.newContext({ viewport: { width: 1440, height: 1000 } }); watchContext(viewer);
  await api(viewer.request, '/api/auth/register', { method: 'POST', status: 201, data: { email: viewerEmail, password, fullName: 'Synthetic Project Viewer' } });
  const invite = await api(context.request, '/api/memberships', { method: 'POST', status: 201, data: { email: viewerEmail, role: 'VIEWER' } });
  await api(viewer.request, '/api/auth/login', { method: 'POST', data: { email: viewerEmail, password } });
  await api(viewer.request, `/api/invitations/${invite.id}/accept`, { method: 'POST', status: 204 });
  await api(viewer.request, '/api/businesses/active', { method: 'POST', data: { businessId: business.businessId } });
  const viewerPage = await viewer.newPage();
  await viewerPage.goto(`${base}/projects`); await viewerPage.getByTestId('projects-readonly-banner').waitFor();
  assert.equal(await viewerPage.getByTestId('create-project-button').count(), 0);
  await viewerPage.getByTestId(`open-project-${project.code}`).click(); await viewerPage.getByTestId('detail-readonly-role-banner').waitFor();
  for (const control of ['edit-metadata-button', 'delete-draft-project-button', 'budget-editor-card', 'project-transition-form']) {
    assert.equal(await viewerPage.getByTestId(control).count(), 0, `VIEWER has no ${control}`);
  }
  await viewerPage.goto(`${base}/projects/new`); await viewerPage.getByTestId('project-create-forbidden').waitFor();
  assert.equal(await viewerPage.getByTestId('project-create-form').count(), 0);
  const currentProject = await api(viewer.request, `/api/projects/${project.id}`);
  await api(viewer.request, '/api/projects', { method: 'POST', status: 403, data: { code: 'FORBIDDEN', kind: 'JOB', name: 'Forbidden plan' } });
  await api(viewer.request, budgetPath, { method: 'POST', status: 403, data: { expectedProjectVersion: currentProject.version, plannedRevenue: '1000', lines: first.sent.lines } });
  await api(viewer.request, `${budgetPath}/${second.body.id}/approve`, { method: 'POST', status: 403,
    data: { expectedProjectVersion: currentProject.version, expectedBudgetVersion: secondApproved.version } });
  await viewer.close();
  mark('21. Real VIEWER UI hides all mutation controls and create/budget/approval requests are forbidden server-side');
  assert.deepEqual(await api(context.request, '/api/records/monthly'), recordsBefore);
  assert.deepEqual(requests.slice(networkBaseline).filter(request => request.method !== 'GET' && /^\/api\/(records|score|transactions|ledger|inventory|funding)/.test(request.path)), []);
  assert.deepEqual(apiRequests.slice(apiBaseline).filter(request => request.method !== 'GET' && /^\/api\/(records|score|transactions|ledger|inventory|funding)/.test(request.path)), []);
  mark('22. Actual MonthlyRecord payloads remain identical and F05 sends zero financial posting requests; Java verifies full rows and zero DB write statements');
  assert.deepEqual(await api(context.request, '/api/scores/latest'), scoreBefore);
  mark('23. Stored health-score-v1 payload remains identical; Java verifies complete score rows and zero DB write statements');
  assert.deepEqual(await api(context.request, '/api/estimates/preview', { method: 'POST', data: estimatePayload }), estimateBefore);
  assert.deepEqual(requests.slice(networkBaseline).filter(request => request.method !== 'GET' && request.path.startsWith('/api/estimates')), []);
  for (const budget of history) assert.equal(budget.sourceEstimateRef, null);
  mark('24. F06 exact preview is unchanged; project journey performs no F06 writes or persisted estimate conversion');
  assert.deepEqual(errors, []); assert.deepEqual(externalRequests, []);
  writeFileSync(resolve(out, 'results.json'), JSON.stringify({
    scope: 'Production Next + real Spring/session/CSRF + disposable PostgreSQL; synthetic accounts/data; F05 creations and approvals use real UI; malformed request uses actual backend response; no paid providers',
    checks, errors, externalRequests,
    screenshots: ['projects-list-desktop-en.png', 'project-detail-budget-desktop-en.png', 'project-mobile-en.png', 'project-detail-mobile-ur-rtl.png'],
    databaseBoundaryEvidence: 'database-boundaries.json is emitted only after Java verifies nine complete table snapshots and zero actual-record/score write statements; profile language changes are intentional'
  }, null, 2));
  console.log(`RESULT ${checks.length} checks passed`);
} catch (error) {
  await page.screenshot({ path: resolve(out, 'failure.png'), fullPage: true }).catch(() => {});
  writeFileSync(resolve(out, 'failure.txt'), `${error.stack}\n${await page.locator('body').innerText().catch(() => '')}`);
  throw error;
} finally { await browser.close(); }
