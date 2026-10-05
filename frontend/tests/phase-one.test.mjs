import assert from 'node:assert/strict';
import {after,afterEach,beforeEach,mock,test} from 'node:test';
import {mkdtempSync,readFileSync,writeFileSync,unlinkSync,rmdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import ts from 'typescript';
const dir=mkdtempSync(path.join(tmpdir(),'finsight-secure-contracts-'));
const modules=['client','session','contracts','phase-one','finance','documents','zakat','navigation','team','score-presentation'];
for(const name of modules){const source=readFileSync(fileURLToPath(new URL(`../src/lib/api/${name}.ts`,import.meta.url)),'utf8');writeFileSync(path.join(dir,`${name}.js`),ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText);}
const require=createRequire(import.meta.url);
const client=require(path.join(dir,'client.js'));
const {phaseOneApi}=require(path.join(dir,'phase-one.js'));
const {hasPermission}=require(path.join(dir,'session.js'));
const {financeApi,searchDestination,completenessPercent}=require(path.join(dir,'finance.js'));
const {documentsApi,documentFilePath}=require(path.join(dir,'documents.js'));
const {zakatApi,optionalNumber}=require(path.join(dir,'zakat.js'));
const {routePermission}=require(path.join(dir,'navigation.js'));
const businessId='11111111-1111-4111-8111-111111111111';
const record={month:'2026-09',cashInflow:0,cashOutflow:0,revenue:0,operatingExpenses:0,cashBalanceEom:0,cogs:null,receivablesOutstanding:null,payablesOutstanding:null,inventoryValue:null,loanOutstanding:null,interestExpense:null,financingType:'none'};
let calls=[];
function backend(handler=()=>Response.json({ok:true})) {mock.method(globalThis,'fetch',async(url,options)=>{calls.push({url,options});if(url==='/api/auth/csrf')return Response.json({token:'csrf-test',headerName:'X-XSRF-TOKEN',parameterName:'_csrf'});return handler(url,options);});}
beforeEach(()=>{calls=[];client.invalidatePrivateRequests();client.allowPrivateRequests();});
afterEach(()=>{mock.restoreAll();client.invalidatePrivateRequests();});
after(()=>{for(const name of modules)unlinkSync(path.join(dir,`${name}.js`));rmdirSync(dir);});
test('reads use same-origin credentials and no-store even if caller requests otherwise',async()=>{backend();await client.apiClient('/profile',{credentials:'omit',cache:'force-cache'});assert.equal(calls[0].url,'/api/profile');assert.equal(calls[0].options.credentials,'same-origin');assert.equal(calls[0].options.cache,'no-store');});
test('unsafe calls bootstrap CSRF, preserve zero/null and strip tenant identifiers',async()=>{backend((url,options)=>{assert.equal(options.headers.get('X-XSRF-TOKEN'),'csrf-test');assert.equal(url,'/api/records/monthly');assert.deepEqual(JSON.parse(options.body),record);return Response.json({...record,id:'saved',businessId});});const saved=await phaseOneApi.createMonthlyRecord({...record,userId:'old',businessId:'injected',extra:'omit'});assert.equal(saved.businessId,businessId);assert.equal(saved.cogs,null);assert.equal(saved.cashBalanceEom,0);assert.equal(calls[0].url,'/api/auth/csrf');});
test('query routes use POST month body without tenant paths',async()=>{backend(()=>Response.json(record));await phaseOneApi.getMonthlyRecords();await phaseOneApi.getMonthlyRecord('2026-09');await financeApi.insights('2026-09');assert.deepEqual(calls.filter(c=>!c.url.endsWith('/csrf')).map(c=>c.url),['/api/records/monthly','/api/records/monthly/query','/api/insights/query']);assert.deepEqual(JSON.parse(calls.at(-1).options.body),{month:'2026-09'});});
test('record updates preserve the original requested month',async()=>{backend((url,options)=>{assert.equal(JSON.parse(options.body).month,'2026-09');return Response.json(record);});await phaseOneApi.updateMonthlyRecord('2026-09',{...record,month:'2026-08'});});
test('invalid month fails before an HTTP call',async()=>{backend();assert.throws(()=>phaseOneApi.getMonthlyRecord('2026-13'));assert.equal(calls.length,0);});
test('multipart upload keeps browser boundary and includes CSRF',async()=>{backend((url,options)=>{assert.equal(url,'/api/documents/upload');assert.ok(options.body instanceof FormData);assert.equal(options.body.get('file').name,'invoice.pdf');assert.equal(options.headers.has('Content-Type'),false);assert.equal(options.headers.get('X-XSRF-TOKEN'),'csrf-test');return Response.json({id:businessId});});await documentsApi.upload(new File(['test'],'invoice.pdf',{type:'application/pdf'}));});
test('403 mutations are not replayed and CSRF refreshes only on next action',async()=>{backend(()=>Response.json({error:'access_denied',message:'Forbidden'},{status:403}));await assert.rejects(()=>phaseOneApi.createMonthlyRecord(record),e=>e.status===403);assert.equal(calls.filter(c=>c.url==='/api/records/monthly').length,1);await assert.rejects(()=>phaseOneApi.createMonthlyRecord(record));assert.equal(calls.filter(c=>c.url==='/api/auth/csrf').length,2);});
test('parallel mutations share a single CSRF bootstrap',async()=>{backend();await Promise.all([client.apiClient('/profile/language',{method:'PATCH',body:'{}'}),client.apiClient('/records/monthly/query',{method:'POST',body:'{}'})]);assert.equal(calls.filter(c=>c.url==='/api/auth/csrf').length,1);});
test('session transition blocks business API before the request is issued',async()=>{backend();client.invalidatePrivateRequests();await assert.rejects(()=>phaseOneApi.getMonthlyRecords(),e=>e.name==='AbortError');assert.equal(calls.length,0);});
test('late response from old business is rejected even when fetch ignores abort',async()=>{let finish;backend(()=>new Promise(resolve=>{finish=resolve;}));const request=phaseOneApi.getMonthlyRecords();client.invalidatePrivateRequests();finish(Response.json([record]));await assert.rejects(()=>request,e=>e.name==='AbortError');});
test('expired private request locks out subsequent business requests',async()=>{backend(()=>Response.json({error:'session_invalidated',message:'Sign in again'},{status:401}));await assert.rejects(()=>phaseOneApi.getMonthlyRecords(),e=>e.status===401);await assert.rejects(()=>phaseOneApi.getMonthlyRecords(),e=>e.name==='AbortError');assert.equal(calls.length,1);});
test('error fields and rate limit metadata survive parsing',async()=>{backend(()=>Response.json({message:'Invalid amount',errors:{cashInflow:'Must be positive',ignored:12}},{status:429,headers:{'Retry-After':'30'}}));await assert.rejects(()=>phaseOneApi.getMonthlyRecords(),e=>e.status===429&&e.retryAfter==='30'&&e.fieldErrors.cashInflow==='Must be positive'&&!('ignored' in e.fieldErrors));});
test('network failure never substitutes financial data',async()=>{mock.method(globalThis,'fetch',async()=>{throw new TypeError('offline');});await assert.rejects(()=>financeApi.dashboard(),e=>e.status===0);});
test('unsafe API destinations are rejected before fetch',async()=>{backend();for(const path of ['https://evil.test','//evil.test','/\\evil.test','/profile#secret'])await assert.rejects(()=>client.apiClient(path));assert.equal(calls.length,0);});
test('password reset uses the exempt endpoint and does not persist its token',async()=>{backend();await client.apiClient('/auth/password-reset/confirm',{method:'POST',body:JSON.stringify({token:'one-use',newPassword:'long-password'})});assert.equal(calls.length,1);assert.equal(calls[0].options.headers.has('X-XSRF-TOKEN'),false);});
test('viewer and manager cannot write records, owner and accountant can',()=>{for(const role of ['OWNER','ACCOUNTANT','MANAGER','VIEWER'])assert.equal(hasPermission({role,membershipStatus:'ACTIVE'},'RECORD_CREATE_UPDATE'),['OWNER','ACCOUNTANT'].includes(role));assert.equal(hasPermission({role:'OWNER',membershipStatus:'SUSPENDED'},'RECORD_CREATE_UPDATE'),false);assert.equal(hasPermission(null,'FINANCIAL_DATA_READ'),false);});
test('manager upload and viewer finance access mirror backend role matrix',()=>{assert.equal(hasPermission({role:'MANAGER',membershipStatus:'ACTIVE'},'DOCUMENT_UPLOAD'),true);assert.equal(hasPermission({role:'MANAGER',membershipStatus:'ACTIVE'},'DOCUMENT_CONFIRM'),false);assert.equal(hasPermission({role:'VIEWER',membershipStatus:'ACTIVE'},'DOCUMENT_READ'),false);assert.equal(hasPermission({role:'ACCOUNTANT',membershipStatus:'ACTIVE'},'WHATSAPP_CONFIG_MANAGE'),false);});
test('route permissions keep restricted screens out of navigation',()=>{assert.equal(routePermission('/upload/123'),'DOCUMENT_READ');assert.equal(routePermission('/records/new'),'RECORD_CREATE_UPDATE');assert.equal(routePermission('/sharia-zakat'),'ZAKAT_READ_CALCULATE');});
test('score not found is empty, forbidden and unavailable are errors',async()=>{backend(()=>Response.json({message:'not found'},{status:404}));assert.equal(await financeApi.score('2026-09'),null);});
test('canonical completeness fraction maps to percent without changing score',()=>{assert.equal(completenessPercent(.85),85);assert.equal(completenessPercent(0),0);});
test('search destination ignores server-supplied external links',()=>{assert.equal(searchDestination({type:'transaction',date:'2026-09',href:'javascript:alert(1)'}),'/records/2026-09');assert.equal(searchDestination({type:'transaction',date:'invalid',href:'https://evil.test'}),null);assert.equal(searchDestination({type:'document',href:'//evil.test'}),null);});
test('protected file URLs accept only document UUIDs',()=>{assert.equal(documentFilePath(businessId),`/api/documents/${businessId}/file`);assert.throws(()=>documentFilePath('../auth/logout'));});
test('confirmation transmits exact reviewed fields and never supplies identity',async()=>{const input={targetMonth:'2026-09',confirmedAmount:123,confirmedDate:null,confirmedParty:null,targetClassification:'revenue',cashFlowImpact:'cash_inflow',initialCashBalanceEom:0};backend((url,options)=>{assert.equal(url,`/api/documents/${businessId}/confirm`);assert.deepEqual(JSON.parse(options.body),input);return Response.json({id:businessId});});await documentsApi.confirm(businessId,input);});
test('Zakat blank is unknown and zero remains zero',()=>{assert.equal(optionalNumber(''),null);assert.equal(optionalNumber('0'),0);assert.throws(()=>optionalNumber('not a number'));assert.throws(()=>optionalNumber('-1'));});
test('Zakat monthly preview sends declarations and preserves incomplete result',async()=>{const input={assessment:{haulStatus:'UNKNOWN'},inventory:null,receivables:null,currentPayables:null,principalDueWithin12LunarMonths:null,principalExcludedFromPayables:null,unsupportedCategories:null};backend((url,options)=>{assert.equal(url,'/api/zakat/monthly/preview');assert.deepEqual(JSON.parse(options.body),{month:'2026-09',...input});return Response.json({calculationStatus:'INCOMPLETE',zakatDue:null});});const result=await zakatApi.monthly('2026-09',input);assert.equal(result.zakatDue,null);assert.equal(result.calculationStatus,'INCOMPLETE');});

test('an old 401 response cannot invalidate the newly selected business',async()=>{let finish;backend(()=>new Promise(resolve=>{finish=resolve;}));const request=phaseOneApi.getMonthlyRecords();client.invalidatePrivateRequests();client.allowPrivateRequests();finish(Response.json({message:'old session'},{status:401}));await assert.rejects(()=>request,e=>e.name==='AbortError');mock.restoreAll();backend(()=>Response.json([]));assert.deepEqual(await phaseOneApi.getMonthlyRecords(),[]);});

const {teamApi}=require(path.join(dir,'team.js'));
const {scorePresentation,scoreBand,adviceMatchesScore}=require(path.join(dir,'score-presentation.js'));
const {currentDocumentDraft,parseDocumentData}=require(path.join(dir,'documents.js'));
test('all backend score bands including weak and zero scores are rendered without recalculation',()=>{
 const componentScores={cashflow:12,profitability:34,repayment:null,trend:null,compliance:0};
 for(const [wire,view] of [['Strong','strong'],['Stable','stable'],['Needs Attention','attention'],['At Risk','risk']]) {
  const score={compositeScore:0,band:wire,componentScores,dataCompleteness:.65,weakestComponent:'compliance',explanation:{historyMonthsAvailable:1}};
  const shown=scorePresentation(score);assert.equal(shown.composite_score,0);assert.equal(shown.score_band,view);assert.equal(shown.component_scores,componentScores);assert.equal(shown.component_scores.repayment,null);assert.equal(shown.is_provisional,true);assert.equal(shown.data_completeness,65);
 }
 assert.equal(scoreBand('unexpected'),null);assert.equal(scorePresentation({compositeScore:null,band:'Strong'}),null);assert.equal(scorePresentation({compositeScore:99,band:'unexpected'}),null);
});
test('history uses a tenant-free read and preserves missing months and historical methodology',async()=>{
 const saved=[{month:'2026-09',methodologyVersion:'health-score-v1',explanation:{overallDelta:null}},{month:'2026-07',methodologyVersion:null,explanation:null}];backend((url,options)=>{assert.equal(url,'/api/scores/history');assert.equal(options.method,'GET');assert.equal(options.body,undefined);return Response.json(saved);});assert.deepEqual(await financeApi.scoreHistory(),saved);
});
test('advice is rejected when its tenant, month or source computation does not match score',()=>{
 const score={businessId,month:'2026-09',computedAt:'2026-09-30T00:00:00Z'},advice={businessId,month:score.month,sourceComputedAt:score.computedAt};assert.equal(adviceMatchesScore(advice,score),true);
 for(const delta of [{businessId:'other'},{month:'2026-08'},{sourceComputedAt:'old'}])assert.equal(adviceMatchesScore({...advice,...delta},score),false);
});
test('recommendation status sends only the requested lifecycle state with CSRF',async()=>{
 backend((url,options)=>{assert.equal(url,`/api/recommendations/${businessId}/status`);assert.equal(options.method,'PATCH');assert.deepEqual(JSON.parse(options.body),{status:'DONE'});assert.equal(options.headers.get('X-XSRF-TOKEN'),'csrf-test');return Response.json({id:businessId,status:'DONE'});});assert.equal((await financeApi.updateRecommendationStatus(businessId,'DONE')).status,'DONE');
});
test('team invitations resolve email server-side and updates omit supplied identity',async()=>{
 backend();await teamApi.invite(' person@example.test ','ACCOUNTANT');assert.deepEqual(JSON.parse(calls.at(-1).options.body),{email:'person@example.test',role:'ACCOUNTANT'});
 await teamApi.update(businessId,{role:'MANAGER',status:'SUSPENDED',businessId:'injected',userId:'injected'});assert.deepEqual(JSON.parse(calls.at(-1).options.body),{role:'MANAGER',status:'SUSPENDED'});
 await teamApi.rename(' Shop name ');assert.deepEqual(JSON.parse(calls.at(-1).options.body),{businessName:'Shop name'});assert.equal(calls.at(-1).url,'/api/businesses/active/name');
});
test('invitation acceptance and decline use invitation reference without arbitrary user IDs',async()=>{
 backend(()=>new Response(null,{status:204}));await teamApi.respond(businessId,true);assert.equal(calls.at(-1).url,`/api/invitations/${businessId}/accept`);assert.equal(calls.at(-1).options.body,undefined);await teamApi.respond(businessId,false);assert.equal(calls.at(-1).url,`/api/invitations/${businessId}/decline`);assert.throws(()=>teamApi.remove('../auth/logout'));
});
test('membership and recommendation controls preserve existing role permissions',()=>{
 for(const role of ['OWNER','ACCOUNTANT','MANAGER','VIEWER']) {const business={role,membershipStatus:'ACTIVE'};assert.equal(hasPermission(business,'MEMBERSHIP_MANAGE'),role==='OWNER');assert.equal(hasPermission(business,'BUSINESS_SETTINGS_MANAGE'),role==='OWNER');assert.equal(hasPermission(business,'RECORD_CREATE_UPDATE'),['OWNER','ACCOUNTANT'].includes(role));}
 assert.equal(hasPermission({role:'OWNER',membershipStatus:'INVITED'},'MEMBERSHIP_MANAGE'),false);
});
test('search destinations include saved score months and protected document IDs but ignore arbitrary href',()=>{
 assert.equal(searchDestination({type:'document',id:businessId,href:'https://evil.test'}),`/upload/${businessId}`);assert.equal(searchDestination({type:'document',id:'../../secret'}),null);assert.equal(searchDestination({type:'score',date:'2026-07',href:'javascript:alert(1)'}),'/health/components?month=2026-07');assert.equal(searchDestination({type:'recommendation',date:'2026-09'}),'/health/components?month=2026-09');
});
test('reviewed draft takes precedence while original extraction and machine confidence remain unchanged',()=>{
 const original=JSON.stringify({amount:100,confidence:'low'}),reviewed=JSON.stringify({amount:125,confidence:'low'}),doc={extractedData:original,reviewedData:reviewed};assert.deepEqual(currentDocumentDraft(doc),{amount:125,confidence:'low'});assert.equal(doc.extractedData,original);assert.deepEqual(currentDocumentDraft({...doc,reviewedData:null}),{amount:100,confidence:'low'});assert.deepEqual(parseDocumentData('[]'),{});assert.deepEqual(parseDocumentData('invalid'),{});
});
test('correction history is a protected document-scoped read',async()=>{backend((url,options)=>{assert.equal(url,`/api/documents/${businessId}/corrections`);assert.equal(options.method,'GET');return Response.json([{id:'audit',actorIsCurrentUser:true,changedFields:['amount']}]);});assert.equal((await documentsApi.corrections(businessId))[0].actorIsCurrentUser,true);});
