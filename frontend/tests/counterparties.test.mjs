import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-counterparties-'));
for (const name of [
  'lib/api/dev-a/counterparties',
  'components/records/counterparties/counterparty-copy',
  'lib/api/dev-a/transaction-contracts',
  'lib/api/dev-a/account-contracts',
  'lib/api/dev-a/drafts',
  'lib/api/client',
  'lib/utils/decimal-display',
  'lib/utils/amount-input',
  'components/finance/transactions/transaction-input',
  'components/records/draft-entry',
]) {
  const target = path.join(directory, name + '.js');
  mkdirSync(path.dirname(target), { recursive: true });
  const source = readFileSync(new URL('../src/' + name + '.ts', import.meta.url), 'utf8');
  writeFileSync(
    target,
    ts.transpileModule(source, {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    }).outputText
  );
}
after(() => rmSync(directory, { recursive: true }));

const require = createRequire(import.meta.url);
const cp = require(path.join(directory, 'lib/api/dev-a/counterparties.js'));
const { counterpartyEn, counterpartyUr } = require(
  path.join(directory, 'components/records/counterparties/counterparty-copy.js')
);
const txContracts = require(path.join(directory, 'lib/api/dev-a/transaction-contracts.js'));
const draftContracts = require(path.join(directory, 'lib/api/dev-a/drafts.js'));

const validId = '11111111-1111-4111-8111-111111111111';
const validBusinessId = '22222222-2222-4222-8222-222222222222';
const validAccountId = '33333333-3333-4333-8333-333333333333';
const validUserId = '44444444-4444-4444-8444-444444444444';

const sampleSummary = () => ({
  id: validId,
  displayName: 'Tariq Wholesale Traders',
  type: 'CUSTOMER',
  status: 'ACTIVE',
  email: 'tariq@traders.test',
  phone: '+923001234567',
  address: 'Shop 10, Wholesale Market, Karachi',
  taxRegistrationNumber: '1234567-8',
  version: 0,
  createdAt: '2026-10-18T10:00:00Z',
  updatedAt: '2026-10-18T10:00:00Z',
});

const sampleDetail = () => ({
  ...sampleSummary(),
  businessId: validBusinessId,
  notes: 'Key retail customer for bulk rice orders.',
});

test('parseCounterpartySummary validates and normalizes customer summary', () => {
  const item = sampleSummary();
  const parsed = cp.parseCounterpartySummary(item);
  assert.equal(parsed.id, validId);
  assert.equal(parsed.displayName, 'Tariq Wholesale Traders');
  assert.equal(parsed.type, 'CUSTOMER');
  assert.equal(parsed.status, 'ACTIVE');
  assert.equal(parsed.email, 'tariq@traders.test');
  assert.equal(parsed.phone, '+923001234567');
  assert.equal(parsed.version, 0);
});

test('parseCounterpartySummary handles Urdu / Unicode display name', () => {
  const item = {
    ...sampleSummary(),
    displayName: 'طارق ہول سیل ٹریڈرز',
    type: 'BOTH',
  };
  const parsed = cp.parseCounterpartySummary(item);
  assert.equal(parsed.displayName, 'طارق ہول سیل ٹریڈرز');
  assert.equal(parsed.type, 'BOTH');
});

test('parseCounterpartySummary rejects invalid or missing fields', () => {
  assert.throws(() => cp.parseCounterpartySummary(null), cp.InvalidCounterpartyResponse);
  assert.throws(() => cp.parseCounterpartySummary({ ...sampleSummary(), id: 'not-a-uuid' }));
  assert.throws(() => cp.parseCounterpartySummary({ ...sampleSummary(), displayName: '' }));
  assert.throws(() => cp.parseCounterpartySummary({ ...sampleSummary(), displayName: '   ' }));
  assert.throws(() => cp.parseCounterpartySummary({ ...sampleSummary(), type: 'INVALID' }));
  assert.throws(() => cp.parseCounterpartySummary({ ...sampleSummary(), status: 'DELETED' }));
  assert.throws(() => cp.parseCounterpartySummary({ ...sampleSummary(), version: -1 }));
});

test('parseCounterpartyDetail validates businessId and notes', () => {
  const detail = sampleDetail();
  const parsed = cp.parseCounterpartyDetail(detail);
  assert.equal(parsed.businessId, validBusinessId);
  assert.equal(parsed.notes, 'Key retail customer for bulk rice orders.');

  assert.throws(
    () => cp.parseCounterpartyDetail({ ...detail, businessId: 'invalid' }),
    cp.InvalidCounterpartyResponse
  );
});

test('parseCounterpartyList validates paginated response', () => {
  const list = {
    content: [sampleSummary()],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };
  const parsed = cp.parseCounterpartyList(list);
  assert.equal(parsed.content.length, 1);
  assert.equal(parsed.totalElements, 1);
  assert.equal(parsed.page, 0);

  assert.throws(
    () => cp.parseCounterpartyList({ ...list, page: -1 }),
    cp.InvalidCounterpartyResponse
  );
  assert.throws(
    () => cp.parseCounterpartyList({ ...list, content: 'not-array' }),
    cp.InvalidCounterpartyResponse
  );
});

test('counterpartyQueryString serializes filters correctly', () => {
  assert.equal(cp.counterpartyQueryString(), '');
  assert.equal(
    cp.counterpartyQueryString({ type: 'CUSTOMER', status: 'ACTIVE' }),
    '?type=CUSTOMER&status=ACTIVE'
  );
  assert.equal(
    cp.counterpartyQueryString({ type: 'ALL', status: 'ARCHIVED', q: 'Tariq', page: 1, size: 50 }),
    '?status=ARCHIVED&q=Tariq&page=1&size=50'
  );
});

test('Bilingual copy has identical keys in counterpartyEn and counterpartyUr', () => {
  const enKeys = Object.keys(counterpartyEn).sort();
  const urKeys = Object.keys(counterpartyUr).sort();
  assert.deepEqual(enKeys, urKeys);
});

test('Transaction contracts preserve counterpartyId and counterpartyName', () => {
  const tx = {
    id: validId,
    businessId: validBusinessId,
    accountId: validAccountId,
    accountName: 'Main Cash Account',
    accountType: 'CASH',
    eventType: 'DIRECT_SALE_RECEIPT',
    movementDirection: 'INCREASE',
    amount: '500.00',
    currency: 'PKR',
    baseAmountPkr: '500.00',
    effectiveDate: '2026-10-18',
    description: 'Sale to customer',
    resultingBalance: '1500.00',
    createdByUserId: validUserId,
    createdAt: '2026-10-18T10:00:00Z',
    counterpartyId: validId,
    counterpartyName: 'Tariq Traders',
  };

  const parsed = txContracts.parseTransaction(tx);
  assert.equal(parsed.counterpartyId, validId);
  assert.equal(parsed.counterpartyName, 'Tariq Traders');

  const payload = txContracts.transactionPostPayload({
    selectedAccount: validAccountId,
    event: 'DIRECT_SALE_RECEIPT',
    amountText: '500.00',
    dateText: '2026-10-18',
    descriptionText: 'Sale to customer',
    confirmed: true,
    counterpartyId: validId,
  });
  assert.equal(payload.counterpartyId, validId);
});

test('Draft contracts preserve counterpartyId and counterpartyName', () => {
  const draft = {
    id: validId,
    accountId: validAccountId,
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    effectiveDate: '2026-10-18',
    amountInput: '750.00',
    description: 'Supplier payment',
    status: 'DRAFT',
    readyToRecord: true,
    version: 0,
    recordedTransactionId: null,
    createdById: validUserId,
    updatedById: validUserId,
    createdAt: '2026-10-18T10:00:00Z',
    updatedAt: '2026-10-18T10:00:00Z',
    counterpartyId: validId,
    counterpartyName: 'Ali Supplier',
  };

  const parsed = draftContracts.parseDraft(draft);
  assert.equal(parsed.counterpartyId, validId);
  assert.equal(parsed.counterpartyName, 'Ali Supplier');

  const entry = draftContracts.draftEntry(parsed);
  assert.equal(entry.counterpartyId, validId);

  const payload = draftContracts.draftPayload(entry);
  assert.equal(payload.counterpartyId, validId);
});
