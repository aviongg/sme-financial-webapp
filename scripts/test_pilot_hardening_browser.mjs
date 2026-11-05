// Opt-in production Next + actual Spring/session/CSRF + disposable PostgreSQL.
// Run through PilotHardeningBrowserAcceptance; only the OCR provider response is simulated.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.FINSIGHT_BROWSER_BASE_URL || 'http://127.0.0.1:3100';
assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/);
assert.ok(process.env.BROWSER_ARTIFACT_DIR, 'Harness must provide an artifact directory outside the repository tmp/');
const out = resolve(process.env.BROWSER_ARTIFACT_DIR);
mkdirSync(out, { recursive: true });
const checks = [], errors = [], externalRequests = [], requests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Synthetic-Pilot-2026!';
const email = `pilot-owner-${Date.now()}@example.test`;
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
function observe(page) {
  page.setDefaultTimeout(20000);
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(message.text())) errors.push(message.text());
  });
}
context.on('page', observe);
context.on('request', request => {
  const url = new URL(request.url());
  requests.push({ path: url.pathname, method: request.method(), data: request.postData() });
  if (/^https?:/.test(url.protocol) && url.hostname !== '127.0.0.1') externalRequests.push(url.origin);
});
const page = await context.newPage();
const documents = await context.newPage();
const endpoint = '/api/records/monthly';
const count = path => requests.filter(request => request.path === path && request.method === 'POST').length;
async function api(path, { method = 'GET', data, multipart, status = 200 } = {}) {
  const headers = {};
  if (method !== 'GET') {
    const response = await context.request.get(`${base}/api/auth/csrf`);
    assert.equal(response.status(), 200);
    const token = await response.json(); headers[token.headerName] = token.token;
  }
  const response = await context.request.fetch(`${base}${path}`, {
    method, headers, ...(data === undefined ? {} : { data }), ...(multipart ? { multipart } : {})
  });
  assert.equal(response.status(), status, `${method} ${path}: ${await response.text()}`);
  return response.status() === 204 ? null : response.json();
}
const record = month => ({ month, expectedVersion: null, cashInflow: 1000, cashOutflow: 300, revenue: 1000,
  operatingExpenses: 200, cashBalanceEom: 700, cogs: null, receivablesOutstanding: 0,
  payablesOutstanding: null, inventoryValue: null, loanOutstanding: 0, interestExpense: 0, financingType: 'none' });
const query = month => api(`${endpoint}/query`, { method: 'POST', data: { month } });
async function edit(month = '2026-09') {
  await page.goto(`${base}/records/${month}`);
  await page.getByTestId('monthly-save').waitFor();
}
async function save(status = 201) {
  const reply = page.waitForResponse(response => response.url().endsWith(endpoint) && response.request().method() === 'POST');
  await page.getByTestId('monthly-save').click();
  const response = await reply;
  assert.equal(response.status(), status, await response.text());
  const sent = response.request().postDataJSON();
  assert.ok(Object.hasOwn(sent, 'expectedVersion'));
  assert.equal(Object.hasOwn(sent, 'businessId'), false); assert.equal(Object.hasOwn(sent, 'userId'), false);
  const body = await response.json();
  if (status === 201) await page.waitForURL(`${base}/records`);
  else await page.getByTestId('monthly-conflict').waitFor();
  return { sent, body };
}
async function screenshot(target, name) {
  await target.evaluate(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }));
  await target.screenshot({ path: resolve(out, name), fullPage: true });
}
async function upload(name) {
  const uploaded = await api('/api/documents/upload', { method: 'POST', status: 201, multipart: {
    file: { name, mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9V8AAAAASUVORK5CYII=', 'base64') }
  } });
  for (let attempt = 0; attempt < 100; attempt++) {
    const doc = await api(`/api/documents/${uploaded.id}`);
    if (['extracted', 'needs_review'].includes(doc.processingStatus)) return doc;
    assert.notEqual(doc.processingStatus, 'failed', doc.failureReason);
    await new Promise(resolveWait => setTimeout(resolveWait, 100));
  }
  throw new Error('Synthetic OCR did not complete');
}
async function review(doc) {
  await documents.goto(`${base}/upload/${doc.id}`);
  await documents.getByTestId('document-target-month').fill('2026-09');
  await documents.getByTestId('document-amount').fill('200');
  await documents.getByTestId('document-date').fill('2026-09-15');
  await documents.getByTestId('document-party').fill('Synthetic pilot vendor');
  await documents.getByTestId('document-classification').selectOption('revenue');
  await documents.getByTestId('document-cash-impact').selectOption('cash_inflow');
}
async function confirm(doc, { duplicate = false, status = 200 } = {}) {
  if (!duplicate) {
    await documents.getByTestId('review-document').click();
    await documents.getByTestId('confirm-document').waitFor();
  }
  const target = documents.getByTestId(duplicate ? 'confirm-duplicate-source' : 'confirm-document');
  const reply = documents.waitForResponse(response => response.url().endsWith(`/api/documents/${doc.id}/confirm`) && response.request().method() === 'POST');
  await target.focus(); await documents.keyboard.press('Enter');
  const response = await reply;
  assert.equal(response.status(), status, await response.text());
  const sent = response.request().postDataJSON();
  assert.deepEqual(Object.keys(sent).sort(), ['targetMonth', 'confirmedAmount', 'confirmedDate', 'confirmedParty',
    'targetClassification', 'cashFlowImpact', 'initialCashBalanceEom', ...(duplicate ? ['allowPotentialDuplicate'] : [])].sort());
  if (duplicate) assert.equal(sent.allowPotentialDuplicate, true);
  if (status === 409) {
    assert.equal((await response.json()).error, 'potential_duplicate_source');
    await documents.getByTestId('duplicate-source-warning').waitFor();
  } else await documents.getByTestId('review-document').waitFor({ state: 'detached' });
  return sent;
}
async function language(target, locale) {
  const reply = target.waitForResponse(response => response.url().endsWith('/api/profile/language') && response.request().method() === 'PATCH');
  await target.getByRole('button', { name: locale === 'ur' ? 'اردو' : 'English', exact: true }).first().click();
  assert.equal((await reply).status(), 200);
  await target.waitForFunction(dir => document.documentElement.dir === dir, locale === 'ur' ? 'rtl' : 'ltr');
}
try {
  await api('/api/auth/register', { method: 'POST', status: 201, data: { email, password, fullName: 'Synthetic Pilot Owner' } });
  await api('/api/auth/login', { method: 'POST', data: { email, password } });
  const business = await api('/api/businesses', { method: 'POST', status: 201, data: { businessName: 'Pilot Integrity Studio', businessType: 'trade', languagePreference: 'en', whatsappOptIn: false } });
  const initial = await api(endpoint, { method: 'POST', status: 201, data: record('2026-09') });
  assert.equal(initial.cogs, null); assert.equal(initial.receivablesOutstanding, 0);
  assert.equal(typeof initial.version, 'number');
  mark('Real synthetic owner/business creates a versioned month with null COGS and known zero preserved');

  await edit();
  await page.locator('#field-revenue').fill('1100');
  const first = await upload('pilot-first.png');
  await review(first); await confirm(first);
  const contributed = await query('2026-09');
  assert.equal(contributed.revenue, 1200); assert.equal(contributed.cashInflow, 1200);
  assert.equal(contributed.cashBalanceEom, 700); assert.equal(contributed.cogs, null);
  assert.ok(contributed.version > initial.version);
  mark('Upload/extraction/review/keyboard confirmation adds the real source once without changing point-in-time balance or unknown COGS');

  let before = count(endpoint);
  let saved = await save(409);
  assert.equal(saved.body.error, 'stale_monthly_record'); assert.equal(saved.sent.expectedVersion, initial.version);
  assert.equal(count(endpoint), before + 1);
  assert.equal(await page.getByTestId('monthly-save').isDisabled(), true);
  assert.equal((await page.locator('#field-revenue').inputValue()).replaceAll(',', ''), '1100');
  assert.match(await page.getByTestId('monthly-conflict').innerText(), /Reload the latest record/);
  assert.deepEqual(await query('2026-09'), contributed);
  await screenshot(page, 'stale-month-desktop.png');
  await page.setViewportSize({ width: 390, height: 844 });
  await language(page, 'ur');
  assert.equal(await page.getByTestId('monthly-save').isDisabled(), true);
  assert.equal((await page.locator('#field-revenue').inputValue()).replaceAll(',', ''), '1100');
  assert.match(await page.getByTestId('monthly-conflict').innerText(), /تازہ ریکارڈ/);
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2));
  assert.equal(count(endpoint), before + 1);
  await screenshot(page, 'stale-month-urdu-mobile.png');
  mark('Stale edit sends its original version once, preserves the draft and blocks saving; Urdu switch keeps the conflict and never replays');

  await page.getByTestId('monthly-reload').focus(); await page.keyboard.press('Enter');
  await page.waitForFunction(() => document.querySelector('#field-revenue')?.value.replaceAll(',', '') === '1200');
  assert.equal(await page.getByTestId('monthly-save').isDisabled(), false);
  await page.locator('#field-revenue').fill('1300'); saved = await save();
  assert.equal(saved.sent.expectedVersion, contributed.version); assert.equal(saved.body.revenue, 1300);
  assert.equal(saved.body.cogs, null);
  mark('Explicit reload discards only the stale draft, loads the current version and a reviewed save succeeds');

  await edit(); const beforeFloor = await query('2026-09');
  await page.locator('#field-revenue').fill('100'); before = count(endpoint);
  saved = await save(409);
  assert.equal(saved.body.error, 'confirmed_source_conflict');
  assert.equal(await page.locator('#field-revenue').getAttribute('aria-invalid'), 'true');
  assert.match(await page.getByTestId('monthly-conflict').innerText(), /تصدیق شدہ/);
  assert.equal(count(endpoint), before + 1); assert.deepEqual(await query('2026-09'), beforeFloor);
  await page.locator('#field-revenue').fill('1400'); saved = await save();
  assert.equal(saved.sent.expectedVersion, beforeFloor.version); assert.equal(saved.body.revenue, 1400);
  mark('Confirmed-source floor conflict is translated, marks the affected total and changes nothing; an explicit corrected save succeeds');

  await edit(); before = count(endpoint);
  await page.locator('#field-revenue').fill('1.001'); await page.getByTestId('monthly-save').click();
  assert.equal(await page.locator('#field-revenue').getAttribute('aria-invalid'), 'true');
  await page.locator('#field-revenue').fill('1000000000000'); await page.getByTestId('monthly-save').click();
  assert.equal(await page.locator('#field-revenue').getAttribute('aria-invalid'), 'true'); assert.equal(count(endpoint), before);
  await page.locator('#field-revenue').fill('1400');
  mark('Excess scale and NUMERIC(14,2) overflow are rejected in the monthly form without a financial POST');

  // The document page was mounted in English before the other tab changed the profile language.
  await documents.reload(); await documents.getByRole('button', { name: 'English', exact: true }).first().waitFor();
  await language(documents, 'en');
  const second = await upload('pilot-second.png'); await review(second);
  const confirmPath = `/api/documents/${second.id}/confirm`;
  const duplicateBaseline = await query('2026-09'); before = count(confirmPath);
  const duplicateRequest = await confirm(second, { status: 409 });
  assert.match(await documents.getByTestId('duplicate-source-warning').innerText(), /may count the same receipt or expense twice/);
  assert.equal(count(confirmPath), before + 1); assert.deepEqual(await query('2026-09'), duplicateBaseline);
  assert.equal((await api(`/api/documents/${second.id}`)).processingStatus, 'extracted');
  await screenshot(documents, 'duplicate-source-desktop.png');
  await documents.getByTestId('document-party').fill('Edited draft');
  assert.equal(await documents.getByTestId('confirm-duplicate-source').count(), 0);
  await documents.getByTestId('document-party').fill('Synthetic pilot vendor');
  assert.deepEqual(await confirm(second, { status: 409 }), duplicateRequest);
  const override = await confirm(second, { duplicate: true });
  assert.deepEqual(override, { ...duplicateRequest, allowPotentialDuplicate: true });
  const afterOverride = await query('2026-09');
  assert.equal(afterOverride.revenue, duplicateBaseline.revenue + 200);
  assert.equal(afterOverride.cashInflow, duplicateBaseline.cashInflow + 200);
  assert.equal(count(confirmPath), before + 3);
  await documents.reload(); await documents.getByText('Confirmed in 2026-09. This document cannot be added again.', { exact: true }).waitFor();
  assert.equal(await documents.getByTestId('review-document').count(), 0);
  assert.deepEqual(await query('2026-09'), afterOverride);
  mark('Duplicate source never auto-replays; edits clear acknowledgement, keyboard Confirm anyway sends only the reviewed payload plus true and applies once');

  const third = await upload('pilot-third.png');
  await documents.setViewportSize({ width: 390, height: 844 });
  await language(documents, 'ur'); await review(third);
  before = count(`/api/documents/${third.id}/confirm`);
  await confirm(third, { status: 409 });
  assert.match(await documents.getByTestId('duplicate-source-warning').innerText(), /ایک رسید یا خرچہ دو بار/);
  assert.equal(await documents.getByTestId('confirm-duplicate-source').innerText(), 'پھر بھی تصدیق کریں');
  assert.ok(await documents.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2));
  await screenshot(documents, 'duplicate-source-urdu-mobile.png');
  assert.equal(count(`/api/documents/${third.id}/confirm`), before + 1);
  await confirm(third, { duplicate: true });
  assert.equal((await query('2026-09')).revenue, afterOverride.revenue + 200);
  mark('Urdu mobile warning explains duplicate counting, fits RTL viewport and requires a deliberate keyboard override');

  // A new month remains absent until the user saves. No fallback data or default COGS is introduced.
  await page.goto(`${base}/records/new`); await page.getByTestId('monthly-save').waitFor();
  await page.locator('main form select').first().selectOption('2026-08');
  // Keep enough cash-flow evidence for the unchanged health-score-v1 save prerequisite.
  for (const [field, value] of Object.entries({ cashInflow: '100', cashOutflow: '50', revenue: '0', operatingExpenses: '20', cashBalanceEom: '0' }))
    await page.locator(`#field-${field}`).fill(value);
  saved = await save(); assert.equal(saved.sent.expectedVersion, null); assert.equal(saved.body.cogs, null);
  assert.equal(saved.body.revenue, 0);
  mark('Normal new-month UI sends an absent assertion and preserves optional null versus required zero');

  // Interpose a genuine competing create after the client precheck; never fabricate an API response.
  await page.goto(`${base}/records/new`); await page.getByTestId('monthly-save').waitFor();
  await page.locator('main form select').first().selectOption('2026-07');
  for (const field of ['cashInflow', 'cashOutflow', 'revenue', 'operatingExpenses', 'cashBalanceEom']) await page.locator(`#field-${field}`).fill('0');
  await page.route(`**${endpoint}`, async route => {
    if (route.request().method() !== 'POST') return route.continue();
    await api(endpoint, { method: 'POST', status: 201, data: record('2026-07') });
    await route.continue();
  });
  before = count(endpoint); saved = await save(409); await page.unroute(`**${endpoint}`);
  assert.equal(saved.sent.expectedVersion, null); assert.equal(saved.body.error, 'stale_monthly_record');
  assert.equal(count(endpoint), before + 1); assert.equal((await query('2026-07')).revenue, 1000);
  assert.equal(await page.getByTestId('monthly-save').isDisabled(), true);
  mark('A real competing create after the precheck is rejected as stale, never overwrites the new month and requires opening the saved record');

  await edit(); await page.locator('#field-revenue').fill('9999');
  const other = await api('/api/businesses', { method: 'POST', status: 201, data: { businessName: 'Pilot Empty Other Business', businessType: 'services', languagePreference: 'en', whatsappOptIn: false } });
  assert.notEqual(other.businessId, business.businessId);
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await page.getByTestId('monthly-save').waitFor({ state: 'detached' });
  assert.equal(await page.locator('#field-revenue').count(), 0); assert.deepEqual(await api(endpoint), []);
  await page.goto(`${base}/records/new`); await page.getByTestId('monthly-save').waitFor();
  await page.locator('#field-revenue').fill('99');
  await api('/api/auth/logout', { method: 'POST', status: 204 });
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await page.locator('input[type="email"]').waitFor(); assert.equal(await page.getByTestId('monthly-save').count(), 0);
  mark('Real business/session transitions remove old financial drafts and conflict state instead of carrying authority or versions across contexts');

  assert.deepEqual(errors, []); assert.deepEqual(externalRequests, []);
  writeFileSync(resolve(out, 'results.json'), JSON.stringify({
    scope: 'Production Next + actual Spring/session/CSRF + disposable PostgreSQL; only OCR provider simulated; synthetic accounts and files',
    checks, errors, externalRequests
  }, null, 2));
  console.log(`RESULT ${checks.length} checks passed`);
} catch (error) {
  await page.screenshot({ path: resolve(out, 'failure-record.png'), fullPage: true }).catch(() => {});
  await documents.screenshot({ path: resolve(out, 'failure-document.png'), fullPage: true }).catch(() => {});
  writeFileSync(resolve(out, 'failure.txt'), `${error.stack}\nMONTHLY\n${await page.locator('body').innerText().catch(() => '')}\nDOCUMENT\n${await documents.locator('body').innerText().catch(() => '')}`);
  throw error;
} finally { await browser.close(); }
