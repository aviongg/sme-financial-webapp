"use client";

import { useEffect, useRef, useState } from 'react';
import { Download, FileBarChart2, Printer } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Select';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { reportsApi, type BusinessFinancialReport, type ReportFlow, type ReportMonthlyRow } from '@/lib/api/reports';
import { scoreBand } from '@/lib/api/score-presentation';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { reportsCopy, type ReportsCopy } from '@/lib/i18n/translations/reports';
import { formatGeneratedAt, formatReportAmount, formatReportCompleteness, formatReportScore, saveBlob } from '@/lib/reports/format';
import { defaultPeriod, formatMonthLabel, MAX_REPORT_MONTHS, monthOptions, validatePeriod } from '@/lib/reports/period';

type State = 'idle' | 'loading' | 'ready' | 'stale';
type Locale = 'en' | 'ur';
const muted = 'text-sm text-[var(--color-text-muted)]';
const FLOW_KEYS = ['revenue', 'cogs', 'operatingExpenses', 'interestExpense', 'cashInflow', 'cashOutflow', 'netCashMovement'] as const;
const BALANCE_KEYS = ['cashBalanceEom', 'receivablesOutstanding', 'payablesOutstanding', 'inventoryValue', 'loanOutstanding'] as const;
const COMPONENT_LABEL = { cashflow: 'pillarCashflow', profitability: 'pillarProfitability', repayment: 'pillarRepayment', trend: 'pillarTrend', compliance: 'pillarCompliance' } as const;

/** Keyed by the active business, so a business switch discards every draft, report and in-flight request. */
export function BusinessFinancialReportView() {
  const { business } = useSession();
  return <ReportScreen key={business?.businessId ?? 'none'}/>;
}

function ReportScreen() {
  const { locale, t } = useLanguage();
  const copy = reportsCopy(locale);
  const { can } = useSession();
  const [period, setPeriod] = useState(() => defaultPeriod(new Date()));
  const [report, setReport] = useState<BusinessFinancialReport | null>(null);
  const [applied, setApplied] = useState<{ fromMonth: string; toMonth: string } | null>(null);
  const [state, setState] = useState<State>('idle');
  const [failure, setFailure] = useState<unknown>(null);
  const [issue, setIssue] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportNote, setExportNote] = useState<'exported' | 'failed' | null>(null);
  const revision = useRef(0);
  const activeRequest = useRef<AbortController | null>(null);
  const resultsRef = useRef<HTMLDivElement>(null);

  useEffect(() => () => { revision.current++; activeRequest.current?.abort(); }, []);

  const edit = (next: { fromMonth: string; toMonth: string }) => {
    revision.current++; activeRequest.current?.abort(); activeRequest.current = null;
    setPeriod(next); setIssue(null); setFailure(null); setExportNote(null); setReport(null); setApplied(null);
    setState(state === 'idle' ? 'idle' : 'stale');
  };
  const generate = async (event: React.FormEvent) => {
    event.preventDefault();
    if (state === 'loading') return;
    const problem = validatePeriod(period.fromMonth, period.toMonth);
    setFailure(null); setExportNote(null);
    if (problem) { setIssue(problem); setReport(null); setApplied(null); setState('stale'); return; }
    setIssue(null);
    const controller = new AbortController(), ticket = ++revision.current;
    activeRequest.current?.abort(); activeRequest.current = controller;
    setState('loading'); setReport(null); setApplied(null);
    try {
      const result = await reportsApi.preview({ ...period, language: locale }, controller.signal);
      if (controller.signal.aborted || ticket !== revision.current) return;
      if (result.schemaVersion !== 'business-financial-summary-v1' || result.currency !== 'PKR'
        || result.period.fromMonth !== period.fromMonth || result.period.toMonth !== period.toMonth) throw new ApiError(502, 'Invalid report response.');
      setReport(result); setApplied({ ...period }); setState('ready');
      requestAnimationFrame(() => resultsRef.current?.focus());
    } catch (error) {
      if (controller.signal.aborted || ticket !== revision.current) return;
      setFailure(error); setState('stale');
    } finally {
      if (ticket === revision.current) activeRequest.current = null;
    }
  };
  const exportCsv = async () => {
    if (!applied || exporting) return;
    const ticket = revision.current;
    setExporting(true); setExportNote(null); setFailure(null);
    try {
      // Same period as the report on screen; the server renders the CSV from the same canonical dataset.
      const file = await reportsApi.exportCsv({ ...applied, language: locale });
      if (ticket !== revision.current) return;
      saveBlob(file.blob, file.fileName, copy.csvFallbackName);
      setExportNote('exported');
    } catch (error) {
      if (ticket !== revision.current || (error instanceof DOMException && error.name === 'AbortError')) return;
      setFailure(error);
      setExportNote('failed');
    } finally {
      if (ticket === revision.current) setExporting(false);
    }
  };

  if (isDemoMode || !can('FINANCIAL_DATA_READ')) return <LivePage title={copy.title}>
    <Card><p data-testid="report-unavailable">{isDemoMode ? copy.demoUnavailable : copy.noAccess}</p></Card>
  </LivePage>;

  const options = monthOptions(new Date(), locale, [period.fromMonth, period.toMonth]);
  const issueText = issue === 'tooLong' ? copy.periodIssues.tooLong(MAX_REPORT_MONTHS)
    : issue === 'reversed' ? copy.periodIssues.reversed : issue === 'required' ? copy.periodIssues.required : undefined;
  const failureText = failure === null ? null : failure instanceof ApiError && failure.status === 403 ? copy.noAccess : localizedError(failure, t);

  return <LivePage title={copy.title}>
    <div data-report-print data-testid="report-root" className="space-y-6 min-w-0">
      <div className="space-y-2 print:hidden">
        <p className="max-w-3xl text-[var(--color-text-secondary)]">{copy.intro}</p>
        <p className={muted}>{copy.readOnly}</p>
      </div>

      <form onSubmit={generate} noValidate className="print:hidden" data-testid="report-controls">
        <Card className="space-y-5 min-w-0">
          <h2 className="font-semibold text-lg">{copy.controls}</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Select label={copy.fromMonth} value={period.fromMonth} data-testid="report-from" options={options}
              onChange={event => edit({ ...period, fromMonth: event.target.value })}/>
            <Select label={copy.toMonth} value={period.toMonth} data-testid="report-to" options={options}
              onChange={event => edit({ ...period, toMonth: event.target.value })}/>
          </div>
          {issueText && <p role="alert" data-testid="report-period-issue" className="text-sm text-[var(--color-status-error)]">{issueText}</p>}
          {failureText && <p role="alert" data-testid="report-error" className="text-sm text-[var(--color-status-error)]">{failureText}</p>}
          <div className="flex flex-wrap items-center gap-3">
            <Button type="submit" size="lg" leftIcon={<FileBarChart2/>} isLoading={state === 'loading'} data-testid="report-generate">
              {state === 'loading' ? copy.generating : report ? copy.refresh : copy.generate}</Button>
            <Button type="button" variant="secondary" leftIcon={<Download/>} isLoading={exporting} disabled={!report} data-testid="report-export"
              onClick={exportCsv}>{exporting ? copy.exporting : copy.exportCsv}</Button>
            <Button type="button" variant="secondary" leftIcon={<Printer/>} disabled={!report} data-testid="report-print"
              onClick={() => window.print()}>{copy.print}</Button>
          </div>
        </Card>
      </form>

      <div aria-live="polite" aria-atomic="true" data-testid="report-status" className="print:hidden">
        <p role="status" className={muted}>{state === 'loading' ? copy.loadingStatus : state === 'stale' ? (issue || failure ? '' : copy.stale)
          : state === 'ready' ? copy.readyStatus : copy.notGenerated}{exportNote ? ` ${exportNote === 'exported' ? copy.exported : copy.exportFailed}` : ''}</p>
      </div>

      {report && <div ref={resultsRef} tabIndex={-1} role="region" aria-label={copy.reportTitle} data-testid="report-view" className="space-y-6 outline-none min-w-0">
        <ReportBody report={report} copy={copy} locale={locale} t={t}/>
      </div>}
    </div>
  </LivePage>;
}

function ReportBody({ report, copy, locale, t }: { report: BusinessFinancialReport; copy: ReportsCopy; locale: Locale; t: ReturnType<typeof useLanguage>['t'] }) {
  const money = (value: string | null) => formatReportAmount(value, locale, copy.notRecorded);
  const empty = report.completeness.recordedMonths === 0;
  const band = report.latestHealth?.band ? scoreBand(report.latestHealth.band) : null;
  const bandLabel = report.latestHealth?.band ? (band ? t.healthStates[band] : report.latestHealth.band) : copy.notRecorded;
  return <>
    <div className="space-y-1 border-b border-[var(--color-border-default)] pb-4" data-testid="report-header">
      <h2 className="text-2xl font-semibold">{copy.reportTitle}</h2>
      <p className="[overflow-wrap:anywhere]"><span className={muted}>{copy.business}: </span><bdi data-testid="report-business">{report.business.name ?? copy.notRecorded}</bdi></p>
      <p><span className={muted}>{copy.periodLabel}: </span><bdi data-testid="report-period">{formatMonthLabel(report.period.fromMonth, locale)} – {formatMonthLabel(report.period.toMonth, locale)}</bdi>
        <span className={muted}> ({report.period.fromMonth} – {report.period.toMonth})</span></p>
      <p><span className={muted}>{copy.generatedAt}: </span><bdi data-testid="report-generated-at">{formatGeneratedAt(report.generatedAt, locale)}</bdi></p>
      <p className={muted}>{copy.currencyNote}</p>
      <p className={muted} data-testid="report-semantics">{copy.semanticsNote}</p>
    </div>

    {empty && <Card data-testid="report-nodata"><p>{copy.noData}</p></Card>}

    <section aria-labelledby="report-summary-title" className="space-y-3" data-testid="report-summary">
      <h3 id="report-summary-title" className="text-lg font-semibold">{copy.summary}</h3>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Tile label={copy.revenueTotal} value={money(report.periodFlows.revenue.total)} testId="tile-revenue" nullValue={report.periodFlows.revenue.total === null}/>
        <Tile label={copy.netCashTotal} value={money(report.periodFlows.netCashMovement.total)} testId="tile-net-cash" nullValue={report.periodFlows.netCashMovement.total === null}/>
        <Tile label={copy.latestCash} value={money(report.latestBalances.cashBalanceEom)} testId="tile-cash-balance" nullValue={report.latestBalances.cashBalanceEom === null}
          detail={report.latestBalances.asOfMonth ? copy.asOf(report.latestBalances.asOfMonth) : undefined}/>
        <Tile label={copy.latestHealthTile} value={formatReportScore(report.latestHealth?.compositeScore, copy.notRecorded)} testId="tile-health" nullValue={!report.latestHealth}
          detail={report.latestHealth ? `${bandLabel} · ${copy.healthMonth(report.latestHealth.month)}` : undefined}/>
      </div>
    </section>

    <Card className="space-y-4 min-w-0" data-testid="report-flows">
      <div className="space-y-1"><h3 className="text-lg font-semibold">{copy.periodFlows}</h3><p className={muted}>{copy.flowsHelp}</p></div>
      <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {FLOW_KEYS.map(key => <FlowItem key={key} id={key} label={copy.flowLabels[key]} flow={report.periodFlows[key]} copy={copy} money={money}/>)}
      </dl>
    </Card>

    {!empty && <MonthlyTable title={copy.monthlyFlows} testId="flows" keys={FLOW_KEYS} labels={copy.flowLabels} rows={report.monthlyRows} copy={copy} locale={locale} money={money}/>}

    <Card className="space-y-4 min-w-0" data-testid="report-balances">
      <div className="space-y-1"><h3 className="text-lg font-semibold">{copy.latestBalances}</h3><p className={muted}>{copy.balancesHelp}</p>
        <p className="font-medium" data-testid="balances-as-of">{report.latestBalances.asOfMonth ? copy.asOf(report.latestBalances.asOfMonth) : copy.notRecorded}</p></div>
      <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {BALANCE_KEYS.map(key => <div key={key} className="min-w-0"><dt className={muted}>{copy.balanceLabels[key]}</dt>
          <dd data-testid={`balance-${key}`} data-null={report.latestBalances[key] === null} className="font-medium tabular-nums [overflow-wrap:anywhere]"><bdi>{money(report.latestBalances[key])}</bdi></dd></div>)}
      </dl>
    </Card>

    {!empty && <MonthlyTable title={copy.monthlyBalances} testId="balances" keys={BALANCE_KEYS} labels={copy.balanceLabels} rows={report.monthlyRows} copy={copy} locale={locale} money={money}/>}

    <Card className="space-y-4 min-w-0" data-testid="report-health">
      <div className="space-y-1"><h3 className="text-lg font-semibold">{copy.healthTitle}</h3><p className={muted}>{copy.healthHelp}</p></div>
      {report.latestHealth ? <>
        <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <Fact label={copy.score} value={formatReportScore(report.latestHealth.compositeScore, copy.notRecorded)} testId="health-score"/>
          <Fact label={copy.band} value={bandLabel} testId="health-band"/>
          <Fact label={copy.periodLabel} value={report.latestHealth.month} testId="health-month"/>
          <Fact label={copy.scoreCompleteness} value={formatReportCompleteness(report.latestHealth.dataCompleteness, copy.notRecorded)} testId="health-completeness"/>
          <Fact label={copy.methodology} value={report.latestHealth.methodologyVersion ?? copy.notRecorded} testId="health-methodology"/>
          <Fact label={copy.computedAt} value={report.latestHealth.computedAt ? formatGeneratedAt(report.latestHealth.computedAt, locale) : copy.notRecorded} testId="health-computed"/>
        </dl>
        <div className="space-y-2"><h4 className="font-semibold">{copy.components}</h4>
          <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {report.latestHealth.components.map(component => {
              const label = component.key in COMPONENT_LABEL ? t.dashboard[COMPONENT_LABEL[component.key as keyof typeof COMPONENT_LABEL]] : component.key;
              return <Fact key={component.key} label={label} value={formatReportScore(component.score, copy.notRecorded)} testId={`health-component-${component.key}`}/>;
            })}
          </dl>
        </div>
      </> : <p data-testid="health-none">{copy.noScore}</p>}
    </Card>

    <Card className="space-y-4 min-w-0" data-testid="report-completeness">
      <h3 className="text-lg font-semibold">{copy.completenessTitle}</h3>
      <p data-testid="completeness-months">{copy.monthsRecorded(report.completeness.recordedMonths, report.completeness.requestedMonths)}</p>
      {report.completeness.missingMonths.length ? <div><p className="font-medium">{copy.missingMonths}</p>
        <ul className="flex flex-wrap gap-2 mt-1" data-testid="completeness-missing">{report.completeness.missingMonths.map(month =>
          <li key={month} className="rounded-full border border-[var(--color-border-default)] px-2.5 py-0.5 text-sm"><bdi>{month}</bdi></li>)}</ul></div>
        : !empty && <p className={muted}>{copy.allRecorded}</p>}
      {!empty && <div><p className="font-medium">{copy.optionalFields}</p>
        <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 mt-1">{report.completeness.optionalFields.map(field =>
          <li key={field.field} className="text-sm" data-testid={`coverage-${field.field}`}>{copy.optionalLabels[field.field as keyof typeof copy.optionalLabels] ?? field.field}: <bdi>{field.recordedMonths}/{field.includedMonths}</bdi></li>)}</ul></div>}
      {report.limitations.length > 0 && <div><p className="font-medium">{copy.limitationsTitle}</p>
        <ul className="list-disc ps-5 space-y-1 mt-1" data-testid="report-limitations">{report.limitations.map(code =>
          <li key={code} className="text-sm" data-testid={`limitation-${code}`}>{copy.limitations[code] ?? code}</li>)}</ul></div>}
    </Card>
  </>;
}

function Tile({ label, value, testId, detail, nullValue }: { label: string; value: string; testId: string; detail?: string; nullValue: boolean }) {
  return <Card className="min-w-0 space-y-1"><p className={muted}>{label}</p>
    <p data-testid={testId} data-null={nullValue} className="text-xl sm:text-2xl font-semibold tabular-nums [overflow-wrap:anywhere]"><bdi>{value}</bdi></p>
    {detail && <p className="text-xs text-[var(--color-text-muted)]">{detail}</p>}</Card>;
}
function Fact({ label, value, testId }: { label: string; value: string; testId: string }) {
  return <div className="min-w-0"><dt className={muted}>{label}</dt><dd data-testid={testId} className="font-medium tabular-nums [overflow-wrap:anywhere]"><bdi>{value}</bdi></dd></div>;
}
function FlowItem({ id, label, flow, copy, money }: { id: string; label: string; flow: ReportFlow; copy: ReportsCopy; money: (value: string | null) => string }) {
  return <div className="min-w-0" data-testid={`flow-${id}`}>
    <dt className={muted}>{label}</dt>
    <dd className="font-medium tabular-nums [overflow-wrap:anywhere]"><bdi data-testid={`flow-${id}-total`} data-null={flow.total === null}>{money(flow.total)}</bdi></dd>
    <dd className="text-xs text-[var(--color-text-muted)]"><span data-testid={`flow-${id}-status`}>{copy.flowStatus[flow.status]}</span> · {copy.recordedOf(flow.recordedMonths, flow.includedMonths)}</dd>
  </div>;
}

/** Wide screens and print: a real table. Narrow screens: one stacked card per month, so nothing overflows sideways. */
function MonthlyTable<K extends keyof ReportMonthlyRow>({ title, testId, keys, labels, rows, copy, locale, money }: {
  title: string; testId: string; keys: readonly K[]; labels: Record<K, string>; rows: ReportMonthlyRow[]; copy: ReportsCopy; locale: Locale;
  money: (value: string | null) => string;
}) {
  return <Card className="space-y-4 min-w-0" data-testid={`report-${testId}-monthly`}>
    <h3 className="text-lg font-semibold">{title}</h3>
    <div className="hidden lg:block print:block overflow-x-auto print:overflow-visible">
      <table className="w-full text-sm border-collapse" data-testid={`${testId}-table`}>
        <caption className="sr-only">{title}</caption>
        <thead><tr className="border-b border-[var(--color-border-default)]">
          <th scope="col" className="py-2 pe-3 text-start font-semibold">{copy.month}</th>
          {keys.map(key => <th key={String(key)} scope="col" className="py-2 px-2 text-end font-semibold">{labels[key]}</th>)}
        </tr></thead>
        <tbody>{rows.map(row => <tr key={row.month} className="border-b border-[var(--color-border-subtle)] break-inside-avoid">
          <th scope="row" className="py-2 pe-3 text-start font-medium whitespace-nowrap"><bdi>{row.month}</bdi></th>
          {keys.map(key => <td key={String(key)} data-testid={`${testId}-${row.month}-${String(key)}`} data-null={row[key] === null}
            className="py-2 px-2 text-end tabular-nums"><bdi>{money(row[key] as string | null)}</bdi></td>)}
        </tr>)}</tbody>
      </table>
    </div>
    <div className="lg:hidden print:hidden space-y-3">
      {rows.map(row => <section key={row.month} aria-label={formatMonthLabel(row.month, locale)} className="rounded-lg border border-[var(--color-border-default)] p-3 min-w-0" data-testid={`${testId}-card-${row.month}`}>
        <h4 className="font-semibold mb-2"><bdi>{formatMonthLabel(row.month, locale)}</bdi></h4>
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2">
          {keys.map(key => <div key={String(key)} className="min-w-0"><dt className="text-xs text-[var(--color-text-muted)]">{labels[key]}</dt>
            <dd data-testid={`${testId}-card-${row.month}-${String(key)}`} data-null={row[key] === null} className="tabular-nums [overflow-wrap:anywhere]"><bdi>{money(row[key] as string | null)}</bdi></dd></div>)}
        </dl>
      </section>)}
    </div>
  </Card>;
}
