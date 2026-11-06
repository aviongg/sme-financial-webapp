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
import { RecurringWorkspace } from '@/components/records/recurring/RecurringWorkspace';
import { CashForecastWorkspace } from '@/components/records/forecast/CashForecastWorkspace';
import { FinancialRiskWorkspace } from '@/components/records/risk/FinancialRiskWorkspace';
import { isDemoMode } from '@/lib/api/config';
import { Card } from '@/components/ui/Card';
import { unifiedCopy } from '@/lib/i18n/translations/unified';
import { recordsView, recordsTabHref, type RecordView } from '@/components/records/workspace-routing';
import { recordsEn, recordsUr } from '@/components/records/records-copy';

function Workspace() {
  const params = useSearchParams(), {locale, direction} = useLanguage(), copy = locale === 'ur' ? recordsUr : recordsEn;
  const view = recordsView(new URLSearchParams(params.toString()));
  const link = (tab: RecordView) => recordsTabHref(new URLSearchParams(params.toString()), tab);
  const currentContext = '/records' + (params.size ? '?' + params.toString() : '');
  const returnParams = new URLSearchParams(params.toString()); returnParams.set('focus','scan');
  const returnTo = '/records?' + returnParams.toString(), scanHref = '/scan?returnTo=' + encodeURIComponent(returnTo);
  const restored = useRef(false);
  useEffect(() => { if (params.get('focus') === 'scan' && !restored.current) {document.getElementById('records-scan')?.focus();restored.current=true;} },[params]);
  return <LivePage title={copy.title}><div dir={direction} className="space-y-5 min-w-0">
   <nav aria-label={copy.title} className="flex gap-4 overflow-x-auto max-w-full pb-3 [&>a]:shrink-0 [&>a]:rounded [&>a]:px-1 [&>a]:py-2">
     <Link className="underline focus-visible:outline-2" aria-current={view === 'activity' ? 'page' : undefined} href={link('activity')}>{copy.activity}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'accounts' ? 'page' : undefined} href={link('accounts')}>{copy.accounts}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'contacts' ? 'page' : undefined} href={link('contacts')}>{copy.contacts}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'receivables' ? 'page' : undefined} href={link('receivables')}>{copy.receivables}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'payables' ? 'page' : undefined} href={link('payables')}>{copy.payables}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'advances' ? 'page' : undefined} href={link('advances')}>{copy.advances}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'calendar' ? 'page' : undefined} href={link('calendar')}>{copy.calendar}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'reconciliation' ? 'page' : undefined} href={link('reconciliation')}>{copy.reconciliation}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'recurring' ? 'page' : undefined} href={link('recurring')}>{copy.recurring}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'forecast' ? 'page' : undefined} href={link('forecast')}>{copy.forecast}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'risks' ? 'page' : undefined} href={link('risks')}>{copy.risks}</Link>
     <Link className="underline focus-visible:outline-2" aria-current={view === 'review' ? 'page' : undefined} href={link('review')}>{locale === 'ur' ? draftUr.toReview : draftEn.toReview}</Link>
     <Link className="underline focus-visible:outline-2" href={'/records/history?returnTo=' + encodeURIComponent(currentContext)}>{copy.historical}</Link>
   </nav>
   <Link className="inline-block underline rounded focus-visible:outline-2" href={"/records/import?returnTo=" + encodeURIComponent(currentContext)}>{unifiedCopy(locale).imports}</Link>
   {(isDemoMode || view === 'accounts') && <Link id="records-scan" href={scanHref} className="inline-block rounded bg-[var(--color-brand-primary)] text-white px-4 py-3 focus-visible:outline-2 focus-visible:outline-offset-2">{copy.scan}</Link>}
   <p className="text-sm">{copy.ocrNotice}</p>
   {isDemoMode ? <Card><p role="status">{copy.preview}</p></Card> : view === 'contacts' ? <CounterpartiesWorkspace /> : view === 'receivables' ? <ObligationsWorkspace key="receivables" initialDirection="RECEIVABLE" /> : view === 'payables' ? <ObligationsWorkspace key="payables" initialDirection="PAYABLE" /> : view === 'advances' ? <AdvancesWorkspace /> : view === 'calendar' ? <PaymentCalendarWorkspace /> : view === 'reconciliation' ? <BankReconciliationWorkspace /> : view === 'recurring' ? <RecurringWorkspace locale={locale} /> : view === 'forecast' ? <CashForecastWorkspace /> : view === 'risks' ? <FinancialRiskWorkspace /> : view === 'review' ? <DraftsWorkspace /> : view === 'accounts' ? <AccountsList embedded activityHref={link('activity')} /> : <TransactionsList embedded scanHref={scanHref} />}
  </div></LivePage>;
}
export default function RecordsPage() {return <Suspense><Workspace /></Suspense>;}
