// QA evidence driver only. Invoked after the frozen candidate's unchanged 26-check
// product harness, against its freshly migrated PostgreSQL and task-owned servers.
// Real providers are disabled. Only the explicitly labelled Copilot fault case
// intercepts an API response. No production application file is modified.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

export async function independentQA({origin,browser,call,csrf,cookies,itemId,invoiceId,accountId,businessId,today,outDir}) {
  const out=path.join(outDir,'independent-qa');mkdirSync(out,{recursive:true});
  const results=[],http=[],contexts=[];let page;const selected=process.env.FINSIGHT_QA_CASES?process.env.FINSIGHT_QA_CASES.split(',').filter(Boolean):undefined;
  const persist=()=>writeFileSync(path.join(out,'qa-results.json'),JSON.stringify({testedSHA:'71ac4e0e7e74cbfe2f08fe3a049d37cd26b0c7c3',scope:'Real frozen frontend/Spring/disposable PostgreSQL, except labelled Copilot fault injection',selectedCases:selected||'all',results,http},null,2));
  const check=async(id,description,fn)=>{
    if(selected&&!selected.includes(id))return; // Explicit targeted follow-up, never reported as a full run.
    try { const observed=await fn();results.push({id,description,status:'PASS',observed});console.log('QA PASS',id,description); }
    catch(error){results.push({id,description,status:'FAIL',error:String(error),layout:page&&!page.isClosed()?await page.evaluate(()=>({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,dir:document.documentElement.dir,overflow:[...document.querySelectorAll('main *')].map(e=>({tag:e.tagName,box:e.getBoundingClientRect(),text:e.textContent?.slice(0,50)})).filter(e=>e.box.left< -1||e.box.right>innerWidth+1).slice(0,8)})).catch(()=>null):null});console.log('QA FAIL',id,String(error));if(page&&!page.isClosed()){await page.screenshot({path:path.join(out,id.replaceAll('/','-')+'.png'),fullPage:true}).catch(()=>{});writeFileSync(path.join(out,id.replaceAll('/','-')+'.txt'),await page.locator('body').innerText().catch(()=>''));}}
    persist();
    // Isolate later observations from a failed language/business case. This resets
    // QA context only; it cannot turn that case's failed assertion into a pass.
    for(const ctx of contexts){const tab=ctx.pages().find(p=>p.url().startsWith(origin));if(tab)await tab.evaluate(()=>localStorage.setItem('finsight_locale','en')).catch(()=>{});}
    const selectedBusiness=await owner('GET','/businesses/active');if(selectedBusiness.status===200&&selectedBusiness.json.businessId!==businessId)expect(await owner('POST','/businesses/active',{businessId}),200);
  };
  const expect=(r,status)=>{assert.equal(r.status,status,JSON.stringify(r.json));return r.json;};
  const owner=async(method,route,data,headers={})=>{if(method!=='GET')await csrf();const r=await call(method,route,data,headers);http.push({actor:'OWNER',method,route,status:r.status,error:r.json?.error});return r;};
  const context=await browser.newContext({viewport:{width:1280,height:900}});contexts.push(context);await context.addCookies(cookies);page=await context.newPage();
  const request=async(ctx,role,method,route,data,headers={})=>{
    if(method!=='GET'){const c=await ctx.request.get(origin+'/api/auth/csrf');assert.equal(c.status(),200);headers={...headers,'X-XSRF-TOKEN':(await c.json()).token};}
    const response=await ctx.request.fetch(origin+'/api'+route,{method,headers,data});let json=null;try{json=await response.json();}catch{}
    http.push({actor:role,method,route,status:response.status(),error:json?.error});return {status:response.status(),json};
  };
  const actors=new Map([['OWNER',{context,request:(m,r,d,h)=>request(context,'OWNER',m,r,d,h)}]]);
  const financial=async actor=>{
    const tx=expect(await actor.request('GET','/finance/transactions'),200);delete tx.observationAt;
    return {transactions:tx,months:expect(await actor.request('GET','/records/monthly'),200)};
  };
  const failedUpload=async(actor,label)=>{
    const c=await actor.context.request.get(origin+'/api/auth/csrf');
    const response=await actor.context.request.post(origin+'/api/documents/upload',{headers:{'X-XSRF-TOKEN':(await c.json()).token},multipart:{documentTypeHint:'receipt',file:{name:`synthetic-${label}.png`,mimeType:'image/png',buffer:Buffer.concat([Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJvYAAAAASUVORK5CYII=','base64'),Buffer.from('\nQA fixture '+randomUUID())])}}});
    assert.equal(response.status(),201,await response.text());const id=(await response.json()).id;let doc;
    for(let i=0;i<150;i++){doc=expect(await actor.request('GET','/documents/'+id),200);if(doc.processingStatus==='failed')break;await new Promise(r=>setTimeout(r,100));}
    assert.equal(doc.processingStatus,'failed');assert.equal(doc.failureReason,'unavailable');return doc;
  };
  try {
    expect(await owner('POST','/records/monthly/recalculate',{}),200);
    const modules=expect(await owner('GET','/business-configuration'),200);
    const saveModules=async requestedModules=>{const current=expect(await owner('GET','/business-configuration'),200);return expect(await owner('PUT','/business-configuration',{operatingProfile:current.operatingProfile,operatingAnswers:current.operatingAnswers,requestedModules,version:current.version}),200);};
    // Fresh real accounts, invited and accepted through the public membership API.
    for(const role of ['ACCOUNTANT','MANAGER','VIEWER']){
      const ctx=await browser.newContext({viewport:{width:1280,height:900}});contexts.push(ctx);
      const req=(m,r,d,h)=>request(ctx,role,m,r,d,h),email=`qa.${role.toLowerCase()}.${randomUUID()}@finsight.local`,password='Qa-'+randomUUID()+'-Aa1!';
      expect(await req('POST','/auth/register',{email,password,fullName:'Synthetic QA '+role}),201);
      expect(await req('POST','/auth/login',{email,password}),200);
      const membership=expect(await owner('POST','/memberships',{email,role}),201);
      expect(await req('POST',`/invitations/${membership.id}/accept`,{}),204);
      expect(await req('POST','/businesses/active',{businessId}),200);
      assert.equal(expect(await req('GET','/businesses/active'),200).role,role);
      actors.set(role,{context:ctx,request:req});
    }
    const ownerActor=actors.get('OWNER'),accountant=actors.get('ACCOUNTANT');
    let confirmedDoc,billId,foreignId,secondBusiness,foreignAccount;
    await check('DEF-01','ACCOUNTANT failed upload → manual review → durable correction → evidence confirmation; exact financial facts unchanged',async()=>{
      const before=await financial(accountant),doc=await failedUpload(accountant,'accountant');
      assert.equal(doc.manualReviewEligible,true);assert.equal(doc.extractedData,null);
      expect(await accountant.request('POST',`/documents/${doc.id}/confirm`,{targetMonth:today.slice(0,7),confirmedAmount:13.37,targetClassification:'operating_expenses',cashFlowImpact:'cash_outflow'}),400);
      page=await accountant.context.newPage();await page.goto(origin+'/upload/'+doc.id);
      await page.getByTestId('document-manual-review').click();await page.getByTestId('document-manual-entry').waitFor();
      const started=expect(await accountant.request('GET','/documents/'+doc.id),200);const manual=JSON.parse(started.reviewedData);
      assert.equal(manual.entry_mode,'manual');assert.equal(manual.ocr_failure_reason,'unavailable');assert.equal(manual.amount,null);assert.equal(manual.confidence,null);
      expect(await accountant.request('POST',`/documents/${doc.id}/manual-review`,{}),409);
      await page.getByTestId('document-amount').fill('13.37');await page.getByTestId('document-date').fill(today);await page.getByTestId('document-party').fill('Independent synthetic supplier');
      await Promise.all([page.waitForResponse(r=>r.url().endsWith('/documents/'+doc.id)&&r.request().method()==='PATCH'),page.getByRole('button',{name:'Save draft corrections',exact:true}).click()]);
      await page.reload();await page.getByTestId('document-manual-entry').waitFor();assert.equal(await page.getByTestId('document-amount').inputValue(),'13.37');
      await page.getByTestId('document-target-month').fill(today.slice(0,7));await page.getByTestId('document-classification').selectOption('operating_expenses');await page.getByTestId('document-cash-impact').selectOption('cash_outflow');
      await page.getByTestId('review-document').click();const [r]=await Promise.all([page.waitForResponse(r=>r.url().endsWith(`/documents/${doc.id}/confirm`)),page.getByTestId('confirm-document').click()]);assert.equal(r.status(),200);
      confirmedDoc=expect(await accountant.request('GET','/documents/'+doc.id),200);assert.equal(confirmedDoc.processingStatus,'confirmed');assert.equal(confirmedDoc.extractedData,null);assert.equal(confirmedDoc.failureReason,'unavailable');
      assert.equal(JSON.parse(confirmedDoc.reviewedData).entry_mode,'manual');assert.equal(JSON.parse(confirmedDoc.reviewedData).ocr_failure_reason,'unavailable');
      const corrections=expect(await accountant.request('GET',`/documents/${doc.id}/corrections`),200);assert.equal(corrections.length,1);
      assert.equal(JSON.parse(corrections[0].previousData).amount,null);assert.equal(JSON.parse(corrections[0].newData).entry_mode,'manual');
      assert.deepEqual(await financial(accountant),before);
      writeFileSync(path.join(out,'manual-review.json'),JSON.stringify({before,after:await financial(accountant),document:confirmedDoc,corrections},null,2));
      await page.screenshot({path:path.join(out,'accountant-confirmed-evidence.png'),fullPage:true});
      return {documentId:doc.id,extractionProvenance:confirmedDoc.extractionProvenance,financialFactsUnchanged:true};
    });
    // A foreign business/file is created only in this disposable instance.
    secondBusiness=expect(await owner('POST','/businesses',{businessName:'Independent QA foreign business',businessType:'services',languagePreference:'en',whatsappOptIn:false}),201).businessId;
    const foreign=await failedUpload(ownerActor,'foreign');foreignId=foreign.id;foreignAccount=expect(await owner('POST','/finance/accounts',{accountName:'Synthetic foreign QA account',accountType:'BANK',currency:'PKR',openingBalance:'0.00',openingDate:'2026-01-01'},{'Idempotency-Key':randomUUID()}),201).id;
    expect(await owner('POST','/businesses/active',{businessId}),200);
    for(const [role,actor] of actors){
      await check('RBAC-'+role,role+' real API permissions, UI write controls and foreign file isolation',async()=>{
        const docAccess=role==='VIEWER'?403:200;const beforeFinancial=await financial(actor);
        expect(await actor.request('GET','/finance/transactions'),200);expect(await actor.request('GET','/invoices'),200);
        if(confirmedDoc)expect(await actor.request('GET','/documents/'+confirmedDoc.id),docAccess);
        expect(await actor.request('GET','/documents/'+foreignId),role==='VIEWER'?403:404);
        expect(await actor.request('GET',`/documents/${foreignId}/file`),role==='VIEWER'?403:404);
        if(role!=='OWNER')expect(await actor.request('PUT','/business-configuration',{operatingProfile:'SERVICES',operatingAnswers:{},requestedModules:['core_finance'],version:0}),403);
        page=await actor.context.newPage();await page.goto(origin+'/invoices/new');
        if(['OWNER','ACCOUNTANT'].includes(role)){await page.getByTestId('commercial-save-draft').waitFor();}
        else{await page.getByText(/read-only/i).first().waitFor();assert.equal(await page.getByTestId('commercial-save-draft').count(),0);expect(await actor.request('POST','/invoices',{}),403);expect(await actor.request('POST','/inventory/movements/receipts',{}, {'Idempotency-Key':randomUUID()}),403);}
        if(role==='MANAGER'){
          const failed=await failedUpload(actor,'manager');assert.equal(typeof failed.manualReviewEligible,'boolean'); // File/state eligibility is not role permission; UI and mutation authorization are checked below.
await page.goto(origin+'/upload/'+failed.id);await page.getByRole('heading',{name:'Review document',exact:true}).waitFor();assert.equal(await page.getByTestId('document-manual-review').count(),0);
          expect(await actor.request('POST',`/documents/${failed.id}/manual-review`,{}),403);expect(await actor.request('PATCH',`/documents/${failed.id}`,{amount:1}),403);expect(await actor.request('POST',`/documents/${failed.id}/confirm`,{targetMonth:today.slice(0,7),confirmedAmount:1,confirmedDate:today,confirmedParty:'Synthetic manager denied',targetClassification:'none',cashFlowImpact:'none',initialCashBalanceEom:null}),403);
        }
        if(role==='VIEWER'){
          await page.goto(origin+'/scan');await page.getByTestId('module-disabled-guard').waitFor();assert.equal(await page.locator('input[type=file]').count(),0);
          await page.goto(origin+'/records/new');await page.getByTestId('monthly-automation').waitFor();assert.equal(await page.getByTestId('monthly-financing-form').count(),0);assert.equal(await page.getByTestId('monthly-recalculate').count(),0);
          expect(await actor.request('PUT','/records/monthly/financing/'+today.slice(0,7),{loanOutstanding:'1.00',interestExpense:null,financingType:'none',expectedVersion:null}),403);
        }
        const financialWrite=await actor.request('POST','/finance/transactions',{accountId,eventType:'DIRECT_SALE_RECEIPT',amount:'1.00',effectiveDate:today,description:'Explicit authorized role QA fixture'}, {'Idempotency-Key':randomUUID()});
        expect(financialWrite,['OWNER','ACCOUNTANT'].includes(role)?201:403);
        if(['MANAGER','VIEWER'].includes(role))assert.deepEqual(await financial(actor),beforeFinancial);
        return {documentRead:docAccess,foreignDocument:role==='VIEWER'?403:404,financialWrite:financialWrite.status};
      });
    }
    await check('DEF-04-23-ROLES','All four roles: disabled actual inventory/counterparty/invoice/document writes deny; permitted historical reads survive',async()=>{
      await saveModules(['core_finance']);const statuses=[];
      try{
        for(const [role,actor] of actors){
          for(const route of ['/inventory/movements/receipts','/finance/counterparties','/invoices']){const denied=await actor.request('POST',route,{}, {'Idempotency-Key':randomUUID()});expect(denied,403);assert.equal(denied.json.error,'module_disabled');statuses.push({role,route,status:denied.status,error:denied.json.error});}
          expect(await actor.request('GET',`/inventory/items/${itemId}/movements`),200);expect(await actor.request('GET','/invoices'),200);
          if(confirmedDoc){expect(await actor.request('GET',`/documents/${confirmedDoc.id}`),role==='VIEWER'?403:200);const denied=await actor.request('POST',`/documents/${confirmedDoc.id}/manual-review`,{});expect(denied,403);assert.equal(denied.json.error,'module_disabled');}
        }
        page=await context.newPage();for(const route of ['/inventory/stock','/invoices','/finance/accounts','/reports']){await page.goto(origin+route);await page.getByTestId('module-disabled-guard').waitFor();}
      }finally{await saveModules(modules.requestedModules);}
      return statuses;
    });
    await check('SECURITY-CSRF','Missing CSRF and anonymous requests cannot mutate or disclose documents',async()=>{
      const response=await context.request.post(origin+'/api/inventory/movements/receipts',{data:{}});assert.equal(response.status(),403);
      const anonymous=await browser.newContext();contexts.push(anonymous);const r=await anonymous.request.get(origin+'/api/documents/'+(confirmedDoc?.id||foreignId));assert.equal(r.status(),401);
      const before=await financial(ownerActor);const cross=await owner('POST','/finance/transactions',{accountId:foreignAccount,eventType:'DIRECT_SALE_RECEIPT',amount:'1.00',effectiveDate:today,description:'Denied foreign account fixture'},{'Idempotency-Key':randomUUID()});expect(cross,404);assert.deepEqual(await financial(ownerActor),before);return {csrfMissing:response.status(),anonymousDocument:r.status(),foreignAccountWrite:cross.status,financialFactsUnchanged:true};
    });
    await check('DEF-03','Add inventory and reports widgets, save layout, reload and verify persisted product widgets',async()=>{
      page=await context.newPage();await page.goto(origin);await page.getByTestId('workspace-customize').click();await page.getByTestId('workspace-picker').waitFor();
      for(const widget of ['inventory','reports']){const add=page.getByTestId('workspace-add-'+widget);if(await add.count())await add.click();await page.getByTestId('workspace-widget-'+widget).waitFor();}
      const [r]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/workspace/preferences')&&r.request().method()==='PUT'),page.getByTestId('workspace-save').click()]);assert.equal(r.status(),200);
      await page.reload();for(const widget of ['inventory','reports'])await page.getByTestId('workspace-widget-'+widget).waitFor();
      const saved=expect(await owner('GET','/workspace'),200);assert.ok(saved.widgets.some(w=>w.key==='inventory'));assert.ok(saved.widgets.some(w=>w.key==='reports'));
      return {persisted:saved.widgets.map(w=>w.key)};
    });
    await check('DEF-05-BILL','Bill line inventory selector persists and finalized bill receives stock through the real API/UI',async()=>{
      page=await context.newPage();await page.goto(origin+'/bills/new');await page.getByTestId('commercial-counterparty-input').fill('Independent synthetic supplier');await page.getByTestId('commercial-document-date-input').fill(today);
      await page.getByTestId('commercial-line-inventory-item-0').selectOption(itemId);await page.getByTestId('commercial-line-description-0').fill('QA stock purchase');await page.getByTestId('commercial-line-quantity-0').fill('1');await page.getByTestId('commercial-line-unit-price-0').fill('2.00');
      const [saved]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/api/bills')&&r.request().method()==='POST'),page.getByTestId('commercial-save-draft').click()]);assert.equal(saved.status(),201);let bill=await saved.json();billId=bill.id;assert.equal(bill.lines[0].inventoryItemId,itemId);
      bill=expect(await owner('POST',`/bills/${billId}/finalize`,{expectedVersion:bill.version}),200);
      const supplier=expect(await owner('POST','/finance/counterparties',{displayName:'Independent synthetic supplier',type:'SUPPLIER'}),201);
      expect(await owner('POST','/finance/obligations/recognize-commercial-document',{documentId:billId,documentKind:'BILL',counterpartyId:supplier.id,notes:'QA'},{'Idempotency-Key':randomUUID()}),201);
      await page.goto(origin+'/bills/'+billId);await page.getByTestId('lifecycle-receive-stock').click();await page.getByTestId('lifecycle-stock-count').getByText('1',{exact:true}).waitFor();
      if(confirmedDoc){const before=await financial(ownerActor);const linked=expect(await owner('POST',`/commercial/documents/${billId}/evidence`,{uploadedDocumentId:confirmedDoc.id}),200);assert.ok(linked.evidence.some(e=>e.uploadedDocumentId===confirmedDoc.id));assert.deepEqual(await financial(ownerActor),before);}
      return {billId,inventoryItemId:itemId,evidenceDocument:confirmedDoc?.id};
    });
    await check('DEF-05-EDIT','Existing stock-linked invoice draft keeps its inventory link through a browser edit and save',async()=>{
      const draft=expect(await owner('POST','/invoices',{counterpartyName:'Synthetic stock edit',documentDate:today,lines:[{description:'Original synthetic line',quantity:'1',unit:'unit',unitPrice:'2.00',inventoryItemId:itemId}]}),201);
      page=await context.newPage();await page.goto(origin+'/invoices/'+draft.id);await page.getByTestId('commercial-line-inventory-item-0').waitFor();assert.equal(await page.getByTestId('commercial-line-inventory-item-0').inputValue(),itemId);
      await page.getByTestId('commercial-line-description-0').fill('Updated synthetic line');const [response]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/api/invoices/'+draft.id)&&r.request().method()==='PUT'),page.getByTestId('commercial-save-draft').click()]);assert.equal(response.status(),200);assert.equal((await response.json()).lines[0].inventoryItemId,itemId);return {preservedItem:itemId};
    });
    await check('DEF-07','Recognized invoice and bill disclose real financial effect and use lifecycle reversal instead of plain Void',async()=>{
      const observations=[];page=await context.newPage();
      for(const [kind,id] of [['invoices',invoiceId],['bills',billId]]){assert.ok(id);await page.goto(`${origin}/${kind}/${id}`);await page.getByTestId('commercial-void-lifecycle-hint').waitFor();const effect=await page.getByTestId('commercial-financial-effect').innerText();assert.doesNotMatch(effect,/effect: none/i);assert.equal(await page.getByTestId('commercial-void-button').count(),0);observations.push({kind,effect});}
      return observations;
    });
    await check('DEF-09','Financial pack EN/UR renders translated labels and nested aging content; cash-flow page remains responsive',async()=>{
      page=await context.newPage();const observed=[];
      for(const locale of ['en','ur']){
        await page.goto(origin+'/reports');await page.getByTestId('pack-generate').click();await page.getByTestId('pack-section-profit_and_loss').waitFor();
        const text=await page.getByTestId('financial-pack').innerText();assert.doesNotMatch(text,/\bcashSales\b|\binvoiceRevenue\b|\bdays1to30\b|\bgrossMarginPct\b/);assert.match(text,/Brown rice bag/);assert.ok(await page.getByTestId('pack-section-aging').getByRole('table').count()>2);observed.push({locale,nestedAgingAndInventory:true,rawActivityEnum:text.includes('DIRECT_SALE_RECEIPT')});
        if(locale==='en')await Promise.all([page.waitForResponse(r=>r.url().endsWith('/api/profile/language')&&r.request().method()==='PATCH'),page.getByRole('button',{name:'اردو',exact:true}).click()]);
      }
      await page.setViewportSize({width:390,height:600});await page.evaluate(()=>document.fonts.ready.then(()=>true));await page.waitForLoadState('networkidle');writeFileSync(path.join(out,'report-label-observations.json'),JSON.stringify(observed,null,2));assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:path.join(out,'report-urdu-mobile.png'),fullPage:true});
      await Promise.all([page.waitForResponse(r=>r.url().endsWith('/api/profile/language')&&r.request().method()==='PATCH'),page.getByRole('button',{name:'English',exact:true}).click()]);await page.goto(origin+'/finance/forecast');await page.getByRole('heading',{name:/cash.*forecast/i}).first().waitFor();assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
      return observed;
    });
    await check('DEF-09-ENUM','Financial pack cash activity types use user-facing labels instead of raw event enums',async()=>{
      page=await context.newPage();await page.goto(origin+'/reports');await page.getByTestId('pack-generate').click();await page.getByTestId('pack-section-cash').waitFor();
      assert.doesNotMatch(await page.getByTestId('pack-section-cash').innerText(),/\bDIRECT_SALE_RECEIPT\b/,'Remaining raw activity enum is visible in the financial pack');
    });
    await check('CASH-FLOW','Real daily cash forecast reconciles backend starting/ending amounts in EN/UR and mobile without posting',async()=>{
      const before=await financial(ownerActor);const response=expect(await owner('GET','/finance/forecast/daily?horizonDays=30&currency=PKR'),200);
      const money=value=>{const [whole,fraction='']=String(value).split('.');return whole.replace(/\B(?=(\d{3})+(?!\d))/g,',')+'.'+fraction.padEnd(2,'0').slice(0,2);};
      page=await context.newPage();await page.goto(origin+'/finance/forecast');await page.getByRole('heading',{name:'Daily Cash Forecast & Liquidity Simulation',exact:true}).first().waitFor();
      for(const value of [response.summary.startingCash,response.summary.endingProjectedCash])await page.getByText(money(value)+' PKR',{exact:true}).first().waitFor();
      await Promise.all([page.waitForResponse(r=>r.url().endsWith('/api/profile/language')&&r.request().method()==='PATCH'),page.getByRole('button',{name:'اردو',exact:true}).click()]);await page.setViewportSize({width:390,height:600});await page.evaluate(()=>document.fonts.ready.then(()=>true));await page.waitForLoadState('networkidle');
      assert.equal(await page.locator('html').getAttribute('dir'),'rtl');assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));assert.deepEqual(await financial(ownerActor),before);
      return {startingCash:response.summary.startingCash,endingProjectedCash:response.summary.endingProjectedCash,postedFactsUnchanged:true};
    });
    await check('CASH-RISK','Real risk wire response renders severity/counts and every active insight without synthetic API substitution',async()=>{
      const assessment=expect(await owner('GET','/finance/risk/assessment?horizonDays=30&currency=PKR'),200);page=await context.newPage();await page.goto(origin+'/finance/risks');await page.getByTestId('overall-severity-badge').waitFor();
      assert.equal((await page.getByTestId('overall-severity-badge').innerText()).trim().toUpperCase().replaceAll(' ','_'),assessment.summary.highestSeverity);
      for(const level of ['critical','high','medium'])assert.equal((await page.getByTestId('kpi-'+level+'-count').innerText()).trim(),String(assessment.summary[level+'Count']));
      assert.equal(await page.getByTestId('risk-insights-grid').locator('h3').count(),assessment.insights.length);return {severity:assessment.summary.highestSeverity,insights:assessment.insights.length};
    });
    await check('DEF-10','Existing owner can open business setup and create another business through the UI',async()=>{
      page=await context.newPage();await page.goto(origin+'/settings');await page.getByRole('link',{name:/another business/i}).click();await page.getByRole('heading',{name:'Create business',exact:true}).waitFor();
      await page.getByLabel(/^Business name/).fill('Independent QA UI-created business');
      await page.locator('form').getByRole('button',{name:/Create business/i}).click();await page.getByTestId('business-configuration').waitFor();
      const selected=expect(await owner('GET','/businesses/active'),200);assert.equal(selected.businessName,'Independent QA UI-created business');expect(await owner('POST','/businesses/active',{businessId}),200);
      return {createdBusiness:selected.businessId,route:page.url()};
    });
    await check('DEF-11','Cross-tab business change invalidates old detail and clears old-business draft; no prior tenant data survives',async()=>{
      page=await context.newPage();await page.goto(origin+'/invoices/new');await page.getByTestId('commercial-counterparty-input').fill('UNSAVED OLD BUSINESS ONLY');
      const other=await context.newPage();await other.goto(origin+'/settings');await other.getByLabel('Active business').selectOption(secondBusiness);await other.getByLabel('Active business').waitFor();
      await page.bringToFront();await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await page.getByTestId('commercial-counterparty-input').waitFor();
      // Observe the security-boundary clear; do not classify deliberate tenant clearing as a data leak.
      for(let i=0;i<50&&(await page.getByTestId('commercial-counterparty-input').inputValue())!=='';i++)await page.waitForTimeout(100);
      assert.equal(await page.getByTestId('commercial-counterparty-input').inputValue(),'');
      await page.goto(origin+'/invoices/'+invoiceId);await page.getByText(/not found in the active business/i).waitFor();assert.equal(await page.getByTestId('commercial-readonly-counterparty').count(),0);
      expect(await owner('POST','/businesses/active',{businessId}),200);return {draftCleared:true,foreignDetail:'404/not found',unsavedChangeWarning:false};
    });
    await check('DEF-12','Commercial evidence linking offers a selectable uploaded document instead of raw UUID-only entry',async()=>{
      page=await context.newPage();await page.goto(origin+'/invoices/'+invoiceId);await page.getByTestId('commercial-lifecycle-panel').waitFor();
      const raw=page.getByTestId('lifecycle-evidence-id');await raw.waitFor();const tag=await raw.evaluate(e=>e.tagName);assert.equal(tag,'SELECT','Remaining Upload ID input requires a raw UUID');return {tag};
    });
    await check('DEF-13','Document confirmation explicitly explains evidence-only semantics; financial-classification fields remain metadata',async()=>{
      assert.ok(confirmedDoc);page=await context.newPage();await page.goto(origin+'/upload/'+confirmedDoc.id);await page.getByText(/does not change monthly figures/i).waitFor();
      return {evidenceOnlyDisclosure:true,classification:'Metadata; real financial equality checked in DEF-01'};
    });
    await check('DEF-14','Bill relationships navigate reviewed OCR evidence and payables using the correct entity kinds',async()=>{
      assert.ok(billId);page=await context.newPage();await page.goto(`${origin}/operations/relationships?kind=DOCUMENT&id=${billId}`);await page.getByTestId('relationships-table').waitFor();
      const data=expect(await owner('GET',`/operations/relationships/DOCUMENT/${billId}`),200);
      const scan=data.related.find(r=>r.kind==='OCR_EVIDENCE'),obligation=data.related.find(r=>r.kind==='OBLIGATION');assert.ok(scan);assert.ok(obligation);
      const hrefs=await page.getByTestId('relationships-table').locator('a').evaluateAll(a=>a.map(x=>x.getAttribute('href')));
      const observation={data,hrefs,correctReviewedScanHref:'/upload/'+scan.id,correctObligationRoute:'/records/payables'};
      writeFileSync(path.join(out,'relationships.json'),JSON.stringify(observation,null,2));
      assert.ok(hrefs.includes('/upload/'+scan.id),'OCR_EVIDENCE lacks its /upload/[id] review link');assert.ok(hrefs.includes('/records/payables'),'A bill payable must not link to receivables');return observation;
    });
    await check('DEF-14-ITEM','Stock item relationships navigate commercial DOCUMENT rows to invoices/bills, not uploaded-file URLs',async()=>{
      page=await context.newPage();await page.goto(`${origin}/operations/relationships?kind=ITEM&id=${itemId}`);await page.getByTestId('relationships-table').waitFor();
      const data=expect(await owner('GET',`/operations/relationships/ITEM/${itemId}`),200);assert.ok(data.related.some(r=>r.kind==='DOCUMENT'));
      const hrefs=await page.getByTestId('relationships-table').locator('a').evaluateAll(a=>a.map(x=>x.getAttribute('href')));
      writeFileSync(path.join(out,'item-relationships.json'),JSON.stringify({data,hrefs},null,2));assert.ok(hrefs.some(h=>h.startsWith('/invoices/')||h.startsWith('/bills/')),'Commercial DOCUMENT rows are badges without navigation');
    });
    await check('DEF-15','Mobile scan requests the rear camera explicitly',async()=>{
      page=await context.newPage();await page.setViewportSize({width:390,height:600});await page.goto(origin+'/scan');const file=page.locator('input[type=file]');await file.waitFor();assert.equal(await file.getAttribute('capture'),'environment');
    });
    await check('DEF-16','Search initializes the query supplied by navigation',async()=>{
      page=await context.newPage();await page.goto(origin+'/search?q=Alpha');await page.getByLabel('Search query',{exact:false}).waitFor();assert.equal(await page.getByLabel('Search query',{exact:false}).inputValue(),'Alpha');
    });
    await check('DEF-08-WHATSAPP','Outbound WhatsApp settings disclose mock/disabled provider separately from inbound configuration',async()=>{
      page=await context.newPage();await page.goto(origin+'/settings');const heading=page.getByRole('heading',{name:'WhatsApp updates',exact:true});await heading.waitFor();const card=heading.locator('..');const text=await card.innerText();
      writeFileSync(path.join(out,'whatsapp-disclosure.json'),JSON.stringify({runtimeProvider:'mock; scheduler disabled by disposable launcher',outboundCard:text,inbound:expect(await owner('GET','/whatsapp/inbound/link'),200)},null,2));
      assert.match(text,/mock|simulat|not live|provider.*disabled|delivery.*disabled/i,'Outbound card does not identify its mock provider');
    });
    await check('DEF-08-COPILOT','Fault injection: deterministic provider-unavailable response is disclosed per answer',async()=>{
      const ctx=await browser.newContext();contexts.push(ctx);await ctx.addCookies(cookies);
      await ctx.route('**/api/copilot/status',route=>route.fulfill({json:{version:'copilot-v1',moduleEnabled:true,mode:'openai',providerReason:'',tools:['health_evidence']}}));
      const response={version:'copilot-v1',language:'en',answer:'ledgerCashInflow: 1234.00.',evidence:[{id:'health_evidence.ledgerCashInflow',tool:'health_evidence',label:'ledgerCashInflow',value:'1234.00'}],tools:[{name:'health_evidence',status:'OK',message:null}],mode:'deterministic',model:null,reasonCode:'provider_unavailable'};
      await ctx.route('**/api/copilot/ask',route=>route.fulfill({json:response}));page=await ctx.newPage();await page.goto(origin+'/ask-finsight');await page.getByTestId('copilot-question').fill('Explain cash');await page.getByTestId('copilot-send').click();await page.getByTestId('copilot-answer').waitFor();
      const displayed=await page.getByTestId('copilot').innerText();writeFileSync(path.join(out,'copilot-fault.json'),JSON.stringify({scope:'Provider response fault injection only; no OpenAI call',response,displayed},null,2));
      assert.match(displayed,/Offline assistant|fallback|not AI-generated|provider unavailable/i);
    });
    await check('DEF-19','Anonymous browser Back returns from sign-in to public landing',async()=>{
      const ctx=await browser.newContext();contexts.push(ctx);page=await ctx.newPage();await page.goto(origin);await page.locator('[data-landing-root]').waitFor();await page.locator('header').getByRole('link',{name:'Sign In',exact:true}).click();await page.getByRole('heading',{name:'Sign in to FinSight'}).waitFor();await page.goBack();assert.equal(new URL(page.url()).search,'');assert.equal(await page.locator('[data-landing-root]').count(),1);
    });
    await check('DEF-20','Mobile transaction page visibly identifies the active business',async()=>{
      page=await context.newPage();await page.setViewportSize({width:390,height:600});await page.goto(origin+'/invoices/new');await page.getByTestId('commercial-save-draft').waitFor();assert.ok(await page.getByTestId('header-business-name').isVisible());
    });
  } finally {
    persist();for(const ctx of contexts)await ctx.close();
  }
  assert.ok(results.length,'No QA cases executed');if(selected)assert.ok(selected.every(id=>results.some(r=>r.id===id)),'Unknown selected QA case');
  const failed=results.filter(r=>r.status==='FAIL');console.log(`INDEPENDENT QA: ${results.length-failed.length} passed, ${failed.length} failed; report is not a release approval`);
  if(failed.length)throw new Error('Independent QA retains unresolved findings: '+failed.map(r=>r.id).join(', '));
}
