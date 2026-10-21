import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-recurring-'));
for (const name of [
  'lib/api/dev-a/recurring',
  'components/records/recurring/recurring-copy',
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
const { recurringEn, recurringUr } = require(path.join(directory, 'components/records/recurring/recurring-copy.js'));
const { recordsEn, recordsUr } = require(path.join(directory, 'components/records/records-copy.js'));
const { recurringApi } = require(path.join(directory, 'lib/api/dev-a/recurring.js'));

test('Recurring copy has 1:1 English/Urdu key symmetry', () => {
  const enKeys = Object.keys(recurringEn).sort();
  const urKeys = Object.keys(recurringUr).sort();
  assert.deepEqual(enKeys, urKeys, 'Recurring copy keys must be identical across languages');
  for (const key of enKeys) {
    assert.ok(recurringEn[key], `recurringEn[${key}] must not be empty`);
    assert.ok(recurringUr[key], `recurringUr[${key}] must not be empty`);
  }
});

test('Recurring copy contains mandatory review policy notice in EN and UR', () => {
  assert.match(
    recurringEn.reviewPolicyNotice,
    /Recurring entries are not recorded as actual transactions until you review and confirm them\./,
    'EN policy notice must match exact required wording'
  );
  assert.ok(
    recurringUr.reviewPolicyNotice.length > 20,
    'UR policy notice must be provided'
  );
});

test('Records copy contains recurring tab key in both EN and UR', () => {
  assert.equal(recordsEn.recurring, 'Recurring Rules');
  assert.equal(recordsUr.recurring, 'تکراری قواعد');
});

test('Recurring API adapter exposes required endpoints', () => {
  const requiredMethods = [
    'listRules',
    'getRule',
    'createRule',
    'updateRule',
    'pauseRule',
    'resumeRule',
    'archiveRule',
    'previewSchedule',
    'listOccurrences',
    'getOccurrence',
    'generateDueOccurrences',
    'skipOccurrence',
  ];
  for (const method of requiredMethods) {
    assert.equal(typeof recurringApi[method], 'function', `recurringApi.${method} must be a function`);
  }
});

test('Frequency labels are defined for all 5 W5-05 frequencies', () => {
  const frequencies = ['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY'];
  for (const freq of frequencies) {
    assert.ok(recurringEn.frequencies[freq], `EN frequency label missing for ${freq}`);
    assert.ok(recurringUr.frequencies[freq], `UR frequency label missing for ${freq}`);
  }
});

test('Occurrence status labels are defined for all lifecycle states', () => {
  const statuses = ['SCHEDULED', 'DRAFT_READY', 'POSTED', 'SKIPPED', 'REVERSED'];
  for (const st of statuses) {
    assert.ok(recurringEn.occurrenceStatuses[st], `EN occurrence status missing for ${st}`);
    assert.ok(recurringUr.occurrenceStatuses[st], `UR occurrence status missing for ${st}`);
  }
});
