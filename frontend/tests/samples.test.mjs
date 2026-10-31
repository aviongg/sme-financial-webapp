import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { mkdtempSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-sample-contracts-'));
const files = { client: '../src/lib/api/client.ts', form: '../src/lib/samples/form.ts', samples: '../src/lib/api/samples.ts', copy: '../src/lib/i18n/translations/samples.ts', navigation: '../src/lib/api/navigation.ts', configuration: '../src/lib/api/configuration.ts' };
for (const [name, file] of Object.entries(files)) {
  const raw = readFileSync(new URL(file, import.meta.url), 'utf8').replace(/from '@\/lib\/samples\/form'/g, "from './form'");
  writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(raw, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
}
const require = createRequire(import.meta.url);
const client = require(path.join(directory, 'client.js'));
const form = require(path.join(directory, 'form.js'));
const { samplesApi } = require(path.join(directory, 'samples.js'));
const { samplesEn, samplesUr, samplesCopy } = require(path.join(directory, 'copy.js'));
const { routePermission } = require(path.join(directory, 'navigation.js'));
const { routeModule } = require(path.join(directory, 'configuration.js'));
let calls = [];
function backend(handler = () => Response.json({ ok: true })) {
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return url === '/api/auth/csrf' ? Response.json({ token: 'sample-test-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }) : handler(url, options);
  });
}
beforeEach(() => { calls = []; client.invalidatePrivateRequests(); client.allowPrivateRequests(); });
afterEach(() => { mock.restoreAll(); client.invalidatePrivateRequests(); });
after(() => { for (const name of Object.keys(files)) unlinkSync(path.join(directory, `${name}.js`)); rmdirSync(directory); });
const line = { id: '0', category: 'MATERIAL', description: 'Cotton', quantity: '2.5', unit: 'kg', unitCost: '320.00' };
const plan = { plannedRecovery: '', lines: [line] };
const validMetadata = { ...form.initialMetadata(), code: 's-2026/1', name: 'Sample', recoveryIntent: 'FREE' };

test('metadata serialization selects only allowed fields and preserves optional project context', () => {
  const projectId = '12345678-1234-1234-1234-123456789abc';
  const payload = form.createPayload({ ...validMetadata, code: ' s-2026/1 ', name: ' Sample ', projectId, businessId: 'forged', actorUserId: 'forged', status: 'ACTIVE', financialBasis: 'ACTUAL' });
  assert.deepEqual(payload, { code: 'S-2026/1', name: 'Sample', kind: 'SAMPLE', description: null, recoveryIntent: 'FREE', projectId, startDate: null, targetEndDate: null });
  assert.deepEqual(form.metadataFromActivity(payload), { ...validMetadata, code: 'S-2026/1', projectId });
});
test('metadata validation checks all kind/recovery values, real dates and same-contract field bounds', () => {
  assert.deepEqual(form.validateMetadata(validMetadata), {});
  assert.equal(form.validateMetadata({ ...validMetadata, code: 'A BAD CODE' }).code, 'code');
  assert.equal(form.validateMetadata({ ...validMetadata, kind: 'ORDER' }).kind, 'invalid');
  assert.equal(form.validateMetadata({ ...validMetadata, recoveryIntent: 'RECEIVED' }).recoveryIntent, 'invalid');
  assert.equal(form.validateMetadata({ ...validMetadata, projectId: 'bad-id' }).projectId, 'invalid');
  assert.equal(form.validateMetadata({ ...validMetadata, name: 'x'.repeat(161) }).name, 'text');
  assert.equal(form.validateMetadata({ ...validMetadata, startDate: '2026-02-30' }).startDate, 'date');
  assert.equal(form.validateMetadata({ ...validMetadata, startDate: '2026-03-01', targetEndDate: '2026-02-28' }).targetEndDate, 'dateOrder');
  assert.equal(form.validateMetadata({ ...validMetadata, description: 'bad\u0001text' }).description, 'text');
});
test('FREE recovery serializes explicit zero; PAID and PARTIAL preserve unknown separately from recorded zero', () => {
  assert.equal(form.planPayload(plan, 'FREE').plannedRecovery, '0');
  assert.equal(form.planPayload({ ...plan, plannedRecovery: '999' }, 'FREE').plannedRecovery, '0');
  for (const intent of ['PAID', 'PARTIAL']) {
    assert.equal(form.planPayload(plan, intent).plannedRecovery, null);
    assert.equal(form.planPayload({ ...plan, plannedRecovery: '0' }, intent).plannedRecovery, '0');
    assert.equal(form.planPayload({ ...plan, plannedRecovery: '0.0001' }, intent).plannedRecovery, '0.0001');
  }
});
test('plan serialization preserves exact inputs and excludes totals, history and authority', () => {
  const payload = form.planPayload({ plannedRecovery: '1000.1234', plannedCost: 'forged', plannedContribution: 'forged', businessId: 'forged', status: 'APPROVED', lines: [{ ...line, description: ' Cotton ', unit: ' kg ', plannedLineCost: 'forged', id: 'forged' }] }, 'PAID');
  assert.deepEqual(payload, { plannedRecovery: '1000.1234', lines: [{ category: 'MATERIAL', description: 'Cotton', quantity: '2.5', unit: 'kg', unitCost: '320.00' }] });
});
test('plan decimal validation permits null recovery and exact maxima but rejects signs, exponent, whitespace and overflow', () => {
  assert.deepEqual(form.validatePlan(plan, 'PAID'), {});
  assert.deepEqual(form.validatePlan({ ...plan, plannedRecovery: '0' }, 'PARTIAL'), {});
  for (const value of ['-1', '1e3', 'NaN', '1,000', '.5', '1.', ' 1', '1 ']) {
    assert.equal(form.validatePlan({ ...plan, plannedRecovery: value }, 'PAID').plannedRecovery, 'decimal', value);
    assert.equal(form.validatePlan({ ...plan, lines: [{ ...line, quantity: value }] }, 'FREE')['lines[0].quantity'], 'decimal', value);
    assert.equal(form.validatePlan({ ...plan, lines: [{ ...line, unitCost: value }] }, 'FREE')['lines[0].unitCost'], 'decimal', value);
  }
  assert.equal(form.validatePlan({ ...plan, plannedRecovery: '1.00001' }, 'PAID').plannedRecovery, 'decimal');
  assert.equal(form.validatePlan({ ...plan, lines: [{ ...line, quantity: '1000000000' }] }, 'FREE')['lines[0].quantity'], 'decimal');
  assert.equal(form.validatePlan({ ...plan, lines: [{ ...line, unitCost: '0.1234567' }] }, 'FREE')['lines[0].unitCost'], 'decimal');
  assert.deepEqual(form.validatePlan({ plannedRecovery: '999999999999.9999', lines: [{ ...line, quantity: '999999999.999999', unitCost: '999999999999.999999' }] }, 'PARTIAL'), {});
});
test('line validation enforces complete 1-100 category, unit and description lines', () => {
  assert.equal(form.validatePlan({ ...plan, lines: [] }, 'FREE').lines, 'lines');
  assert.equal(form.validatePlan({ ...plan, lines: Array.from({ length: 101 }, () => line) }, 'FREE').lines, 'lines');
  assert.deepEqual(form.validatePlan({ ...plan, lines: Array.from({ length: 100 }, () => line) }, 'FREE'), {});
  const issues = form.validatePlan({ ...plan, lines: [{ ...line, category: 'COGS', unit: '', description: '' }] }, 'FREE');
  assert.equal(issues['lines[0].category'], 'invalid'); assert.equal(issues['lines[0].unit'], 'text'); assert.equal(issues['lines[0].description'], 'text');
});
test('cloning history preserves unknown recovery and strips storage scale for editable decimals', () => {
  const cloned = form.draftFromPlan({ plannedRecovery: null, lines: [{ ...line, quantity: '2.500000', unitCost: '320.000000' }] });
  assert.equal(cloned.plannedRecovery, ''); assert.equal(cloned.lines[0].quantity, '2.5'); assert.equal(cloned.lines[0].unitCost, '320');
  assert.equal(form.draftFromPlan({ plannedRecovery: '0.0000', lines: [line] }).plannedRecovery, '0');
});
test('lifecycle rules lock closed states and require draft resolution before every closing transition', () => {
  assert.deepEqual(form.allowedTransitions('DRAFT'), ['ACTIVE', 'CANCELLED', 'ARCHIVED']);
  assert.deepEqual(form.allowedTransitions('ACTIVE'), ['COMPLETED', 'CANCELLED', 'ARCHIVED']);
  assert.deepEqual(form.allowedTransitions('COMPLETED'), ['ARCHIVED']); assert.deepEqual(form.allowedTransitions('CANCELLED'), ['ARCHIVED']); assert.deepEqual(form.allowedTransitions('ARCHIVED'), []);
  for (const status of ['COMPLETED', 'CANCELLED', 'ARCHIVED']) { assert.equal(form.canEdit(status, true), false); assert.equal(form.transitionBlocked(status, true), true); }
  assert.equal(form.canEdit('ACTIVE', false), false); assert.equal(form.canEdit('DRAFT', true), true); assert.equal(form.transitionBlocked('ACTIVE', true), false);
});
test('sample API uses existing CSRF/session transport and correct optimistic version fields for every mutation', async () => {
  backend();
  await samplesApi.create(form.createPayload(validMetadata));
  assert.equal(calls.at(-1).options.headers.get('X-XSRF-TOKEN'), 'sample-test-csrf');
  assert.equal(calls.at(-1).url, '/api/samples'); assert.equal(calls.at(-1).options.method, 'POST');
  await samplesApi.createPlan('a/b', { ...form.planPayload(plan, 'FREE'), expectedActivityVersion: 3 });
  assert.equal(calls.at(-1).url, '/api/samples/a%2Fb/plans'); assert.equal(JSON.parse(calls.at(-1).options.body).expectedActivityVersion, 3);
  await samplesApi.updatePlan('a', 'p', { ...form.planPayload(plan, 'PAID'), expectedPlanVersion: 4 });
  assert.equal(JSON.parse(calls.at(-1).options.body).expectedPlanVersion, 4); assert.equal(calls.at(-1).options.method, 'PUT');
  await samplesApi.approvePlan('a', 'p', { expectedActivityVersion: 5, expectedPlanVersion: 4 });
  assert.deepEqual(JSON.parse(calls.at(-1).options.body), { expectedActivityVersion: 5, expectedPlanVersion: 4 });
  await samplesApi.deletePlan('a', 'p', 5, 4); assert.equal(calls.at(-1).url, '/api/samples/a/plans/p?expectedActivityVersion=5&expectedPlanVersion=4');
  await samplesApi.deleteDraft('a', 6); assert.equal(calls.at(-1).url, '/api/samples/a?expectedVersion=6');
  await samplesApi.list({ status: 'ACTIVE', kind: 'SAMPLE', q: ' cotton ', includeArchived: true });
  assert.equal(calls.at(-1).url, '/api/samples?status=ACTIVE&kind=SAMPLE&q=cotton&includeArchived=true');
  await samplesApi.getPlan('a', 'p'); assert.equal(calls.at(-1).url, '/api/samples/a/plans/p');
});
test('409 conflicts retain machine codes and financial writes are not retried', async () => {
  backend(() => Response.json({ status: 409, error: 'stale_plan_version', message: 'Stale' }, { status: 409 }));
  await assert.rejects(samplesApi.approvePlan('a', 'p', { expectedActivityVersion: 0, expectedPlanVersion: 0 }), error => error.status === 409 && error.code === 'stale_plan_version');
  assert.equal(calls.filter(call => call.url.includes('/approve')).length, 1);
});
test('active business/session invalidation aborts in-flight sample data', async () => {
  let finish; let announce; const reached = new Promise(resolve => { announce = resolve; });
  backend(() => new Promise(resolve => { finish = resolve; announce(); }));
  const pending = samplesApi.summary('a'); await reached; client.invalidatePrivateRequests(); client.allowPrivateRequests();
  finish(Response.json({ businessId: 'old-business' })); await assert.rejects(pending, error => error.name === 'AbortError');
});
test('money display preserves all authoritative digits and unknown never renders as zero', () => {
  assert.equal(form.formatMoney('1234.123456789012', 'en'), 'PKR 1,234.123456789012');
  assert.equal(form.formatMoney('-800.00', 'ur'), '- 800.00 روپے'); assert.equal(form.formatMoney(null, 'en'), '—'); assert.equal(form.formatMoney('0.00', 'en'), 'PKR 0.00');
});
test('route permissions preserve F04, F05 and sample read-only access without enabling future modules', () => {
  for (const route of ['/samples', '/samples/a']) { assert.equal(routePermission(route), 'FINANCIAL_DATA_READ'); assert.equal(routeModule(route), null); }
  for (const route of ['/samples/new', '/projects/new', '/invoices/new', '/bills/new']) assert.equal(routePermission(route), 'RECORD_CREATE_UPDATE');
  for (const file of ['Sidebar.tsx', 'MobileNav.tsx']) {
    const source = readFileSync(new URL(`../src/components/layout/${file}`, import.meta.url), 'utf8');
    for (const route of ['/samples', '/projects', '/reports', '/estimates', '/invoices', '/bills']) assert.ok(source.includes(route), `${file}: ${route}`);
  }
});
test('EN and UR copies have matching keys and explicitly describe planned and unknown semantics', () => {
  function keys(en, ur) { assert.deepEqual(Object.keys(en).sort(), Object.keys(ur).sort()); for (const key of Object.keys(en)) { if (typeof en[key] === 'object') keys(en[key], ur[key]); else assert.ok(typeof ur[key] === 'string' && ur[key].length > 0, key); } }
  keys(samplesEn, samplesUr); assert.equal(samplesCopy('en'), samplesEn); assert.equal(samplesCopy('ur'), samplesUr);
  assert.match(samplesEn.disclosure, /NOT AVAILABLE IN THIS PHASE/); assert.match(samplesEn.planned, /PLANNED ONLY/); assert.match(samplesUr.unknownRecovery, /[\u0600-\u06ff]/);
  for (const field of ['plannedCost', 'plannedRecovery', 'plannedContribution']) assert.match(samplesEn[field], /Planned/);
});
test('sample financial UI never coerces money to floating point or calculates client totals', () => {
  for (const file of ['../src/lib/samples/form.ts', '../src/lib/api/samples.ts', '../src/components/samples/SampleShared.tsx', '../src/components/samples/SamplePlanEditor.tsx', '../src/components/samples/SampleDetailWorkspace.tsx']) {
    const source = ts.createSourceFile(file, readFileSync(new URL(file, import.meta.url), 'utf8'), ts.ScriptTarget.Latest, true, file.endsWith('tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
    const violations = [];
    function visit(node) { if (ts.isCallExpression(node) && /^(Number|parseFloat|parseInt)$/.test(node.expression.getText(source))) violations.push(node.getText(source)); if (ts.isPropertyAccessExpression(node) && ['toFixed', 'toPrecision', 'valueAsNumber'].includes(node.name.text)) violations.push(node.getText(source)); ts.forEachChild(node, visit); }
    visit(source); assert.deepEqual(violations, [], file);
  }
});