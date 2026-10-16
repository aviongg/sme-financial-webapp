import assert from 'node:assert/strict';
import { test, after, beforeEach, afterEach, mock } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';
const directory=mkdtempSync(path.join(tmpdir(),'finsight-drafts-'));after(()=>rmSync(directory,{recursive:true}));
for(const name of ['lib/utils/decimal-display','lib/utils/amount-input','components/records/draft-entry','components/records/draft-copy','components/records/draft-recording','lib/api/client','lib/api/dev-a/account-contracts','lib/api/dev-a/account-create-contracts','lib/api/dev-a/transaction-contracts','lib/api/dev-a/drafts','components/finance/transactions/transaction-input']){
 const target=path.join(directory,name+'.js');mkdirSync(path.dirname(target),{recursive:true});writeFileSync(target,ts.transpileModule(readFileSync(new URL('../src/'+name+'.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText);
}
const require=createRequire(import.meta.url),load=name=>require(path.join(directory,name+'.js'));
const {emptyDraftEntry,missingEntryFields,entryHasChanges,draftAmountPreview}=load('components/records/draft-entry');
const {parseDraft,parseDraftPage,draftPayload,draftEntry,draftsApi}=load('lib/api/dev-a/drafts');
const {reviewedDraftPayload,DraftRecording}=load('components/records/draft-recording');
const {draftEn,draftUr}=load('components/records/draft-copy'),client=load('lib/api/client');
const id='11111111-1111-4111-8111-111111111111',account='22222222-2222-4222-8222-222222222222';
const fixture=()=>({id,accountId:null,eventType:null,effectiveDate:null,amountInput:'1500.',description:null,status:'DRAFT',readyToRecord:false,version:0,recordedTransactionId:null,createdById:id,updatedById:id,createdAt:'2026-10-15T00:00:00Z',updatedAt:'2026-10-15T00:00:00Z'});
const complete=()=>({...fixture(),accountId:account,eventType:'DIRECT_SALE_RECEIPT',effectiveDate:'2026-10-15',amountInput:'۱,۵۰۰.۵۵۵'});
beforeEach(()=>{client.invalidatePrivateRequests();client.allowPrivateRequests();});afterEach(()=>{mock.restoreAll();client.invalidatePrivateRequests();});
test('incomplete fields have no invented defaults; missing and invalid differ',()=>{
 assert.deepEqual(missingEntryFields(emptyDraftEntry()),['selectedAccount','event','amountText','dateText']);
 assert.deepEqual(missingEntryFields({...emptyDraftEntry(),amountText:'1.'}),['selectedAccount','event','dateText']);
});
test('raw unfinished text remains untouched; dirty comparison includes every field',()=>{
 for(const amountText of ['1.','۱,۵۰۰.۵۵۵',' 1500 ','1,50','']){const e={...emptyDraftEntry(),amountText},before={...e};draftAmountPreview(e);assert.deepEqual(e,before);assert.equal(entryHasChanges(e,{...e}),false);}
 const baseline=emptyDraftEntry();for(const key of Object.keys(baseline))assert.equal(entryHasChanges({...baseline,[key]:'changed'},baseline),true);assert.deepEqual(baseline,emptyDraftEntry());
});
test('exact HALF_UP preview and capacity check reuse existing input helper',()=>{
 assert.deepEqual(draftAmountPreview(draftEntry(complete())),{canonical:'1500.56',rounded:true});
 for(const amountText of ['1.','1e2','999999999999.995'])assert.equal(draftAmountPreview({...emptyDraftEntry(),amountText}),null);
});
test('draft validator requires fields, valid metadata, and strict terminal linkage',()=>{
 assert.deepEqual(parseDraft(fixture()),fixture());for(const field of Object.keys(fixture())){const d=fixture();delete d[field];assert.throws(()=>parseDraft(d),field);}
 for(const patch of [{version:-1},{version:1.1},{status:'PAID'},{eventType:'LOAN'},{amountInput:'x'.repeat(101)},{effectiveDate:'2026-02-30'},{recordedTransactionId:id},{createdById:'bad'}])assert.throws(()=>parseDraft({...fixture(),...patch}));
 assert.equal(parseDraft({...fixture(),status:'RECORDED',recordedTransactionId:account}).status,'RECORDED');assert.throws(()=>parseDraft({...fixture(),status:'RECORDED'}));
});
test('actual items DTO has deterministic pagination count and status checks',()=>{
 const p={items:[fixture()],page:0,size:20,totalElements:1,totalPages:1};assert.deepEqual(parseDraftPage(p,0,'DRAFT'),p);assert.throws(()=>parseDraftPage({...p,totalPages:2},0,'DRAFT'));assert.throws(()=>parseDraftPage(p,0,'RECORDED'));
 assert.deepEqual(parseDraftPage({items:[],page:2,size:20,totalElements:1,totalPages:1},2,'DRAFT').items,[]);
});
test('allowlisted payload preserves raw text without normalization or zero defaults',()=>{
 assert.deepEqual(draftPayload({...emptyDraftEntry(),amountText:' ۱۵۰۰. ',extra:'secret'}),{accountId:null,eventType:null,effectiveDate:null,amountInput:' ۱۵۰۰. ',description:null});assert.throws(()=>draftPayload({...emptyDraftEntry(),amountText:'x'.repeat(101)}));
});
test('recording binds exact saved version; unsaved fields and terminal drafts block recording',()=>{
 const d=complete(),e=draftEntry(d);assert.deepEqual(reviewedDraftPayload(d,e),{version:0,confirmedAmount:'1500.56'});
 for(const key of Object.keys(e))assert.throws(()=>reviewedDraftPayload(d,{...e,[key]:e[key]+'changed'}));
 for(const status of ['RECORDED','DISCARDED'])assert.throws(()=>reviewedDraftPayload({...d,status},e));
});
test('save preserves input through real transport, propagates abort, and has no error fallback',async()=>{
 const controller=new AbortController();let sent;mock.method(globalThis,'fetch',async(url,options)=>{if(url==='/api/auth/csrf')return new Response(JSON.stringify({token:'csrf',headerName:'X-XSRF-TOKEN',parameterName:'_csrf'}));sent=options;return new Response(JSON.stringify(fixture()),{status:201});});
 assert.equal((await draftsApi.save({...emptyDraftEntry(),amountText:'1500.'},null,'key',controller.signal)).amountInput,'1500.');assert.equal(JSON.parse(sent.body).amountInput,'1500.');assert.equal(sent.signal instanceof AbortSignal,true);
 mock.restoreAll();let finish;mock.method(globalThis,'fetch',async(url,options)=>{sent=options;return new Promise(resolve=>finish=resolve);});const inFlight=draftsApi.detail(id,controller.signal);await new Promise(resolve=>setTimeout(resolve,0));controller.abort();assert.equal(sent.signal.aborted,true);finish(new Response(JSON.stringify(fixture())));await assert.rejects(inFlight);
 mock.restoreAll();mock.method(globalThis,'fetch',async()=>new Response(JSON.stringify({message:'Denied'}),{status:403}));await assert.rejects(draftsApi.list(),error=>error.status===403);
 mock.restoreAll();mock.method(globalThis,'fetch',async()=>{throw new TypeError('offline');});await assert.rejects(draftsApi.list());
});
test('double recording blocked; explicit uncertain retry preserves key and payload',async()=>{
 let finish;const operation=new DraftRecording(()=>new Promise(resolve=>finish=resolve));const pending=operation.submit(complete(),draftEntry(complete()));await assert.rejects(operation.submit(complete(),draftEntry(complete())));finish('recorded');assert.equal(await pending,'recorded');
 const calls=[];const uncertain=new DraftRecording(async(...args)=>{calls.push(args);if(calls.length===1)throw new TypeError('lost');return 'recorded';},()=> 'original-key');
 await assert.rejects(uncertain.submit(complete(),draftEntry(complete())));assert.equal(uncertain.phase,'uncertain');assert.equal(calls.length,1);await assert.rejects(uncertain.submit({...complete(),version:1},draftEntry(complete())));assert.equal(calls.length,1);assert.equal(await uncertain.submit(complete(),draftEntry(complete())),'recorded');assert.equal(calls[0][2],calls[1][2]);assert.deepEqual(calls[0][1],calls[1][1]);
});
test('business/session invalidation aborts request, rejects late success and retry',async()=>{
 let done,signal;const operation=new DraftRecording((id,payload,key,s)=>{signal=s;return new Promise(resolve=>done=resolve);});const pending=operation.submit(complete(),draftEntry(complete()));operation.invalidate();assert.equal(signal.aborted,true);done('stale');await assert.rejects(pending);assert.equal(operation.phase,'invalidated');await assert.rejects(operation.submit(complete(),draftEntry(complete())));
});
test('conflict/denial lock mutation and never replay automatically',async()=>{
 for(const [status,phase] of [[409,'conflict'],[403,'denied'],[401,'denied'],[400,'idle']]){const operation=new DraftRecording(async()=>{throw new client.ApiError(status,'failure');});await assert.rejects(operation.submit(complete(),draftEntry(complete())));assert.equal(operation.phase,phase);}
});
test('recorded success stays complete even when subsequent refresh fails',async()=>{
 const operation=new DraftRecording(async()=>({transaction:{id}}));assert.equal((await operation.submit(complete(),draftEntry(complete()))).transaction.id,id);await assert.rejects(Promise.reject(new Error('refresh failed')));assert.equal(operation.phase,'complete');await assert.rejects(operation.submit(complete(),draftEntry(complete())));
});
test('typed EN/UR strings distinguish saved and recorded',()=>{assert.deepEqual(Object.keys(draftEn).sort(),Object.keys(draftUr).sort());for(const c of [draftEn,draftUr]){assert.notEqual(c.saved,c.recorded);assert.ok(Object.values(c).every(Boolean));}});

test('a successful HTTP save with altered raw text is rejected rather than silently replacing entered text',async()=>{
 mock.method(globalThis,'fetch',async url=>url==='/api/auth/csrf'?new Response(JSON.stringify({token:'csrf',headerName:'X-XSRF-TOKEN'})):new Response(JSON.stringify({...fixture(),amountInput:'1500.'}),{status:201}));
 await assert.rejects(draftsApi.save({...emptyDraftEntry(),amountText:' 1500. '},null,'key'),error=>error.constructor.name==='InvalidDraftResponse');
});
