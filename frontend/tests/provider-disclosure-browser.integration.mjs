// DEF-08 provider-transparency browser regression: production Next build + synthetic loopback API through a gateway.
// No Spring, database, AI provider or WhatsApp provider is involved; the fixture never computes or mutates finance.
// Prerequisites: pnpm build:release and pnpm browser:install. Run: pnpm test:browser:provider-disclosure
import http from 'node:http';
import net from 'node:net';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { configurationFixture } from './fixtures/phase-two.mjs';
import { workspaceFixture } from './fixtures/workspace.mjs';
import { EXACT_VALUE, copilotFixture, deliveryFixture } from './fixtures/provider-disclosure.mjs';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const out = path.resolve(process.env.BROWSER_ARTIFACT_DIR || path.join(frontendDir, 'test-artifacts', 'provider-disclosure-browser'));
mkdirSync(out, { recursive: true });
const apiPort = Number(process.env.DISCLOSURE_API_PORT || 18291), nextPort = Number(process.env.DISCLOSURE_NEXT_PORT || 13292), gatewayPort = Number(process.env.DISCLOSURE_GATEWAY_PORT || 13291);
const origin = `http://127.0.0.1:${gatewayPort}`;
const businessId = '11111111-1111-4111-8111-111111111111';

// ------------------------------------------------------------------ synthetic state
let signedIn = false, language = 'en', statusMode = 'openai', aiModule = true;
let next = { unavailable: false, unsupported: false, limited: false };
const deliveries = [
  deliveryFixture({ id: '44444444-4444-4444-8444-444444444441', providerName: 'mock', deliveryStatus: 'SENT' }),
  deliveryFixture({ id: '44444444-4444-4444-8444-444444444442', providerName: 'meta', deliveryStatus: 'SENT', targetMonth: '2026-09' }),
  deliveryFixture({ id: '44444444-4444-4444-8444-444444444443', providerName: 'disabled', deliveryStatus: 'FAILED', failureReason: 'provider_disabled', targetMonth: '2026-08' }),
  deliveryFixture({ id: '44444444-4444-4444-8444-444444444444', providerName: null, deliveryStatus: 'PENDING', targetMonth: '2026-07' }),
];
const events = [], checks = [], errors = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const config = () => configurationFixture({ requestedModules: aiModule ? ['core_finance', 'documents', 'whatsapp', 'ai'] : ['core_finance', 'documents', 'whatsapp'] });
const business = () => ({ businessId, businessName: 'Disclosure fixture', businessType: 'trade', languagePreference: language, role: 'OWNER', membershipStatus: 'ACTIVE', active: true });
const profile = () => ({ businessId, businessType: 'trade', languagePreference: language, whatsappNumber: '+923001234567', whatsappOptIn: true, createdAt: '2026-10-12' });
const respond = (res, value, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(value)); };
const api = http.createServer(async (req, res) => {
  let raw = ''; for await (const chunk of req) raw += chunk; let body = {}; try { body = JSON.parse(raw); } catch { /* empty */ }
  const url = req.url.split('?')[0], { method } = req; events.push({ url, method });
  if (url === '/api/auth/csrf') { res.setHeader('Set-Cookie', 'FINSIGHT_TEST=disclosure; HttpOnly; SameSite=Lax; Path=/'); return respond(res, { token: 'synthetic-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }); }
  if (!['GET', 'HEAD'].includes(method) && req.headers['x-xsrf-token'] !== 'synthetic-csrf') return respond(res, { error: 'access_denied' }, 403);
  if (url === '/api/auth/login') { signedIn = true; return respond(res, { id: 'synthetic-owner', email: body.email, fullName: 'Synthetic owner', authStage: 'FULLY_AUTHENTICATED', platformRole: null }); }
  if (url === '/api/auth/logout') { signedIn = false; res.writeHead(204); return res.end(); }
  if (!signedIn) return respond(res, { error: 'session_expired' }, 401);
  if (url === '/api/auth/me') return respond(res, { id: 'synthetic-owner', email: 'owner@example.test', fullName: 'Synthetic owner', authStage: 'FULLY_AUTHENTICATED', platformRole: null });
  if (url === '/api/businesses') return respond(res, [business()]);
  if (url === '/api/businesses/active') return respond(res, business());
  if (url === '/api/profile') return respond(res, profile());
  if (url === '/api/profile/language') { language = body.languagePreference; return respond(res, profile()); }
  if (url === '/api/whatsapp/deliveries') return respond(res, deliveries);
  if (['/api/memberships', '/api/invitations', '/api/records/monthly', '/api/documents'].includes(url)) return respond(res, []);
  const dashboard = { businessId, profile: profile(), score: null, topInsight: null, topRecommendation: null, cashFlowHistory: [], trendProjection: null, hasHistory: false };
  const aiStatus = { enabled: aiModule, available: aiModule && statusMode === 'openai', mode: statusMode === 'openai' ? 'live' : 'deterministic', reasonCode: statusMode === 'openai' ? null : 'ai_disabled', model: null, maxQuestionLength: 1000, maxConversationTurns: 8 };
  if (url === '/api/dashboard') return respond(res, dashboard);
  if (url === '/api/workspace') return respond(res, workspaceFixture({ dashboard, configuration: config(), aiStatus, role: 'OWNER' }));
  if (url === '/api/business-configuration' && method === 'GET') return respond(res, config());
  if (url === '/api/ai/status') return respond(res, aiStatus);
  if (url === '/api/copilot/status') return respond(res, { version: 'copilot-v1', moduleEnabled: aiModule, mode: aiModule ? statusMode : 'disabled', providerReason: statusMode === 'openai' ? '' : 'ai_disabled', tools: ['health_evidence', 'budget_goal_status'] });
  if (url === '/api/copilot/ask' && method === 'POST') {
    const reply = copilotFixture({ language: body.language, ...next });
    // An offline business never claims a model; the fixture mirrors CopilotService.
    if (statusMode !== 'openai' && reply.mode === 'openai') Object.assign(reply, { mode: 'deterministic', model: null });
    return respond(res, reply);
  }
  return respond(res, { error: 'fixture_missing' }, 404);
});

let nextProc, gateway, browser, nextOutput = '';
const ports = [apiPort, nextPort, gatewayPort];
try {
  for (const port of ports) {
    await new Promise((resolve, reject) => {
      const probe = net.createServer();
      probe.once('error', () => reject(new Error(`Port ${port} is occupied; refusing to overlap another service`)));
      probe.listen(port, '127.0.0.1', () => probe.close(resolve));
    });
  }
  await new Promise((resolve, reject) => { api.once('error', reject); api.listen(apiPort, '127.0.0.1', resolve); });
  nextProc = spawn(process.execPath, [path.join(frontendDir, 'node_modules', 'next', 'dist', 'bin', 'next'), 'start', '--hostname', '127.0.0.1', '--port', String(nextPort)],
    { cwd: frontendDir, windowsHide: true, env: { ...process.env, BACKEND_API_URL: `http://127.0.0.1:${apiPort}` } });
  nextProc.stdout.on('data', d => { nextOutput += d; }); nextProc.stderr.on('data', d => { nextOutput += d; });
  for (let i = 0; i < 240 && !/Ready in/.test(nextOutput); i++) {
    assert.equal(nextProc.exitCode, null, 'Next exited: ' + nextOutput);
    await new Promise(r => setTimeout(r, 250));
  }
  assert.match(nextOutput, /Ready in/, 'Next production server did not start (run pnpm build:release first)');
  // Same-origin gateway: /api goes to the synthetic API regardless of the build-time rewrite target.
  gateway = http.createServer((req, res) => {
    const up = http.request({ hostname: '127.0.0.1', port: req.url.startsWith('/api') ? apiPort : nextPort, path: req.url, method: req.method, headers: req.headers },
      reply => { res.writeHead(reply.statusCode, reply.headers); reply.pipe(res); });
    up.on('error', e => { if (!res.headersSent) res.writeHead(502); res.end(String(e)); });
    req.pipe(up);
  });
  await new Promise(r => gateway.listen(gatewayPort, '127.0.0.1', r));

  browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}) });
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage();
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(message.text())) errors.push(message.text()); });
  const asks = () => events.filter(e => e.url === '/api/copilot/ask').length;
  const lastTurn = () => page.getByTestId('copilot-turn').last();
  const ask = async (question, expectedTurns) => {
    await page.getByTestId('copilot-question').fill(question);
    await page.getByTestId('copilot-send').click();
    await page.waitForFunction(n => document.querySelectorAll('[data-testid="copilot-turn"]').length === n, expectedTurns);
  };

  await page.goto(`${origin}/?auth=login`);
  await page.getByLabel(/^Email/).fill('owner@example.test'); await page.getByLabel(/^Password/).fill('test-password-123');
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.locator('a[href="/ask-finsight"]').first().waitFor({ state: 'attached' });

  // 1. Provider-generated answer: labelled AI with the model, exact figure unchanged.
  await page.goto(`${origin}/ask-finsight`);
  await page.getByTestId('copilot-mode').getByText('AI assistant', { exact: true }).waitFor();
  await ask('What is my cash inflow?', 1);
  assert.equal(await lastTurn().getAttribute('data-provenance'), 'ai');
  assert.match(await lastTurn().getByTestId('copilot-provenance').innerText(), /^AI-generated answer, checked against your records \(gpt-synthetic\)$/);
  assert.ok((await lastTurn().getByTestId('copilot-answer').innerText()).includes(EXACT_VALUE));
  mark('provider-generated answer is labelled AI-generated with its model and keeps the exact figure');

  // 2. DEF-08: status says openai, this answer fell back (provider_unavailable). Disclosed per answer.
  next = { unavailable: true, unsupported: false, limited: false };
  await ask('Explain the trend', 2);
  const fallback = lastTurn();
  assert.equal(await fallback.getAttribute('data-provenance'), 'fallback');
  assert.equal(await fallback.getByTestId('copilot-provenance').innerText(), 'Evidence-based fallback — not AI-generated');
  assert.match(await fallback.getByTestId('copilot-fallback-cause').innerText(), /AI provider was unavailable for this answer/);
  const fallbackAnswer = await fallback.getByTestId('copilot-answer').innerText();
  assert.equal(fallbackAnswer, copilotFixture({ unavailable: true }).answer, 'Fallback answer text is shown unchanged');
  await fallback.locator('summary').click();
  assert.equal(await fallback.locator('details li').count(), 3, 'All evidence rows retained');
  assert.ok((await fallback.locator('details').innerText()).includes(EXACT_VALUE), 'Exact evidence value retained');
  assert.match(await page.getByTestId('copilot').innerText(), /Offline assistant|not AI-generated|provider unavailable|fallback/i, "Codex's DEF-08 assertion");
  assert.equal(await page.getByTestId('copilot-mode').innerText(), 'AI assistant', 'Page-level status semantics unchanged');
  assert.equal(await page.getByTestId('copilot-turn').first().getAttribute('data-provenance'), 'ai', 'Earlier AI answer keeps its own label');
  await page.screenshot({ path: path.join(out, 'copilot-fallback-en.png'), fullPage: true });
  writeFileSync(path.join(out, 'fallback-disclosure.json'), JSON.stringify({ statusMode: 'openai', response: copilotFixture({ unavailable: true }), displayed: await page.getByTestId('copilot').innerText() }, null, 2));
  mark('provider_unavailable fallback under an AI status is disclosed as an evidence-based fallback, not AI-generated');

  // 3. Limited: not an answer; existing rate-limit reason preserved.
  next = { unavailable: false, unsupported: false, limited: true };
  await ask('Another question', 3);
  assert.equal(await lastTurn().getAttribute('data-provenance'), 'limited');
  await lastTurn().getByText('Too many questions — please wait a minute.', { exact: true }).waitFor();
  assert.equal(await lastTurn().getByTestId('copilot-fallback-cause').count(), 0);
  mark('rate-limited request is labelled as not answered and keeps its reason');

  // 4. Offline business (status deterministic): unsupported question keeps the offline label and scope reply.
  statusMode = 'deterministic'; next = { unavailable: false, unsupported: true, limited: false };
  await page.reload();
  await page.getByTestId('copilot-mode').getByText('Offline assistant', { exact: true }).waitFor();
  await ask('Create an invoice', 1);
  assert.equal(await lastTurn().getAttribute('data-provenance'), 'offline');
  assert.match(await lastTurn().getByTestId('copilot-provenance').innerText(), /^Offline assistant answer/);
  assert.equal(await lastTurn().getByTestId('copilot-answer').innerText(), 'This request is outside the read-only scope.');
  next = { unavailable: false, unsupported: false, limited: false };
  await ask('What is my cash inflow?', 2);
  assert.equal(await lastTurn().getAttribute('data-provenance'), 'offline', 'An offline business is never shown as AI-generated');
  mark('offline assistant and unsupported answers keep accurate labels');

  // 5. Disabled module: no question box and no ask request.
  aiModule = false; const before = asks();
  await page.reload();
  await page.waitForLoadState('networkidle');
  assert.equal(await page.getByTestId('copilot-question').count(), 0);
  assert.equal(asks(), before);
  aiModule = true; statusMode = 'openai';
  mark('disabled assistant offers no question and sends no request');

  // 6. Urdu / RTL fallback disclosure.
  await page.goto(`${origin}/ask-finsight`);
  await page.getByTestId('copilot-mode').waitFor();
  await page.getByRole('button', { name: 'اردو', exact: true }).first().click();
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await page.getByTestId('copilot-mode').getByText('AI معاون', { exact: true }).waitFor();
  next = { unavailable: true, unsupported: false, limited: false };
  await ask('رجحان سمجھائیں', 1);
  assert.equal(await lastTurn().getAttribute('data-provenance'), 'fallback');
  assert.equal(await lastTurn().getByTestId('copilot-provenance').innerText(), 'شواہد پر مبنی متبادل جواب — AI کا تیار کردہ نہیں');
  assert.match(await lastTurn().getByTestId('copilot-fallback-cause').innerText(), /AI فراہم کنندہ دستیاب نہیں تھا/);
  assert.ok((await lastTurn().getByTestId('copilot-answer').innerText()).includes(EXACT_VALUE));
  await page.screenshot({ path: path.join(out, 'copilot-fallback-ur.png'), fullPage: true });
  mark('Urdu RTL fallback answer is disclosed as not AI-generated with exact figures');

  // 7. WhatsApp delivery history (Urdu, then English): mock is simulated, actual statuses preserved.
  await page.goto(`${origin}/settings`);
  await page.locator('[data-testid="whatsapp-delivery"]').first().waitFor();
  const providerText = async kind => page.locator(`[data-testid="whatsapp-delivery"][data-provider="${kind}"] [data-testid="whatsapp-delivery-provider"]`).innerText();
  assert.match(await providerText('mock'), /فرضی/);
  assert.match(await providerText('disabled'), /واٹس ایپ ترسیل بند ہے/);
  await page.getByRole('button', { name: 'English', exact: true }).first().click();
  await page.waitForFunction(() => document.documentElement.dir === 'ltr');
  await page.locator('[data-testid="whatsapp-delivery"]').first().waitFor();
  const row = kind => page.locator(`[data-testid="whatsapp-delivery"][data-provider="${kind}"]`);
  assert.deepEqual(await page.locator('[data-testid="whatsapp-delivery"]').evaluateAll(rows => rows.map(r => r.getAttribute('data-provider'))), ['mock', 'meta', 'disabled', 'unknown']);
  assert.equal(await providerText('mock'), 'Delivery provider: Simulated — test provider, no WhatsApp message was transmitted');
  assert.match(await row('mock').innerText(), /Sent/, 'Actual recorded status is still shown');
  assert.equal(await providerText('meta'), 'Delivery provider: WhatsApp Cloud API (Meta)');
  assert.equal(await providerText('disabled'), 'Delivery provider: WhatsApp sending is turned off — nothing was transmitted');
  assert.match(await row('disabled').innerText(), /Failed/);
  assert.equal(await providerText('unknown'), 'Delivery provider: Provider not recorded');
  assert.ok(!(await row('mock').innerText()).includes('WhatsApp Cloud API'), 'A mock delivery is never attributed to Meta');
  await page.screenshot({ path: path.join(out, 'whatsapp-delivery-history.png'), fullPage: true });
  mark('WhatsApp mock deliveries are identified as simulated; Meta, disabled and unrecorded providers are distinct');

  // 8. Disclosure is read-only: only sign-in, language and Copilot questions were sent.
  const allowed = new Set(['/api/auth/login', '/api/profile/language', '/api/copilot/ask']);
  const mutations = events.filter(e => !['GET', 'HEAD'].includes(e.method) && !allowed.has(e.url));
  assert.deepEqual(mutations, [], 'No financial, delivery, consent or configuration mutation');
  mark('disclosure flows send no financial, delivery, consent or provider mutations');

  assert.deepEqual(errors, []); mark('no browser exceptions, CSP failures or hydration errors');
  writeFileSync(path.join(out, 'results.json'), JSON.stringify({ scope: 'Synthetic API fixture + production Next; not real provider or deployed acceptance', checks, errors }, null, 2));
} catch (error) {
  writeFileSync(path.join(out, 'results.json'), JSON.stringify({ scope: 'Synthetic API fixture + production Next', checks, errors, status: 'FAILED', failure: String(error?.stack || error), next: nextOutput.slice(-4000) }, null, 2));
  const page = browser?.contexts()[0]?.pages()[0];
  if (page) { await page.screenshot({ path: path.join(out, 'failure.png'), fullPage: true }).catch(() => {}); console.error(await page.locator('body').innerText().catch(() => '')); }
  throw error;
} finally {
  await browser?.close();
  nextProc?.kill();
  for (const server of [gateway, api]) { if (server?.listening) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); } }
}
