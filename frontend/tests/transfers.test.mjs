import assert from 'node:assert/strict';
import { after, mock, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-transfers-'));
const filesToTranspile = [
  'lib/api/client',
  'lib/utils/decimal-display',
  'lib/utils/amount-input',
  'lib/api/dev-a/account-contracts',
  'lib/api/dev-a/transaction-contracts',
  'lib/api/dev-a/transaction-totals',
  'lib/api/dev-a/transactions',
  'lib/api/dev-a/transfers',
  'components/finance/transactions/transaction-input',
  'components/finance/transfers/transfer-copy',
  'components/finance/transfers/transfer-input',
  'components/finance/transfers/transfer-submission'
];

for (const name of filesToTranspile) {
  const target = path.join(directory, name + '.js');
  mkdirSync(path.dirname(target), { recursive: true });
  const source = readFileSync(new URL('../src/' + name + '.ts', import.meta.url), 'utf8');
  writeFileSync(target, ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
}

after(() => rmSync(directory, { recursive: true }));
const require = createRequire(import.meta.url);

const client = require(path.join(directory, 'lib/api/client.js'));
const input = require(path.join(directory, 'components/finance/transfers/transfer-input.js'));
const contracts = require(path.join(directory, 'lib/api/dev-a/transaction-contracts.js'));
const transfersApiModule = require(path.join(directory, 'lib/api/dev-a/transfers.js'));
const { transfersApi, validateTransferPayload } = transfersApiModule;
const { TransferSubmission, TransferBlocked, canPresentTransfer, transferFailure, transferFieldErrors } = require(path.join(directory, 'components/finance/transfers/transfer-submission.js'));
const { transferEn, transferUr } = require(path.join(directory, 'components/finance/transfers/transfer-copy.js'));

const account1 = { id: '11111111-1111-4111-8111-111111111111', accountName: 'Meezan Bank', status: 'ACTIVE', balanceStatus: 'AVAILABLE', currency: 'PKR', currencyDecimals: 2, openingDate: '2026-10-01' };
const account2 = { id: '22222222-2222-4222-8222-222222222222', accountName: 'Petty Cash', status: 'ACTIVE', balanceStatus: 'AVAILABLE', currency: 'PKR', currencyDecimals: 2, openingDate: '2026-10-05' };

const validDraft = () => ({
  sourceAccountId: account1.id,
  destinationAccountId: account2.id,
  amountText: '5000.00',
  dateText: '2026-10-14',
  descriptionText: 'Replenish petty cash',
  confirmed: true
});

test('bilingual transfer copy is complete and keys match', () => {
  assert.deepEqual(Object.keys(transferEn), Object.keys(transferUr));
  for (const c of [transferEn, transferUr]) {
    assert.ok(c.title);
    assert.ok(c.moveMoney);
    assert.ok(c.fromAccount);
    assert.ok(c.toAccount);
    assert.ok(c.amount);
    assert.ok(c.sameAccount);
    assert.ok(c.positiveAmount);
    assert.ok(c.validDate);
    assert.ok(c.cashFailure);
    assert.ok(typeof c.movementSummary === 'function');
    const msg = c.movementSummary('5,000.00', 'Meezan Bank', 'Petty Cash');
    assert.ok(msg.includes('5,000.00'));
    assert.ok(msg.includes('Meezan Bank'));
    assert.ok(msg.includes('Petty Cash'));
  }
});

test('transfer input validates identical source and destination accounts', () => {
  const draft = { ...validDraft(), destinationAccountId: account1.id };
  const errors = input.validateTransferInput(draft, [account1, account2], 255, '2026-10-14');
  assert.equal(errors.destinationAccountId, 'sameAccount');
});

test('transfer input requires active PKR accounts', () => {
  const inactive = { ...account2, status: 'INACTIVE' };
  const errors = input.validateTransferInput(validDraft(), [account1, inactive], 255, '2026-10-14');
  assert.equal(errors.destinationAccountId, 'accountRejected');

  const usdAccount = { ...account2, currency: 'USD' };
  const usdErrors = input.validateTransferInput(validDraft(), [account1, usdAccount], 255, '2026-10-14');
  assert.equal(usdErrors.destinationAccountId, 'accountRejected');
});

test('transfer input enforces chronological opening date bounds and Karachi today', () => {
  // Before account 2 opening date (2026-10-05)
  const tooEarly = { ...validDraft(), dateText: '2026-10-02' };
  const earlyErrors = input.validateTransferInput(tooEarly, [account1, account2], 255, '2026-10-14');
  assert.equal(earlyErrors.dateText, 'validDate');

  // Future date
  const future = { ...validDraft(), dateText: '2026-10-15' };
  const futureErrors = input.validateTransferInput(future, [account1, account2], 255, '2026-10-14');
  assert.equal(futureErrors.dateText, 'validDate');

  // Valid date
  const validErrors = input.validateTransferInput(validDraft(), [account1, account2], 255, '2026-10-14');
  assert.deepEqual(validErrors, {});
});

test('transfer input requires positive canonical-compatible amount and confirmation', () => {
  for (const badAmount of ['0.00', '-50.00', 'abc', '10.5', '1e3']) {
    const errors = input.validateTransferInput({ ...validDraft(), amountText: badAmount }, [account1, account2], 255, '2026-10-14');
    assert.equal(errors.amountText, 'positiveAmount');
  }

  const unconfirmed = { ...validDraft(), confirmed: false };
  assert.equal(input.validateTransferInput(unconfirmed, [account1, account2], 255, '2026-10-14').confirmed, 'confirmRequired');

  const longDesc = { ...validDraft(), descriptionText: 'a'.repeat(256) };
  assert.equal(input.validateTransferInput(longDesc, [account1, account2], 255, '2026-10-14').descriptionText, 'descriptionLimit');
});

test('validateTransferPayload produces frozen allowlist', () => {
  const payload = validateTransferPayload({
    sourceAccountId: account1.id,
    destinationAccountId: account2.id,
    amount: '5000.00',
    effectiveDate: '2026-10-14',
    description: '  Trimming check  '
  });
  assert.equal(Object.isFrozen(payload), true);
  assert.equal(payload.description, 'Trimming check');
  assert.equal(payload.sourceAccountId, account1.id);
  assert.equal(payload.destinationAccountId, account2.id);
  assert.equal(payload.amount, '5000.00');

  // Same account throws
  assert.throws(() => validateTransferPayload({
    sourceAccountId: account1.id,
    destinationAccountId: account1.id,
    amount: '5000.00',
    effectiveDate: '2026-10-14'
  }));
});

test('presentation permits OWNER and ACCOUNTANT only', () => {
  assert.equal(canPresentTransfer({ role: 'OWNER', membershipStatus: 'ACTIVE' }), true);
  assert.equal(canPresentTransfer({ role: 'ACCOUNTANT', membershipStatus: 'ACTIVE' }), true);
  assert.equal(canPresentTransfer({ role: 'MANAGER', membershipStatus: 'ACTIVE' }), false);
  assert.equal(canPresentTransfer({ role: 'VIEWER', membershipStatus: 'ACTIVE' }), false);
  assert.equal(canPresentTransfer({ role: 'OWNER', membershipStatus: 'INVITED' }), false);
  assert.equal(canPresentTransfer(null), false);
});

test('parseTransactionDetail handles INTERNAL_TRANSFER with dual movements', () => {
  const rawTransfer = {
    id: '33333333-3333-4333-8333-333333333333',
    businessId: '44444444-4444-4444-8444-444444444444',
    accountId: account1.id,
    accountName: 'Meezan Bank',
    accountType: 'BANK',
    eventType: 'INTERNAL_TRANSFER',
    movementDirection: 'DECREASE',
    amount: '5000.00',
    currency: 'PKR',
    baseAmountPkr: '5000.00',
    effectiveDate: '2026-10-14',
    description: 'Petty cash refill',
    resultingBalance: '45000.00',
    createdByUserId: '55555555-5555-4555-8555-555555555555',
    createdAt: '2026-10-14T12:00:00Z',
    sourceAccountId: account1.id,
    sourceAccountName: 'Meezan Bank',
    destinationAccountId: account2.id,
    destinationAccountName: 'Petty Cash',
    movements: [
      {
        id: '66666666-6666-4666-8666-666666666666',
        accountId: account1.id,
        movementDirection: 'DECREASE',
        amount: '5000.00',
        currency: 'PKR',
        effectiveDate: '2026-10-14',
        createdAt: '2026-10-14T12:00:00Z'
      },
      {
        id: '77777777-7777-4777-8777-777777777777',
        accountId: account2.id,
        movementDirection: 'INCREASE',
        amount: '5000.00',
        currency: 'PKR',
        effectiveDate: '2026-10-14',
        createdAt: '2026-10-14T12:00:00Z'
      }
    ]
  };

  const parsed = contracts.parseTransactionDetail(rawTransfer, rawTransfer.businessId, rawTransfer.id);
  assert.equal(parsed.eventType, 'INTERNAL_TRANSFER');
  assert.equal(parsed.movements.length, 2);
  assert.equal(parsed.sourceAccountId, account1.id);
  assert.equal(parsed.destinationAccountId, account2.id);
  assert.equal(parsed.sourceAccountName, 'Meezan Bank');
  assert.equal(parsed.destinationAccountName, 'Petty Cash');
});

test('TransferSubmission manages explicit retry with same key and blocks changed payload', async () => {
  const sent = [];
  const submission = new TransferSubmission(
    async (payload, key) => {
      sent.push({ payload, key });
      if (sent.length === 1) throw new client.ApiError(0, 'Network lost');
      return { id: 'mock-transfer-id', eventType: 'INTERNAL_TRANSFER' };
    },
    () => 'test-idempotency-key'
  );

  const draft = validDraft();
  await assert.rejects(submission.submit(draft));
  assert.equal(submission.phase, 'uncertain');

  // Attempting to change amount while uncertain is blocked
  await assert.rejects(
    submission.submit({ ...draft, amountText: '6000.00' }),
    err => err instanceof TransferBlocked
  );
  assert.equal(sent.length, 1);

  // Retrying with identical draft reuses key
  const result = await submission.submit(draft);
  assert.equal(sent.length, 2);
  assert.equal(sent[0].key, sent[1].key);
  assert.equal(sent[1].payload.amount, '5000.00');
  assert.equal(result.id, 'mock-transfer-id');
  assert.equal(submission.phase, 'complete');
});

test('transfersApi.post validates response and calls API', async () => {
  client.invalidatePrivateRequests();
  client.allowPrivateRequests();
  const rawTransfer = {
    id: '33333333-3333-4333-8333-333333333333',
    businessId: '44444444-4444-4444-8444-444444444444',
    accountId: account1.id,
    accountName: 'Meezan Bank',
    accountType: 'BANK',
    eventType: 'INTERNAL_TRANSFER',
    movementDirection: 'DECREASE',
    amount: '5000.00',
    currency: 'PKR',
    baseAmountPkr: '5000.00',
    effectiveDate: '2026-10-14',
    description: 'Petty cash refill',
    resultingBalance: '45000.00',
    createdByUserId: '55555555-5555-4555-8555-555555555555',
    createdAt: '2026-10-14T12:00:00Z',
    sourceAccountId: account1.id,
    sourceAccountName: 'Meezan Bank',
    destinationAccountId: account2.id,
    destinationAccountName: 'Petty Cash',
    movements: [
      { id: '66666666-6666-4666-8666-666666666666', accountId: account1.id, movementDirection: 'DECREASE', amount: '5000.00', currency: 'PKR', effectiveDate: '2026-10-14', createdAt: '2026-10-14T12:00:00Z' },
      { id: '77777777-7777-4777-8777-777777777777', accountId: account2.id, movementDirection: 'INCREASE', amount: '5000.00', currency: 'PKR', effectiveDate: '2026-10-14', createdAt: '2026-10-14T12:00:00Z' }
    ]
  };

  const calls = [];
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    if (url === '/api/auth/csrf') return Response.json({ token: 'test-token', headerName: 'X-XSRF-TOKEN' });
    return Response.json(rawTransfer);
  });

  const res = await transfersApi.post(
    { sourceAccountId: account1.id, destinationAccountId: account2.id, amount: '5000.00', effectiveDate: '2026-10-14' },
    'key-123'
  );
  assert.equal(res.id, rawTransfer.id);
  assert.equal(calls.some(c => c.url === '/api/finance/transfers' && c.options.method === 'POST'), true);
  mock.restoreAll();
});

test('transferFailure and transferFieldErrors map API errors to UI keys', () => {
  assert.equal(transferFailure(new client.ApiError(400, 'Bad request')), 'validation');
  assert.equal(transferFailure(new client.ApiError(404, 'Not found')), 'accountRejected');
  assert.equal(transferFailure(new client.ApiError(409, 'Conflict')), 'conflict');
  assert.equal(transferFailure(new client.ApiError(403, 'Forbidden')), 'forbidden');
  assert.equal(transferFailure(new client.ApiError(401, 'Unauthorized')), 'session');
  assert.equal(transferFailure(new Error('Unknown')), 'uncertain');

  const fields = transferFieldErrors(new client.ApiError(400, 'Validation', {
    sourceAccountId: 'Invalid',
    destinationAccountId: 'Invalid',
    amount: 'Bad amount',
    effectiveDate: 'Bad date',
    description: 'Too long',
    cashBalance: 'Negative'
  }));
  assert.equal(fields.sourceAccountId, 'accountRejected');
  assert.equal(fields.destinationAccountId, 'accountRejected');
  assert.equal(fields.amountText, 'cashFailure');
  assert.equal(fields.dateText, 'validDate');
  assert.equal(fields.descriptionText, 'descriptionLimit');
});
