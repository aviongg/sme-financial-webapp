import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import ts from 'typescript';
const require=createRequire(import.meta.url), root=fileURLToPath(new URL('../src/',import.meta.url)), cache=new Map();
function load(file){
 const resolved=path.resolve(root,file);if(cache.has(resolved))return cache.get(resolved).exports;
 const raw=readFileSync(resolved,'utf8');const m={exports:{}};cache.set(resolved,m);
 const code=ts.transpileModule(raw,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText;
 const imports=name=>{if(name.startsWith('@/')||name.startsWith('.')){let target=name.startsWith('@/')?path.join(root,name.slice(2)):path.resolve(path.dirname(resolved),name);for(const ext of ['.ts','.tsx']){try{return load(target+ext);}catch(e){if(e.code!=='ENOENT')throw e;}}throw Error('Missing test import '+name);}return require(name);};
 new Function('require','module','exports',code)(imports,m,m.exports);return m.exports;
}
const input=load('components/finance/funding/funding-input.ts'),{normalizeAmountInput}=load('lib/utils/amount-input.ts');
const {fundingEn:en,fundingUr:ur}=load('lib/i18n/translations/funding.ts');
const {chartGeometry,FundingTimeline}=load('components/finance/funding/FundingTimeline.tsx'),{FundingResults}=load('components/finance/funding/FundingResults.tsx'),{CashEventEditor}=load('components/finance/funding/CashEventEditor.tsx');
const {createElement:h}=require('react'),{renderToStaticMarkup:render}=require('react-dom/server');
const event={id:'one',direction:'OUT',date:'2026-11-06',description:'Planned payment',amountText:'60,000'};
test('add, edit, clone and remove events preserve original draft and stable identities',()=>{
 const original=[event],edited=input.updateEvent(original,'one',{date:'2026-11-10',amountText:'50000'});assert.equal(original[0].date,'2026-11-06');assert.equal(edited[0].date,'2026-11-10');assert.equal(edited[0].id,'one');
 const clone=input.cloneEvents(edited,()=> 'two');assert.equal(clone[0].id,'two');assert.equal(clone[0].amountText,'50000');assert.equal(input.removeEvent(edited,'one').length,0);assert.equal(edited.length,1);
});
test('horizon dates use inclusive UTC calendar arithmetic, leap days and reject invalid dates',()=>{
 assert.equal(input.endDate('2026-11-06',10),'2026-11-16');assert.equal(input.endDate('2028-02-28',3),'2028-03-02');for(const start of ['2026-02-29','2026-13-01',''])assert.equal(input.endDate(start,30),'');for(const days of [-1,1.5,367])assert.equal(input.endDate('2026-11-06',days),'');
});
test('events outside horizon or missing dates are not silently dropped or assigned today',()=>{
 assert.deepEqual(input.eventErrors(event,'2026-11-06','2026-11-15'),{});
 assert.equal(input.eventErrors({...event,date:'2026-11-16'},'2026-11-06','2026-11-15').date,'outside');assert.equal(input.eventErrors({...event,date:''},'2026-11-06','2026-11-15').date,'validDate');
});
test('exact natural amount normalization, positive event amounts, manual negative opening and capacity after rounding',()=>{
 for(const [raw,expected]of [['1500','1500.00'],['1,500','1500.00'],['۱۵۰۰.۵','1500.50'],['-2500.50','-2500.50']])assert.equal(normalizeAmountInput(raw,'PKR').canonical,expected);
 assert.deepEqual(normalizeAmountInput('1.005','PKR'),{canonical:'1.01',rounded:true});assert.equal(normalizeAmountInput('999999999999.995','PKR'),null);
 for(const amountText of ['0','-1','1e3','1,50',''])assert.equal(input.eventErrors({...event,amountText},'2026-11-06','2026-11-15').amountText,'positive');
});
test('description validation does not allow empty or overlong event descriptions',()=>{
 assert.equal(input.eventErrors({...event,description:'  '},'2026-11-06','2026-11-15').description,'required');assert.equal(input.eventErrors({...event,description:'x'.repeat(201)},'2026-11-06','2026-11-15').description,'tooLong');
});
test('chart uses returned positions with a zero reference and bounded presentation coordinates',()=>{
 const rows=[{date:'2026-11-06',baseline:'-60000.00',alternative:'0.00'},{date:'2026-11-10',baseline:'-40000.00',alternative:'10000.00'},{date:'2026-11-15',baseline:'10000.00',alternative:'-999999999999.99'}];
 const geo=chartGeometry(rows);assert.ok(!geo.baseline.includes('NaN'));assert.ok(!geo.alternative.includes('Infinity'));for(const points of Object.values(geo))for(const pair of points.split(' ')){const [x,y]=pair.split(',').map(Number);assert.ok(x>=45&&x<=855);assert.ok(y>=50&&y<=250);}
 const html=render(h(FundingTimeline,{rows,copy:en}));assert.ok(html.includes('-60,000.00'));assert.ok(html.includes('stroke-dasharray'));assert.ok(html.includes('Daily numerical values'));
});
const baseline={peak:'60000.00',firstDeficit:'2026-11-06',recovery:'2026-11-15',closing:'10000.00',inflows:'70000.00',outflows:'60000.00'};
const result={baseline,alternative:{...baseline,recovery:null},rows:[{date:'2026-11-06',baseline:'-60000.00',alternative:'-60000.00'}],opening:'0.00',source:'MANUAL',asOf:'2026-11-06',evaluatedAt:'2026-11-06T12:00:00Z',fundingDifference:'0.00',closingDifference:'0.00'};
test('controlled result fixture displays backend peak, negative position, recovery and no-recovery distinctly in EN/UR',()=>{
 for(const copy of [en,ur]){const html=render(h(FundingResults,{result,copy}));for(const text of [copy.manual,copy.noRecovery,copy.peak,'60,000.00','2026-11-15','10,000.00'])assert.ok(html.includes(text),text);assert.ok(html.includes('<bdi dir="ltr">'));}
});
test('no deficit is distinct from never recovering after a deficit',()=>{
 const html=render(h(FundingResults,{result:{...result,baseline:{...baseline,peak:'0.00',firstDeficit:null,recovery:null}},copy:en}));assert.ok(html.includes(en.none));assert.ok(html.includes(en.noRecovery));
});
test('editor renders actual labelled controls, exact rounding preview and no mock posting actions',()=>{
 const html=render(h(CashEventEditor,{name:en.baseline,events:[{...event,amountText:'1.005'}],start:'2026-11-06',end:'2026-11-15',copy:en,disabled:false,showErrors:true,onChange(){}}));for(const text of [en.addIn,en.addOut,en.remove,en.rounded,'1.01','fieldset','type="date"','inputMode="decimal"'])assert.ok(html.includes(text),text);assert.equal(html.includes('type="number"'),false);
});
test('all funding labels have English/Urdu coverage and financial provenance distinctions',()=>{
 assert.deepEqual(Object.keys(en).sort(),Object.keys(ur).sort());for(const copy of [en,ur])for(const text of Object.values(copy))assert.ok(text.trim());assert.match(en.notice,/does not post/);assert.match(en.ledgerHelp,/never treated as zero/);
});


const {compareFundingScenarios,InvalidFundingResponse,fundingPayload,parseFundingResponse,fundingError}=load('lib/api/dev-a/funding.ts'),client=load('lib/api/client.ts');
const editor=()=>({assumptions:{startDate:'2026-11-01',horizonDays:14,source:'MANUAL',openingText:'0'},baseline:[{id:'payment',direction:'OUT',date:'2026-11-02',amountText:'60000',description:'Planned payment'}],alternative:[]});
function wireFixture(request){
 const scenario=(name)=>({scenarioName:name,totalPlannedInflows:'70000.00',totalPlannedOutflows:'60000.00',netPlannedMovement:'10000.00',closingProjectedLiquidity:'10000.00',peakFundingRequirement:'60000.00',peakDeficitDate:'2026-11-02',firstDeficitDate:'2026-11-02',recoveryDate:'2026-11-11',deficitDayCount:9,hasDeficit:true,isRecovered:true,timeline:Array.from({length:15},(_,i)=>{const position=i===0?'0.00':i<5?'-60000.00':i<10?'-40000.00':'10000.00';return {date:input.endDate('2026-11-01',i),openingPosition:'0.00',plannedInflows:'0.00',plannedOutflows:'0.00',netPlannedMovement:'0.00',closingPosition:position,cumulativeProjectedPosition:position,deficit:position.startsWith('-')?position.slice(1):'0.00',isShortfall:position.startsWith('-'),eventsOnDay:[]};})});
 const b=scenario(request.baselineScenario.scenarioName),a=scenario(request.alternativeScenario.scenarioName);
 return {currency:'PKR',asOfDate:'2026-11-01',horizonStart:'2026-11-01',horizonEnd:'2026-11-15',horizonDays:14,openingLiquidity:{source:'MANUAL_ASSUMPTION',amount:'0.00',note:'Controlled fixture'},baseline:b,alternative:a,comparison:{baselineScenarioName:b.scenarioName,alternativeScenarioName:a.scenarioName,peakFundingDifference:'0.00',closingLiquidityDifference:'0.00',totalInflowsDifference:'0.00',totalOutflowsDifference:'0.00',firstDeficitDateChangeDays:0,recoveryDateChangeDays:0,dailyDifferences:b.timeline.map(d=>({date:d.date,baselineClosingPosition:d.closingPosition,alternativeClosingPosition:d.closingPosition,positionDifference:'0.00',baselineDeficit:d.deficit,alternativeDeficit:d.deficit,deficitDifference:'0.00'}))},provenance:{openingLiquiditySource:'MANUAL_ASSUMPTION',planningMode:'HYPOTHETICAL_SIMULATION',calculatedAt:'2026-11-10T12:00:00Z',calculationEngine:'funding-scenario-v1'},warnings:[],limitations:[]};
}
test('request allowlist normalizes exact amounts without guessing recognition categories or tenant IDs',()=>{
 const data=fundingPayload({...editor(),businessId:'injected'});assert.equal(data.baselineScenario.events[0].amount,'60000.00');assert.equal(data.baselineScenario.events[0].direction,'OUTFLOW');assert.equal(data.openingLiquidity.manualAmount,'0.00');assert.equal('category'in data.baselineScenario.events[0],false);assert.equal('businessId'in data,false);assert.deepEqual(data.alternativeScenario.events,[]);
});
test('source, horizon, negative manual opening, duplicate identity and terminal horizon date are validated',()=>{
 for(const horizonDays of [6,91,1.5])assert.throws(()=>fundingPayload({...editor(),assumptions:{...editor().assumptions,horizonDays}}));
 assert.throws(()=>fundingPayload({...editor(),assumptions:{...editor().assumptions,openingText:'-1'}}));assert.throws(()=>fundingPayload({...editor(),baseline:[editor().baseline[0],editor().baseline[0]]}));
 assert.doesNotThrow(()=>fundingPayload({...editor(),baseline:[{...editor().baseline[0],date:'2026-11-15'}]}));
 const ledger=fundingPayload({...editor(),assumptions:{...editor().assumptions,source:'LEDGER'}});assert.deepEqual(ledger.openingLiquidity,{source:'LEDGER_CASH'});
});
test('actual producer DTO maps exact negative positions, recovery and comparison without defaults',()=>{
 const q=fundingPayload(editor()),r=parseFundingResponse(wireFixture(q),q);assert.equal(r.baseline.peak,'60000.00');assert.equal(r.baseline.recovery,'2026-11-11');assert.equal(r.rows.length,15);assert.equal(r.rows[1].baseline,'-60000.00');
});
test('missing/invalid money, wrong context, incomplete timeline and invented aliases fail closed',()=>{
 const q=fundingPayload(editor());for(const change of [r=>delete r.baseline.peakFundingRequirement,r=>r.openingLiquidity.amount=null,r=>r.currency='USD',r=>r.horizonDays=30,r=>r.baseline.timeline.pop(),r=>r.comparison.dailyDifferences[0].baselineClosingPosition='1.00',r=>r.provenance.openingLiquiditySource='POSTED_ACTUAL',r=>r.baseline.closingProjectedLiquidity='1e3',r=>r.baseline.totalPlannedInflows='-0.00']){const r=wireFixture(q);change(r);assert.throws(()=>parseFundingResponse(r,q),InvalidFundingResponse);}
});
test('actual adapter uses existing cookie/CSRF transport, one POST and no fallback on 403',async()=>{
 const original=globalThis.fetch;let count=0;client.invalidatePrivateRequests();client.allowPrivateRequests();
 globalThis.fetch=async(url,options)=>{if(String(url).endsWith('/auth/csrf'))return Response.json({headerName:'X-XSRF-TOKEN',token:'test',parameterName:'_csrf'});count++;assert.equal(options.credentials,'same-origin');assert.equal(new Headers(options.headers).get('X-XSRF-TOKEN'),'test');return Response.json({message:'Denied'},{status:403});};
 try{await assert.rejects(compareFundingScenarios(editor()),e=>e.status===403);assert.equal(count,1);}finally{globalThis.fetch=original;client.invalidatePrivateRequests();}
});
test('abort propagates and a successful obsolete response is discarded',async()=>{
 const original=globalThis.fetch,c=new AbortController();client.invalidatePrivateRequests();client.allowPrivateRequests();let reached=false;
 globalThis.fetch=async(url,options)=>{if(String(url).endsWith('/auth/csrf'))return Response.json({headerName:'X-XSRF-TOKEN',token:'test',parameterName:'_csrf'});reached=true;c.abort();assert.equal(options.signal.aborted,true);return Response.json(wireFixture(fundingPayload(editor())));};
 try{await assert.rejects(compareFundingScenarios(editor(),c.signal),e=>e.name==='AbortError');assert.equal(reached,true);}finally{globalThis.fetch=original;client.invalidatePrivateRequests();}
});
test('financial errors distinguish permissions, session and missing posted-cash evidence',()=>{
 assert.equal(fundingError(new client.ApiError(403,'Denied')),'forbidden');assert.equal(fundingError(new client.ApiError(401,'Expired')),'expired');assert.equal(fundingError(new client.ApiError(400,'No cash',{'openingLiquidity.source':'Unavailable'})),'unavailableLedger');assert.equal(fundingError(new InvalidFundingResponse()),'malformed');
});
