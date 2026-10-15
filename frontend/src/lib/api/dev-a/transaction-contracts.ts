import { accountTypes, isAccountId, isCalendarDate, type AccountType } from './account-contracts';
import { validCurrencyAmount } from '../../utils/decimal-display';
import { postingChoices, isPositivePostingAmount, type PostingChoice, type PostingDraft } from '../../../components/finance/transactions/transaction-input';

export const descriptionLimit = 255;
export type MovementDirection = 'INCREASE' | 'DECREASE';
export interface TransactionSummary {
  id: string; businessId: string; accountId: string; accountName: string; accountType: AccountType;
  eventType: PostingChoice; movementDirection: MovementDirection; amount: string; currency: 'PKR';
  baseAmountPkr: string; effectiveDate: string; description: string | null; resultingBalance: string;
  createdByUserId: string; createdAt: string;
}
export interface TransactionMovement {
  id: string; accountId: string; movementDirection: MovementDirection; amount: string; currency: 'PKR'; effectiveDate: string; createdAt: string;
}
export interface TransactionDetailData extends TransactionSummary { movements: TransactionMovement[] }
export interface TransactionQuery { accountId?: string; eventType?: PostingChoice; startDate?: string; endDate?: string; page?: number; size?: number }
export interface TransactionPage { content: TransactionSummary[]; page: number; size: number; totalElements: number; totalPages: number; effectiveCutoffDate: string; observationAt: string }
export interface TransactionPost { accountId: string; eventType: PostingChoice; amount: string; effectiveDate: string; description?: string }
export class InvalidTransactionResponse extends Error { constructor() { super('Invalid transaction response.'); this.name = 'InvalidTransactionResponse'; } }
function ensure(value: unknown): asserts value { if (!value) throw new InvalidTransactionResponse(); }
function object(value: unknown): Record<string, unknown> { ensure(value !== null && typeof value === 'object' && !Array.isArray(value)); return value as Record<string, unknown>; }
function uuid(value: unknown): value is string { return typeof value === 'string' && isAccountId(value); }
export function isTransactionTimestamp(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/.test(value)
    && isCalendarDate(value.slice(0, 10)) && Number.isFinite(Date.parse(value));
}
const timestampOrder = (value: string) => value.replace(/(?:\.(\d+))?Z$/, (_, fraction) => '.' + (fraction ?? '').padEnd(9, '0') + 'Z');

export function parseTransaction(value: unknown, expectedBusiness?: string): TransactionSummary {
  const t = object(value);
  for (const key of ['id', 'businessId', 'accountId', 'createdByUserId']) ensure(uuid(t[key]));
  ensure(!expectedBusiness || (t.businessId as string).toLowerCase() === expectedBusiness.toLowerCase());
  ensure(typeof t.accountName === 'string' && t.accountName.length >= 1 && t.accountName.length <= 100 && t.accountName.trim() === t.accountName);
  ensure(accountTypes.includes(t.accountType as AccountType) && postingChoices.includes(t.eventType as PostingChoice));
  ensure(t.movementDirection === (t.eventType === 'DIRECT_SALE_RECEIPT' ? 'INCREASE' : 'DECREASE'));
  ensure(t.currency === 'PKR' && isPositivePostingAmount(t.amount) && t.baseAmountPkr === t.amount);
  ensure(validCurrencyAmount(t.resultingBalance, 'PKR', 2));
  ensure(t.accountType !== 'CASH' || !(t.resultingBalance as string).startsWith('-'));
  ensure(isCalendarDate(t.effectiveDate) && isTransactionTimestamp(t.createdAt));
  ensure(t.description === null || typeof t.description === 'string' && t.description.length >= 1 && t.description.length <= descriptionLimit && t.description.trim() === t.description);
  return { id: t.id, businessId: t.businessId, accountId: t.accountId, accountName: t.accountName, accountType: t.accountType,
    eventType: t.eventType, movementDirection: t.movementDirection, amount: t.amount, currency: t.currency, baseAmountPkr: t.baseAmountPkr,
    effectiveDate: t.effectiveDate, description: t.description, resultingBalance: t.resultingBalance,
    createdByUserId: t.createdByUserId, createdAt: t.createdAt } as TransactionSummary;
}
export function parseTransactionDetail(value: unknown, expectedBusiness?: string, expectedId?: string): TransactionDetailData {
  const raw = object(value), t = parseTransaction(raw, expectedBusiness);
  ensure(!expectedId || t.id.toLowerCase() === expectedId.toLowerCase());
  ensure(Array.isArray(raw.movements) && raw.movements.length === 1);
  const m = object(raw.movements[0]);
  ensure(uuid(m.id) && typeof m.accountId === 'string' && m.accountId.toLowerCase() === t.accountId.toLowerCase());
  ensure(m.movementDirection === t.movementDirection && m.amount === t.amount && m.currency === t.currency && m.effectiveDate === t.effectiveDate && isTransactionTimestamp(m.createdAt));
  const movement = { id: m.id, accountId: m.accountId, movementDirection: m.movementDirection, amount: m.amount, currency: m.currency, effectiveDate: m.effectiveDate, createdAt: m.createdAt } as TransactionMovement;
  return { ...t, movements: [movement] };
}
export function parseTransactionPage(value: unknown, query: TransactionQuery, expectedBusiness?: string): TransactionPage {
  const raw = object(value);
  for (const key of ['page', 'size', 'totalElements', 'totalPages']) ensure(Number.isSafeInteger(raw[key]) && (raw[key] as number) >= 0);
  const page = raw.page as number, size = raw.size as number, count = raw.totalElements as number, pages = raw.totalPages as number;
  ensure(page === (query.page ?? 0) && size === (query.size ?? 20) && size >= 1 && size <= 100 && pages === Math.ceil(count / size));
  ensure(isCalendarDate(raw.effectiveCutoffDate) && isTransactionTimestamp(raw.observationAt));
  const observation = new Date(raw.observationAt); observation.setUTCHours(observation.getUTCHours() + 5);
  ensure(observation.toISOString().slice(0, 10) === raw.effectiveCutoffDate);
  ensure(Array.isArray(raw.content));
  const content = raw.content.map(item => parseTransaction(item, expectedBusiness));
  ensure(content.length === (page >= pages ? 0 : Math.min(size, count - page * size)) && new Set(content.map(t => t.id.toLowerCase())).size === content.length);
  for (let i = 0; i < content.length; i++) {
    const t = content[i], previous = content[i - 1];
    ensure(!query.accountId || t.accountId.toLowerCase() === query.accountId.toLowerCase());
    ensure(!query.eventType || t.eventType === query.eventType);
    ensure((!query.startDate || t.effectiveDate >= query.startDate) && (!query.endDate || t.effectiveDate <= query.endDate) && t.effectiveDate <= raw.effectiveCutoffDate);
    ensure(!previous || previous.businessId === t.businessId && (previous.effectiveDate > t.effectiveDate
      || previous.effectiveDate === t.effectiveDate && timestampOrder(previous.createdAt) >= timestampOrder(t.createdAt)));
    // Wire timestamps may hide DB subsecond ties: do not invent UUID tie ordering.
  }
  return { content, page, size, totalElements: count, totalPages: pages, effectiveCutoffDate: raw.effectiveCutoffDate, observationAt: raw.observationAt };
}
export function transactionPostPayload(draft: PostingDraft): TransactionPost {
  if (!isAccountId(draft.selectedAccount) || !postingChoices.includes(draft.event) || !isPositivePostingAmount(draft.amountText)
    || !isCalendarDate(draft.dateText) || draft.descriptionText.length > descriptionLimit || !draft.confirmed) throw new Error('Invalid transaction input.');
  const description = draft.descriptionText.trim();
  return Object.freeze({ accountId: draft.selectedAccount, eventType: draft.event, amount: draft.amountText, effectiveDate: draft.dateText,
    ...(description ? { description } : {}) });
}
