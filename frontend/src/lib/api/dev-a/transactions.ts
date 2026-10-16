import { apiClient } from '../client';
import { isAccountId, isCalendarDate } from './account-contracts';
import { parseTransactionDetail, parseTransactionPage, transactionPostPayload, transactionEventTypes, InvalidTransactionResponse, type TransactionQuery, type TransactionPost } from './transaction-contracts';
import type { PostingDraft } from '../../../components/finance/transactions/transaction-input';

export function transactionQueryString(query: TransactionQuery = {}): string {
  const params = new URLSearchParams();
  if (query.accountId !== undefined) { if (!isAccountId(query.accountId)) throw new Error('Invalid account filter.'); params.set('accountId', query.accountId); }
  if (query.eventType !== undefined) { if (!transactionEventTypes.includes(query.eventType)) throw new Error('Invalid event filter.'); params.set('eventType', query.eventType); }
  for (const key of ['startDate', 'endDate'] as const) if (query[key] !== undefined) { if (!isCalendarDate(query[key])) throw new Error('Invalid date filter.'); params.set(key, query[key]); }
  if (query.startDate && query.endDate && query.startDate > query.endDate) throw new Error('Invalid date range.');
  for (const key of ['page', 'size'] as const) if (query[key] !== undefined) {
    const value = query[key]; if (!Number.isSafeInteger(value) || value < (key === 'page' ? 0 : 1) || key === 'size' && value > 100) throw new Error('Invalid pagination.'); params.set(key, String(value));
  }
  return params.size ? '?' + params.toString() : '';
}
export const transactionsApi = {
  list: async (query: TransactionQuery = {}, signal?: AbortSignal, expectedBusiness?: string) => parseTransactionPage(await apiClient<unknown>('/finance/transactions' + transactionQueryString(query), { signal }), query, expectedBusiness),
  detail: async (id: string, signal?: AbortSignal, expectedBusiness?: string) => {
    if (!isAccountId(id)) throw new Error('Invalid transaction ID.');
    return parseTransactionDetail(await apiClient<unknown>('/finance/transactions/' + id, { signal }), expectedBusiness, id);
  },
  post: async (payload: TransactionPost, key: string, signal?: AbortSignal, expectedBusiness?: string) => {
    if (!key.trim() || key.length > 100) throw new Error('Invalid idempotency key.');
    // Validate and reconstruct a whitelist even when called outside the form.
    const body = transactionPostPayload({ selectedAccount: payload.accountId, event: payload.eventType, amountText: payload.amount,
      dateText: payload.effectiveDate, descriptionText: payload.description ?? '', confirmed: true } as PostingDraft);
    const data = parseTransactionDetail(await apiClient<unknown>('/finance/transactions', { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(body), signal }), expectedBusiness);
    if (data.accountId.toLowerCase() !== body.accountId.toLowerCase() || data.eventType !== body.eventType || data.amount !== body.amount
      || data.effectiveDate !== body.effectiveDate || data.description !== (body.description ?? null)) throw new InvalidTransactionResponse();
    return data;
  },
};
