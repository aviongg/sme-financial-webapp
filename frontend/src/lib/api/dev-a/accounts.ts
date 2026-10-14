import { apiClient } from '../client';
import { accountStatuses, accountTypes, isAccountId, parseAccount, parseAccountDetail, parseAccountPage, InvalidAccountResponse, type AccountQuery } from './account-contracts';
import { isSupportedCurrency } from '../../utils/decimal-display';

export function accountQueryString(query: AccountQuery = {}): string {
  const params = new URLSearchParams();
  if (query.status !== undefined) {
    if (!accountStatuses.includes(query.status)) throw new Error('Invalid account status filter.');
    params.set('status', query.status);
  }
  if (query.accountType !== undefined) {
    if (!accountTypes.includes(query.accountType)) throw new Error('Invalid account type filter.');
    params.set('accountType', query.accountType);
  }
  if (query.currency !== undefined) {
    const currency = query.currency.toUpperCase();
    if (!isSupportedCurrency(currency)) throw new Error('Invalid account currency filter.');
    params.set('currency', currency);
  }
  for (const key of ['page', 'size'] as const) {
    const value = query[key];
    if (value === undefined) continue;
    if (!Number.isSafeInteger(value) || value < (key === 'page' ? 0 : 1) || key === 'size' && value > 100) throw new Error('Invalid account pagination.');
    params.set(key, String(value));
  }
  const serialized = params.toString();
  return serialized ? '?' + serialized : '';
}
import type { OpeningSetupDraft } from './account-create-contracts';
export const accountsApi = {
  create: async (draft: OpeningSetupDraft, idempotencyKey: string, signal?: AbortSignal) => {
    if (!idempotencyKey.trim() || idempotencyKey.length > 100) throw new Error('Invalid idempotency key.');
    const { accountCreatePayload } = await import('./account-create-contracts');
    const payload = accountCreatePayload(draft), body = JSON.stringify(payload);
    const data = await apiClient<unknown>('/finance/accounts', { method: 'POST', signal,
      headers: { 'Idempotency-Key': idempotencyKey }, body });
    const account = parseAccount(data);
    const detail = parseAccountDetail(data, account.id);
    if (detail.balanceStatus !== 'AVAILABLE' || detail.status !== 'ACTIVE'
      || detail.accountName !== payload.accountName || detail.accountType !== payload.accountType
      || detail.currency !== payload.currency || detail.openingBalance !== payload.openingBalance
      || detail.openingDate !== payload.openingDate
      || detail.openingDate <= detail.effectiveCutoffDate && detail.currentBalance !== detail.openingBalance) throw new InvalidAccountResponse();
    return detail;
  },
  list: async (query: AccountQuery = {}, signal?: AbortSignal) => {
    const suffix = accountQueryString(query);
    const data = await apiClient<unknown>('/finance/accounts' + suffix, { signal });
    return parseAccountPage(data, query);
  },
  detail: async (id: string, signal?: AbortSignal) => {
    if (!isAccountId(id)) throw new Error('Invalid account reference.');
    return parseAccountDetail(await apiClient<unknown>('/finance/accounts/' + id, { signal }), id);
  },
};
