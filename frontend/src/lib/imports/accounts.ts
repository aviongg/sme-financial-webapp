import type { Account, AccountPage } from '../api/dev-a/account-contracts';
export async function loadImportAccounts(read: (page: number, signal: AbortSignal) => Promise<AccountPage>, signal: AbortSignal): Promise<Account[]> {
 const result: Account[] = [], ids = new Set<string>();
 for (let page = 0; page < 10; page++) {
  signal.throwIfAborted(); const response = await read(page, signal); signal.throwIfAborted();
  if (response.page !== page || response.totalPages > 10) throw new Error('Account list exceeds preview selection limit');
  for (const account of response.items) { if (ids.has(account.id)) throw new Error('Duplicate account response'); ids.add(account.id); result.push(account); }
  if (page + 1 >= response.totalPages) return result;
 }
 throw new Error('Account selection unavailable');
}
