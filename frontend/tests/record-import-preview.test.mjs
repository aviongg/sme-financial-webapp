import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createRequire } from 'node:module';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import path from 'node:path';
import { tmpdir } from 'node:os';
import ts from 'typescript';

const temporary=mkdtempSync(path.join(tmpdir(),'finsight-import-preview-'));
after(()=>rmSync(temporary,{recursive:true}));
const require=createRequire(import.meta.url);
const names=['lib/imports/delimited','lib/imports/preview','lib/imports/lifetime','lib/imports/accounts','lib/utils/amount-input','lib/utils/decimal-display','components/records/import/import-copy'];
for(const name of names){const file=path.join(temporary,name+'.js');mkdirSync(path.dirname(file),{recursive:true});const source=readFileSync(new URL('../src/'+name+'.ts',import.meta.url),'utf8');const result=ts.transpileModule(source,{reportDiagnostics:true,compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}});assert.equal(result.diagnostics?.filter(d=>d.category===ts.DiagnosticCategory.Error).length,0,name);writeFileSync(file,result.outputText);}
const load=name=>require(path.join(temporary,name+'.js'));
const {parseDelimited:parse,IMPORT_LIMITS:L,ImportParseError,validateCsvFile,decodeCsv}=load('lib/imports/delimited');
const {reviewImport,emptyMapping,parseImportDate,previewCounts,excludeRows,mappingIssues,importToday}=load('lib/imports/preview');
const {PreviewJob}=load('lib/imports/lifetime');
const {loadImportAccounts}=load('lib/imports/accounts');
const {importEn,importUr}=load('components/records/import/import-copy');
const account={id:'synthetic-account',accountName:'Synthetic bank',currency:'PKR',currencyDecimals:2,status:'ACTIVE',balanceStatus:'AVAILABLE',openingDate:'2020-01-01'};
const options=(patch={})=>({headings:true,mapping:{date:0,description:1,amount:2,direction:3,moneyIn:null,moneyOut:null,reference:4},mode:'direction',dateFormat:'YYYY-MM-DD',currency:'PKR',account,inToken:'IN',outToken:'OUT',classification:'',overrides:{},excluded:new Set(),today:'2026-10-17',...patch});
const table=(rows='2026-10-01,Sale,1500.5,IN,000123')=>parse('Date,Description,Amount,Direction,Reference\n'+rows,',');
const issues=row=>row.issues.map(i=>i.code);
const failure=(text,code,delimiter=',')=>assert.throws(()=>parse(text,delimiter),error=>error instanceof ImportParseError&&error.code===code,code);

test('CSV quotes, escaped quotes and embedded separator preserve decoded and original tokens',()=>{
 const t=parse('Date,Description,Amount\n2026-10-01,"A, B says ""hello""","1,500.50"',',');
 assert.deepEqual(t.rows[1].cells,['2026-10-01','A, B says "hello"','1,500.50']);assert.deepEqual(t.rows[1].rawCells,['2026-10-01','"A, B says ""hello"""','"1,500.50"']);
});
test('quoted multiline CRLF/LF cells retain physical starting and ending lines',()=>{
 const t=parse('h1,h2\r\n"line1\r\nline2\nline3",x\r\nnext,y',',');assert.equal(t.rows[1].rowNumber,2);assert.equal(t.rows[1].endLine,4);assert.equal(t.rows[1].cells[0],'line1\r\nline2\nline3');assert.equal(t.rows[2].rowNumber,5);
});
test('TSV paste supports tab separators and quoted tabs/newlines without locale guessing',()=>{
 const t=parse('Date\tDescription\tAmount\n2026-10-01\t"A\tB\nC"\t1500.5','\t');assert.deepEqual(t.rows[1].cells,['2026-10-01','A\tB\nC','1500.5']);assert.equal(t.delimiter,'\t');
});
test('BOM and CR/LF/CRLF accepted, blank lines counted, explicit empty cells kept',()=>{
 const t=parse('\uFEFFa,b\r\n\r\n  \n,\r"",z\n',',');assert.equal(t.blankRows,2);assert.deepEqual(t.rows.map(r=>r.rowNumber),[1,4,5]);assert.deepEqual(t.rows[1].cells,['','']);assert.deepEqual(t.rows[2].cells,['','z']);
});
test('strict quoting rejects unclosed, bare or trailing junk with helpful physical line',()=>{
 for(const text of ['a"b,c','"open','"a" x,b',' "a",b'])failure(text,'quoting');
 assert.throws(()=>parse('a,b\n"open',','),error=>error.code==='quoting'&&error.line===2);
});
test('empty sources, NUL, field/column/row/line and byte limits fail visibly',()=>{
 failure(' \n\r\n','empty');failure('a\0b','control');failure('x'.repeat(L.field+1),'fieldLimit');failure(Array(33).fill('x').join(','),'columnLimit');failure(Array(1001).fill('x').join('\n'),'rowLimit');failure('\n'.repeat(5001)+'x','lineLimit');failure('x'.repeat(L.characters+1),'sourceLimit');failure('ا'.repeat(L.bytes/2+1),'sourceLimit');
 assert.equal(parse(Array(32).fill('x').join(','),',').columns,32);assert.equal(parse(Array(1000).fill('x').join('\n'),',').rows.length,1000);
});
test('quoted token limit checked in addition to decoded content',()=>{
 failure('"'+'""'.repeat(1000)+'"','fieldLimit');assert.equal(parse('x'.repeat(2000),',').rows[0].cells[0].length,2000);
});
test('file extension/size checked before decoding; strict UTF-8 handles BOM and rejects binary/UTF16',()=>{
 validateCsvFile('SYNTHETIC.CSV',20);assert.throws(()=>validateCsvFile('sheet.xlsx',20),e=>e.code==='fileType');assert.throws(()=>validateCsvFile('x.csv',L.bytes+1),e=>e.code==='sourceLimit');
 const bytes=new TextEncoder().encode('\uFEFFa,b\n۱,۲');assert.equal(decodeCsv(bytes.buffer),'a,b\n۱,۲');assert.throws(()=>decodeCsv(new Uint8Array([0xff,0xfe,0x61,0]).buffer),e=>e.code==='encoding');
});
test('explicit date formats disambiguate instead of guessing; Gregorian leap validation',()=>{
 assert.equal(parseImportDate('02/03/2024','DD/MM/YYYY'),'2024-03-02');assert.equal(parseImportDate('02/03/2024','MM/DD/YYYY'),'2024-02-03');assert.equal(parseImportDate('2024-02-29','YYYY-MM-DD'),'2024-02-29');assert.equal(parseImportDate('۲۹/۰۲/۲۰۲۴','DD/MM/YYYY'),'2024-02-29');
 for(const value of ['2023-02-29','1900-02-29','2026-04-31','2026-13-01','0000-01-01','2026-1-1',''])assert.equal(parseImportDate(value,'YYYY-MM-DD'),null,value);
 assert.equal(parseImportDate('2000-02-29','YYYY-MM-DD'),'2000-02-29');assert.equal(parseImportDate('02/03/2024','YYYY-MM-DD'),null);assert.equal(parseImportDate('02/03/2024','AUTO'),null);
});
test('mapping starts empty and duplicate/out-of-range columns are explicit issues',()=>{
 assert.deepEqual(emptyMapping(),{date:null,description:null,amount:null,direction:null,moneyIn:null,moneyOut:null,reference:null});assert.equal(mappingIssues(emptyMapping(),5,'direction').length,4);
 assert.ok(mappingIssues({...options().mapping,date:1},5,'direction').some(i=>i.code==='columnReused'));assert.ok(mappingIssues({...options().mapping,date:99},5,'direction').some(i=>i.code==='badMapping'));
});
test('remapping/headings reinterpret immutable source rows without reparsing or renumbering',()=>{
 const t=table(),before=JSON.stringify(t);assert.equal(reviewImport(t,options())[0].date,'2026-10-01');assert.equal(reviewImport(t,options({mapping:{...options().mapping,date:1}}))[0].date,null);assert.equal(JSON.stringify(t),before);
 assert.equal(reviewImport(t,options())[0].source.rowNumber,2);assert.equal(reviewImport(t,options({headings:false}))[0].source.rowNumber,1);
});
test('classification unresolved by default; direction never invents sales or expense purpose',()=>{
 const r=reviewImport(table(),options())[0];assert.equal(r.classification,'');assert.ok(issues(r).includes('purposeMissing'));assert.equal(r.ready,false);
 assert.equal(reviewImport(table(),options({classification:'DIRECT_SALE_RECEIPT'}))[0].ready,true);
 const out=reviewImport(table('2026-10-01,Payment,10,OUT,R'),options({classification:'DIRECT_SALE_RECEIPT'}))[0];assert.ok(issues(out).includes('purposeMismatch'));
 assert.equal(reviewImport(table('2026-10-01,Payment,10,OUT,R'),options({classification:'OPERATING_EXPENSE_PAYMENT'}))[0].ready,true);
});
test('per-row explicit undecided override remains unresolved even with batch purpose',()=>{
 const r=reviewImport(table(),options({classification:'DIRECT_SALE_RECEIPT',overrides:{2:''}}))[0];assert.equal(r.ready,false);assert.ok(issues(r).includes('purposeMissing'));
});
test('exact grouped/Urdu/Arabic inputs normalize without changing original amount text',()=>{
 for(const [raw,canonical] of [['1500','1500.00'],['1,500','1500.00'],['1500.5','1500.50'],['۱,۵۰۰.۵','1500.50'],['١٥٠٠.٥','1500.50']]){
 const t=table('2026-10-01,Sale,"'+raw+'",IN,R'),r=reviewImport(t,options())[0];assert.equal(r.amount,canonical);assert.equal(r.originalAmounts[0].text,raw);assert.equal(t.rows[1].cells[2],raw);
 }
});
test('HALF_UP exact preview flags rounding; carry-over capacity failure is never repaired',()=>{
 const r=reviewImport(table('2026-10-01,Sale,1500.555,IN,R'),options())[0];assert.equal(r.amount,'1500.56');assert.equal(r.originalAmounts[0].rounded,true);assert.equal(r.originalAmounts[0].text,'1500.555');
 const max=reviewImport(table('2026-10-01,Sale,999999999999.995,IN,R'),options())[0];assert.equal(max.amount,null);assert.ok(issues(max).includes('amountInvalid'));
});
test('ambiguous punctuation, malformed grouping, exponents, signs and zeros are unresolved',()=>{
 for(const raw of ['1,50','1.500,50','1e2','+10','-10','-0','01.00','NaN','0','0.004','']){
 const r=reviewImport(table('2026-10-01,Sale,"'+raw+'",IN,R'),options({classification:'DIRECT_SALE_RECEIPT'}))[0];assert.equal(r.ready,false,raw);assert.ok(r.issues.some(i=>['amountInvalid','negative','zero','amountMissing'].includes(i.code)),raw);
 }
});
const separate=(incoming,outgoing)=>parse('Date,Description,In,Out,Ref\n2026-10-01,Entry,"'+incoming+'","'+outgoing+'",0009',',');
const splitOptions=(patch={})=>options({mode:'separate',mapping:{date:0,description:1,amount:null,direction:null,moneyIn:2,moneyOut:3,reference:4},...patch});
test('separate columns distinguish blank from explicit zero and select only a single nonzero direction',()=>{
 let r=reviewImport(separate('','0'),splitOptions())[0];assert.ok(issues(r).includes('zero'));assert.equal(r.originalAmounts[0].blank,true);assert.equal(r.originalAmounts[1].blank,false);
 r=reviewImport(separate('',''),splitOptions())[0];assert.ok(issues(r).includes('amountMissing'));
 r=reviewImport(separate('0','15.5'),splitOptions({classification:'OPERATING_EXPENSE_PAYMENT'}))[0];assert.equal(r.direction,'OUT');assert.equal(r.amount,'15.50');assert.equal(r.ready,true);
});
test('conflicting original nonzero in/out values reject even if one rounds to zero',()=>{
 for(const [incoming,outgoing] of [['10','20'],['10','0.004']]){const r=reviewImport(separate(incoming,outgoing),splitOptions())[0];assert.ok(issues(r).includes('conflictingAmounts'));assert.equal(r.amount,null);assert.equal(r.ready,false);}
 assert.ok(issues(reviewImport(separate('10','-1'),splitOptions())[0]).includes('negative'));
});
test('direction tokens are explicit, distinct and case-insensitive; no inferred aliases',()=>{
 assert.equal(reviewImport(table('2026-10-01,Sale,1,received,R'),options())[0].direction,null);
 assert.equal(reviewImport(table('2026-10-01,Sale,1,received,R'),options({inToken:'RECEIVED',outToken:'PAID'}))[0].direction,'IN');
 assert.ok(issues(reviewImport(table(),options({inToken:'IN',outToken:'in'}))[0]).includes('tokensInvalid'));
});
test('batch account/currency changes revalidate; non-PKR remains interpreted but not ready',()=>{
 const t=table('2026-10-01,Sale,1250.3755,IN,R');
 const r=reviewImport(t,options({currency:'BHD',account:{...account,currency:'BHD',currencyDecimals:3},classification:'DIRECT_SALE_RECEIPT'}))[0];assert.equal(r.amount,'1250.376');assert.ok(issues(r).includes('unsupportedCurrency'));assert.equal(r.ready,false);
 assert.ok(issues(reviewImport(t,options({account:null}))[0]).includes('accountMissing'));assert.ok(issues(reviewImport(t,options({account:{...account,balanceStatus:'UNAVAILABLE'}}))[0]).includes('accountUnavailable'));assert.ok(issues(reviewImport(t,options({account:{...account,currencyDecimals:3}}))[0]).includes('currencyMismatch'));
 assert.ok(issues(reviewImport(t,options({currency:'XYZ'}))[0]).includes('currencyInvalid'));
});
test('future/before-opening dates and missing descriptions remain unresolved; no today substitution',()=>{
 const missing=reviewImport(table(',Sale,1,IN,R'),options())[0];assert.equal(missing.date,null);assert.ok(issues(missing).includes('dateInvalid'));
 assert.ok(issues(reviewImport(table('2026-10-18,Sale,1,IN,R'),options())[0]).includes('futureDate'));
 assert.ok(issues(reviewImport(table(),options({account:{...account,openingDate:'2026-10-02'}}))[0]).includes('beforeOpening'));
 assert.ok(issues(reviewImport(table('2026-10-01,,1,IN,R'),options())[0]).includes('descriptionMissing'));
 assert.equal(importToday(new Date('2026-10-16T19:00:00Z')),'2026-10-17');
});
test('possible repeats warn across distinct source rows and references without removal',()=>{
 const t=table('2026-10-01,Sale,1.00,IN,0001\n2026-10-01,Sale,1,IN,0002'),before=JSON.stringify(t);const r=reviewImport(t,options({classification:'DIRECT_SALE_RECEIPT'}));assert.equal(r.length,2);assert.deepEqual(r[0].duplicateRows,[3]);assert.deepEqual(r[1].duplicateRows,[2]);assert.equal(r[0].reference,'0001');assert.equal(r.every(row=>row.ready&&!row.excluded),true);assert.equal(JSON.stringify(t),before);
});
test('exclusion/restoration is immutable, preserves source identity and does not lose issues',()=>{
 const original=new Set(),excluded=excludeRows(original,[2],true);assert.equal(original.size,0);const r=reviewImport(table(),options({excluded}));assert.equal(r[0].source.rowNumber,2);assert.equal(r[0].excluded,true);assert.equal(r[0].ready,false);assert.deepEqual(previewCounts(r),{source:1,included:0,excluded:1,issues:0,ready:0});assert.equal(reviewImport(table(),options({excluded:excludeRows(excluded,[2],false)}))[0].issues.length>0,true);assert.equal(excluded.has(2),true);
});
test('preview jobs discard late reads on selection/business/session invalidation',async()=>{
 const jobs=new PreviewJob(),old=jobs.start(),newer=jobs.start();assert.equal(old.signal.aborted,true);assert.equal(jobs.accepts(old),false);assert.equal(jobs.accepts(newer),true);jobs.close();assert.equal(newer.signal.aborted,true);assert.equal(jobs.accepts(newer),false);assert.throws(()=>jobs.start(),e=>e.name==='AbortError');
});
test('account read helper exhausts bounded real pages and propagates cancellation without partial fallback',async()=>{
 const controller=new AbortController(),calls=[];
 const result=await loadImportAccounts(async(page,signal)=>{calls.push({page,signal});return{items:[{...account,id:String(page)}],page,size:100,totalElements:2,totalPages:2};},controller.signal);assert.equal(result.length,2);assert.deepEqual(calls.map(c=>c.page),[0,1]);assert.equal(calls.every(c=>c.signal===controller.signal),true);
 await assert.rejects(loadImportAccounts(async()=>{throw new Error('403');},controller.signal));
 await assert.rejects(loadImportAccounts(async page=>({items:[],page,size:100,totalElements:1001,totalPages:11}),controller.signal));
 controller.abort();await assert.rejects(loadImportAccounts(async()=>{throw new Error('must not run');},controller.signal),e=>e.name==='AbortError');
});
test('feature pure modules and copy pass isolated semantic TypeScript without shared outputs',()=>{
 const roots=['lib/imports/delimited.ts','lib/imports/preview.ts','lib/imports/lifetime.ts','lib/imports/accounts.ts','components/records/import/import-copy.ts'].map(name=>new URL('../src/'+name,import.meta.url));
 const program=ts.createProgram(roots.map(url=>decodeURIComponent(url.pathname.replace(/^\/([A-Za-z]:)/,'$1'))),{strict:true,noEmit:true,target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,moduleResolution:ts.ModuleResolutionKind.Node10,lib:['lib.es2022.d.ts','lib.dom.d.ts'],types:[],skipLibCheck:true});
 const errors=ts.getPreEmitDiagnostics(program).filter(d=>d.category===ts.DiagnosticCategory.Error);assert.deepEqual(errors.map(d=>ts.flattenDiagnosticMessageText(d.messageText,'\n')),[]);
});

// Render actual feature components with controlled shared-boundary hooks; no browser/server is started.
const {createElement}=require('react'),{renderToStaticMarkup}=require('react-dom/server');
let uiLocale='en',uiSession={epoch:1,user:{id:'synthetic-user'},business:{businessId:'business-a',role:'OWNER',membershipStatus:'ACTIVE'},status:'authenticated'},uiDemo=false;
let privateReads=0;const pendingLoads=[];const uiCache=new Map();
function uiModule(name){
 if(name==='lib/i18n/context')return{useLanguage:()=>({locale:uiLocale,direction:uiLocale==='ur'?'rtl':'ltr'})};
 if(name==='components/auth/SessionProvider')return{useSession:()=>uiSession};
 if(name==='lib/api/config')return{get isDemoMode(){return uiDemo;}};
 if(name==='lib/api/dev-a/accounts')return{accountsApi:{list:async()=>{privateReads++;return{items:[],page:0,size:100,totalElements:0,totalPages:0};}}};
 if(name==='components/integration/useLiveResource')return{useLiveResource:load=>{pendingLoads.push(Promise.resolve().then(()=>load(new AbortController().signal)));return{data:[],loading:false,error:'',retry(){}};}};
 if(name==='components/integration/LivePage')return{LivePage:({title,children})=>createElement('main',null,createElement('h1',null,title),children)};
 if(uiCache.has(name))return uiCache.get(name);
 const extension=name.startsWith('lib/') ? '.ts' : ['lib/utils/cn','lib/imports/delimited','lib/imports/preview','lib/imports/accounts','lib/imports/lifetime','components/records/import/import-copy'].includes(name)?'.ts':'.tsx';
 const text=readFileSync(new URL('../src/'+name+extension,import.meta.url),'utf8');
 const result=ts.transpileModule(text,{reportDiagnostics:true,compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX}});assert.equal(result.diagnostics?.filter(d=>d.category===ts.DiagnosticCategory.Error).length,0,name);
 const mod={exports:{}};
 const resolve=specifier=>{
  if(specifier==='next/link')return{__esModule:true,default:({children,...props})=>createElement('a',props,children)};
  if(specifier.startsWith('@/'))return uiModule(specifier.slice(2));
  if(specifier.startsWith('.'))return uiModule(path.posix.normalize(path.posix.join(path.posix.dirname(name),specifier)));
  return require(specifier);
 };
 new Function('require','module','exports',result.outputText)(resolve,mod,mod.exports);uiCache.set(name,mod.exports);return mod.exports;
}
test('actual EN/UR rows render rounding/issues/references and inert HTML/formula source text',()=>{
 const t=parse('date\tdescription\tamount\tdirection\treference\n2026-10-01\t<script>alert(1)</script>\t1.005\tIN\t=1+1\n2026-10-01\t<script>alert(1)</script>\t1.005\tIN\t0001','\t');const rows=reviewImport(t,options());
 const {ImportRows}=uiModule('components/records/import/ImportRows');
 for(const copy of [importEn,importUr]){const html=renderToStaticMarkup(createElement(ImportRows,{rows,currency:'PKR',selected:new Set(),copy,onSelect(){},onExclude(){},onPurpose(){}}));
  assert.ok(html.includes('1.01 PKR'));assert.ok(html.includes(copy.rounded));assert.ok(html.includes(copy.duplicates));assert.ok(html.includes(copy.issues.purposeMissing));assert.ok(html.includes('=1+1'));assert.ok(html.includes('0001'));assert.ok(html.includes('&lt;script&gt;'));assert.equal(html.includes('<script>'),false);assert.match(html,/<bdi dir="ltr"/);assert.match(html,/<input[^>]*type="checkbox"/);assert.equal(/>Import<|>Post</.test(html),false);
 }
});
test('actual original preview preserves row lines, headings and safe mapped source labels in both locales',()=>{
 const t=table('2026-10-01,"<img src=x onerror=alert(1)>",1,IN,001');const {SourcePreview,ImportMapping}=uiModule('components/records/import/ImportMapping');
 for(const copy of [importEn,importUr]){
  const original=renderToStaticMarkup(createElement(SourcePreview,{table:t,copy}));assert.ok(original.includes('&lt;img'));assert.equal(original.includes('<img'),false);assert.ok(original.includes(copy.line));assert.ok(original.includes('tabindex="0"'));
  const mapped=renderToStaticMarkup(createElement(ImportMapping,{table:t,options:options({mapping:emptyMapping()}),copy,onChange(){}}));assert.ok(mapped.includes(copy.headings));assert.ok(mapped.includes('aria-invalid="true"'));assert.ok(mapped.includes('YYYY-MM-DD'));assert.ok(mapped.includes(copy.numberConvention));
 }
});
test('demo mounts the real local wizard without private account requests; initial settings stay progressive',async()=>{
 const {ImportPreview}=uiModule('components/records/import/ImportPreview');privateReads=0;
 for(const locale of ['en','ur']){uiLocale=locale;const copy=locale==='ur'?importUr:importEn;const html=renderToStaticMarkup(createElement(ImportPreview,{demo:true}));assert.ok(html.includes(copy.notice));assert.ok(html.includes(copy.upload));assert.ok(html.includes(copy.paste));assert.ok(html.includes(copy.privacy));assert.equal(html.includes(copy.defaultPurpose),false);assert.equal(html.includes('localStorage'),false);assert.match(html,new RegExp('dir="'+(locale==='ur'?'rtl':'ltr')+'"'));}
 await Promise.all(pendingLoads.splice(0));assert.equal(privateReads,0);
});
test('page keys private preview by identity/business/epoch and unmounts it on session or membership loss',()=>{
 const Page=uiModule('app/records/import/page').default;uiDemo=false;uiLocale='en';
 const first=Page().props.children;assert.equal(typeof first.type,'function');
 uiSession={...uiSession,epoch:2,business:{...uiSession.business,businessId:'business-b'}};const second=Page().props.children;assert.notEqual(first.key,second.key);
 uiSession={...uiSession,status:'anonymous',user:null,business:null};assert.equal(Page().props.children.type,'p');
 uiSession={epoch:3,status:'authenticated',user:{id:'synthetic-user'},business:{businessId:'business-a',role:'VIEWER',membershipStatus:'SUSPENDED'}};assert.equal(Page().props.children.type,'p');
});
test('typed English and Urdu copy have matching keys and explicit preview/zero-persistence messaging',()=>{
 const compare=(a,b)=>{assert.deepEqual(Object.keys(a).sort(),Object.keys(b).sort());for(const key of Object.keys(a)){if(typeof a[key]==='object')compare(a[key],b[key]);else assert.equal(typeof b[key],'string');}};compare(importEn,importUr);assert.ok(importEn.notice.includes('have not been added'));assert.ok(importEn.purposeHelp.includes('does not establish'));
});
test('owned route/components pass isolated semantic check with stable shared-boundary types and no emit',()=>{
 const src=path.resolve(new URL('../src/',import.meta.url).pathname.replace(/^\/([A-Za-z]:)/,'$1'));
 const stubDirectory=path.join(temporary,'boundary-types');mkdirSync(stubDirectory,{recursive:true});
 const stubs={
  '@/components/auth/SessionProvider':"export function useSession(): {epoch:number;status:string;user:{id:string}|null;business:{businessId:string;role:string;membershipStatus:string}|null};",
  '@/lib/i18n/context':"export function useLanguage(): {locale:'en'|'ur';direction:'ltr'|'rtl'};",
  '@/lib/api/config':'export const isDemoMode:boolean;',
  '@/components/integration/LivePage':"import type {ReactNode} from 'react';export function LivePage(props:{title:string;children?:ReactNode}):ReactNode;",
  '@/components/integration/useLiveResource':'export function useLiveResource<T>(load:(signal:AbortSignal)=>Promise<T>):{data:T|null;loading:boolean;error:string;retry:()=>void};',
  '@/lib/api/dev-a/accounts':"import type {AccountQuery,AccountPage} from '"+path.join(src,'lib/api/dev-a/account-contracts').replaceAll('\\','/')+"';export const accountsApi:{list(query?:AccountQuery,signal?:AbortSignal):Promise<AccountPage>};",
  'next/link':"import type {FC,AnchorHTMLAttributes} from 'react';declare const Link:FC<AnchorHTMLAttributes<HTMLAnchorElement>&{href:string}>;export default Link;",
 };
 const files=new Map();let index=0;for(const [name,text] of Object.entries(stubs)){const file=path.join(stubDirectory,`${index++}.d.ts`);writeFileSync(file,text);files.set(name,file);}
 const roots=['app/records/import/page.tsx','components/records/import/ImportPreview.tsx','components/records/import/ImportRows.tsx','components/records/import/ImportMapping.tsx'].map(name=>path.join(src,name));
 const compilerOptions={strict:true,noEmit:true,target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext,moduleResolution:ts.ModuleResolutionKind.Bundler,jsx:ts.JsxEmit.ReactJSX,baseUrl:src,paths:{'@/*':['*']},types:['react'],typeRoots:[path.resolve('node_modules/@types')],skipLibCheck:true,lib:['lib.es2022.d.ts','lib.dom.d.ts','lib.dom.iterable.d.ts']};
 const host=ts.createCompilerHost(compilerOptions);host.resolveModuleNames=(names,containing)=>names.map(name=>files.has(name)?{resolvedFileName:files.get(name),extension:ts.Extension.Dts}:ts.resolveModuleName(name,containing,compilerOptions,host).resolvedModule);
 const program=ts.createProgram(roots,compilerOptions,host);const errors=ts.getPreEmitDiagnostics(program).filter(d=>d.category===ts.DiagnosticCategory.Error);assert.deepEqual(errors.map(d=>(d.file?.fileName??'')+': '+ts.flattenDiagnosticMessageText(d.messageText,'\n')),[]);
});

test('zero- and three-decimal currencies use declared scale while remaining unsupported for posting',()=>{
 for(const [currency,scale,input,expected] of [['JPY',0,'1250.5','1251'],['BHD',3,'1250.3755','1250.376']]){
  const r=reviewImport(table('2026-10-01,Sale,'+input+',IN,R'),options({currency,account:{...account,currency,currencyDecimals:scale},classification:'DIRECT_SALE_RECEIPT'}))[0];assert.equal(r.amount,expected);assert.equal(r.originalAmounts[0].rounded,true);assert.equal(r.ready,false);assert.ok(issues(r).includes('unsupportedCurrency'));
 }
 for(const [currency,scale,input] of [['JPY',0,'999999999999.5'],['BHD',3,'999999999999.9995']]){
  const r=reviewImport(table('2026-10-01,Sale,'+input+',IN,R'),options({currency,account:{...account,currency,currencyDecimals:scale}}))[0];assert.equal(r.amount,null);assert.ok(issues(r).includes('amountInvalid'));
 }
});

test('in-memory preview preserves parsed data across language switch and clears on business switch or session loss',()=>{
 const {getStoredPreview,saveStoredPreview,clearStoredPreview}=uiModule('lib/imports/lifetime');
 const t=table('2026-10-01,Sale,1500,IN,001');
 const key='synthetic-user:business-a';
 clearStoredPreview();
 assert.equal(getStoredPreview(key),null);
 const sessionState={
  source:{table:t,filename:'sales.csv'},
  paste:'',
  settings:options(),
  step:'review',
  accountId:'synthetic-account',
  currency:'PKR',
  excluded:new Set([1]),
  selected:new Set(),
  overrides:{},
  issuesOnly:false,
  page:0,
 };
 saveStoredPreview(key,sessionState);
 const retrieved=getStoredPreview(key);
 assert.ok(retrieved);
 assert.equal(retrieved.source.filename,'sales.csv');
 assert.equal(retrieved.step,'review');
 assert.equal(retrieved.currency,'PKR');
 assert.ok(retrieved.excluded.has(1));

 // Swapping language retains memory state
 uiLocale='ur';
 assert.equal(getStoredPreview(key),retrieved);
 uiLocale='en';
 assert.equal(getStoredPreview(key),retrieved);

 // Switching business clears access to previous state
 assert.equal(getStoredPreview('synthetic-user:business-b'),null);

 // Explicit session clear (logout/expiry)
 clearStoredPreview(key);
 assert.equal(getStoredPreview(key),null);
});

test('bilingual draft-saving copy has complete parity and matching placeholders',()=>{
 const keys=[
  'saveDrafts','savingDrafts','savingChunk','savedCountNotice','savedSuccess',
  'chunkFailure','openToReview','savedBadge','replayedBadge','openDraft',
  'unresolvedDraftNotice','validationBlockNotice','allSelectedSaved','noRowsSelected',
  'recoverBatchTitle','recoverBatchHelp','recoverBatchTotal','recoverBatchEmpty',
  'loadingBatch','loadBatchError','retryBatch','permissionDenied'
 ];
 for(const k of keys){
  assert.ok(typeof importEn[k]==='string'&&importEn[k].length>0,`importEn missing ${k}`);
  assert.ok(typeof importUr[k]==='string'&&importUr[k].length>0,`importUr missing ${k}`);
 }
 assert.ok(importEn.savingChunk.includes('{start}')&&importEn.savingChunk.includes('{end}')&&importEn.savingChunk.includes('{total}'));
 assert.ok(importUr.savingChunk.includes('{start}')&&importUr.savingChunk.includes('{end}')&&importUr.savingChunk.includes('{total}'));
});

test('lifetime preview storage preserves batchId and savedDrafts across language switch',()=>{
 const {getStoredPreview,saveStoredPreview,clearStoredPreview}=uiModule('lib/imports/lifetime');
 const t=table('2026-10-01,Sale,1500,IN,001');
 const key='synthetic-user:business-lifetime';
 clearStoredPreview();
 const batchId='a1b2c3d4-e5f6-4701-abcd-0123456789ab';
 const savedDrafts={
  2:{draftId:'b1b2b3b4-c5c6-4701-abcd-ef0123456789',status:'SAVED',draftStatus:'DRAFT',readyToRecord:true}
 };
 const sessionState={
  source:{table:t,filename:'sales.csv'},
  paste:'',
  settings:options(),
  step:'review',
  accountId:'synthetic-account',
  currency:'PKR',
  excluded:new Set(),
  selected:new Set([2]),
  overrides:{},
  issuesOnly:false,
  page:0,
  batchId,
  savedDrafts,
 };
 saveStoredPreview(key,sessionState);
 const retrieved=getStoredPreview(key);
 assert.ok(retrieved);
 assert.equal(retrieved.batchId,batchId);
 assert.deepEqual(retrieved.savedDrafts,savedDrafts);
 clearStoredPreview(key);
 assert.equal(getStoredPreview(key),null);
});

test('import-drafts API parser validates row result and response contracts strictly',()=>{
 const {parseImportDraftRowResult,parseImportDraftsResponse,parseImportBatchDetailResponse,InvalidImportDraftResponse}=uiModule('lib/api/dev-a/import-drafts');
 const validRow={
  sourceRowId:'row-1',
  sourceRowNumber:1,
  sourceReference:'REF-01',
  draftId:'b1b2b3b4-c5c6-4701-abcd-ef0123456789',
  status:'SAVED',
  draftStatus:'DRAFT',
  readyToRecord:true,
  version:0,
  recordedTransactionId:null
 };
 const parsedRow=parseImportDraftRowResult(validRow);
 assert.equal(parsedRow.sourceRowId,'row-1');
 assert.equal(parsedRow.draftId,'b1b2b3b4-c5c6-4701-abcd-ef0123456789');
 assert.equal(parsedRow.status,'SAVED');
 assert.equal(parsedRow.readyToRecord,true);

 // Missing or invalid fields throw InvalidImportDraftResponse
 assert.throws(()=>parseImportDraftRowResult({...validRow,draftId:'invalid-uuid'}),error=>error instanceof InvalidImportDraftResponse);
 assert.throws(()=>parseImportDraftRowResult({...validRow,status:'UNKNOWN'}),error=>error instanceof InvalidImportDraftResponse);
 assert.throws(()=>parseImportDraftRowResult({...validRow,sourceRowNumber:0}),error=>error instanceof InvalidImportDraftResponse);

 const validBatchResponse={
  batchId:'a1b2c3d4-e5f6-4701-abcd-0123456789ab',
  sourceLabel:'test.csv',
  savedCount:1,
  replayedCount:0,
  totalRowsInBatch:1,
  rows:[validRow]
 };
 const parsedBatch=parseImportDraftsResponse(validBatchResponse);
 assert.equal(parsedBatch.batchId,'a1b2c3d4-e5f6-4701-abcd-0123456789ab');
 assert.equal(parsedBatch.savedCount,1);
 assert.equal(parsedBatch.rows.length,1);

 assert.throws(()=>parseImportDraftsResponse({...validBatchResponse,batchId:'not-a-uuid'}),error=>error instanceof InvalidImportDraftResponse);

 const validDetailResponse={
  batchId:'a1b2c3d4-e5f6-4701-abcd-0123456789ab',
  sourceLabel:'test.csv',
  totalRows:1,
  createdAt:'2026-10-18T10:00:00Z',
  updatedAt:'2026-10-18T10:00:00Z',
  rows:[validRow]
 };
 const parsedDetail=parseImportBatchDetailResponse(validDetailResponse);
 assert.equal(parsedDetail.totalRows,1);
 assert.equal(parsedDetail.rows[0].sourceRowId,'row-1');
});
