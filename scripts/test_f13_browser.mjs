// Opt-in real Next + Spring HTTP + disposable PostgreSQL. Synthetic accounts only.
// Launch through F13BrowserAcceptance; never against a customer or developer database.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.FINSIGHT_BROWSER_BASE_URL || 'http://127.0.0.1:3100';
assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/);
const out = resolve(process.env.BROWSER_ARTIFACT_DIR || '../tmp/f13/real-browser');
mkdirSync(out, { recursive: true });
const checks = [], errors = [], externalRequests = [], requests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Synthetic-F13-2026!';
const email = `f13-owner-${Date.now()}@example.test`, viewerEmail = email.replace('owner', 'viewer');
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
page.setDefaultTimeout(20000);
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => { if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(message.text())) errors.push(message.text()); });
context.on('request', request => {
  requests.push({ url: new URL(request.url()).pathname, method: request.method() });
  if (/^https?:/.test(request.url()) && new URL(request.url()).hostname !== '127.0.0.1') externalRequests.push(new URL(request.url()).origin);
});
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
const versions = value => ({ version: value.preferenceVersion, registryVersion: value.registryVersion, configurationVersion: value.configurationVersion });
const configBody = value => ({ operatingProfile: value.operatingProfile, operatingAnswers: value.operatingAnswers, requestedModules: value.requestedModules, version: value.version });
const getWorkspace = client => api(client || context.request, '/api/workspace');
const save = (value, widgets, client = context.request, status = 200) => api(client, '/api/workspace/preferences', { method: 'PUT', data: { ...versions(value), widgets }, status });
const layout = value => value.preferences;
async function login() {
  await page.goto(base);
  await page.getByLabel(/^Email/).fill(email); await page.getByLabel(/^Password/).fill(password);
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.getByTestId('dashboard-workspace').waitFor();
}
try {
  await api(context.request, '/api/auth/register', { method: 'POST', status: 201, data: { email, password, fullName: 'Synthetic Workspace Owner' } });
  await api(context.request, '/api/auth/login', { method: 'POST', data: { email, password } });
  const firstBusiness = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: { businessName: 'F13 Manufacturing Studio', businessType: 'manufacturing', languagePreference: 'en', whatsappOptIn: false } });
  let workspace = await getWorkspace();
  assert.equal(workspace.presetKey, 'manufacturing'); assert.equal(workspace.dashboard.score, null);
  assert.deepEqual(workspace.records, []); assert.equal(workspace.preferenceVersion, 0);
  await page.goto(base); await page.getByTestId('dashboard-workspace').waitFor();
  await page.getByTestId('workspace-empty').waitFor();
  assert.equal(await page.locator('[data-testid^="workspace-widget-"]').count(), 0);
  await page.screenshot({ path: resolve(out, 'empty-workspace.png'), fullPage: true });
  mark('Authenticated empty workspace uses F02 manufacturing preset and preserves missing finance');

  // Gaps and null optional balances are intentional; no invented historical month or COGS.
  for (const record of [
    { month: '2025-12', cashInflow: 1.11, cashOutflow: 0.12, revenue: 2.22, operatingExpenses: 0.13, cashBalanceEom: 0.99, cogs: null },
    { month: '2026-07', cashInflow: 100000.11, cashOutflow: 60000.01, revenue: 120000.23, operatingExpenses: 41000.11, cashBalanceEom: 40000.10, cogs: 21000.12 },
    { month: '2026-08', cashInflow: 95000.22, cashOutflow: 70000.02, revenue: 100000.34, operatingExpenses: 39000.22, cashBalanceEom: 65000.30, cogs: 20000.23 },
    { month: '2026-09', cashInflow: 90000.33, cashOutflow: 80000.03, revenue: 98000.45, operatingExpenses: 42000.33, cashBalanceEom: 75000.60, cogs: null }
  ]) await api(context.request, '/api/records/monthly', { method: 'POST', status: 201, data: { ...record, receivablesOutstanding: 0, payablesOutstanding: 0, inventoryValue: null, loanOutstanding: 0, interestExpense: 0, financingType: 'none' } });
  const recordsBefore = await api(context.request, '/api/records/monthly');
  const canonical = await api(context.request, '/api/dashboard');
  workspace = await getWorkspace();
  assert.deepEqual(workspace.dashboard.score, canonical.score);
  assert.deepEqual(workspace.dashboard.cashFlowHistory, canonical.cashFlowHistory);
  assert.deepEqual(workspace.dashboard.trendProjection, canonical.trendProjection);
  assert.deepEqual(workspace.records, recordsBefore);
  assert.equal(workspace.dashboard.score.methodologyVersion, 'health-score-v1');
  assert.equal(workspace.sourceMode, 'SNAPSHOT');
  assert.equal(workspace.cashSummaryByPeriod['3'].totalInflow, 285000.66);
  assert.equal(workspace.cashSummaryByPeriod['3'].totalOutflow, 210000.06);
  assert.equal(workspace.cashSummaryByPeriod['3'].net, 75000.60);
  assert.equal(workspace.cashSummaryByPeriod['3'].endingCash, 75000.60);
  assert.equal(workspace.cashSummaryByPeriod.all.totalInflow, 285001.77);
  mark('Batched workspace preserves canonical score, cash flow, projection, monthly evidence and nulls');

  let configuration = await api(context.request, '/api/business-configuration');
  const resolved = [];
  for (const [operatingProfile, presetKey] of [['MANUFACTURING', 'manufacturing'], ['RETAIL_TRADE', 'retail-trade'], ['SERVICES', 'services'], ['PROJECT_ORDER', 'project-order']]) {
    configuration = await api(context.request, '/api/business-configuration', { method: 'PUT', data: { ...configBody(configuration), operatingProfile } });
    workspace = await getWorkspace(); assert.equal(workspace.presetKey, presetKey);
    const preview = await api(context.request, `/api/workspace/presets/${presetKey}`);
    assert.deepEqual(preview.widgets, workspace.widgets); resolved.push(JSON.stringify(workspace.widgets));
    await page.reload(); await page.getByTestId('dashboard-workspace').waitFor();
    await page.screenshot({ path: resolve(out, `preset-${presetKey}.png`), fullPage: true });
  }
  assert.equal(new Set(resolved).size, 4);
  assert.equal((await api(context.request, '/api/businesses/active')).businessType, 'manufacturing');
  mark('All four real F02 profile changes resolve distinct presets without changing the score category');

  await api(context.request, '/api/workspace/preferences', { method: 'PUT', csrf: false, status: 403, data: { ...versions(workspace), widgets: layout(workspace) } });
  await save(workspace, [...layout(workspace), { key: 'arbitrary-widget', order: 99, size: 'wide', hidden: false }], context.request, 400);
  await save(workspace, [{ key: 'inventory', order: 99, size: 'wide', hidden: false }], context.request, 403);
  await api(context.request, '/api/workspace/preferences', { method: 'PUT', status: 400, data: { ...versions(workspace), widgets: layout(workspace), businessId: firstBusiness.businessId } });
  mark('Real session/CSRF and closed preference validation reject forged scope and unavailable widget keys');

  // UI customization acceptance is deliberately in the same real authenticated session.
  const batchStart = requests.length;
  await page.reload(); await page.getByTestId('dashboard-workspace').waitFor();
  const batchRequests = requests.slice(batchStart);
  assert.ok(batchRequests.some(request => request.url === '/api/workspace'));
  assert.ok(!batchRequests.some(request => ['/api/dashboard', '/api/records/monthly', '/api/ai/explain'].includes(request.url)));
  mark('Dashboard loads one batched financial endpoint and never generates an AI explanation automatically');
  const beforePreview = await getWorkspace();
  assert.ok(beforePreview.dashboard.topRecommendation?.text);
  const liveRec = page.getByTestId('workspace-widget-recommendation');
  await liveRec.getByText(beforePreview.dashboard.topRecommendation.text, { exact: true }).waitFor();
  await liveRec.getByText('New', { exact: true }).waitFor();
  assert.equal(await liveRec.getByRole('button', { name: 'Mark done', exact: true }).count(), 1);
  await page.getByTestId('workspace-preset-preview').click();
  const presetReply = page.waitForResponse(r => r.url().endsWith('/api/workspace/presets/retail-trade'));
  await page.getByTestId('workspace-preset-select').selectOption('retail-trade');
  assert.equal((await (await presetReply).json()).presetKey, 'retail-trade');
  await liveRec.getByText(beforePreview.dashboard.topRecommendation.text, { exact: true }).waitFor();
  await liveRec.getByText('New', { exact: true }).waitFor();
  assert.equal(await liveRec.getByRole('button', { name: 'Mark done', exact: true }).count(), 0);
  await page.getByTestId('workspace-close-preview').waitFor();
  await page.getByTestId('workspace-close-preview').click();
  assert.deepEqual((await getWorkspace()).preferences, beforePreview.preferences);
  await page.getByTestId('workspace-customize').click();
  await liveRec.getByText(beforePreview.dashboard.topRecommendation.text, { exact: true }).waitFor();
  assert.equal(await liveRec.getByRole('button', { name: 'Mark done', exact: true }).count(), 0);
  await page.getByTestId('workspace-hide-cash-flow').click();
  await page.getByTestId('workspace-preview-changes').click();
  assert.equal(await page.getByTestId('workspace-widget-cash-flow').count(), 0);
  assert.equal((await getWorkspace()).preferenceVersion, beforePreview.preferenceVersion);
  await page.getByTestId('workspace-cancel').click();
  await page.getByTestId('workspace-widget-cash-flow').waitFor();
  mark('Live recommendation widget renders text/status/actions while preset and draft previews hide mutation buttons and Cancel restores saved dashboard');

  await page.getByTestId('workspace-customize').click();
  // Move second widget into first position via keyboard and verify focus stays on its active move button.
  await page.getByTestId('workspace-up-cash-flow').focus();
  await page.keyboard.press('Enter');
  assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), 'workspace-down-cash-flow');
  await page.keyboard.press('Enter');
  assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), 'workspace-down-cash-flow');
  await page.getByTestId('workspace-hide-cash-projection').click();
  const addDocuments = page.getByTestId('workspace-add-document-workflow');
  if (await addDocuments.count()) await addDocuments.click();
  await page.getByTestId('workspace-size-document-workflow').selectOption('wide');
  const orderBeforeMove = await page.locator('[data-testid^="workspace-widget-"]').evaluateAll(elements => elements.map(element => element.dataset.testid));
  // Move penultimate widget down into last position via keyboard and verify focus moves to its up button.
  const penultimateKey = orderBeforeMove.at(-2).replace('workspace-widget-', '');
  await page.getByTestId(`workspace-down-${penultimateKey}`).focus();
  await page.keyboard.press('Enter');
  assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), `workspace-up-${penultimateKey}`);
  await page.keyboard.press('Enter');
  await page.getByTestId('workspace-up-document-workflow').focus();
  await page.keyboard.press('Enter');
  await page.getByTestId('workspace-preview-changes').click();
  await page.getByTestId('workspace-widget-document-workflow').waitFor();
  const savedResponse = page.waitForResponse(r => r.url().endsWith('/api/workspace/preferences') && r.request().method() === 'PUT');
  await page.getByTestId('workspace-save').click();
  assert.equal((await savedResponse).status(), 200);
  await page.getByTestId('workspace-customize').waitFor();
  workspace = await getWorkspace();
  assert.equal(workspace.personalized, true);
  assert.ok(!workspace.widgets.some(w => w.key === 'cash-projection'));
  assert.equal(workspace.widgets.find(w => w.key === 'document-workflow').size, 'wide');
  assert.ok(workspace.widgets.findIndex(w => w.key === 'document-workflow') < orderBeforeMove.indexOf('workspace-widget-document-workflow'));
  const personalLayout = workspace.widgets;
  await page.reload(); await page.getByTestId('dashboard-workspace').waitFor();
  assert.deepEqual((await getWorkspace()).widgets, personalLayout);
  assert.equal(await page.getByTestId('workspace-widget-cash-projection').count(), 0);
  mark('Keyboard reorder (preserving first/last focus), add, hide, resize, preview and Save persist through real PostgreSQL and reload');

  await page.goto(`${base}/settings`); await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await page.getByRole('heading', { name: 'Sign in to FinSight' }).waitFor();
  await login(); assert.deepEqual((await getWorkspace()).widgets, personalLayout);
  mark('Personal dashboard survives actual logout and login');

  // A second real browser tab saves concurrently. Clicking Reload latest dashboard must rebase the draft and allow saving.
  await page.getByTestId('workspace-customize').click();
  await page.getByTestId('workspace-hide-cash-flow').click();
  const tab = await context.newPage(); await tab.goto(base); await tab.getByTestId('workspace-customize').click();
  await tab.getByTestId('workspace-size-next-step').selectOption('wide');
  await tab.getByTestId('workspace-save').click(); await tab.getByTestId('workspace-customize').waitFor();
  const conflict = page.waitForResponse(r => r.url().endsWith('/api/workspace/preferences') && r.request().method() === 'PUT');
  await page.getByTestId('workspace-save').click(); assert.equal((await conflict).status(), 409);
  await page.getByRole('alert').filter({ hasText: 'changed in another session' }).waitFor();
  assert.equal(await page.getByTestId('workspace-save').isDisabled(), true);
  assert.equal(await page.getByTestId('workspace-add-cash-flow').count(), 1);
  await tab.close();
  const reloadReply = page.waitForResponse(r => r.url().endsWith('/api/workspace') && r.request().method() === 'GET');
  await page.getByTestId('workspace-reload').click();
  assert.equal((await reloadReply).status(), 200);
  await page.waitForFunction(() => !document.querySelector('[data-testid="workspace-save"]')?.disabled);
  // Next's empty route announcer is also role=alert, so only visible alert text is considered.
  const alertsAfterReload = await page.getByRole('alert').allInnerTexts();
  assert.equal(await page.getByRole('alert').filter({ hasText: 'changed in another session' }).count(), 0);
  assert.equal(alertsAfterReload.filter(text => text.trim()).length, 0, `Unexpected visible alert: ${JSON.stringify(alertsAfterReload)}`);
  assert.equal(await page.getByTestId('workspace-add-cash-flow').count(), 1);
  assert.equal(await page.getByTestId('workspace-size-next-step').inputValue(), 'wide');
  const saveAfterReload = page.waitForResponse(r => r.url().endsWith('/api/workspace/preferences') && r.request().method() === 'PUT');
  await page.getByTestId('workspace-save').click();
  assert.equal((await saveAfterReload).status(), 200);
  await page.getByTestId('workspace-customize').waitFor();
  workspace = await getWorkspace();
  assert.ok(!workspace.widgets.some(w => w.key === 'cash-flow'));
  assert.equal(workspace.widgets.find(w => w.key === 'next-step').size, 'wide');
  // Restore cash-flow and next-step to personalLayout baseline for subsequent assertions.
  workspace = await save(workspace, layout(workspace).map(w => w.key === 'cash-flow' ? { ...w, hidden: false } : w.key === 'next-step' ? { ...w, size: 'compact' } : w));
  await page.reload(); await page.getByTestId('dashboard-workspace').waitFor();
  mark('Two browser tabs receive a visible 409 conflict and Reload latest dashboard rebases the draft onto the newer server layout and saves');

  configuration = await api(context.request, '/api/business-configuration');
  const enabledModules = configuration.requestedModules;
  workspace = await getWorkspace(); const documentsBefore = layout(workspace).find(w => w.key === 'document-workflow');
  configuration = await api(context.request, '/api/business-configuration', { method: 'PUT', data: { ...configBody(configuration), requestedModules: enabledModules.filter(key => !['documents', 'ai'].includes(key)) } });
  await save(workspace, layout(workspace), context.request, 409);
  workspace = await getWorkspace();
  assert.ok(!workspace.widgets.some(w => ['document-workflow', 'ai-explanation'].includes(w.key)));
  assert.ok(!layout(workspace).some(w => ['document-workflow', 'ai-explanation'].includes(w.key)));
  // Saving another layout while disabled must not erase the server-retained optional override.
  workspace = await save(workspace, layout(workspace));
  await page.reload(); await page.getByTestId('dashboard-workspace').waitFor();
  assert.equal(await page.getByTestId('ai-dashboard').count(), 0);
  await api(context.request, '/api/ai/explain', { method: 'POST', status: 403, data: { type: 'dashboard', language: 'en' } });
  configuration = await api(context.request, '/api/business-configuration', { method: 'PUT', data: { ...configBody(configuration), requestedModules: enabledModules } });
  workspace = await getWorkspace();
  const documentsAfter = layout(workspace).find(w => w.key === 'document-workflow');
  assert.equal(documentsAfter.hidden, documentsBefore.hidden); assert.equal(documentsAfter.size, documentsBefore.size);
  mark('Module disable removes access, rejects stale configuration and preserves optional choices across another save');

  await page.reload(); await page.getByTestId('dashboard-workspace').waitFor();
  await page.getByTestId('ai-dashboard').waitFor();
  const aiReply = page.waitForResponse(r => r.url().endsWith('/api/ai/explain') && r.request().method() === 'POST');
  await page.getByTestId('ai-dashboard').getByRole('button', { name: 'Explain this result', exact: true }).click();
  assert.equal((await (await aiReply).json()).provenance.mode, 'simulated');
  await page.getByTestId('ai-provenance').waitFor();
  mark('Optional AI stays opt-in with explicit simulated provenance and no paid request');

  const viewer = await browser.newContext();
  await api(viewer.request, '/api/auth/register', { method: 'POST', status: 201, data: { email: viewerEmail, password, fullName: 'Synthetic Workspace Viewer' } });
  const invite = await api(context.request, '/api/memberships', { method: 'POST', status: 201, data: { email: viewerEmail, role: 'VIEWER' } });
  await api(viewer.request, '/api/auth/login', { method: 'POST', data: { email: viewerEmail, password } });
  await api(viewer.request, `/api/invitations/${invite.id}/accept`, { method: 'POST', status: 204 });
  await api(viewer.request, '/api/businesses/active', { method: 'POST', data: { businessId: firstBusiness.businessId } });
  let viewerWorkspace = await getWorkspace(viewer.request);
  assert.equal(viewerWorkspace.preferenceVersion, 0); assert.equal(viewerWorkspace.personalized, false);
  assert.ok(!viewerWorkspace.widgets.some(w => w.key === 'document-workflow'));
  await save(viewerWorkspace, [{ key: 'document-workflow', order: 99, size: 'wide', hidden: false }], viewer.request, 403);
  viewerWorkspace = await save(viewerWorkspace, layout(viewerWorkspace).map(w => w.key === 'cash-flow' ? { ...w, hidden: true } : w), viewer.request);
  assert.ok(!viewerWorkspace.widgets.some(w => w.key === 'cash-flow'));
  assert.ok((await getWorkspace()).widgets.some(w => w.key === 'cash-flow'));
  await viewer.close(); mark('Viewer can personalize permitted data only; injecting a forbidden key fails and owner preferences stay isolated');

  const ownerBeforeSwitch = await getWorkspace();
  const secondBusiness = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: { businessName: 'F13 Empty Services', businessType: 'services', languagePreference: 'en', whatsappOptIn: false } });
  await page.reload(); await page.getByTestId('dashboard-workspace').waitFor();
  let empty = await getWorkspace();
  assert.equal(empty.dashboard.businessId, secondBusiness.businessId); assert.deepEqual(empty.records, []);
  assert.equal(empty.preferenceVersion, 0); assert.equal(empty.personalized, false);
  await page.goto(`${base}/settings`); await page.getByLabel('Active business').selectOption(firstBusiness.businessId);
  await page.waitForFunction(() => document.querySelector('[data-testid="operating-profile"]')?.value === 'PROJECT_ORDER');
  await page.goto(base); await page.getByTestId('dashboard-workspace').waitFor();
  assert.deepEqual((await getWorkspace()).widgets, ownerBeforeSwitch.widgets);
  mark('Actual business switching clears another tenant’s financial data and restores only that user’s saved layout');

  await page.getByTestId('workspace-customize').click();
  await page.getByTestId('workspace-size-ai-explanation').selectOption('compact');
  await page.getByTestId('workspace-hide-next-step').click();
  configuration = await api(context.request, '/api/business-configuration');
  const fullModules = configuration.requestedModules;
  configuration = await api(context.request, '/api/business-configuration', { method: 'PUT', data: { ...configBody(configuration), operatingProfile: 'SERVICES', requestedModules: fullModules.filter(key => key !== 'ai') } });
  const staleConfiguration = page.waitForResponse(r => r.url().endsWith('/api/workspace/preferences') && r.request().method() === 'PUT');
  await page.getByTestId('workspace-save').click(); assert.equal((await staleConfiguration).status(), 409);
  await page.getByRole('alert').filter({ hasText: 'changed in another session' }).waitFor();
  await page.getByTestId('workspace-reload').click();
  await page.waitForFunction(() => !document.querySelector('[data-testid="workspace-save"]')?.disabled);
  assert.equal(await page.getByTestId('workspace-widget-ai-explanation').count(), 0);
  assert.equal(await page.getByTestId('workspace-unavailable-ai-explanation').count(), 1);
  assert.equal(await page.getByTestId('workspace-add-next-step').count(), 1);
  await page.getByTestId('workspace-save').click();
  await page.getByTestId('workspace-customize').waitFor();
  assert.ok(!(await getWorkspace()).widgets.some(w => w.key === 'next-step' || w.key === 'ai-explanation'));
  configuration = await api(context.request, '/api/business-configuration', { method: 'PUT', data: { ...configBody(configuration), operatingProfile: 'SERVICES', requestedModules: fullModules } });
  await page.reload(); await page.getByTestId('dashboard-workspace').waitFor();
  await page.getByTestId('workspace-reset').click(); await page.getByTestId('workspace-cancel-reset').click();
  assert.equal((await getWorkspace()).personalized, true);
  await page.getByTestId('workspace-reset').click();
  const resetReply = page.waitForResponse(r => r.url().endsWith('/api/workspace/reset') && r.request().method() === 'POST');
  await page.getByTestId('workspace-confirm-reset').click(); assert.equal((await resetReply).status(), 200);
  await page.getByTestId('workspace-confirm-reset').waitFor({ state: 'detached' });
  await page.getByTestId('workspace-customize').waitFor();
  workspace = await getWorkspace(); assert.equal(workspace.personalized, false); assert.equal(workspace.presetKey, 'services');
  assert.deepEqual(workspace.widgets, (await api(context.request, '/api/workspace/presets/services')).widgets);
  mark('Configuration conflict reload drops newly unavailable widgets while preserving valid draft changes; confirmed reset restores services preset');

  // Customize -> modify layout -> switch to Urdu -> verify draft still exists and can be saved.
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByTestId('workspace-customize').click();
  await page.getByTestId('workspace-hide-next-step').click();
  await page.getByTestId('workspace-down-cash-flow').focus(); await page.keyboard.press('Enter');
  const languageUpdate = page.waitForResponse(r => r.url().endsWith('/api/profile/language') && r.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'اردو', exact: true }).first().click();
  assert.equal((await languageUpdate).status(), 200);
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  assert.equal(await page.getByTestId('workspace-save').count(), 1);
  assert.equal(await page.getByTestId('workspace-add-next-step').count(), 1);
  await page.getByTestId('workspace-preview-changes').click();
  assert.equal(await page.getByTestId('workspace-widget-next-step').count(), 0);
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2));
  await page.screenshot({ path: resolve(out, 'workspace-urdu-mobile-preview.png'), fullPage: true });
  const urduSave = page.waitForResponse(r => r.url().endsWith('/api/workspace/preferences') && r.request().method() === 'PUT');
  await page.getByTestId('workspace-save').click(); assert.equal((await urduSave).status(), 200);
  await page.getByTestId('workspace-customize').waitFor();
  workspace = await getWorkspace();
  assert.equal(workspace.personalized, true);
  assert.ok(!workspace.widgets.some(w => w.key === 'next-step'));
  assert.equal((await api(context.request, '/api/profile')).languagePreference, 'ur');
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2));
  await page.screenshot({ path: resolve(out, 'workspace-urdu-mobile.png'), fullPage: true });
  mark('Switching to Urdu during customization preserves the active draft, renders RTL mobile preview without overflow, and saves');

  // End the actual server session while an edit is open; focus revalidation must clear private state.
  await page.getByTestId('workspace-customize').click();
  await api(context.request, '/api/auth/logout', { method: 'POST', status: 204 });
  await api(context.request, '/api/workspace', { status: 401 });
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await page.locator('input[type="email"]').waitFor();
  assert.equal(await page.getByTestId('dashboard-workspace').count(), 0);
  mark('Expired real session removes the private workspace and unsaved draft');

  await api(context.request, '/api/auth/login', { method: 'POST', data: { email, password } });
  await api(context.request, '/api/businesses/active', { method: 'POST', data: { businessId: firstBusiness.businessId } });

  assert.deepEqual(await api(context.request, '/api/records/monthly'), recordsBefore);
  assert.deepEqual(errors, []); assert.deepEqual(externalRequests, []);
  writeFileSync(resolve(out, 'results.json'), JSON.stringify({ scope: 'Real production Next/Spring/disposable PostgreSQL; synthetic data; simulated AI; no paid provider', checks, errors, externalRequests }, null, 2));
  console.log(`RESULT ${checks.length} checks passed`);
} catch (error) {
  await page.screenshot({ path: resolve(out, 'failure.png'), fullPage: true }).catch(() => {});
  writeFileSync(resolve(out, 'failure.txt'), `${error.stack}\n${await page.locator('body').innerText().catch(() => '')}`);
  throw error;
} finally { await browser.close(); }
