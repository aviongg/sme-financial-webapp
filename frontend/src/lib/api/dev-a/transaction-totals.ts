import { apiClient } from '../client';
import { isAccountId, isCalendarDate } from './account-contracts';
import { transactionQueryString } from './transactions';
import { isTransactionTimestamp, InvalidTransactionResponse, type TransactionQuery } from './transaction-contracts';
export interface PeriodTotals {
 currency: 'PKR'; startDate: string | null; endDate: string | null; accountId: string | null;
 eventType: TransactionQuery['eventType'] | null; transactionCount: number;
 moneyReceived: string; moneyPaid: string; netCashMovement: string; observationAt: string;
}
export function summaryQuery(query: TransactionQuery): TransactionQuery {
 return { ...(query.accountId ? {accountId: query.accountId} : {}), ...(query.eventType ? {eventType: query.eventType} : {}),
  ...(query.startDate ? {startDate: query.startDate} : {}), ...(query.endDate ? {endDate: query.endDate} : {}) };
}
export function parsePeriodTotals(value: unknown, query: TransactionQuery): PeriodTotals {
 const bad = () => { throw new InvalidTransactionResponse(); };
 if (!value || typeof value !== 'object' || Array.isArray(value)) return bad();
 const t = value as Record<string, unknown>, q = summaryQuery(query);
 if (t.currency !== 'PKR' || !Number.isSafeInteger(t.transactionCount) || (t.transactionCount as number) < 0 || !isTransactionTimestamp(t.observationAt) || (t.observationAt as string).includes('.')) return bad();
 for (const key of ['startDate','endDate','accountId','eventType'] as const) if (t[key] !== (q[key] ?? null)) return bad();
 if (t.accountId !== null && (typeof t.accountId !== 'string' || !isAccountId(t.accountId))) return bad();
 for (const key of ['startDate','endDate'] as const) if (t[key] !== null && !isCalendarDate(t[key])) return bad();
 // Summary contract has no per-account 12-digit capacity: sums are independent server values.
 for (const key of ['moneyReceived','moneyPaid','netCashMovement']) {
  const amount = t[key]; if (typeof amount !== 'string' || !/^-?(0|[1-9][0-9]*)\.[0-9]{2}$/.test(amount) || amount === '-0.00' || key !== 'netCashMovement' && amount.startsWith('-')) return bad();
 }
 return { currency: 'PKR', startDate: t.startDate, endDate: t.endDate, accountId: t.accountId, eventType: t.eventType,
 transactionCount: t.transactionCount, moneyReceived: t.moneyReceived, moneyPaid: t.moneyPaid, netCashMovement: t.netCashMovement, observationAt: t.observationAt } as PeriodTotals;
}
export async function loadPeriodTotals(query: TransactionQuery, signal?: AbortSignal): Promise<PeriodTotals> {
 const q = summaryQuery(query); return parsePeriodTotals(await apiClient<unknown>('/finance/transactions/summary' + transactionQueryString(q), {signal}), q);
}
export function formatPeriodAmount(value: string): string {
 const negative = value.startsWith('-'), [integer, fraction] = (negative ? value.slice(1) : value).split('.');
 return (negative ? '- ' : '') + integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + fraction;
}
