"use client";

import Link from 'next/link';
import { Card } from '@/components/ui/Card';
import { apiClient } from '@/lib/api/client';
import { insightApi, planningApi, stockApi } from '@/lib/api/product';
import { useLanguage } from '@/lib/i18n/context';
import { productCopy } from '@/lib/i18n/translations/product';
import { workspaceCopy } from '@/lib/i18n/translations/workspace';
import { Amount, LoadState, Pill, asList, asRecord, monthStart, today, useLoad } from './shared';

/** Keys rendered by this module (all backed by authoritative product sources). */
export const PRODUCT_WIDGETS = ['transactions', 'receivables', 'payables', 'invoices', 'projects', 'costing', 'inventory', 'production',
  'commissions', 'multi-currency', 'budgets', 'goals', 'profitability', 'break-even', 'action-center', 'calendar', 'funding-gap', 'reports'] as const;

const LINKS: Record<string, string> = {
  transactions: '/finance/transactions', receivables: '/records/receivables', payables: '/records/payables', invoices: '/invoices',
  projects: '/projects', costing: '/estimates', inventory: '/inventory/stock', production: '/samples', commissions: '/finance/commissions',
  'multi-currency': '/finance/fx', budgets: '/planning', goals: '/planning', profitability: '/planning', 'break-even': '/planning',
  'action-center': '/intelligence', calendar: '/operations/calendar', 'funding-gap': '/finance/funding', reports: '/reports',
};

function Row({ label, value }: { label: string; value: unknown }) {
  return <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm"><dt>{label}</dt><dd><Amount value={value}/></dd></div>;
}

export function ProductWidget({ widgetKey, preview }: { widgetKey: string; preview: boolean }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const names = workspaceCopy(locale).names as Record<string, string>;
  return (
    <Card padding="md" className="h-full min-w-0 space-y-3" data-testid={`widget-${widgetKey}`}>
      <h2 className="font-semibold">{names[widgetKey] ?? widgetKey}</h2>
      {preview ? <p className="text-sm text-[var(--color-text-muted)]">{copy.common.authoritative}</p> : <WidgetBody widgetKey={widgetKey}/>}
      <Link href={LINKS[widgetKey] ?? '/'} className="text-sm underline text-[var(--color-brand-primary)]">{copy.common.open}</Link>
    </Card>
  );
}

function WidgetBody({ widgetKey }: { widgetKey: string }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const i = copy.intelligence;
  const needsPosition = ['transactions', 'receivables', 'payables', 'projects', 'costing', 'production', 'inventory'].includes(widgetKey);
  const needsPnl = ['invoices', 'commissions', 'multi-currency', 'profitability'].includes(widgetKey);
  const load = useLoad<Record<string, unknown> | null>(signal => {
    if (needsPosition) return apiClient<Record<string, unknown>>('/intelligence/position', { signal });
    if (needsPnl) return planningApi.pnl(monthStart(), today(), signal);
    if (widgetKey === 'budgets') return planningApi.budgets(signal) as Promise<Record<string, unknown>>;
    if (widgetKey === 'goals') return planningApi.goals(signal) as Promise<Record<string, unknown>>;
    if (widgetKey === 'break-even') return planningApi.breakEven(monthStart(), today(), signal);
    if (widgetKey === 'action-center') return insightApi.actions(signal) as unknown as Promise<Record<string, unknown>>;
    if (widgetKey === 'calendar') return insightApi.calendar(today(), undefined, signal);
    if (widgetKey === 'inventory') return stockApi.register(signal) as unknown as Promise<Record<string, unknown>>;
    return Promise.resolve(null);
  }, [widgetKey]);
  const d = asRecord(load.data);
  const body = (() => {
    switch (widgetKey) {
      case 'transactions': return <dl className="space-y-1"><Row label={i.cash} value={d.cashBalance}/></dl>;
      case 'receivables': return <dl className="space-y-1"><Row label={i.ar} value={d.receivablesOutstanding}/><Row label={i.overdueAr} value={d.overdueReceivables}/></dl>;
      case 'payables': return <dl className="space-y-1"><Row label={i.ap} value={d.payablesOutstanding}/><Row label={i.overdueAp} value={d.overduePayables}/></dl>;
      case 'inventory': return <dl className="space-y-1"><Row label={i.inventory} value={d.inventoryValue}/><p className="text-sm">{copy.stock.belowReorder}: <bdi dir="ltr">{String(d.itemsBelowReorder ?? 0)}</bdi></p></dl>;
      case 'projects': return asList(d.activeProjects).length === 0 ? <p className="text-sm">{copy.common.empty}</p>
        : <ul className="space-y-1 text-sm">{asList(d.activeProjects).map(p => <li key={String(p.projectId)} className="flex flex-wrap justify-between gap-2"><bdi dir="ltr">{String(p.code)}</bdi><Amount value={p.profit}/></li>)}</ul>;
      case 'costing': return <p className="text-sm"><bdi dir="ltr">{String(d.activeEstimates ?? 0)}</bdi></p>;
      case 'production': { const s = asRecord(d.samples); return <dl className="space-y-1"><Row label={copy.sampleActuals.total} value={s.actualCost}/><Row label={copy.sampleActuals.recognized} value={s.linkedRecovery}/></dl>; }
      case 'invoices': return <dl className="space-y-1"><Row label={copy.planning.pnl.invoices} value={d.invoiceRevenue}/><Row label={copy.planning.pnl.creditNotes} value={d.creditNotes}/></dl>;
      case 'commissions': return <dl className="space-y-1"><Row label={copy.projectActuals.commission} value={d.commissionCost}/></dl>;
      case 'multi-currency': return <dl className="space-y-1"><Row label={copy.planning.pnl.fx} value={d.realizedFx}/></dl>;
      case 'profitability': return <dl className="space-y-1"><Row label={copy.planning.pnl.revenue} value={d.revenue}/><Row label={copy.planning.pnl.grossProfit} value={d.grossProfit}/><Row label={copy.planning.pnl.operatingProfit} value={d.operatingProfit}/></dl>;
      case 'break-even': return d.status === 'AVAILABLE' ? <dl className="space-y-1"><Row label={copy.planning.breakEven.ber} value={d.breakEvenRevenue}/></dl>
        : <p className="text-sm">{copy.planning.breakEven.reasons[String(d.reason)] ?? String(d.reason ?? '')}</p>;
      case 'budgets': { const active = asList(d.budgets).filter(b => b.status === 'ACTIVE'); return active.length === 0 ? <p className="text-sm">{copy.common.empty}</p>
        : <ul className="space-y-1 text-sm">{active.slice(0, 3).map(b => { const v = asRecord(b.variance); return <li key={String(b.id)} className="flex flex-wrap justify-between gap-2"><span>{String(b.name)}</span><span><Amount value={v.actualExpense}/> / <Amount value={v.budgetExpense}/></span></li>; })}</ul>; }
      case 'goals': return asList(d.goals).length === 0 ? <p className="text-sm">{copy.common.empty}</p>
        : <ul className="space-y-1 text-sm">{asList(d.goals).filter(g => g.status === 'ACTIVE').slice(0, 4).map(g => <li key={String(g.id)} className="flex flex-wrap justify-between gap-2"><span>{String(g.name)}</span><Pill>{copy.planning.goals.statuses[String(g.progressStatus)] ?? String(g.progressStatus)}</Pill></li>)}</ul>;
      case 'action-center': return asList(d.actions).length === 0 ? <p className="text-sm">{i.noActions}</p>
        : <ul className="space-y-2 text-sm">{asList(d.actions).slice(0, 3).map(a => <li key={String(a.key)}><Pill tone={a.priority === 'HIGH' ? 'bad' : 'warn'}>{i.priority[String(a.priority)] ?? String(a.priority)}</Pill> <span className="break-words">{String(a.title)}</span></li>)}</ul>;
      case 'calendar': return asList(d.events).length === 0 ? <p className="text-sm">{copy.calendar.empty}</p>
        : <ul className="space-y-1 text-sm">{asList(d.events).slice(0, 5).map((e, idx) => <li key={idx} className="flex flex-wrap justify-between gap-2"><bdi dir="ltr">{String(e.date)}</bdi><span className="break-words">{copy.calendar.types[String(e.type)] ?? String(e.label ?? '')}</span></li>)}</ul>;
      case 'funding-gap': return <p className="text-sm">{copy.projectActuals.commitments}</p>;
      case 'reports': return <p className="text-sm">{copy.reports.pack}</p>;
      default: return null;
    }
  })();
  return <LoadState loading={load.loading} error={load.error} reload={load.reload}>{body}</LoadState>;
}
