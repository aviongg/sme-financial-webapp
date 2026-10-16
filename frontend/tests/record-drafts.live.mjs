import assert from 'node:assert/strict';
import {writeFileSync,mkdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import { randomUUID } from 'node:crypto';

export async function verifyRecordDrafts({api,owner,viewer,manager,accountant,page,browser,signin,origin,accountId,today,blockers,mark,businessA,businessB,txCounts,requestFactory}) {
 const diagnostics=[];const directory=new URL('../test-artifacts/accounts-live/',import.meta.url);mkdirSync(directory,{recursive:true});
 const capture=(label,request,status,response)=>{diagnostics.push({label,request,status,response});writeFileSync(fileURLToPath(new URL('draft-parity.json',directory)),JSON.stringify(diagnostics,null,2));};
 const base='/finance/drafts', create=(data,who=owner)=>api(who.context,base,'POST',data,{'Idempotency-Key':randomUUID()});
 const read=async id=>{const r=await api(owner.context,base+'/'+id);assert.equal(r.status(),200);return r.json();};
 const record=(id,data,key=randomUUID(),who=owner)=>api(who.context,base+'/'+id+'/record','POST',data,{'Idempotency-Key':key});
 const complete={accountId,eventType:'DIRECT_SALE_RECEIPT',effectiveDate:today,amountInput:'15.55',description:'Draft HTTP fixture'};
 const fingerprint=async()=>{
  const summary=await (await api(owner.context,'/finance/transactions/summary')).json();delete summary.observationAt;
  const accounts=await (await api(owner.context,'/finance/accounts?size=100')).json();
  return JSON.stringify({summary,accounts:accounts.items.map(a=>[a.id,a.currentBalance,a.balanceStatus]).sort()});
 };
 await verifyReplaySyntax({api,owner,accountId,today,txCounts,mark,requestFactory,readers:[viewer,manager]});
 const before=await fingerprint();
 const raw=' ۱۵۰۰. ';
 let response=await create({amountInput:raw,description:'Verbatim raw input regression'});assert.equal(response.status(),201);const verbatim=await response.json();
 if(verbatim.amountInput!==raw)blockers.push('BE-DRAFT-RAW: POST /api/finance/drafts '+JSON.stringify({amountInput:raw})+' returned HTTP '+response.status()+' amountInput='+JSON.stringify(verbatim.amountInput)+'; contract 2.3 requires verbatim text.');

 for(const rawInput of [' ۱۵۰۰. ','\t١٥٠٠.٥\n','1500.',' '.repeat(98)+'1.']) {
  const created=await create({amountInput:rawInput,description:'Parity raw text'});const body=await created.json();capture('verbatim create',{amountInput:rawInput},created.status(),body);
  assert.equal(created.status(),201);assert.equal(body.amountInput,rawInput);assert.equal((await read(body.id)).amountInput,rawInput);
  const updateText=' '+rawInput.slice(0,95)+' . ';
  const updated=await api(owner.context,base+'/'+body.id,'PUT',{version:body.version,amountInput:updateText});const updatedBody=await updated.json();capture('verbatim update',{version:body.version,amountInput:updateText},updated.status(),updatedBody);assert.equal(updated.status(),200);assert.equal(updatedBody.amountInput,updateText);assert.equal((await read(body.id)).amountInput,updateText);
  const tooLong=' '.repeat(99)+'1.';assert.equal(tooLong.length,101);
  assert.equal((await create({amountInput:tooLong})).status(),400);
  const rejected=await api(owner.context,base+'/'+body.id,'PUT',{version:updatedBody.version,amountInput:tooLong});assert.equal(rejected.status(),400);const unchanged=await read(body.id);assert.equal(unchanged.version,updatedBody.version);assert.equal(unchanged.amountInput,updateText);
 }
 mark('DRAFT PARITY HTTP: create/read/update preserve whitespace, Urdu/Arabic digits and unfinished punctuation; 100 allowed and 101 rejected before trimming');
 response=await create({amountInput:'1500.',description:'Incomplete persists'});assert.equal(response.status(),201);let saved=await response.json();assert.equal(saved.amountInput,'1500.');assert.equal((await read(saved.id)).amountInput,'1500.');
 response=await api(owner.context,base+'/'+saved.id,'PUT',{version:saved.version,amountInput:'۱,۵۰۰.۵',description:'Updated incomplete'});assert.equal(response.status(),200);saved=await response.json();assert.equal(saved.accountId,null);assert.equal(saved.eventType,null);assert.equal(saved.effectiveDate,null);
 assert.equal((await api(owner.context,base+'/'+saved.id,'PUT',{version:saved.version-1,amountInput:'stale'})).status(),409);assert.equal((await read(saved.id)).amountInput,'۱,۵۰۰.۵');
 response=await api(owner.context,base+'/'+saved.id+'/discard','POST',{version:saved.version});assert.equal(response.status(),200);saved=await response.json();assert.equal(saved.status,'DISCARDED');
 assert.equal((await api(owner.context,base+'/'+saved.id,'PUT',{version:saved.version,amountInput:'1'})).status(),409);assert.equal((await record(saved.id,{version:saved.version,confirmedAmount:'1.00'})).status(),409);
 assert.equal(await fingerprint(),before);
 mark('DRAFT HTTP: incomplete text persists, optimistic update/discard terminal behavior and zero transaction/account/summary effects');
 for(const member of [viewer,manager]){assert.equal((await api(member.context,base)).status(),200);assert.equal((await create({},member)).status(),403);}
 response=await create({description:'Accountant draft'},accountant);assert.equal(response.status(),201);
 await api(owner.context,'/businesses/active','POST',{businessId:businessB});assert.equal((await api(owner.context,base+'/'+saved.id)).status(),404);await api(owner.context,'/businesses/active','POST',{businessId:businessA});
 mark('DRAFT HTTP: OWNER/ACCOUNTANT mutation, MANAGER/VIEWER read-only and real cross-business detail denial');
 response=await create({...complete,amountInput:'10.00'});assert.equal(response.status(),201);const concurrent=await response.json();
 const start=await (await api(owner.context,'/finance/transactions?accountId='+accountId+'&size=100')).json();
 const attempts=await Promise.all(Array.from({length:6},()=>record(concurrent.id,{version:concurrent.version,confirmedAmount:'10.00'})));
 assert.equal(attempts.filter(r=>r.status()===201).length,1);assert.equal(attempts.filter(r=>r.status()===409).length,5);
 const end=await (await api(owner.context,'/finance/transactions?accountId='+accountId+'&size=100')).json();assert.equal(end.totalElements,start.totalElements+1);
 response=await create(complete);assert.equal(response.status(),201);const retryDraft=await response.json(),key=randomUUID(),payload={version:retryDraft.version,confirmedAmount:'15.55'};
 response=await record(retryDraft.id,payload,key);assert.equal(response.status(),201);const original=await response.json();
 const replay=await record(retryDraft.id,payload,key);assert.equal(replay.status(),201);assert.equal((await replay.json()).transaction.id,original.transaction.id);
 const changed=await record(retryDraft.id,{...payload,confirmedAmount:'16.55'},key);const changedBody=await changed.json();
 if(changed.status()!==409)blockers.push('BE-DRAFT-REPLAY: POST '+base+'/'+retryDraft.id+'/record same Idempotency-Key changed confirmedAmount 15.55 -> 16.55 returned '+changed.status()+' with transaction.amount='+changedBody.transaction?.amount+'; expected 409, contract 2.4.6.2.');

 const beforeReplayCounts=await txCounts(),beforeReplayFinancial=await fingerprint();
 capture('identical recording retry',payload,replay.status(),await replay.json());
 for(const patch of [{confirmedAmount:'16.55'},{version:payload.version+1},{description:'Changed operation description'},{eventType:'OPERATING_EXPENSE_PAYMENT'},{effectiveDate:'2026-01-01'},{accountId:'44444444-4444-4444-8444-444444444444'}]) {
  const requestBody={...payload,...patch},r=await record(retryDraft.id,requestBody,key),body=await r.json();capture('changed operation fields',requestBody,r.status(),body);assert.equal(r.status(),409,JSON.stringify(patch));
 }
 assert.equal((await record(retryDraft.id,payload,randomUUID())).status(),409);
 const syntaxBypasses=[];
 for(const amount of ['15.550','1.555e1','+15.55','015.55','15.5','15','15,55','NaN','Infinity','۱۵.۵۵','١٥.٥٥',' 15.55 ','\t15.55\t','15.55\n','\u000015.55\u0000']) {
  const requestBody={...payload,confirmedAmount:amount},r=await record(retryDraft.id,requestBody,key),body=await r.json();capture('malformed recovery amount',requestBody,r.status(),body);
  if(r.status()!==400)syntaxBypasses.push({amount,status:r.status(),returnedAmount:body.transaction?.amount});
 }
 for(const amount of ['0.00','1000000000000.00','9'.repeat(1000)+'.00']) {
  const r=await record(retryDraft.id,{...payload,confirmedAmount:amount},key),body=await r.json();capture('recovery numeric bound',{...payload,confirmedAmount:amount},r.status(),body);assert.ok([400,409].includes(r.status()),'numeric replay must be a controlled error, never 500');
 }
 if(syntaxBypasses.length)blockers.push('BE-DRAFT-REPLAY-SYNTAX: canonical regex is applied after trim; malformed same-key replay amounts returned success or wrong error: '+JSON.stringify(syntaxBypasses)+'. Expected HTTP 400 before replay recovery, no writes.');
 assert.equal(await txCounts(),beforeReplayCounts);assert.equal(await fingerprint(),beforeReplayFinancial);
 mark('DRAFT PARITY HTTP: identical recovery, changed amount/version/account/type/date/description conflicts and different-key re-record denial; all replay paths have no extra financial writes');
 // Another draft and an ordinary transaction cannot lend their operation key to this draft.
 const anotherResponse=await create(complete),another=await anotherResponse.json();assert.equal(anotherResponse.status(),201);
 let counts=await txCounts();assert.equal((await record(another.id,{version:another.version,confirmedAmount:'15.55'},key)).status(),409);assert.equal((await read(another.id)).status,'DRAFT');assert.equal(await txCounts(),counts);
 const ordinaryKey=randomUUID(),ordinary=await api(owner.context,'/finance/transactions','POST',{accountId,eventType:'DIRECT_SALE_RECEIPT',amount:'0.01',effectiveDate:today,description:'Ordinary operation key fixture'},{'Idempotency-Key':ordinaryKey});assert.equal(ordinary.status(),201);
 counts=await txCounts();assert.equal((await record(another.id,{version:another.version,confirmedAmount:'0.01'},ordinaryKey)).status(),409);assert.equal(await txCounts(),counts);assert.equal((await read(another.id)).status,'DRAFT');
 mark('DRAFT PARITY HTTP: another draft recording key and ordinary transaction operation key cannot be claimed by an unrecorded draft');
 mark('DRAFT HTTP: six different keys race one draft, one transaction; same-key same-payload recovery checked');
 response=await create({...complete,accountId:'33333333-3333-4333-8333-333333333333',eventType:'OPERATING_EXPENSE_PAYMENT'});assert.equal(response.status(),201);const invalid=await response.json(),beforeReject=await fingerprint();
 const rejected=await record(invalid.id,{version:invalid.version,confirmedAmount:'1.00'});assert.equal(rejected.status(),400);assert.equal((await read(invalid.id)).status,'DRAFT');assert.equal(await fingerprint(),beforeReject);
 mark('DRAFT HTTP: rejected posting preserves version/status and financial fingerprint');
 // Actual browser form; network failures intercept delivery only, never fabricate API data.
 await page.goto(origin+'/records?period=today');await page.getByRole('button',{name:'Add record',exact:true}).click();
 let form=page.getByRole('form',{name:'Add record',exact:true});await form.getByLabel(/^Amount/).fill('1500.');await form.getByLabel(/^Description/).fill('Browser incomplete draft');
 await form.getByRole('button',{name:'Save draft',exact:true}).click();await form.waitFor({state:'detached'});await page.getByText('Saved draft. It has not been recorded.',{exact:true}).waitFor();
 await page.getByRole('link',{name:'To review',exact:true}).first().click();await page.getByRole('heading',{name:'Browser incomplete draft',exact:true}).waitFor();await page.reload();
 let card=page.locator('li').filter({has:page.getByRole('heading',{name:'Browser incomplete draft',exact:true})});await card.getByRole('button',{name:'Continue entry',exact:true}).click();
 let dialog=page.getByRole('dialog',{name:'Continue entry',exact:true});await dialog.getByLabel('Amount (PKR)',{exact:true}).waitFor();assert.equal(await dialog.getByLabel('Amount (PKR)',{exact:true}).inputValue(),'1500.');
 await dialog.getByLabel('Amount (PKR)',{exact:true}).fill('1500.555');await dialog.getByLabel('Account',{exact:true}).selectOption(accountId);await dialog.getByLabel('Entry type',{exact:true}).selectOption('DIRECT_SALE_RECEIPT');await dialog.getByLabel('Effective date',{exact:true}).fill(today);
 assert.equal(await dialog.getByRole('button',{name:'Record saved entry',exact:true}).isDisabled(),true);
 let failSave=true;await page.route(/\/api\/finance\/drafts\/[0-9a-f-]+$/,async route=>{if(route.request().method()==='PUT'&&failSave){failSave=false;await route.abort('connectionfailed');}else await route.continue();});
 await dialog.getByRole('button',{name:'Save draft',exact:true}).click();await dialog.getByText('Draft could not be saved. Your entry is still here. Try again.',{exact:true}).waitFor();assert.equal(await dialog.getByLabel('Amount (PKR)',{exact:true}).inputValue(),'1500.555');
 await page.unroute(/\/api\/finance\/drafts\/[0-9a-f-]+$/);await dialog.getByRole('button',{name:'Save draft',exact:true}).click();await dialog.getByText('Saved draft. It has not been recorded.',{exact:true}).waitFor();
 const drafts=await (await api(owner.context,base+'?size=100')).json();const ui= drafts.items.find(d=>d.description==='Browser incomplete draft');assert.ok(ui);
 await dialog.getByLabel('Description',{exact:true}).fill('My unsaved comparison text');
 response=await api(owner.context,base+'/'+ui.id,'PUT',{version:ui.version,...complete,description:'Newer session text'});assert.equal(response.status(),200);
 await dialog.getByRole('button',{name:'Save draft',exact:true}).click();await dialog.getByText('A newer version exists. Your unsaved text has been kept. Compare it before reloading.',{exact:true}).waitFor();assert.equal(await dialog.getByLabel('Description',{exact:true}).inputValue(),'My unsaved comparison text');
 await dialog.getByRole('button',{name:'Reload newer version',exact:true}).click();await dialog.getByRole('button',{name:'Replace my editing text with this newer version',exact:true}).click();assert.equal(await dialog.getByLabel('Description',{exact:true}).inputValue(),'Newer session text');
 await dialog.getByLabel('Amount (PKR)',{exact:true}).fill('1500.555');await dialog.getByRole('button',{name:'Save draft',exact:true}).click();await dialog.getByText('Saved draft. It has not been recorded.',{exact:true}).waitFor();
 await dialog.getByText('1500.56 PKR',{exact:true}).waitFor();await dialog.getByRole('checkbox').check();
 let lost=true,observed=[];const recordRoute='**/api/finance/drafts/'+ui.id+'/record';await page.route(recordRoute,async route=>{observed.push({key:route.request().headers()['idempotency-key'],body:route.request().postDataJSON()});if(lost){lost=false;await route.fetch();await route.abort('connectionfailed');}else await route.continue();});
 await dialog.getByRole('button',{name:'Record saved entry',exact:true}).click();await dialog.getByText('The recording outcome is uncertain. Keep these values unchanged and explicitly retry the original operation.',{exact:true}).waitFor();await dialog.getByRole('button',{name:'Retry original recording',exact:true}).click();await dialog.getByText('Recorded',{exact:true}).first().waitFor();
 assert.equal(observed.length,2);assert.deepEqual(observed[0],observed[1]);assert.deepEqual(Object.keys(observed[0].body).sort(),['confirmedAmount','version']);assert.equal(observed[0].body.confirmedAmount,'1500.56');
 await page.unroute(recordRoute);await dialog.getByRole('button',{name:'Close entry',exact:true}).last().click();await dialog.waitFor({state:'detached'});
 mark('DRAFT BROWSER: incomplete save/reload/resume; failed-save text retained; stale-version comparison; unsaved recording blocked; exact reviewed version and lost-response same-key recovery');

 const beforeDiscard=await fingerprint();
 await page.getByRole('button',{name:'Start an unfinished entry',exact:true}).click();dialog=page.getByRole('dialog');await dialog.getByLabel('Description',{exact:true}).fill('Browser discard has no financial effects');await dialog.getByRole('button',{name:'Save draft',exact:true}).click();await dialog.getByText('Saved draft. It has not been recorded.',{exact:true}).waitFor();await dialog.getByRole('button',{name:'Discard draft',exact:true}).click();await dialog.getByRole('button',{name:'Yes, discard draft',exact:true}).click();await dialog.getByText('Draft discarded. No financial records were changed.',{exact:true}).waitFor();assert.equal(await dialog.getByRole('button',{name:'Record saved entry',exact:true}).count(),0);await dialog.getByRole('button',{name:'Close entry',exact:true}).last().click();assert.equal(await fingerprint(),beforeDiscard);
 mark('DRAFT BROWSER: discard confirmation reaches immutable terminal state with no account/summary/transaction effects');
 // A separate sign-in/session sees the persisted completed draft.
 response=await create({amountInput:'42.',description:'Resume in another session'});assert.equal(response.status(),201);
 const second=await browser.newContext(),secondPage=await second.newPage();await signin(secondPage,owner.email);await secondPage.goto(origin+'/records?view=review&draftStatus=RECORDED');await secondPage.getByRole('button',{name:'View recorded transaction',exact:true}).first().waitFor();await secondPage.goto(origin+'/records?view=review');const resumeCard=secondPage.locator('li').filter({has:secondPage.getByRole('heading',{name:'Resume in another session',exact:true})});await resumeCard.getByRole('button',{name:'Continue entry',exact:true}).click();assert.equal(await secondPage.getByRole('dialog').getByLabel('Amount (PKR)',{exact:true}).inputValue(),'42.');await second.close();
 for(const member of [viewer,manager]){const c=await browser.newContext(),p=await c.newPage();await signin(p,member.email);await p.goto(origin+'/records?view=review');await p.getByLabel('Draft status',{exact:true}).waitFor();assert.equal(await p.getByRole('button',{name:'Start an unfinished entry',exact:true}).count(),0);await c.close();}
 await page.goto(origin+'/records?view=review');await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'اردو',exact:true}).click();await page.getByRole('link',{name:'جائزے کے لیے',exact:true}).waitFor();await page.getByRole('button',{name:'نامکمل اندراج شروع کریں',exact:true}).click();dialog=page.getByRole('dialog');await dialog.getByLabel('رقم (PKR)',{exact:true}).fill('۱,۵۰۰.۵۵۵');await dialog.getByText('1500.56 PKR',{exact:true}).waitFor();assert.equal(await page.locator('html').getAttribute('dir'),'rtl');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.keyboard.press('Escape');await dialog.getByRole('button',{name:'غیر محفوظ ترامیم چھوڑ دیں',exact:true}).click();await page.getByRole('button',{name:'English',exact:true}).click();await page.setViewportSize({width:1280,height:900});

 response=await create({amountInput:'123.',description:'Business A private draft'});assert.equal(response.status(),201);const privateDraft=await response.json();
 await page.goto(origin+'/records?view=review');const settings=await page.context().newPage();await settings.goto(origin+'/settings');await settings.getByLabel(/^Active business/).waitFor();
 let fetched=false;const privateRoute='**/api/finance/drafts/'+privateDraft.id;await page.route(privateRoute,async route=>{const real=await route.fetch();fetched=true;await new Promise(resolve=>setTimeout(resolve,1500));try{await route.fulfill({response:real});}catch{}});
 await page.locator('li').filter({has:page.getByRole('heading',{name:'Business A private draft',exact:true})}).getByRole('button',{name:'Continue entry',exact:true}).click();
 for(let i=0;i<100&&!fetched;i++)await page.waitForTimeout(50);assert.equal(fetched,true);await settings.getByLabel(/^Active business/).selectOption(businessB);await page.waitForTimeout(1800);assert.equal(await page.getByRole('dialog').count(),0);assert.equal(await page.getByText('Business A private draft',{exact:true}).count(),0);
 await page.unroute(privateRoute);await settings.getByLabel(/^Active business/).selectOption(businessA);await page.getByRole('heading',{name:'Business A private draft',exact:true}).waitFor();await settings.close();
 mark('DRAFT BROWSER: business switch cancels delayed real draft detail and clears prior-business private content');
 mark('DRAFT BROWSER: independent sign-in persistence, MANAGER/VIEWER read-only, Urdu/RTL/mobile natural input and keyboard close choices');
}

export async function verifyReplaySyntax({api,owner,accountId,today,txCounts,mark,requestFactory,readers=[]}) {
 const base='/finance/drafts';
 const entry={accountId,eventType:'DIRECT_SALE_RECEIPT',effectiveDate:today,amountInput:' ۱۵.۵۵. ',description:'Canonical replay syntax fixture'};
 const created=await api(owner.context,base,'POST',entry,{'Idempotency-Key':randomUUID()});assert.equal(created.status(),201);const draft=await created.json();assert.equal(draft.amountInput,entry.amountInput);
 assert.equal((await (await api(owner.context,base+'/'+draft.id)).json()).amountInput,entry.amountInput);
 const key=randomUUID(),payload={version:draft.version,confirmedAmount:'15.55'};
 const send=(body,operationKey=key,who=owner.context)=>api(who,base+'/'+draft.id+'/record','POST',body,{'Idempotency-Key':operationKey});
 const malformed=[null,'',' ',' 15.55 ','\t15.55\t','15.55\n','\r15.55\r','\u000015.55\u0000','\u000115.55','15.55\u001f','\b15.55','\u007f15.55','15.550','1.555e1','+15.55','-15.55','015.55','15.5','15','15,55','NaN','Infinity','۱۵.۵۵','١٥.٥٥','0.00','1000000000000.00','9'.repeat(1000)+'.00'];
 const amounts=malformed;
 const diagnostic=[];
 const rejectAll=async stage=>{
  const before=await txCounts();
  for(const amount of amounts){const body={...payload,confirmedAmount:amount},r=await send(body),data=await r.json();diagnostic.push({stage,request:body,status:r.status(),response:data});assert.equal(r.status(),400,stage+' '+JSON.stringify(amount));assert.equal(typeof data.errors?.confirmedAmount,'string',stage+' must return confirmedAmount field error');}
  assert.equal(await txCounts(),before,stage+' rejects must not add financial writes');
 };
 await rejectAll('initial recording');assert.equal((await (await api(owner.context,base+'/'+draft.id)).json()).status,'DRAFT');
 const success=await send(payload);assert.equal(success.status(),201);const recorded=await success.json();
 await rejectAll('recorded replay');
 const before=await txCounts(),again=await send(payload);assert.equal(again.status(),201);const retried=await again.json();assert.deepEqual(retried.transaction,recorded.transaction);assert.deepEqual(retried.draft,{...recorded.draft,updatedAt:retried.draft.updatedAt});assert.equal(retried.draft.updatedAt,(await (await api(owner.context,base+'/'+draft.id)).json()).updatedAt);
 for(const change of [{confirmedAmount:'16.55'},{version:draft.version+1},{description:'Changed operation'}])assert.equal((await send({...payload,...change})).status(),409);
 assert.equal((await send(payload,randomUUID())).status(),409);assert.equal(await txCounts(),before);
 const anonymous=await requestFactory.newContext();try{assert.equal((await send({...payload,confirmedAmount:' 15.55 '},key,anonymous)).status(),401);}finally{await anonymous.dispose();}
 for(const reader of readers){assert.equal((await send({...payload,confirmedAmount:' 15.55 '},key,reader.context)).status(),403);assert.equal((await send(payload,key,reader.context)).status(),403);}
 // Active tenant B is authorized, but cannot access A's draft. Malformed requests stay generic and disclose no resource state.
 const other=await api(owner.context,'/businesses','POST',{businessName:'Syntax tenant isolation '+randomUUID(),businessType:'retail',languagePreference:'en',whatsappOptIn:false});assert.equal(other.status(),201);const otherId=(await other.json()).businessId;
 await api(owner.context,'/businesses/active','POST',{businessId:otherId});assert.equal((await send(payload)).status(),404);
 const foreignMalformed=await send({...payload,confirmedAmount:' 15.55 '}),missingMalformed=await api(owner.context,base+'/'+randomUUID()+'/record','POST',{...payload,confirmedAmount:' 15.55 '},{'Idempotency-Key':key});assert.equal(foreignMalformed.status(),400);assert.equal(missingMalformed.status(),400);assert.deepEqual((await foreignMalformed.json()).errors,(await missingMalformed.json()).errors);
 await api(owner.context,'/businesses/active','POST',{businessId:recorded.transaction.businessId});
 assert.equal(await txCounts(),before);
 const directory=new URL('../test-artifacts/accounts-live/',import.meta.url);mkdirSync(directory,{recursive:true});writeFileSync(fileURLToPath(new URL('draft-syntax-cases.json',directory)),JSON.stringify(diagnostic,null,2));
 mark('DRAFT SYNTAX: '+amounts.length+' malformed initial amounts and '+amounts.length+' replay amounts reject with 400 confirmedAmount field errors; no extra financial writes');
 mark('DRAFT SYNTAX: canonical retry identical 201, changed canonical payload/version 409, different-key 409; raw amountInput verbatim');
 mark('DRAFT SYNTAX: authentication/role boundary precedes service validation; foreign canonical draft 404 and malformed foreign/missing IDs give identical non-disclosing field errors');
}
