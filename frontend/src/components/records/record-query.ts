import type { AccountQuery, AccountStatus, AccountType } from '../../lib/api/dev-a/account-contracts';
import type { TransactionQuery } from '../../lib/api/dev-a/transaction-contracts';
import { periodDates, type RecordPeriod } from './record-period';
export function accountQueryFromURL(params: URLSearchParams, embedded: boolean): AccountQuery {
 const key = (field: string) => embedded ? 'money' + field : field;
 return {status: (params.has(key('status')) ? params.get(key('status')) || undefined : 'ACTIVE') as AccountStatus | undefined,
 page: Number(params.get(key('page')) ?? 0), size: Number(params.get(key('size')) ?? 50),
 ...(params.get(key('accountType')) ? {accountType: params.get(key('accountType')) as AccountType} : {}),
 ...(params.get(key('currency')) ? {currency: params.get(key('currency'))!} : {})};
}
export function periodFromURL(params: URLSearchParams, embedded: boolean): RecordPeriod {
 return (['today','week','month','custom'].includes(params.get('period') ?? '') ? params.get('period') : embedded ? 'today' : 'custom') as RecordPeriod;
}
export function transactionQueryFromURL(params: URLSearchParams, embedded: boolean): TransactionQuery {
 const period = periodFromURL(params, embedded);
 return {page: Number(params.get('page') ?? 0), size: Number(params.get('size') ?? 20),
 ...(params.get('accountId') ? {accountId: params.get('accountId')!} : {}),
 ...(params.get('eventType') ? {eventType: params.get('eventType') as TransactionQuery['eventType']} : {}),
 ...(params.get('startDate') ? {startDate: params.get('startDate')!} : {}), ...(params.get('endDate') ? {endDate: params.get('endDate')!} : {}),
 ...(period !== 'custom' ? periodDates(period) : {})};
}
export function accountSearch(params: URLSearchParams, update: AccountQuery | ((previous: AccountQuery) => AccountQuery), embedded: boolean): URLSearchParams {
 const search = new URLSearchParams(params), previous = accountQueryFromURL(search,embedded), next = typeof update === 'function' ? update(previous) : update;
 for (const field of ['status','page','size','accountType','currency'] as const) {const key=embedded?'money'+field:field;
  if(next[field]===undefined&&field!=='status')search.delete(key);else search.set(key,String(next[field]??''));}
 return search;
}
export function transactionSearch(params: URLSearchParams, update: TransactionQuery | ((previous: TransactionQuery) => TransactionQuery), embedded: boolean, period?: RecordPeriod): URLSearchParams {
 const search=new URLSearchParams(params),previous=transactionQueryFromURL(search,embedded),next=typeof update==='function'?update(previous):update;
 for(const field of ['page','size','accountId','eventType','startDate','endDate'] as const){if(next[field]===undefined)search.delete(field);else search.set(field,String(next[field]));}
 if(period)search.set('period',period);return search;
}
