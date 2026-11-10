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

const finance=load('lib/api/dashboard-finance.ts'),{dashboardFinanceEn:en,dashboardFinanceUr:ur}=load('lib/i18n/translations/dashboard-finance.ts');
const businessId='11111111-1111-4111-8111-111111111111';
const envelope=()=>({businessId,asOfDate:'2026-11-10',status:'AVAILABLE',completeness:'COMPLETE',warnings:[],data:{currencySummaries:[{currency:'PKR',currencyDecimals:2,currentBalance:'105000.00',accountCount:1},{currency:'BHD',currencyDecimals:3,currentBalance:'1250.375',accountCount:1}]}});
test('cash stays separated by currency and preserves exact strings without an invented total',()=>{
 const r=finance.parseCashEvidence(envelope(),businessId);assert.equal(r.data[0].currentBalance,'105000.00');assert.equal(r.data[1].currentBalance,'1250.375');assert.equal('combinedTotal'in r,false);
});
test('cash rejects wrong business, corrupt precision/currency metadata and duplicate currencies',()=>{
 for(const alter of [e=>e.businessId='22222222-2222-4222-8222-222222222222',e=>e.data.currencySummaries[1].currencyDecimals=2,e=>e.data.currencySummaries[0].currentBalance='1e3',e=>e.data.currencySummaries[0].currentBalance='-0.00',e=>e.data.currencySummaries.push(e.data.currencySummaries[0])]){const e=envelope();alter(e);assert.throws(()=>finance.parseCashEvidence(e,businessId));}
});
test('unavailable and empty evidence remain distinct from legitimate zero cash',()=>{
 const e=envelope();e.status='UNAVAILABLE';e.data=null;assert.equal(finance.parseCashEvidence(e,businessId).data,null);assert.equal(finance.exactDisplayAmount(null),null);assert.equal(finance.exactDisplayAmount('0.00'),'0.00');assert.equal(finance.exactDisplayAmount('999999999999.99'),'999999999999.99');
});
test('missing or unknown recognized costs never become zero profit; actual zero is preserved',()=>{
 for(const data of [{profitabilityStatus:'UNAVAILABLE',actualGrossProfit:100},{profitabilityStatus:'AVAILABLE',actualGrossProfit:null},{profitabilityStatus:'AVAILABLE',actualGrossProfit:undefined}])assert.equal(finance.projectProfitability(data),null);
 assert.equal(finance.projectProfitability({profitabilityStatus:'AVAILABLE',actualGrossProfit:0}),'0.00');assert.equal(finance.projectProfitability({profitabilityStatus:'AVAILABLE',actualGrossProfit:'-1234.50'}),'-1234.50');
});
test('bilingual financial labels distinguish actuals, forecasts, commitments and historical health',()=>{
 assert.deepEqual(Object.keys(en).sort(),Object.keys(ur).sort());for(const copy of [en,ur])for(const text of Object.values(copy))assert.ok(text.trim());assert.match(en.notProfit,/not accounting profit/);assert.match(en.healthHelp,/historical monthly/);assert.match(en.forecast,/not posted/);
});
test('F13 uses the existing financial-summary widget and preserves preference/registry authority',()=>{
 const render=readFileSync(path.join(root,'components/workspace/WidgetRenderer.tsx'),'utf8');assert.match(render,/case 'financial-summary':.*FinancialOverview/);assert.match(render,/RecordedTrends records/);
 const overview=readFileSync(path.join(root,'components/workspace/FinancialOverview.tsx'),'utf8');assert.equal(overview.includes('/workspace/preferences'),false);assert.equal(overview.includes('localStorage'),false);assert.match(overview,/key=\{key\}/);assert.match(overview,/return\(\)=>c.abort/);assert.match(overview,/state.key===key/);
});

test('live financial-summary remains visible when a business has no historical monthly score',()=>{
 const source=readFileSync(path.join(root,'components/workspace/DashboardWorkspace.tsx'),'utf8');assert.match(source,/!widgets.some\(widget => widget.key === 'financial-summary'\)/);
});

test('partial ledger evidence is retained as partial rather than promoted to complete',()=>{
 const e=envelope();e.completeness='PARTIAL';e.warnings=['One category unavailable'];const parsed=finance.parseCashEvidence(e,businessId);assert.equal(parsed.completeness,'PARTIAL');assert.equal(parsed.warnings.length,1);
});
