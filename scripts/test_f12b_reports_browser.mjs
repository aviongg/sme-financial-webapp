// Opt-in production Next + real Spring session/CSRF + disposable PostgreSQL for F12B Phase A reports.
// Launch through F12bReportsBrowserAcceptance only; never use a developer/customer database.
// Financial records below are isolated synthetic fixtures, never a live application fallback.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.FINSIGHT_BROWSER_BASE_URL || 'http://127.0.0.1:3100';
assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/);
const out = resolve(process.env.BROWSER_ARTIFACT_DIR || '../tmp/f12b/real-browser');
mkdirSync(out, { recursive: true });
const checks = [], errors = [], externalRequests = [], requests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Synthetic-F12B-2026!';
const email = `f12b-owner-${Date.now()}@example.test`, viewerEmail = email.replace('owner', 'viewer');
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
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
const JSON_ENDPOINT = '/api/reports/business-financial-summary', CSV_ENDPOINT = '/api/reports/business-financial-summary.csv';
const reportRequests = () => requests.filter(request => request.path === JSON_ENDPOINT && request.method === 'POST').length;
const csvRequests = () => requests.filter(request => request.path === CSV_ENDPOINT && request.method === 'POST').length;
const id = value => page.getByTestId(value);
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
async function selectPeriod(from, to) {
  await id('report-from').selectOption(from); await id('report-to').selectOption(to);
}
async function generate({ keyboard = false, status = 200 } = {}) {
  const reply = page.waitForResponse(response => response.url().endsWith(JSON_ENDPOINT) && response.request().method() === 'POST');
  if (keyboard) { await id('report-generate').focus(); await page.keyboard.press('Enter'); } else await id('report-generate').click();
  const response = await reply;
  const sent = response.request().postDataJSON();
  assert.deepEqual(Object.keys(sent).sort(), ['fromMonth', 'language', 'toMonth']);
  for (const value of Object.values(sent)) assert.equal(typeof value, 'string');
  assert.equal(response.status(), status, await response.text());
  const body = await response.json();
  if (status === 200) await id('report-view').waitFor();
  return { sent, body };
}
const text = value => id(value).innerText();
const digits = value => value.replaceAll(',', '');
async function shows(testId, expected) { assert.ok(digits(await text(testId)).includes(expected), `${testId} must show ${expected}, got ${await text(testId)}`); }
async function capture(name) {
  await page.evaluate(async () => {
    await document.fonts?.ready;
    await Promise.all(document.getAnimations({ subtree: true }).map(animation => animation.finished.catch(() => {})));
    await new Promise(resolveFrame => requestAnimationFrame(() => requestAnimationFrame(() => { window.scrollTo({ top: 0, left: 0, behavior: 'instant' }); resolveFrame(); })));
  });
  await page.screenshot({ path: resolve(out, name), fullPage: true });
}
const noOverflow = () => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2);
function parseCsv(bytes) {
  assert.deepEqual([...bytes.slice(0, 3)], [0xef, 0xbb, 0xbf], 'UTF-8 BOM');
  const raw = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes.slice(3));
  const rows = []; let row = [], cell = '', quoted = false;
  for (let i = 0; i < raw.length; i++) {
    const c = raw[i];
    if (quoted) { if (c === '"' && raw[i + 1] === '"') { cell += '"'; i++; } else if (c === '"') quoted = false; else cell += c; }
    else if (c === '"') quoted = true; else if (c === ',') { row.push(cell); cell = ''; }
    else if (c === '\r' && raw[i + 1] === '\n') { row.push(cell); cell = ''; rows.push(row); row = []; i++; } else cell += c;
  }
  assert.ok(!quoted && !row.length && !cell.length); return rows;
}
const csvValue = (rows, section, period, key) => { const found = rows.filter(r => r[0] === section && r[1] === period && r[2] === key); assert.equal(found.length, 1, `${section}/${period}/${key}`); return found[0]; };
async function download(click) {
  const waiting = page.waitForEvent('download'); await click();
  const file = await waiting; const path = resolve(out, `download-${Date.now()}.csv`); await file.saveAs(path);
  return { name: file.suggestedFilename(), bytes: new Uint8Array(readFileSync(path)) };
}
const seed = (month, extra) => ({ month, financingType: 'none', ...extra });
try {
  await api(context.request, '/api/auth/register', { method: 'POST', status: 201, data: { email, password, fullName: 'Synthetic Reports Owner' } });
  await api(context.request, '/api/auth/login', { method: 'POST', data: { email, password } });
  const business = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: { businessName: 'F12B Reports Studio', businessType: 'services', languagePreference: 'en', whatsappOptIn: false } });
  await page.goto(`${base}/reports`);
  await id('report-generate').waitFor();
  assert.equal(await id('report-view').count(), 0);
  assert.equal(await id('report-export').isDisabled(), true); assert.equal(await id('report-print').isDisabled(), true);
  assert.match(await text('report-status'), /Choose a period/);
  assert.deepEqual(await api(context.request, '/api/records/monthly'), []);
  assert.equal((await api(context.request, '/api/dashboard')).score, null);
  mark('Authorized Reports route opens with a period chooser, no invented figures, and Export/Print unavailable until a report exists');

  await selectPeriod('2026-01', '2026-03');
  let result = await generate({ keyboard: true });
  assert.equal(result.body.completeness.recordedMonths, 0); assert.equal(result.body.periodFlows.revenue.total, null);
  await id('report-nodata').waitFor();
  assert.equal(await text('tile-revenue'), 'Not recorded'); assert.equal(await text('tile-health'), 'Not recorded');
  assert.equal(await id('tile-revenue').getAttribute('data-null'), 'true');
  assert.doesNotMatch(await id('report-view').innerText(), /PKR\s*0(\.00)?\b/);
  assert.equal(await id('flows-table').count(), 0);
  await id('limitation-NO_RECORDS_IN_PERIOD').waitFor();
  mark('Empty period shows explicit no-data, Not recorded (never PKR 0), unknown health and the empty-period limitation');

  for (const [month, data] of [
    ['2026-01', { cashInflow: 900, cashOutflow: 700.3, revenue: 1000.1, operatingExpenses: 100.1, cashBalanceEom: 5000, cogs: null, receivablesOutstanding: 50, payablesOutstanding: 20, inventoryValue: 30, loanOutstanding: 0, interestExpense: 1.25 }],
    ['2026-03', { cashInflow: 1500, cashOutflow: 1600.05, revenue: 2000.2, operatingExpenses: 100.1, cashBalanceEom: 4500, cogs: 400, receivablesOutstanding: null, payablesOutstanding: null, inventoryValue: 0, loanOutstanding: 1000, interestExpense: null }],
  ]) await api(context.request, '/api/records/monthly', { method: 'POST', status: 201, data: seed(month, data) });
  const recordsBefore = await api(context.request, '/api/records/monthly');
  const dashboardBefore = await api(context.request, '/api/dashboard');
  assert.equal(dashboardBefore.score.methodologyVersion, 'health-score-v1');
  const baseline = requests.length;

  const before = reportRequests();
  await id('report-generate').click(); // same selection, now with real records
  await id('report-view').waitFor();
  assert.equal(reportRequests(), before + 1);
  result = { body: await (await context.request.fetch(`${base}${JSON_ENDPOINT}`, { method: 'POST', headers: await csrfHeaders(), data: { fromMonth: '2026-01', toMonth: '2026-03', language: 'en' } })).json() };
  assert.equal(result.body.business.name, 'F12B Reports Studio');
  assert.equal(JSON.stringify(result.body).includes(business.businessId), false);
  await shows('tile-revenue', 'PKR 3000.30'); await shows('tile-net-cash', 'PKR 99.65'); await shows('tile-cash-balance', 'PKR 4500.00');
  assert.match(await text('report-summary'), /As of 2026-03/);
  await shows('flow-revenue-total', '3000.30'); await shows('flow-cashInflow-total', '2400.00'); await shows('flow-cashOutflow-total', '2300.35');
  await shows('flow-netCashMovement-total', '99.65'); await shows('flow-operatingExpenses-total', '200.20'); await shows('flow-cogs-total', '400.00'); await shows('flow-interestExpense-total', '1.25');
  assert.match(await text('flow-cogs-status'), /Partial/); assert.match(await text('flow-cogs'), /Recorded in 1 of 2 months/);
  assert.match(await text('flow-revenue-status'), /Complete/);
  await shows('balance-cashBalanceEom', '4500.00'); await shows('balance-inventoryValue', 'PKR 0.00'); await shows('balance-loanOutstanding', '1000.00');
  assert.equal(await text('balance-receivablesOutstanding'), 'Not recorded'); assert.equal(await text('balance-payablesOutstanding'), 'Not recorded');
  assert.equal(await id('balance-receivablesOutstanding').getAttribute('data-null'), 'true');
  assert.equal(await id('balance-inventoryValue').getAttribute('data-null'), 'false');
  assert.match(await text('balances-as-of'), /2026-03/);
  const view = await id('report-view').innerText();
  assert.match(view, /Latest recorded balance/); assert.match(view, /not period totals/); assert.match(view, /not an average/);
  assert.doesNotMatch(digits(view), /9500\.00/);
  assert.equal(await text('flows-2026-01-cogs'), 'Not recorded'); await shows('flows-2026-03-cogs', '400.00');
  assert.equal(await text('balances-2026-03-receivablesOutstanding'), 'Not recorded'); await shows('balances-2026-03-inventoryValue', 'PKR 0.00');
  assert.equal(await id('flows-table').locator('tbody tr').count(), 2);
  assert.equal(await id('flows-card-2026-01').isVisible(), false);
  await shows('flows-2026-01-netCashMovement', 'PKR 199.70'); await shows('flows-2026-03-netCashMovement', '- PKR 100.05');
  assert.match(await text('completeness-months'), /2 of 3 months/);
  assert.ok((await id('completeness-missing').innerText()).includes('2026-02'));
  for (const code of ['MONTHS_MISSING', 'COGS_PARTIAL', 'INTEREST_EXPENSE_PARTIAL', 'BALANCE_NOT_RECORDED_IN_LATEST_MONTH']) await id(`limitation-${code}`).waitFor();
  assert.equal(await text('health-score'), Number(dashboardBefore.score.compositeScore).toFixed(2));
  assert.equal(await text('health-month'), dashboardBefore.score.month); assert.equal(await text('health-methodology'), 'health-score-v1');
  assert.match(await id('report-health').innerText(), /not an average of months/);
  await capture('report-desktop-en.png');
  mark('Generated report separates exact period flows, latest balances (null stays Not recorded, zero stays 0.00) and the latest stored health score with partial/missing-data limitations');

  let reportCalls = reportRequests();
  await id('report-to').selectOption('2026-04');
  assert.equal(await id('report-view').count(), 0); assert.equal(await id('report-export').isDisabled(), true);
  assert.match(await text('report-status'), /period changed/i);
  assert.equal(reportRequests(), reportCalls);
  await id('report-to').selectOption('2026-03');
  await id('report-from').focus(); await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), 'report-to');
  await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), 'report-generate');
  await page.keyboard.press('Enter'); await id('report-view').waitFor();
  assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('data-testid')), 'report-view');
  mark('Changing the period discards the stale report and disables export; keyboard Tab/Enter reaches and runs Generate and focuses the results');

  reportCalls = reportRequests();
  await selectPeriod('2026-05', '2026-04'); await id('report-generate').click();
  await id('report-period-issue').waitFor(); assert.match(await text('report-period-issue'), /first month must be/);
  await selectPeriod('2023-12', '2026-10'); await id('report-generate').click();
  assert.match(await text('report-period-issue'), /at most 24 months/);
  assert.equal(reportRequests(), reportCalls);
  await selectPeriod('2026-01', '2026-03');
  mark('Reversed and over-24-month periods are rejected in the browser without sending a request');

  await page.route(`**${JSON_ENDPOINT}`, async route => {
    const body = route.request().postDataJSON(); body.language = 'fr'; await route.continue({ postData: JSON.stringify(body) });
  }, { times: 1 });
  await generate({ status: 400 });
  await id('report-error').waitFor(); assert.equal(await id('report-view').count(), 0);
  result = await generate(); assert.equal(result.body.completeness.recordedMonths, 2);
  mark('A real backend validation error is visible and a normal retry succeeds');

  await page.route(`**${JSON_ENDPOINT}`, route => route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ status: 403, error: 'Forbidden', message: 'simulated' }) }), { times: 1 });
  await id('report-generate').click(); await id('report-error').waitFor();
  assert.match(await text('report-error'), /does not allow viewing financial reports/); assert.equal(await id('report-view').count(), 0);
  await id('report-generate').click(); await id('report-view').waitFor();
  mark('A simulated authorization (403) response shows the localized access message and no report; this one response is explicitly simulated');

  let releaseReply, replyReady;
  const release = new Promise(resolveReply => { releaseReply = resolveReply; }), ready = new Promise(resolveReady => { replyReady = resolveReady; });
  let finishRoute; const routeFinished = new Promise(resolveFinished => { finishRoute = resolveFinished; });
  await page.route(`**${JSON_ENDPOINT}`, async route => {
    const response = await route.fetch(); replyReady(); await release;
    try { await route.fulfill({ response }); } catch (error) { if (!/closed|cancel|abort|Invalid InterceptionId/i.test(error.message)) throw error; } finally { finishRoute(); }
  }, { times: 1 });
  await id('report-generate').click(); await ready;
  assert.equal(await id('report-generate').isDisabled(), true); assert.match(await text('report-status'), /Generating the report/);
  assert.equal(await id('report-view').count(), 0);
  await id('report-from').selectOption('2026-02');
  releaseReply(); await routeFinished;
  await page.evaluate(() => new Promise(resolveFrame => requestAnimationFrame(() => requestAnimationFrame(resolveFrame))));
  assert.equal(await id('report-view').count(), 0);
  await id('report-from').selectOption('2026-01'); result = await generate();
  mark('Loading state disables duplicate submit and an older delayed reply never overwrites a newer period selection');

  const exported = await download(() => id('report-export').click());
  assert.equal(exported.name, 'finsight-business-financial-summary_2026-01_2026-03.csv');
  let rows = parseCsv(exported.bytes);
  assert.equal(csvRequests(), 1);
  assert.deepEqual(rows[0], ['section', 'period', 'key', 'label', 'value', 'status', 'detail']);
  const current = result.body;
  assert.equal(csvValue(rows, 'periodFlow', '2026-01..2026-03', 'revenue')[4], current.periodFlows.revenue.total);
  assert.equal(csvValue(rows, 'periodFlow', '2026-01..2026-03', 'netCashMovement')[4], current.periodFlows.netCashMovement.total);
  assert.equal(csvValue(rows, 'periodFlow', '2026-01..2026-03', 'cogs')[5], 'PARTIAL');
  assert.equal(csvValue(rows, 'latestBalance', '2026-03', 'cashBalanceEom')[4], current.latestBalances.cashBalanceEom);
  assert.deepEqual([csvValue(rows, 'latestBalance', '2026-03', 'receivablesOutstanding')[4], csvValue(rows, 'latestBalance', '2026-03', 'receivablesOutstanding')[5]], ['', 'NOT_RECORDED']);
  assert.equal(csvValue(rows, 'latestBalance', '2026-03', 'inventoryValue')[4], '0.00');
  assert.equal(csvValue(rows, 'latestHealth', current.latestHealth.month, 'compositeScore')[4], current.latestHealth.compositeScore);
  for (const row of current.monthlyRows) for (const key of ['revenue', 'cogs', 'cashInflow', 'cashBalanceEom', 'receivablesOutstanding']) assert.equal(csvValue(rows, 'monthly', row.month, key)[4], row[key] ?? '');
  assert.doesNotMatch(new TextDecoder().decode(exported.bytes), new RegExp(business.businessId));
  assert.match(await text('report-status'), /CSV downloaded/);
  mark('Export CSV downloads the exact canonical figures with a safe file name, UTF-8 BOM, empty unknowns and no internal identifiers');

  await page.emulateMedia({ media: 'print' });
  await page.setViewportSize({ width: 794, height: 1123 });
  for (const hidden of ['report-controls', 'report-status']) assert.equal(await id(hidden).isVisible(), false, hidden);
  assert.equal(await page.locator('aside').first().isVisible(), false); assert.equal(await page.locator('header').first().isVisible(), false);
  assert.equal(await page.locator('nav').first().isVisible(), false);
  assert.equal(await id('report-view').isVisible(), true); assert.equal(await id('report-header').isVisible(), true);
  assert.equal(await id('flows-table').isVisible(), true); assert.equal(await id('balances-table').isVisible(), true);
  assert.equal(await id('flows-card-2026-01').isVisible(), false);
  for (const marker of ['Business Financial Summary', 'F12B Reports Studio', '2026-01', 'Generated', 'Period flows (totals)', 'Latest recorded balance', 'not an average'])
    assert.ok((await page.locator('main').innerText()).includes(marker), marker);
  assert.ok(await noOverflow());
  const clipped = await page.evaluate(() => [...document.querySelectorAll('[data-report-print] table')].filter(table => table.scrollWidth > table.parentElement.clientWidth + 2).length);
  assert.equal(clipped, 0, 'no printed table may be clipped');
  const pdfPath = resolve(out, 'business-financial-summary-en.pdf');
  await page.pdf({ path: pdfPath, format: 'A4', printBackground: true });
  assert.equal(readFileSync(pdfPath).subarray(0, 4).toString(), '%PDF'); assert.ok(statSync(pdfPath).size > 5000);
  await page.emulateMedia({ media: 'screen' }); await page.setViewportSize({ width: 1440, height: 1000 });
  assert.equal(await id('report-controls').isVisible(), true);
  mark('Print layout hides navigation, controls and status, keeps title/business/period/generated time/semantics, shows unclipped tables and produces a PDF');

  await page.setViewportSize({ width: 390, height: 844 });
  assert.ok(await noOverflow()); assert.equal(await id('flows-table').isVisible(), false); assert.equal(await id('flows-card-2026-03').isVisible(), true);
  await shows('flows-card-2026-03-revenue', '2000.20'); assert.equal(await text('flows-card-2026-01-cogs'), 'Not recorded');
  await capture('report-mobile-en.png');
  const languageReply = page.waitForResponse(response => response.url().endsWith('/api/profile/language') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'اردو', exact: true }).first().click();
  assert.equal((await languageReply).status(), 200);
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  assert.match(await id('report-view').innerText(), /[\u0600-\u06ff]/); assert.match(await text('report-semantics'), /اوسط نہیں/);
  assert.doesNotMatch(await text('report-status'), /CSV downloaded/);
  assert.equal(await text('flows-card-2026-01-cogs'), 'درج نہیں'); assert.equal(await text('balance-receivablesOutstanding'), 'درج نہیں');
  assert.match(await text('tile-revenue'), /3,000\.30 روپے/); assert.match(await id('report-balances').innerText(), /تازہ ترین ریکارڈ شدہ بیلنس/);
  assert.ok(await noOverflow()); await capture('report-mobile-ur.png');
  const urduExport = await download(() => id('report-export').click());
  rows = parseCsv(urduExport.bytes);
  assert.equal(csvValue(rows, 'report', '', 'language')[4], 'ur'); assert.equal(csvValue(rows, 'periodFlow', '2026-01..2026-03', 'revenue')[3], 'آمدنی');
  assert.equal(csvValue(rows, 'periodFlow', '2026-01..2026-03', 'revenue')[4], current.periodFlows.revenue.total);
  assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('[data-testid="tile-revenue"]')).direction), 'rtl');
  await page.setViewportSize({ width: 1440, height: 1000 }); assert.ok(await noOverflow()); await shows('flows-2026-03-revenue', '2000.20');
  assert.match(await id('flows-table').locator('thead').innerText(), /آمدنی/);
  await capture('report-desktop-ur.png');
  await page.emulateMedia({ media: 'print' }); assert.ok(await noOverflow()); assert.equal(await id('report-controls').isVisible(), false);
  assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('[data-report-print]')).direction), 'rtl');
  await page.pdf({ path: resolve(out, 'business-financial-summary-ur.pdf'), format: 'A4', printBackground: true });
  await page.emulateMedia({ media: 'screen' });
  mark('Urdu switch keeps the report, localizes labels/null copy, preserves RTL on mobile/desktop/print without overflow and exports Urdu CSV with identical figures');

  const viewer = await browser.newContext();
  await api(viewer.request, '/api/auth/register', { method: 'POST', status: 201, data: { email: viewerEmail, password, fullName: 'Synthetic Reports Viewer' } });
  const invite = await api(context.request, '/api/memberships', { method: 'POST', status: 201, data: { email: viewerEmail, role: 'VIEWER' } });
  await api(viewer.request, '/api/auth/login', { method: 'POST', data: { email: viewerEmail, password } });
  await api(viewer.request, `/api/invitations/${invite.id}/accept`, { method: 'POST', status: 204 });
  await api(viewer.request, '/api/businesses/active', { method: 'POST', data: { businessId: business.businessId } });
  const viewerPayload = { fromMonth: '2026-01', toMonth: '2026-03', language: 'en' };
  const viewerReport = await api(viewer.request, JSON_ENDPOINT, { method: 'POST', data: viewerPayload });
  assert.equal(viewerReport.periodFlows.revenue.total, current.periodFlows.revenue.total);
  await api(viewer.request, JSON_ENDPOINT, { method: 'POST', data: { ...viewerPayload, businessId: business.businessId }, status: 400 });
  await api(viewer.request, JSON_ENDPOINT, { method: 'POST', data: viewerPayload, csrf: false, status: 403 });
  await api(context.request, `/api/memberships/${invite.id}`, { method: 'PATCH', data: { status: 'SUSPENDED' } });
  await api(viewer.request, JSON_ENDPOINT, { method: 'POST', data: viewerPayload, status: 409 });
  await viewer.close();
  const anonymous = await browser.newContext();
  await api(anonymous.request, JSON_ENDPOINT, { method: 'POST', data: viewerPayload, status: 401 });
  await anonymous.close();
  mark('Real API: VIEWER may read the same report, tenant authority/CSRF/authentication are enforced and a suspended membership loses access');

  await page.getByRole('button', { name: 'English', exact: true }).first().click();
  await page.waitForFunction(() => document.documentElement.dir === 'ltr');
  // The toggle stays busy until session revalidation finishes; focus during it would be ignored.
  await page.getByRole('button', { name: 'اردو', exact: true }).first().waitFor();
  assert.equal(await id('report-view').count(), 1);
  const second = await api(context.request, '/api/businesses', { method: 'POST', status: 201, data: { businessName: 'F12B Second Business', businessType: 'retail', languagePreference: 'en', whatsappOptIn: false } });
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await page.waitForFunction(() => !document.querySelector('[data-testid="report-view"]'));
  assert.equal(await id('report-export').isDisabled(), true);
  await selectPeriod('2026-01', '2026-03'); result = await generate();
  assert.equal(result.body.business.name, 'F12B Second Business'); assert.equal(result.body.completeness.recordedMonths, 0);
  assert.equal(await text('report-business'), 'F12B Second Business'); assert.doesNotMatch(await id('report-view').innerText(), /3,?000\.30/);
  assert.equal((await api(context.request, JSON_ENDPOINT, { method: 'POST', data: viewerPayload })).business.name, 'F12B Second Business');
  mark('A server business switch discards the previous business report and the next report belongs only to the newly active business');

  assert.deepEqual((await api(context.request, '/api/records/monthly')), []);
  await api(context.request, '/api/businesses/active', { method: 'POST', data: { businessId: business.businessId } });
  assert.deepEqual(await api(context.request, '/api/records/monthly'), recordsBefore);
  assert.deepEqual((await api(context.request, '/api/dashboard')).score, dashboardBefore.score);
  const prohibited = requests.slice(baseline).filter(request => request.method !== 'GET' && /^\/api\/(records|score|dashboard|documents|upload|whatsapp|recommendations|insights|estimates|workspace|configuration)/.test(request.path));
  assert.deepEqual(prohibited, []);
  mark('Reports, exports and business switching leave MonthlyRecord rows and health-score-v1 unchanged and issue no financial/document/WhatsApp/recommendation writes');

  await api(context.request, '/api/businesses/active', { method: 'POST', data: { businessId: business.businessId } });
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await selectPeriod('2026-01', '2026-03'); await generate();
  await api(context.request, '/api/auth/logout', { method: 'POST', status: 204 });
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await page.locator('input[type="email"]').waitFor();
  assert.equal(await id('report-view').count(), 0); assert.equal(await id('report-generate').count(), 0);
  mark('Real session expiry removes the private report form and results');

  assert.deepEqual(errors, []); assert.deepEqual(externalRequests, []);
  writeFileSync(resolve(out, 'results.json'), JSON.stringify({
    scope: 'Production Next + real Spring/session/CSRF + disposable PostgreSQL; synthetic accounts/data; no paid provider; one authorization-error response is explicitly simulated; held or malformed requests still use actual backend responses',
    checks, errors, externalRequests
  }, null, 2));
  console.log(`RESULT ${checks.length} checks passed`);
} catch (error) {
  await page.screenshot({ path: resolve(out, 'failure.png'), fullPage: true }).catch(() => {});
  writeFileSync(resolve(out, 'failure.txt'), `${error.stack}\n${await page.locator('body').innerText().catch(() => '')}`);
  throw error;
} finally { await browser.close(); }

async function csrfHeaders() {
  const response = await context.request.get(`${base}/api/auth/csrf`); const token = await response.json();
  return { [token.headerName]: token.token };
}
