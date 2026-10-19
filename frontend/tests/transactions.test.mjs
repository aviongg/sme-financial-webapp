import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';
const directory = mkdtempSync(path.join(tmpdir(), 'finsight-transactions-'));
for (const name of ['lib/api/dev-a/drafts','components/records/draft-copy','lib/api/client', 'lib/utils/decimal-display', 'lib/utils/amount-input', 'lib/api/dev-a/account-contracts', 'lib/api/dev-a/transaction-contracts', 'lib/api/dev-a/transactions', 'lib/api/dev-a/accounts', 'components/finance/transactions/transaction-input', 'components/finance/transactions/transaction-copy', 'components/finance/transactions/transaction-submission', 'components/finance/transactions/transaction-accounts']) {
  const target = path.join(directory, name + '.js'); mkdirSync(path.dirname(target), { recursive: true });
  const source = readFileSync(new URL('../src/' + name + '.ts', import.meta.url), 'utf8');
  writeFileSync(target, ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
}
after(() => rmSync(directory, { recursive: true }));
const require = createRequire(import.meta.url);
const input = require(path.join(directory, 'components/finance/transactions/transaction-input.js'));
const contracts = require(path.join(directory, 'lib/api/dev-a/transaction-contracts.js'));
const { transactionsApi, transactionQueryString } = require(path.join(directory, 'lib/api/dev-a/transactions.js'));
const { TransactionSubmission, PostingBlocked, postingFailure, postingFieldErrors, canPresentPosting } = require(path.join(directory, 'components/finance/transactions/transaction-submission.js'));
const client = require(path.join(directory, 'lib/api/client.js'));
const { transactionEn, transactionUr } = require(path.join(directory, 'components/finance/transactions/transaction-copy.js'));
const account = { id: '11111111-1111-4111-8111-111111111111', status: 'ACTIVE', balanceStatus: 'AVAILABLE', currency: 'PKR', currencyDecimals: 2, openingDate: '2026-10-01' };
const draft = () => ({ selectedAccount: account.id, event: 'DIRECT_SALE_RECEIPT', amountText: '1.00', dateText: '2026-10-14', descriptionText: '', confirmed: true });
test('positive exact PKR input preserves capacity boundaries without rounding', () => {
  for (const value of ['0.01', '1.00', '999999999999.99']) assert.equal(input.isPositivePostingAmount(value), true);
  for (const value of ['0.00', '-0.00', '-1.00', '1', '1.0', '1.001', '+1.00', '01.00', '1e5', '1,000.00', 'Û±.Û°Û°', '1000000000000.00', 1, null]) assert.equal(input.isPositivePostingAmount(value), false, String(value));
});
test('posting dates use the actual Karachi calendar boundary', () => {
  assert.equal(input.karachiToday(new Date('2026-10-14T18:59:59Z')), '2026-10-14');
  assert.equal(input.karachiToday(new Date('2026-10-14T19:00:00Z')), '2026-10-15');
  assert.deepEqual(input.validatePostingInput(draft(), account, 20, '2026-10-14'), {});
  for (const dateText of ['2026-09-30', '2026-10-15', '2026-02-30', '']) assert.equal(input.validatePostingInput({ ...draft(), dateText }, account, 20, '2026-10-14').dateText, 'validDate');
});
test('posting preliminarily requires active PKR metadata and explicit confirmation', () => {
  for (const patch of [{ status: 'INACTIVE' }, { currency: 'USD' }, { currencyDecimals: 3 }]) assert.equal(input.isActivePkr({ ...account, ...patch }), false);
  assert.equal(input.validatePostingInput(draft(), undefined, 20, '2026-10-14').selectedAccount, 'chooseAccount');
  assert.equal(input.validatePostingInput({ ...draft(), confirmed: false }, account, 20, '2026-10-14').confirmed, 'confirmRequired');
  assert.equal(input.validatePostingInput({ ...draft(), descriptionText: 'x'.repeat(21) }, account, 20, '2026-10-14').descriptionText, 'descriptionLimit');
});
test('event scope and cutover notice have complete bilingual copy', () => {
  assert.deepEqual(Object.keys(transactionEn), Object.keys(transactionUr));
  for (const copy of [transactionEn, transactionUr]) {
    for (const choice of input.postingChoices) assert.ok(copy[choice]);
    assert.ok(copy.notice); assert.ok(copy.excluded); assert.notEqual(copy.saleHelp, copy.expenseHelp);
  }
});
const transaction = () => ({ id: '22222222-2222-4222-8222-222222222222', businessId: '33333333-3333-4333-8333-333333333333', accountId: account.id,
  accountName: 'Controlled PKR bank', accountType: 'BANK', eventType: 'DIRECT_SALE_RECEIPT', movementDirection: 'INCREASE',
  amount: '1.00', currency: 'PKR', baseAmountPkr: '1.00', effectiveDate: '2026-10-14', description: null, resultingBalance: '-49.00',
  createdByUserId: '44444444-4444-4444-8444-444444444444', createdAt: '2026-10-14T12:00:00Z' });
const detail = () => ({ ...transaction(), movements: [{ id: '55555555-5555-4555-8555-555555555555', accountId: account.id, movementDirection: 'INCREASE', amount: '1.00', currency: 'PKR', effectiveDate: '2026-10-14', createdAt: '2026-10-14T12:00:00Z' }] });
const page = () => ({ content: [transaction()], page: 0, size: 20, totalElements: 1, totalPages: 1, effectiveCutoffDate: '2026-10-14', observationAt: '2026-10-14T12:00:00Z' });
beforeEach(() => { client.invalidatePrivateRequests(); client.allowPrivateRequests(); });
afterEach(() => { mock.restoreAll(); client.invalidatePrivateRequests(); });
function transport(handler) {
  const calls = [];
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    if (url === '/api/auth/csrf') return Response.json({ token: 'controlled-token', headerName: 'X-XSRF-TOKEN' });
    return handler(url, options);
  });
  return calls;
}
test('request allowlisting excludes confirmation, identity, balance and recognition flags', () => {
  const value = contracts.transactionPostPayload({ ...draft(), descriptionText: '  controlled sale  ', businessId: 'injected', actorId: 'injected', resultingBalance: '900.00', revenue: true });
  assert.deepEqual(value, { accountId: account.id, eventType: 'DIRECT_SALE_RECEIPT', amount: '1.00', effectiveDate: '2026-10-14', description: 'controlled sale' });
  assert.equal(Object.isFrozen(value), true);
  assert.equal('description' in contracts.transactionPostPayload(draft()), false);
  for (const patch of [{ confirmed: false }, { event: 'LOAN' }, { amountText: '0.00' }, { descriptionText: 'x'.repeat(256) }]) assert.throws(() => contracts.transactionPostPayload({ ...draft(), ...patch }));
});
test('query serialization allowlists filters, omits ALL and rejects bad ranges/pagination', () => {
  assert.equal(transactionQueryString(), '');
  assert.equal(transactionQueryString({ accountId: account.id, eventType: 'OPERATING_EXPENSE_PAYMENT', startDate: '2026-10-01', endDate: '2026-10-14', page: 2, size: 10, businessId: 'injected' }), '?accountId=' + account.id + '&eventType=OPERATING_EXPENSE_PAYMENT&startDate=2026-10-01&endDate=2026-10-14&page=2&size=10');
  for (const query of [{ accountId: '../other' }, { eventType: 'ALL' }, { startDate: '2026-02-30' }, { startDate: '2026-10-15', endDate: '2026-10-14' }, { page: -1 }, { size: 101 }, { size: 0 }, { page: 0.5 }]) assert.throws(() => transactionQueryString(query));
});
test('wire validation preserves exact amounts, classification and authorized metadata only', () => {
  assert.deepEqual(contracts.parseTransaction(transaction()), transaction());
  assert.deepEqual(contracts.parseTransactionDetail(detail(), transaction().businessId, transaction().id), detail());
  assert.equal('evidenceUrl' in contracts.parseTransaction({ ...transaction(), evidenceUrl: 'javascript:bad' }), false);
  const expense = { ...transaction(), eventType: 'OPERATING_EXPENSE_PAYMENT', movementDirection: 'DECREASE', amount: '999999999999.99', baseAmountPkr: '999999999999.99', resultingBalance: '-999999999999.99' };
  assert.equal(contracts.parseTransaction(expense).amount, expense.amount);
  assert.equal(contracts.parseTransaction({ ...transaction(), createdAt: '2026-10-14T12:00:00.123456789Z' }).createdAt, '2026-10-14T12:00:00.123456789Z');
});
test('missing fields, invalid enums/precision/capacity/dates/tenant and null balances reject safely', () => {
  for (const key of Object.keys(transaction())) { const raw = transaction(); delete raw[key]; assert.throws(() => contracts.parseTransaction(raw), key); }
  for (const patch of [{ amount: '0.00', baseAmountPkr: '0.00' }, { amount: '1.001' }, { amount: 1 }, { baseAmountPkr: '2.00' }, { currency: 'USD' },
    { eventType: 'ADVANCE' }, { movementDirection: 'DECREASE' }, { resultingBalance: null }, { resultingBalance: '1000000000000.00' }, { resultingBalance: '-0.00' },
    { effectiveDate: '2026-02-30' }, { createdAt: '2026-10-14T25:00:00Z' }, { description: 'x'.repeat(256) }, { accountType: 'CASH', resultingBalance: '-1.00' }]) assert.throws(() => contracts.parseTransaction({ ...transaction(), ...patch }));
  assert.throws(() => contracts.parseTransaction(transaction(), account.id));
});
test('detail validates one matching movement and expected transaction ID', () => {
  for (const movements of [[], [detail().movements[0], detail().movements[0]], [{ ...detail().movements[0], amount: '2.00' }], [{ ...detail().movements[0], accountId: transaction().id }], [{ ...detail().movements[0], movementDirection: 'DECREASE' }]]) assert.throws(() => contracts.parseTransactionDetail({ ...detail(), movements }));
  assert.throws(() => contracts.parseTransactionDetail(detail(), undefined, account.id));
});
test('pages validate echo/count/filter/order and preserve empty/out-of-range semantics', () => {
  assert.deepEqual(contracts.parseTransactionPage(page(), {}), page());
  for (const query of [{ accountId: transaction().id }, { eventType: 'OPERATING_EXPENSE_PAYMENT' }, { startDate: '2026-10-15' }]) assert.throws(() => contracts.parseTransactionPage(page(), query));
  const outside = { ...page(), content: [], page: 5 };
  assert.deepEqual(contracts.parseTransactionPage(outside, { page: 5 }), outside);
  assert.equal(contracts.parseTransactionPage({ ...page(), content: [], totalElements: 0, totalPages: 0 }, {}).content.length, 0);
  const older = { ...transaction(), id: account.id, effectiveDate: '2026-10-13' };
  const two = { ...page(), content: [transaction(), older], totalElements: 2 };
  assert.equal(contracts.parseTransactionPage(two, {}).content.length, 2);
  for (const patch of [{ content: [older, transaction()] }, { totalPages: 2 }, { content: [transaction(), transaction()] }, { observationAt: '2026-10-14T20:00:00Z' }]) assert.throws(() => contracts.parseTransactionPage({ ...two, ...patch }, {}));
});
test('reads reuse same-origin/no-store and reject missing endpoints without empty fallback', async () => {
  const calls = transport(() => Response.json(page()));
  assert.equal((await transactionsApi.list()).content[0].id, transaction().id);
  assert.equal(calls[0].url, '/api/finance/transactions'); assert.equal(calls[0].options.credentials, 'same-origin'); assert.equal(calls[0].options.cache, 'no-store');
  mock.restoreAll(); transport(() => Response.json({ error: 'Not Found' }, { status: 404 }));
  await assert.rejects(transactionsApi.list(), error => error instanceof client.ApiError && error.status === 404);
});
test('POST uses real adapter allowlist, cookie/CSRF header and validates returned fields', async () => {
  const calls = transport(() => Response.json(detail(), { status: 201 }));
  assert.deepEqual(await transactionsApi.post({ ...contracts.transactionPostPayload(draft()), actorId: 'injected', businessId: 'injected' }, 'logical-key'), detail());
  assert.equal(calls.length, 2); assert.equal(calls[1].options.method, 'POST');
  assert.equal(calls[1].options.headers.get('Idempotency-Key'), 'logical-key'); assert.equal(calls[1].options.headers.get('X-XSRF-TOKEN'), 'controlled-token');
  assert.deepEqual(JSON.parse(calls[1].options.body), contracts.transactionPostPayload(draft()));
  mock.restoreAll(); client.resetCsrf(); transport(() => Response.json({ ...detail(), amount: '2.00', baseAmountPkr: '2.00', movements: [{ ...detail().movements[0], amount: '2.00' }] }, { status: 201 }));
  await assert.rejects(transactionsApi.post(contracts.transactionPostPayload(draft()), 'key'), contracts.InvalidTransactionResponse);
});
test('invalid financial response is not replaced with fixtures or zero', async () => {
  transport(() => Response.json({ ...page(), content: [{ ...transaction(), resultingBalance: null }] }));
  await assert.rejects(transactionsApi.list(), contracts.InvalidTransactionResponse);
});
test('presentation grants only active OWNER/ACCOUNTANT posting, not server authority', () => {
  for (const role of ['OWNER', 'ACCOUNTANT', 'MANAGER', 'VIEWER', 'SALESPERSON']) {
    assert.equal(canPresentPosting({ role, membershipStatus: 'ACTIVE' }), ['OWNER', 'ACCOUNTANT'].includes(role));
    assert.equal(canPresentPosting({ role, membershipStatus: 'REVOKED' }), false);
  }
  assert.equal(canPresentPosting(null), false);
});
test('double click and completed submission cannot send a second event', async () => {
  let finish, calls = 0;
  const submission = new TransactionSubmission(() => { calls++; return new Promise(resolve => { finish = resolve; }); }, () => 'key');
  const pending = submission.submit(draft()); await assert.rejects(submission.submit(draft()), PostingBlocked);
  finish(detail()); await pending; await assert.rejects(submission.submit(draft()), PostingBlocked); assert.equal(calls, 1);
});
test('uncertain outcome retries only explicitly with identical frozen payload and original key', async () => {
  const calls = []; let keys = 0;
  const submission = new TransactionSubmission(async (payload, key) => { calls.push({ payload, key }); if (calls.length === 1) throw new client.ApiError(0, 'Controlled disconnect'); return detail(); }, () => 'key-' + ++keys);
  const original = draft(); await assert.rejects(submission.submit(original)); original.amountText = '5.00';
  assert.equal(submission.phase, 'uncertain'); assert.equal(calls.length, 1);
  await assert.rejects(submission.submit(original), PostingBlocked); assert.equal(calls.length, 1);
  await submission.submit(draft()); assert.equal(keys, 1); assert.equal(calls[0].payload, calls[1].payload); assert.equal(calls[0].key, calls[1].key);
});
test('validation/account errors allow correction; key conflicts and denied roles lock retries', async () => {
  for (const status of [400, 404, 409, 403, 401]) {
    let calls = 0, keys = 0;
    const submission = new TransactionSubmission(async () => { calls++; if (calls === 1) throw new client.ApiError(status, 'Controlled server error', { cashBalance: 'Controlled insufficient CASH' }); return detail(); }, () => String(++keys));
    await assert.rejects(submission.submit(draft()));
    if ([400, 404].includes(status)) { assert.equal(submission.phase, 'idle'); await submission.submit({ ...draft(), amountText: '0.50' }); assert.equal(keys, 2); }
    else { await assert.rejects(submission.submit(draft()), PostingBlocked); assert.equal(calls, 1); }
  }
  assert.equal(postingFailure(new contracts.InvalidTransactionResponse()), 'uncertain');
});
test('session/business invalidation aborts and discards even a late successful mutation', async () => {
  let finish, actualSignal;
  const submission = new TransactionSubmission((payload, key, signal) => { actualSignal = signal; return new Promise(resolve => { finish = resolve; }); }, () => 'key');
  const pending = submission.submit(draft()); submission.invalidate(); assert.equal(actualSignal.aborted, true); finish(detail());
  await assert.rejects(pending, { name: 'AbortError' }); await assert.rejects(submission.submit(draft()), PostingBlocked);
});
test('detail abort propagates; stale read success from old business is rejected', async () => {
  let finish;
  const calls = transport(() => new Promise(resolve => { finish = () => resolve(Response.json(detail())); }));
  const controller = new AbortController(), pending = transactionsApi.detail(transaction().id, controller.signal);
  controller.abort(); finish(); await assert.rejects(pending, { name: 'AbortError' }); assert.equal(calls[0].options.signal.aborted, true);
  mock.restoreAll(); transport(() => new Promise(resolve => { finish = () => resolve(Response.json(page())); }));
  const read = transactionsApi.list(); client.invalidatePrivateRequests(); finish(); await assert.rejects(read, { name: 'AbortError' });
});
test('network failure and 403 remain visible with one mutation and no replay/fallback', async () => {
  const calls = transport(() => { throw new TypeError('Controlled disconnect'); });
  const submission = new TransactionSubmission((payload, key, signal) => transactionsApi.post(payload, key, signal), () => 'key');
  await assert.rejects(submission.submit(draft())); assert.equal(submission.phase, 'uncertain'); assert.equal(calls.filter(call => call.options.method === 'POST').length, 1);
  mock.restoreAll(); client.resetCsrf(); transport(() => Response.json({ error: 'Forbidden', message: 'Controlled denial' }, { status: 403 }));
  await assert.rejects(transactionsApi.post(contracts.transactionPostPayload(draft()), 'key'), error => error instanceof client.ApiError && error.status === 403);
});
test('CASH rejection and account field errors use bilingual copy without exposing raw service text', () => {
  const error = new client.ApiError(400, 'Controlled server rejection', { accountId: 'Inactive controlled account', cashBalance: 'Would cause negative on controlled date', actorId: 'unexpected metadata' });
  assert.deepEqual(postingFieldErrors(error), { selectedAccount: 'accountRejected', amountText: 'cashFailure' });
  for (const copy of [transactionEn, transactionUr]) for (const key of Object.values(postingFieldErrors(error))) assert.ok(copy[key]);
  assert.deepEqual(postingFieldErrors(new client.ApiError(403, 'Denied')), {});
});
test('account choices exhaust existing pagination and propagate the same abort signal', async () => {
  const { accountsApi } = require(path.join(directory, 'lib/api/dev-a/accounts.js'));
  const { loadTransactionAccounts } = require(path.join(directory, 'components/finance/transactions/transaction-accounts.js'));
  const controller = new AbortController(), calls = [];
  mock.method(accountsApi, 'list', async (query, signal) => {
    calls.push({ query, signal });
    const prefix = query.status === 'ACTIVE' ? '1' : query.status === 'INACTIVE' ? '2' : '3';
    return { items: [{ id: prefix + String(query.page) }], totalPages: query.status === 'ACTIVE' ? 2 : 1 };
  });
  assert.equal((await loadTransactionAccounts(controller.signal)).length, 4);
  assert.deepEqual(calls.map(call => [call.query.status, call.query.page, call.query.size]), [['ACTIVE', 0, 100], ['ACTIVE', 1, 100], ['INACTIVE', 0, 100], ['ARCHIVED', 0, 100]]);
  assert.equal(calls.every(call => call.signal === controller.signal), true);
});
test('posting API discards successful late response after caller/context cancellation', async () => {
  let finish, started;
  const ready = new Promise(resolve => { started = resolve; });
  transport((url, options) => new Promise(resolve => { finish = () => resolve(Response.json(detail(), { status: 201 })); started(options.signal); }));
  const controller = new AbortController(), pending = transactionsApi.post(contracts.transactionPostPayload(draft()), 'key', controller.signal);
  const actual = await ready; controller.abort(); client.invalidatePrivateRequests(); finish();
  await assert.rejects(pending, { name: 'AbortError' }); assert.equal(actual.aborted, true);
});
test('actual bilingual form uses labelled exact text input, confirmation and locked retry controls', () => {
  const { createElement } = require('react'), { renderToStaticMarkup } = require('react-dom/server');
  const cache = new Map();
  const alias = name => {
    if (name === '@/components/auth/SessionProvider' || name === '@/lib/i18n/context') return {};
    if (name.startsWith('@/lib/api/') || (name === '@/lib/utils/decimal-display' || name === '@/lib/utils/amount-input')) return require(path.join(directory, name.slice(2) + '.js'));
    if (name.startsWith('./transaction-')) return require(path.join(directory, 'components/finance/transactions', name.slice(2) + '.js'));
    if (name === '../../records/draft-copy') return require(path.join(directory,'components/records/draft-copy.js'));
    if (name === '../accounts/CreateAccountForm') return {CreateAccountForm() {}};
    if (name === '../../records/counterparties/CounterpartySelector') return {CounterpartySelector() {}};
    if (!name.startsWith('@/')) return require(name);
    if (cache.has(name)) return cache.get(name);
    const ext = name === '@/lib/utils/cn' ? '.ts' : '.tsx';
    const source = readFileSync(new URL('../src/' + name.slice(2) + ext, import.meta.url), 'utf8');
    const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX } }).outputText;
    const compiledModule = { exports: {} }; new Function('require', 'module', 'exports', compiled)(alias, compiledModule, compiledModule.exports);
    cache.set(name, compiledModule.exports); return compiledModule.exports;
  };
  const { PostingFormView } = alias('@/components/finance/transactions/PostTransactionForm');
  for (const copy of [transactionEn, transactionUr]) {
    const html = renderToStaticMarkup(createElement(PostingFormView, { draft: draft(), accounts: [account], descriptionLimit: 255, errors: {}, copy, locked: true, onChange() {}, onSubmit() {}, onClose() {} }));
    assert.match(html, /<form[^>]*aria-labelledby=/); assert.match(html, /<fieldset disabled=""/);
    assert.match(html, /<input[^>]*type="text"[^>]*inputMode="decimal"[^>]*dir="ltr"/);
    assert.ok(html.includes(copy.confirm)); assert.ok(html.includes(copy.retryOriginal)); assert.ok(html.includes(copy.excluded));
    assert.equal(html.includes('type="number"'), false);
  }
});
test('actual demo route mounts no live resource or private request for either locale', () => {
  const { createElement } = require('react'), { renderToStaticMarkup } = require('react-dom/server');
  const source = readFileSync(new URL('../src/app/finance/transactions/page.tsx', import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  const calls = transport(() => { throw new Error('No demo private request'); });
  for (const locale of ['en', 'ur']) {
    const compiledModule = { exports: {} };
    const imports = name => {
      if (name === '@/lib/api/config') return { isDemoMode: true };
      if (name === '@/lib/i18n/context') return { useLanguage: () => ({ locale }) };
      if (name.endsWith('/transaction-copy')) return { transactionEn, transactionUr };
      if (name.endsWith('/TransactionsList')) return { TransactionsList() { throw new Error('Live tree mounted in demo'); } };
      if (name.endsWith('/LivePage')) return { LivePage: ({ children }) => createElement('main', null, children) };
      if (name.endsWith('/Card')) return { Card: ({ children }) => createElement('section', null, children) };
      return require(name);
    };
    new Function('require', 'module', 'exports', compiled)(imports, compiledModule, compiledModule.exports);
    const html = renderToStaticMarkup(createElement(compiledModule.exports.default));
    assert.ok(html.includes((locale === 'ur' ? transactionUr : transactionEn).preview));
  }
  assert.equal(calls.length, 0);
});
