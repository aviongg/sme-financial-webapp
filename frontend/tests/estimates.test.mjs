import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { mkdtempSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-estimate-contracts-'));
const files = {
  client: '../src/lib/api/client.ts', estimates: '../src/lib/api/estimates.ts', form: '../src/lib/estimates/form.ts',
  copy: '../src/lib/i18n/translations/estimates.ts', navigation: '../src/lib/api/navigation.ts', configuration: '../src/lib/api/configuration.ts',
};
for (const [name, file] of Object.entries(files)) writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(
  readFileSync(new URL(file, import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
const require = createRequire(import.meta.url), client = require(path.join(directory, 'client.js'));
const { estimatesApi, estimatePayload } = require(path.join(directory, 'estimates.js'));
const { initialEstimate, addLine, changeLine, removeLine, changeMode, validateEstimate, previewRequest,
  percentageToRatio, formatEstimateMoney, formatEstimatePercent } = require(path.join(directory, 'form.js'));
const { estimatesEn, estimatesUr } = require(path.join(directory, 'copy.js'));
const { routePermission } = require(path.join(directory, 'navigation.js'));
const { routeModule } = require(path.join(directory, 'configuration.js'));
let calls = [];
const line = { costGroup: 'MATERIAL', description: 'Synthetic test material', quantity: '0.500000000001', unit: 'kg', unitCost: '1600.00' };
function validDraft() {
  return { ...initialEstimate(), lines: [{ ...line, id: '0' }], saleableQuantity: '10', pricingValues: { PROPOSED_PRICE: '1000.00', TARGET_MARGIN: '20', TARGET_MARKUP: '20' } };
}
function backend(handler = () => Response.json({ ok: true })) {
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return url === '/api/auth/csrf' ? Response.json({ token: 'synthetic-estimate-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }) : handler(url, options);
  });
}
beforeEach(() => { calls = []; client.invalidatePrivateRequests(); client.allowPrivateRequests(); });
afterEach(() => { mock.restoreAll(); client.invalidatePrivateRequests(); });
after(() => { for (const name of Object.keys(files)) unlinkSync(path.join(directory, `${name}.js`)); rmdirSync(directory); });

test('preview submits decimal strings through same-origin CSRF and excludes supplied business, totals and line authority', async () => {
  backend();
  const request = { ...previewRequest(validDraft()), businessId: 'another-business', userId: 'actor', currency: 'USD', totalPlannedCost: '0', post: true };
  request.lines[0] = { ...request.lines[0], id: 'foreign-line', businessId: 'other', plannedCost: '0', sourceRef: 'foreign-stock' };
  await estimatesApi.preview(request);
  const call = calls.at(-1), payload = JSON.parse(call.options.body);
  assert.equal(call.url, '/api/estimates/preview'); assert.equal(call.options.method, 'POST');
  assert.equal(call.options.credentials, 'same-origin'); assert.equal(call.options.headers.get('X-XSRF-TOKEN'), 'synthetic-estimate-csrf');
  assert.deepEqual(payload, { lines: [line], saleableQuantity: '10', pricingMode: 'PROPOSED_PRICE', pricingValue: '1000.00' });
  assert.deepEqual(estimatePayload(request), payload);
});

test('pricing-mode changes preserve independent proposed, margin and markup inputs and send their exact intended modes', () => {
  const draft = validDraft(); draft.pricingValues.TARGET_MARKUP = '31.125';
  const margin = changeMode(draft, 'TARGET_MARGIN'), markup = changeMode(margin, 'TARGET_MARKUP');
  assert.equal(previewRequest(margin).pricingValue, '0.20'); assert.equal(previewRequest(margin).pricingMode, 'TARGET_MARGIN');
  assert.equal(previewRequest(markup).pricingValue, '0.31125'); assert.equal(previewRequest(markup).pricingMode, 'TARGET_MARKUP');
  assert.equal(previewRequest(changeMode(markup, 'PROPOSED_PRICE')).pricingValue, '1000.00');
  assert.equal(draft.pricingMode, 'PROPOSED_PRICE'); assert.equal(margin.pricingValues.TARGET_MARGIN, '20');
});

test('blank drafts never invent sample amounts, and adding/removing lines preserves other assumptions and unique stable IDs', () => {
  let draft = initialEstimate();
  assert.equal(draft.lines[0].quantity, ''); assert.equal(draft.lines[0].unitCost, ''); assert.equal(draft.pricingValues.PROPOSED_PRICE, '');
  draft = changeLine(draft, '0', line);
  const added = addLine(draft, '1'); assert.equal(added.lines.length, 2); assert.equal(draft.lines.length, 1);
  assert.equal(added.lines[1].unitCost, ''); assert.deepEqual(added.lines[0], draft.lines[0]);
  const removed = removeLine(added, '0'); assert.equal(removed.lines[0].id, '1'); assert.equal(added.lines.length, 2);
  const empty = removeLine(removed, '1'); assert.equal(validateEstimate(empty).lines, 'lines');
  assert.equal(addLine(empty, '2').lines[0].id, '2');
});

test('line limits are bounded without dropping existing lines or changing server financial values', () => {
  let draft = validDraft();
  for (let index = 1; index < 100; index++) draft = addLine(draft, String(index));
  assert.equal(draft.lines.length, 100); assert.equal(addLine(draft, '100'), draft);
  assert.equal(validateEstimate({ ...draft, lines: [...draft.lines, { ...line, id: '100' }] }).lines, 'lines');
});

test('decimal validation preserves known zero and unknown quantity while rejecting exponent, negative, malformed and overprecision inputs', () => {
  const draft = validDraft();
  for (const value of ['-1', '1e3', 'NaN', 'Infinity', '+1', '.5', '1.', ' 1', '1 ', '1,000', '١', '1000000000000', '0.1234567890123']) {
    const invalid = changeLine(draft, '0', { quantity: value, unitCost: value });
    assert.equal(validateEstimate(invalid)['lines[0].quantity'], 'decimal', value);
    assert.equal(validateEstimate(invalid)['lines[0].unitCost'], 'decimal', value);
    assert.throws(() => previewRequest(invalid), /Invalid estimate/);
  }
  assert.deepEqual(validateEstimate(changeLine(draft, '0', { quantity: '0', unitCost: '0.000000000000' })), {});
  assert.equal(previewRequest({ ...draft, saleableQuantity: '' }).saleableQuantity, null);
  assert.equal(previewRequest({ ...draft, saleableQuantity: '0' }).saleableQuantity, '0');
  assert.equal(validateEstimate(changeLine(draft, '0', { quantity: '' }))['lines[0].quantity'], 'required');
  assert.equal(validateEstimate({ ...draft, saleableQuantity: ' ' }).saleableQuantity, 'decimal');
});

test('percent conversion is an exact string shift at zero, leading-zero and 12-place ratio boundaries', () => {
  assert.equal(percentageToRatio('20'), '0.20'); assert.equal(percentageToRatio('0'), '0.00');
  assert.equal(percentageToRatio('00020.000'), '0.20000');
  assert.equal(percentageToRatio('99.9999999999'), '0.999999999999');
  assert.equal(percentageToRatio('0.0000000001'), '0.000000000001');
  assert.equal(percentageToRatio('99999999999999.9999999999'), '999999999999.999999999999');
  for (const value of ['', '-1', '1e2', '0.00000000001', '100000000000000', ' 20']) assert.throws(() => percentageToRatio(value));
});

test('target margin is strictly below 100 percent; markup can exceed 100 and retains separate validation bounds', () => {
  const draft = changeMode(validDraft(), 'TARGET_MARGIN');
  for (const value of ['100', '100.00', '0100', '99999999999999']) {
    assert.equal(validateEstimate({ ...draft, pricingValues: { ...draft.pricingValues, TARGET_MARGIN: value } }).pricingValue, 'marginRange');
  }
  for (const value of ['0', '00.00', '99.9999999999', '000099']) {
    assert.deepEqual(validateEstimate({ ...draft, pricingValues: { ...draft.pricingValues, TARGET_MARGIN: value } }), {});
  }
  assert.deepEqual(validateEstimate({ ...draft, pricingMode: 'TARGET_MARKUP', pricingValues: { ...draft.pricingValues, TARGET_MARKUP: '99999999999999.9999999999' } }), {});
  assert.equal(validateEstimate({ ...draft, pricingValues: { ...draft.pricingValues, TARGET_MARGIN: '0.00000000001' } }).pricingValue, 'percentage');
});

test('exact server output formatting preserves every digit, negative contribution and unavailable ratios in both locales', () => {
  const exact = '999999999999999999999999.000000000000000000000001';
  assert.equal(formatEstimateMoney(exact, 'en'), 'PKR 999,999,999,999,999,999,999,999.000000000000000000000001');
  assert.equal(formatEstimateMoney(exact, 'ur'), '999,999,999,999,999,999,999,999.000000000000000000000001 روپے');
  assert.equal(formatEstimateMoney('1000.00', 'en'), 'PKR 1,000.00');
  assert.equal(formatEstimateMoney('-800.00', 'en'), '- PKR 800.00');
  assert.equal(formatEstimateMoney('0.00', 'ur'), '0.00 روپے'); assert.equal(formatEstimateMoney(null, 'en'), '—');
  assert.equal(formatEstimatePercent('0.166666666666666666666667'), '16.6666666666666666666667%');
  assert.equal(formatEstimatePercent('0.20'), '20%'); assert.equal(formatEstimatePercent('-1'), '-100%');
  assert.equal(formatEstimatePercent('0.000000000000000000000001'), '0.0000000000000000000001%');
  assert.equal(formatEstimatePercent('0'), '0%'); assert.equal(formatEstimatePercent(null), '—');
});

test('transport returns server calculations and nulls unchanged instead of computing prices or replacing missing values', async () => {
  const response = { totalPlannedCost: '800.00', plannedBatchPrice: '960.00', plannedContribution: '160.00',
    plannedMargin: '0.166666666666666666666667', plannedMarkup: '0.2', plannedPerUnitPrice: null,
    unavailableReasons: { plannedPerUnitPrice: 'SALEABLE_QUANTITY_UNKNOWN' } };
  backend(() => Response.json(response));
  assert.deepEqual(await estimatesApi.preview(previewRequest(changeMode(validDraft(), 'TARGET_MARKUP'))), response);
});

test('backend validation retains safe field codes, does not retry the POST and leaves caller draft intact', async () => {
  backend(() => Response.json({ error: 'estimate_invalid', errors: { 'lines[0].quantity': 'decimal_invalid' } }, { status: 400 }));
  const draft = validDraft(), snapshot = structuredClone(draft);
  await assert.rejects(() => estimatesApi.preview(previewRequest(draft)), error => error.status === 400 &&
    error.code === 'estimate_invalid' && error.fieldErrors['lines[0].quantity'] === 'decimal_invalid');
  assert.equal(calls.filter(call => call.url === '/api/estimates/preview').length, 1);
  assert.deepEqual(draft, snapshot);
});

test('session/business invalidation rejects a late preview even if fetch ignores abort', async () => {
  let finish; let announce;
  const reached = new Promise(resolve => { announce = resolve; });
  backend(() => new Promise(resolve => { finish = resolve; announce(); }));
  const pending = estimatesApi.preview(previewRequest(validDraft())); await reached;
  client.invalidatePrivateRequests(); client.allowPrivateRequests();
  finish(Response.json({ businessId: 'old-business', totalPlannedCost: '800.00' }));
  await assert.rejects(() => pending, error => error.name === 'AbortError');
});

test('editing/cancelling can abort a pending preview and no delayed amount is accepted', async () => {
  let finish; let announce;
  const reached = new Promise(resolve => { announce = resolve; });
  backend(() => new Promise(resolve => { finish = resolve; announce(); }));
  const controller = new AbortController();
  const pending = estimatesApi.preview(previewRequest(validDraft()), controller.signal); await reached;
  controller.abort(); finish(Response.json({ plannedBatchPrice: '1000.00' }));
  await assert.rejects(() => pending, error => error.name === 'AbortError');
});

test('English and Urdu cover all fields, modes, errors, denominator reasons and planning disclosures without changing domain keys', () => {
  function verify(en, ur) {
    assert.deepEqual(Object.keys(en).sort(), Object.keys(ur).sort());
    for (const key of Object.keys(en)) {
      if (typeof en[key] === 'object') verify(en[key], ur[key]);
      else assert.ok(typeof ur[key] === 'string' && ur[key].trim(), key);
    }
  }
  verify(estimatesEn, estimatesUr);
  for (const field of ['disclosure', 'precision', 'zeroPrice', 'zeroCost', 'unknownQuantity', 'zeroQuantity']) {
    assert.match(estimatesUr[field], /[\u0600-\u06ff]/);
  }
  assert.deepEqual(Object.keys(estimatesUr.groups), ['MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER']);
});

test('planning route uses existing financial-read permission and does not enable the future costing module', () => {
  assert.equal(routePermission('/estimates'), 'FINANCIAL_DATA_READ'); assert.equal(routeModule('/estimates'), null);
});

test('estimate browser code never invokes number conversion or decimal arithmetic for financial values', () => {
  const sources = ['../src/lib/estimates/form.ts', '../src/lib/api/estimates.ts', '../src/components/estimates/EstimateEditor.tsx'];
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
});
