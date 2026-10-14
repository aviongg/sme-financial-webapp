import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-accounts-create-'));
const modules = ['lib/api/client', 'lib/utils/decimal-display', 'lib/api/dev-a/account-contracts', 'lib/api/dev-a/account-create-contracts', 'lib/api/dev-a/accounts', 'components/finance/accounts/account-copy'];
for (const file of modules) {
  const target = path.join(directory, file + '.js');
  mkdirSync(path.dirname(target), { recursive: true });
  const source = readFileSync(new URL('../src/' + file + '.ts', import.meta.url), 'utf8');
  writeFileSync(target, ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
}
after(() => rmSync(directory, { recursive: true })); // Uniquely owned test directory only.
const require = createRequire(import.meta.url);
const { validateOpeningSetup, canPresentAccountCreate, accountCreatePayload, AccountCreateSubmission, AccountSubmissionBlocked, accountCreateFailure } = require(path.join(directory, 'lib/api/dev-a/account-create-contracts.js'));
const client = require(path.join(directory, 'lib/api/client.js'));
const { accountsApi } = require(path.join(directory, 'lib/api/dev-a/accounts.js'));
const { InvalidAccountResponse } = require(path.join(directory, 'lib/api/dev-a/account-contracts.js'));
const { accountCreateEn, accountCreateUr } = require(path.join(directory, 'components/finance/accounts/account-copy.js'));
const draft = () => ({ accountName: 'Synthetic opening', accountType: 'BANK', currency: 'PKR', openingBalance: '0.00', openingDate: '2026-10-14' });
const result = () => ({ ...draft(), id: '11111111-1111-4111-8111-111111111111', currencyDecimals: 2,
  currentBalance: '0.00', balanceStatus: 'AVAILABLE', balanceUnavailableReason: null, status: 'ACTIVE',
  observationAt: '2026-10-14T13:30:00Z', effectiveCutoffDate: '2026-10-14', institutionName: null,
  accountIdentifier: null, notes: null, createdAt: '2026-10-14T13:30:00Z', updatedAt: '2026-10-14T13:30:00Z' });
beforeEach(() => { client.invalidatePrivateRequests(); client.allowPrivateRequests(); });
afterEach(() => { mock.restoreAll(); client.invalidatePrivateRequests(); });
function transport(handler) {
  const calls = [];
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    if (url === '/api/auth/csrf') return Response.json({ token: 'controlled-csrf', headerName: 'X-XSRF-TOKEN' });
    return handler(url, options);
  });
  return calls;
}

test('opening setup accepts exact currency boundaries without numeric conversion', () => {
  for (const [currency, openingBalance] of [['JPY', '999999999999'], ['PKR', '-999999999999.99'], ['BHD', '-999999999999.999'], ['OMR', '0.000']]) {
    assert.deepEqual(validateOpeningSetup({ ...draft(), currency, openingBalance }), {});
  }
});
test('cash disallows negative openings while bank and wallet permit them', () => {
  assert.equal(validateOpeningSetup({ ...draft(), accountType: 'CASH', openingBalance: '-0.01' }).openingBalance, 'cashNegative');
  for (const accountType of ['BANK', 'WALLET']) assert.deepEqual(validateOpeningSetup({ ...draft(), accountType, openingBalance: '-0.01' }), {});
  assert.deepEqual(validateOpeningSetup({ ...draft(), accountType: 'CASH' }), {});
});
test('opening inputs never round, pad, coerce or accept grouping and non-Western digits', () => {
  for (const openingBalance of ['', '1', '1.0', '1.000', '1.0001', '1000000000000.00', '1e5', '+1.00', '01.00', '-0.00', '1,000.00', ' 1.00', '۱.۰۰', '١.٠٠']) {
    assert.equal(validateOpeningSetup({ ...draft(), openingBalance }).openingBalance, 'amountInvalid', openingBalance);
  }
});
test('currency changes preserve invalid-scale feedback instead of changing the amount', () => {
  const original = { ...draft(), currency: 'JPY', openingBalance: '1.00' };
  assert.equal(validateOpeningSetup(original).openingBalance, 'amountInvalid');
  assert.equal(original.openingBalance, '1.00');
  assert.equal(validateOpeningSetup({ ...draft(), currency: 'ZZZ' }).currency, 'currencyInvalid');
});
test('opening date validates actual calendar days and permits future setup', () => {
  assert.equal(validateOpeningSetup({ ...draft(), openingDate: '2026-02-30' }).openingDate, 'dateInvalid');
  assert.equal(validateOpeningSetup({ ...draft(), openingDate: '' }).openingDate, 'dateInvalid');
  assert.deepEqual(validateOpeningSetup({ ...draft(), openingDate: '2030-01-01' }), {});
});
test('name and type must fit the existing read model', () => {
  for (const accountName of ['', '   ', 'x'.repeat(101)]) assert.equal(validateOpeningSetup({ ...draft(), accountName }).accountName, 'nameRequired');
  assert.equal(validateOpeningSetup({ ...draft(), accountType: 'OTHER' }).accountType, 'typeInvalid');
});
test('creation presentation allows only active owner/accountant memberships', () => {
  for (const role of ['OWNER', 'ACCOUNTANT', 'MANAGER', 'VIEWER', 'SALESPERSON', 'UNKNOWN']) {
    assert.equal(canPresentAccountCreate({ role, membershipStatus: 'ACTIVE' }), ['OWNER', 'ACCOUNTANT'].includes(role));
    assert.equal(canPresentAccountCreate({ role, membershipStatus: 'REVOKED' }), false);
  }
  assert.equal(canPresentAccountCreate(null), false);
});
test('opening setup guidance and validation messages have typed bilingual copy', () => {
  assert.deepEqual(Object.keys(accountCreateEn), Object.keys(accountCreateUr));
  for (const copy of [accountCreateEn, accountCreateUr]) {
    for (const value of Object.values(copy)) assert.ok(value.trim());
    assert.notEqual(copy.openingHelp, copy.revenueHelp);
    assert.notEqual(copy.futureHelp, copy.revenueHelp);
  }
});

test('creation payload allowlists fields, trims name and preserves exact strings', () => {
  const input = { ...draft(), accountName: '  Synthetic opening  ', businessId: 'injected', currencyDecimals: 3, accountIdentifier: 'raw', notes: 'ignored', status: 'ARCHIVED' };
  assert.deepEqual(accountCreatePayload(input), draft());
  assert.equal(Object.isFrozen(accountCreatePayload(input)), true);
  assert.throws(() => accountCreatePayload({ ...draft(), openingBalance: '1.0001' }));
});
test('real adapter sends POST with idempotency, established cookie/CSRF transport and no tenant override', async () => {
  const calls = transport(() => Response.json(result(), { status: 201 }));
  assert.deepEqual(await accountsApi.create({ ...draft(), businessId: 'injected' }, 'opaque-key'), result());
  assert.equal(calls.length, 2);
  const request = calls[1];
  assert.equal(request.url, '/api/finance/accounts'); assert.equal(request.options.method, 'POST');
  assert.equal(request.options.credentials, 'same-origin'); assert.equal(request.options.cache, 'no-store');
  assert.equal(request.options.headers.get('Idempotency-Key'), 'opaque-key');
  assert.equal(request.options.headers.get('X-XSRF-TOKEN'), 'controlled-csrf');
  assert.deepEqual(JSON.parse(request.options.body), draft());
});
test('adapter rejects invalid inputs/key before making private requests', async () => {
  const calls = transport(() => { throw new Error('Must not fetch'); });
  for (const key of ['', ' ', 'k'.repeat(101)]) await assert.rejects(accountsApi.create(draft(), key));
  await assert.rejects(accountsApi.create({ ...draft(), accountType: 'CASH', openingBalance: '-1.00' }, 'key'));
  assert.equal(calls.length, 0);
});
test('adapter validates creation response identity fields, ACTIVE/AVAILABLE and exact opening invariants', async () => {
  for (const patch of [{ notes: undefined }, { balanceStatus: 'UNAVAILABLE', openingBalance: null, currentBalance: null, balanceUnavailableReason: 'PRECISION_OVERFLOW' },
    { openingBalance: '0.000' }, { accountName: 'Other account' }, { currency: 'USD' }, { status: 'INACTIVE' }, { currentBalance: '1.00' }]) {
    transport(() => Response.json({ ...result(), ...patch }, { status: 201 }));
    await assert.rejects(accountsApi.create(draft(), 'key'), InvalidAccountResponse);
    mock.restoreAll(); client.resetCsrf();
  }
});
test('API validation/denial errors preserve field metadata and do not replay', async () => {
  const calls = transport(() => Response.json({ error: 'Validation Failed', message: 'Controlled validation', errors: { openingBalance: 'Controlled field error' } }, { status: 400 }));
  await assert.rejects(accountsApi.create(draft(), 'key'), error => error instanceof client.ApiError && error.fieldErrors.openingBalance === 'Controlled field error');
  assert.equal(calls.filter(call => call.options.method === 'POST').length, 1);
});
test('logical submission prevents double clicks and a second submission after success', async () => {
  let finish; const calls = [];
  const submission = new AccountCreateSubmission((payload, key) => { calls.push({ payload, key }); return new Promise(resolve => { finish = resolve; }); }, () => 'logical-key');
  const pending = submission.submit(draft());
  assert.equal(submission.phase, 'submitting');
  await assert.rejects(submission.submit(draft()), AccountSubmissionBlocked);
  finish(result()); assert.deepEqual(await pending, result());
  await assert.rejects(submission.submit(draft()), AccountSubmissionBlocked);
  assert.equal(calls.length, 1);
});
test('uncertain outcome retains immutable payload/key and retries only on explicit action', async () => {
  const calls = []; let keys = 0;
  const submission = new AccountCreateSubmission(async (payload, key) => {
    calls.push({ payload, key }); if (calls.length === 1) throw new client.ApiError(0, 'Controlled disconnect'); return result();
  }, () => 'logical-' + ++keys);
  const input = draft(); await assert.rejects(submission.submit(input));
  input.openingBalance = '12.00';
  assert.equal(submission.phase, 'uncertain'); assert.equal(calls.length, 1);
  await assert.rejects(submission.submit(input), AccountSubmissionBlocked);
  assert.equal(calls.length, 1);
  assert.deepEqual(await submission.submit(draft()), result());
  assert.equal(keys, 1); assert.equal(calls[0].key, calls[1].key);
  assert.equal(calls[0].payload, calls[1].payload); assert.equal(calls[1].payload.openingBalance, '0.00');
});
test('definite validation and duplicate-name errors permit correction with a new logical key', async () => {
  for (const error of [new client.ApiError(400, 'Invalid input'), new client.ApiError(409, "An account with name 'Synthetic opening' already exists for this business")]) {
    const keys = []; let counter = 0;
    const submission = new AccountCreateSubmission(async (payload, key) => { keys.push(key); if (keys.length === 1) throw error; return result(); }, () => String(++counter));
    const input = draft(); await assert.rejects(submission.submit(input));
    assert.deepEqual(input, draft()); assert.equal(submission.phase, 'idle');
    await submission.submit({ ...input, accountName: 'Corrected name' }); assert.notEqual(keys[0], keys[1]);
  }
});
test('same-key payload conflict and permission denial lock further mutation attempts', async () => {
  for (const [error, phase] of [[new client.ApiError(409, 'Idempotency key has already been used with a different request payload'), 'conflict'], [new client.ApiError(403, 'Denied'), 'denied'], [new client.ApiError(401, 'Expired'), 'denied']]) {
    let calls = 0; const submission = new AccountCreateSubmission(async () => { calls++; throw error; }, () => 'key');
    await assert.rejects(submission.submit(draft())); assert.equal(submission.phase, phase);
    await assert.rejects(submission.submit(draft()), AccountSubmissionBlocked); assert.equal(calls, 1);
  }
});
test('actual backend quoted-key conflict is localized without exposing raw service text', () => {
  assert.equal(accountCreateFailure(new client.ApiError(409, "Idempotency key 'controlled-key' has already been used with a different request payload")), 'conflict');
  assert.equal(accountCreateFailure(new client.ApiError(409, "Unknown Idempotency key 'controlled-key' conflict")), 'uncertain');
});
test('unknown conflict, server failure and malformed success retain uncertain state', async () => {
  for (const error of [new client.ApiError(409, 'Unknown conflict'), new client.ApiError(500, 'Controlled server failure'), new InvalidAccountResponse()]) {
    const submission = new AccountCreateSubmission(async () => { throw error; }, () => 'key');
    await assert.rejects(submission.submit(draft())); assert.equal(submission.phase, 'uncertain');
    assert.equal(accountCreateFailure(error), 'uncertain');
  }
});
test('context invalidation aborts and rejects even a late successful mutation response', async () => {
  let finish, signal;
  const submission = new AccountCreateSubmission((payload, key, callerSignal) => { signal = callerSignal; return new Promise(resolve => { finish = resolve; }); }, () => 'key');
  const pending = submission.submit(draft()); submission.invalidate();
  assert.equal(signal.aborted, true); finish(result());
  await assert.rejects(pending, { name: 'AbortError' });
  await assert.rejects(submission.submit(draft()), AccountSubmissionBlocked);
});
test('real adapter propagates caller cancellation and business-context generation rejection', async () => {
  let finish, started;
  const ready = new Promise(resolve => { started = resolve; });
  transport((url, options) => new Promise(resolve => { finish = () => resolve(Response.json(result(), { status: 201 })); started(options.signal); }));
  const controller = new AbortController();
  const pending = accountsApi.create(draft(), 'key', controller.signal);
  const actualSignal = await ready; controller.abort(); client.invalidatePrivateRequests(); finish();
  await assert.rejects(pending, { name: 'AbortError' }); assert.equal(actualSignal.aborted, true);
});
test('real network failure remains uncertain with one mutation and no mock fallback', async () => {
  const calls = transport(() => { throw new TypeError('Controlled disconnect'); });
  const submission = new AccountCreateSubmission(accountsApi.create, () => 'key');
  await assert.rejects(submission.submit(draft()), error => error instanceof client.ApiError && error.status === 0);
  assert.equal(submission.phase, 'uncertain'); assert.equal(calls.filter(call => call.options.method === 'POST').length, 1);
});
test('actual EN/UR form renders labelled exact-text inputs and locks edits for explicit retry', () => {
  const { createElement } = require('react');
  const { renderToStaticMarkup } = require('react-dom/server');
  const copy = require(path.join(directory, 'components/finance/accounts/account-copy.js'));
  const cache = new Map();
  const alias = name => {
    if (name === '@/components/auth/SessionProvider' || name === '@/lib/i18n/context') return {};
    if (name === './account-copy') return copy;
    if (name.startsWith('@/lib/api/') || name === '@/lib/utils/decimal-display') return require(path.join(directory, name.slice(2) + '.js'));
    if (!name.startsWith('@/')) return require(name);
    if (cache.has(name)) return cache.get(name);
    const extension = name === '@/lib/utils/cn' ? '.ts' : '.tsx';
    const source = readFileSync(new URL('../src/' + name.slice(2) + extension, import.meta.url), 'utf8');
    const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX } }).outputText;
    const compiledModule = { exports: {} };
    new Function('require', 'module', 'exports', compiled)(alias, compiledModule, compiledModule.exports);
    cache.set(name, compiledModule.exports); return compiledModule.exports;
  };
  const { OpeningSetupForm } = alias('@/components/finance/accounts/CreateAccountForm');
  for (const [createCopy, accountCopy] of [[copy.accountCreateEn, copy.accountEn], [copy.accountCreateUr, copy.accountUr]]) {
    const html = renderToStaticMarkup(createElement(OpeningSetupForm, { draft: draft(), copy: createCopy, accountCopy, errors: { openingBalance: 'amountInvalid' }, locked: true,
      message: createCopy.uncertain, onSubmit() {}, onClose() {}, onChange() {} }));
    assert.match(html, /<form[^>]*aria-labelledby=[^>]*aria-describedby=/);
    assert.match(html, /<fieldset disabled=""/);
    assert.match(html, /<input[^>]*type="text"[^>]*inputMode="decimal"[^>]*dir="ltr"/);
    assert.match(html, /role="alert"/);
    assert.ok(html.includes(createCopy.retryOriginal)); assert.ok(html.includes(createCopy.openingHelp));
    assert.ok(html.includes(createCopy.revenueHelp)); assert.ok(html.includes(createCopy.futureHelp));
    assert.equal(html.includes('type="number"'), false);
  }
});
