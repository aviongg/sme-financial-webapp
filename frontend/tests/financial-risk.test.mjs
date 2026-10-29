import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-risk-'));
for (const name of [
  'lib/api/dev-a/financial-risk',
  'components/records/risk/risk-copy',
  'components/records/records-copy',
  'lib/api/client',
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
const { riskEn, riskUr } = require(path.join(directory, 'components/records/risk/risk-copy.js'));
const { recordsEn, recordsUr } = require(path.join(directory, 'components/records/records-copy.js'));
const { financialRiskApi } = require(path.join(directory, 'lib/api/dev-a/financial-risk.js'));

test('Financial Risk copy has 1:1 English/Urdu key symmetry', () => {
  const enKeys = Object.keys(riskEn).sort();
  const urKeys = Object.keys(riskUr).sort();
  assert.deepEqual(enKeys, urKeys, 'Risk copy keys must be identical across languages');
  for (const key of enKeys) {
    assert.ok(riskEn[key], `riskEn[${key}] must not be empty`);
    assert.ok(riskUr[key], `riskUr[${key}] must not be empty`);
  }
});

test('Records copy contains risks tab key in both EN and UR', () => {
  assert.equal(recordsEn.risks, 'Risk Intelligence');
  assert.equal(recordsUr.risks, 'مالیاتی خطرات');
});

test('Risk copy covers all 8 risk categories', () => {
  const expectedCategories = [
    'cat_LIQUIDITY_FORECAST',
    'cat_UPCOMING_PAYMENTS',
    'cat_RECEIVABLES_COLLECTION',
    'cat_COUNTERPARTY_CONCENTRATION',
    'cat_RECURRING_OPERATIONS',
    'cat_CASH_POSITION',
    'cat_DATA_INTEGRITY',
    'cat_BANK_RECONCILIATION',
  ];
  for (const cat of expectedCategories) {
    assert.ok(riskEn[cat], `riskEn must define category label for ${cat}`);
    assert.ok(riskUr[cat], `riskUr must define Urdu label for ${cat}`);
  }
});

test('Risk copy covers all severities and urgencies', () => {
  for (const sev of ['sev_CRITICAL', 'sev_HIGH', 'sev_MEDIUM', 'sev_LOW', 'sev_NOT_ASSESSED']) {
    assert.ok(riskEn[sev], `riskEn must define severity label for ${sev}`);
    assert.ok(riskUr[sev], `riskUr must define Urdu label for ${sev}`);
  }
  for (const urg of ['urg_IMMEDIATE', 'urg_SHORT_TERM', 'urg_MEDIUM_TERM', 'urg_MONITOR']) {
    assert.ok(riskEn[urg], `riskEn must define urgency label for ${urg}`);
    assert.ok(riskUr[urg], `riskUr must define Urdu label for ${urg}`);
  }
});

test('Risk copy contains disclaimer and data completeness notices', () => {
  assert.match(
    riskEn.disclaimer,
    /Disclaimer: Risk intelligence evaluations are deterministic calculations derived from current ledger entries/,
    'EN disclaimer must be present'
  );
  assert.ok(riskUr.disclaimer.length > 20, 'UR disclaimer must be present');
  assert.match(
    riskEn.dataCompletenessNotice,
    /Assessment accuracy is constrained by pending reviews or unscheduled obligations\./,
    'EN data completeness notice must be present'
  );
  assert.ok(riskUr.dataCompletenessNotice.length > 20, 'UR data completeness notice must be present');
});

test('Financial Risk API client exposes expected functions', () => {
  assert.equal(typeof financialRiskApi.getAssessment, 'function');
  assert.equal(typeof financialRiskApi.getInsights, 'function');
  assert.equal(typeof financialRiskApi.getSummary, 'function');
});
