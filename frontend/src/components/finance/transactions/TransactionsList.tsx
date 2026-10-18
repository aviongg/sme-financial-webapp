"use client";
import { draftEn, draftUr } from '../../records/draft-copy';
import { transactionQueryFromURL, transactionSearch, periodFromURL } from '../../records/record-query';
import { useSearchParams, usePathname } from 'next/navigation';
import { periodDates, type RecordPeriod } from '../../records/record-period';
import { recordsEn, recordsUr } from '../../records/records-copy';
import { loadPeriodTotals, formatPeriodAmount } from '@/lib/api/dev-a/transaction-totals';
import { useCallback, useMemo, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { transactionsApi, transactionQueryString } from '@/lib/api/dev-a/transactions';
import type { TransactionDetailData, TransactionQuery } from '@/lib/api/dev-a/transaction-contracts';
import { LivePage } from '@/components/integration/LivePage';
import { useLiveResource } from '@/components/integration/useLiveResource';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { Input } from '@/components/ui/Input';
import { AccountMoneyValue } from '../accounts/AccountMoneyValue';
import { accountEn, accountUr } from '../accounts/account-copy';
import { transactionEn, transactionUr, transactionErrorKey } from './transaction-copy';
import { canPresentPosting } from './transaction-submission';
import { loadTransactionAccounts } from './transaction-accounts';
import { PostTransactionForm } from './PostTransactionForm';
import { PostTransferForm } from '../transfers/PostTransferForm';
import { TransactionDetail } from './TransactionDetail';
import { transactionEventTypes } from '@/lib/api/dev-a/transaction-contracts';

const EmbeddedFrame = ({ children }: { children?: React.ReactNode; title?: string }) => <>{children}</>;

export function TransactionsList({ embedded = false, scanHref }: { embedded?: boolean; scanHref?: string }) {
  const { locale, direction } = useLanguage(), session = useSession(), business = session.business?.businessId;
  const copy = locale === 'ur' ? transactionUr : transactionEn, moneyCopy = locale === 'ur' ? accountUr : accountEn;
  const context = JSON.stringify([session.epoch, session.user?.id, business, session.business?.role, session.business?.membershipStatus, session.status]);
  const params = useSearchParams(), pathname = usePathname();
  const recordsCopy = locale === 'ur' ? recordsUr : recordsEn;
  const period = periodFromURL(new URLSearchParams(params.toString()), embedded);
  const query = useMemo(() => transactionQueryFromURL(new URLSearchParams(params.toString()),embedded), [params,embedded]);
  const setQuery = (update: TransactionQuery | ((previous: TransactionQuery) => TransactionQuery), selectedPeriod?: RecordPeriod) => {
    const search = transactionSearch(new URLSearchParams(window.location.search),update,embedded,selectedPeriod);
    window.history.replaceState(null, '', pathname + '?' + search.toString());
  };
  const [draftSaved,setDraftSaved]=useState<string|null>(null);
  const [refresh, setRefresh] = useState(0), [posting, setPosting] = useState<string | null>(null), [transferring, setTransferring] = useState<string | null>(null), [selected, setSelected] = useState<string | null>(null);
  const [posted, setPosted] = useState<{ context: string; transaction: TransactionDetailData } | null>(null);
  const addRef = useRef<HTMLButtonElement>(null), close = useCallback(() => setSelected(null), []);
  const restoreFocus = useRef(false);
  const accountKey = context + ':' + refresh, queryKey = accountKey + ':' + JSON.stringify(query);
  const accountsResource = useLiveResource(useCallback(async (signal: AbortSignal) => {
    try { return { key: accountKey, accounts: await loadTransactionAccounts(signal), error: null }; }
    catch (error) { if (error instanceof Error && error.name === 'AbortError') throw error; return { key: accountKey, accounts: null, error: transactionErrorKey(error) }; }
  }, [accountKey]));
  const history = useLiveResource(useCallback(async (signal: AbortSignal) => {
    try { transactionQueryString(query); }
    catch { return { key: queryKey, page: null, error: 'invalidFilters' as const }; }
    try { return { key: queryKey, page: await transactionsApi.list(query, signal, business), error: null }; }
    catch (error) { if (error instanceof Error && error.name === 'AbortError') throw error; return { key: queryKey, page: null, error: transactionErrorKey(error) }; }
  }, [query, queryKey, business]));
  const totals = useLiveResource(useCallback(async (signal: AbortSignal) => {
    try { return {key: queryKey, data: await loadPeriodTotals(query, signal)}; }
    catch (error) { if (error instanceof Error && error.name === 'AbortError') throw error; return {key: queryKey, data: null}; }
  }, [query, queryKey]));
  const totalState = totals.data?.key === queryKey ? totals.data : null;
  const accountState = accountsResource.data?.key === accountKey ? accountsResource.data : null;
  const accounts = accountState?.accounts ?? [], accountLoading = accountsResource.loading || !accountState && !accountsResource.error;
  const state = history.data?.key === queryKey ? history.data : null, page = state?.page;
  const loading = history.loading || !state && !history.error;
  const filter = (patch: Partial<TransactionQuery>) => { close(); setQuery(previous => ({ ...previous, ...patch, page: 0 })); };
  const activePosted = posted?.context === context ? posted.transaction : null;
  const refreshedAccount = activePosted && accounts.find(account => account.id === activePosted.accountId);
  const canPost = session.status === 'authenticated' && canPresentPosting(session.business);
  const requestedAdd = useRef<string | null>(null);
  useEffect(() => {
    if (params.get('add') === '1' && canPost && !accountLoading && requestedAdd.current !== context) {
      requestedAdd.current = context; setPosting(context);
    }
  }, [params, canPost, accountLoading, context]);
  useEffect(() => {
    if (restoreFocus.current && !accountLoading && canPost && posting !== context && transferring !== context && addRef.current) { addRef.current.focus(); restoreFocus.current = false; }
  }, [accountLoading, canPost, posting, transferring, context]);
  const Frame = embedded ? EmbeddedFrame : LivePage;
  return <Frame title={copy.title}><div dir={direction} className="space-y-5 min-w-0">
    <div className="flex flex-wrap items-center gap-3">
      {canPost && <Button ref={addRef} disabled={accountLoading || !accountState?.accounts || posting === context || transferring === context} aria-expanded={posting === context} onClick={() => {close();setPosted(null);setPosting(context);setTransferring(null);}}>{copy.add}</Button>}
      {canPost && <Button variant="secondary" disabled={accountLoading || !accountState?.accounts || transferring === context || posting === context} aria-expanded={transferring === context} onClick={() => {close();setPosted(null);setTransferring(context);setPosting(null);}}>{copy.moveMoney}</Button>}
      {scanHref && <Link id="records-scan" href={scanHref} className="inline-block rounded border border-[var(--color-brand-primary)] px-4 py-3 focus-visible:outline-2 focus-visible:outline-offset-2">{recordsCopy.scan}</Link>}
    </div>
    <Card className="border-2 border-[var(--color-brand-primary)]"><p role="note" className="font-semibold">{copy.notice}</p></Card>
    {!embedded && <Link href="/finance/accounts" className="inline-block underline rounded focus-visible:outline-2 focus-visible:outline-offset-2">{moneyCopy.title}</Link>}
    {accountLoading ? <p role="status">{copy.accountLoading}</p> : accountState?.error || accountsResource.error ? <Card><p role="alert">{accountState?.error ? copy[accountState.error] : copy.failed}</p><Button variant="secondary" onClick={accountsResource.retry}>{copy.retry}</Button></Card> : null}
    {draftSaved===context&&<Card><p role="status">{locale==='ur'?draftUr.savedNotice:draftEn.savedNotice}</p><Link href="/records?view=review" className="underline">{locale==='ur'?draftUr.toReview:draftEn.toReview}</Link></Card>}
    {posting === context && accountState?.accounts && <PostTransactionForm key={context} accounts={accounts} onDraftSaved={()=>{setDraftSaved(context);setPosting(null);restoreFocus.current=true;}} onClose={() => { restoreFocus.current = true; setPosting(null); }} onPosted={transaction => {
      close(); restoreFocus.current = true; setPosted({ context, transaction }); setPosting(null); setQuery(previous => ({ ...previous, page: 0 })); setRefresh(previous => previous + 1);
    }} />}
    {transferring === context && accountState?.accounts && <PostTransferForm key={'transfer:' + context} accounts={accounts} onClose={() => { restoreFocus.current = true; setTransferring(null); }} onTransferred={transaction => {
      close(); restoreFocus.current = true; setPosted({ context, transaction }); setSelected(transaction.id); setTransferring(null); setQuery(previous => ({ ...previous, page: 0 })); setRefresh(previous => previous + 1);
    }} />}
    {activePosted && <Card className="space-y-3"><p role="status">{activePosted.eventType === 'INTERNAL_TRANSFER' ? (locale === 'ur' ? 'رقم کامیابی سے منتقل ہو گئی۔' : 'Transfer recorded successfully.') : copy.posted}</p><p className="break-words [overflow-wrap:anywhere]">{activePosted.accountName} · <bdi dir="ltr">{activePosted.id}</bdi></p>
      {accountLoading ? <p role="status">{copy.accountLoading}</p> : refreshedAccount && <div><p>{copy.balances}: {refreshedAccount.accountName}</p><AccountMoneyValue value={refreshedAccount.currentBalance} currency={refreshedAccount.currency} scale={refreshedAccount.currencyDecimals} copy={moneyCopy} /><p className="text-sm">{moneyCopy.observed}: <bdi dir="ltr">{refreshedAccount.observationAt}</bdi></p></div>}
    </Card>}
    <Card className="space-y-3"><p>{recordsCopy.cashNotice}</p>
      {totals.loading || !totalState && !totals.error ? <p role="status">{recordsCopy.summaryLoading}</p> : !totalState?.data ? <div><p role="alert">{recordsCopy.summaryError}</p><Button variant="secondary" onClick={totals.retry}>{copy.retry}</Button></div> : <div className="grid gap-3 sm:grid-cols-3">{([
        ['moneyReceived', recordsCopy.received, 'DIRECT_SALE_RECEIPT'], ['moneyPaid', recordsCopy.paid, 'OPERATING_EXPENSE_PAYMENT'], ['netCashMovement', recordsCopy.net, undefined],
      ] as const).map(([key, label, eventType]) => <Button key={key} variant="secondary" className="h-auto min-h-[88px] min-w-0 whitespace-normal py-4 text-start [&>span]:w-full [&>span]:justify-start" onClick={() => filter({eventType})}><span className="flex min-w-0 flex-col items-start gap-2"><span>{label}</span><bdi dir="ltr" className="break-all font-sans tabular-nums text-lg">{formatPeriodAmount(totalState.data![key])} PKR</bdi></span></Button>)}</div>}
    </Card>
    <Card className="space-y-3"><Select label={recordsCopy.period} value={period} options={(['today','week','month','custom'] as const).map(value => ({value,label: recordsCopy[value]}))} onChange={event => {
      const next = event.target.value as RecordPeriod; close(); setQuery(previous => ({...previous, startDate: undefined, endDate: undefined, ...periodDates(next), page: 0}), next);
    }} /><p className="text-sm">{recordsCopy.boundary}</p></Card>
    <Card className="min-w-0"><div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <Select label={copy.account} value={query.accountId ?? ''} options={[{ value: '', label: copy.allAccounts }, ...accounts.map(a => ({ value: a.id, label: a.accountName }))]} onChange={event => filter({ accountId: event.target.value || undefined })} />
      <Select label={copy.event} value={query.eventType ?? ''} options={[{ value: '', label: copy.allEvents }, ...transactionEventTypes.map(value => ({ value, label: copy[value] }))]} onChange={event => filter({ eventType: event.target.value ? event.target.value as TransactionQuery['eventType'] : undefined })} />
      <Select label={copy.size} value={String(query.size)} options={[10, 20, 50, 100].map(value => ({ value: String(value), label: String(value) }))} onChange={event => filter({ size: Number(event.target.value) })} />
      <Input label={copy.from} type="date" dir="ltr" value={query.startDate ?? ''} onChange={event => setQuery(previous => ({...previous, startDate: event.target.value || undefined, page: 0}), 'custom')} />
      <Input label={copy.through} type="date" dir="ltr" value={query.endDate ?? ''} onChange={event => setQuery(previous => ({...previous, endDate: event.target.value || undefined, page: 0}), 'custom')} />
    </div></Card>
    {loading ? <p role="status">{copy.loading}</p> : state?.error || history.error ? <Card><p role="alert">{state?.error ? copy[state.error] : copy.failed}</p><Button variant="secondary" onClick={history.retry}>{copy.retry}</Button></Card> : page && <>
      <p role="status">{copy.count}: <bdi dir="ltr">{page.totalElements}</bdi></p>
      {page.content.length === 0 ? <Card><p>{page.totalElements === 0 ? copy.empty : copy.outOfRange}</p>{page.page > 0 && <Button variant="secondary" onClick={() => setQuery(previous => ({ ...previous, page: 0 }))}>{copy.first}</Button>}</Card> : <ul className="grid gap-4 md:grid-cols-2" aria-label={copy.title}>{page.content.map(t => <li key={t.id} className="min-w-0"><Card className="space-y-3 h-full min-w-0">
        <div className="flex items-start justify-between gap-2">
          <h2 className="font-semibold [overflow-wrap:anywhere]">{t.accountName}</h2>
          {t.isReversed ? (
            <span className="inline-block rounded px-2 py-0.5 text-xs font-semibold bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20 shrink-0">{copy.reversedBadge}</span>
          ) : t.isReversal ? (
            <span className="inline-block rounded px-2 py-0.5 text-xs font-semibold bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20 shrink-0">{copy.reversalBadge}</span>
          ) : null}
        </div>
        <p>{copy[t.eventType]} · {copy[t.movementDirection]}</p>
        {t.eventType === 'INTERNAL_TRANSFER' && t.sourceAccountName && t.destinationAccountName && (
          <p className="text-xs text-[var(--color-text-muted)] font-medium">
            {t.sourceAccountName} &rarr; {t.destinationAccountName}
          </p>
        )}
        <div className={t.isReversed ? 'line-through opacity-70' : ''}>
          <AccountMoneyValue value={t.amount} currency={t.currency} scale={2} copy={moneyCopy} />
        </div>
        <p>{copy.date}: <bdi dir="ltr">{t.effectiveDate}</bdi></p>
        <Button variant="secondary" aria-label={copy.details + ': ' + t.id} onClick={() => setSelected(t.id)}>{copy.details}</Button>
      </Card></li>)}</ul>}
      {page.totalPages > 0 && <nav aria-label={copy.page} className="flex flex-wrap items-center gap-3"><Button variant="secondary" disabled={page.page === 0} onClick={() => { close(); setQuery(previous => ({ ...previous, page: Math.max(0, (previous.page ?? 0) - 1) })); }}>{copy.previous}</Button><span>{copy.page} <bdi dir="ltr">{page.page + 1}</bdi> {copy.of} <bdi dir="ltr">{page.totalPages}</bdi></span><Button variant="secondary" disabled={page.page + 1 >= page.totalPages} onClick={() => { close(); setQuery(previous => ({ ...previous, page: (previous.page ?? 0) + 1 })); }}>{copy.next}</Button></nav>}
      <p className="text-sm">{moneyCopy.cutoff}: <bdi dir="ltr">{page.effectiveCutoffDate}</bdi> · {moneyCopy.observed}: <bdi dir="ltr">{page.observationAt}</bdi></p>
    </>}
    {selected && (
      <TransactionDetail
        key={context + ':' + selected}
        id={selected}
        onClose={close}
        onNavigate={targetId => setSelected(targetId)}
        onReversed={() => {
          setRefresh(r => r + 1);
          history.retry();
          totals.retry();
          accountsResource.retry();
        }}
      />
    )}
  </div></Frame>;
}
