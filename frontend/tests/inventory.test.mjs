import assert from 'node:assert/strict';
import { after, afterEach, mock, test } from 'node:test';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

// F08 Phase A: inventory catalog drafts, API paths, EN/UR copy and non-authoritative preview labelling.
const directory = mkdtempSync(path.join(tmpdir(), 'finsight-inventory-contracts-'));
const files = {
  client: '../src/lib/api/client.ts',
  form: '../src/lib/inventory/form.ts',
  inventory: '../src/lib/api/inventory.ts',
  copy: '../src/lib/i18n/translations/inventory.ts',
  navigation: '../src/lib/api/navigation.ts',
};
for (const [name, file] of Object.entries(files)) {
  const raw = readFileSync(new URL(file, import.meta.url), 'utf8').replace(/from '@\/lib\/inventory\/form'/g, "from './form'");
  writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(raw, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText);
}
const require = createRequire(import.meta.url);
const client = require(path.join(directory, 'client.js'));
const form = require(path.join(directory, 'form.js'));
const { inventoryApi } = require(path.join(directory, 'inventory.js'));
const { inventoryEn, inventoryUr, inventoryCopy } = require(path.join(directory, 'copy.js'));
const { routePermission } = require(path.join(directory, 'navigation.js'));
const source = (file) => readFileSync(new URL(file, import.meta.url), 'utf8');

after(() => rmSync(directory, { recursive: true, force: true }));
afterEach(() => mock.restoreAll());

test('item drafts validate the server contract and produce closed payloads with no stock fields', () => {
  const draft = { ...form.initialItemDraft(), sku: ' rm-01 ', name: ' Cotton ', unit: 'kg', category: ' ', reorderLevel: '2.500000' };
  assert.deepEqual(form.validateItemDraft(draft), {});
  const payload = form.itemPayload(draft);
  assert.deepEqual(payload, { sku: 'RM-01', name: 'Cotton', unit: 'kg', itemKind: 'RAW_MATERIAL', category: null, reorderLevel: '2.500000' });
  assert.deepEqual(Object.keys(form.itemUpdatePayload(draft, 3)).sort(),
    ['category', 'expectedVersion', 'itemKind', 'name', 'reorderLevel', 'sku', 'unit']);
  for (const key of ['quantity', 'onHand', 'value', 'averageCost', 'businessId', 'status']) assert.equal(key in payload, false, key);

  assert.deepEqual(form.validateItemDraft({ ...draft, sku: '', name: ' ', unit: '' }), { sku: 'required', name: 'required', unit: 'required' });
  assert.equal(form.validateItemDraft({ ...draft, sku: 'bad sku' }).sku, 'sku');
  assert.equal(form.validateItemDraft({ ...draft, sku: '-RM' }).sku, 'sku');
  assert.equal(form.validateItemDraft({ ...draft, name: 'x'.repeat(161) }).name, 'text');
  for (const bad of ['-1', '1e3', '1,000', '1.0000001', '1234567890']) {
    assert.equal(form.validateItemDraft({ ...draft, reorderLevel: bad }).reorderLevel, 'quantity', bad);
  }
  assert.deepEqual(form.draftFromItem({ sku: 'A', name: 'n', unit: 'u', itemKind: 'FINISHED_GOOD', category: null, reorderLevel: null }),
    { sku: 'A', name: 'n', unit: 'u', itemKind: 'FINISHED_GOOD', category: '', reorderLevel: '' });
  assert.deepEqual(form.inventoryItemKinds, ['RAW_MATERIAL', 'RESALE_GOOD', 'CONSUMABLE', 'FINISHED_GOOD']);
});

test('preview drafts never send an issue value and reject inexact input before the request', () => {
  const draft = {
    openingQuantity: '10', openingValue: '1000',
    steps: [
      { key: 'a', type: 'RECEIPT', quantity: '10', value: '2000' },
      { key: 'b', type: 'ISSUE', quantity: '4', value: 'ignored' },
    ],
  };
  assert.deepEqual(form.validatePreviewDraft(draft), {});
  assert.deepEqual(form.previewPayload(draft), {
    openingQuantity: '10', openingValue: '1000',
    steps: [{ type: 'RECEIPT', quantity: '10', value: '2000' }, { type: 'ISSUE', quantity: '4' }],
  });
  const errors = form.validatePreviewDraft({
    openingQuantity: '', openingValue: '1.00001',
    steps: [{ key: 'a', type: 'RECEIPT', quantity: '0', value: '' }, { key: 'b', type: 'ISSUE', quantity: '1.1234567', value: '' }],
  });
  assert.deepEqual(errors, {
    openingQuantity: 'required', openingValue: 'value',
    'steps[0].quantity': 'positive', 'steps[0].value': 'required', 'steps[1].quantity': 'quantity',
  });
  assert.deepEqual(form.mapInventoryFieldErrors({
    'steps[2].quantity': 'insufficient_stock', openingValue: 'decimal_invalid', reorderLevel: 'decimal_invalid', sku: 'sku_invalid',
  }), { 'steps[2].quantity': 'insufficient', openingValue: 'value', reorderLevel: 'quantity', sku: 'sku' });
  assert.equal(form.isInventoryConflictCode('stale_inventory_item_version'), true);
  assert.equal(form.isInventoryConflictCode('inventory_invalid'), false);
  assert.notEqual(form.newPreviewStep('ISSUE').key, form.newPreviewStep('ISSUE').key);
});

test('inventory API uses the bounded item and stateless preview endpoints with CSRF on writes', async () => {
  client.resetCsrf();
  client.allowPrivateRequests();
  const calls = [];
  mock.method(globalThis, 'fetch', async (url, init = {}) => {
    calls.push({ url: String(url), method: init.method ?? 'GET', body: init.body, headers: init.headers });
    if (String(url).endsWith('/api/auth/csrf')) {
      return new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'fixture' }), { status: 200 });
    }
    return new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  });
  await inventoryApi.list({ kind: 'CONSUMABLE', q: ' tape ', includeArchived: true });
  await inventoryApi.get('a/b');
  await inventoryApi.create({ sku: 'A', name: 'n', unit: 'u', itemKind: 'CONSUMABLE', category: null, reorderLevel: null });
  await inventoryApi.update('id1', { sku: 'A', name: 'n', unit: 'u', itemKind: 'CONSUMABLE', category: null, reorderLevel: null, expectedVersion: 2 });
  await inventoryApi.archive('id1', 3);
  await inventoryApi.previewValuation({ openingQuantity: '0', openingValue: '0', steps: [] });
  const requests = calls.filter(call => !call.url.endsWith('/api/auth/csrf')).map(call => `${call.method} ${call.url}`);
  assert.deepEqual(requests, [
    'GET /api/inventory/items?kind=CONSUMABLE&q=tape&includeArchived=true',
    'GET /api/inventory/items/a%2Fb',
    'POST /api/inventory/items',
    'PUT /api/inventory/items/id1',
    'POST /api/inventory/items/id1/archive',
    'POST /api/inventory/valuation/preview',
  ]);
  assert.equal(calls.find(call => call.url.endsWith('/archive')).body, JSON.stringify({ expectedVersion: 3 }));
  for (const call of calls.filter(call => call.method !== 'GET')) {
    assert.equal(new Headers(call.headers).get('X-XSRF-TOKEN'), 'fixture', call.url);
  }
});

test('EN and UR copy have identical structure and the required preview label', () => {
  const shape = (value) => (value && typeof value === 'object'
    ? Object.fromEntries(Object.entries(value).map(([key, inner]) => [key, shape(inner)])) : typeof value);
  assert.deepEqual(shape(inventoryUr), shape(inventoryEn));
  assert.equal(inventoryEn.preview.banner, 'PLANNING PREVIEW — NO STOCK IS RECORDED');
  assert.match(inventoryUr.preview.banner, /پیش منظر/);
  assert.match(inventoryUr.preview.banner, /اسٹاک/);
  assert.equal(inventoryCopy('ur'), inventoryUr);
  assert.equal(inventoryCopy('en'), inventoryEn);
  const walk = (value, prefix = '') => Object.entries(value).flatMap(([key, inner]) =>
    typeof inner === 'object' ? walk(inner, `${prefix}${key}.`) : [[`${prefix}${key}`, inner]]);
  for (const [key, value] of walk(inventoryUr)) assert.ok(String(value).trim(), `Urdu copy missing: ${key}`);
  // Preview copy must not claim accounting authority.
  assert.match(inventoryEn.preview.policyNotice, /not approved accounting policy/);
  assert.match(inventoryEn.preview.nonAuthoritative, /Not authoritative/);
});

test('routes are permission-gated by FINANCIAL_DATA_READ and inventory is a live configuration module', () => {
  assert.equal(routePermission('/inventory'), 'FINANCIAL_DATA_READ');
  assert.equal(routePermission('/inventory/valuation-preview'), 'FINANCIAL_DATA_READ');
  const registry = readFileSync(new URL('../../backend/src/main/java/com/app/sme_health_backend/configuration/service/ModuleRegistry.java', import.meta.url), 'utf8');
  // Product completion: the authoritative perpetual inventory (F08 ledger) makes the module live.
  assert.match(registry, /live\("inventory", "transactions"\)/);
  assert.doesNotMatch(source('../src/lib/api/configuration.ts'), /inventory/);
  const sidebar = source('../src/components/layout/Sidebar.tsx');
  assert.match(sidebar, /\{ label: inventoryCopy\(locale\)\.nav, href: '\/inventory', icon: Boxes \}/);
  assert.match(source('../src/components/layout/MobileNav.tsx'), /data-testid="mobile-inventory-action"/);
  assert.match(source('../src/app/inventory/page.tsx'), /<InventoryRegister \/>/);
  assert.match(source('../src/app/inventory/valuation-preview/page.tsx'), /<ValuationPreviewSandbox \/>/);
});

test('register shows catalog facts only and the sandbox is labelled non-authoritative', () => {
  const register = source('../src/components/inventory/InventoryRegister.tsx');
  for (const forbidden of [/onHand/i, /averageUnitCost/, /stockValue/i, /cogs/i, /previewValuation/]) {
    assert.doesNotMatch(register, forbidden, String(forbidden));
  }
  assert.match(register, /can\('RECORD_CREATE_UPDATE'\)/);
  assert.match(register, /canWrite && item\.status === 'ACTIVE'/);
  assert.match(register, /data-testid="inventory-catalog-banner"/);
  const sandbox = source('../src/components/inventory/ValuationPreviewSandbox.tsx');
  assert.match(sandbox, /data-testid="valuation-preview-banner"/);
  assert.match(sandbox, /response\.authoritative !== false \|\| response\.persisted !== false/);
  assert.match(sandbox, /can\('FINANCIAL_DATA_READ'\)/);
  assert.doesNotMatch(sandbox, /RECORD_CREATE_UPDATE|localStorage|sessionStorage/);
  // Numeric inputs stay left-to-right in RTL; back arrow mirrors.
  assert.ok((sandbox.match(/dir="ltr" inputMode="decimal"/g) ?? []).length >= 4);
  assert.match(sandbox, /<ArrowLeft className="w-4 h-4 me-1\.5 rtl:rotate-180"/);
  for (const file of [register, sandbox]) assert.doesNotMatch(file, /--color-semantic-danger/);
});
