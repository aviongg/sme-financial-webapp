import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-reversals-'));
for (const name of [
  'lib/api/dev-a/drafts',
  'components/records/draft-copy',
  'lib/api/client',
  'lib/utils/decimal-display',
  'lib/utils/amount-input',
  'lib/api/dev-a/account-contracts',
  'lib/api/dev-a/transaction-contracts',
  'lib/api/dev-a/transactions',
  'lib/api/dev-a/accounts',
  'components/finance/transactions/transaction-input',
  'components/finance/transactions/transaction-copy',
  'components/finance/transactions/transaction-submission',
  'components/finance/transactions/transaction-accounts'
]) {
  const target = path.join(directory, name + '.js');
  mkdirSync(path.dirname(target), { recursive: true });
  const source = readFileSync(new URL('../src/' + name + '.ts', import.meta.url), 'utf8');
  writeFileSync(target, ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
}
after(() => rmSync(directory, { recursive: true }));

const require = createRequire(import.meta.url);
const contracts = require(path.join(directory, 'lib/api/dev-a/transaction-contracts.js'));
const { transactionsApi } = require(path.join(directory, 'lib/api/dev-a/transactions.js'));
const client = require(path.join(directory, 'lib/api/client.js'));
const { transactionEn, transactionUr } = require(path.join(directory, 'components/finance/transactions/transaction-copy.js'));

const accountId = '11111111-1111-4111-8111-111111111111';
const origId = '22222222-2222-4222-8222-222222222222';
const revId = '33333333-3333-4333-8333-333333333333';
const businessId = '44444444-4444-4444-8444-444444444444';
const userId = '55555555-5555-4555-8555-555555555555';

const origReceipt = () => ({
  id: origId,
  businessId,
  accountId,
  accountName: 'Bank Account',
  accountType: 'BANK',
  eventType: 'DIRECT_SALE_RECEIPT',
  movementDirection: 'INCREASE',
  amount: '100.00',
  currency: 'PKR',
  baseAmountPkr: '100.00',
  effectiveDate: '2026-10-14',
  description: 'Original direct sale',
  resultingBalance: '500.00',
  createdByUserId: userId,
  createdAt: '2026-10-14T10:00:00Z',
  isReversed: false,
  isReversal: false
});

const receiptReversal = () => ({
  id: revId,
  businessId,
  accountId,
  accountName: 'Bank Account',
  accountType: 'BANK',
  eventType: 'DIRECT_SALE_RECEIPT',
  movementDirection: 'DECREASE', // Inverse movement
  amount: '100.00',
  currency: 'PKR',
  baseAmountPkr: '100.00',
  effectiveDate: '2026-10-14',
  description: 'Reversal reason',
  resultingBalance: '400.00',
  createdByUserId: userId,
  createdAt: '2026-10-14T11:00:00Z',
  isReversal: true,
  reversedTransactionId: origId,
  isReversed: false,
  reversalReason: 'Wrong customer account'
});

beforeEach(() => {
  client.invalidatePrivateRequests();
  client.allowPrivateRequests();
});
afterEach(() => {
  mock.restoreAll();
  client.invalidatePrivateRequests();
});

test('reversal wire validation accepts inverted movement directions', () => {
  // Receipt reversal must have DECREASE direction
  const parsedRev = contracts.parseTransaction(receiptReversal());
  assert.equal(parsedRev.isReversal, true);
  assert.equal(parsedRev.movementDirection, 'DECREASE');
  assert.equal(parsedRev.reversedTransactionId, origId);

  // Expense reversal must have INCREASE direction
  const expReversal = {
    ...receiptReversal(),
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    movementDirection: 'INCREASE'
  };
  const parsedExp = contracts.parseTransaction(expReversal);
  assert.equal(parsedExp.isReversal, true);
  assert.equal(parsedExp.movementDirection, 'INCREASE');

  // Reversed original transaction has isReversed: true and reversedByTransactionId
  const reversedOrig = {
    ...origReceipt(),
    isReversed: true,
    reversedByTransactionId: revId
  };
  const parsedOrig = contracts.parseTransaction(reversedOrig);
  assert.equal(parsedOrig.isReversed, true);
  assert.equal(parsedOrig.reversedByTransactionId, revId);
});

test('reversal wire validation rejects non-inverted directions', () => {
  // Receipt reversal with INCREASE direction must be rejected
  const badRev = { ...receiptReversal(), movementDirection: 'INCREASE' };
  assert.throws(() => contracts.parseTransaction(badRev));

  // Expense reversal with DECREASE direction must be rejected
  const badExpRev = {
    ...receiptReversal(),
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    movementDirection: 'DECREASE'
  };
  assert.throws(() => contracts.parseTransaction(badExpRev));
});

test('transfer reversal detail validates inverted account movements', () => {
  const destId = '66666666-6666-4666-8666-666666666666';
  const transferRev = {
    id: revId,
    businessId,
    accountId,
    accountName: 'Bank Account',
    accountType: 'BANK',
    eventType: 'INTERNAL_TRANSFER',
    movementDirection: 'INCREASE',
    amount: '500.00',
    currency: 'PKR',
    baseAmountPkr: '500.00',
    effectiveDate: '2026-10-14',
    description: 'Transfer reversal',
    resultingBalance: '5000.00',
    createdByUserId: userId,
    createdAt: '2026-10-14T11:00:00Z',
    sourceAccountId: accountId,
    sourceAccountName: 'Bank Account',
    destinationAccountId: destId,
    destinationAccountName: 'Petty Cash',
    isReversal: true,
    reversedTransactionId: origId,
    reversalReason: 'Wrong amount transferred',
    movements: [
      {
        id: '77777777-7777-4777-8777-777777777777',
        accountId, // source account receives INCREASE in reversal
        movementDirection: 'INCREASE',
        amount: '500.00',
        currency: 'PKR',
        effectiveDate: '2026-10-14',
        createdAt: '2026-10-14T11:00:00Z'
      },
      {
        id: '88888888-8888-4888-8888-888888888888',
        accountId: destId, // destination account receives DECREASE in reversal
        movementDirection: 'DECREASE',
        amount: '500.00',
        currency: 'PKR',
        effectiveDate: '2026-10-14',
        createdAt: '2026-10-14T11:00:00Z'
      }
    ]
  };

  const parsed = contracts.parseTransactionDetail(transferRev, businessId, revId);
  assert.equal(parsed.isReversal, true);
  assert.equal(parsed.movements.length, 2);
});

test('transactionsApi.reverse posts to correct endpoint with Idempotency-Key and payload', async () => {
  const calls = [];
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    if (url === '/api/auth/csrf') {
      return Response.json({ token: 'test-csrf-token', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' });
    }
    if (url === `/api/finance/transactions/${origId}/reverse`) {
      return Response.json({
        ...receiptReversal(),
        movements: [
          {
            id: '99999999-9999-4999-8999-999999999999',
            accountId,
            movementDirection: 'DECREASE',
            amount: '100.00',
            currency: 'PKR',
            effectiveDate: '2026-10-14',
            createdAt: '2026-10-14T11:00:00Z'
          }
        ]
      });
    }
    return new Response(null, { status: 404 });
  });

  const result = await transactionsApi.reverse(
    origId,
    'Entry was recorded in error',
    'idem-rev-key-01',
    undefined,
    businessId
  );

  assert.equal(result.isReversal, true);
  assert.equal(result.reversedTransactionId, origId);

  // Verify fetch call was made with POST, CSRF and Idempotency-Key
  const postCall = calls.find(c => c.url === `/api/finance/transactions/${origId}/reverse`);
  assert.ok(postCall);
  assert.equal(postCall.options.method, 'POST');
  assert.equal(postCall.options.headers.get('Idempotency-Key'), 'idem-rev-key-01');
  assert.equal(postCall.options.headers.get('X-XSRF-TOKEN'), 'test-csrf-token');
  const sentBody = JSON.parse(postCall.options.body);
  assert.deepEqual(sentBody, { reason: 'Entry was recorded in error' });
});

test('transactionsApi.reverse validates input parameters before network request', async () => {
  // Invalid transaction ID
  await assert.rejects(() => transactionsApi.reverse('not-a-uuid', 'Valid reason here', 'key-1'), /Invalid transaction ID/);

  // Missing or invalid idempotency key
  await assert.rejects(() => transactionsApi.reverse(origId, 'Valid reason here', ''), /Invalid idempotency key/);
  await assert.rejects(() => transactionsApi.reverse(origId, 'Valid reason here', 'x'.repeat(101)), /Invalid idempotency key/);

  // Reason too short (< 5 chars)
  await assert.rejects(() => transactionsApi.reverse(origId, 'Four', 'key-1'), /Reason must be between 5 and 255/);

  // Reason too long (> 255 chars)
  await assert.rejects(() => transactionsApi.reverse(origId, 'x'.repeat(256), 'key-1'), /Reason must be between 5 and 255/);
});

test('reversal copy keys are fully defined in English and Urdu', () => {
  const reversalKeys = [
    'reverseEntry',
    'reverseTitle',
    'reverseNotice',
    'reverseReason',
    'reverseReasonHelp',
    'reverseConfirm',
    'reverseSubmit',
    'reversing',
    'reversedBadge',
    'reversalBadge',
    'reversedBy',
    'reversalOf',
    'reversalReasonLabel',
    'viewOriginal',
    'viewReversal',
    'alreadyReversed',
    'cannotReverseReversal',
    'reverseSuccess',
    'reverseImpactReceipt',
    'reverseImpactExpense',
    'reverseImpactTransfer'
  ];

  for (const key of reversalKeys) {
    assert.ok(transactionEn[key], `Missing English key: ${key}`);
    assert.ok(transactionUr[key], `Missing Urdu key: ${key}`);
  }
});
