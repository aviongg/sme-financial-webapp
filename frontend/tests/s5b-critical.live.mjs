
// Real Edge -> Next -> Spring acceptance. No route interception or synthetic API server.
// Requires an Antigravity-provided private JSON fixture manifest outside the repository.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import path from 'node:path';
const require=createRequire(import.meta.url);
const fixtureFile=process.env.FINSIGHT_LIVE_FIXTURE;
if(!fixtureFile)throw new Error('LIVE_ENVIRONMENT_REQUIRED: set FINSIGHT_LIVE_FIXTURE to the approved disposable V37 fixture manifest. No tests were skipped or passed.');
const fixture=JSON.parse(readFileSync(fixtureFile,'utf8'));
assert.equal(fixture.disposable,true,'Never run mutating acceptance against persistent user data');
assert.equal(fixture.migrationHead,37,'Fixture owner must supply V37 migration evidence');
const origin=new URL(fixture.frontendUrl);
assert.ok(['127.0.0.1','localhost','[::1]'].includes(origin.hostname),'Local acceptance only');
for(const field of ['email','password','businessAId','businessBId','businessAName'])assert.ok(fixture[field],`Fixture field required: ${field}`);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/PC/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const out=process.env.BROWSER_ARTIFACT_DIR||path.resolve('test-artifacts/s5b-live');mkdirSync(out,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true}),context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();
const journeys=[],requests=[],pageErrors=[],mutations=[];context.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))requests.push({method:r.method(),path:new URL(r.url()).pathname});});page.on('pageerror',e=>pageErrors.push(e.message));
const pass=(name,kind,endpoints,evidence={})=>{journeys.push({name,kind,endpoints,evidence,url:page.url(),fixtureBusiness:fixture.businessAId,passed:evidence.passed!==false});console.log((evidence.passed===false?'FAIL ':'PASS ')+name);};
async function api(url,method='GET',data,extraHeaders={}){let headers={...extraHeaders};if(method!=='GET'){const csrf=await context.request.get(new URL('/api/auth/csrf',origin).href);assert.equal(csrf.status(),200);const token=await csrf.json();headers={...headers,[token.headerName]:token.token};}const response=await context.request.fetch(new URL('/api'+url,origin).href,{method,data,headers});if(method!=='GET'&&!url.startsWith('/auth/')&&!url.startsWith('/businesses')){const body=await response.json().catch(()=>null);mutations.push({url,method,status:response.status(),request:data,entityId:body?.id??body?.paymentId??body?.estimate?.id??null});}return response;}
async function json(url){const r=await api(url);assert.equal(r.status(),200,url);return r.json();}
async function switchBusiness(id){assert.equal((await api('/businesses/active','POST',{businessId:id})).status(),200);await page.evaluate(()=>window.dispatchEvent(new Event('finsight-business-changed')));}
async function balances(){const data=await json('/finance/accounts?size=100');return data.items.map(({id,currentBalance,openingBalance,balanceStatus})=>({id,currentBalance,openingBalance,balanceStatus})).sort((a,b)=>a.id.localeCompare(b.id));}
const tag=randomUUID().slice(0,8),today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Karachi',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
try{
 await page.goto(new URL('/finance/accounts',origin).href);await page.getByRole('heading',{name:'Sign in to FinSight'}).waitFor();await page.getByLabel(/^Email/).fill(fixture.email);await page.getByLabel(/^Password/).fill(fixture.password);await page.getByRole('button',{name:'Continue',exact:true}).click();
 // Fixtures must use the established MFA/session procedure. A challenge fails here; it is never disabled.
 await page.waitForFunction(()=>document.querySelector('input[type=password]')===null);
 const chooser=page.getByRole('button',{name:new RegExp(fixture.businessAName.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'))});await chooser.or(page.getByRole('button',{name:/^(Add account|اکاؤنٹ شامل کریں)$/})).first().waitFor();if(await chooser.isVisible())await chooser.click();
 await page.getByRole('button',{name:/^(Add account|اکاؤنٹ شامل کریں)$/}).waitFor();if(await page.locator('html').getAttribute('dir')==='rtl'){await page.getByRole('button',{name:'English',exact:true}).click();await page.getByRole('button',{name:'Add account',exact:true}).waitFor();}
 const evidence=[],created=[];
 const check=async(name,fn)=>{if(process.env.S5B_ONLY && !process.env.S5B_ONLY.split(',').some(prefix=>name.startsWith(prefix)))return;try{const details=await fn();evidence.push({name,status:'PASS',details});console.log('PASS '+name);}catch(e){evidence.push({name,status:'FAIL',error:e.message});console.log('FAIL '+name+' '+e.message);await page.screenshot({path:path.join(out,'failure-'+evidence.length+'.png'),fullPage:true});}};
 const send=async(url,data,{method='POST',key=randomUUID(),status=201}={})=>{const r=await api(url,method,data,{'Idempotency-Key':key});const b=await r.json();assert.equal(r.status(),status,JSON.stringify({url,request:data,status:r.status(),response:b}));if(b.id||b.paymentId)created.push({url,id:b.id||b.paymentId});return b;};
 const newAccount=()=>send('/finance/accounts',{accountName:'S5B isolated '+tag+'-'+randomUUID().slice(0,4),accountType:'BANK',currency:'PKR',openingBalance:'10000.00',openingDate:today});
 const cash=async id=>(await json('/finance/accounts/'+id)).currentBalance;
 const count=async id=>(await json('/finance/transactions?accountId='+id+'&size=100')).totalElements;
 const obligation=(party,direction,amount)=>send('/finance/obligations',{counterpartyId:party.id,direction,amount,issueDate:today,dueDate:today,referenceNumber:'S5B-'+tag+'-'+randomUUID().slice(0,4),sourceType:'MANUAL_DECLARATION'});
 const beforeMonthly=await json('/records/monthly'),beforeScores=await json('/scores/history');
 await check('A customer advance: cash once, partial/full application, replay and over-allocation',async()=>{
 const a=await newAccount(),party=await send('/finance/counterparties',{displayName:'S5B customer '+tag,type:'CUSTOMER'}),ob=await obligation(party,'RECEIVABLE','1500.00');
 const adv=await send('/finance/advances',{counterpartyId:party.id,direction:'RECEIVABLE',accountId:a.id,paymentDate:today,amount:'1500.00',description:'S5B advance '+tag});
 assert.equal(await cash(a.id),'11500.00');assert.equal(adv.unappliedAmount,'1500.00');assert.equal(await count(a.id),1);
 const payload={obligationId:ob.id,amount:'500.00',applicationDate:today},key=randomUUID();
 const applied=await send('/finance/advances/'+adv.id+'/applications',payload,{key});assert.equal(applied.unappliedAmount,'1000.00');assert.equal((await json('/finance/obligations/'+ob.id)).outstandingAmount,'1000.00');
 const replay=await api('/finance/advances/'+adv.id+'/applications','POST',payload,{'Idempotency-Key':key});assert.equal(replay.status(),201);assert.equal((await replay.json()).id,adv.id);assert.equal(await count(a.id),1);assert.equal(await cash(a.id),'11500.00');
 const bad=await api('/finance/advances/'+adv.id+'/applications','POST',{...payload,amount:'1000.01'},{'Idempotency-Key':randomUUID()});assert.equal(bad.status(),409);const badBody=await bad.json();
 const exhausted=await send('/finance/advances/'+adv.id+'/applications',{...payload,amount:'1000.00'});assert.equal(exhausted.unappliedAmount,'0.00');assert.equal((await json('/finance/obligations/'+ob.id)).outstandingAmount,'0.00');
 const repeat=await api('/finance/advances/'+adv.id+'/applications','POST',payload,{'Idempotency-Key':randomUUID()});assert.equal(repeat.status(),409);assert.equal(await cash(a.id),'11500.00');assert.equal(await count(a.id),1);
 await page.goto(new URL('/records?view=advances',origin).href);await page.getByText(party.displayName,{exact:true}).first().waitFor();await page.screenshot({path:path.join(out,'advances-en.png'),fullPage:true});await page.getByRole('button',{name:'اردو',exact:true}).click();await page.waitForFunction(()=>document.documentElement.dir==='rtl');await page.screenshot({path:path.join(out,'advances-ur.png'),fullPage:true});await page.getByRole('button',{name:'English',exact:true}).click();
 return {accountId:a.id,advanceId:adv.id,obligationId:ob.id,opening:'10000.00',afterReceipt:'11500.00',afterApplications:'11500.00',transactions:1,unapplied:'0.00',outstanding:'0.00',overAllocation:{status:409,response:badBody},repeatStatus:409};
 });
 await check('B supplier payable: schedule nonposting, partial/full cash settlements, replay/overpayment',async()=>{
 const a=await newAccount(),party=await send('/finance/counterparties',{displayName:'S5B supplier '+tag,type:'SUPPLIER'}),ob=await obligation(party,'PAYABLE','3000.00');assert.equal(await cash(a.id),'10000.00');
 const plan=await send('/finance/obligations/'+ob.id+'/payment-plan',{installments:[{installmentNumber:1,expectedDate:today,expectedAmount:'3000.00'}]},{method:'PUT',status:200});assert.equal(await cash(a.id),'10000.00');assert.equal(await count(a.id),0);assert.equal(plan.installments[0].status,'PLANNED');
 const pay=amount=>({counterpartyId:party.id,direction:'PAYABLE',accountId:a.id,paymentDate:today,amount,allocations:[{obligationId:ob.id,amount}],retainAdvance:false});
 const key=randomUUID(),partial=await send('/finance/payments/allocate',pay('1000.00'),{key});assert.equal(await cash(a.id),'9000.00');assert.equal((await json('/finance/obligations/'+ob.id)).outstandingAmount,'2000.00');
 const repeat=await send('/finance/payments/allocate',pay('1000.00'),{key});assert.equal(repeat.paymentId,partial.paymentId);assert.equal(await count(a.id),1);
 const over=await api('/finance/payments/allocate','POST',pay('2000.01'),{'Idempotency-Key':randomUUID()});assert.equal(over.status(),409);const rejected=await over.json();assert.equal(await cash(a.id),'9000.00');
 const final=await send('/finance/payments/allocate',pay('2000.00'));const settled=await json('/finance/obligations/'+ob.id);assert.equal(settled.outstandingAmount,'0.00');assert.equal(settled.settlementStatus,'FULLY_PAID');assert.equal(await cash(a.id),'7000.00');assert.equal(await count(a.id),2);
 await page.goto(new URL('/records?view=payables',origin).href);await page.getByRole('heading',{name:'Supplier Payables',exact:true}).first().waitFor();await page.reload();assert.equal(await cash(a.id),'7000.00');assert.equal(await count(a.id),2);await page.screenshot({path:path.join(out,'payables.png'),fullPage:true});
 return {accountId:a.id,obligationId:ob.id,partialId:partial.paymentId,finalId:final.paymentId,plan,settled,balance:'7000.00',transactions:2,overSettlement:{status:409,response:rejected}};
 });
 await check('C browser double submit, refresh/back/forward, lost acknowledgement and original-key recovery',async()=>{
 const a=await newAccount();const posts=[];page.on('request',r=>{if(r.method()==='POST'&&new URL(r.url()).pathname==='/api/finance/transactions')posts.push({key:r.headers()['idempotency-key'],payload:r.postDataJSON()});});
 async function fill(amount){await page.goto(new URL('/records?add=1',origin).href);const f=page.getByRole('form',{name:'Add record',exact:true});await f.getByLabel(/^Account/).first().selectOption(a.id);await f.getByLabel(/^Event/).selectOption('DIRECT_SALE_RECEIPT');await f.getByLabel(/^Amount/).fill(amount);await f.getByLabel(/^Effective date/).fill(today);await f.getByLabel(/^Description/).fill('S5B replay '+tag);await f.getByLabel('I confirm this is a collected direct sale or a paid operating expense for the selected account.',{exact:true}).check();return f;}
 let f=await fill('25');let response=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/finance/transactions');await f.getByRole('button',{name:'Add record',exact:true}).evaluate(b=>{b.click();b.click();});assert.equal((await response).status(),201);assert.equal(await count(a.id),1);assert.equal(posts.length,1);await page.reload();await page.goto(new URL('/finance/accounts',origin).href);await page.goBack({waitUntil:'commit'});await page.waitForLoadState('networkidle');await page.goForward({waitUntil:'commit'});await page.waitForLoadState('networkidle');assert.equal(await count(a.id),1);
 f=await fill('75');let lostBody;await page.route('**/api/finance/transactions',async route=>{if(route.request().method()!=='POST')return route.continue();const actual=await route.fetch();assert.equal(actual.status(),201);lostBody=await actual.json();await route.abort('failed');},{times:1});await f.getByRole('button',{name:'Add record',exact:true}).click();await f.getByRole('button',{name:'Retry original request',exact:true}).waitFor();assert.equal(await cash(a.id),'10100.00');assert.equal(await count(a.id),2);assert.equal(posts.length,2);assert.equal(await f.getByLabel(/^Amount/).isDisabled(),true);
 response=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/finance/transactions');await f.getByRole('button',{name:'Retry original request',exact:true}).click();const recovered=await response;assert.equal(recovered.status(),201);assert.equal((await recovered.json()).id,lostBody.id);assert.deepEqual(posts[2],posts[1]);assert.equal(await count(a.id),2);assert.equal(await cash(a.id),'10100.00');
 const conflict=await api('/finance/transactions','POST',{...posts[1].payload,amount:'76.00'},{'Idempotency-Key':posts[1].key});assert.equal(conflict.status(),409);assert.equal(await count(a.id),2);return {accountId:a.id,posts,transactionId:lostBody.id,balance:'10100.00',transactions:2,changedPayloadStatus:409,networkFault:'Real route.fetch completed Spring write; only its acknowledgement was aborted. No synthetic success supplied.'};
 });
 await check('D OWNER navigation/mutation and tenant evidence isolation',async()=>{await page.goto(new URL('/finance/accounts',origin).href);await page.getByRole('button',{name:'Add account',exact:true}).waitFor();await switchBusiness(fixture.businessBId);assert.equal((await api('/finance/obligations/'+fixture.obligationId)).status(),404);assert.equal((await api('/finance/accounts/'+fixture.account1Id)).status(),404);await switchBusiness(fixture.businessAId);return {role:'OWNER',foreignAccountStatus:404,foreignObligationStatus:404};});
 evidence.push({name:'D ACCOUNTANT/MANAGER/VIEWER real sessions and server-denied mutation matrix',status:'BLOCKED',reason:'Approved S4B manifest/initializer supplies OWNER only; no other-role credentials. Fixture initializer was not rerun; no RBAC or authentication bypass.'});
 if(process.env.S5B_ONLY?.includes('Read-only')) await check('Read-only completed schedule exposure and bilingual advance labels',async()=>{
 const previous=JSON.parse(readFileSync(path.join(out,'attempt-2.json'),'utf8'));const b=previous.evidence.find(x=>x.name.startsWith('B ')).details;
 const plan=await json('/finance/obligations/'+b.obligationId+'/payment-plan');assert.equal(plan.obligationOutstandingAmount,'0.00');const calendar=await json('/finance/payment-calendar?from='+today+'&to='+today);assert.equal(calendar.items.some(x=>x.obligationId===b.obligationId),false);
 await page.goto(new URL('/records?view=advances',origin).href);await page.getByRole('heading',{name:'Customer & Supplier Advances',exact:true}).waitFor();await page.getByText('Unapplied Balance (PKR)',{exact:true}).waitFor();await page.getByRole('button',{name:'اردو',exact:true}).click();await page.getByRole('heading',{name:'پیشگی ادائیگیاں (ایڈوانس)',exact:true}).waitFor();await page.getByText('غیر منقسم باقی رقم (PKR)',{exact:true}).waitFor();assert.equal(await page.locator('html').getAttribute('dir'),'rtl');await page.getByRole('button',{name:'English',exact:true}).click();return {obligationId:b.obligationId,outstanding:'0.00',remainingCalendarItems:0,planStatus:plan.installments[0].status,labels:'Actual EN/UR heading and unapplied-credit column verified'};
 });
 if(process.env.S5B_ONLY?.includes('Inventory')) await check('Inventory of all isolated S5B accounts and resulting transactions',async()=>{
 const ids=new Set();for(const file of ['attempt-1.json','attempt-2.json','attempt-3.json','attempt-4.json']){const saved=JSON.parse(readFileSync(path.join(out,file),'utf8'));for(const item of saved.created||[])if(item.url==='/finance/accounts')ids.add(item.id);}
 const accounts=[];for(const id of ids){const account=await json('/finance/accounts/'+id),history=await json('/finance/transactions?accountId='+id+'&size=100');accounts.push({id,name:account.accountName,currentBalance:account.currentBalance,transactions:history.items});}const original=JSON.parse(readFileSync(path.join(out,'attempt-1.json'),'utf8'));const advanceId=original.evidence.find(x=>x.name.startsWith('A ')).details.advanceId;const advance=await json('/finance/advances/'+advanceId);return {accounts,advance,financialWrites:false};
 });
 await check('Historical financial fingerprints unchanged and original reference balances preserved',async()=>{assert.deepEqual(await json('/records/monthly'),beforeMonthly);assert.deepEqual(await json('/scores/history'),beforeScores);for(const[id,expected]of[[fixture.account1Id,'105000.00'],[fixture.account2Id,'10000.00'],[fixture.account3Id,'80000.00']])assert.equal(await cash(id),expected);assert.equal((await json('/finance/obligations/'+fixture.obligationId)).outstandingAmount,'20000.00');return {monthlyUnchanged:true,scoresUnchanged:true,seededBalances:['105000.00','10000.00','80000.00'],seededOutstanding:'20000.00'};});
 writeFileSync(path.join(out,'results.json'),JSON.stringify({tag,environment:{frontend:origin.href,backend:'8081',database:'sme_health_s4b',migrationHead:37},evidence,created,mutations,pageErrors,mockedFinancialAPIs:false},null,2));assert.ok(evidence.every(x=>x.status!=='FAIL'),'Failed focused checks; see results.json');
}catch(error){writeFileSync(path.join(out,'fatal.json'),JSON.stringify({error:error.message,mutations,pageErrors},null,2));throw error;}finally{await browser.close();}







