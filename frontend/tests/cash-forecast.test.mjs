import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-forecast-'));
for (const name of [
  'lib/api/dev-a/cash-forecast',
  'components/records/forecast/forecast-copy',
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
const { forecastEn, forecastUr } = require(path.join(directory, 'components/records/forecast/forecast-copy.js'));
const { recordsEn, recordsUr } = require(path.join(directory, 'components/records/records-copy.js'));
const { cashForecastApi } = require(path.join(directory, 'lib/api/dev-a/cash-forecast.js'));

test('Cash Forecast copy has 1:1 English/Urdu key symmetry', () => {
  const enKeys = Object.keys(forecastEn).sort();
  const urKeys = Object.keys(forecastUr).sort();
  assert.deepEqual(enKeys, urKeys, 'Forecast copy keys must be identical across languages');
  for (const key of enKeys) {
    assert.ok(forecastEn[key], `forecastEn[${key}] must not be empty`);
    assert.ok(forecastUr[key], `forecastUr[${key}] must not be empty`);
  }
});

test('Records copy contains forecast tab key in both EN and UR', () => {
  assert.equal(recordsEn.forecast, 'Cash Forecast');
  assert.equal(recordsUr.forecast, 'کیش پیشگوئی');
});

test('Forecast copy contains mandatory disclaimer and data coverage notices', () => {
  assert.match(
    forecastEn.disclaimer,
    /Disclaimer: Forecasts are deterministic estimates derived from current payment schedules and active recurring rules\./,
    'EN disclaimer notice must match required wording'
  );
  assert.ok(
    forecastUr.disclaimer.length > 20,
    'UR disclaimer notice must be provided'
  );
  assert.match(
    forecastEn.completenessNotice,
    /Data Coverage: Inventory purchases \(COGS pending Fatima F08\) and project budgets \(pending F05\) are omitted until authoritative feeds are integrated\./,
    'EN completeness notice must match boundary definitions'
  );
  assert.ok(
    forecastUr.completenessNotice.length > 20,
    'UR completeness notice must be provided'
  );
});

test('Cash Forecast API client exposes expected functions', () => {
  assert.equal(typeof cashForecastApi.getDailyForecast, 'function');
  assert.equal(typeof cashForecastApi.previewScenario, 'function');
});

