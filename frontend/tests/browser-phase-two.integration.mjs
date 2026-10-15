// Production Next + controlled synthetic API faults. Real Spring/PostgreSQL acceptance is separate.
import http from 'node:http';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import { configurationFixture, aiStatusFixture } from './fixtures/phase-two.mjs';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const origin = process.env.FRONTEND_TEST_ORIGIN || 'http://127.0.0.1:3100';
const out = process.env.BROWSER_ARTIFACT_DIR || 'test-artifacts/browser-phase-two'; mkdirSync(out, { recursive: true });
const first = '11111111-1111-4111-8111-111111111111', second = '22222222-2222-4222-8222-222222222222';
const configs = new Map([[first, configurationFixture()], [second, configurationFixture({ operatingProfile: 'SERVICES' })]]);
let active = first, role = 'OWNER', language = 'en', signedIn = false, mode = 'disabled', conflict = false, unavailable = false, throttle = false;
const events = [], checks = [], errors = [];
const business = id => ({ businessId: id, businessName: id === first ? 'Configuration fixture A' : 'Configuration fixture B', businessType: 'trade', languagePreference: language, role, membershipStatus: 'ACTIVE', active: id === active });
const profile = () => ({ businessId: active, businessType: 'trade', languagePreference: language, whatsappNumber: null, whatsappOptIn: false, createdAt: '2026-10-12' });
const respond = (res, value, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(value)); };
const server = http.createServer(async (req, res) => {
  let raw = ''; for await (const chunk of req) raw += chunk; let body = {}; try { body = JSON.parse(raw); } catch {}
  const { url, method } = req; events.push({ url, method, body, active });
  if (url === '/api/auth/csrf') { res.setHeader('Set-Cookie', 'FINSIGHT_TEST=phase-two; HttpOnly; SameSite=Lax; Path=/'); return respond(res, { token: 'synthetic-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }); }
  if (!['GET', 'HEAD'].includes(method) && req.headers['x-xsrf-token'] !== 'synthetic-csrf') return respond(res, { error: 'access_denied' }, 403);
  if (url === '/api/auth/login') { signedIn = true; return respond(res, { id: 'synthetic-owner', email: body.email, fullName: 'Synthetic owner', authStage: 'FULLY_AUTHENTICATED', platformRole: null }); }
  if (url === '/api/auth/logout') { signedIn = false; res.writeHead(204); return res.end(); }
  if (!signedIn) return respond(res, { error: 'session_expired' }, 401);
  if (url === '/api/auth/me') return respond(res, { id: 'synthetic-owner', email: 'owner@example.test', fullName: 'Synthetic owner', authStage: 'FULLY_AUTHENTICATED', platformRole: null });
  if (url === '/api/businesses') return respond(res, [business(first), business(second)]);
  if (url === '/api/businesses/active') { if (method === 'POST') active = body.businessId; return respond(res, business(active)); }
  if (url === '/api/profile') return respond(res, profile());
  if (url === '/api/profile/language') { language = body.languagePreference; return respond(res, profile()); }
  if (url === '/api/whatsapp/deliveries' || url === '/api/memberships' || url === '/api/invitations' || url === '/api/records/monthly') return respond(res, []);
  if (url === '/api/documents') return respond(res, [{ id: '33333333-3333-4333-8333-333333333333', originalFilename: 'Preserved historical fixture.pdf', processingStatus: 'confirmed', linkedMonth: '2026-09' }]);
  if (url === '/api/dashboard') return respond(res, { businessId: active, profile: profile(), score: null, topInsight: null, topRecommendation: null, cashFlowHistory: [], trendProjection: null });
  if (url === '/api/business-configuration' || url === '/api/business-configuration/preview') {
    if (method === 'GET') return respond(res, configurationFixture({ ...configs.get(active), role }));
    if (role !== 'OWNER') return respond(res, { error: 'access_denied' }, 403);
    if (conflict && method === 'PUT') { conflict = false; configs.set(active, { ...configs.get(active), version: configs.get(active).version + 1 }); return respond(res, { error: 'stale_version' }, 409); }
    if (body.version !== configs.get(active).version) return respond(res, { error: 'stale_version' }, 409);
    const config = configurationFixture({ ...body, role, configured: true, version: body.version + (method === 'PUT' ? 1 : 0) });
    if (method === 'PUT') configs.set(active, config); return respond(res, config);
  }
  if (url === '/api/ai/status') return respond(res, aiStatusFixture(mode));
  if (url === '/api/ai/explain') {
    if (throttle) { throttle = false; res.setHeader('Retry-After', '60'); return respond(res, { error: 'ai_rate_limited' }, 429); }
    const unsupported = /invoice|transfer|inventory/i.test(body.question || '');
    return respond(res, { schemaVersion: 'ai-explanation-v1', language: body.language,
      summary: unsupported ? 'This request is outside the read-only scope.' : body.language === 'ur' ? 'یہ تصدیق شدہ مالی شواہد کی وضاحت ہے۔' : `Verified explanation for ${active === first ? 'fixture A' : 'fixture B'}.`,
      interpretation: ['Recorded evidence does not establish causation.'], considerations: [], limitations: ['Sparse recorded history.'],
      evidence: [{ id: 'score.total', kind: 'score', label: 'Verified exact amount', value: '123456789012345.6700', unit: 'recorded_amount', period: '2026-09', sourceMode: 'SNAPSHOT' }, { id: 'projection.net', kind: 'projection', label: 'Missing projection', value: null, unit: 'recorded_amount', period: '2026-10', sourceMode: 'PLAN' }],
      provenance: { mode: unavailable || unsupported ? 'deterministic' : 'simulated', model: null, promptVersion: 'evidence-selection-v1', evidenceFingerprint: 'synthetic-fingerprint', cached: false }, reasonCode: unavailable ? 'provider_unavailable' : unsupported ? 'unsupported_request' : null });
  }
  return respond(res, { error: 'fixture_missing' }, 404);
});
await new Promise((resolve, reject) => { server.once('error', reject); server.listen(8080, '127.0.0.1', resolve); });
let browser;
const mark = name => { checks.push(name); console.log('PASS', name); };
try {
  browser = await chromium.launch({ headless: true, channel: 'msedge' }); const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } }); const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message)); page.on('console', message => { if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(message.text())) errors.push(message.text()); });
  await page.goto(origin); await page.getByLabel(/^Email/).fill('owner@example.test'); await page.getByLabel(/^Password/).fill('test-password-123'); await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.getByTestId('ai-dashboard').getByText('AI explanations are not activated.', { exact: false }).waitFor();
  assert.equal(events.filter(event => event.url === '/api/ai/explain').length, 0); assert.equal(await page.getByRole('link', { name: 'Ask FinSight', exact: true }).count(), 0); mark('disabled AI leaves deterministic dashboard usable and never requests explanations');
  await page.goto(`${origin}/settings`); await page.getByTestId('operating-profile').waitFor(); assert.equal(await page.getByTestId('operating-profile').inputValue(), 'RETAIL_TRADE'); assert.ok(await page.getByTestId('module-inventory').isDisabled()); assert.ok(await page.getByTestId('module-core_finance').isDisabled()); mark('existing business gets compatible defaults and future/required modules cannot be toggled');
  await page.getByTestId('operating-profile').selectOption('PROJECT_ORDER'); await page.getByTestId('operating-tracksProjects').check(); await page.getByRole('button', { name: 'Preview setup', exact: true }).click(); await page.getByTestId('configuration-preview').getByText('Project revenue', { exact: false }).waitFor(); assert.equal(configs.get(first).operatingProfile, 'RETAIL_TRADE'); mark('server preview adapts terminology without persisting');
  await page.getByTestId('module-documents').uncheck(); await page.getByTestId('save-configuration').click(); await page.getByText('Business configuration saved.', { exact: true }).waitFor(); assert.equal(configs.get(first).operatingProfile, 'PROJECT_ORDER'); assert.ok(!configs.get(first).requestedModules.includes('documents'));
  await page.reload(); await page.getByTestId('operating-profile').waitFor(); assert.equal(await page.getByTestId('operating-profile').inputValue(), 'PROJECT_ORDER'); assert.equal(await page.getByTestId('module-documents').isChecked(), false); mark('configuration and optional module choices persist across reload');
  assert.equal(await page.locator('a[href="/scan"]').count(), 0); await page.goto(`${origin}/upload`); await page.getByText('Preserved historical fixture.pdf', { exact: true }).waitFor(); assert.equal(await page.locator('input[type="file"]').count(), 0); await page.getByText('This module is disabled.', { exact: false }).waitFor(); mark('disabled documents preserve history and hide uploads including direct routes');
  await page.goto(`${origin}/settings`); await page.getByTestId('module-whatsapp').uncheck(); await page.getByTestId('save-configuration').click(); await page.getByText('Business configuration saved.', { exact: true }).waitFor(); assert.ok(await page.getByRole('button', { name: 'Save preferences', exact: true }).isDisabled()); mark('disabled WhatsApp prevents preference mutations without deleting delivery history');
  conflict = true; await page.getByTestId('operating-sellsOnCredit').check(); await page.getByTestId('save-configuration').click(); await page.getByRole('alert').filter({ hasText: 'changed in another session' }).waitFor(); assert.ok(await page.getByTestId('save-configuration').isDisabled()); await page.getByRole('button', { name: 'Reload configuration', exact: true }).click(); await page.waitForFunction(() => !document.querySelector('[data-testid="save-configuration"]').disabled); mark('optimistic conflict requires reloading latest settings before saving');
  mode = 'simulated'; await page.goto(`${origin}/ask-finsight`); await page.getByTestId('ask-question').waitFor(); await page.getByText('Simulated development response — not live GPT', { exact: true }).first().waitFor(); await page.getByTestId('ask-question').fill('Explain my score'); await page.getByTestId('ask-send').click(); await page.getByTestId('ai-response').waitFor(); await page.getByText('123456789012345.6700', { exact: true }).waitFor(); await page.getByText('Projection / plan', { exact: false }).waitFor(); assert.equal((await page.getByTestId('ai-provenance').innerText()), 'Simulated development response — not live GPT'); mark('explicit demo mode shows provenance, exact strings, unknown data and plan labels');
  unavailable = true; await page.getByTestId('ask-question').fill('Explain the trend'); await page.getByTestId('ask-send').click(); await page.getByText('FinSight fallback — not AI-generated', { exact: true }).waitFor(); mark('provider fallback is identified as deterministic rather than GPT'); unavailable = false;
  throttle = true; await page.getByTestId('ask-question').fill('Explain cash flow'); await page.getByTestId('ask-send').click(); await page.getByRole('alert').filter({ hasText: 'request limit' }).waitFor(); assert.equal(await page.getByTestId('ask-question').inputValue(), 'Explain cash flow'); mark('rate limit preserves question and never retries automatically');
  await page.getByTestId('ask-send').click(); await page.waitForFunction(() => document.querySelectorAll('[data-testid="ai-response"]').length === 3); await page.getByTestId('ask-question').fill('Explain missing data'); await page.getByTestId('ask-send').click(); await page.getByText('This conversation reached its limit.', { exact: false }).waitFor(); assert.ok(await page.getByTestId('ask-send').isDisabled()); assert.ok(events.filter(event => event.url === '/api/ai/explain').every(event => (event.body.conversation?.length || 0) <= 8)); mark('conversation is bounded and reports its limit');
  await page.getByRole('button', { name: 'Start a new conversation', exact: true }).click(); assert.equal(await page.getByTestId('ai-response').count(), 0); await page.getByTestId('ask-question').fill('Create an invoice'); await page.getByTestId('ask-send').click(); await page.getByText('This request is outside the read-only scope.', { exact: true }).waitFor(); mark('new conversation clears evidence and unsupported requests remain read-only');
  await page.goto(`${origin}/settings`); await page.getByLabel('Active business').selectOption(second); await page.getByTestId('operating-profile').waitFor(); assert.equal(await page.getByTestId('operating-profile').inputValue(), 'SERVICES'); await page.goto(`${origin}/ask-finsight`); await page.getByTestId('ask-question').waitFor(); assert.equal(await page.getByTestId('ai-response').count(), 0); assert.equal(await page.getByText('Verified explanation for fixture A.', { exact: true }).count(), 0); mark('switching businesses loads the other configuration and clears conversation evidence');
  await page.setViewportSize({ width: 390, height: 844 }); await page.getByRole('button', { name: 'اردو', exact: true }).click(); await page.waitForFunction(() => document.documentElement.dir === 'rtl'); await page.getByTestId('ask-question').fill('میرا مالی اسکور سمجھائیں'); await page.getByTestId('ask-send').click(); await page.getByText('یہ تصدیق شدہ مالی شواہد کی وضاحت ہے۔', { exact: true }).waitFor(); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)); await page.screenshot({ path: `${out}/ask-urdu-mobile.png`, fullPage: true }); mark('Urdu assistant requests, right-to-left rendering and mobile width work');
  await page.goto(`${origin}/settings`); await page.getByTestId('operating-profile').waitFor(); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)); await page.screenshot({ path: `${out}/configuration-urdu-mobile.png`, fullPage: true }); mark('Urdu configuration remains usable on mobile');
  await page.getByRole('button', { name: 'English', exact: true }).first().click(); role = 'VIEWER'; await page.reload(); await page.getByTestId('operating-profile').waitFor(); assert.ok(await page.getByTestId('operating-profile').isDisabled()); assert.equal(await page.getByTestId('save-configuration').count(), 0); mark('viewer receives read-only settings without owner controls');
  await page.getByRole('button', { name: 'Sign out', exact: true }).click(); await page.getByRole('heading', { name: 'Sign in to FinSight' }).waitFor(); assert.equal(await page.getByTestId('ask-conversation').count(), 0); assert.equal(await page.getByTestId('business-configuration').count(), 0); mark('logout removes all private configuration and assistant state');
  assert.deepEqual(errors, []); mark('no browser exceptions, CSP failures or hydration errors');
  writeFileSync(`${out}/results.json`, JSON.stringify({ scope: 'Synthetic API fixture + production Next; not real provider or deployed acceptance', checks, errors }, null, 2));
} catch (error) { if (browser) { const page = browser.contexts()[0]?.pages()[0]; await page?.screenshot({ path: `${out}/failure.png`, fullPage: true }); console.error(await page?.locator('body').innerText()); } throw error; }
finally { await browser?.close(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
