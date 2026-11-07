"use client";
import { Card } from '@/components/ui/Card';
import { formatPkr } from '@/lib/utils/decimal-display';
import type { FundingCopy } from '@/lib/i18n/translations/funding';
import { FundingTimeline, type FundingTimelineRow } from './FundingTimeline';
/** View data must be mapped from the agreed backend response, never derived from events. */
export type FundingScenarioView = { peak: string; firstDeficit: string | null; recovery: string | null; closing: string; inflows: string; outflows: string };
export type FundingComparisonView = { baseline: FundingScenarioView; alternative: FundingScenarioView; rows: FundingTimelineRow[]; opening: string; source: 'LEDGER' | 'MANUAL'; asOf: string; evaluatedAt: string; fundingDifference: string; closingDifference: string };
export function FundingResults({ result, copy }: { result: FundingComparisonView; copy: FundingCopy }) {
 const money = (value: string) => <bdi dir="ltr">{formatPkr(value)} PKR</bdi>;
 return <section aria-label={copy.results} className="min-w-0 space-y-5">
  <h2 className="text-xl font-semibold">{copy.results}</h2><p className="text-sm text-[var(--color-text-secondary)]">{copy.resultHelp}</p>
  <Card className="space-y-2 text-sm"><p>{copy.sourceLabel}: <strong>{result.source === 'LEDGER' ? copy.ledger : copy.manual}</strong></p><p>{copy.openingValue}: {money(result.opening)}</p><p>{copy.asOf}: <bdi dir="ltr">{result.asOf}</bdi></p><p>{copy.evaluated}: <bdi dir="ltr">{result.evaluatedAt}</bdi></p></Card>
  <div className="grid min-w-0 gap-4 lg:grid-cols-2">{(['baseline', 'alternative'] as const).map(key => { const scenario = result[key]; return <Card key={key} className="min-w-0 space-y-4"><h3 className="text-lg font-semibold">{copy[key]}</h3><dl className="space-y-3">{[
   [copy.peak, money(scenario.peak)], [copy.first, scenario.firstDeficit ? <bdi key="first" dir="ltr">{scenario.firstDeficit}</bdi> : copy.none],
   [copy.recovery, scenario.recovery ? <bdi key="recovery" dir="ltr">{scenario.recovery}</bdi> : scenario.firstDeficit ? copy.noRecovery : copy.none],
   [copy.closing, money(scenario.closing)], [copy.totalIn, money(scenario.inflows)], [copy.totalOut, money(scenario.outflows)]
  ].map(([label, value], index) => <div key={index} className="flex min-w-0 flex-wrap justify-between gap-x-3 gap-y-1 border-b border-[var(--color-border-subtle)] pb-2"><dt className="text-sm text-[var(--color-text-secondary)]">{label}</dt><dd className="text-sm font-semibold break-all">{value}</dd></div>)}</dl></Card>; })}</div>
  <Card className="space-y-3"><h3 className="font-semibold">{copy.difference}</h3><p className="text-sm text-[var(--color-text-secondary)]">{copy.differenceHelp}</p><dl className="grid gap-3 sm:grid-cols-2"><div><dt className="text-sm">{copy.peak}</dt><dd className="font-semibold">{money(result.fundingDifference)}</dd></div><div><dt className="text-sm">{copy.closing}</dt><dd className="font-semibold">{money(result.closingDifference)}</dd></div></dl></Card>
  <FundingTimeline rows={result.rows} copy={copy} />
 </section>;
}
