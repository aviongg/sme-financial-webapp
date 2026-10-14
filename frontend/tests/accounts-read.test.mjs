import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

// Transpile the real modules in a uniquely owned temporary tree, as the MVP tests do.
const directory = mkdtempSync(path.join(tmpdir(), 'finsight-accounts-read-'));
const files = ['lib/api/client', 'lib/utils/decimal-display', 'lib/api/dev-a/account-contracts', 'lib/api/dev-a/accounts', 'components/finance/accounts/account-copy'];
const directories = new Set();
for (const file of files) {
  const target = path.join(directory, file + '.js');
  mkdirSync(path.dirname(target), { recursive: true });
  for (let parent = path.dirname(target); parent !== directory; parent = path.dirname(parent)) directories.add(parent);
  const source = readFileSync(fileURLToPath(new URL('../src/' + file + '.ts', import.meta.url)), 'utf8');
  writeFileSync(target, ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
}
const require = createRequire(import.meta.url);
const client = require(path.join(directory, 'lib/api/client.js'));
const money = require(path.join(directory, 'lib/utils/decimal-display.js'));
const contracts = require(path.join(directory, 'lib/api/dev-a/account-contracts.js'));
const { accountsApi, accountQueryString } = require(path.join(directory, 'lib/api/dev-a/accounts.js'));
const { accountErrorKey, accountEn, accountUr } = require(path.join(directory, 'components/finance/accounts/account-copy.js'));
const id = '11111111-1111-4111-8111-111111111111';
const account = () => ({ id, accountName: 'Fixture bank', accountType: 'BANK', currency: 'PKR', currencyDecimals: 2,
  openingBalance: '100000.00', openingDate: '2026-10-01', currentBalance: '115000.00', balanceStatus: 'AVAILABLE', balanceUnavailableReason: null,
  status: 'ACTIVE', observationAt: '2026-10-14T13:30:00Z', effectiveCutoffDate: '2026-10-14', institutionName: null, accountIdentifier: '****1234',
  createdAt: '2026-10-14T10:00:00Z', updatedAt: '2026-10-14T12:30:00Z' });
const page = () => ({ items: [account()], page: 0, size: 50, totalElements: 1, totalPages: 1 });
let calls;
function backend(handler) {
  mock.method(globalThis, 'fetch', async (url, options) => { calls.push({ url, options }); return handler(url, options); });
}
beforeEach(() => { calls = []; client.invalidatePrivateRequests(); client.allowPrivateRequests(); });
afterEach(() => { mock.restoreAll(); client.invalidatePrivateRequests(); });
after(() => {
  for (const file of files) unlinkSync(path.join(directory, file + '.js'));
  for (const parent of [...directories].sort((a, b) => b.length - a.length)) rmdirSync(parent);
  rmdirSync(directory);
});

test('exact formatting preserves boundaries, negative fractions and currency scales', () => {
  for (const [currency, scale, value, expected] of [
    ['JPY', 0, '0', '0'], ['JPY', 0, '999999999999', '999,999,999,999'],
    ['PKR', 2, '0.00', '0.00'], ['PKR', 2, '-2500.50', '- 2,500.50'],
    ['PKR', 2, '999999999999.99', '999,999,999,999.99'], ['PKR', 2, '-0.25', '- 0.25'],
    ['BHD', 3, '0.000', '0.000'], ['BHD', 3, '1250.375', '1,250.375'],
    ['BHD', 3, '-999999999999.999', '- 999,999,999,999.999'],
  ]) { assert.equal(money.validCurrencyAmount(value, currency, scale), true); assert.equal(money.formatExactDecimal(value, currency, scale), expected); }
});
test('actual money component renders null as bilingual unavailable and isolates exact RTL numbers', () => {
  const source = readFileSync(fileURLToPath(new URL('../src/components/finance/accounts/AccountMoneyValue.tsx', import.meta.url)), 'utf8')
    .replace('@/lib/utils/decimal-display', '../../../lib/utils/decimal-display');
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  const compiledModule = { exports: {} };
  const localRequire = createRequire(path.join(directory, 'components/finance/accounts/AccountMoneyValue.js'));
  new Function('require', 'module', 'exports', compiled)(name => name.startsWith('react') ? require(name) : localRequire(name), compiledModule, compiledModule.exports);
  const { renderToStaticMarkup } = require('react-dom/server');
  const { createElement } = require('react');
  for (const copy of [accountEn, accountUr]) {
    const render = (value, currency = 'PKR', scale = 2) => renderToStaticMarkup(createElement(compiledModule.exports.AccountMoneyValue, { value, currency, scale, copy }));
    assert.equal(render(null), '<span>' + copy.unavailable + '</span>');
    assert.equal(render('1.0001'), '<span>' + copy.unavailable + '</span>');
    assert.match(render('-2500.50'), /<bdi dir="ltr"[^>]*>PKR - 2,500.50<\/bdi>/);
    assert.match(render('0.00'), /PKR 0.00/);
  }
});
test('all supported currencies require their declared scale', () => {
  for (const [currency, scale] of Object.entries(money.currencyScales)) {
    const zero = scale ? '0.' + '0'.repeat(scale) : '0';
    assert.equal(money.validCurrencyAmount(zero, currency, scale), true);
    assert.equal(money.validCurrencyAmount(zero, currency, scale === 2 ? 3 : 2), false);
  }
});
test('invalid decimals are never rounded, coerced or guessed', () => {
  for (const value of ['1e5', '+1.00', '01.00', '-0.00', '1', '1.0', '1.000', '1000000000000.00', ' 1.00', '1.00\n', 'NaN', '', 1, null]) {
    assert.equal(money.validCurrencyAmount(value, 'PKR', 2), false, String(value));
    assert.throws(() => money.formatExactDecimal(value, 'PKR', 2));
  }
  for (const value of ['-0', '1.0', '1.00']) assert.equal(money.validCurrencyAmount(value, 'JPY', 0), false);
  assert.equal(money.validCurrencyAmount('-0.000', 'BHD', 3), false);
  assert.equal(money.validCurrencyAmount('1.00', 'ZZZ', 2), false);
});
test('query serialization omits optional filters and normalizes currency without a tenant', () => {
  assert.equal(accountQueryString(), '');
  assert.equal(accountQueryString({ status: 'ARCHIVED', accountType: 'WALLET', currency: 'bhd', page: 2, size: 20, businessId: 'injected' }), '?status=ARCHIVED&accountType=WALLET&currency=BHD&page=2&size=20');
  assert.equal(accountQueryString({ page: 0, size: 50 }), '?page=0&size=50');
  for (const query of [{ status: 'ALL' }, { accountType: 'ALL' }, { currency: 'ZZZ' }, { page: -1 }, { page: 0.1 }, { size: 0 }, { size: 101 }]) assert.throws(() => accountQueryString(query));
});
test('AVAILABLE requires exact money, matching metadata and no unavailable reason', () => {
  assert.deepEqual(contracts.parseAccount(account()), account());
  for (const patch of [{ currentBalance: null }, { currentBalance: 115000 }, { currentBalance: '1.001' }, { currentBalance: '-0.00' },
    { balanceUnavailableReason: 'INTEGRITY_CHECK_FAILED' }, { currencyDecimals: 3 }, { currency: 'ZZZ' }, { openingBalance: '1e5' }, { openingBalance: null }]) {
    assert.throws(() => contracts.parseAccount({ ...account(), ...patch }), contracts.InvalidAccountResponse);
  }
});
test('UNAVAILABLE preserves null and each machine reason rather than fabricating zero', () => {
  for (const reason of contracts.balanceReasons) {
    const raw = { ...account(), openingBalance: reason === 'INTEGRITY_CHECK_FAILED' ? '100000.00' : null, currentBalance: null, balanceStatus: 'UNAVAILABLE', balanceUnavailableReason: reason, ...(reason === 'UNSUPPORTED_CURRENCY' ? { currencyDecimals: 3 } : {}) };
    assert.equal(contracts.parseAccount(raw).currentBalance, null);
  }
  for (const patch of [{ currentBalance: '0.00' }, { balanceUnavailableReason: null }, { balanceUnavailableReason: 'RECONCILING' }]) {
    assert.throws(() => contracts.parseAccount({ ...account(), balanceStatus: 'UNAVAILABLE', currentBalance: null, balanceUnavailableReason: 'INTEGRITY_CHECK_FAILED', ...patch }));
  }
});
test('unsupported currency is allowed only as explicit unavailable metadata, never guessed', () => {
  const raw = { ...account(), currency: 'ZZZ', currencyDecimals: 7, openingBalance: null, currentBalance: null,
    balanceStatus: 'UNAVAILABLE', balanceUnavailableReason: 'UNSUPPORTED_CURRENCY' };
  assert.equal(contracts.parseAccount(raw).currency, 'ZZZ');
  assert.equal(money.validCurrencyAmount(raw.openingBalance, raw.currency, raw.currencyDecimals), false);
  assert.throws(() => contracts.parseAccount({ ...raw, balanceUnavailableReason: 'CAPACITY_OVERFLOW' }));
  assert.throws(() => contracts.parseAccount({ ...raw, openingBalance: 'opaque' }));
});
test('v1.3 rejects invalid null/reason combinations and malformed preserved openings', () => {
  const unavailable = { ...account(), currentBalance: null, balanceStatus: 'UNAVAILABLE' };
  for (const reason of ['PRECISION_OVERFLOW', 'CAPACITY_OVERFLOW']) {
    assert.equal(contracts.parseAccount({ ...unavailable, openingBalance: null, balanceUnavailableReason: reason }).openingBalance, null);
    for (const openingBalance of ['100000.00', '1.0001', '1000000000000.00', undefined]) {
      assert.throws(() => contracts.parseAccount({ ...unavailable, openingBalance, balanceUnavailableReason: reason }));
    }
  }
  for (const openingBalance of [null, '1.0001', '1000000000000.00', '-0.00', '1e5']) {
    assert.throws(() => contracts.parseAccount({ ...unavailable, openingBalance, balanceUnavailableReason: 'INTEGRITY_CHECK_FAILED' }));
  }
  assert.throws(() => contracts.parseAccount({ ...unavailable, openingBalance: null, balanceUnavailableReason: 'UNSUPPORTED_CURRENCY' }));
  const corruptFuture = { ...unavailable, openingDate: '2026-11-01', openingBalance: null, balanceUnavailableReason: 'PRECISION_OVERFLOW' };
  assert.equal(contracts.parseAccount(corruptFuture).openingBalance, null);
  assert.throws(() => contracts.parseAccount({ ...corruptFuture, currentBalance: '0.00' }));
});
test('v1.3 mixed valid/unavailable list and nullable detail preserve exact values', () => {
  const second = { ...account(), id: '22222222-2222-4222-8222-222222222222', openingBalance: null, currentBalance: null,
    balanceStatus: 'UNAVAILABLE', balanceUnavailableReason: 'PRECISION_OVERFLOW' };
  const parsed = contracts.parseAccountPage({ ...page(), items: [account(), second], totalElements: 2 }, {});
  assert.equal(parsed.items[0].openingBalance, '100000.00');
  assert.equal(parsed.items[1].openingBalance, null);
  assert.equal(contracts.parseAccountDetail({ ...second, notes: null }, second.id).openingBalance, null);
});
test('pre-opening known zero and Karachi cutoff are validated without calculating balances', () => {
  assert.equal(contracts.parseAccount({ ...account(), openingDate: '2026-11-01', currentBalance: '0.00' }).currentBalance, '0.00');
  assert.throws(() => contracts.parseAccount({ ...account(), openingDate: '2026-11-01' }));
  assert.equal(contracts.parseAccount({ ...account(), observationAt: '2026-10-14T20:00:00Z', effectiveCutoffDate: '2026-10-15' }).effectiveCutoffDate, '2026-10-15');
  assert.throws(() => contracts.parseAccount({ ...account(), effectiveCutoffDate: '2026-10-15' }));
});
test('malformed required fields, enums, dates and unmasked identifiers fail safely', () => {
  for (const key of Object.keys(account())) { const raw = account(); delete raw[key]; assert.throws(() => contracts.parseAccount(raw), key); }
  for (const patch of [{ id: 'javascript:alert(1)' }, { accountType: 'OTHER' }, { status: 'CREATED' }, { balanceStatus: 'RECONCILING' },
    { openingDate: '2026-02-30' }, { observationAt: 'not-a-date' }, { accountIdentifier: '1234567890' }, { accountName: '' }]) {
    assert.throws(() => contracts.parseAccount({ ...account(), ...patch }));
  }
});
test('detail identity and nullable notes are validated; unexpected fields are discarded', () => {
  const raw = { ...account(), notes: null, privateSecret: 'not retained' };
  assert.equal(contracts.parseAccountDetail(raw, id).notes, null);
  assert.equal('privateSecret' in contracts.parseAccountDetail(raw, id), false);
  assert.throws(() => contracts.parseAccountDetail(raw, '22222222-2222-4222-8222-222222222222'));
  assert.throws(() => contracts.parseAccountDetail(account(), id));
  assert.throws(() => contracts.parseAccountDetail({ ...raw, notes: 123 }, id));
});
test('empty and out-of-range pages retain requested pagination', () => {
  const empty = { items: [], page: 3, size: 20, totalElements: 0, totalPages: 0 };
  assert.deepEqual(contracts.parseAccountPage(empty, { page: 3, size: 20 }), empty);
  const outside = { items: [], page: 5, size: 20, totalElements: 12, totalPages: 1 };
  assert.deepEqual(contracts.parseAccountPage(outside, { page: 5, size: 20 }), outside);
  assert.throws(() => contracts.parseAccountPage({ ...empty, page: 0 }, { page: 3, size: 20 }));
  assert.throws(() => contracts.parseAccountPage({ ...outside, totalPages: 2 }, { page: 5, size: 20 }));
});
test('list validates filters, deterministic order, counts and shared snapshot', () => {
  assert.deepEqual(contracts.parseAccountPage(page(), {}), page());
  assert.throws(() => contracts.parseAccountPage(page(), { status: 'ARCHIVED' }));
  assert.throws(() => contracts.parseAccountPage(page(), { accountType: 'CASH' }));
  assert.throws(() => contracts.parseAccountPage(page(), { currency: 'USD' }));
  const second = { ...account(), id: '22222222-2222-4222-8222-222222222222', createdAt: '2026-10-14T10:05:00Z' };
  const two = { ...page(), items: [account(), second], totalElements: 2 };
  assert.equal(contracts.parseAccountPage(two, {}).items.length, 2);
  assert.throws(() => contracts.parseAccountPage({ ...two, items: [second, account()] }, {}));
  assert.throws(() => contracts.parseAccountPage({ ...two, items: [account(), { ...second, observationAt: '2026-10-14T13:31:00Z' }] }, {}));
  assert.throws(() => contracts.parseAccountPage({ ...two, items: [account(), account()] }, {}));
});
test('whole-second wire timestamps do not invent UUID ordering for subsecond database rows', () => {
  const first = { ...account(), id: 'ffffffff-ffff-4fff-8fff-ffffffffffff' };
  const second = account();
  const response = { ...page(), items: [first, second], totalElements: 2 };
  assert.equal(contracts.parseAccountPage(response, {}).items[0].id, first.id);
});
test('list API uses only GET, cookies, no-store and validated wire data', async () => {
  backend(() => Response.json(page()));
  assert.equal((await accountsApi.list()).items[0].currentBalance, '115000.00');
  assert.equal(calls.length, 1); assert.equal(calls[0].url, '/api/finance/accounts');
  assert.equal(calls[0].options.method, 'GET'); assert.equal(calls[0].options.credentials, 'same-origin');
  assert.equal(calls[0].options.cache, 'no-store'); assert.equal(calls[0].options.body, undefined);
});
test('detail API validates returned identity and encodes no user/business identity', async () => {
  backend(() => Response.json({ ...account(), notes: null }));
  assert.equal((await accountsApi.detail(id)).id, id);
  assert.equal(calls[0].url, '/api/finance/accounts/' + id);
  await assert.rejects(accountsApi.detail('../other')); assert.equal(calls.length, 1);
});
test('invalid financial wire data rejects instead of becoming an empty list', async () => {
  for (const payload of [{}, { ...page(), items: [{ ...account(), currentBalance: '115000.001' }] }, { ...page(), items: [] }]) {
    backend(() => Response.json(payload)); await assert.rejects(accountsApi.list(), contracts.InvalidAccountResponse); mock.restoreAll();
  }
});
test('HTTP errors remain errors, with no fixture fallback or automatic retry', async () => {
  for (const status of [400, 401, 403, 404, 409, 500]) {
    client.allowPrivateRequests(); backend(() => Response.json({ error: 'Forbidden', message: 'Controlled error' }, { status }));
    await assert.rejects(accountsApi.list(), error => error instanceof client.ApiError && error.status === status);
    mock.restoreAll();
  }
  assert.equal(calls.length, 6);
});
test('network failure remains a visible connection error', async () => {
  backend(() => { throw new TypeError('Fixture connection failed'); });
  await assert.rejects(accountsApi.list(), error => error instanceof client.ApiError && error.status === 0);
  assert.equal(calls.length, 1);
});
test('caller abort propagates to fetch and rejects the detail request', async () => {
  backend((url, options) => new Promise((resolve, reject) => options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true })));
  const controller = new AbortController(); const pending = accountsApi.detail(id, controller.signal);
  controller.abort(); await assert.rejects(pending, { name: 'AbortError' });
  assert.equal(calls[0].options.signal.aborted, true);
});
test('business-context invalidation rejects a late successful response', async () => {
  let respond; backend(() => new Promise(resolve => { respond = resolve; }));
  const pending = accountsApi.list(); client.invalidatePrivateRequests(); respond(Response.json(page()));
  await assert.rejects(pending, { name: 'AbortError' });
});
test('errors have bilingual safe presentation; a missing list endpoint is not an empty business', () => {
  assert.deepEqual(Object.keys(accountUr), Object.keys(accountEn));
  assert.equal(accountErrorKey(new client.ApiError(404, 'Controlled')), 'endpointMissing');
  assert.equal(accountErrorKey(new client.ApiError(404, 'Controlled'), true), 'notFound');
  assert.equal(accountErrorKey(new contracts.InvalidAccountResponse()), 'invalid');
  assert.equal(accountErrorKey(new client.ApiError(403, 'Controlled')), 'forbidden');
  assert.equal(accountErrorKey(new client.ApiError(409, 'Controlled', undefined, 'active_business_required')), 'business');
});
