import { apiClient } from '../client';
import { isAccountId, isCalendarDate } from './account-contracts';
import { isTransactionTimestamp, parseTransactionDetail } from './transaction-contracts';
import { postingChoices, isPositivePostingAmount } from '../../../components/finance/transactions/transaction-input';
import type { DraftEntry } from '../../../components/records/draft-entry';
export type DraftStatus = 'DRAFT' | 'RECORDED' | 'DISCARDED';
export interface RecordDraft {
 id: string; accountId: string | null; eventType: DraftEntry['event'] | null; effectiveDate: string | null;
 amountInput: string | null; description: string | null; status: DraftStatus; readyToRecord: boolean; version: number;
 recordedTransactionId: string | null; createdById: string; updatedById: string; createdAt: string; updatedAt: string;
}
export interface DraftPage { items: RecordDraft[]; page: number; size: number; totalElements: number; totalPages: number }
export class InvalidDraftResponse extends Error { constructor() { super('Invalid draft response'); } }
const uuid = (value: unknown): value is string => typeof value === 'string' && isAccountId(value);
const ensure: (condition: unknown) => asserts condition = condition => { if (!condition) throw new InvalidDraftResponse(); };
function object(value: unknown): Record<string, unknown> { ensure(!!value && typeof value === 'object' && !Array.isArray(value)); return value as Record<string, unknown>; }
export function parseDraft(value: unknown, expectedId?: string): RecordDraft {
 const d = object(value);
 for (const key of ['id','createdById','updatedById']) ensure(uuid(d[key]));
 ensure(!expectedId || d.id === expectedId);
 ensure(d.accountId === null || uuid(d.accountId));
 ensure(d.eventType === null || postingChoices.includes(d.eventType as typeof postingChoices[number]));
 ensure(d.effectiveDate === null || isCalendarDate(d.effectiveDate));
 for (const [key, limit] of [['amountInput',100],['description',255]] as const) ensure(d[key] === null || typeof d[key] === 'string' && d[key].length <= limit);
 ensure(['DRAFT','RECORDED','DISCARDED'].includes(d.status as string));
 ensure(typeof d.readyToRecord === 'boolean' && Number.isSafeInteger(d.version) && (d.version as number) >= 0);
 ensure(d.status === 'RECORDED' ? uuid(d.recordedTransactionId) : d.recordedTransactionId === null);
 ensure(isTransactionTimestamp(d.createdAt) && isTransactionTimestamp(d.updatedAt));
 return d as unknown as RecordDraft;
}
export function draftEntry(d: RecordDraft): DraftEntry { return { selectedAccount: d.accountId ?? '', event: d.eventType ?? '', dateText: d.effectiveDate ?? '', amountText: d.amountInput ?? '', descriptionText: d.description ?? '' }; }
export function draftPayload(entry: DraftEntry) {
 if (entry.selectedAccount && !isAccountId(entry.selectedAccount) || entry.event && !postingChoices.includes(entry.event) || entry.dateText && !isCalendarDate(entry.dateText) || entry.amountText.length > 100 || entry.descriptionText.length > 255) throw new Error('Invalid draft input');
 return Object.freeze({ accountId: entry.selectedAccount || null, eventType: entry.event || null, effectiveDate: entry.dateText || null, amountInput: entry.amountText || null, description: entry.descriptionText || null });
}
export function parseDraftPage(value: unknown, page: number, status: DraftStatus | 'ALL'): DraftPage {
 const p = object(value); ensure(p.page === page && p.size === 20 && Array.isArray(p.items));
 ensure(Number.isSafeInteger(p.totalElements) && (p.totalElements as number) >= 0 && p.totalPages === Math.ceil((p.totalElements as number) / 20));
 const items = p.items.map(item => parseDraft(item));
 ensure(items.length === Math.max(0, Math.min(20, (p.totalElements as number) - page * 20)) && new Set(items.map(d => d.id)).size === items.length);
 ensure(status === 'ALL' || items.every(d => d.status === status));
 return { items, page, size: 20, totalElements: p.totalElements as number, totalPages: p.totalPages as number };
}
const path = (id: string) => { if (!isAccountId(id)) throw new Error('Invalid draft ID'); return '/finance/drafts/' + id; };
const versionBody = (version: number) => { if (!Number.isSafeInteger(version) || version < 0) throw new Error('Invalid version'); return {version}; };
const keyHeader = (key: string) => { if (!key.trim() || key.length > 100) throw new Error('Invalid key'); return {'Idempotency-Key': key}; };
export const draftsApi = {
 list: async (page = 0, status: DraftStatus | 'ALL' = 'DRAFT', signal?: AbortSignal) => {
  if (!Number.isSafeInteger(page) || page < 0 || !['DRAFT','RECORDED','DISCARDED','ALL'].includes(status)) throw new Error('Invalid draft filter');
  return parseDraftPage(await apiClient<unknown>('/finance/drafts?' + new URLSearchParams({page:String(page),size:'20',status}),{signal}),page,status);
 },
 detail: async (id: string, signal?: AbortSignal) => parseDraft(await apiClient<unknown>(path(id),{signal}),id),
 save: async (entry: DraftEntry, saved: RecordDraft | null, key: string, signal?: AbortSignal) => {
  const payload=draftPayload(entry);
  const result=parseDraft(await apiClient<unknown>(saved ? path(saved.id) : '/finance/drafts',{
   method:saved?'PUT':'POST',signal,headers:saved?undefined:keyHeader(key),body:JSON.stringify({...payload,...(saved?versionBody(saved.version):{})}),
  }),saved?.id);
  ensure(result.status==='DRAFT' && result.accountId===payload.accountId && result.eventType===payload.eventType && result.effectiveDate===payload.effectiveDate && result.amountInput===payload.amountInput);
  ensure(result.version===(saved?saved.version+1:0));
  return result;
 },
 discard: async (saved: RecordDraft, signal?: AbortSignal) => parseDraft(await apiClient<unknown>(path(saved.id) + '/discard',{method:'POST',signal,body:JSON.stringify(versionBody(saved.version))}),saved.id),
 record: async (id: string, payload: Readonly<{version:number;confirmedAmount:string}>, key: string, signal?: AbortSignal, business?: string) => {
  versionBody(payload.version); if (!isPositivePostingAmount(payload.confirmedAmount)) throw new Error('Invalid confirmed amount');
  const raw = object(await apiClient<unknown>(path(id) + '/record',{method:'POST',signal,headers:keyHeader(key),body:JSON.stringify({version:payload.version,confirmedAmount:payload.confirmedAmount})}));
  const draft = parseDraft(raw.draft,id), transaction = parseTransactionDetail(raw.transaction,business);
  ensure(draft.status === 'RECORDED' && draft.recordedTransactionId === transaction.id && transaction.amount === payload.confirmedAmount);
  ensure(draft.accountId === transaction.accountId && draft.eventType === transaction.eventType && draft.effectiveDate === transaction.effectiveDate);
  return {draft,transaction};
 },
};
