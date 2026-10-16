"use client";
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { useLiveResource } from '@/components/integration/useLiveResource';
import { ApiError } from '@/lib/api/client';
import { draftsApi, draftPayload, draftEntry, type RecordDraft, type DraftStatus } from '@/lib/api/dev-a/drafts';
import { loadTransactionAccounts } from '../finance/transactions/transaction-accounts';
import { canPresentPosting } from '../finance/transactions/transaction-submission';
import { validatePostingInput } from '../finance/transactions/transaction-input';
import { TransactionDetail } from '../finance/transactions/TransactionDetail';
import { Card } from '../ui/Card';
import { Button } from '../ui/Button';
import { Select } from '../ui/Select';
import { Sheet } from '../ui/Sheet';
import { DraftEntryFields } from './DraftEntryFields';
import { draftEn, draftUr } from './draft-copy';
import { emptyDraftEntry, entryHasChanges, missingEntryFields, draftAmountPreview } from './draft-entry';
import { DraftRecording, reviewedDraftPayload } from './draft-recording';

export function DraftsWorkspace() {
 const session=useSession();
 const context=JSON.stringify([session.epoch,session.user?.id,session.business?.businessId,session.business?.role,session.business?.membershipStatus,session.status]);
 if(session.status!=='authenticated') return null;
 return <DraftList key={context} business={session.business?.businessId} writable={canPresentPosting(session.business)} />;
}
function DraftList({business,writable}:{business?:string;writable:boolean}) {
 const {locale,direction}=useLanguage(), copy=locale==='ur'?draftUr:draftEn;
 const params=useSearchParams(), rawPage=Number(params.get('draftPage')??0), page=Number.isSafeInteger(rawPage)&&rawPage>=0?rawPage:0;
 const rawStatus=params.get('draftStatus'), status:DraftStatus|'ALL'=rawStatus==='ALL'||rawStatus==='RECORDED'||rawStatus==='DISCARDED'?rawStatus:'DRAFT';
 const [selected,setSelected]=useState<string|null>(null),[transaction,setTransaction]=useState<string|null>(null);
 const list=useLiveResource(useCallback((signal:AbortSignal)=>draftsApi.list(page,status,signal),[page,status]));
 const accounts=useLiveResource(useCallback((signal:AbortSignal)=>loadTransactionAccounts(signal),[]));
 const change=(nextPage:number,nextStatus=status)=>{const next=new URLSearchParams(window.location.search);next.set('draftPage',String(nextPage));next.set('draftStatus',nextStatus);window.history.replaceState(null,'','/records?'+next);};
 const update=()=>{list.retry();accounts.retry();};
 return <div dir={direction} className="space-y-4 min-w-0">
  <p>{copy.incomplete}</p>
  {writable&&<Button onClick={()=>setSelected('new')}>{copy.newDraft}</Button>}
  <Select label={copy.status} value={status} options={(['DRAFT','RECORDED','DISCARDED','ALL'] as const).map(value=>({value,label:value==='ALL'?copy.all:copy[value]}))} onChange={e=>change(0,e.target.value as DraftStatus|'ALL')}/>
  {list.loading?<p role="status">{copy.loading}</p>:list.error?<Card><p role="alert">{copy.failed}</p><Button onClick={list.retry}>{copy.retry}</Button></Card>:list.data&&<>
   {!list.data.items.length&&<p>{copy.empty}</p>}
   <ul aria-label={copy.toReview} className="grid gap-4 md:grid-cols-2">{list.data.items.map(d=><li key={d.id}><Card className="space-y-3 break-words [overflow-wrap:anywhere]">
    <h2>{d.description|| (d.eventType==='DIRECT_SALE_RECEIPT'?copy.sale:d.eventType==='OPERATING_EXPENSE_PAYMENT'?copy.expense:copy.continueEntry)}</h2><p>{copy[d.status]}</p>
    <p>{copy.updated}: <bdi dir="ltr">{d.updatedAt}</bdi></p>
    {d.status==='DRAFT'&&missingEntryFields(draftEntry(d)).length>0&&<p>{copy.missing}: {missingEntryFields(draftEntry(d)).map(field=>({selectedAccount:copy.account,event:copy.event,amountText:copy.amount,dateText:copy.date}[field])).join(' · ')}</p>}
    {d.amountInput!==null&&<p>{copy.amount}: <bdi dir="ltr">{d.amountInput}</bdi></p>}
    <Button variant="secondary" onClick={()=>setSelected(d.id)}>{copy.continueEntry}</Button>
    {d.recordedTransactionId&&<Button variant="secondary" onClick={()=>setTransaction(d.recordedTransactionId)}>{copy.viewTransaction}</Button>}
   </Card></li>)}</ul>
   <nav aria-label={copy.page} className="flex flex-wrap gap-3"><Button variant="secondary" disabled={page===0} onClick={()=>change(page-1)}>{copy.previous}</Button><bdi dir="ltr">{page+1}</bdi><Button variant="secondary" disabled={page+1>=list.data.totalPages} onClick={()=>change(page+1)}>{copy.next}</Button></nav>
  </>}
  {selected&&<DraftEditor key={selected} id={selected} business={business} writable={writable} accounts={accounts.data??[]} onClose={()=>setSelected(null)} onChanged={update} onTransaction={setTransaction}/>}
  {transaction&&<TransactionDetail id={transaction} onClose={()=>setTransaction(null)}/>}
 </div>;
}
function DraftEditor({id,business,writable,accounts,onClose,onChanged,onTransaction}:{id:string;business?:string;writable:boolean;accounts:Awaited<ReturnType<typeof loadTransactionAccounts>>;onClose:()=>void;onChanged:()=>void;onTransaction:(id:string)=>void}) {
 const {locale}=useLanguage(),copy=locale==='ur'?draftUr:draftEn;
 const [saved,setSaved]=useState<RecordDraft|null>(null),[entry,setEntry]=useState(emptyDraftEntry),[loading,setLoading]=useState(id!=='new');
 const [saveUncertain,setSaveUncertain]=useState(false);
 const [busy,setBusy]=useState(false),[message,setMessage]=useState<keyof typeof copy|null>(null),[conflict,setConflict]=useState(false),[newer,setNewer]=useState<RecordDraft|null>(null);
 const [closeChoice,setCloseChoice]=useState(false),[discardChoice,setDiscardChoice]=useState(false),[confirmed,setConfirmed]=useState(false),[phase,setPhase]=useState('idle');
 const active=useRef(true), controller=useRef<AbortController|null>(null), host=useRef<HTMLDivElement>(null);
 const saveKey=useRef<string|null>(null), saving=useRef(false);
 const recording=useRef<DraftRecording<Awaited<ReturnType<typeof draftsApi.record>>>|null>(null);
 useEffect(()=>{
  active.current=true;const c=new AbortController();controller.current=c;
  recording.current=new DraftRecording((draftId,payload,key,signal)=>draftsApi.record(draftId,payload,key,signal,business));
  if(id!=='new') draftsApi.detail(id,c.signal).then(d=>{if(active.current&&!c.signal.aborted){setSaved(d);setEntry(draftEntry(d));setLoading(false);}}).catch(()=>{if(active.current&&!c.signal.aborted){setMessage('failed');setLoading(false);}});
  return()=>{active.current=false;controller.current?.abort();recording.current?.invalidate();};
 },[id,business]);
 useEffect(()=>{
  const dialog=host.current?.querySelector<HTMLElement>('[role="dialog"]');if(!dialog)return;
  dialog.setAttribute('aria-label',copy.continueEntry);const close=dialog.querySelector<HTMLButtonElement>('button');close?.setAttribute('aria-label',copy.close);
  const panel=dialog.querySelector<HTMLElement>('.relative');if(panel){panel.style.maxHeight='90dvh';panel.style.overflowY='auto';}
  const previous=document.activeElement instanceof HTMLElement?document.activeElement:null;close?.focus();
  const trap=(e:KeyboardEvent)=>{if(e.key!=='Tab')return;const nodes=Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled),a[href],input:not(:disabled),select:not(:disabled)')).filter(n=>!n.closest('fieldset:disabled'));const first=nodes[0],last=nodes.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}};
  window.addEventListener('keydown',trap,true);return()=>{window.removeEventListener('keydown',trap,true);if(previous?.isConnected)previous.focus();};
 },[copy.continueEntry,copy.close]);
 const dirty=entryHasChanges(entry,saved?draftEntry(saved):emptyDraftEntry()), terminal=saved!==null&&saved.status!=='DRAFT';
 const locked=busy||saveUncertain||['uncertain','conflict','denied','complete'].includes(phase);
 const close=()=>{if(saving.current||locked)return;if(dirty){setCloseChoice(true);return;}onClose();};
 const save=async(closeAfter=false)=>{
  if(!writable||saving.current||busy||['uncertain','conflict','denied','complete'].includes(phase)||terminal||conflict||(id!=='new'&&!saved))return;
  try {draftPayload(entry);}catch{setMessage('invalid');return;}
  saving.current=true;setBusy(true);setMessage(null);controller.current?.abort();const c=new AbortController();controller.current=c;
  saveKey.current??=crypto.randomUUID();
  try{const d=await draftsApi.save(entry,saved,saveKey.current,c.signal);if(!active.current||c.signal.aborted)return;
   setSaved(d);setEntry(draftEntry(d));saveKey.current=null;setSaveUncertain(false);setConfirmed(false);setMessage('savedNotice');setCloseChoice(false);onChanged();if(closeAfter)onClose();
  }catch(error){if(!active.current||c.signal.aborted)return;setMessage(error instanceof ApiError&&error.status===409?'conflict':error instanceof ApiError&&[401,403].includes(error.status)?'denied':'saveFailed');if(error instanceof ApiError&&error.status===409)setConflict(true);if(!(error instanceof ApiError)||error.status>=500)setSaveUncertain(true);}
  finally{saving.current=false;if(active.current&&!c.signal.aborted)setBusy(false);}
 };
 const reload=async()=>{if(!saved||saving.current)return;controller.current?.abort();const c=new AbortController();controller.current=c;try{const d=await draftsApi.detail(saved.id,c.signal);if(active.current&&!c.signal.aborted)setNewer(d);}catch{if(active.current&&!c.signal.aborted)setMessage('failed');}};
 const record=async()=>{
  const operation=recording.current;if(!saved||!operation||!writable||saving.current||!confirmed||conflict||terminal)return;
  try{const payload=reviewedDraftPayload(saved,entry);const validation=validatePostingInput({...entry,event:entry.event as 'DIRECT_SALE_RECEIPT'|'OPERATING_EXPENSE_PAYMENT',amountText:payload.confirmedAmount,confirmed:true},accounts.find(a=>a.id===entry.selectedAccount),255);
   if(Object.keys(validation).length){setMessage('invalid');return;}
   saving.current=true;setBusy(true);const pending=operation.submit(saved,entry);setPhase(operation.phase);const result=await pending;
   if(!active.current)return;setSaved(result.draft);setEntry(draftEntry(result.draft));setMessage('recorded');setPhase(operation.phase);onChanged();
  }catch{if(!active.current)return;setPhase(operation.phase);if(operation.phase==='conflict'){setConflict(true);setMessage('conflict');}else setMessage(operation.phase==='uncertain'?'uncertain':operation.phase==='denied'?'denied':'invalid');}
  finally{saving.current=false;if(active.current)setBusy(false);}
 };
 const discard=async()=>{if(!saved||!writable||saving.current||locked||conflict||terminal)return;saving.current=true;setBusy(true);controller.current?.abort();const c=new AbortController();controller.current=c;
  try{const d=await draftsApi.discard(saved,c.signal);if(!active.current||c.signal.aborted)return;setSaved(d);setEntry(draftEntry(d));setDiscardChoice(false);setMessage('discarded');onChanged();}
  catch(error){if(active.current&&!c.signal.aborted){setMessage(error instanceof ApiError&&error.status===409?'conflict':'saveFailed');if(error instanceof ApiError&&error.status===409)setConflict(true);}}
  finally{saving.current=false;if(active.current&&!c.signal.aborted)setBusy(false);}
 };
 const preview=draftAmountPreview(entry);
 return <div ref={host}><Sheet isOpen onClose={close} title={copy.continueEntry}><div className="space-y-4 min-w-0">
  {loading?<p role="status">{copy.loading}</p>:<>
   <DraftEntryFields entry={entry} accounts={accounts.filter(a=>a.currency==='PKR')} disabled={!writable||locked||terminal} onChange={patch=>{setEntry(previous=>({...previous,...patch}));saveKey.current=null;setConfirmed(false);setMessage(null);}}/>
   {saved&&<p>{copy[saved.status]} · <bdi dir="ltr">{saved.updatedAt}</bdi></p>}
   {dirty&&<p>{copy.saveFirst}</p>}
   {saveUncertain&&<p role="alert">{copy.saveUncertain}</p>}
   {message&&<p role={message==='savedNotice'||message==='recorded'||message==='discarded'?'status':'alert'}>{copy[message]}</p>}
   {conflict&&<><Button variant="secondary" onClick={()=>void reload()}>{copy.reload}</Button>{newer&&<div className="space-y-3"><h3>{copy.comparison}</h3><DraftEntryFields entry={draftEntry(newer)} accounts={accounts} disabled onChange={()=>{}}/><Button onClick={()=>{setSaved(newer);setEntry(draftEntry(newer));setNewer(null);setConflict(false);setPhase('idle');setConfirmed(false);setMessage(null);}}>{copy.replace}</Button></div>}</>}
   {writable&&!terminal&&<>
    <label className="flex gap-3"><input type="checkbox" checked={confirmed} disabled={busy||dirty||conflict||!saved||!preview||phase!=='idle'} onChange={e=>setConfirmed(e.target.checked)}/>{copy.confirm}</label>
    <div className="flex flex-wrap gap-3"><Button disabled={busy||phase!=='idle'||conflict||(id!=='new'&&!saved)} onClick={()=>void save()}>{copy.save}</Button><Button disabled={busy||dirty||conflict||!saved||!confirmed||!['idle','uncertain'].includes(phase)} onClick={()=>void record()}>{phase==='uncertain'?copy.retryOriginal:copy.record}</Button>{saved&&<Button variant="secondary" disabled={locked||conflict} onClick={()=>setDiscardChoice(true)}>{copy.discard}</Button>}</div>
   </>}
   {saved?.recordedTransactionId&&<Button onClick={()=>{const transaction=saved.recordedTransactionId!;onClose();onTransaction(transaction);}}>{copy.viewTransaction}</Button>}
   {discardChoice&&<div role="group" aria-label={copy.discardConfirm}><p>{copy.discardConfirm}</p><Button disabled={busy} onClick={()=>void discard()}>{copy.yesDiscard}</Button><Button variant="secondary" onClick={()=>setDiscardChoice(false)}>{copy.cancel}</Button></div>}
   {closeChoice&&<div role="group" aria-label={copy.unsaved}><p>{copy.unsaved}</p><div className="flex flex-wrap gap-3"><Button disabled={busy||conflict} onClick={()=>void save(true)}>{copy.save}</Button><Button variant="secondary" onClick={onClose}>{copy.discardEdits}</Button><Button variant="secondary" onClick={()=>setCloseChoice(false)}>{copy.keepEditing}</Button></div></div>}
   <Button variant="secondary" disabled={busy||phase==='uncertain'} onClick={()=>{if(terminal||phase==='complete')onClose();else close();}}>{copy.close}</Button>
  </>}
 </div></Sheet></div>;
}
