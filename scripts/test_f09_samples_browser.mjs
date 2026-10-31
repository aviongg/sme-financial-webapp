// Opt-in production Next + real Spring/session/CSRF + disposable PostgreSQL.
// Launch through F09SamplesBrowserAcceptance only. All accounts/data are synthetic.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.FINSIGHT_BROWSER_BASE_URL || 'http://127.0.0.1:3100';
assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/);
assert.ok(process.env.FINSIGHT_DB_BASELINE_READY && process.env.FINSIGHT_DB_BASELINE_CAPTURED,
  'Launch through F09SamplesBrowserAcceptance so database write probes are installed');
const out = resolve(process.env.BROWSER_ARTIFACT_DIR || '../logs/f09/real-browser');
mkdirSync(out, { recursive: true });
const checks = [], errors = [], externalRequests = [], requests = [], apiRequests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Synthetic-F09-2026!';
const email = `f09-owner-${Date.now()}@example.test`, viewerEmail = email.replace('owner', 'viewer');
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
function watchContext(current) {
  current.on('request', request => {
    const url = new URL(request.url());
    requests.push({ path: url.pathname, method: request.method() });
    if (/^https?:/.test(url.protocol) && url.hostname !== '127.0.0.1') externalRequests.push(url.origin);
  });
  current.on('page', currentPage => {
    currentPage.setDefaultTimeout(20000);
    currentPage.on('pageerror', error => errors.push(error.message));
    currentPage.on('console', message => {
      if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/i.test(message.text())) errors.push(message.text());
    });
    currentPage.on('response', response => { if (response.status() >= 500) errors.push(`${response.status()} ${response.url()}`); });
  });
}
watchContext(context);
const page = await context.newPage();
const id = name => page.getByTestId(name);
async function api(client, path, { method = 'GET', data, status = 200, csrf = true } = {}) {
  const headers = {};
  if (method !== 'GET' && csrf) {
    const tokenResponse = await client.get(`${base}/api/auth/csrf`);
    assert.equal(tokenResponse.status(), 200);
    const token = await tokenResponse.json(); headers[token.headerName] = token.token;
  }
  apiRequests.push({ path, method });
  const response = await client.fetch(`${base}${path}`, { method, headers, ...(data === undefined ? {} : { data }) });
  assert.equal(response.status(), status, `${method} ${path}: ${await response.text()}`);
  return response.status() === 204 ? null : response.json();
}
async function uiReply(path, method, action, status = 200) {
  const pending = page.waitForResponse(response => new URL(response.url()).pathname === path && response.request().method() === method);
  await action();
  const response = await pending;
  assert.equal(response.status(), status, `${method} ${path}: ${await response.text()}`);
  return { body: response.status() === 204 ? null : await response.json(), sent: response.request().postDataJSON() };
}
async function settled() {
  await page.evaluate(async () => {
    await document.fonts?.ready;
    await Promise.all(document.getAnimations({ subtree: true }).map(animation => animation.finished.catch(() => {})));
    await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
  });
}
async function noOverflow() {
  await settled();
  const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  assert.ok(dimensions.document <= dimensions.viewport + 2, JSON.stringify(dimensions));
}
async function capture(name) {
  await settled();
  await page.evaluate(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }));
  await page.screenshot({ path: resolve(out, name), fullPage: true });
}
async function metric(name, value) {
  assert.ok((await id(name).innerText()).replaceAll(',', '').includes(value), `${name} must show server value ${value}`);
}
page.on('dialog', dialog => dialog.accept());
const lines = (quantity = '2') => ['MATERIAL','LABOUR','OVERHEAD','FREIGHT','OTHER'].map((category,index) => ({category,description:`Synthetic ${category} plan`,quantity,unit:'batch',unitCost:['100','25','15','5','5'][index]}));
const planInput = plan => ({plannedRecovery:plan.plannedRecovery,lines:plan.lines.map(({category,description,quantity,unit,unitCost})=>({category,description,quantity,unit,unitCost}))});
async function waitValue(name,value) {
  await page.waitForFunction(([name,value]) => {
    const text = document.querySelector(`[data-testid="${name}"]`)?.textContent;
    return text?.replace(/PKR|روپے|[,\s]/g, '') === value;
  },[name,value]);
}
async function createSample(kind,intent,code,projectId='') {
  await page.goto(`${base}/samples`); await id('create-sample-button').click(); await id('sample-create-form').waitFor();
  await id('sample-code-input').fill(code); await id('sample-kind-select').selectOption(kind); await id('sample-name-input').fill(`Synthetic ${intent} ${kind}`);
  await id('sample-description-input').fill('Planning only; no actual costs, revenue or cash.'); await id('sample-recovery-intent-select').selectOption(intent);
  if(projectId) await id('sample-project-select').selectOption(projectId);
  await id('sample-start-date-input').fill('2026-10-01'); await id('sample-end-date-input').fill('2026-11-30');
  const result=await uiReply('/api/samples','POST',()=>id('sample-create-submit').click(),201);
  for(const field of ['businessId','actorUserId','createdBy','updatedBy','financialBasis','status','plannedCost']) assert.ok(!(field in result.sent),field);
  assert.equal(result.body.kind,kind); assert.equal(result.body.recoveryIntent,intent); assert.equal(result.body.projectId,projectId||null);
  assert.equal(result.body.financialBasis,'PLANNED_ONLY'); assert.equal(result.body.planningOnly,true); assert.equal(result.body.baseCurrency,'PKR');
  await id('sample-plan-editor').waitFor(); return result.body;
}
async function fillLine(index,input) {
  for(const key of ['category','description','quantity','unit','unitCost']) {const field=id(`sample-line-${index}-${key==='unitCost'?'unit-cost':key}`);if(key==='category')await field.selectOption(input[key]);else await field.fill(input[key]);}
}
async function fillLines(inputs) {for(let index=0;index<inputs.length;index++){if(index){await id('sample-add-line').focus();await page.keyboard.press('Enter');}await fillLine(index,inputs[index]);}}
async function save(activity,draft=null) {
  const result=await uiReply(`/api/samples/${activity.id}/plans${draft?`/${draft.id}`:''}`,draft?'PUT':'POST',()=>id('sample-plan-save').click(),draft?200:201);
  await id('sample-saved-notice').waitFor(); await waitValue('sample-summary-plannedCost',result.body.plannedCost);
  for(const field of ['businessId','actorUserId','status','financialBasis','plannedCost','plannedContribution','approvedBy'])assert.ok(!(field in result.sent),field);
  assert.equal(result.body.financialBasis,'PLANNED_ONLY');return result.body;
}
async function approve(activity,plan) {
  const result=await uiReply(`/api/samples/${activity.id}/plans/${plan.id}/approve`,'POST',()=>id('sample-plan-approve').click());
  await id('sample-new-revision').waitFor();assert.equal(result.body.status,'APPROVED');return result.body;
}
async function transition(activity,status) {
  await id('sample-transition-select').selectOption(status);const result=await uiReply(`/api/samples/${activity.id}/transition`,'POST',()=>id('sample-transition-submit').click());
  assert.equal(result.body.status,status);await id('sample-saved-notice').waitFor();return result.body;
}
async function switchBusiness(businessId) {
  await api(context.request,'/api/businesses/active',{method:'POST',data:{businessId}});
  const refreshed=page.waitForResponse(async response=>{
    if(!response.url().endsWith('/api/businesses/active')||response.request().method()!=='GET'||response.status()!==200)return false;
    try{return(await response.json()).businessId===businessId;}catch{return false;}
  });await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await refreshed;
}
const estimatePayload={lines:[{costGroup:'MATERIAL',description:'Unchanged F06 probe',quantity:'1',unit:'batch',unitCost:'800'}],saleableQuantity:'10',pricingMode:'TARGET_MARGIN',pricingValue:'0.20'};
const reportPayload={fromMonth:'2026-07',toMonth:'2026-09',language:'en'};
const comparableReport=({generatedAt:_time,...report})=>report;
try {
  await api(context.request,'/api/auth/register',{method:'POST',status:201,data:{email,password,fullName:'Synthetic Sample Owner'}});
  await page.goto(`${base}/samples`);await page.locator('input[type="email"]').fill(email);await page.locator('input[type="password"]').fill(password);
  await uiReply('/api/auth/login','POST',()=>page.locator('form button[type="submit"]').click());mark('01. Authenticate through real UI, session and CSRF');
  const other=await api(context.request,'/api/businesses',{method:'POST',status:201,data:{businessName:'F09 Other Business',businessType:'retail',languagePreference:'en',whatsappOptIn:false}});
  const business=await api(context.request,'/api/businesses',{method:'POST',status:201,data:{businessName:'F09 Planning Studio',businessType:'manufacturing',languagePreference:'en',whatsappOptIn:false}});
  for(const month of ['2026-07','2026-08','2026-09'])await api(context.request,'/api/records/monthly',{method:'POST',status:201,data:{month,cashInflow:10000,cashOutflow:8000,revenue:12000,operatingExpenses:3000,cashBalanceEom:6000,cogs:4000,receivablesOutstanding:0,payablesOutstanding:0,inventoryValue:null,loanOutstanding:0,interestExpense:0,financingType:'none'}});
  await api(context.request,'/api/dashboard'); // Warm existing unrelated advice/score behavior before strict baseline.
  const project=await api(context.request,'/api/projects',{method:'POST',status:201,data:{code:'F09-CONTEXT',name:'Existing F05 context only',kind:'JOB'}});
  const budget=await api(context.request,`/api/projects/${project.id}/budgets`,{method:'POST',status:201,data:{expectedProjectVersion:0,plannedRevenue:'1000',lines:lines()}});
  await api(context.request,`/api/projects/${project.id}/budgets/${budget.id}/approve`,{method:'POST',data:{expectedProjectVersion:1,expectedBudgetVersion:0}});
  const document={counterpartyName:'Synthetic Existing Counterparty',documentDate:'2026-10-28',dueDate:'2026-11-28',notes:'Preexisting F04 fixture',lines:[{description:'Commercial fixture line',quantity:'2',unit:'batch',unitPrice:'50.00'}]};
  const invoice=await api(context.request,'/api/invoices',{method:'POST',status:201,data:document});
  await api(context.request,`/api/invoices/${invoice.id}/finalize`,{method:'POST',data:{expectedVersion:0}});
  await api(context.request,'/api/bills',{method:'POST',status:201,data:document});
  const before={records:await api(context.request,'/api/records/monthly'),score:await api(context.request,'/api/scores/latest'),project:await api(context.request,`/api/projects/${project.id}/planning-summary`),invoices:await api(context.request,'/api/invoices'),bills:await api(context.request,'/api/bills'),estimate:await api(context.request,'/api/estimates/preview',{method:'POST',data:estimatePayload}),report:comparableReport(await api(context.request,'/api/reports/business-financial-summary',{method:'POST',data:reportPayload}))};
  assert.equal(before.score.methodologyVersion,'health-score-v1');
  await page.goto(`${base}/samples`);await id('samples-empty-state').waitFor();
  const networkBaseline=requests.length,apiBaseline=apiRequests.length;
  writeFileSync(process.env.FINSIGHT_DB_BASELINE_READY,'Nonempty actual, F04 and F05 fixtures ready for strict no-write baseline.');
  const deadline=Date.now()+120000;while(!existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED)&&Date.now()<deadline)await delay(100);
  assert.ok(existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED));mark('02. Actuals, F04 invoices/bills/numbering and F05 approved budgets precede SQL write probes');
  for(const path of ['/','/samples','/projects','/reports','/estimates','/invoices','/bills','/records','/upload','/settings'])assert.ok(await page.locator(`aside a[href="${path}"]`).count(),path);
  assert.match(await id('samples-planning-banner').innerText(),/PLANNED ONLY/);mark('03. Empty Samples workspace preserves F04/F05/Reports/Estimates/Dashboard navigation');
  await id('create-sample-button').click();await id('sample-create-submit').click();
  await page.waitForFunction(()=>document.querySelector('[data-testid="sample-code-input"]')?.getAttribute('aria-invalid')==='true');
  assert.equal(await page.evaluate(()=>document.activeElement?.getAttribute('data-testid')),'sample-code-input');
  assert.equal(requests.slice(networkBaseline).filter(r=>r.path==='/api/samples'&&r.method==='POST').length,0);mark('04. Empty form validation focuses the invalid keyboard field without posting');
  const sample=await createSample('SAMPLE','FREE','F09-FREE',project.id);
  assert.equal(await id('sample-planned-recovery-input').inputValue(),'0');assert.equal(await id('sample-planned-recovery-input').isDisabled(),true);assert.ok(await id('sample-project-context').count());
  mark('05. Create FREE SAMPLE and associate an existing same-business F05 project as context');
  await fillLines(lines());await id('sample-line-0-quantity').fill('-1');await id('sample-plan-save').click();
  await page.waitForFunction(()=>document.querySelector('[data-testid="sample-line-0-quantity"]')?.getAttribute('aria-invalid')==='true');await id('sample-line-0-quantity').fill('2');
  let first=await save(sample);assert.equal(first.plannedCost,'300.00');assert.equal(first.plannedRecovery,'0.00');assert.equal(first.plannedContribution,'-300.00');
  assert.deepEqual(first.categorySubtotals,{MATERIAL:'200.00',LABOUR:'50.00',OVERHEAD:'30.00',FREIGHT:'10.00',OTHER:'10.00'});
  for(const[key,value]of Object.entries({plannedCost:'300.00',plannedRecovery:'0.00',plannedContribution:'-300.00'}))await waitValue(`sample-summary-${key}`,value);
  mark('06. Keyboard line editing and validation produce exact server totals across five cost categories');
  first=await approve(sample,first);await page.reload();await id('sample-new-revision').waitFor();assert.equal(await id('sample-plan-editor').count(),0);mark('07. Approved plan survives reload and becomes immutable');
  await id('sample-new-revision').click();for(let index=0;index<5;index++)await id(`sample-line-${index}-quantity`).fill('3');
  let replacement=await save(sample);assert.equal(replacement.revision,2);assert.equal(replacement.plannedCost,'450.00');replacement=await approve(sample,replacement);
  const history=await api(context.request,`/api/samples/${sample.id}/plans`);assert.equal(history[0].status,'APPROVED');assert.equal(history[1].status,'SUPERSEDED');assert.equal(history[1].supersededByPlanId,replacement.id);assert.deepEqual(history[1].lines,first.lines);assert.equal(history[1].plannedCost,'300.00');
  await id('sample-plan-revision-1').locator('[data-plan-status="SUPERSEDED"]').waitFor();await id('sample-plan-revision-2').locator('[data-plan-status="APPROVED"]').waitFor();await id('sample-plan-revision-1').locator('summary').click();await capture('sample-approved-history-desktop-en.png');
  mark('08. Replacement approval supersedes the old revision while preserving its immutable values and lines');
  const production=await createSample('PRODUCTION_COST_ACTIVITY','PAID','F09-PRODUCTION');await fillLines(lines());await id('sample-planned-recovery-input').fill('600');
  let paid=await save(production);assert.equal(paid.plannedCost,'300.00');assert.equal(paid.plannedRecovery,'600.00');assert.equal(paid.plannedContribution,'300.00');
  await page.setViewportSize({width:390,height:844});await noOverflow();assert.equal(await id('sample-line-0-quantity').getAttribute('dir'),'ltr');await capture('production-plan-editor-mobile-en.png');
  mark('09. PAID production planning separates cost/recovery/contribution and mobile line editor has no overflow');paid=await approve(production,paid);await page.setViewportSize({width:1440,height:1000});
  const partial=await createSample('PRODUCTION_COST_ACTIVITY','PARTIAL','F09-PARTIAL');await fillLine(0,{category:'LABOUR',description:'Unknown recovery plan',quantity:'2',unit:'hour',unitCost:'40'});
  let partialPlan=await save(partial);assert.equal(partialPlan.plannedRecovery,null);assert.equal(partialPlan.plannedContribution,null);assert.equal(partialPlan.unavailableReasons.plannedContribution,'PLANNED_RECOVERY_UNKNOWN');await id('sample-summary-unavailable').waitFor();
  await id('sample-planned-recovery-input').fill('0');partialPlan=await save(partial,partialPlan);assert.equal(partialPlan.plannedRecovery,'0.00');assert.equal(partialPlan.plannedContribution,'-80.00');mark('10. PARTIAL recovery distinguishes unknown from explicit zero');
  await api(context.request,`/api/samples/${partial.id}/plans/${partialPlan.id}`,{method:'PUT',data:{...planInput(partialPlan),plannedRecovery:'30',expectedPlanVersion:partialPlan.version}});
  await id('sample-planned-recovery-input').fill('55');await uiReply(`/api/samples/${partial.id}/plans/${partialPlan.id}`,'PUT',()=>id('sample-plan-save').click(),409);
  await id('sample-conflict').waitFor();assert.equal(await id('sample-planned-recovery-input').inputValue(),'55');await id('sample-reload').click();
  await page.waitForFunction(()=>!document.querySelector('[data-testid="sample-conflict"]'));await page.waitForFunction(()=>document.querySelector('[data-testid="sample-planned-recovery-input"]')?.value==='30');
  partialPlan=(await api(context.request,`/api/samples/${partial.id}`)).latestDraftPlan;await approve(partial,partialPlan);await waitValue('sample-summary-plannedContribution','-50.00');mark('11. Real stale edit returns409, preserves user draft, and explicit reload recovers server state');
  await id('sample-edit-metadata').click();assert.equal(await id('sample-recovery-intent-select').isDisabled(),true);await id('sample-name-input').fill('Edited partial production plan');
  await uiReply(`/api/samples/${partial.id}`,'PUT',()=>id('sample-save-metadata').click());await id('sample-saved-notice').waitFor();
  await transition(partial,'ACTIVE');await transition(partial,'COMPLETED');await transition(partial,'ARCHIVED');assert.equal(await id('sample-plan-editor').count(),0);assert.equal(await id('sample-edit-metadata').count(),0);
  assert.match(await id('sample-lifecycle').innerText(),/paid|payment|financial settlement/i);mark('12. Metadata edit and operational lifecycle work; archive is terminal/read-only');
  await page.goto(`${base}/samples`);await id('sample-item-F09-FREE').waitFor();await id('sample-item-F09-PRODUCTION').waitFor();await id('samples-include-archived').check();await id('sample-item-F09-PARTIAL').waitFor();await capture('samples-list-desktop-en.png');
  await id('open-sample-F09-FREE').click();await id('sample-plan-revision-2').waitFor();
  const summary=await api(context.request,`/api/samples/${sample.id}/planning-summary`);assert.equal(summary.financialBasis,'PLANNED_ONLY');assert.equal(summary.actualFinancialsStatus,'NOT_IMPLEMENTED_PHASE_A');
  for(const key of ['recognizedRecovery','receivedCash','outstandingRecovery','paidStatus','actualCost']){assert.ok(!(key in summary));assert.ok(!(key in summary.activeApprovedPlan));}
  const configuration=await api(context.request,'/api/business-configuration');for(const key of ['production','inventory']){const module=configuration.modules.find(item=>item.key===key);assert.ok(module,key);assert.equal(module.implemented,false);assert.equal(module.available,false);}
  mark('13. Persisted history and module registry expose bounded planning only');
  await page.setViewportSize({width:390,height:844});await noOverflow();
  const language=page.waitForResponse(response=>response.url().endsWith('/api/profile/language')&&response.request().method()==='PATCH');await page.getByRole('button',{name:'اردو',exact:true}).first().click();assert.equal((await language).status(),200);
  await page.waitForFunction(()=>document.documentElement.dir==='rtl');assert.match(await id('samples-planning-banner').innerText(),/[\u0600-\u06ff]/);assert.equal(await page.evaluate(()=>getComputedStyle(document.querySelector('main')).direction),'rtl');
  await waitValue('sample-summary-plannedCost','450.00');assert.equal(await id('sample-summary-plannedCost').getAttribute('dir'),'ltr');await noOverflow();await capture('sample-history-mobile-ur-rtl.png');
  await page.setViewportSize({width:1440,height:1000});await noOverflow();await capture('sample-history-desktop-ur-rtl.png');mark('14. EN/UR/RTL desktop and mobile preserve readable LTR amounts without horizontal overflow');await page.waitForLoadState('networkidle');
  for(const role of ['MANAGER','VIEWER']) {
    const read=await browser.newContext({viewport:{width:1440,height:1000}});watchContext(read);const roleEmail=email.replace('owner',role.toLowerCase());
    await api(read.request,'/api/auth/register',{method:'POST',status:201,data:{email:roleEmail,password,fullName:`Synthetic F09 ${role}`}});
    const invitation=await api(context.request,'/api/memberships',{method:'POST',status:201,data:{email:roleEmail,role}});
    await api(read.request,'/api/auth/login',{method:'POST',data:{email:roleEmail,password}});await api(read.request,`/api/invitations/${invitation.id}/accept`,{method:'POST',status:204});await api(read.request,'/api/businesses/active',{method:'POST',data:{businessId:business.businessId}});
    const rp=await read.newPage();await rp.goto(`${base}/samples`);await rp.getByTestId('samples-readonly-banner').waitFor();assert.equal(await rp.getByTestId('create-sample-button').count(),0);
    await rp.goto(`${base}/samples/${sample.id}`);await rp.getByTestId('samples-readonly-banner').waitFor();for(const control of ['sample-edit-metadata','sample-delete-draft','sample-plan-editor','sample-new-revision','sample-plan-approve','sample-transition-submit'])assert.equal(await rp.getByTestId(control).count(),0,`${role} ${control}`);
    await rp.goto(`${base}/samples/new`);await rp.getByTestId('sample-create-forbidden').waitFor();assert.equal(await rp.getByTestId('sample-create-form').count(),0);
    await api(read.request,'/api/samples',{method:'POST',status:403,data:{code:'DENIED',name:'Denied',kind:'SAMPLE',recoveryIntent:'FREE'}});
    await api(read.request,`/api/samples/${sample.id}/plans`,{method:'POST',status:403,data:{expectedActivityVersion:summary.activity.version,plannedRecovery:'0',lines:lines()}});
    await api(read.request,`/api/samples/${sample.id}/plans/${replacement.id}/approve`,{method:'POST',status:403,data:{expectedActivityVersion:summary.activity.version,expectedPlanVersion:replacement.version}});
    await read.close();mark(`${role==='MANAGER'?'15':'16'}. ${role} reads history without mutation controls and write requests are forbidden`);
  }
  await switchBusiness(other.businessId);await id('sample-error').waitFor();assert.equal(await id('sample-summary-plannedCost').count(),0);assert.equal(await id('sample-plan-history').count(),0);await api(context.request,`/api/samples/${sample.id}`,{status:404});
  await page.goto(`${base}/samples`);await id('samples-empty-state').waitFor();assert.doesNotMatch(await page.locator('main').innerText(),/F09-FREE|F09-PRODUCTION/);
  await switchBusiness(business.businessId);await page.goto(`${base}/samples/${sample.id}`);await id('sample-plan-revision-2').waitFor();mark('17. Business switching removes old results and enforces cross-business404');
  assert.deepEqual(await api(context.request,'/api/records/monthly'),before.records);assert.deepEqual(await api(context.request,'/api/scores/latest'),before.score);
  assert.deepEqual(await api(context.request,`/api/projects/${project.id}/planning-summary`),before.project);assert.deepEqual(await api(context.request,'/api/invoices'),before.invoices);assert.deepEqual(await api(context.request,'/api/bills'),before.bills);
  mark('18. Actuals, health-score-v1, F05 budgets and F04 documents remain identical; Java verifies SQL writes and number sequences');
  assert.deepEqual(await api(context.request,'/api/estimates/preview',{method:'POST',data:estimatePayload}),before.estimate);assert.deepEqual(comparableReport(await api(context.request,'/api/reports/business-financial-summary',{method:'POST',data:reportPayload})),before.report);
  const financialWrite=r=>r.method!=='GET'&&/^\/api\/(records|score|transactions|ledger|inventory|funding|projects|invoices|bills)(\/|$)/.test(r.path);
  assert.deepEqual(requests.slice(networkBaseline).filter(financialWrite),[]);assert.deepEqual(apiRequests.slice(apiBaseline).filter(financialWrite),[]);mark('19. F06 and F12B outputs remain equivalent; zero actual/F04/F05 mutation requests');
  assert.deepEqual(errors,[]);assert.deepEqual(externalRequests,[]);mark('20. Zero runtime/CSP/hydration errors or unexpected external requests');
  writeFileSync(resolve(out,'results.json'),JSON.stringify({scope:'Production Next + real Spring/session/CSRF + disposable PostgreSQL; synthetic accounts/data; no mocked success responses or paid providers',checks,errors,externalRequests,screenshots:['sample-approved-history-desktop-en.png','production-plan-editor-mobile-en.png','samples-list-desktop-en.png','sample-history-mobile-ur-rtl.png','sample-history-desktop-ur-rtl.png'],databaseBoundaryEvidence:'Java emits database-boundaries.json only after 15 complete table snapshots and zero SQL writes on actuals/F04/F05; profile language changes are intentional'},null,2));console.log(`RESULT ${checks.length} checks passed`);
} catch(error) {await page.screenshot({path:resolve(out,'failure.png'),fullPage:true}).catch(()=>{});writeFileSync(resolve(out,'failure.txt'),`${error.stack}\n${await page.locator('body').innerText().catch(()=>'')}`);throw error;}finally{await browser.close();}