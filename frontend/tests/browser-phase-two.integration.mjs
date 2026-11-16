import { historicalOrigin as origin, historicalBackendPort as backendPort } from './historical-harness.mjs';
// Production Next + controlled synthetic API faults. Real Spring/PostgreSQL acceptance is separate.
import { copilotFixture } from './fixtures/copilot.mjs';
import http from 'node:http';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import { configurationFixture, aiStatusFixture } from './fixtures/phase-two.mjs';
import { workspaceFixture } from './fixtures/workspace.mjs';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
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
  const dashboard = { businessId: active, profile: profile(), score: null, topInsight: null, topRecommendation: null, cashFlowHistory: [], trendProjection: null, hasHistory: false };
  if (url === '/api/dashboard') return respond(res, dashboard);
  if (url === '/api/workspace') return respond(res, workspaceFixture({ dashboard, configuration: configurationFixture({ ...configs.get(active), role }), aiStatus: aiStatusFixture(mode), role }));
  if (url.startsWith('/api/workspace/presets/')) return respond(res, workspaceFixture({ dashboard, configuration: configurationFixture({ ...configs.get(active), role }), aiStatus: aiStatusFixture(mode), role, presetKey: url.split('/').at(-1) }));
  if (url === '/api/business-configuration' || url === '/api/business-configuration/preview') {
    if (method === 'GET') return respond(res, configurationFixture({ ...configs.get(active), role }));
    if (role !== 'OWNER') return respond(res, { error: 'access_denied' }, 403);
    if (conflict && method === 'PUT') { conflict = false; configs.set(active, { ...configs.get(active), version: configs.get(active).version + 1 }); return respond(res, { error: 'stale_version' }, 409); }
    if (body.version !== configs.get(active).version) return respond(res, { error: 'stale_version' }, 409);
    const config = configurationFixture({ ...body, role, configured: true, version: body.version + (method === 'PUT' ? 1 : 0) });
    if (method === 'PUT') configs.set(active, config); return respond(res, config);
  }
  if (url === '/api/ai/status') return respond(res, aiStatusFixture(mode));
  if(url==='/api/copilot/status')return respond(res,{version:'copilot-v1',moduleEnabled:configs.get(active).modules.find(m=>m.key==='ai').enabled,mode:mode==='disabled'?'deterministic':'openai',providerReason:mode==='disabled'?'ai_disabled':'',tools:['health_evidence','budget_goal_status']});
  if(url==='/api/copilot/ask'){
    if(!configs.get(active).modules.find(m=>m.key==='ai').enabled)return respond(res,{error:'module_disabled'},403);
    assert.deepEqual(Object.keys(body).sort(),['conversation','language','question']);
    assert.ok(body.conversation.length<=8);assert.ok(body.conversation.every(t=>['user','assistant'].includes(t.role)&&t.content.length<=1000));
    const unsupported=/create.*invoice|transfer|inventory write/i.test(body.question||'');
    const limited=throttle;throttle=false;
    return respond(res,copilotFixture({language:body.language,businessLabel:active===first?'fixture A':'fixture B',mode,unavailable,unsupported,limited}));
  }
  return respond(res, { error: 'fixture_missing' }, 404);
});
await new Promise((resolve, reject) => { server.once('error', reject); server.listen(backendPort, '127.0.0.1', resolve); });
let browser;
const mark = name => { checks.push(name); console.log('PASS', name); };
try {
  browser = await chromium.launch({ headless: true, ...(process.env.FINSIGHT_BROWSER_CHANNEL ? { channel: process.env.FINSIGHT_BROWSER_CHANNEL } : {}) }); const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } }); const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message)); page.on('console', message => { if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(message.text())) errors.push(message.text()); });
  await page.goto(origin + '/?auth=login'); await page.getByLabel(/^Email/).fill('owner@example.test'); await page.getByLabel(/^Password/).fill('test-password-123'); await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.getByTestId('workspace-widget-financial-summary').waitFor();await page.getByTestId('workspace-widget-health-score').getByText('Save a monthly record to begin. A missing score is never estimated.',{exact:true}).waitFor();
  assert.ok(await page.locator('[data-testid^="workspace-widget-"]').count()>0);assert.equal(configs.get(first).configured,false);
  assert.equal(await page.getByTestId('ai-dashboard').count(), 0);
  await page.screenshot({ path: `${out}/empty-workspace-desktop.png`, fullPage: true });
  await page.getByTestId('workspace-preset-preview').click();
  await page.getByTestId('workspace-preset-select').selectOption('services');
  await page.getByTestId('workspace-widget-financial-evidence').waitFor();
  assert.equal(await page.getByTestId('dashboard-workspace').getAttribute('data-preset'), 'services');
  await page.getByTestId('workspace-close-preview').click();
  await page.getByTestId('workspace-widget-financial-summary').waitFor();await page.getByTestId('workspace-widget-health-score').getByText('Save a monthly record to begin. A missing score is never estimated.',{exact:true}).waitFor();
  assert.equal(configs.get(first).operatingProfile, 'RETAIL_TRADE');
  assert.equal(events.filter(event => event.url.startsWith('/api/workspace') && event.method !== 'GET').length, 0);
  mark('empty business exposes its financial overview without inventing a health score while preset previews remain available without saving');
  await page.getByTestId('workspace-customize').click();
  await page.getByTestId('workspace-widget-health-score').waitFor();
  await page.getByTestId('workspace-unavailable-ai-explanation').waitFor();
  assert.equal(events.filter(event => event.url === '/api/ai/explain').length, 0); assert.equal(await page.getByTestId('ai-dashboard').count(),0); mark('disabled AI leaves deterministic dashboard usable and never requests explanations');
  await page.goto(`${origin}/settings`); await page.getByTestId('operating-profile').waitFor(); assert.equal(await page.getByTestId('operating-profile').inputValue(), 'RETAIL_TRADE'); assert.equal(await page.getByTestId('module-inventory').isDisabled(),false);assert.ok(await page.getByTestId('module-inventory').isChecked());assert.ok(await page.getByTestId('module-transactions').isChecked()); assert.ok(await page.getByTestId('module-core_finance').isDisabled()); mark('existing business gets compatible defaults and implemented inventory and its transaction dependency are enabled; required core cannot be toggled');
  await page.getByTestId('operating-profile').selectOption('PROJECT_ORDER'); await page.getByTestId('operating-tracksProjects').check(); await page.getByRole('button', { name: 'Preview setup', exact: true }).click(); await page.getByTestId('configuration-preview').getByText('Project revenue', { exact: false }).waitFor(); assert.equal(configs.get(first).operatingProfile, 'RETAIL_TRADE'); mark('server preview adapts terminology without persisting');
  await page.getByTestId('module-documents').uncheck(); await page.getByTestId('save-configuration').click(); await page.getByText('Business configuration saved.', { exact: true }).waitFor(); assert.equal(configs.get(first).operatingProfile, 'PROJECT_ORDER'); assert.ok(!configs.get(first).requestedModules.includes('documents'));
  await page.reload(); await page.getByTestId('operating-profile').waitFor(); assert.equal(await page.getByTestId('operating-profile').inputValue(), 'PROJECT_ORDER'); assert.equal(await page.getByTestId('module-documents').isChecked(), false); mark('configuration and optional module choices persist across reload');
  assert.equal(await page.locator('a[href="/scan"]').count(), 0); await page.goto(`${origin}/upload`); await page.getByText('This module is disabled.', { exact: false }).waitFor();const historicalDocs=await context.request.get(origin+'/api/documents');assert.equal(historicalDocs.status(),200);assert.equal((await historicalDocs.json())[0].originalFilename,'Preserved historical fixture.pdf'); assert.equal(await page.locator('input[type="file"]').count(), 0); await page.getByText('This module is disabled.', { exact: false }).waitFor(); mark('disabled document route is guarded and cannot upload; authorized history fixture remains intact');
  await page.goto(`${origin}/settings`); await page.getByTestId('module-whatsapp').uncheck(); await page.getByTestId('save-configuration').click(); await page.getByText('Business configuration saved.', { exact: true }).waitFor(); assert.ok(await page.getByRole('button', { name: 'Save preferences', exact: true }).isDisabled()); mark('disabled WhatsApp prevents preference mutations without deleting delivery history');
  conflict = true; await page.getByTestId('operating-sellsOnCredit').check(); await page.getByTestId('save-configuration').click(); await page.getByRole('alert').filter({ hasText: 'changed in another session' }).waitFor(); assert.ok(await page.getByTestId('save-configuration').isDisabled()); await page.getByRole('button', { name: 'Reload configuration', exact: true }).click(); await page.waitForFunction(() => !document.querySelector('[data-testid="save-configuration"]').disabled); mark('optimistic conflict requires reloading latest settings before saving');
  // Current /ask-finsight is copilot-v1; the retired /ai/explain chat is not this journey.
  await page.goto(`${origin}/ask-finsight`);await page.getByTestId('copilot-mode').getByText('Offline assistant',{exact:true}).waitFor();
  assert.equal(events.filter(e=>e.url==='/api/copilot/ask').length,0);mark('provider-disabled Copilot is disclosed as offline and does not submit automatically');
  mode='openai';await page.reload();await page.getByTestId('copilot-mode').getByText('AI assistant',{exact:true}).waitFor();
  const ask=async question=>{await page.getByTestId('copilot-question').fill(question);await Promise.all([page.waitForResponse(r=>r.url().endsWith('/api/copilot/ask')),page.getByTestId('copilot-send').click()]);await page.getByTestId('copilot-send').waitFor();};
  await ask('Explain my score');await page.getByTestId('copilot-answer').getByText('Verified explanation for fixture A.',{exact:true}).waitFor();
  await page.getByText('Sources (3)',{exact:true}).click();await page.getByText('123456789012345.6700',{exact:true}).waitFor();await page.getByText('health_evidence.readinessStatus',{exact:true}).waitFor();await page.getByText('UNKNOWN',{exact:true}).waitFor();
  await page.getByText('Synthetic planned budget (not posted)',{exact:true}).waitFor();
  mark('current Copilot displays exact strings, unknown readiness and a source-labelled plan without treating it as posted cash');
  const beforeLimit=events.filter(e=>e.url==='/api/copilot/ask').length;throttle=true;await ask('Explain cash flow');
  await page.getByRole('status').filter({hasText:'Too many questions'}).waitFor();assert.equal(events.filter(e=>e.url==='/api/copilot/ask').length,beforeLimit+1);
  mark('current rate-limit response is disclosed without automatic retry');
  for(let i=0;i<5;i++)await ask(`Explain evidence ${i}`);
  const questions=events.filter(e=>e.url==='/api/copilot/ask');assert.equal(questions.at(-1).body.conversation.length,8);
  assert.ok(questions.every(e=>e.body.conversation.length<=8&&e.body.conversation.every(t=>t.content.length<=1000)));
  mark('Copilot keeps a bounded rolling request history of at most eight turns');
  await page.getByRole('button',{name:'New conversation',exact:true}).click();assert.equal(await page.getByTestId('copilot-answer').count(),0);
  const beforeReadOnly=events.filter(e=>!['GET','HEAD'].includes(e.method)&&!e.url.startsWith('/api/copilot/')).length;
  await ask('Create an invoice');await page.getByText('This request is outside the read-only scope.',{exact:true}).waitFor();
  assert.equal(events.filter(e=>!['GET','HEAD'].includes(e.method)&&!e.url.startsWith('/api/copilot/')).length,beforeReadOnly);
  assert.deepEqual(events.filter(e=>e.url==='/api/copilot/ask').at(-1).body.conversation,[]);
  mark('new conversation clears history and unsupported request submits only to the read-only Copilot API');
  await page.goto(`${origin}/settings`);await page.getByLabel('Active business').selectOption(second);await page.getByTestId('operating-profile').waitFor();assert.equal(await page.getByTestId('operating-profile').inputValue(),'SERVICES');
  await page.goto(`${origin}/ask-finsight`);await page.getByTestId('copilot-question').waitFor();assert.equal(await page.getByTestId('copilot-answer').count(),0);assert.equal(await page.getByText('Verified explanation for fixture A.',{exact:true}).count(),0);mark('business switch loads independent configuration and removes prior Copilot evidence');
  await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'اردو',exact:true}).click();await page.waitForFunction(()=>document.documentElement.dir==='rtl');
  await ask('میرا مالی اسکور سمجھائیں');await page.getByText('یہ تصدیق شدہ مالی شواہد کی وضاحت ہے۔',{exact:true}).waitFor();
  assert.equal(events.filter(e=>e.url==='/api/copilot/ask').at(-1).body.language,'ur');assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
  await page.screenshot({path:`${out}/ask-urdu-mobile.png`,fullPage:true});mark('current Copilot submits Urdu, renders RTL and fits mobile width');
  await page.goto(`${origin}/settings`); await page.getByTestId('operating-profile').waitFor(); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)); await page.screenshot({ path: `${out}/configuration-urdu-mobile.png`, fullPage: true }); mark('Urdu configuration remains usable on mobile');
  await Promise.all([page.waitForResponse(r=>r.url().endsWith('/api/profile/language')&&r.request().method()==='PATCH'),page.getByRole('button', { name: 'English', exact: true }).first().click()]);await page.waitForFunction(()=>document.documentElement.dir==='ltr');role = 'VIEWER'; await page.reload(); await page.getByTestId('operating-profile').waitFor(); assert.ok(await page.getByTestId('operating-profile').isDisabled()); assert.equal(await page.getByTestId('save-configuration').count(), 0); mark('viewer receives read-only settings without owner controls');
  await page.getByRole('button', { name: 'Sign out', exact: true }).click(); await page.getByRole('heading', { name: 'Sign in to FinSight' }).waitFor(); assert.equal(await page.getByTestId('copilot-conversation').count(), 0); assert.equal(await page.getByTestId('business-configuration').count(), 0); mark('logout removes all private configuration and assistant state');
  assert.deepEqual(errors, []); mark('no browser exceptions, CSP failures or hydration errors');
  await page.getByLabel(/^Email/).fill('owner@example.test');await page.getByLabel(/^Password/).fill('test-password-123');await page.getByRole('button',{name:'Continue',exact:true}).click();
  // The submit click does not await SessionProvider's asynchronous login/load.
  // Sign-in is inline on /settings; observe that protected page again before
  // a hard navigation can abort login/load. The fixture is still a VIEWER.
  await page.getByTestId('operating-profile').waitFor();
  assert.ok(await page.getByTestId('operating-profile').isDisabled());
  await page.goto(`${origin}/ask-finsight`);await page.getByTestId('copilot-mode').getByText('AI assistant',{exact:true}).waitFor();
  unavailable=true;await ask('Explain the trend');await page.getByTestId('copilot-answer').waitFor();writeFileSync(`${out}/fallback-contract.json`,JSON.stringify({statusMode:'openai',response:copilotFixture({unavailable:true,businessLabel:'fixture B'}),displayed:await page.getByTestId('copilot').innerText()},null,2));
  assert.match(await page.getByTestId('copilot').innerText(),/Offline assistant|not AI-generated|provider unavailable|fallback/i,'DEF-08: a deterministic provider_unavailable answer must be disclosed instead of only the pre-request AI assistant status');
  mark('per-answer deterministic provider fallback is disclosed');
  writeFileSync(`${out}/results.json`, JSON.stringify({ scope: 'Synthetic API fixture + production Next; not real provider or deployed acceptance', checks, errors }, null, 2));
} catch (error) { writeFileSync(`${out}/results.json`,JSON.stringify({scope:'Synthetic copilot-v1/configuration fixture + frozen production Next',checks,errors,status:'FAILED',failure:String(error)},null,2));if (browser) { const page = browser.contexts()[0]?.pages()[0]; await page?.screenshot({ path: `${out}/failure.png`, fullPage: true }); console.error(await page?.locator('body').innerText()); } throw error; }
finally { await browser?.close(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
