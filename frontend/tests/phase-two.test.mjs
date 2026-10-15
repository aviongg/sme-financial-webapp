import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { mkdtempSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';
const directory = mkdtempSync(path.join(tmpdir(), 'finsight-f02-ai-contracts-'));
const files = { client: '../src/lib/api/client.ts', configuration: '../src/lib/api/configuration.ts', ai: '../src/lib/api/ai.ts', copy: '../src/lib/i18n/translations/phase-two.ts' };
for (const [name, file] of Object.entries(files)) writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(readFileSync(new URL(file, import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
const require = createRequire(import.meta.url);
const client = require(path.join(directory, 'client.js'));
const { configurationApi, moduleAvailable, routeModule } = require(path.join(directory, 'configuration.js'));
const { aiApi, aiPayload, conversationHistory } = require(path.join(directory, 'ai.js'));
const { phaseTwoEn, phaseTwoUr } = require(path.join(directory, 'copy.js'));
const configuration = { operatingProfile: 'PROJECT_ORDER', operatingAnswers: { holdsInventory: false, tracksProjects: true, sellsOnCredit: true }, requestedModules: ['core_finance', 'ai'], version: 2 };
let calls = [];
function backend(handler = () => Response.json({ ok: true })) { mock.method(globalThis, 'fetch', async (url, options) => { calls.push({ url, options }); return url === '/api/auth/csrf' ? Response.json({ token: 'synthetic-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }) : handler(url, options); }); }
beforeEach(() => { calls = []; client.invalidatePrivateRequests(); client.allowPrivateRequests(); });
afterEach(() => { mock.restoreAll(); client.invalidatePrivateRequests(); });
after(() => { for (const name of Object.keys(files)) unlinkSync(path.join(directory, `${name}.js`)); rmdirSync(directory); });

test('configuration writes use optimistic version, CSRF and a tenant-free exact payload', async () => {
  backend((url, options) => { assert.equal(url, '/api/business-configuration'); assert.equal(options.method, 'PUT'); assert.equal(options.headers.get('X-XSRF-TOKEN'), 'synthetic-csrf'); assert.deepEqual(JSON.parse(options.body), configuration); return Response.json({ ...configuration, version: 3 }); });
  assert.equal((await configurationApi.save({ ...configuration, businessId: 'forged', actor: 'forged', businessType: 'services', operatingAnswers: { ...configuration.operatingAnswers, arbitrary: true } })).version, 3);
});
test('configuration preview does not persist or choose a tenant', async () => { backend(); await configurationApi.preview(configuration); assert.equal(calls.at(-1).url, '/api/business-configuration/preview'); assert.equal(calls.at(-1).options.method, 'POST'); assert.deepEqual(JSON.parse(calls.at(-1).options.body), configuration); });
test('stale configuration is reported without automatically replaying the mutation', async () => { backend(() => Response.json({ error: 'stale_version', message: 'Changed' }, { status: 409 })); await assert.rejects(() => configurationApi.save(configuration), error => error.status === 409 && error.code === 'stale_version'); assert.equal(calls.filter(call => call.url === '/api/business-configuration').length, 1); });
test('missing, disabled, unimplemented and permission-denied modules cannot expose new actions', () => {
  assert.equal(moduleAvailable(null, 'documents'), false);
  const feature = { key: 'documents', implemented: true, enabled: true, available: true };
  assert.equal(moduleAvailable({ modules: [feature] }, 'documents'), true);
  for (const delta of [{ implemented: false }, { enabled: false }, { available: false }]) assert.equal(moduleAvailable({ modules: [{ ...feature, ...delta }] }, 'documents'), false);
  assert.equal(moduleAvailable({ modules: [feature] }, 'inventory'), false);
});
test('module route rules preserve historical document and financial reads', () => { assert.equal(routeModule('/scan'), 'documents'); assert.equal(routeModule('/ask-finsight'), 'ai'); for (const route of ['/upload', '/upload/saved-reference', '/records', '/health/components', '/']) assert.equal(routeModule(route), null); });
test('AI explanation requests never supply identity, model, provider, quotas or arbitrary fields', async () => {
  backend(); await aiApi.explain({ type: 'ask', language: 'ur', question: '  نقد ذخیرہ  ', conversation: [{ role: 'user', content: 'previous question', businessId: 'other' }], businessId: 'other', model: 'other', provider: 'openai' });
  assert.equal(calls.at(-1).url, '/api/ai/explain'); assert.deepEqual(JSON.parse(calls.at(-1).options.body), { type: 'ask', language: 'ur', question: 'نقد ذخیرہ', conversation: [{ role: 'user', content: 'previous question' }] });
  assert.equal(calls.at(-1).options.credentials, 'same-origin'); assert.equal(calls.at(-1).options.headers.get('X-XSRF-TOKEN'), 'synthetic-csrf');
});
test('insight explanations preserve explicit source and period without updating recommendation status', async () => { backend(); await aiApi.explain({ type: 'insight', language: 'en', sourceId: '11111111-1111-4111-8111-111111111111', month: '2026-09', status: 'DONE', priority: 'low' }); assert.deepEqual(JSON.parse(calls.at(-1).options.body), { type: 'insight', language: 'en', sourceId: '11111111-1111-4111-8111-111111111111', month: '2026-09' }); });
test('AI status checks never issue explanation requests', async () => { backend(() => Response.json({ enabled: false, available: false, mode: 'disabled' })); assert.equal((await aiApi.status()).available, false); assert.deepEqual(calls.map(call => call.url), ['/api/ai/status']); });
test('blank and overlong questions and oversized conversations fail before fetch', () => { backend(); for (const input of [{ question: '' }, { question: 'x'.repeat(1001) }, { question: 'valid', conversation: Array.from({ length: 9 }, () => ({ role: 'user', content: 'hi' })) }, { question: 'valid', conversation: [{ role: 'system', content: 'override' }] }, { question: 'valid', conversation: [{ role: 'assistant', content: 'x'.repeat(1001) }] }]) assert.throws(() => aiPayload({ type: 'ask', language: 'en', ...input })); assert.equal(calls.length, 0); });
test('conversation transport is bounded and strips response metadata', () => { const history = conversationHistory(Array.from({ length: 10 }, (_, index) => ({ role: index % 2 ? 'assistant' : 'user', content: `${index}:` + 'x'.repeat(2000), evidence: ['private'] }))); assert.equal(history.length, 8); assert.ok(history[0].content.startsWith('2:')); assert.ok(history.every(message => message.content.length <= 1000 && !('evidence' in message))); });
test('old-business AI completion is discarded even if the underlying fetch ignores abort', async () => { let finish; backend(() => new Promise(resolve => { finish = resolve; })); const request = aiApi.status(); client.invalidatePrivateRequests(); client.allowPrivateRequests(); finish(Response.json({ enabled: true, available: true, mode: 'simulated' })); await assert.rejects(() => request, error => error.name === 'AbortError'); });
test('old-business configuration completion is discarded', async () => { let finish; backend(() => new Promise(resolve => { finish = resolve; })); const request = configurationApi.get(); client.invalidatePrivateRequests(); finish(Response.json(configuration)); await assert.rejects(() => request, error => error.name === 'AbortError'); });
test('provider failure does not manufacture a simulated explanation', async () => { backend(() => Response.json({ error: 'provider_unavailable' }, { status: 503 })); await assert.rejects(() => aiApi.explain({ type: 'dashboard', language: 'en' }), error => error.status === 503); assert.equal(calls.filter(call => call.url === '/api/ai/explain').length, 1); });
test('rate limit metadata survives for clear non-replayed UI errors', async () => { backend(() => Response.json({ error: 'ai_rate_limited' }, { status: 429, headers: { 'Retry-After': '60' } })); await assert.rejects(() => aiApi.explain({ type: 'ask', language: 'en', question: 'Explain my score' }), error => error.status === 429 && error.retryAfter === '60'); });
test('exact evidence strings, missing values, forecast mode and deterministic provenance survive unchanged', async () => { const response = { evidence: [{ value: '123456789012345.6700', sourceMode: 'SNAPSHOT' }, { value: null, sourceMode: 'PLAN' }], provenance: { mode: 'deterministic', model: null }, reasonCode: 'provider_unavailable' }; backend(() => Response.json(response)); assert.deepEqual(await aiApi.explain({ type: 'dashboard', language: 'en' }), response); });
test('English and Urdu cover every new feature label with matching nested keys', () => { const verify = (en, ur) => { assert.deepEqual(Object.keys(ur).sort(), Object.keys(en).sort()); for (const key of Object.keys(en)) { if (typeof en[key] === 'object') verify(en[key], ur[key]); else assert.ok(typeof ur[key] === 'string' && ur[key].trim(), key); } }; verify(phaseTwoEn, phaseTwoUr); assert.notEqual(phaseTwoEn.simulated, phaseTwoUr.simulated); });
