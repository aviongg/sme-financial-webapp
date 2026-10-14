import { apiClient } from '../client';
import { accountStatuses, accountTypes, isAccountId, parseAccountDetail, parseAccountPage, type AccountQuery } from './account-contracts';
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
export const accountsApi = {
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
