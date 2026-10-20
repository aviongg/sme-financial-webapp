"use client";
import { Suspense, useEffect, useRef } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { LivePage } from '@/components/integration/LivePage';
import { DraftsWorkspace } from '@/components/records/DraftsWorkspace';
import { CounterpartiesWorkspace } from '@/components/records/counterparties/CounterpartiesWorkspace';
import { ObligationsWorkspace } from '@/components/records/obligations/ObligationsWorkspace';
import { AdvancesWorkspace } from '@/components/records/advances/AdvancesWorkspace';
import { PaymentCalendarWorkspace } from '@/components/records/calendar/PaymentCalendarWorkspace';
import { draftEn, draftUr } from '@/components/records/draft-copy';
import { AccountsList } from '@/components/finance/accounts/AccountsList';
import { TransactionsList } from '@/components/finance/transactions/TransactionsList';
import { useLanguage } from '@/lib/i18n/context';
import { BankReconciliationWorkspace } from '@/components/records/reconciliation/BankReconciliationWorkspace';
import { isDemoMode } from '@/lib/api/config';
import { Card } from '@/components/ui/Card';
import { recordsEn, recordsUr } from '@/components/records/records-copy';
function Workspace() {
 const params = useSearchParams(), {locale, direction} = useLanguage(), copy = locale === 'ur' ? recordsUr : recordsEn;
 const rawView = params.get('view');
 const view = rawView === 'accounts' ? 'accounts' : rawView === 'contacts' ? 'contacts' : rawView === 'receivables' ? 'receivables' : rawView === 'payables' ? 'payables' : rawView === 'advances' ? 'advances' : rawView === 'calendar' ? 'calendar' : rawView === 'reconciliation' ? 'reconciliation' : (rawView === 'review' || params.has('draftId')) ? 'review' : 'activity';
 const link = (tab: string) => { const next = new URLSearchParams(params.toString()); next.set('view',tab); return '/records?' + next; };
 const currentContext = '/records' + (params.size ? '?' + params.toString() : '');
 const returnParams = new URLSearchParams(params.toString()); returnParams.set('focus','scan');
 const returnTo = '/records?' + returnParams.toString(), scanHref = '/scan?returnTo=' + encodeURIComponent(returnTo);
 const restored = useRef(false);
 useEffect(() => { if (params.get('focus') === 'scan' && !restored.current) {document.getElementById('records-scan')?.focus();restored.current=true;} },[params]);
 return <LivePage title={copy.title}><div dir={direction} className="space-y-5 min-w-0">
  <nav aria-label={copy.title} className="flex flex-wrap gap-4">
    <Link className="underline focus-visible:outline-2" aria-current={view === 'activity' ? 'page' : undefined} href={link('activity')}>{copy.activity}</Link>
    <Link className="underline focus-visible:outline-2" aria-current={view === 'accounts' ? 'page' : undefined} href={link('accounts')}>{copy.accounts}</Link>
    <Link className="underline focus-visible:outline-2" aria-current={view === 'contacts' ? 'page' : undefined} href={link('contacts')}>{copy.contacts}</Link>
    <Link className="underline focus-visible:outline-2" aria-current={view === 'receivables' ? 'page' : undefined} href={link('receivables')}>{copy.receivables}</Link>
    <Link className="underline focus-visible:outline-2" aria-current={view === 'payables' ? 'page' : undefined} href={link('payables')}>{copy.payables}</Link>
    <Link className="underline focus-visible:outline-2" aria-current={view === 'advances' ? 'page' : undefined} href={link('advances')}>{copy.advances}</Link>
    <Link className="underline focus-visible:outline-2" aria-current={view === 'calendar' ? 'page' : undefined} href={link('calendar')}>{copy.calendar}</Link>
    <Link className="underline focus-visible:outline-2" aria-current={view === 'reconciliation' ? 'page' : undefined} href={link('reconciliation')}>{copy.reconciliation}</Link>
    <Link className="underline focus-visible:outline-2" aria-current={view === 'review' ? 'page' : undefined} href={link('review')}>{locale === 'ur' ? draftUr.toReview : draftEn.toReview}</Link>
    <Link className="underline focus-visible:outline-2" href={'/records/history?returnTo=' + encodeURIComponent(currentContext)}>{copy.historical}</Link>
  </nav>
  {(isDemoMode || view === 'accounts') && <Link id="records-scan" href={scanHref} className="inline-block rounded bg-[var(--color-brand-primary)] text-white px-4 py-3 focus-visible:outline-2 focus-visible:outline-offset-2">{copy.scan}</Link>}
  <p className="text-sm">{copy.ocrNotice}</p>
  {isDemoMode ? <Card><p role="status">{copy.preview}</p></Card> : view === 'contacts' ? <CounterpartiesWorkspace /> : view === 'receivables' ? <ObligationsWorkspace initialDirection="RECEIVABLE" /> : view === 'payables' ? <ObligationsWorkspace initialDirection="PAYABLE" /> : view === 'advances' ? <AdvancesWorkspace /> : view === 'calendar' ? <PaymentCalendarWorkspace /> : view === 'reconciliation' ? <BankReconciliationWorkspace /> : view === 'review' ? <DraftsWorkspace /> : view === 'accounts' ? <AccountsList embedded activityHref={link('activity')} /> : <TransactionsList embedded scanHref={scanHref} />}
 </div></LivePage>;
}
export default function RecordsPage() {return <Suspense><Workspace /></Suspense>;}
