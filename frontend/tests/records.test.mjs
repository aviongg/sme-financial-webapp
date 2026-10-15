import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import ts from 'typescript';
const directory = mkdtempSync(path.join(tmpdir(), 'finsight-records-'));
const names = ['lib/api/client','lib/utils/decimal-display','lib/utils/amount-input','lib/api/dev-a/account-contracts','lib/api/dev-a/account-create-contracts','lib/api/dev-a/transaction-contracts','lib/api/dev-a/transactions','lib/api/dev-a/transaction-totals','components/finance/transactions/transaction-input','components/finance/transactions/transaction-submission','components/records/record-period','components/records/record-query','components/records/records-copy'];
for (const name of names) {
 const target = path.join(directory,name+'.js'); mkdirSync(path.dirname(target),{recursive:true});
 writeFileSync(target,ts.transpileModule(readFileSync(new URL('../src/'+name+'.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText);
}
after(()=>rmSync(directory,{recursive:true}));
const require = createRequire(import.meta.url), load = name => require(path.join(directory,name+'.js'));
const {normalizeAmountInput: normalize} = load('lib/utils/amount-input');
const {periodDates,safeRecordsReturn} = load('components/records/record-period');
const {parsePeriodTotals,summaryQuery,loadPeriodTotals,formatPeriodAmount} = load('lib/api/dev-a/transaction-totals');
const client = load('lib/api/client');
beforeEach(()=>{client.invalidatePrivateRequests();client.allowPrivateRequests();});
afterEach(()=>{mock.restoreAll();client.invalidatePrivateRequests();});
test('ordinary integer/grouped/fractional and Arabic/Urdu input becomes strict Western wire money',()=>{
 for(const raw of ['1500','1,500','1500.0','1500.00','۱٬۵۰۰'].slice(0,4)) assert.deepEqual(normalize(raw,'PKR'),{canonical:'1500.00',rounded:false});
 for(const raw of ['1500.5','۱,۵۰۰.۵','١٥٠٠.٥']) assert.deepEqual(normalize(raw,'PKR'),{canonical:'1500.50',rounded:false});
 assert.deepEqual(normalize(' 1500 ','PKR'),{canonical:'1500.00',rounded:false});
});
test('exact HALF_UP uses no floating point, rounds ties away from zero and preserves editing input',()=>{
 const cases=[['1.005','PKR','1.01'],['-1.005','PKR','-1.01'],['99.995','PKR','100.00'],['0.004','PKR','0.00'],['1250.3755','BHD','1250.376'],['1.5','JPY','2'],['-1.5','JPY','-2']];
 for(const [raw,currency,canonical] of cases) assert.deepEqual(normalize(raw,currency),{canonical,rounded:true});
 assert.deepEqual(normalize('1.0000','PKR'),{canonical:'1.00',rounded:false});
 const raw='1.005'; normalize(raw,'PKR'); assert.equal(raw,'1.005');
});
test('rounding checks capacity after carry at each supported scale',()=>{
 for(const [currency,raw] of [['JPY','999999999999.5'],['PKR','999999999999.995'],['BHD','999999999999.9995']]) assert.equal(normalize(raw,currency),null);
 assert.deepEqual(normalize('999999999999.994','PKR'),{canonical:'999999999999.99',rounded:true});
 assert.deepEqual(normalize('-999999999999.9994','BHD'),{canonical:'-999999999999.999',rounded:true});
 assert.equal(normalize('1000000000000','JPY'),null);
});
test('grouping is validated, not stripped; ambiguous, unsupported and exponent forms reject',()=>{
 for(const raw of ['','1,50','12,34,567','1234,567','1,,500','1.500,50','1 500','1e2','+1','01','00.50','1.','1,500,','۱٬۵۰۰','١٫٥','NaN','Infinity']) assert.equal(normalize(raw,'PKR'),null,raw);
 assert.equal(normalize('1','UNKNOWN'),null);
 assert.deepEqual(normalize('-0.000','PKR'),{canonical:'0.00',rounded:false});
});
test('Karachi date presets define Monday start and stop at today across UTC boundary',()=>{
 const now=new Date('2026-10-14T19:00:00Z');
 assert.deepEqual(periodDates('today',now),{startDate:'2026-10-15',endDate:'2026-10-15'});
 assert.deepEqual(periodDates('week',now),{startDate:'2026-10-12',endDate:'2026-10-15'});
 assert.deepEqual(periodDates('month',now),{startDate:'2026-10-01',endDate:'2026-10-15'});
 assert.deepEqual(periodDates('week',new Date('2026-10-11T12:00:00Z')),{startDate:'2026-10-05',endDate:'2026-10-11'});
 assert.deepEqual(periodDates('custom',now),{});
});
test('document return accepts only Records context and rejects external/control-character destinations',()=>{
 assert.equal(safeRecordsReturn('/records?period=week&page=2'),'/records?period=week&page=2');
 for(const raw of ['https://example.com','//example.com','/records/new','/records\\evil','/records?x=1\n',null]) assert.equal(safeRecordsReturn(raw),'/records');
});
const totals=()=>({currency:'PKR',startDate:null,endDate:null,accountId:null,eventType:null,transactionCount:123,
 moneyReceived:'10000000000000.00',moneyPaid:'100.00',netCashMovement:'-5.00',observationAt:'2026-10-14T12:00:00Z'});
test('summary consumes authoritative complete-dataset strings without page aggregation or account capacity clamp',()=>{
 assert.deepEqual(summaryQuery({page:4,size:10,startDate:'2026-10-01'}),{startDate:'2026-10-01'});
 assert.deepEqual(parsePeriodTotals(totals(),{}),totals());
 assert.equal(formatPeriodAmount(totals().moneyReceived),'10,000,000,000,000.00');
 assert.equal(formatPeriodAmount('-1500.50'),'- 1,500.50');
});
test('summary requires every field, filter echo, canonical sign/scale and genuine null semantics',()=>{
 for(const key of Object.keys(totals())){const raw=totals();delete raw[key];assert.throws(()=>parsePeriodTotals(raw,{}));}
 for(const patch of [{moneyPaid:'-1.00'},{moneyReceived:'1e2'},{netCashMovement:'-0.00'},{moneyReceived:0},{currency:'USD'},{transactionCount:-1},{observationAt:'2026-10-14T12:00:00.1Z'},{startDate:'2026-10-01'}]) assert.throws(()=>parsePeriodTotals({...totals(),...patch},{}));
});
test('real summary adapter drops pagination/tenant, propagates abort and never falls back on errors',async()=>{
 const calls=[];let release;
 mock.method(globalThis,'fetch',(url,options)=>{calls.push({url,options});return new Promise(resolve=>{release=resolve;});});
 const controller=new AbortController();const pending=loadPeriodTotals({page:3,size:10,businessId:'injected'},controller.signal);
 await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(calls[0].url,'/api/finance/transactions/summary'); assert.equal(calls[0].options.cache,'no-store');
 controller.abort();assert.equal(calls[0].options.signal.aborted,true);release(Response.json(totals()));await assert.rejects(pending,error=>error.name==='AbortError');
 mock.restoreAll();let failures=0;mock.method(globalThis,'fetch',async()=>{failures++;return Response.json({message:'Missing endpoint'},{status:404});});
 await assert.rejects(loadPeriodTotals({}),error=>error instanceof client.ApiError&&error.status===404);assert.equal(failures,1);
});
test('normalized operation retries keep the original identity/payload and forbid changed intended submission',async()=>{
 const {TransactionSubmission}=load('components/finance/transactions/transaction-submission');
 const sent=[], submission=new TransactionSubmission(async(payload,key)=>{sent.push({payload,key});if(sent.length===1)throw new client.ApiError(0,'Lost reply');return {id:'controlled'};},()=> 'opaque-controlled-key');
 const raw={selectedAccount:'11111111-1111-4111-8111-111111111111',event:'DIRECT_SALE_RECEIPT',amountText:'1,500.555',dateText:'2026-10-14',descriptionText:'Retained description',confirmed:true};
 const normalized={...raw,amountText:normalize(raw.amountText,'PKR').canonical};
 await assert.rejects(submission.submit(normalized));assert.equal(submission.phase,'uncertain');
 await assert.rejects(submission.submit({...normalized,amountText:'1501.00'}));assert.equal(sent.length,1);
 await submission.submit(normalized);assert.equal(sent[0].payload,sent[1].payload);assert.equal(sent[1].payload.amount,'1500.56');assert.equal(sent[0].key,sent[1].key);assert.equal(raw.amountText,'1,500.555');
});
test('records workspace and OCR destination copy has complete typed English/Urdu coverage',()=>{
 const {recordsEn,recordsUr}=load('components/records/records-copy');assert.deepEqual(Object.keys(recordsEn),Object.keys(recordsUr));for(const value of Object.values(recordsUr))assert.ok(value);
});

test('rapid filter updates merge the latest URL, preserve other workspace filters and reset only the changed page',()=>{
 const {accountSearch,transactionSearch,accountQueryFromURL,transactionQueryFromURL}=load('components/records/record-query');
 let search=new URLSearchParams('view=accounts&moneyaccountType=CASH&moneycurrency=JPY&moneypage=2&period=custom&page=4');
 search=accountSearch(search,q=>({...q,accountType:undefined,page:0}),true);
 search=accountSearch(search,q=>({...q,currency:undefined,page:0}),true);
 assert.equal(accountQueryFromURL(search,true).accountType,undefined);assert.equal(accountQueryFromURL(search,true).currency,undefined);
 assert.equal(search.get('page'),'4');assert.equal(search.get('view'),'accounts');
 search=transactionSearch(search,q=>({...q,startDate:'2026-10-01',page:0}),true,'custom');
 search=transactionSearch(search,q=>({...q,endDate:'2026-10-14',page:0}),true,'custom');
 assert.equal(transactionQueryFromURL(search,true).startDate,'2026-10-01');assert.equal(transactionQueryFromURL(search,true).endDate,'2026-10-14');
 assert.equal(search.get('moneypage'),'0');
});
