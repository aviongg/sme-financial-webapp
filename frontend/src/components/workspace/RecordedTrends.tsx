"use client";
import Link from 'next/link';
import { useId } from 'react';
import { Card } from '@/components/ui/Card';
import { FinancialValue } from '@/components/ui/FinancialValue';
import type { MonthlyRecordResponse } from '@/lib/api/contracts';
import { useLanguage } from '@/lib/i18n/context';
import { workspaceCopy } from '@/lib/i18n/translations/workspace';
import { formatPKR } from '@/lib/utils/currency';

/** Chart geometry only: financial values are read unchanged from the monthly service. */
export function RecordedTrends({ records }: { records: MonthlyRecordResponse[] }) {
  const { locale } = useLanguage(), copy = workspaceCopy(locale), titleId = useId();
  const history = [...records].sort((a, b) => a.month.localeCompare(b.month)).slice(-6);
  const latest = history.at(-1);
  const scale = Math.max(1, ...history.flatMap(record => [record.revenue, record.operatingExpenses]));
  return <Card padding="md" className="h-full space-y-4">
    <div><h2 id={titleId} className="font-heading text-lg font-semibold">{copy.names['financial-summary']}</h2><p className="mt-1 text-xs text-[var(--color-text-muted)]">{copy.trendHelp}</p></div>
    {!latest ? <p className="text-sm">{copy.noRecords}</p> : <>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,180px),1fr))] gap-3"><div className="min-w-0 rounded-lg bg-[var(--color-brand-surface)] p-3"><p className="text-xs mb-2">{copy.revenue}</p><FinancialValue value={latest.revenue} size="md" className="max-w-full whitespace-normal break-all"/><p className="text-xs mt-1"><bdi>{latest.month}</bdi></p></div><div className="min-w-0 rounded-lg bg-[var(--color-surface-subtle)] p-3"><p className="text-xs mb-2">{copy.expenses}</p><FinancialValue value={latest.operatingExpenses} size="md" className="max-w-full whitespace-normal break-all"/><p className="text-xs mt-1"><bdi>{latest.month}</bdi></p></div></div>
      <div dir="ltr" className="overflow-x-auto"><svg viewBox="0 0 600 160" className="w-full min-w-[360px]" role="img" aria-labelledby={titleId}>
        {[30, 80, 130].map(y => <line key={y} x1="12" y1={y} x2="588" y2={y} stroke="var(--color-border-subtle)" strokeDasharray="3 3"/>)}
        {history.map((record, index) => { const x = 20 + index * (560 / history.length), group = 560 / history.length; return <g key={record.month}><title>{record.month}: {copy.revenue} {formatPKR(record.revenue, { locale })}; {copy.expenses} {formatPKR(record.operatingExpenses, { locale })}</title><rect x={x + group / 2 - 23} y={130 - record.revenue / scale * 108} width="20" height={record.revenue / scale * 108} rx="3" fill="var(--color-brand-primary)"/><rect x={x + group / 2 + 3} y={130 - record.operatingExpenses / scale * 108} width="20" height={record.operatingExpenses / scale * 108} rx="3" fill="#64748b"/><text x={x + group / 2} y="151" textAnchor="middle" fill="var(--color-text-muted)" fontSize="11">{record.month}</text></g>; })}
      </svg></div>
      <div className="flex flex-wrap gap-4 text-xs"><span className="inline-flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-sm bg-[var(--color-brand-primary)]"/>{copy.revenue}</span><span className="inline-flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-sm bg-slate-500"/>{copy.expenses}</span></div>
      <details className="text-sm"><summary className="cursor-pointer font-medium">{copy.viewRecords}</summary><div className="overflow-x-auto mt-3"><table className="w-full text-start"><thead><tr><th className="p-2 text-start">{copy.period}</th><th className="p-2 text-start">{copy.revenue}</th><th className="p-2 text-start">{copy.expenses}</th></tr></thead><tbody>{history.map(record => <tr key={record.month} className="border-t border-[var(--color-border-subtle)]"><th scope="row" className="p-2 text-start font-normal"><bdi>{record.month}</bdi></th><td className="p-2 whitespace-nowrap">{formatPKR(record.revenue, { locale })}</td><td className="p-2 whitespace-nowrap">{formatPKR(record.operatingExpenses, { locale })}</td></tr>)}</tbody></table></div></details>
    </>}
    <Link href="/records" className="inline-block text-sm font-medium text-[var(--color-brand-primary)] underline underline-offset-4">{copy.viewRecords}</Link>
  </Card>;
}
