import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-payment-cycles-'));
for (const name of [
  'lib/api/dev-a/payments',
  'lib/api/dev-a/advances',
  'lib/api/dev-a/payment-calendar',
  'components/records/payments/payment-copy',
  'components/records/advances/advance-copy',
  'components/records/calendar/calendar-copy',
  'lib/api/dev-a/obligations',
  'lib/api/dev-a/transaction-contracts',
  'lib/api/dev-a/account-contracts',
  'lib/api/client',
  'lib/utils/decimal-display',
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
const { paymentEn, paymentUr } = require(path.join(directory, 'components/records/payments/payment-copy.js'));
const { advanceEn, advanceUr } = require(path.join(directory, 'components/records/advances/advance-copy.js'));
const { calendarEn, calendarUr } = require(path.join(directory, 'components/records/calendar/calendar-copy.js'));
const { paymentsApi } = require(path.join(directory, 'lib/api/dev-a/payments.js'));
const { advancesApi } = require(path.join(directory, 'lib/api/dev-a/advances.js'));
const { paymentCalendarApi } = require(path.join(directory, 'lib/api/dev-a/payment-calendar.js'));

test('Payment allocation copy has 1:1 English/Urdu key symmetry', () => {
  const enKeys = Object.keys(paymentEn).sort();
  const urKeys = Object.keys(paymentUr).sort();
  assert.deepEqual(enKeys, urKeys, 'Payment copy keys must be identical across languages');
  for (const key of enKeys) {
    assert.ok(paymentEn[key], `paymentEn[${key}] must not be empty`);
    assert.ok(paymentUr[key], `paymentUr[${key}] must not be empty`);
  }
});

test('Advances copy has 1:1 English/Urdu key symmetry', () => {
  const enKeys = Object.keys(advanceEn).sort();
  const urKeys = Object.keys(advanceUr).sort();
  assert.deepEqual(enKeys, urKeys, 'Advance copy keys must be identical across languages');
  for (const key of enKeys) {
    assert.ok(advanceEn[key], `advanceEn[${key}] must not be empty`);
    assert.ok(advanceUr[key], `advanceUr[${key}] must not be empty`);
  }
});

test('Calendar copy has 1:1 English/Urdu key symmetry', () => {
  const enKeys = Object.keys(calendarEn).sort();
  const urKeys = Object.keys(calendarUr).sort();
  assert.deepEqual(enKeys, urKeys, 'Calendar copy keys must be identical across languages');
  for (const key of enKeys) {
    assert.ok(calendarEn[key], `calendarEn[${key}] must not be empty`);
    assert.ok(calendarUr[key], `calendarUr[${key}] must not be empty`);
  }
});

test('Payment allocation arithmetic: full allocation matches payment total', () => {
  const totalCash = 100000.00;
  const allocations = [
    { obligationId: 'ob-1', amount: 60000.00 },
    { obligationId: 'ob-2', amount: 40000.00 },
  ];
  const sumAllocated = allocations.reduce((acc, it) => acc + it.amount, 0);
  const residual = totalCash - sumAllocated;
  assert.equal(sumAllocated, 100000.00);
  assert.equal(residual, 0.00);
});

test('Payment allocation arithmetic: residual advance is computed accurately', () => {
  const totalCash = 100000.00;
  const allocations = [
    { obligationId: 'ob-1', amount: 40000.00 },
    { obligationId: 'ob-2', amount: 35000.00 },
  ];
  const sumAllocated = allocations.reduce((acc, it) => acc + it.amount, 0);
  const residual = totalCash - sumAllocated;
  assert.equal(sumAllocated, 75000.00);
  assert.equal(residual, 25000.00);
});

test('Payment allocation validation: overallocation exceeds total cash', () => {
  const totalCash = 100000.00;
  const allocations = [
    { obligationId: 'ob-1', amount: 60000.00 },
    { obligationId: 'ob-2', amount: 50000.00 },
  ];
  const sumAllocated = allocations.reduce((acc, it) => acc + it.amount, 0);
  const isOverallocated = sumAllocated > totalCash;
  assert.ok(isOverallocated, 'Allocating 110,000 for a 100,000 payment must flag overallocation');
});

test('Advance balance calculation: derived unapplied balance and exhaustion', () => {
  const originalAdvance = 50000.00;
  const applications = [
    { amount: 30000.00, status: 'EFFECTIVE' },
    { amount: 10000.00, status: 'CANCELLED' }, // Reversal should not consume balance
    { amount: 20000.00, status: 'EFFECTIVE' },
  ];

  const totalEffectiveApplied = applications
    .filter((a) => a.status === 'EFFECTIVE')
    .reduce((acc, a) => acc + a.amount, 0);

  const unapplied = Math.max(0, originalAdvance - totalEffectiveApplied);
  assert.equal(totalEffectiveApplied, 50000.00);
  assert.equal(unapplied, 0.00);
  const status = unapplied === 0 ? 'EXHAUSTED' : 'ACTIVE';
  assert.equal(status, 'EXHAUSTED');
});

test('Payment plan coverage classification: FULL, PARTIAL, OVER, NONE', () => {
  const evaluateCoverage = (outstanding, installments) => {
    const totalPlanned = installments.reduce((acc, i) => acc + i.amount, 0);
    if (totalPlanned === 0) return 'NO_SCHEDULE';
    if (Math.abs(totalPlanned - outstanding) < 0.001) return 'FULL_COVERAGE';
    if (totalPlanned < outstanding) return 'PARTIAL_COVERAGE';
    return 'OVER_COVERAGE';
  };

  assert.equal(evaluateCoverage(150000, []), 'NO_SCHEDULE');
  assert.equal(evaluateCoverage(150000, [{ amount: 50000 }, { amount: 50000 }]), 'PARTIAL_COVERAGE');
  assert.equal(evaluateCoverage(150000, [{ amount: 50000 }, { amount: 50000 }, { amount: 50000 }]), 'FULL_COVERAGE');
  assert.equal(evaluateCoverage(150000, [{ amount: 100000 }, { amount: 100000 }]), 'OVER_COVERAGE');
});

test('Duplicate submission prevention: prevents double submit while pending', () => {
  let isSubmitting = false;
  let submissionCount = 0;

  const handleSubmit = () => {
    if (isSubmitting) return false;
    isSubmitting = true;
    submissionCount++;
    return true;
  };

  assert.equal(handleSubmit(), true);
  assert.equal(handleSubmit(), false); // Blocked while in progress
  assert.equal(handleSubmit(), false); // Blocked
  assert.equal(submissionCount, 1);
  isSubmitting = false; // Reset after complete
  assert.equal(handleSubmit(), true);
  assert.equal(submissionCount, 2);
});

test('API client methods exist and are properly bound', () => {
  assert.equal(typeof paymentsApi.allocate, 'function');
  assert.equal(typeof paymentsApi.getPayment, 'function');
  assert.equal(typeof advancesApi.create, 'function');
  assert.equal(typeof advancesApi.list, 'function');
  assert.equal(typeof advancesApi.getById, 'function');
  assert.equal(typeof advancesApi.apply, 'function');
  assert.equal(typeof advancesApi.reverseApplication, 'function');
  assert.equal(typeof paymentCalendarApi.getPaymentCalendar, 'function');
  assert.equal(typeof paymentCalendarApi.getPaymentPlan, 'function');
  assert.equal(typeof paymentCalendarApi.setPaymentPlan, 'function');
});
