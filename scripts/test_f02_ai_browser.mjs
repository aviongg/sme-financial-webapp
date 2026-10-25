// Explicit opt-in acceptance: production Next + real Spring HTTP + disposable PostgreSQL.
// Launch only through F02AiBrowserAcceptance; synthetic accounts and simulated AI only.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdirSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.FINSIGHT_BROWSER_BASE_URL || 'http://127.0.0.1:3100';
assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/, 'Disposable local acceptance only');
const out = resolve(process.env.BROWSER_ARTIFACT_DIR || '../tmp/f02-ai-launch/real-browser');
mkdirSync(out, {recursive:true});
const checks = [], errors = [], externalRequests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Browser-synthetic-2026!';
const email = `f02-owner-${Date.now()}@example.test`;
const viewerEmail = email.replace('owner', 'viewer');
const browser = await chromium.launch({headless:true, channel:'msedge'});
const context = await browser.newContext({viewport:{width:1440,height:1000}});
const page = await context.newPage();
page.setDefaultTimeout(20000);
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => {if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(message.text())) errors.push(message.text());});
context.on('request', request => {if (/^https?:/.test(request.url()) && new URL(request.url()).hostname !== '127.0.0.1') externalRequests.push(new URL(request.url()).origin);});
async function api(client, path, {method='GET', data, multipart, status=200, csrf=true}={}) {
  const headers = {};
  if (method !== 'GET' && csrf) {
    const tokenResponse = await client.get(`${base}/api/auth/csrf`);
    assert.equal(tokenResponse.status(), 200);
    const token = await tokenResponse.json(); headers[token.headerName] = token.token;
  }
  const response = await client.fetch(`${base}${path}`, {method, headers, ...(data === undefined ? {} : {data}), ...(multipart ? {multipart} : {})});
  assert.equal(response.status(), status, `${method} ${path}: ${await response.text()}`);
  return response.status() === 204 ? null : response.json();
}
const configBody = value => ({operatingProfile:value.operatingProfile,operatingAnswers:value.operatingAnswers,requestedModules:value.requestedModules,version:value.version});
async function ask(question) {
  await page.getByTestId('ask-question').fill(question);
  const response = page.waitForResponse(r => r.url().endsWith('/api/ai/explain') && r.request().method() === 'POST');
  await page.getByTestId('ask-send').click();
  const result = await response; assert.equal(result.status(),200);
  const body = await result.json(); await page.getByTestId('ai-response').last().waitFor(); return body;
}
try {
  await api(context.request, '/api/auth/register', {method:'POST', data:{email,password,fullName:'Synthetic F02 Owner'}, status:201});
  await page.goto(base);
  await page.getByRole('heading', {name:'Sign in to FinSight'}).waitFor();
  await page.getByLabel(/^Email/).fill(email); await page.getByLabel(/^Password/).fill(password);
  await page.getByRole('button',{name:'Continue',exact:true}).click();
  await page.getByLabel('Business name',{exact:false}).fill('Synthetic F02 Trading');
  await page.getByRole('button',{name:'Create business',exact:true}).click();
  await page.getByTestId('business-configuration').waitFor();
  await page.getByTestId('operating-profile').selectOption('PROJECT_ORDER');
  await page.getByTestId('operating-tracksProjects').check();
  assert.equal(await page.getByTestId('module-inventory').isDisabled(),true);
  assert.equal(await page.getByTestId('module-core_finance').isDisabled(),true);
  await page.getByTestId('save-configuration').click();
  await page.getByText('Business configuration saved.',{exact:true}).waitFor();
  let configuration = await api(context.request, '/api/business-configuration');
  assert.equal(configuration.operatingProfile,'PROJECT_ORDER'); assert.equal(configuration.configured,true);
  const firstBusiness = await api(context.request,'/api/businesses/active');
  assert.equal(firstBusiness.businessType,'trade');
  await page.reload(); await page.getByTestId('operating-profile').waitFor();
  assert.equal(await page.getByTestId('operating-profile').inputValue(),'PROJECT_ORDER');
  mark('Real account/business onboarding saves F02 and reloads without changing scoring category');

  await api(context.request,'/api/business-configuration',{method:'PUT',data:configBody(configuration),csrf:false,status:403});
  await api(context.request,'/api/business-configuration',{method:'PUT',data:{...configBody(configuration),requestedModules:[...configuration.requestedModules,'inventory']},status:400});
  const updated = await api(context.request,'/api/business-configuration',{method:'PUT',data:configBody(configuration)});
  await api(context.request,'/api/business-configuration',{method:'PUT',data:configBody(configuration),status:409});
  configuration = updated;
  mark('Actual HTTP CSRF, unavailable-module validation and optimistic version conflict enforced');

  for (const [month,cashInflow,cashOutflow] of [['2026-07',100000,60000],['2026-08',95000,70000],['2026-09',90000,80000]]) {
    await api(context.request,'/api/records/monthly',{method:'POST',status:201,data:{month,cashInflow,cashOutflow,revenue:cashInflow,cogs:20000,operatingExpenses:40000,cashBalanceEom:15000,receivablesOutstanding:0,payablesOutstanding:0,inventoryValue:null,loanOutstanding:0,interestExpense:0,financingType:'none'}});
  }
  const beforeRecords = await api(context.request,'/api/records/monthly');
  const dashboard = await api(context.request,'/api/dashboard');
  assert.equal(dashboard.score.methodologyVersion,'health-score-v1');
  await page.goto(base); await page.getByTestId('ai-dashboard').waitFor();
  const dashboardReply = page.waitForResponse(r=>r.url().endsWith('/api/ai/explain') && r.request().method()==='POST');
  await page.getByTestId('ai-dashboard').getByRole('button',{name:'Explain this result',exact:true}).click();
  const explained = await (await dashboardReply).json();
  assert.equal(explained.provenance.mode,'simulated'); assert.ok(explained.evidence.length>0);
  assert.ok(explained.evidence.some(e=>e.value !== null && Number(e.value)===dashboard.score.compositeScore),'Existing score is present exactly in financial evidence');
  await page.getByTestId('ai-response').waitFor(); await page.getByTestId('ai-provenance').waitFor();
  mark('Dashboard explanation uses real persisted score and clearly simulated provenance');

  await page.goto(`${base}/health/components?month=2026-09`);
  const recommendation = page.getByTestId('ai-recommendation').first();
  await recommendation.waitFor(); await recommendation.getByRole('button',{name:'Explain this recommendation',exact:true}).click();
  await recommendation.getByTestId('ai-response').waitFor();
  const insight = page.getByTestId('ai-insight').first();
  await insight.waitFor(); await insight.getByRole('button',{name:'Explain this insight',exact:true}).click();
  await insight.getByTestId('ai-response').waitFor();
  assert.deepEqual(await api(context.request,'/api/records/monthly'),beforeRecords);
  mark('Insight and recommendation explanation UI consumes real advice without financial writes');

  await page.goto(`${base}/ask-finsight`); await page.getByTestId('ask-question').waitFor();
  const monthly = await ask('Show monthly financial records for 2026-09');
  assert.equal(monthly.provenance.mode,'simulated');
  assert.ok(monthly.evidence.some(e=>e.period==='2026-09'));
  const unsupported = await ask('Show my inventory and unpaid invoices');
  assert.equal(unsupported.provenance.mode,'deterministic');
  assert.ok(unsupported.reasonCode);
  mark('Ask FinSight uses the requested month and explains unsupported modules');
  await page.evaluate(()=>window.scrollTo(0,0));
  await page.screenshot({path:resolve(out,'ask-desktop.png'),fullPage:true});

  await page.goto(`${base}/settings`); await page.getByTestId('module-documents').uncheck();
  await page.getByTestId('module-whatsapp').uncheck();
  await page.getByTestId('save-configuration').click();
  await page.getByText('Business configuration saved.',{exact:true}).waitFor();
  await api(context.request,'/api/documents');
  await api(context.request,'/api/documents/query',{method:'POST',data:{}});
  await api(context.request,'/api/documents/upload',{method:'POST',multipart:{file:{name:'synthetic.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4\nsynthetic disabled-module check')}},status:403});
  await api(context.request,'/api/profile/whatsapp',{method:'PATCH',data:{optIn:true,whatsappNumber:'+923001234567'},status:403});
  await page.goto(`${base}/scan`);
  await page.getByText('This module is disabled. Historical information remains available; new actions are unavailable.',{exact:true}).waitFor();
  assert.equal(await page.locator('input[type=file]:enabled').count(),0);
  mark('Disabling modules blocks new writes while real document history remains readable');

  const secondBusiness = await api(context.request,'/api/businesses',{method:'POST',status:201,data:{businessName:'Synthetic Empty Services',businessType:'services',languagePreference:'en',whatsappOptIn:false}});
  await page.goto(`${base}/settings`); await page.getByLabel('Active business').waitFor();
  await page.getByLabel('Active business').selectOption(firstBusiness.businessId);
  await page.waitForFunction(()=>document.querySelector('[data-testid="operating-profile"]')?.value==='PROJECT_ORDER');
  assert.equal(await page.getByTestId('operating-profile').inputValue(),'PROJECT_ORDER');
  await page.getByLabel('Active business').selectOption(secondBusiness.businessId);
  await page.waitForFunction(()=>document.querySelector('[data-testid="operating-profile"]')?.value==='SERVICES');
  assert.equal(await page.getByTestId('operating-profile').inputValue(),'SERVICES');
  await page.goto(`${base}/ask-finsight`); await page.getByTestId('ask-question').waitFor();
  assert.equal(await page.getByTestId('ai-response').count(),0);
  const empty = await ask('Explain my financial health score');
  assert.ok(empty.evidence.every(e=>e.value===null),'Other business evidence does not leak');
  mark('Business switching preserves separate configurations and clears conversation/evidence');

  await page.goto(`${base}/settings`); await page.getByLabel('Active business').selectOption(firstBusiness.businessId);
  await page.waitForFunction(()=>document.querySelector('[data-testid="operating-profile"]')?.value==='PROJECT_ORDER');
  await page.getByTestId('module-ai').uncheck(); await page.getByTestId('save-configuration').click();
  await page.getByText('Business configuration saved.',{exact:true}).waitFor();
  await api(context.request,'/api/ai/explain',{method:'POST',data:{type:'dashboard',language:'en'},status:403});
  await page.goto(`${base}/ask-finsight`);
  await page.getByText('AI explanations are not activated. Your existing FinSight results and recommendations remain available.',{exact:true}).waitFor();
  assert.equal(await page.getByTestId('ask-question').count(),0);
  await api(context.request,'/api/dashboard');
  mark('Disabled AI cannot be invoked and deterministic financial dashboard remains available');

  await page.goto(`${base}/settings`); await page.getByTestId('module-ai').check(); await page.getByTestId('save-configuration').click();
  await page.getByText('Business configuration saved.',{exact:true}).waitFor();
  await page.getByRole('button',{name:'اردو',exact:true}).first().click();
  await page.waitForFunction(()=>document.documentElement.dir==='rtl');
  await page.setViewportSize({width:390,height:844});
  await page.getByTestId('business-configuration').waitFor();
  // Viewport resize and the Urdu font can finish on different frames; inspect the settled layout.
  await page.evaluate(async()=>{await document.fonts.ready;await new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)));});
  writeFileSync(resolve(out,'configuration-mobile-layout.json'),JSON.stringify(await page.evaluate(()=>({
    viewport:window.innerWidth,scrollWidth:document.documentElement.scrollWidth,
    overflowing:[...document.querySelectorAll('main *')].map(element=>({tag:element.tagName,text:element.textContent?.slice(0,100),rect:element.getBoundingClientRect().toJSON()})).filter(item=>item.rect.right>window.innerWidth+2||item.rect.left < -2)
  })),null,2));
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+2));
  await page.evaluate(()=>window.scrollTo(0,0));
  await page.screenshot({path:resolve(out,'configuration-urdu-mobile.png'),fullPage:true});
  await page.goto(`${base}/ask-finsight`); const urdu = await ask('مالی صحت کا اسکور بتائیں');
  assert.equal(urdu.language,'ur'); assert.equal(urdu.provenance.mode,'simulated');
  assert.ok(/[\u0600-\u06ff]/.test(urdu.summary));
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+2));
  await page.evaluate(()=>window.scrollTo(0,0));
  await page.screenshot({path:resolve(out,'ask-urdu-mobile.png'),fullPage:true});
  mark('Urdu configuration and grounded assistant work in mobile RTL layout');

  const viewer = await browser.newContext();
  await api(viewer.request,'/api/auth/register',{method:'POST',data:{email:viewerEmail,password,fullName:'Synthetic Viewer'},status:201});
  const invite = await api(context.request,'/api/memberships',{method:'POST',data:{email:viewerEmail,role:'VIEWER'},status:201});
  await api(viewer.request,'/api/auth/login',{method:'POST',data:{email:viewerEmail,password}});
  await api(viewer.request,`/api/invitations/${invite.id}/accept`,{method:'POST',status:204});
  await api(viewer.request,'/api/businesses/active',{method:'POST',data:{businessId:firstBusiness.businessId}});
  const viewerConfig = await api(viewer.request,'/api/business-configuration'); assert.equal(viewerConfig.canManage,false);
  await api(viewer.request,'/api/business-configuration',{method:'PUT',data:configBody(viewerConfig),status:403});
  const viewerPage = await viewer.newPage(); await viewerPage.goto(`${base}/settings`);
  await viewerPage.getByTestId('operating-profile').waitFor(); assert.equal(await viewerPage.getByTestId('operating-profile').isDisabled(),true);
  await viewerPage.goto(`${base}/ask-finsight`); await viewerPage.getByTestId('ask-question').waitFor();
  await api(viewer.request,'/api/businesses/active',{method:'POST',data:{businessId:secondBusiness.businessId},status:403});
  await viewer.close(); mark('Real viewer membership has read-only UI/API and cannot select an unrelated business');

  assert.deepEqual(errors,[]); assert.deepEqual(externalRequests,[]);
  mark('No browser runtime, hydration, CSP or external-network errors');
  writeFileSync(resolve(out,'results.json'),JSON.stringify({scope:'real Next/Spring/disposable PostgreSQL; synthetic data; simulated AI; no paid provider',checks,errors,externalRequests},null,2));
  console.log(`RESULT ${checks.length} checks passed`);
} catch(error) {
  await page.screenshot({path:resolve(out,'failure.png'),fullPage:true}).catch(()=>{});
  writeFileSync(resolve(out,'failure.txt'),`${error.stack}\n${await page.locator('body').innerText().catch(()=>'')}`);
  throw error;
} finally { await browser.close(); }
