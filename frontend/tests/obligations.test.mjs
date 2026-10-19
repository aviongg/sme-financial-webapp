import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-obligations-'));
for (const name of [
  'lib/api/dev-a/obligations',
  'components/records/obligations/obligation-copy',
  'lib/api/dev-a/transaction-contracts',
  'lib/api/dev-a/account-contracts',
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
const ob = require(path.join(directory, 'lib/api/dev-a/obligations.js'));
const { obligationEn, obligationUr } = require(
  path.join(directory, 'components/records/obligations/obligation-copy.js')
);

const validId = '11111111-1111-4111-8111-111111111111';
const validBusinessId = '22222222-2222-4222-8222-222222222222';
const validCounterpartyId = '33333333-3333-4333-8333-333333333333';
const validTxId = '44444444-4444-4444-8444-444444444444';
const validAllocId = '55555555-5555-4555-8555-555555555555';

const sampleSummary = () => ({
  id: validId,
  businessId: validBusinessId,
  counterpartyId: validCounterpartyId,
  counterpartyName: 'Tariq Wholesale Traders',
  counterpartyType: 'CUSTOMER',
  direction: 'RECEIVABLE',
  sourceType: 'CUSTOMER_INVOICE',
  sourceId: 'INV-2026-001',
  referenceNumber: 'REF-001',
  originalAmount: '100000.00',
  paidAmount: '40000.00',
  outstandingAmount: '60000.00',
  currency: 'PKR',
  issueDate: '2026-10-15',
  dueDate: '2026-11-15',
  status: 'OPEN',
  settlementStatus: 'PARTIALLY_PAID',
  agingBucket: 'NOT_YET_DUE',
  daysOverdue: null,
  description: 'Initial rice shipment invoice',
  createdAt: '2026-10-15T10:00:00Z',
});

const sampleDetail = () => ({
  ...sampleSummary(),
  voidDate: null,
  voidReason: null,
  allocations: [
    {
      id: validAllocId,
      transactionId: validTxId,
      amount: '40000.00',
      effectiveDate: '2026-10-16',
      status: 'ALLOCATED',
      reversalTransactionId: null,
      createdAt: '2026-10-16T12:00:00Z',
    },
  ],
});

test('Bilingual copy keys have exact 1:1 symmetry', () => {
  const enKeys = Object.keys(obligationEn).sort();
  const urKeys = Object.keys(obligationUr).sort();
  assert.deepEqual(enKeys, urKeys, 'English and Urdu copy objects must contain identical keys');
  for (const key of enKeys) {
    assert.ok(obligationEn[key], `obligationEn[${key}] must not be empty`);
    assert.ok(obligationUr[key], `obligationUr[${key}] must not be empty`);
  }
});

test('Aging buckets contain all 7 canonical buckets', () => {
  assert.equal(ob.agingBuckets.length, 7);
  assert.deepEqual(ob.agingBuckets, [
    'UNKNOWN_DUE_DATE',
    'NOT_YET_DUE',
    'DUE_TODAY',
    'OVERDUE_1_30',
    'OVERDUE_31_60',
    'OVERDUE_61_90',
    'OVERDUE_91_PLUS',
  ]);
});

test('parseObligationSummary validates and normalizes summary response', () => {
  const item = sampleSummary();
  const parsed = ob.parseObligationSummary(item);
  assert.equal(parsed.id, validId);
  assert.equal(parsed.businessId, validBusinessId);
  assert.equal(parsed.direction, 'RECEIVABLE');
  assert.equal(parsed.counterpartyName, 'Tariq Wholesale Traders');
  assert.equal(parsed.originalAmount, '100000.00');
  assert.equal(parsed.paidAmount, '40000.00');
  assert.equal(parsed.outstandingAmount, '60000.00');
  assert.equal(parsed.currency, 'PKR');
  assert.equal(parsed.status, 'OPEN');
  assert.equal(parsed.settlementStatus, 'PARTIALLY_PAID');
});

test('parseObligationSummary rejects invalid direction or missing amounts', () => {
  assert.throws(() => ob.parseObligationSummary(null), ob.InvalidObligationResponse);
  assert.throws(() => ob.parseObligationSummary({ ...sampleSummary(), direction: 'INVALID' }), ob.InvalidObligationResponse);
  assert.throws(() => ob.parseObligationSummary({ ...sampleSummary(), originalAmount: undefined }), ob.InvalidObligationResponse);
  assert.throws(() => ob.parseObligationSummary({ ...sampleSummary(), currency: 'USD' }), ob.InvalidObligationResponse);
});

test('parseObligationDetail validates detail response including allocations', () => {
  const item = sampleDetail();
  const parsed = ob.parseObligationDetail(item);
  assert.equal(parsed.id, validId);
  assert.equal(parsed.businessId, validBusinessId);
  assert.equal(parsed.allocations.length, 1);
  assert.equal(parsed.allocations[0].id, validAllocId);
  assert.equal(parsed.allocations[0].amount, '40000.00');
  assert.equal(parsed.allocations[0].status, 'ALLOCATED');
});

test('parseObligationDetail validates voided obligation metadata', () => {
  const item = {
    ...sampleDetail(),
    status: 'VOIDED',
    voidReason: 'Duplicate invoice entered by error',
    voidDate: '2026-10-18',
  };
  const parsed = ob.parseObligationDetail(item);
  assert.equal(parsed.status, 'VOIDED');
  assert.equal(parsed.voidReason, 'Duplicate invoice entered by error');
  assert.equal(parsed.voidDate, '2026-10-18');
});

test('parseObligationList validates paginated response', () => {
  const listPayload = {
    items: [sampleSummary()],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };
  const parsed = ob.parseObligationList(listPayload);
  assert.equal(parsed.page, 0);
  assert.equal(parsed.size, 20);
  assert.equal(parsed.totalElements, 1);
  assert.equal(parsed.items.length, 1);
  assert.equal(parsed.items[0].id, validId);
});

test('obligationQueryString generates proper query parameters', () => {
  const q1 = ob.obligationQueryString({
    direction: 'PAYABLE',
    settlementStatus: 'UNPAID',
    search: 'Lahore Flour Mills',
    page: 0,
    size: 20,
  });
  assert.ok(q1.includes('direction=PAYABLE'));
  assert.ok(q1.includes('settlementStatus=UNPAID'));
  assert.ok(q1.includes('search=Lahore+Flour+Mills') || q1.includes('search=Lahore%20Flour%20Mills'));
  assert.ok(q1.includes('page=0'));
  assert.ok(q1.includes('size=20'));
});
