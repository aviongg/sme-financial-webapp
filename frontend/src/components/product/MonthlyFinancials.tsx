"use client";

import Link from 'next/link';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { monthlyApi, type FinancingInput, type MonthlyRefreshResult } from '@/lib/api/product';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { productCopy } from '@/lib/i18n/translations/product';
import { Amount, LoadState, Panel, Pill, Table, errorText, today, useLoad } from './shared';

const ACTIVITY_LINKS: [key: string, href: string][] = [
  ['transactions', '/finance/transactions'], ['invoices', '/invoices'], ['bills', '/bills'], ['stock', '/inventory/stock'], ['documents', '/upload'],
];

/** The monthly model explained: derived months, projection status, explicit recalculation, supplemental inputs. */
export function MonthlyAutomationPanel() {
  const { locale } = useLanguage();
  const m = productCopy(locale).monthly;
  return (
    <div className="space-y-6" data-testid="monthly-automation">
      <Panel title={m.how} testId="monthly-principle">
        <p className="text-sm leading-relaxed" data-testid="monthly-principle-text">{m.principle}</p>
        <div className="space-y-2">
          <p className="text-sm font-semibold">{m.recordActivity}</p>
          <ul className="flex flex-wrap gap-2">
            {ACTIVITY_LINKS.map(([key, href]) => (
              <li key={key}><Link href={href} className="inline-block rounded border border-[var(--color-border-default)] px-3 py-2 text-sm underline focus-visible:outline-2 focus-visible:outline-offset-2">{m.links[key]}</Link></li>
            ))}
          </ul>
        </div>
      </Panel>
      {!isDemoMode && <ProjectionStatusPanel/>}
      {!isDemoMode && <FinancingInputsPanel/>}
    </div>
  );
}

function ProjectionStatusPanel() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const m = copy.monthly;
  const { can } = useSession();
  const status = useLoad(signal => monthlyApi.status(signal), []);
  const [result, setResult] = useState<MonthlyRefreshResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const recalculate = async () => {
    setBusy(true); setError(null);
    try { setResult(await monthlyApi.recalculate()); status.reload(); } catch (e) { setError(e); } finally { setBusy(false); }
  };
  return (
    <Panel title={m.status} testId="monthly-status"
      actions={can('RECORD_CREATE_UPDATE') ? <Button size="sm" onClick={recalculate} disabled={busy} data-testid="monthly-recalculate">{busy ? copy.common.working : m.recalculate}</Button> : undefined}>
      <LoadState loading={status.loading} error={status.error} reload={status.reload}>
        {status.data && <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-3">
          <div><dt className="text-[var(--color-text-secondary)]">{m.status}</dt><dd><Pill tone={status.data.stale ? 'warn' : 'good'} testId="monthly-status-pill">{status.data.stale ? m.pending : m.upToDate}</Pill></dd></div>
          <div><dt className="text-[var(--color-text-secondary)]">{m.through}</dt><dd><bdi dir="ltr" data-testid="monthly-through">{status.data.projectedThrough ?? '—'}</bdi></dd></div>
          <div><dt className="text-[var(--color-text-secondary)]">{m.lastRun}</dt><dd><bdi dir="ltr">{status.data.lastProjectedAt ? status.data.lastProjectedAt.replace('T', ' ').slice(0, 16) : '—'}</bdi></dd></div>
        </dl>}
        {status.data?.lastError && <p role="alert" className="text-sm">{m.error}</p>}
      </LoadState>
      {result && <p role="status" className="text-sm" data-testid="monthly-recalculated">
        {result.changedMonths.length === 0 ? m.noChanges : <>{m.recalculated} <bdi dir="ltr">{result.changedMonths.join(', ')}</bdi></>}
      </p>}
      {error !== null && <p role="alert" className="text-sm">{errorText(error, copy.common.error)}</p>}
    </Panel>
  );
}

function FinancingInputsPanel() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const m = copy.monthly;
  const { can } = useSession();
  const list = useLoad(signal => monthlyApi.financing(signal), []);
  const [month, setMonth] = useState(today().slice(0, 7));
  const [loan, setLoan] = useState('');
  const [interest, setInterest] = useState('');
  const [type, setType] = useState('none');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const existing = (list.data ?? []).find(row => row.month === month) ?? null;
  const pick = (row: FinancingInput) => {
    setMonth(row.month); setLoan(row.loanOutstanding ?? ''); setInterest(row.interestExpense ?? ''); setType(row.financingType);
  };
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true); setMessage(null);
    try {
      await monthlyApi.saveFinancing(month, { loanOutstanding: loan.trim() || null, interestExpense: interest.trim() || null, financingType: type, expectedVersion: existing ? existing.version : null });
      setMessage(copy.common.done);
      list.reload();
    } catch (e) {
      setMessage(e instanceof ApiError && e.status === 409 ? m.stale : errorText(e, copy.common.error));
    } finally { setBusy(false); }
  };
  return (
    <Panel title={m.financingTitle} testId="monthly-financing">
      <p className="text-sm text-[var(--color-text-secondary)]">{m.financingHelp}</p>
      {can('RECORD_CREATE_UPDATE') && <form onSubmit={save} className="grid grid-cols-1 gap-3 sm:grid-cols-2" data-testid="monthly-financing-form">
        <Input label={m.month} type="month" dir="ltr" value={month} onChange={e => setMonth(e.target.value)} required data-testid="financing-month"/>
        <Select label={m.financingType} value={type} onChange={e => setType(e.target.value)} data-testid="financing-type"
          options={['none', 'conventional', 'islamic'].map(v => ({ value: v, label: m.types[v] }))}/>
        <Input label={m.loan} dir="ltr" inputMode="decimal" value={loan} onChange={e => setLoan(e.target.value)} data-testid="financing-loan"/>
        <Input label={m.interest} dir="ltr" inputMode="decimal" value={interest} onChange={e => setInterest(e.target.value)} data-testid="financing-interest"/>
        <div className="sm:col-span-2"><Button type="submit" disabled={busy} data-testid="financing-save">{busy ? copy.common.working : m.saveFinancing}</Button></div>
        {message && <p role="status" className="text-sm sm:col-span-2" data-testid="financing-message">{message}</p>}
      </form>}
      <LoadState loading={list.loading} error={list.error} reload={list.reload}>
        <Table testId="financing-list" empty={copy.common.empty} head={[m.month, m.loan, m.interest, m.financingType, '']}
          rows={(list.data ?? []).map(row => [
            <bdi key="m" dir="ltr">{row.month}</bdi>, <Amount key="l" value={row.loanOutstanding}/>, <Amount key="i" value={row.interestExpense}/>,
            <span key="t">{m.types[row.financingType]} <span className="text-xs text-[var(--color-text-secondary)]">({m.origin[row.origin] ?? row.origin})</span></span>,
            can('RECORD_CREATE_UPDATE') ? <Button key="e" size="sm" variant="secondary" onClick={() => pick(row)}>{copy.common.open}</Button> : null,
          ])}/>
      </LoadState>
    </Panel>
  );
}

/** Read-only derived month: every figure with its basis and authoritative source. */
export function DerivedMonthSources({ month }: { month: string }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const m = copy.monthly;
  const sources = useLoad(signal => monthlyApi.sources(month, signal), [month]);
  const data = sources.data;
  return (
    <Panel title={m.sourcesTitle} testId="monthly-sources">
      <LoadState loading={sources.loading} error={sources.error} reload={sources.reload}>
        {data && <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            <Pill tone={data.periodComplete ? 'neutral' : 'warn'} testId="monthly-period">{data.periodComplete ? m.closed : m.inProgress}</Pill>
            <Pill tone={data.storedSource === 'DERIVED' ? 'good' : 'warn'} testId="monthly-origin">{data.storedSource === 'DERIVED' ? m.derived : m.legacy}</Pill>
            {data.storedMatchesDerivation && <Pill tone="good" testId="monthly-matches">{m.matches}</Pill>}
          </div>
          <Table testId="monthly-source-table" head={[m.field, copy.common.value, m.basis, m.source]}
            rows={data.fields.map(f => [
              <span key="f" className="font-medium">{m.fields[f.field] ?? f.field}</span>,
              f.value === null ? <span key="v" className="text-[var(--color-text-secondary)]" data-testid={`monthly-value-${f.field}`}>{m.unknown}</span>
                : <span key="v" data-testid={`monthly-value-${f.field}`}><Amount value={f.value}/></span>,
              <Pill key="b" tone={f.basis === 'SUPPLEMENTAL_MANUAL' ? 'warn' : 'neutral'}>{m.bases[f.basis] ?? f.basis}</Pill>,
              <span key="s" className="text-xs">{f.source}</span>,
            ])}/>
          <p className="text-sm">{m.netProfit}: <Amount value={data.profitAndLoss.netProfit} testId="monthly-net-profit"/></p>
          <p className="text-xs text-[var(--color-text-secondary)]">{m.fxNote}</p>
          {data.notes.length > 0 && <ul className="list-disc ps-5 text-sm" data-testid="monthly-notes">{data.notes.map(n => <li key={n}>{m.notes[n] ?? n}</li>)}</ul>}
        </div>}
      </LoadState>
    </Panel>
  );
}

export function ReviewEvidenceNotice() {
  const { locale } = useLanguage();
  return <p className="rounded border border-[var(--color-border-default)] p-3 text-sm" data-testid="review-evidence-notice">{productCopy(locale).monthly.reviewEvidence}</p>;
}
