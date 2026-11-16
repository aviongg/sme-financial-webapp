import assert from 'node:assert/strict';
import { test } from 'node:test';
import { configurationFixture } from './fixtures/phase-two.mjs';
import { workspaceFixture } from './fixtures/workspace.mjs';
import { monthlyFixture } from './fixtures/monthly-v52.mjs';
import { copilotFixture } from './fixtures/copilot.mjs';

test('historical configuration fixture distinguishes never-configured, explicitly disabled and permission-denied modules', () => {
  const defaults = configurationFixture();
  assert.equal(defaults.modules.length, 15);
  assert.ok(defaults.modules.every(module => module.implemented && module.enabled && module.available));
  assert.deepEqual(defaults.requestedModules, defaults.modules.map(module => module.key));
  const configured = configurationFixture({ configured: true, requestedModules: ['core_finance', 'transactions', 'inventory'] });
  assert.deepEqual(configured.modules.find(module => module.key === 'inventory').dependencies, ['transactions']);
  assert.deepEqual(configured.modules.find(module => module.key === 'production').dependencies, ['inventory']);
  assert.equal(configured.modules.find(module => module.key === 'documents').reason, 'DISABLED');
  const viewer = configurationFixture({ role: 'VIEWER' });
  assert.equal(viewer.modules.find(module => module.key === 'documents').reason, 'PERMISSION_REQUIRED');
  assert.equal(viewer.modules.find(module => module.key === 'inventory').available, true);
  assert.equal(viewer.canManage, false);
});

test('historical workspace lists current implemented product widgets without enabling extra fixture journeys', () => {
  const workspace = workspaceFixture({ dashboard: {} });
  for (const key of ['inventory', 'transactions', 'goals', 'profitability', 'break-even', 'action-center', 'calendar', 'reports']) {
    assert.equal(workspace.catalog.find(item => item.key === key).implemented, true);
    assert.equal(workspace.preferences.find(item => item.key === key).hidden, true);
  }
  assert.equal(workspace.catalog.find(item => item.key === 'ai-explanation').reason, 'AI_UNAVAILABLE');
});

test('supplemental financing fixture rejects role/stale writes and retired totals without mutating either business financial observations', () => {
  const records = new Map([['A', [{ month: '2026-09', cashInflow: 1000 }]], ['B', []]]);
  const before = structuredClone(records), fixture = monthlyFixture(records);
  const request = { url: '/api/records/monthly/financing/2026-09', method: 'PUT', businessId: 'A', canWrite: true,
    payload: { loanOutstanding: '1200.00', interestExpense: null, financingType: 'none', expectedVersion: null } };
  assert.equal(fixture.handle({ ...request, canWrite: false }).status, 403);
  assert.equal(fixture.handle(request).data.version, 0);
  assert.equal(fixture.handle(request).status, 409);
  assert.equal(fixture.handle({ ...request, payload: { ...request.payload, expectedVersion: 0 } }).data.version, 1);
  assert.deepEqual(fixture.financing.get('B'), []);
  assert.equal(fixture.handle({ ...request, url: '/api/records/monthly', method: 'POST' }).status, 409);
  assert.deepEqual(records, before);
});

test('provider fallback fixture retains the real response provenance and never labels a deterministic answer as OpenAI', () => {
  const normal = copilotFixture(), fallback = copilotFixture({ unavailable: true });
  assert.equal(normal.mode, 'openai');
  assert.equal(fallback.version, 'copilot-v1');
  assert.equal(fallback.mode, 'deterministic');
  assert.equal(fallback.reasonCode, 'provider_unavailable');
  assert.equal(fallback.model, null);
  assert.deepEqual(fallback.evidence, normal.evidence);
  assert.match(fallback.answer, /123456789012345\.6700/);
  assert.match(fallback.answer, /UNKNOWN/);
  assert.ok(fallback.tools.every(tool => tool.status === 'OK'));
  const limited = copilotFixture({ limited: true });
  assert.equal(limited.reasonCode, 'rate_limited');
  assert.deepEqual(limited.evidence, []);
});
