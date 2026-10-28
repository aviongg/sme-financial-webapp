import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { mkdtempSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

// F06 Phase B persisted estimates: client contracts, exact string handling, copy parity and planning-only boundaries.
const directory = mkdtempSync(path.join(tmpdir(), 'finsight-estimate-planning-'));
const files = {
  client: '../src/lib/api/client.ts', estimates: '../src/lib/api/estimates.ts', form: '../src/lib/estimates/form.ts',
  planning: '../src/lib/i18n/translations/estimatePlanning.ts', navigation: '../src/lib/api/navigation.ts',
};
for (const [name, file] of Object.entries(files)) writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(
  readFileSync(new URL(file, import.meta.url), 'utf8').replace("'../api/estimates'", "'./estimates'"),
  { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
const require = createRequire(import.meta.url), client = require(path.join(directory, 'client.js'));
const { estimateRecordsApi, isTrustedEstimate, isTrustedRevision, conversionPrecisionCode } = require(path.join(directory, 'estimates.js'));
const { draftFromResult, previewRequest, percentageToRatio, ratioToPercentage, validateIdentity, identityPayload, validateEstimate } = require(path.join(directory, 'form.js'));
const { estimatePlanningEn, estimatePlanningUr } = require(path.join(directory, 'planning.js'));
const { routePermission } = require(path.join(directory, 'navigation.js'));
let calls = [];
function backend(handler = () => Response.json({ ok: true })) {
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return url === '/api/auth/csrf' ? Response.json({ token: 'synthetic-f06b-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }) : handler(url, options);
  });
}
beforeEach(() => { calls = []; client.invalidatePrivateRequests(); client.allowPrivateRequests(); });
afterEach(() => { mock.restoreAll(); client.invalidatePrivateRequests(); });
after(() => { for (const name of Object.keys(files)) unlinkSync(path.join(directory, `${name}.js`)); rmdirSync(directory); });

const content = {
  lines: [{ costGroup: 'MATERIAL', description: 'Steel sheet', quantity: '2.5', unit: 'sheet', unitCost: '120.125' }],
  saleableQuantity: '10', pricingMode: 'TARGET_MARGIN', pricingValue: '0.2',
};
const storedResult = {
  businessId: 'business-a', currency: 'PKR', currencyBasis: 'APPLICATION_FIXED_PKR', planningOnly: true,
  lines: [{ lineNumber: 1, costGroup: 'MATERIAL', description: 'Steel sheet', quantity: '2.5', unit: 'sheet', unitCost: '120.125', plannedCost: '300.3125' }],
  groupSubtotals: { MATERIAL: '300.3125', LABOUR: '0.00', OVERHEAD: '0.00', FREIGHT: '0.00', OTHER: '0.00' },
  totalPlannedCost: '300.3125', saleableQuantity: '10', pricingMode: 'TARGET_MARGIN', pricingValue: '0.2',
  plannedBatchPrice: '375.390625', plannedContribution: '75.078125', plannedMargin: '0.2', plannedMarkup: '0.25', plannedPerUnitPrice: '37.5390625',
  unavailableReasons: { plannedMargin: null, plannedMarkup: null, plannedPerUnitPrice: null },
  calculation: { version: 'f06-preview-v1', divisionScale: 24, divisionRounding: 'HALF_EVEN', accountingRoundingPolicy: null },
};

test('ratio and percentage conversions are exact inverse string shifts at every Phase A boundary', () => {
  for (const percentage of ['0', '20', '12.5', '99.9999999999', '31.125', '100', '0.0000000001', '99999999999999.9999999999']) {
    assert.equal(ratioToPercentage(percentageToRatio(percentage)), percentage, percentage);
  }
  for (const [ratio, percentage] of [['0.2', '20'], ['0.25', '25'], ['0.000000000001', '0.0000000001'], ['1', '100'], ['0', '0'], ['0.105', '10.5']]) {
    assert.equal(ratioToPercentage(ratio), percentage);
  }
  for (const invalid of ['-0.2', '1e-3', '.5', '0.2 ', '0.1234567890123']) assert.throws(() => ratioToPercentage(invalid), /Invalid ratio/);
});

test('a stored revision rebuilds the identical f06-preview-v1 request without recalculating in the browser', () => {
  const draft = draftFromResult(storedResult, 'r1');
  assert.deepEqual(Object.keys(validateEstimate(draft)), []);
  assert.equal(draft.pricingValues.TARGET_MARGIN, '20');
  assert.equal(draft.pricingValues.PROPOSED_PRICE, '');
  // Phase A's percentage-to-ratio shift writes 20% as "0.20": the same exact value the server stored as "0.2".
  assert.deepEqual(previewRequest(draft), { ...content, pricingValue: '0.20' });
  const proposed = draftFromResult({ ...storedResult, pricingMode: 'PROPOSED_PRICE', pricingValue: '1500.25', saleableQuantity: null }, 'r2');
  assert.equal(proposed.pricingValues.PROPOSED_PRICE, '1500.25');
  assert.equal(proposed.saleableQuantity, '', 'Unknown saleable quantity stays unknown, never zero');
  assert.equal(previewRequest(proposed).saleableQuantity, null);
});

test('identity validation mirrors the server code, name and description bounds without inventing values', () => {
  assert.deepEqual(validateIdentity({ code: 'est-order/1', name: 'Order', description: '' }), {});
  assert.deepEqual(validateIdentity({ code: '', name: ' ', description: 'x'.repeat(1001) }),
    { code: 'codeRequired', name: 'nameRequired', description: 'descriptionLength' });
  assert.equal(validateIdentity({ code: '-EST', name: 'n', description: '' }).code, 'codeInvalid');
  assert.equal(validateIdentity({ code: 'E'.repeat(41), name: 'n', description: '' }).code, 'codeInvalid');
  assert.deepEqual(identityPayload({ code: ' est-1 ', name: ' Order ', description: '  ' }), { code: 'est-1', name: 'Order', description: null });
});

test('persisted mutations send only client-owned identity, assumptions and expected versions through CSRF', async () => {
  backend();
  const polluted = { ...content, businessId: 'other', totalPlannedCost: '0', plannedBatchPrice: '1', status: 'APPROVED',
    lines: [{ ...content.lines[0], plannedCost: '0', businessId: 'other' }] };
  await estimateRecordsApi.create({ code: 'EST-1', name: 'Order', description: null }, polluted);
  let call = calls.at(-1);
  assert.equal(call.url, '/api/estimates'); assert.equal(call.options.method, 'POST');
  assert.equal(call.options.headers.get('X-XSRF-TOKEN'), 'synthetic-f06b-csrf');
  assert.deepEqual(JSON.parse(call.options.body), { code: 'EST-1', name: 'Order', description: null, draft: content });

  await estimateRecordsApi.updateRevision('est/1', 'rev 1', polluted, 3);
  call = calls.at(-1);
  assert.equal(call.url, '/api/estimates/est%2F1/versions/rev%201'); assert.equal(call.options.method, 'PUT');
  assert.deepEqual(JSON.parse(call.options.body), { ...content, expectedVersion: 3 });

  await estimateRecordsApi.createRevision('e', polluted, 4);
  assert.deepEqual(JSON.parse(calls.at(-1).options.body), { ...content, expectedVersion: 4 });
  await estimateRecordsApi.approve('e', 'r', 2);
  assert.equal(calls.at(-1).url, '/api/estimates/e/versions/r/approve');
  assert.deepEqual(JSON.parse(calls.at(-1).options.body), { expectedVersion: 2 });
  await estimateRecordsApi.archive('e', 7);
  assert.deepEqual(JSON.parse(calls.at(-1).options.body), { expectedVersion: 7 });
  await estimateRecordsApi.convert('e', 'r', 'project-1', 5);
  call = calls.at(-1);
  assert.equal(call.url, '/api/estimates/e/versions/r/convert-to-project');
  assert.deepEqual(JSON.parse(call.options.body), { targetProjectId: 'project-1', expectedProjectVersion: 5 },
    'Conversion never sends totals, budget values, business, actor, revision number or lineage');
});

test('deletes and reads carry versions in the query and use the existing /api convention', async () => {
  backend(() => new Response(null, { status: 204 }));
  await estimateRecordsApi.removeRevision('e', 'r', 1);
  assert.equal(calls.at(-1).url, '/api/estimates/e/versions/r?expectedVersion=1'); assert.equal(calls.at(-1).options.method, 'DELETE');
  await estimateRecordsApi.remove('e', 2);
  assert.equal(calls.at(-1).url, '/api/estimates/e?expectedVersion=2');
  mock.restoreAll(); backend(() => Response.json({ businessId: 'b', items: [] }));
  await estimateRecordsApi.list(true);
  assert.equal(calls.at(-1).url, '/api/estimates?includeArchived=true'); assert.equal(calls.at(-1).options.method, 'GET');
});

test('a precision rejection keeps its stable code for the conversion limitation UI', async () => {
  backend(() => Response.json({ status: 422, error: conversionPrecisionCode, message: 'm', estimateValid: true, details: [] }, { status: 422 }));
  await assert.rejects(estimateRecordsApi.convert('e', 'r', 'p', 0), error => error.status === 422 && error.code === 'conversion_precision_unrepresentable');
  assert.equal(calls.filter(call => call.url.includes('convert-to-project')).length, 1, 'A conversion is never retried automatically');
});

test('responses from another business or without planning-only markers are rejected', () => {
  const estimate = { businessId: 'business-a', planningOnly: true, baseCurrency: 'PKR' };
  assert.equal(isTrustedEstimate(estimate, 'business-a'), true);
  assert.equal(isTrustedEstimate(estimate, 'business-b'), false);
  assert.equal(isTrustedEstimate({ ...estimate, planningOnly: false }, 'business-a'), false);
  const revision = { businessId: 'business-a', estimateId: 'e', planningOnly: true, result: storedResult };
  assert.equal(isTrustedRevision(revision, 'business-a', 'e'), true);
  assert.equal(isTrustedRevision(revision, 'business-a', 'other-estimate'), false);
  assert.equal(isTrustedRevision({ ...revision, result: { ...storedResult, businessId: 'business-b' } }, 'business-a', 'e'), false);
  assert.equal(isTrustedRevision({ ...revision, result: { ...storedResult, calculation: { ...storedResult.calculation, version: 'other' } } }, 'business-a', 'e'), false);
});

test('creating requires RECORD_CREATE_UPDATE while register, detail and preview stay financial-read', () => {
  assert.equal(routePermission('/estimates/new'), 'RECORD_CREATE_UPDATE');
  for (const route of ['/estimates', '/estimates/preview', '/estimates/2f1c']) assert.equal(routePermission(route), 'FINANCIAL_DATA_READ');
});

test('English and Urdu copy have identical structure, Urdu script and the exact planning-only conversion warning', () => {
  const shape = value => typeof value === 'function' ? 'fn' : value && typeof value === 'object'
    ? Object.fromEntries(Object.keys(value).sort().map(key => [key, shape(value[key])])) : typeof value;
  assert.deepEqual(shape(estimatePlanningUr), shape(estimatePlanningEn));
  assert.equal(estimatePlanningEn.conversion.warning,
    'This creates a DRAFT project planning budget only. It does not post money or record actual project performance.');
  assert.equal(estimatePlanningEn.conversion.limitationBody,
    'This estimate is valid, but some values have more precision than Project Planning supports. Revise those values before conversion.');
  assert.equal(estimatePlanningEn.planned, 'PLANNED'); assert.equal(estimatePlanningEn.planningOnly, 'PLANNING ONLY');
  for (const key of ['banner', 'approveNoPosting', 'archivedBanner']) assert.match(estimatePlanningUr[key], /[؀-ۿ]/, key);
  assert.match(estimatePlanningUr.conversion.warning, /[؀-ۿ]/);
  for (const code of ['stale_estimate_version', 'stale_revision_version', 'draft_revision_already_exists', 'revision_already_converted',
    'draft_budget_already_exists', 'conversion_precision_unrepresentable', 'invalid_project_state', 'project_not_found']) {
    assert.ok(estimatePlanningEn.errors[code] && estimatePlanningUr.errors[code], code);
  }
  assert.match(estimatePlanningEn.conversion.reasons.scale_exceeded(6), /6 decimal places/);
});

test('persisted estimate UI never converts or calculates financial values in the browser', () => {
  const sources = ['../src/components/estimates/EstimateInputs.tsx', '../src/components/estimates/EstimateWorkspace.tsx',
    '../src/components/estimates/EstimateRegister.tsx', '../src/lib/estimates/form.ts', '../src/lib/api/estimates.ts'];
  for (const file of sources) {
    const source = ts.createSourceFile(file, readFileSync(new URL(file, import.meta.url), 'utf8'), ts.ScriptTarget.Latest, true, file.endsWith('tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
    const violations = [];
    function visit(node) {
      if (ts.isCallExpression(node) && /^(Number|parseFloat|parseInt|BigInt)$/.test(node.expression.getText(source))) violations.push(node.expression.getText(source));
      if (ts.isBinaryExpression(node) && [ts.SyntaxKind.AsteriskToken, ts.SyntaxKind.SlashToken, ts.SyntaxKind.PercentToken, ts.SyntaxKind.AsteriskAsteriskToken].includes(node.operatorToken.kind)) violations.push(node.getText(source));
      if (ts.isPropertyAccessExpression(node) && ['toFixed', 'toPrecision', 'valueAsNumber'].includes(node.name.text)) violations.push(node.getText(source));
      ts.forEachChild(node, visit);
    }
    visit(source); assert.deepEqual(violations, [], file);
  }
  const workspace = readFileSync(new URL('../src/components/estimates/EstimateWorkspace.tsx', import.meta.url), 'utf8');
  assert.ok(!/monthly|score|ledger|invoice|commercial/i.test(workspace.replace(/\/\/.*$/gm, '')), 'Workspace calls no financial-posting API');
});
