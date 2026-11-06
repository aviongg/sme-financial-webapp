import assert from 'node:assert/strict';
import {test,after} from 'node:test';
import {readFileSync,writeFileSync,mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';
import ts from 'typescript';
const require=createRequire(import.meta.url),temp=mkdtempSync(path.join(tmpdir(),'finsight-s2b-'));
after(()=>rmSync(temp,{recursive:true,force:true}));
const source=p=>readFileSync(new URL('../src/'+p,import.meta.url),'utf8');
function load(p){const target=path.join(temp,path.basename(p)+'.cjs');writeFileSync(target,ts.transpileModule(source(p),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText);return require(target);}
const {recordsView,recordsTabHref,recordViews}=load('components/records/workspace-routing.ts');
const {financeWorkspaces,routeIsActive}=load('lib/navigation/workspaces.ts');
const {routePermission}=load('lib/api/navigation.ts');
const {sameSessionBoundary,hasPermission}=load('lib/api/session.ts');
const {formatPkr}=load('lib/utils/decimal-display.ts');
const {unifiedEn,unifiedUr}=load('lib/i18n/translations/unified.ts');
test('explicit Records tab overrides stale draft deep link; bare draft links still open review',()=>{
 assert.equal(recordsView(new URLSearchParams('view=activity&draftId=saved')),'activity');
 assert.equal(recordsView(new URLSearchParams('draftId=saved')),'review');
 for(const view of recordViews) assert.equal(recordsView(new URLSearchParams({view})),view);
 assert.equal(recordsView(new URLSearchParams('view=unsupported')),'activity');
});
test('tab navigation clears transient operations, preserves independently namespaced list filters and history context',()=>{
 const before=new URLSearchParams('view=review&draftId=x&transactionId=y&add=1&focus=scan&moneypage=3&page=2&period=custom&startDate=2026-10-01');
 const url=recordsTabHref(before,'accounts'),after=new URL(url,'http://local').searchParams;
 for(const key of ['draftId','transactionId','add','focus'])assert.equal(after.has(key),false);
 for(const key of ['moneypage','page','period','startDate'])assert.equal(after.get(key),before.get(key));
 assert.equal(before.get('draftId'),'x');assert.equal(recordsView(after),'accounts');
});
test('workspace destinations exist, cover every financial view and have complete bilingual labels',()=>{
 for(const [key,href]of financeWorkspaces){assert.ok(unifiedEn[key]);assert.ok(unifiedUr[key]);const p=href.split('?')[0];assert.doesNotThrow(()=>source('app'+p+'/page.tsx'),href);}
 for(const word of ['accounts','transactions','imports','contacts','receivables','payables','advances','calendar','reconciliation','recurring','forecast','risks','history'])assert.ok(financeWorkspaces.some(([k])=>k===word));
 assert.deepEqual(Object.keys(unifiedEn),Object.keys(unifiedUr));
});
test('active links match complete path boundaries and never mark a query action active by pathname',()=>{
 assert.equal(routeIsActive('/records/import','/records'),true);assert.equal(routeIsActive('/invoices/abc','/invoices'),true);
 assert.equal(routeIsActive('/invoices-other','/invoices'),false);assert.equal(routeIsActive('/records','/records?add=1'),false);assert.equal(routeIsActive('/records','/'),false);
});
test('query-bearing scan/create routes retain actual permission boundaries',()=>{
 assert.equal(routePermission('/scan?returnTo=%2Frecords'),'DOCUMENT_UPLOAD');
 assert.equal(routePermission('/records?add=1&view=activity'),'RECORD_CREATE_UPDATE');
 assert.equal(routePermission('/records?view=receivables'),'FINANCIAL_DATA_READ');
 assert.equal(routePermission('/invoices/new?returnTo=%2Finvoices'),'RECORD_CREATE_UPDATE');
 for(const role of ['MANAGER','VIEWER']){assert.equal(hasPermission({role,membershipStatus:'ACTIVE'},'FINANCIAL_DATA_READ'),true);assert.equal(hasPermission({role,membershipStatus:'ACTIVE'},routePermission('/records?add=1')),false);}
});
const user={id:'u',authStage:'FULLY_AUTHENTICATED',accountStatus:'ACTIVE',mustChangePassword:false,platformRole:null,email:'test@example.test',fullName:'Test'};
const business={businessId:'b',role:'OWNER',membershipStatus:'ACTIVE',businessType:'retail',active:true,businessName:'Test',languagePreference:'en'};
test('routine language/name revalidation preserves unsaved input but every security-boundary change invalidates it',()=>{
 assert.equal(sameSessionBoundary(user,{...user,fullName:'New'},business,{...business,businessName:'Renamed',languagePreference:'ur'}),true);
 for(const delta of [{id:'other'},{authStage:'MFA_CHALLENGE_REQUIRED'},{accountStatus:'SUSPENDED'},{mustChangePassword:true},{platformRole:'ADMIN'}])assert.equal(sameSessionBoundary(user,{...user,...delta},business,business),false);
 for(const delta of [{businessId:'other'},{role:'VIEWER'},{membershipStatus:'SUSPENDED'},{businessType:'services'},{active:false}])assert.equal(sameSessionBoundary(user,user,business,{...business,...delta}),false);
 assert.equal(sameSessionBoundary(user,user,null,null),true);assert.equal(sameSessionBoundary(user,user,business,null),false);assert.equal(sameSessionBoundary(null,user,null,null),false);
});
test('exact PKR presentation preserves large strings and separates unavailable from legitimate zero',()=>{
 assert.equal(formatPkr('999999999999.99'),'999,999,999,999.99');assert.equal(formatPkr('-2500.50'),'-2,500.50');assert.equal(formatPkr(1500.5),'1,500.50');assert.equal(formatPkr('0'),'0.00');
 for(const value of [null,undefined,'',NaN,Infinity,'1e5','1.001','malformed','-0.00','+1.00'])assert.equal(formatPkr(value),'—');
});
test('source disclosures distinguish monthly scores from daily actuals and forecasts in both languages',()=>{
 assert.match(unifiedEn.snapshotHelp,/monthly summaries/);assert.match(unifiedEn.snapshotHelp,/do not automatically update/);assert.match(unifiedEn.snapshotHelp,/forecasts/);assert.ok(unifiedUr.snapshotHelp.length>80);
 const dashboard=source('components/integration/LiveDashboard.tsx');assert.match(dashboard,/unifiedCopy\(locale\)\.snapshotHelp/);assert.match(source('components/workspace/RecordedTrends.tsx'),/href="\/records\/history"/);
});
test('Records obligation views remount by direction instead of reusing the initial-direction state',()=>{
 const text=source('app/records/page.tsx');assert.match(text,/<ObligationsWorkspace key="receivables" initialDirection="RECEIVABLE"/);assert.match(text,/<ObligationsWorkspace key="payables" initialDirection="PAYABLE"/);
});


test('actual modal hook contains keyboard focus, respects nested ownership, and restores the opener',()=>{
 const effects=[],listeners=new Map();let closed=0;
 class Element {
  constructor(parent=null){this.parent=parent;this.controls=[];this.tabIndex=0;this.isConnected=true;}
  contains(node){return node===this||Boolean(node?.parent&&this.contains(node.parent));}
  querySelectorAll(){return this.controls;}
  getClientRects(){return[{}];}
  matches(){return false;}
  closest(){return null;}
  focus(){doc.activeElement=this;}
 }
 const opener=new Element(),root=new Element(),first=new Element(root),last=new Element(root);root.controls=[first,last];
 const doc={activeElement:opener,body:{style:{overflow:'auto'}},addEventListener(k,v){if(!listeners.has(k))listeners.set(k,new Set());listeners.get(k).add(v);},removeEventListener(k,v){listeners.get(k)?.delete(v);}};
 const code=ts.transpileModule(source('components/ui/useModalFocus.ts'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
 const compiledModule={exports:{}};new Function('require','module','exports','document','HTMLElement',code)(()=>({useRef:v=>({current:v}),useEffect:fn=>{const cleanup=fn();if(cleanup)effects.push(cleanup);}}),compiledModule,compiledModule.exports,doc,Element);
 compiledModule.exports.useModalFocus(true,()=>closed++,{current:root});assert.equal(doc.activeElement,first);assert.equal(doc.body.style.overflow,'hidden');
 const key=(key,shiftKey=false)=>{let prevented=false,stopped=false;for(const fn of [...listeners.get('keydown')]){fn({key,shiftKey,preventDefault(){prevented=true;},stopImmediatePropagation(){stopped=true;}});if(stopped)break;}return prevented;};
 last.focus();assert.equal(key('Tab'),true);assert.equal(doc.activeElement,first);assert.equal(key('Tab',true),true);assert.equal(doc.activeElement,last);
 key('Escape');assert.equal(closed,1);effects.pop()();assert.equal(doc.activeElement,opener);assert.equal(doc.body.style.overflow,'auto');
 // Parent mounting after its child must not steal Escape or focus ownership.
 const outer=new Element(),inner=new Element(outer),inside=new Element(inner);inner.controls=[inside];outer.controls=[new Element(outer)];let innerClosed=0,outerClosed=0;
 compiledModule.exports.useModalFocus(true,()=>innerClosed++,{current:inner});compiledModule.exports.useModalFocus(true,()=>outerClosed++,{current:outer});key('Escape');assert.equal(innerClosed,1);assert.equal(outerClosed,0);
 effects.pop()();effects.pop()();assert.equal(doc.body.style.overflow,'auto');
});



test('business selection preserves protected deep links and only setup entry routes default to dashboard',()=>{
 const {businessSelectionReturn}=load('lib/navigation/workspaces.ts');assert.equal(businessSelectionReturn('/finance/accounts','?page=2'),'/finance/accounts?page=2');assert.equal(businessSelectionReturn('/records','?view=review&draftId=x'),'/records?view=review&draftId=x');assert.equal(businessSelectionReturn('/onboarding',''),'/');assert.equal(businessSelectionReturn('/business-setup',''),'/');assert.equal(businessSelectionReturn('/records','','/business-setup'),'/business-setup');
});
