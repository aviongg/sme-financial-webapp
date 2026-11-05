import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { mkdtempSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';
const directory = mkdtempSync(path.join(tmpdir(), 'finsight-workspace-contracts-'));
const files = { client: '../src/lib/api/client.ts', workspace: '../src/lib/api/workspace.ts', layout: '../src/lib/workspace/layout.ts', copy: '../src/lib/i18n/translations/workspace.ts' };
for (const [name, file] of Object.entries(files)) writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(readFileSync(new URL(file, import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
const require = createRequire(import.meta.url), client = require(path.join(directory, 'client.js'));
const { workspaceApi, presetKeys } = require(path.join(directory, 'workspace.js'));
const { editableLayout, moveWidget, changeWidget, implementedWidgetKeys, rebaseDraft, cashNetDirection, resolveRecordNet } = require(path.join(directory, 'layout.js'));
const { workspaceEn, workspaceUr, widgetTitle } = require(path.join(directory, 'copy.js'));
let calls = [];
const versions = { version: 4, registryVersion: 'f13-v1', configurationVersion: 2 };
const widgets = [{ key: 'health-score', hidden: false, order: 0, size: 'wide' }, { key: 'cash-flow', hidden: true, order: 1, size: 'compact' }];
function backend(handler = () => Response.json({ ok: true })) { mock.method(globalThis, 'fetch', async (url, options) => { calls.push({ url, options }); return url === '/api/auth/csrf' ? Response.json({ token: 'synthetic-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }) : handler(url, options); }); }
beforeEach(() => { calls = []; client.invalidatePrivateRequests(); client.allowPrivateRequests(); });
afterEach(() => { mock.restoreAll(); client.invalidatePrivateRequests(); });
after(() => { for (const name of Object.keys(files)) unlinkSync(path.join(directory, `${name}.js`)); rmdirSync(directory); });

test('workspace saves only personal presentation, optimistic versions and real CSRF transport', async () => {
  backend(); await workspaceApi.save({ ...versions, widgets: widgets.map(widget => ({ ...widget, businessId: 'other', html: '<script>', amount: 999 })), userId: 'other', businessId: 'other', presetKey: 'services', permission: 'OWNER' });
  const call = calls.at(-1); assert.equal(call.url, '/api/workspace/preferences'); assert.equal(call.options.method, 'PUT');
  assert.equal(call.options.credentials, 'same-origin'); assert.equal(call.options.headers.get('X-XSRF-TOKEN'), 'synthetic-csrf');
  assert.deepEqual(JSON.parse(call.options.body), { ...versions, widgets });
});
test('current workspace retrieves financial data in one aggregate request without per-widget calls', async () => {
  const response = { widgets, dashboard: { score: null }, records: [{ month: '2025-01', revenue: 0, cogs: null }, { month: '2026-09', revenue: 1234.56, cogs: null }], cashSummaryByPeriod: { all: { net: 900.13 } } };
  backend(() => Response.json(response)); assert.deepEqual(await workspaceApi.get(), response); assert.deepEqual(calls.map(call => call.url), ['/api/workspace']);
});
test('all four preset previews use read-only routes and never select a tenant or persist a layout', async () => {
  backend(); for (const key of presetKeys) await workspaceApi.preview(key);
  assert.deepEqual(calls.map(call => call.url), presetKeys.map(key => `/api/workspace/presets/${key}`));
  assert.ok(calls.every(call => call.options.method === 'GET' && call.options.body === undefined));
});
test('unsupported preview keys fail before any network request', () => { backend(); for (const key of ['../businesses', 'retail', 'https://example.com', '']) assert.throws(() => workspaceApi.preview(key)); assert.equal(calls.length, 0); });
test('reset uses current versions, same-origin CSRF and cannot reset a supplied user or business', async () => {
  backend(); await workspaceApi.reset({ ...versions, userId: 'other', businessId: 'other', widgets, presetKey: 'project-order' });
  const call = calls.at(-1); assert.equal(call.url, '/api/workspace/reset'); assert.equal(call.options.method, 'POST'); assert.deepEqual(JSON.parse(call.options.body), versions); assert.equal(call.options.headers.get('X-XSRF-TOKEN'), 'synthetic-csrf');
});
test('stale layout and configuration conflicts retain metadata and are never automatically replayed', async () => {
  for (const error of ['stale_version', 'stale_configuration', 'stale_registry']) {
    mock.restoreAll(); calls = []; client.resetCsrf(); backend(() => Response.json({ error }, { status: 409 }));
    await assert.rejects(() => workspaceApi.save({ ...versions, widgets }), failure => failure.status === 409 && failure.code === error);
    assert.equal(calls.filter(call => call.url === '/api/workspace/preferences').length, 1);
  }
});
test('session or business changes discard a late aggregate even if fetch ignores abort', async () => {
  let finish; backend(() => new Promise(resolve => { finish = resolve; })); const request = workspaceApi.get();
  client.invalidatePrivateRequests(); client.allowPrivateRequests(); finish(Response.json({ dashboard: { businessId: 'old-business' } }));
  await assert.rejects(() => request, error => error.name === 'AbortError');
});
test('closing a preset preview cancels its late response', async () => {
  let finish; backend(() => new Promise(resolve => { finish = resolve; })); const controller = new AbortController();
  const request = workspaceApi.preview('services', controller.signal); controller.abort(); finish(Response.json({ presetKey: 'services' }));
  await assert.rejects(() => request, error => error.name === 'AbortError');
});
test('editing clones authoritative hidden preferences without mutating the saved workspace', () => {
  const saved = { preferences: widgets, widgets: [widgets[0]], catalog: [] }, draft = editableLayout(saved);
  assert.deepEqual(draft, widgets); draft[1].hidden = false; draft[0].size = 'compact';
  assert.equal(saved.preferences[1].hidden, true); assert.equal(saved.preferences[0].size, 'wide');
});
test('keyboard moves skip hidden cards and preserve unique explicit positions', () => {
  const original = [...widgets, { key: 'financial-summary', hidden: false, order: 2, size: 'wide' }];
  const moved = moveWidget(original, 'financial-summary', -1);
  assert.deepEqual(moved.map(widget => widget.key), ['financial-summary', 'cash-flow', 'health-score']);
  assert.deepEqual(moved.map(widget => widget.order), [0, 1, 2]); assert.equal(moved[1].hidden, true);
  assert.equal(original[0].key, 'health-score'); assert.equal(moveWidget(original, 'health-score', -1), original);
});
test('required, restricted and unsupported sizes cannot be selected by customization helpers', () => {
  const health = { key: 'health-score', available: true, required: true, sizes: ['wide'] };
  assert.equal(changeWidget(widgets, health, { hidden: true }), widgets);
  assert.equal(changeWidget(widgets, health, { size: 'compact' }), widgets);
  assert.equal(changeWidget(widgets, { ...health, available: false }, { hidden: false }), widgets);
  const cash = { key: 'cash-flow', available: true, required: false, sizes: ['compact', 'wide'] };
  assert.equal(changeWidget(widgets, cash, { hidden: false })[1].hidden, false); assert.equal(widgets[1].hidden, true);
});
test('conflict reload rebases valid draft changes onto latest server workspace and drops newly unavailable widgets', () => {
  const catalog = [
    { key: 'health-score', available: true, required: true, sizes: ['wide'] },
    { key: 'cash-flow', available: true, required: false, sizes: ['compact', 'wide'] },
    { key: 'next-step', available: true, required: false, sizes: ['compact', 'wide'] },
    { key: 'ai-explanation', available: true, required: false, sizes: ['compact', 'wide'] },
  ];
  const previous = {
    catalog,
    preferences: [
      { key: 'health-score', hidden: false, order: 0, size: 'wide' },
      { key: 'cash-flow', hidden: false, order: 1, size: 'wide' },
      { key: 'next-step', hidden: false, order: 2, size: 'compact' },
      { key: 'ai-explanation', hidden: false, order: 3, size: 'wide' },
    ],
  };
  // User draft hid cash-flow and resized ai-explanation, leaving next-step untouched.
  const draft = [
    { key: 'health-score', hidden: false, order: 0, size: 'wide' },
    { key: 'cash-flow', hidden: true, order: 1, size: 'wide' },
    { key: 'next-step', hidden: false, order: 2, size: 'compact' },
    { key: 'ai-explanation', hidden: false, order: 3, size: 'compact' },
  ];
  // Concurrent server state resized next-step to wide and disabled ai-explanation.
  const latest = {
    catalog: catalog.map(item => item.key === 'ai-explanation' ? { ...item, available: false, reason: 'AI_UNAVAILABLE' } : item),
    preferences: [
      { key: 'health-score', hidden: false, order: 0, size: 'wide' },
      { key: 'cash-flow', hidden: false, order: 1, size: 'wide' },
      { key: 'next-step', hidden: false, order: 2, size: 'wide' },
    ],
  };
  const rebased = rebaseDraft(previous, draft, latest);
  assert.deepEqual(rebased, [
    { key: 'health-score', hidden: false, order: 0, size: 'wide' },
    { key: 'cash-flow', hidden: true, order: 1, size: 'wide' },
    { key: 'next-step', hidden: false, order: 2, size: 'wide' },
  ]);
});
test('cash-flow null semantics consume authoritative net and classify unknown net as unavailable rather than deficit', () => {
  assert.equal(cashNetDirection(null), 'unavailable');
  assert.equal(cashNetDirection(undefined), 'unavailable');
  assert.equal(cashNetDirection(Number.NaN), 'unavailable');
  assert.equal(cashNetDirection(0), 'surplus');
  assert.equal(cashNetDirection(250.5), 'surplus');
  assert.equal(cashNetDirection(-0.01), 'deficit');
  assert.equal(resolveRecordNet({ month: '2026-09', cashInflow: 500, cashOutflow: 100 }, [{ month: '2026-09', net: 400.25 }]), 400.25);
  assert.equal(resolveRecordNet({ month: '2026-09', cashInflow: 500, cashOutflow: 100 }, [{ month: '2026-09', net: null }]), null);
  assert.equal(resolveRecordNet({ month: '2026-09', cashInflow: 500, cashOutflow: 100, netCashFlow: null }), null);
  assert.equal(resolveRecordNet({ month: '2026-09', cashInflow: null, cashOutflow: 100 }), null);
  assert.equal(resolveRecordNet({ month: '2026-09', cashInflow: 500, cashOutflow: null }), null);
});
test('English and Urdu cover the complete current and unavailable future registry with matching copy', () => {
  function verify(en, ur) { assert.deepEqual(Object.keys(en).sort(), Object.keys(ur).sort()); for (const key of Object.keys(en)) { if (typeof en[key] === 'object') verify(en[key], ur[key]); else assert.ok(typeof ur[key] === 'string' && ur[key].trim(), key); } }
  verify(workspaceEn, workspaceUr);
  const future = ['transactions', 'receivables', 'payables', 'invoices', 'projects', 'costing', 'inventory', 'production', 'commissions', 'multi-currency', 'budgets', 'funding-gap', 'reports'];
  for (const key of [...implementedWidgetKeys, ...future]) for (const copy of [workspaceEn, workspaceUr]) assert.notEqual(widgetTitle(key, copy), copy.unavailable, key);
  for (const key of future) assert.equal(implementedWidgetKeys.has(key), false);
});
