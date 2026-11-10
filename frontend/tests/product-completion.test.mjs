import assert from 'node:assert/strict';
import { after, afterEach, mock, test } from 'node:test';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

// Product completion: API adapters (paths, idempotency, no client authority), EN/UR parity and UI guardrails.
const directory = mkdtempSync(path.join(tmpdir(), 'finsight-product-contracts-'));
const files = { client: '../src/lib/api/client.ts', product: '../src/lib/api/product.ts', copy: '../src/lib/i18n/translations/product.ts' };
for (const [name, file] of Object.entries(files)) {
  const raw = readFileSync(new URL(file, import.meta.url), 'utf8').replace(/from '\.\.\/context'/g, "from './client'");
  writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(raw, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText);
}
const require = createRequire(import.meta.url);
const client = require(path.join(directory, 'client.js'));
const api = require(path.join(directory, 'product.js'));
const { productCopies, productCopy } = require(path.join(directory, 'copy.js'));
const source = (file) => readFileSync(new URL(file, import.meta.url), 'utf8');

after(() => rmSync(directory, { recursive: true, force: true }));
afterEach(() => mock.restoreAll());

function capture() {
  const calls = [];
  mock.method(globalThis, 'fetch', async (url, init = {}) => {
    calls.push({ url: String(url), init });
    if (String(url).endsWith('/api/auth/csrf')) {
      return new Response(JSON.stringify({ token: 't', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }), { status: 200 });
    }
    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  });
  client.allowPrivateRequests();
  return calls;
}

function keys(value, prefix = '') {
  if (Array.isArray(value)) return [prefix + '[]'];
  if (value && typeof value === 'object') return Object.keys(value).sort().flatMap(k => keys(value[k], prefix ? `${prefix}.${k}` : k));
  return [prefix];
}

test('EN and UR copy have identical keys and Urdu is actually translated', () => {
  assert.deepEqual(keys(productCopies.ur), keys(productCopies.en));
  assert.equal(productCopy('ur').planning.title, productCopies.ur.planning.title);
  assert.notEqual(productCopies.ur.planning.title, productCopies.en.planning.title);
  assert.match(productCopies.ur.copilot.title, /[؀-ۿ]/);
  assert.equal(productCopies.en.copilot.suggestions.length, productCopies.ur.copilot.suggestions.length);
});

test('financial writes send an Idempotency-Key and never send amounts the server should compute', async () => {
  const calls = capture();
  await api.lifecycleApi.adjust('doc-1', { amount: '100.00', reason: 'Discount', adjustmentDate: '2026-11-10' });
  await api.stockApi.record('issues', { itemId: 'i', quantity: '1', purpose: 'SALE' });
  await api.stockApi.reverse('m-1', 'mistake');
  await api.lifecycleApi.receiveBill('bill-1');
  const writes = calls.filter(c => !c.url.endsWith('/api/auth/csrf'));
  assert.deepEqual(writes.map(c => c.url), ['/api/commercial/documents/doc-1/adjustments', '/api/inventory/movements/issues',
    '/api/inventory/movements/m-1/reverse', '/api/inventory/documents/bills/bill-1/receive']);
  for (const w of writes) {
    assert.equal(w.init.method, 'POST');
    assert.ok(new Headers(w.init.headers).get('Idempotency-Key'), w.url);
  }
  const issue = JSON.parse(writes[1].init.body);
  assert.equal('value' in issue, false, 'Issues are valued by the backend WAC policy, never by the browser');
});

test('read adapters hit the documented endpoints with encoded query parameters', async () => {
  const calls = capture();
  await api.planningApi.pnl('2026-11-01', '2026-11-10');
  await api.insightApi.relationships('DOCUMENT', 'a/b');
  await api.insightApi.calendar(undefined, '2026-12-31');
  await api.copilotApi.ask('profit?', 'ur', []);
  const urls = calls.filter(c => !c.url.endsWith('/api/auth/csrf')).map(c => c.url);
  assert.deepEqual(urls, ['/api/planning/profit-and-loss?from=2026-11-01&to=2026-11-10', '/api/operations/relationships/DOCUMENT/a%2Fb',
    '/api/operations/calendar?to=2026-12-31', '/api/copilot/ask']);
  const ask = JSON.parse(calls.find(c => c.url === '/api/copilot/ask').init.body);
  assert.deepEqual(Object.keys(ask).sort(), ['conversation', 'language', 'question'], 'No business or role is ever sent');
});

test('amounts are formatted exactly from strings, never through Number', () => {
  const shared = source('../src/components/product/shared.tsx');
  assert.match(shared, /never converts to Number, never rounds/);
  assert.doesNotMatch(shared, /parseFloat|Number\(value\)/);
  for (const file of ['CommercialLifecyclePanel.tsx', 'StockWorkspace.tsx', 'ActualsPanels.tsx', 'PlanningWorkspace.tsx', 'OperationsViews.tsx']) {
    const text = source(`../src/components/product/${file}`);
    assert.doesNotMatch(text, /parseFloat\(|Number\([a-z]+\.(amount|value|total)/, file);
  }
});

test('panels are wired into the existing workspaces, navigation and settings', () => {
  assert.match(source('../src/components/commercial/CommercialDocumentWorkspace.tsx'), /<CommercialLifecyclePanel documentId=\{document\.id\} kind=\{kind\}\/>/);
  assert.match(source('../src/components/projects/ProjectDetailWorkspace.tsx'), /<ProjectProfitabilityPanel projectId=\{project\.id\}\/>/);
  assert.match(source('../src/components/samples/SampleDetailWorkspace.tsx'), /<SampleActualsPanel sampleId=\{activity\.id\}\/>/);
  assert.match(source('../src/components/estimates/EstimateWorkspace.tsx'), /<EstimateJourneyPanel /);
  assert.match(source('../src/components/reports/BusinessFinancialReportView.tsx'), /<FinancialPackView\/>/);
  assert.match(source('../src/components/integration/LiveSettings.tsx'), /<WhatsAppLinkCard\/>/);
  assert.match(source('../src/app/ask-finsight/page.tsx'), /CopilotChat/);
  const sidebar = source('../src/components/layout/Sidebar.tsx');
  for (const href of ['/inventory/stock', '/planning', '/intelligence', '/operations/calendar']) assert.ok(sidebar.includes(`'${href}'`), href);
});

test('posting controls are presentation-gated to OWNER/ACCOUNTANT and Copilot shows evidence and reasons', () => {
  const shared = source('../src/components/product/shared.tsx');
  assert.match(shared, /canPresentPosting/);
  const ops = source('../src/components/product/OperationsViews.tsx');
  assert.match(ops, /c\.evidence/);
  assert.match(ops, /c\.reasons\[t\.r\.reasonCode\]/);
  assert.match(ops, /aria-live="polite"/);
  const panel = source('../src/components/product/CommercialLifecyclePanel.tsx');
  assert.match(panel, /useCanPost\(\)/);
  assert.match(panel, /c\.blockers\[b\]/);
});
