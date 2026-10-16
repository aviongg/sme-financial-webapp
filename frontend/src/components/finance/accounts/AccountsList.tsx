"use client";
import { accountQueryFromURL, accountSearch } from '../../records/record-query';
import { useSearchParams, usePathname } from 'next/navigation';
import { useCallback, useMemo, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useSession } from '@/components/auth/SessionProvider';
import { canPresentAccountCreate } from '@/lib/api/dev-a/account-create-contracts';
import { accountsApi } from '@/lib/api/dev-a/accounts';
import { accountStatuses, accountTypes, type AccountDetailData, type AccountQuery, type AccountStatus, type AccountType } from '@/lib/api/dev-a/account-contracts';
import { currencyScales } from '@/lib/utils/decimal-display';
import { useLanguage } from '@/lib/i18n/context';
import { LivePage } from '@/components/integration/LivePage';
import { useLiveResource } from '@/components/integration/useLiveResource';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { AccountDetail } from './AccountDetail';
import { AccountMoneyValue } from './AccountMoneyValue';
import { accountEn, accountUr, accountCreateEn, accountCreateUr, accountErrorKey } from './account-copy';
import { CreateAccountForm } from './CreateAccountForm';

import { PostTransferForm } from '../transfers/PostTransferForm';
import type { TransactionDetailData } from '@/lib/api/dev-a/transaction-contracts';

const EmbeddedFrame = ({ children }: { children?: React.ReactNode; title?: string }) => <>{children}</>;

export function AccountsList({ embedded = false, activityHref = '/finance/transactions' }: { embedded?: boolean; activityHref?: string }) {
  const { locale, direction } = useLanguage();
  const copy = locale === 'ur' ? accountUr : accountEn;
  const createCopy = locale === 'ur' ? accountCreateUr : accountCreateEn;
  const session = useSession();
  const contextKey = JSON.stringify([session.epoch, session.user?.id, session.business?.businessId, session.business?.role, session.business?.membershipStatus, session.status]);
  const [creating, setCreating] = useState<string | null>(null);
  const [transferring, setTransferring] = useState<string | null>(null);
  const [created, setCreated] = useState<{ context: string; account: AccountDetailData } | null>(null);
  const [transferred, setTransferred] = useState<{ context: string; transaction: TransactionDetailData } | null>(null);
  const [refresh, setRefresh] = useState(0);
  const addRef = useRef<HTMLButtonElement>(null);
  const previousCreating = useRef<string | null>(null);
  useEffect(() => {
    if (previousCreating.current !== null && creating === null && transferring === null) addRef.current?.focus();
    previousCreating.current = creating;
  }, [creating, transferring]);
  const params = useSearchParams(), pathname = usePathname();
  const query = useMemo(() => accountQueryFromURL(new URLSearchParams(params.toString()), embedded), [params, embedded]);
  const setQuery = (update: AccountQuery | ((previous: AccountQuery) => AccountQuery)) => {
    const search = accountSearch(new URLSearchParams(window.location.search), update, embedded);
    window.history.replaceState(null, '', pathname + '?' + search.toString());
  };
  const [selected, setSelected] = useState<string | null>(null);
  const close = useCallback(() => setSelected(null), []);
  const queryKey = contextKey + ':' + refresh + ':' + JSON.stringify(query);
  const resource = useLiveResource(useCallback(async (signal: AbortSignal) => {
    try { return { key: queryKey, page: await accountsApi.list(query, signal), error: null }; }
    catch (error) {
      if (error instanceof Error && error.name === 'AbortError') throw error;
      return { key: queryKey, page: null, error: accountErrorKey(error) };
    }
  }, [query, queryKey]));
  // Never show the previous filter's results during the effect cleanup/loading gap.
  const state = resource.data?.key === queryKey ? resource.data : null;
  const page = state?.page;
  const loading = resource.loading || !state && !resource.error;
  const filter = (patch: Partial<AccountQuery>) => { close(); setQuery(previous => ({ ...previous, ...patch, page: 0 })); };
  const Frame = embedded ? EmbeddedFrame : LivePage;
  return <Frame title={copy.title}><div dir={direction} className="space-y-5 min-w-0">
    <p className="text-sm text-[var(--color-text-muted)]">{copy.readOnly}</p>
    <Link href={activityHref} className="inline-block underline rounded focus-visible:outline-2 focus-visible:outline-offset-2">{copy.transactions}</Link>
    <div className="flex flex-wrap items-center gap-3">
      {session.status === 'authenticated' && canPresentAccountCreate(session.business) && <Button ref={addRef} aria-expanded={creating === contextKey} disabled={creating === contextKey || transferring === contextKey} onClick={() => { close(); setCreated(null); setTransferred(null); setCreating(contextKey); setTransferring(null); }}>{createCopy.add}</Button>}
      {session.status === 'authenticated' && canPresentAccountCreate(session.business) && <Button variant="secondary" aria-expanded={transferring === contextKey} disabled={transferring === contextKey || creating === contextKey} onClick={() => { close(); setCreated(null); setTransferred(null); setTransferring(contextKey); setCreating(null); }}>{locale === 'ur' ? 'رقم منتقل کریں' : 'Move money'}</Button>}
    </div>
    {creating === contextKey && <CreateAccountForm key={contextKey} onClose={() => { setCreating(null); requestAnimationFrame(() => addRef.current?.focus()); }} onCreated={account => {
      setCreated({ context: contextKey, account }); setCreating(null); setQuery(previous => ({ ...previous, page: 0 })); setRefresh(previous => previous + 1);
      requestAnimationFrame(() => addRef.current?.focus());
    }} />}
    {transferring === contextKey && page && (
      <PostTransferForm
        key={'transfer:' + contextKey}
        accounts={page.items}
        onClose={() => setTransferring(null)}
        onTransferred={transaction => {
          close();
          setTransferred({ context: contextKey, transaction });
          setTransferring(null);
          setRefresh(previous => previous + 1);
        }}
      />
    )}
    {created?.context === contextKey && <p role="status" className="text-sm">{createCopy.created} <span className="font-semibold [overflow-wrap:anywhere]">{created.account.accountName}</span></p>}
    {transferred?.context === contextKey && <p role="status" className="text-sm">{locale === 'ur' ? 'رقم کامیابی سے منتقل ہو گئی۔' : 'Transfer recorded successfully.'}</p>}
    <Card className="min-w-0"><div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <Select label={copy.status} value={query.status} onChange={event => filter({ status: event.target.value as AccountStatus })} options={accountStatuses.map(value => ({ value, label: copy[value] }))} />
      <Select label={copy.type} value={query.accountType ?? ''} onChange={event => filter({ accountType: event.target.value ? event.target.value as AccountType : undefined })} options={[{ value: '', label: copy.allTypes }, ...accountTypes.map(value => ({ value, label: copy[value] }))]} />
      <Select label={copy.currency} value={query.currency ?? ''} onChange={event => filter({ currency: event.target.value || undefined })} options={[{ value: '', label: copy.allCurrencies }, ...Object.keys(currencyScales).map(value => ({ value, label: value }))]} />
      <Select label={copy.size} value={String(query.size)} onChange={event => filter({ size: Number(event.target.value) })} options={[10, 20, 50, 100].map(value => ({ value: String(value), label: String(value) }))} />
    </div></Card>
    {loading ? <p role="status">{copy.loading}</p> : state?.error || resource.error ? <Card className="space-y-3"><p role="alert">{state?.error ? copy[state.error] : copy.failed}</p><Button variant="secondary" onClick={resource.retry}>{copy.retry}</Button></Card> : page && <>
      <p role="status" className="text-sm">{copy.count}: <bdi dir="ltr" className="tabular-nums">{page.totalElements}</bdi></p>
      {page.items.length === 0 ? <Card className="space-y-3"><p>{page.totalElements === 0 ? copy.empty : copy.outOfRange}</p>{page.page > 0 && <Button variant="secondary" onClick={() => setQuery(previous => ({ ...previous, page: 0 }))}>{copy.first}</Button>}</Card> :
        <ul className="grid gap-4 md:grid-cols-2" aria-label={copy.title}>{page.items.map(account => <li key={account.id} className="min-w-0"><Card className="space-y-4 h-full min-w-0">
          <h2 className="font-heading font-semibold break-words [overflow-wrap:anywhere]">{account.accountName}</h2>
          <p className="text-sm flex flex-wrap gap-2"><span>{copy[account.accountType]}</span><span aria-hidden="true">·</span><span>{copy[account.status]}</span><bdi dir="ltr">{account.currency}</bdi></p>
          <div><p className="text-sm text-[var(--color-text-muted)]">{copy.current}</p><p className="font-semibold text-lg"><AccountMoneyValue value={account.currentBalance} currency={account.currency} scale={account.currencyDecimals} copy={copy} /></p>
            {account.balanceStatus === 'UNAVAILABLE' && <p className="text-sm">{copy[account.balanceUnavailableReason]}</p>}
          </div>
          <dl className="text-sm space-y-2">
            <div><dt className="text-[var(--color-text-muted)]">{copy.cutoff}</dt><dd><bdi dir="ltr">{account.effectiveCutoffDate}</bdi></dd></div>
            <div><dt className="text-[var(--color-text-muted)]">{copy.observed}</dt><dd className="break-all"><bdi dir="ltr" className="font-sans">{account.observationAt}</bdi></dd></div>
            {account.accountIdentifier !== null && <div><dt className="text-[var(--color-text-muted)]">{copy.identifier}</dt><dd><bdi dir="ltr" className="break-all">{account.accountIdentifier}</bdi></dd></div>}
          </dl>
          <Button variant="secondary" aria-label={copy.details + ': ' + account.accountName} onClick={() => setSelected(account.id)}>{copy.details}</Button>
        </Card></li>)}</ul>}
      {page.totalPages > 0 && <nav aria-label={copy.page} className="flex flex-wrap items-center gap-3">
        <Button variant="secondary" disabled={page.page === 0} onClick={() => { close(); setQuery(previous => ({ ...previous, page: Math.max(0, (previous.page ?? 0) - 1) })); }}>{copy.previous}</Button>
        <span className="text-sm">{copy.page} <bdi dir="ltr">{page.page + 1}</bdi> {copy.of} <bdi dir="ltr">{page.totalPages}</bdi></span>
        <Button variant="secondary" disabled={page.page + 1 >= page.totalPages} onClick={() => { close(); setQuery(previous => ({ ...previous, page: (previous.page ?? 0) + 1 })); }}>{copy.next}</Button>
      </nav>}<p className="text-sm text-[var(--color-text-muted)]">{copy.dateHelp}</p>
    </>}
    {selected && <AccountDetail key={selected} id={selected} onClose={close} />}
  </div></Frame>;
}
