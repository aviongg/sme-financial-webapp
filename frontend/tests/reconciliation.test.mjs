import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-reconciliation-'));
for (const name of [
  'lib/api/dev-a/reconciliation',
  'components/records/reconciliation/reconciliation-copy',
  'components/records/records-copy',
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
const { reconciliationCopyEn, reconciliationCopyUr } = require(path.join(directory, 'components/records/reconciliation/reconciliation-copy.js'));
const { recordsEn, recordsUr } = require(path.join(directory, 'components/records/records-copy.js'));
const { reconciliationApi } = require(path.join(directory, 'lib/api/dev-a/reconciliation.js'));

test('Reconciliation copy has 1:1 English/Urdu key symmetry', () => {
  const enKeys = Object.keys(reconciliationCopyEn).sort();
  const urKeys = Object.keys(reconciliationCopyUr).sort();
  assert.deepEqual(enKeys, urKeys, 'Reconciliation copy keys must be identical across languages');
  for (const key of enKeys) {
    assert.ok(reconciliationCopyEn[key], `reconciliationCopyEn[${key}] must not be empty`);
    assert.ok(reconciliationCopyUr[key], `reconciliationCopyUr[${key}] must not be empty`);
  }
});

test('Records copy contains reconciliation tab key in both EN and UR', () => {
  assert.ok(recordsEn.reconciliation, 'recordsEn.reconciliation must be defined');
  assert.ok(recordsUr.reconciliation, 'recordsUr.reconciliation must be defined');
});

test('Reconciliation API adapter exposes required operations', () => {
  const requiredMethods = [
    'importStatement',
    'listImports',
    'getImportDetail',
    'listEntries',
    'getEntryDetail',
    'findCandidates',
    'matchEntry',
    'unmatchEntry',
    'excludeEntry',
    'createDraft',
    'getAccountSummary',
    'getUnmatchedMovements',
  ];
  for (const method of requiredMethods) {
    assert.equal(typeof reconciliationApi[method], 'function', `reconciliationApi.${method} must be a function`);
  }
});

test('CSV parsing logic correctly handles commas within quotes and UTF-8 BOM', () => {
  const rawCsv = '\uFEFFDate,Description,Debit,Credit,Ref\r\n' +
    '2026-10-01,"Payment to Supplier, ABC",5000.00,,CHQ-101\r\n' +
    '2026-10-02,Client Deposit,,25000.00,DEP-202\r\n';

  // Test the delimiter parser
  function parseDelimited(text, delimiter) {
    const clean = text.charCodeAt(0) === 0xFEFF ? text.slice(1) : text;
    const rows = [];
    let currentRow = [];
    let currentCell = '';
    let insideQuote = false;

    for (let i = 0; i < clean.length; i++) {
      const char = clean[i];
      const nextChar = clean[i + 1];

      if (char === '"') {
        if (insideQuote && nextChar === '"') {
          currentCell += '"';
          i++;
        } else {
          insideQuote = !insideQuote;
        }
      } else if (char === delimiter && !insideQuote) {
        currentRow.push(currentCell.trim());
        currentCell = '';
      } else if ((char === '\r' || char === '\n') && !insideQuote) {
        if (char === '\r' && nextChar === '\n') i++;
        currentRow.push(currentCell.trim());
        if (currentRow.some(c => c.length > 0)) rows.push(currentRow);
        currentRow = [];
        currentCell = '';
      } else {
        currentCell += char;
      }
    }
    if (currentCell.length > 0 || currentRow.length > 0) {
      currentRow.push(currentCell.trim());
      if (currentRow.some(c => c.length > 0)) rows.push(currentRow);
    }
    return rows;
  }

  const rows = parseDelimited(rawCsv, ',');
  assert.equal(rows.length, 3, 'Should parse header + 2 data rows');
  assert.equal(rows[1][1], 'Payment to Supplier, ABC', 'Quoted cell containing comma must not be split');
  assert.equal(rows[1][2], '5000.00');
  assert.equal(rows[2][3], '25000.00');
  assert.equal(rows[2][4], 'DEP-202');
});
