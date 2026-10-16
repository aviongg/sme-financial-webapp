import { ApiError } from '../../lib/api/client';
import { draftAmountPreview, entryHasChanges, type DraftEntry } from './draft-entry';
import { draftEntry, type RecordDraft } from '../../lib/api/dev-a/drafts';
import { isPositivePostingAmount } from '../finance/transactions/transaction-input';
export function reviewedDraftPayload(saved: RecordDraft, entry: DraftEntry) {
 const amount = draftAmountPreview(entry);
 if (saved.status !== 'DRAFT' || entryHasChanges(entry, draftEntry(saved)) || !amount || !isPositivePostingAmount(amount.canonical) || !entry.selectedAccount || !entry.event || !entry.dateText) throw new Error('Save and review the complete draft first');
 return Object.freeze({version:saved.version,confirmedAmount:amount.canonical});
}
export class DraftRecording<T> {
 phase: 'idle' | 'sending' | 'uncertain' | 'conflict' | 'denied' | 'complete' | 'invalidated' = 'idle';
 private pending: {id:string;payload:Readonly<{version:number;confirmedAmount:string}>;key:string} | null = null;
 private controller: AbortController | null = null;
 constructor(private send: (id:string,payload:Readonly<{version:number;confirmedAmount:string}>,key:string,signal:AbortSignal) => Promise<T>, private key = () => crypto.randomUUID()) {}
 async submit(saved: RecordDraft, entry: DraftEntry): Promise<T> {
  if (!['idle','uncertain'].includes(this.phase)) throw new Error('Resolve original operation');
  const payload = reviewedDraftPayload(saved,entry);
  if (this.pending && (this.pending.id !== saved.id || JSON.stringify(payload) !== JSON.stringify(this.pending.payload))) throw new Error('Resolve original operation');
  this.pending ??= {id:saved.id,payload,key:this.key()};
  const pending=this.pending; this.controller=new AbortController(); this.phase='sending';
  try { const result=await this.send(pending.id,pending.payload,pending.key,this.controller.signal); if (this.phase === ('invalidated' as string)) throw new DOMException('Context changed','AbortError'); this.phase='complete';this.pending=null;return result; }
  catch(error) { if (this.phase !== ('invalidated' as string)) {
   if (error instanceof ApiError && [400,404].includes(error.status)) {this.phase='idle';this.pending=null;}
   else this.phase=error instanceof ApiError && error.status===409 ? 'conflict' : error instanceof ApiError && [401,403].includes(error.status) ? 'denied' : 'uncertain';
  } throw error; }
 }
 invalidate() {this.phase='invalidated';this.controller?.abort();this.pending=null;}
}
