"use client";
import { useId } from 'react';
import { Card } from '@/components/ui/Card';
import { formatPkr } from '@/lib/utils/decimal-display';
import type { FundingCopy } from '@/lib/i18n/translations/funding';
/** Presentation model only; the adapter supplies server-calculated positions. */
export type FundingTimelineRow = { date: string; baseline: string; alternative: string };
export function chartGeometry(rows: FundingTimelineRow[]) {
 const units = (s: string) => BigInt(s.replace('.', ''));
 let magnitude = BigInt(1);
 for (const row of rows) for (const amount of [row.baseline, row.alternative]) { const n = units(amount), abs = n < BigInt(0) ? -n : n; if (abs > magnitude) magnitude = abs; }
 // Number conversions are bounded dimensionless SVG coordinates, never financial amounts.
 const x = (index: number) => 45 + index * 810 / Math.max(rows.length - 1, 1);
 const y = (amount: string) => 150 - Number(units(amount) * BigInt(100000) / magnitude) / 1000;
 return { baseline: rows.map((row, i) => `${x(i)},${y(row.baseline)}`).join(' '), alternative: rows.map((row, i) => `${x(i)},${y(row.alternative)}`).join(' ') };
}
export function FundingTimeline({ rows, copy }: { rows: FundingTimelineRow[]; copy: FundingCopy }) {
 const id = useId(), geometry = chartGeometry(rows);
 return <Card className="min-w-0 space-y-3">
  <h2 className="text-lg font-semibold">{copy.timeline}</h2><p id={id} className="text-sm text-[var(--color-text-secondary)]">{copy.timelineHelp}</p>
  <div className="flex flex-wrap gap-4 text-sm"><span><span aria-hidden="true" className="me-2 inline-block h-1 w-6 bg-[#0D5C52]" />{copy.baseline}</span><span><span aria-hidden="true" className="me-2 inline-block h-1 w-6 bg-[#8B5E3C]" />{copy.alternative}</span></div>
  <svg viewBox="0 0 900 300" role="img" aria-labelledby={id + '-title'} aria-describedby={id} className="block w-full" style={{direction: 'ltr'}}><title id={id + '-title'}>{copy.timeline}</title>
   <rect x="45" y="150" width="810" height="110" fill="var(--color-status-error)" opacity="0.04" /><line x1="45" x2="855" y1="150" y2="150" stroke="currentColor" strokeDasharray="5 5" opacity="0.5" />
   <text x="45" y="140" fontSize="12" fill="currentColor">0 PKR</text>
   <polyline points={geometry.baseline} fill="none" stroke="#0D5C52" strokeWidth="3" /><polyline points={geometry.alternative} fill="none" stroke="#8B5E3C" strokeWidth="3" strokeDasharray="8 4" />
   <text x="45" y="285" fontSize="12" fill="currentColor">{rows[0]?.date}</text><text x="855" y="285" textAnchor="end" fontSize="12" fill="currentColor">{rows.at(-1)?.date}</text>
  </svg>
  <details className="rounded border border-[var(--color-border-default)] p-3"><summary className="cursor-pointer text-sm font-semibold focus-visible:outline-2">{copy.table}</summary>
   <ul className="mt-3 max-h-96 space-y-2 overflow-y-auto" aria-label={copy.table}>{rows.map(row => <li key={row.date} className="grid min-w-0 gap-1 rounded bg-[var(--color-surface-subtle)] p-3 text-sm sm:grid-cols-3"><bdi dir="ltr">{row.date}</bdi><span>{copy.baseline}: <bdi dir="ltr" className={row.baseline.startsWith('-') ? 'text-[var(--color-status-error)]' : ''}>{formatPkr(row.baseline)} PKR</bdi></span><span>{copy.alternative}: <bdi dir="ltr" className={row.alternative.startsWith('-') ? 'text-[var(--color-status-error)]' : ''}>{formatPkr(row.alternative)} PKR</bdi></span></li>)}</ul>
  </details>
 </Card>;
}
