"use client";

import { useRef, useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { isDemoMode } from '@/lib/api/config';
import { copilotApi, insightApi, lifecycleApi, whatsappInboundApi, type CopilotResponse } from '@/lib/api/product';
import { obligationsApi } from '@/lib/api/dev-a/obligations';
import { useLanguage } from '@/lib/i18n/context';
import { productCopy, type ProductCopy } from '@/lib/i18n/translations/product';
import { copilotProvenance, type CopilotProvenance } from '@/lib/providerDisclosure';
import { Amount, LoadState, Panel, Pct, Pill, Stat, StatGrid, Table, asList, asRecord, errorText, monthStart, today, useLoad } from './shared';

function Gate({ title, children }: { title: string; children: React.ReactNode }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const { can } = useSession();
  if (isDemoMode || !can('FINANCIAL_DATA_READ')) {
    return <LivePage title={title}><Card><p>{isDemoMode ? copy.common.demo : copy.common.noAccess}</p></Card></LivePage>;
  }
  return <>{children}</>;
}

// ================================================================== intelligence + action center

export function IntelligenceWorkspace() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const { business } = useSession();
  return <Gate title={copy.intelligence.title}><IntelligenceInner key={business?.businessId ?? 'none'}/></Gate>;
}

function IntelligenceInner() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const i = copy.intelligence;
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(today());
  const overview = useLoad(signal => insightApi.overview(from, to, signal), [from, to]);
  const actions = useLoad(signal => insightApi.actions(signal), []);
  const data = overview.data;
  const revenue = asRecord(data?.revenue);
  const expenses = asRecord(data?.expenses);
  const profitability = asRecord(data?.profitability);
  const position = asRecord(data?.position);
  const health = asRecord(data?.health);
  return (
    <LivePage title={i.title}>
      <Panel title={i.actions} testId="action-center">
        <LoadState loading={actions.loading} error={actions.error} reload={actions.reload}>
          {(actions.data?.actions ?? []).length === 0 ? <p className="text-sm">{i.noActions}</p> :
            <ul className="space-y-3">{(actions.data?.actions ?? []).map(a => <li key={String(a.key)} className="rounded-lg border border-[var(--color-border-subtle)] p-3" data-testid="action-item">
              <div className="flex flex-wrap items-center gap-2">
                <Pill tone={a.priority === 'HIGH' || a.priority === 'CRITICAL' ? 'bad' : a.priority === 'MEDIUM' ? 'warn' : 'neutral'}>{i.priority[String(a.priority)] ?? String(a.priority)}</Pill>
                <span className="font-medium break-words">{String(a.title)}</span>
                {a.amount != null && <Amount value={a.amount}/>}
              </div>
              <p className="mt-1 text-sm"><span className="font-medium">{i.why}: </span>{String(a.reason)}</p>
              <p className="mt-1 text-xs text-[var(--color-text-muted)]">{copy.common.evidence}: {asList(a.evidence).map(e => `${e.source}${e.detail ? ' · ' + e.detail : ''}`).join('; ')}</p>
              {typeof a.link === 'string' && <Link className="text-sm underline" href={a.link}>{copy.common.open}</Link>}
            </li>)}</ul>}
        </LoadState>
      </Panel>
      <Card className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Input label={copy.common.from} type="date" dir="ltr" value={from} onChange={e => setFrom(e.target.value)}/>
        <Input label={copy.common.to} type="date" dir="ltr" value={to} onChange={e => setTo(e.target.value)}/>
      </Card>
      <LoadState loading={overview.loading} error={overview.error} reload={overview.reload}>
        {data && <div className="space-y-6">
          <Panel title={i.position} testId="intel-position">
            <StatGrid>
              <Stat label={i.cash} value={position.cashBalance}/>
              <Stat label={i.ar} value={position.receivablesOutstanding}/>
              <Stat label={i.ap} value={position.payablesOutstanding}/>
              <Stat label={i.inventory} value={position.inventoryValue}/>
            </StatGrid>
          </Panel>
          <Panel title={i.revenue} testId="intel-revenue" actions={<Pill tone={data.completeness === 'COMPLETE' ? 'good' : 'warn'}>{data.completeness === 'COMPLETE' ? copy.common.complete : copy.common.partial}</Pill>}>
            <StatGrid>
              <Stat label={copy.planning.pnl.revenue} value={revenue.recognizedRevenue} testId="intel-revenue-total"/>
              <Stat label={copy.planning.pnl.cashSales} value={revenue.cashSales}/>
              <Stat label={copy.planning.pnl.invoices} value={revenue.invoiceRevenue}/>
              <Stat label={copy.planning.pnl.creditNotes} value={revenue.creditNotes}/>
            </StatGrid>
            <h3 className="font-semibold">{i.trend}</h3>
            <Table head={[i.month, copy.planning.pnl.revenue, i.costOfSales, copy.planning.pnl.opex, i.operatingProfit]}
              rows={asList(revenue.trend).map(t => [<bdi key="m" dir="ltr">{String(t.month)}</bdi>, <Amount key="r" value={t.revenue}/>, <Amount key="c" value={t.costOfSales}/>, <Amount key="o" value={t.operatingExpenses}/>, <Amount key="p" value={t.operatingProfit}/>])}/>
            <h3 className="font-semibold">{i.topCustomers}</h3>
            <Table head={['', copy.common.amount, i.share]} rows={asList(revenue.topCustomers).map(c => [String(c.name ?? '—'), <Amount key="a" value={c.amount}/>, <Pct key="s" value={c.sharePct}/>])} empty={copy.common.empty}/>
          </Panel>
          <Panel title={i.expenses} testId="intel-expenses">
            <Table head={[copy.planning.budgets.category, copy.common.amount]} rows={asList(expenses.byCategory).map(c => [copy.planning.categories[String(c.category)] ?? String(c.category), <Amount key="a" value={c.amount}/>])} empty={copy.common.empty}/>
          </Panel>
          <Panel title={i.profitability} testId="intel-profitability">
            <StatGrid>
              <Stat label={copy.planning.pnl.grossProfit} value={profitability.grossProfit}/>
              <Stat label={copy.planning.pnl.operatingProfit} value={profitability.operatingProfit}/>
              <Stat label="%" value={profitability.grossMarginPct} pct/>
              <Stat label={copy.planning.pnl.net} value={profitability.netProfit}/>
            </StatGrid>
            <h3 className="font-semibold">{i.projects}</h3>
            <Table head={['', copy.projectActuals.revenue, copy.projectActuals.total, copy.projectActuals.profit, copy.projectActuals.margin]}
              rows={asList(profitability.projects).map(p => [<Link key="p" className="underline" href={`/projects/${String(p.projectId)}`}><bdi dir="ltr">{String(p.code)}</bdi></Link>, <Amount key="r" value={p.revenue}/>, <Amount key="c" value={p.cost}/>, <Amount key="pr" value={p.profit}/>, <Pct key="m" value={p.marginPct}/>])} empty={copy.common.empty}/>
          </Panel>
          <Panel title={i.health} testId="intel-health">
            {health.status === 'AVAILABLE' ? <p className="text-sm"><bdi dir="ltr">{String(health.month)}</bdi> · <bdi dir="ltr">{String(health.compositeScore)}</bdi> · {String(health.band)}</p> : <p className="text-sm">{i.noScore}</p>}
          </Panel>
        </div>}
      </LoadState>
    </LivePage>
  );
}

// ================================================================== calendar + relationships

export function CalendarWorkspace() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const { business } = useSession();
  return <Gate title={copy.calendar.title}><CalendarInner key={business?.businessId ?? 'none'}/></Gate>;
}

function CalendarInner() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const c = copy.calendar;
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(() => { const d = new Date(); d.setDate(d.getDate() + 60); return d.toISOString().slice(0, 10); });
  const { data, loading, error, reload } = useLoad(signal => insightApi.calendar(from, to, signal), [from, to]);
  const typeLabel = (type: string) => c.types[type] ?? (type.startsWith('RECURRING_') ? c.recurring : type);
  return (
    <LivePage title={c.title}>
      <Card className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Input label={copy.common.from} type="date" dir="ltr" value={from} onChange={e => setFrom(e.target.value)}/>
        <Input label={copy.common.to} type="date" dir="ltr" value={to} onChange={e => setTo(e.target.value)}/>
      </Card>
      <Panel title={c.title} testId="calendar-panel">
        <LoadState loading={loading} error={error} reload={reload}>
          <Table testId="calendar-events" empty={c.empty} head={[copy.common.date, 'Type', '', copy.common.amount, copy.common.status]}
            rows={asList(data?.events).map(e => [<bdi key="d" dir="ltr">{String(e.date)}</bdi>, typeLabel(String(e.type)), <span key="l" className="break-words">{String(e.label ?? '')}</span>,
              <Amount key="a" value={e.amount}/>, e.status === 'OVERDUE' ? <Pill key="s" tone="bad">{c.overdue}</Pill> : <span key="s">{String(e.status ?? '')}</span>])}/>
        </LoadState>
      </Panel>
    </LivePage>
  );
}

export function RelationshipsView({ kind, id }: { kind: string; id: string }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  return <Gate title={copy.common.related}><RelationshipsInner kind={kind} id={id}/></Gate>;
}

function RelationshipsInner({ kind, id }: { kind: string; id: string }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const { data, loading, error, reload } = useLoad(signal => insightApi.relationships(kind, id, signal), [kind, id]);
  const { can } = useSession();
  const href = (k: string, rid: string) => ({ PROJECT: `/projects/${rid}`, SAMPLE: `/samples/${rid}`, ESTIMATE: `/estimates/${rid}`,
    TRANSACTION: '/finance/transactions', INVENTORY_MOVEMENT: '/inventory/stock',
    // Project evidence IDs identify links, whereas commercial evidence IDs identify uploads.
    OCR_EVIDENCE: !can('DOCUMENT_READ') ? null : kind === 'DOCUMENT' ? `/upload/${rid}` : kind === 'PROJECT' ? `/projects/${id}` : null } as Record<string, string | null>)[k] ?? null;
  return (
    <LivePage title={copy.common.related}>
      <Panel title={String(data?.title ?? copy.common.related)} testId="relationships-panel">
        <LoadState loading={loading} error={error} reload={reload}>
          <Table testId="relationships-table" empty={copy.common.empty} head={['', '', copy.common.amount, '']}
            rows={asList(data?.related).map(r => { const link = href(String(r.kind), String(r.id)); return [copy.common.relations[String(r.relation)] ?? copy.common.related, <span key="l" className="break-words">{String(r.label)}</span>,
              <Amount key="a" value={r.amount}/>, r.kind === 'DOCUMENT' || r.kind === 'OBLIGATION'
                ? <RelatedRecordLink key={`${String(r.kind)}:${String(r.id)}`} kind={r.kind} id={String(r.id)}/>
                : link ? <Link key="o" className="underline" href={link}>{copy.common.open}</Link> : <span key="o">{copy.common.evidence}</span>]; })}/>
        </LoadState>
      </Panel>
    </LivePage>
  );
}

/** Resolve the existing record's authoritative type before navigation; never infer payable direction from a label. */
function RelatedRecordLink({ kind, id }: { kind: 'DOCUMENT' | 'OBLIGATION'; id: string }) {
  const router = useRouter();
  const { locale } = useLanguage();
  const copy = productCopy(locale).common;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  const open = async () => {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) { setError(copy.error); return; }
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    setBusy(true); setError('');
    try {
      let destination: string;
      if (kind === 'DOCUMENT') {
        const record = await lifecycleApi.get(id, controller.signal);
        if (record.kind !== 'INVOICE' && record.kind !== 'BILL') throw new Error(copy.error);
        destination = `/${record.kind === 'BILL' ? 'bills' : 'invoices'}/${id}`;
      } else {
        const record = await obligationsApi.get(id, controller.signal);
        destination = `/records/${record.direction === 'PAYABLE' ? 'payables' : 'receivables'}?obligationId=${id}`;
      }
      if (!controller.signal.aborted) router.push(destination);
    } catch (failure) {
      if (!controller.signal.aborted) setError(errorText(failure, copy.error));
    } finally { if (!controller.signal.aborted) setBusy(false); }
  };
  return <span><Button size="sm" variant="ghost" disabled={busy} onClick={() => void open()}>{copy.open}</Button>{error && <span role="alert" className="block text-sm">{error}</span>}</span>;
}

// ================================================================== F12B financial pack

export function FinancialPackView() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const r = copy.reports;
  const { can } = useSession();
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(today());
  const [range, setRange] = useState<[string, string] | null>(null);
  const [error, setError] = useState('');
  const pack = useLoad(signal => range ? insightApi.pack(range[0], range[1], signal) : Promise.resolve(null), [range?.[0], range?.[1]]);
  if (isDemoMode || !can('FINANCIAL_DATA_READ')) return null;
  const download = async (section: string) => {
    setError('');
    try {
      const file = await insightApi.packCsv(section, range?.[0], range?.[1]);
      const url = URL.createObjectURL(file.blob);
      const a = document.createElement('a');
      a.href = url; a.download = file.fileName ?? `finsight-${section}.csv`; a.click();
      URL.revokeObjectURL(url);
    } catch (failure) { setError(errorText(failure, copy.common.error)); }
  };
  const data = pack.data;
  return (
    <Panel title={r.pack} testId="financial-pack" actions={data ? <Button size="sm" variant="secondary" onClick={() => window.print()} data-testid="pack-print">{r.print}</Button> : undefined}>
      <form className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:items-end print:hidden" onSubmit={e => { e.preventDefault(); setRange([from, to]); }}>
        <Input label={copy.common.from} type="date" dir="ltr" value={from} onChange={e => setFrom(e.target.value)} data-testid="pack-from"/>
        <Input label={copy.common.to} type="date" dir="ltr" value={to} onChange={e => setTo(e.target.value)} data-testid="pack-to"/>
        <Button type="submit" data-testid="pack-generate">{r.generate}</Button>
      </form>
      {error && <p role="alert" className="text-sm">{error}</p>}
      {range && <LoadState loading={pack.loading} error={pack.error} reload={pack.reload}>
        {data && <div className="space-y-5">
          <p className="text-xs">{r.version}: <bdi dir="ltr">{String(data.version)}</bdi> · <bdi dir="ltr">{String(data.from)} → {String(data.to)}</bdi></p>
          {Object.keys(r.sections).map(section => <section key={section} className="space-y-2 break-inside-avoid" data-testid={`pack-section-${section}`}>
            <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-semibold">{r.sections[section]}</h3>
              <Button size="sm" variant="ghost" className="print:hidden" onClick={() => void download(section)} data-testid={`pack-csv-${section}`}>{r.csv}</Button></div>
            <SectionTable value={data[section]} copy={copy}/>
          </section>)}
        </div>}
      </LoadState>}
    </Panel>
  );
}

function packLabel(key: string, copy: ProductCopy): string {
  const labels = copy.reports.labels;
  return (
    labels[key] ??
    copy.planning.categories[key] ??
    copy.planning.goals.metrics[key] ??
    copy.planning.goals.statuses[key] ??
    copy.planning.budgets.statuses[key] ??
    copy.planning.budgets.lineKinds[key] ??
    copy.sampleActuals.recoveryStatus[key] ??
    copy.projectActuals.statuses[key] ??
    key.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').replace(/^\w/, c => c.toUpperCase())
  );
}

function formatPackCell(key: string, raw: unknown, copy: ProductCopy): React.ReactNode {
  if (raw === null || raw === undefined || raw === '') return '—';
  if (typeof raw === 'boolean') return copy.reports.labels[String(raw)] ?? String(raw);
  const str = String(raw);
  if (key.endsWith('Pct')) return <Pct value={raw}/>;
  if (/^-?\d+\.\d+$/.test(str) && key !== 'quantity' && key !== 'compositeScore') return <Amount value={raw}/>;
  if (key === 'eventType' || key === 'category' || key === 'metric' || key === 'lineKind' || key === 'status' || key === 'completeness' || key === 'progressStatus' || key === 'recoveryStatus' || key === 'profitabilityStatus' || key === 'bucket') {
    return packLabel(str, copy);
  }
  return <span className="break-words">{str}</span>;
}

function SectionTable({ value, copy }: { value: unknown; copy: ProductCopy }) {
  const labels = copy.reports.labels;
  if (Array.isArray(value)) {
    const rows = asList(value);
    if (rows.length === 0) return <p className="text-sm">{copy.common.empty}</p>;
    const hiddenCols = new Set(['id', 'version', 'ceiling']);
    const keys = Object.keys(rows[0]).filter(k => !hiddenCols.has(k) && (typeof rows[0][k] !== 'object' || rows[0][k] === null)).slice(0, 9);
    const nestedLists = rows.flatMap(row =>
      Object.entries(row)
        .filter(([, cell]) => Array.isArray(cell) && (cell as unknown[]).length > 0)
        .map(([nestedKey, cell]) => ({ title: `${String(row.name ?? row.code ?? '')} — ${packLabel(nestedKey, copy)}`, items: cell }))
    );
    return (
      <div className="space-y-3">
        <Table
          head={keys.map(k => packLabel(k, copy))}
          rows={rows.map(row => keys.map(k => <span key={k} className="break-words">{formatPackCell(k, row[k], copy)}</span>))}
        />
        {nestedLists.map((nested, idx) => (
          <div key={idx} className="space-y-1.5 ps-3 border-s-2 border-[var(--color-border-subtle)]">
            <h4 className="text-xs font-semibold text-[var(--color-text-secondary)]">{nested.title}</h4>
            <SectionTable value={nested.items} copy={copy}/>
          </div>
        ))}
      </div>
    );
  }
  const obj = asRecord(value);
  const scalars: [string, unknown][] = [];
  const subObjects: [string, Record<string, unknown>][] = [];
  const subArrays: [string, unknown[]][] = [];
  Object.entries(obj).forEach(([k, v]) => {
    if (Array.isArray(v)) subArrays.push([k, v]);
    else if (v && typeof v === 'object') subObjects.push([k, v as Record<string, unknown>]);
    else scalars.push([k, v]);
  });
  return (
    <div className="space-y-3">
      {scalars.length > 0 && (
        <Table
          head={[labels.metricHeader, labels.valueHeader]}
          rows={scalars.map(([k, v]) => [
            <span key="k" className="font-medium">{packLabel(k, copy)}</span>,
            <span key="v">{formatPackCell(k, v, copy)}</span>,
          ])}
        />
      )}
      {subObjects.map(([subKey, subVal]) => {
        const subScalars = Object.entries(subVal).filter(([, x]) => !x || typeof x !== 'object');
        const buckets = subVal.buckets && typeof subVal.buckets === 'object' && !Array.isArray(subVal.buckets)
          ? Object.values(subVal.buckets as Record<string, unknown>).map(b => asRecord(b))
          : [];
        return (
          <div key={subKey} className="space-y-2 pt-1">
            <h4 className="text-sm font-semibold text-[var(--color-text-secondary)]">{packLabel(subKey, copy)}</h4>
            {subScalars.length > 0 && (
              <Table
                head={[labels.metricHeader, labels.valueHeader]}
                rows={subScalars.map(([k, v]) => [
                  <span key="k" className="font-medium">{packLabel(k, copy)}</span>,
                  <span key="v">{formatPackCell(k, v, copy)}</span>,
                ])}
              />
            )}
            {buckets.length > 0 && (
              <Table
                head={[labels.bucket, labels.totalOutstanding, labels.count]}
                rows={buckets.map(b => [
                  <span key="b">{packLabel(String(b.bucket ?? ''), copy)}</span>,
                  <Amount key="a" value={b.totalOutstanding}/>,
                  <bdi key="c" dir="ltr">{String(b.count ?? 0)}</bdi>,
                ])}
              />
            )}
          </div>
        );
      })}
      {subArrays.map(([arrKey, arrVal]) => (
        <div key={arrKey} className="space-y-1.5 pt-1">
          <h4 className="text-sm font-semibold text-[var(--color-text-secondary)]">{packLabel(arrKey, copy)}</h4>
          <SectionTable value={arrVal} copy={copy}/>
        </div>
      ))}
    </div>
  );
}

// ================================================================== Copilot

export function CopilotChat() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const { business } = useSession();
  return <Gate title={copy.copilot.title}><CopilotInner key={`${business?.businessId ?? 'none'}-${locale}`}/></Gate>;
}

function CopilotInner() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const c = copy.copilot;
  const status = useLoad(signal => copilotApi.status(signal), []);
  const [question, setQuestion] = useState('');
  const [turns, setTurns] = useState<{ q: string; r: CopilotResponse; provenance: CopilotProvenance }[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const abort = useRef<AbortController | null>(null);
  const ask = async (text: string) => {
    const q = text.trim();
    if (!q || busy) return;
    setBusy(true); setError('');
    abort.current?.abort();
    const controller = new AbortController();
    abort.current = controller;
    const conversation = turns.slice(-4).flatMap(t => [{ role: 'user' as const, content: t.q.slice(0, 1000) }, { role: 'assistant' as const, content: t.r.answer.slice(0, 1000) }]);
    try {
      const r = await copilotApi.ask(q, locale, conversation, controller.signal);
      // Classified when received, against the status the user saw while asking.
      setTurns(prev => [...prev, { q, r, provenance: copilotProvenance(r, status.data?.mode) }]);
      setQuestion('');
    } catch (failure) {
      if ((failure as Error)?.name !== 'AbortError') setError(errorText(failure, copy.common.error));
    } finally { setBusy(false); }
  };
  const mode = String(status.data?.mode ?? 'deterministic');
  return (
    <LivePage title={c.title}>
      <Panel title={c.title} testId="copilot" actions={status.data ? <Pill testId="copilot-mode">{c.modes[mode] ?? mode}</Pill> : undefined}>
        <p className="text-sm">{c.grounded}</p>
        {status.data && !status.data.moduleEnabled ? <p role="status">{c.disabled}</p> : <>
          <div className="flex flex-wrap gap-2">{c.suggestions.map(s => <Button key={s} size="sm" variant="secondary" disabled={busy} onClick={() => void ask(s)}>{s}</Button>)}</div>
          <ol className="space-y-4" aria-live="polite" data-testid="copilot-conversation">
            {turns.map((t, idx) => { const p = t.provenance; return <li key={idx} className="space-y-2">
              <p className="rounded-lg bg-[var(--color-surface-sunken,#f3f4f6)] p-3 text-sm font-medium break-words">{t.q}</p>
              <div className="rounded-lg border border-[var(--color-border-subtle)] p-3 space-y-2" data-testid="copilot-turn" data-provenance={p.kind}>
                {/* DEF-08: per-answer provenance from this response, not the page-level status. */}
                <p data-testid="copilot-provenance" data-provenance={p.kind}
                  className={`inline-block rounded-full border px-2 py-0.5 text-xs font-semibold ${p.kind === 'ai' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-amber-200 bg-amber-50 text-amber-900'}`}>
                  {c.provenance[p.kind]}{p.kind === 'ai' && t.r.model && <> (<bdi dir="ltr">{t.r.model}</bdi>)</>}
                </p>
                {p.kind === 'fallback' && p.cause && <p className="text-xs" role="status" data-testid="copilot-fallback-cause">{c.fallbackCause[p.cause]}</p>}
                <p className="text-sm whitespace-pre-wrap break-words" data-testid="copilot-answer">{t.r.answer}</p>
                {p.kind !== 'fallback' && t.r.reasonCode && c.reasons[t.r.reasonCode] && <p className="text-xs" role="status">{c.reasons[t.r.reasonCode]}</p>}
                {t.r.evidence.length > 0 && <details><summary className="cursor-pointer text-xs font-medium">{c.evidence} ({t.r.evidence.length})</summary>
                  <ul className="mt-2 space-y-1 text-xs">{t.r.evidence.slice(0, 20).map(e => <li key={e.id} className="break-all"><bdi dir="ltr" className="font-mono">{e.id}</bdi>: <bdi dir="ltr">{e.value}</bdi></li>)}</ul></details>}
              </div>
            </li>; })}
          </ol>
          <form className="flex flex-col gap-2 sm:flex-row sm:items-end" onSubmit={e => { e.preventDefault(); void ask(question); }}>
            <div className="flex-1"><Input label={c.title} placeholder={c.placeholder} value={question} maxLength={1000} onChange={e => setQuestion(e.target.value)} data-testid="copilot-question"/></div>
            <Button type="submit" disabled={busy || !question.trim()} isLoading={busy} data-testid="copilot-send">{busy ? c.thinking : c.send}</Button>
            {turns.length > 0 && <Button type="button" variant="ghost" onClick={() => setTurns([])}>{c.clear}</Button>}
          </form>
          {error && <p role="alert" className="text-sm">{error}</p>}
        </>}
      </Panel>
    </LivePage>
  );
}

// ================================================================== WhatsApp link card (settings)

export function WhatsAppLinkCard() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const w = copy.whatsapp;
  const { can } = useSession();
  const { data, loading, error, reload } = useLoad(signal => whatsappInboundApi.status(signal), []);
  const [phone, setPhone] = useState('');
  const [consent, setConsent] = useState(false);
  const [code, setCode] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  if (isDemoMode || !can('FINANCIAL_DATA_READ')) return null;
  const status = String(data?.status ?? 'NOT_LINKED');
  return (
    <Panel title={w.title} testId="whatsapp-link-card" actions={data ? <Pill tone={status === 'ACTIVE' ? 'good' : 'neutral'} testId="whatsapp-link-status">{w.statuses[status] ?? status}</Pill> : undefined}>
      <p className="text-sm">{w.help}</p>
      <LoadState loading={loading} error={error} reload={reload}>
        {data && !data.configured ? <p className="text-sm">{w.notConfigured}</p> : <>
          {data?.phoneLast4 != null && <p className="text-sm">{w.ending}: <bdi dir="ltr">••{String(data.phoneLast4)}</bdi></p>}
          {code && <p className="rounded-lg border p-3 text-sm" data-testid="whatsapp-link-code">{w.code} <bdi dir="ltr" className="font-mono font-semibold">LINK {code}</bdi></p>}
          <form className="space-y-3" onSubmit={async e => { e.preventDefault(); setMessage(''); try { const r = await whatsappInboundApi.link(phone.trim(), consent, locale); setCode(String(r.verificationCode)); reload(); } catch (f) { setMessage(errorText(f, copy.common.error)); } }}>
            <Input label={w.phone} type="tel" dir="ltr" value={phone} onChange={e => setPhone(e.target.value)} required data-testid="whatsapp-phone"/>
            <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={consent} onChange={e => setConsent(e.target.checked)} data-testid="whatsapp-consent"/>{w.consent}</label>
            <div className="flex flex-wrap gap-2">
              <Button type="submit" disabled={!consent || !phone.trim()} data-testid="whatsapp-link-submit">{w.link}</Button>
              {(status === 'ACTIVE' || status === 'PENDING') && <Button type="button" variant="secondary" onClick={async () => { await whatsappInboundApi.revoke(); setCode(null); reload(); }}>{w.revoke}</Button>}
            </div>
          </form>
          {message && <p role="alert" className="text-sm">{message}</p>}
        </>}
      </LoadState>
    </Panel>
  );
}
