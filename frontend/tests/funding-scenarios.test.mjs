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
 assert.equal(input.endDate('2026-11-06',10),'2026-11-15');assert.equal(input.endDate('2028-02-28',3),'2028-03-01');for(const start of ['2026-02-29','2026-13-01',''])assert.equal(input.endDate(start,30),'');for(const days of [0,-1,1.5,367])assert.equal(input.endDate('2026-11-06',days),'');
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

const { compareFundingScenarios, InvalidFundingResponse } = load('lib/api/dev-a/funding.ts');
const client = load('lib/api/client.ts');

test('compareFundingScenarios correctly formats wire payload and decodes Section 6 fixture', async () => {
  client.invalidatePrivateRequests();
  client.allowPrivateRequests();
  const originalFetch = globalThis.fetch;
  let capturedUrl = '';
  let capturedBody = null;

  globalThis.fetch = async (url, options) => {
    const urlStr = url.toString();
    if (urlStr.includes('/api/auth/csrf')) {
      return new Response(JSON.stringify({ token: 'test-csrf-token', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    capturedUrl = urlStr;
    capturedBody = JSON.parse(options.body);

    const wireResponse = {
      currency: 'PKR',
      asOfDate: '2026-11-01',
      horizonStart: '2026-11-01',
      horizonEnd: '2026-12-01',
      horizonDays: 30,
      openingLiquidity: {
        source: 'MANUAL_ASSUMPTION',
        amount: '0.00',
        note: 'User assumption'
      },
      baseline: {
        scenarioName: 'Baseline Plan',
        totalPlannedInflows: '70000.00',
        totalPlannedOutflows: '60000.00',
        netPlannedMovement: '10000.00',
        closingProjectedLiquidity: '10000.00',
        peakFundingRequirement: '60000.00',
        peakDeficitDate: '2026-11-02',
        firstDeficitDate: '2026-11-02',
        recoveryDate: '2026-11-11',
        deficitDayCount: 9,
        hasDeficit: true,
        isRecovered: true,
        timeline: [
          { date: '2026-11-01', closingLiquidity: '0.00' },
          { date: '2026-11-02', closingLiquidity: '-60000.00' },
          { date: '2026-11-06', closingLiquidity: '-40000.00' },
          { date: '2026-11-11', closingLiquidity: '10000.00' }
        ]
      },
      alternative: {
        scenarioName: 'Alternative Plan',
        totalPlannedInflows: '70000.00',
        totalPlannedOutflows: '30000.00',
        netPlannedMovement: '40000.00',
        closingProjectedLiquidity: '40000.00',
        peakFundingRequirement: '30000.00',
        peakDeficitDate: '2026-11-02',
        firstDeficitDate: '2026-11-02',
        recoveryDate: '2026-11-11',
        deficitDayCount: 9,
        hasDeficit: true,
        isRecovered: true,
        timeline: [
          { date: '2026-11-01', closingLiquidity: '0.00' },
          { date: '2026-11-02', closingLiquidity: '-30000.00' },
          { date: '2026-11-06', closingLiquidity: '-10000.00' },
          { date: '2026-11-11', closingLiquidity: '40000.00' }
        ]
      },
      comparison: {
        baselineScenarioName: 'Baseline Plan',
        alternativeScenarioName: 'Alternative Plan',
        peakFundingDifference: '-30000.00',
        closingLiquidityDifference: '30000.00',
        totalInflowsDifference: '0.00',
        totalOutflowsDifference: '-30000.00',
        firstDeficitDateChangeDays: 0,
        recoveryDateChangeDays: 0,
        dailyDifferences: [
          { date: '2026-11-01', baselineClosing: '0.00', alternativeClosing: '0.00' },
          { date: '2026-11-02', baselineClosing: '-60000.00', alternativeClosing: '-30000.00' },
          { date: '2026-11-06', baselineClosing: '-40000.00', alternativeClosing: '-10000.00' },
          { date: '2026-11-11', baselineClosing: '10000.00', alternativeClosing: '40000.00' }
        ]
      },
      provenance: {
        openingLiquiditySource: 'MANUAL_ASSUMPTION',
        planningMode: 'HYPOTHETICAL_SIMULATION',
        calculatedAt: '2026-11-07T12:00:00Z',
        calculationEngine: 'funding-scenario-v1'
      },
      warnings: [],
      limitations: []
    };

    return new Response(JSON.stringify(wireResponse), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    });
  };

  try {
    const inputData = {
      assumptions: {
        startDate: '2026-11-01',
        horizonDays: 30,
        source: 'MANUAL',
        openingText: '0.00'
      },
      baseline: [
        { id: 'b1', direction: 'OUT', date: '2026-11-02', amountText: '60000.00', description: 'Materials' },
        { id: 'b2', direction: 'IN', date: '2026-11-06', amountText: '20000.00', description: 'Receivable' },
        { id: 'b3', direction: 'IN', date: '2026-11-11', amountText: '50000.00', description: 'Advance' }
      ],
      alternative: [
        { id: 'a1', direction: 'OUT', date: '2026-11-02', amountText: '30000.00', description: 'Phased Materials' }
      ]
    };

    const view = await compareFundingScenarios(inputData);

    assert.ok(capturedUrl.includes('/finance/funding/scenarios/compare'));
    assert.equal(capturedBody.openingLiquidity.source, 'MANUAL_ASSUMPTION');
    assert.equal(capturedBody.openingLiquidity.manualAmount, '0.00');
    assert.equal(capturedBody.baselineScenario.events.length, 3);
    assert.equal(capturedBody.baselineScenario.events[0].direction, 'OUTFLOW');
    assert.equal(capturedBody.alternativeScenario.events.length, 1);

    // Verify Section 6 canonical numbers in decoded view
    assert.equal(view.baseline.peak, '60000.00');
    assert.equal(view.baseline.firstDeficit, '2026-11-02');
    assert.equal(view.baseline.recovery, '2026-11-11');
    assert.equal(view.baseline.closing, '10000.00');
    assert.equal(view.alternative.peak, '30000.00');
    assert.equal(view.fundingDifference, '-30000.00');
    assert.equal(view.closingDifference, '30000.00');
    assert.equal(view.rows.length, 4);
    assert.equal(view.rows[1].baseline, '-60000.00');
    assert.equal(view.rows[1].alternative, '-30000.00');
  } finally {
    globalThis.fetch = originalFetch;
    client.invalidatePrivateRequests();
  }
});
test('compareFundingScenarios throws InvalidFundingResponse on malformed server response', async () => {
  client.invalidatePrivateRequests();
  client.allowPrivateRequests();
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    if (url.toString().includes('/api/auth/csrf')) {
      return new Response(JSON.stringify({ token: 'test-csrf-token', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    }
    return new Response(JSON.stringify({ unexpected: 'payload' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    });
  };

  try {
    await assert.rejects(
      async () => {
        await compareFundingScenarios({
          assumptions: { startDate: '2026-11-01', horizonDays: 30, source: 'LEDGER', openingText: '' },
          baseline: [],
          alternative: []
        });
      },
      (err) => err instanceof InvalidFundingResponse || err.name === 'InvalidFundingResponse'
    );
  } finally {
    globalThis.fetch = originalFetch;
    client.invalidatePrivateRequests();
  }
});
