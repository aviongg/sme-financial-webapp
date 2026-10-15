import { accountsApi } from '../../../lib/api/dev-a/accounts';
import { accountStatuses, InvalidAccountResponse, type Account } from '../../../lib/api/dev-a/account-contracts';

/** Exhaust the existing paginated account reads; never assume the first 100 are all accounts. */
export async function loadTransactionAccounts(signal: AbortSignal): Promise<Account[]> {
  const all: Account[] = [];
  for (const status of accountStatuses) {
    const first = await accountsApi.list({ status, page: 0, size: 100 }, signal);
    all.push(...first.items);
    for (let page = 1; page < first.totalPages; page++) all.push(...(await accountsApi.list({ status, page, size: 100 }, signal)).items);
  }
  if (new Set(all.map(a => a.id.toLowerCase())).size !== all.length) throw new InvalidAccountResponse();
  return all;
}
