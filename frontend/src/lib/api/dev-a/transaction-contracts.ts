import { accountTypes, isAccountId, isCalendarDate, type AccountType } from './account-contracts';
import { validCurrencyAmount } from '../../utils/decimal-display';
import { postingChoices, isPositivePostingAmount, type PostingChoice, type PostingDraft } from '../../../components/finance/transactions/transaction-input';

export const descriptionLimit = 255;
export type MovementDirection = 'INCREASE' | 'DECREASE';
export const transactionEventTypes = ['DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT', 'INTERNAL_TRANSFER'] as const;
export type TransactionEventType = typeof transactionEventTypes[number];
export interface TransactionSummary {
  id: string; businessId: string; accountId: string; accountName: string; accountType: AccountType;
  eventType: TransactionEventType; movementDirection: MovementDirection; amount: string; currency: 'PKR';
  baseAmountPkr: string; effectiveDate: string; description: string | null; resultingBalance: string;
  createdByUserId: string; createdAt: string;
  sourceAccountId?: string | null;
  sourceAccountName?: string | null;
  destinationAccountId?: string | null;
  destinationAccountName?: string | null;
  isReversal?: boolean;
  reversedTransactionId?: string | null;
  isReversed?: boolean;
  reversedByTransactionId?: string | null;
  reversalReason?: string | null;
  counterpartyId?: string | null;
  counterpartyName?: string | null;
}
export interface TransactionMovement {
  id: string; accountId: string; movementDirection: MovementDirection; amount: string; currency: 'PKR'; effectiveDate: string; createdAt: string;
}
export interface TransactionDetailData extends TransactionSummary { movements: TransactionMovement[] }
export interface TransactionQuery { accountId?: string; eventType?: TransactionEventType; startDate?: string; endDate?: string; page?: number; size?: number }
export interface TransactionPage { content: TransactionSummary[]; page: number; size: number; totalElements: number; totalPages: number; effectiveCutoffDate: string; observationAt: string }
export interface TransactionPost { accountId: string; eventType: PostingChoice; amount: string; effectiveDate: string; description?: string; counterpartyId?: string | null }
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
  ensure(accountTypes.includes(t.accountType as AccountType) && transactionEventTypes.includes(t.eventType as TransactionEventType));
  const isRev = Boolean(t.isReversal);
  if (t.eventType === 'DIRECT_SALE_RECEIPT') {
    ensure(t.movementDirection === (isRev ? 'DECREASE' : 'INCREASE'));
  } else if (t.eventType === 'OPERATING_EXPENSE_PAYMENT') {
    ensure(t.movementDirection === (isRev ? 'INCREASE' : 'DECREASE'));
  } else if (t.eventType === 'INTERNAL_TRANSFER') {
    ensure(t.movementDirection === 'INCREASE' || t.movementDirection === 'DECREASE');
  } else {
    ensure(false);
  }
  ensure(t.currency === 'PKR' && isPositivePostingAmount(t.amount) && t.baseAmountPkr === t.amount);
  ensure(validCurrencyAmount(t.resultingBalance, 'PKR', 2));
  ensure(t.accountType !== 'CASH' || !(t.resultingBalance as string).startsWith('-'));
  ensure(isCalendarDate(t.effectiveDate) && isTransactionTimestamp(t.createdAt));
  ensure(t.description === null || typeof t.description === 'string' && t.description.length >= 1 && t.description.length <= descriptionLimit && t.description.trim() === t.description);
  if (t.sourceAccountId !== undefined && t.sourceAccountId !== null) ensure(uuid(t.sourceAccountId));
  if (t.sourceAccountName !== undefined && t.sourceAccountName !== null) ensure(typeof t.sourceAccountName === 'string');
  if (t.destinationAccountId !== undefined && t.destinationAccountId !== null) ensure(uuid(t.destinationAccountId));
  if (t.destinationAccountName !== undefined && t.destinationAccountName !== null) ensure(typeof t.destinationAccountName === 'string');
  if (t.isReversal !== undefined) ensure(typeof t.isReversal === 'boolean');
  if (t.reversedTransactionId !== undefined && t.reversedTransactionId !== null) ensure(uuid(t.reversedTransactionId));
  if (t.isReversed !== undefined) ensure(typeof t.isReversed === 'boolean');
  if (t.reversedByTransactionId !== undefined && t.reversedByTransactionId !== null) ensure(uuid(t.reversedByTransactionId));
  if (t.reversalReason !== undefined && t.reversalReason !== null) {
    ensure(typeof t.reversalReason === 'string' && t.reversalReason.length >= 1 && t.reversalReason.length <= descriptionLimit && t.reversalReason.trim() === t.reversalReason);
  }
  if (t.counterpartyId !== undefined && t.counterpartyId !== null) ensure(uuid(t.counterpartyId));
  if (t.counterpartyName !== undefined && t.counterpartyName !== null) ensure(typeof t.counterpartyName === 'string');
  return { id: t.id, businessId: t.businessId, accountId: t.accountId, accountName: t.accountName, accountType: t.accountType,
    eventType: t.eventType, movementDirection: t.movementDirection, amount: t.amount, currency: t.currency, baseAmountPkr: t.baseAmountPkr,
    effectiveDate: t.effectiveDate, description: t.description, resultingBalance: t.resultingBalance,
    createdByUserId: t.createdByUserId, createdAt: t.createdAt,
    ...(t.sourceAccountId !== undefined ? { sourceAccountId: t.sourceAccountId as string | null } : {}),
    ...(t.sourceAccountName !== undefined ? { sourceAccountName: t.sourceAccountName as string | null } : {}),
    ...(t.destinationAccountId !== undefined ? { destinationAccountId: t.destinationAccountId as string | null } : {}),
    ...(t.destinationAccountName !== undefined ? { destinationAccountName: t.destinationAccountName as string | null } : {}),
    ...(t.isReversal !== undefined ? { isReversal: isRev } : {}),
    ...(t.reversedTransactionId !== undefined ? { reversedTransactionId: t.reversedTransactionId as string | null } : {}),
    ...(t.isReversed !== undefined ? { isReversed: Boolean(t.isReversed) } : {}),
    ...(t.reversedByTransactionId !== undefined ? { reversedByTransactionId: t.reversedByTransactionId as string | null } : {}),
    ...(t.reversalReason !== undefined ? { reversalReason: t.reversalReason as string | null } : {}),
    ...(t.counterpartyId !== undefined ? { counterpartyId: t.counterpartyId as string | null } : {}),
    ...(t.counterpartyName !== undefined ? { counterpartyName: t.counterpartyName as string | null } : {}) } as TransactionSummary;
}
export function parseTransactionDetail(value: unknown, expectedBusiness?: string, expectedId?: string): TransactionDetailData {
  const raw = object(value), t = parseTransaction(raw, expectedBusiness);
  ensure(!expectedId || t.id.toLowerCase() === expectedId.toLowerCase());
  ensure(Array.isArray(raw.movements) && (t.eventType === 'INTERNAL_TRANSFER' ? raw.movements.length === 2 : raw.movements.length === 1));
  const movements: TransactionMovement[] = raw.movements.map((item: unknown) => {
    const m = object(item);
    ensure(uuid(m.id) && typeof m.accountId === 'string' && isAccountId(m.accountId));
    ensure((m.movementDirection === 'INCREASE' || m.movementDirection === 'DECREASE')
      && m.amount === t.amount && m.currency === t.currency && m.effectiveDate === t.effectiveDate && isTransactionTimestamp(m.createdAt));
    return {
      id: m.id as string,
      accountId: m.accountId as string,
      movementDirection: m.movementDirection as MovementDirection,
      amount: m.amount as string,
      currency: m.currency as 'PKR',
      effectiveDate: m.effectiveDate as string,
      createdAt: m.createdAt as string
    };
  });
  if (t.eventType === 'INTERNAL_TRANSFER') {
    const decreaseMov = movements.find(m => m.movementDirection === 'DECREASE');
    const increaseMov = movements.find(m => m.movementDirection === 'INCREASE');
    ensure(decreaseMov && increaseMov);
    if (t.isReversal) {
      if (t.sourceAccountId) ensure(increaseMov.accountId.toLowerCase() === t.sourceAccountId.toLowerCase());
      if (t.destinationAccountId) ensure(decreaseMov.accountId.toLowerCase() === t.destinationAccountId.toLowerCase());
    } else {
      if (t.sourceAccountId) ensure(decreaseMov.accountId.toLowerCase() === t.sourceAccountId.toLowerCase());
      if (t.destinationAccountId) ensure(increaseMov.accountId.toLowerCase() === t.destinationAccountId.toLowerCase());
    }
  } else {
    ensure(movements[0].accountId.toLowerCase() === t.accountId.toLowerCase() && movements[0].movementDirection === t.movementDirection);
  }
  return { ...t, movements };
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
    ensure(!query.accountId || t.accountId.toLowerCase() === query.accountId.toLowerCase() || (t.destinationAccountId && t.destinationAccountId.toLowerCase() === query.accountId.toLowerCase()) || (t.sourceAccountId && t.sourceAccountId.toLowerCase() === query.accountId.toLowerCase()));
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
    ...(description ? { description } : {}),
    ...(draft.counterpartyId ? { counterpartyId: draft.counterpartyId } : {}) });
}
