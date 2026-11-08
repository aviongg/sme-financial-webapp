import { ArrowDownLeft, ArrowUpRight, CalendarClock, CheckCircle2, Sparkles, TrendingUp } from 'lucide-react';
import type { LandingCopy } from '@/lib/i18n/translations/landing';
import { cn } from '@/lib/utils/cn';
import { SampleTag } from './LandingPrimitives';
import styles from './landing.module.css';

/* Every figure below is an illustrative sample value. Nothing here reads, derives or represents live data. */

const money = (amount: string) => <span dir="ltr" className="tabular-nums">PKR {amount}</span>;

function Gauge({ value }: { value: number }) {
  return (
    <svg viewBox="0 0 120 68" className="w-full max-w-[150px] h-auto" aria-hidden="true">
      <path d="M10 62 A50 50 0 0 1 110 62" fill="none" stroke="var(--color-border-default)" strokeWidth="10" strokeLinecap="round" />
      <path d="M10 62 A50 50 0 0 1 110 62" fill="none" stroke="url(#lp-gauge)" strokeWidth="10" strokeLinecap="round" pathLength={100} strokeDasharray={`${value} 100`} />
      <defs>
        <linearGradient id="lp-gauge" x1="0" x2="1" y1="0" y2="0">
          <stop offset="0" stopColor="#60A5FA" />
          <stop offset="1" stopColor="#253DBA" />
        </linearGradient>
      </defs>
    </svg>
  );
}

function Sparkline() {
  return (
    <svg viewBox="0 0 160 44" className="w-full h-11" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient id="lp-spark" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="#4F6BF5" stopOpacity="0.28" />
          <stop offset="1" stopColor="#4F6BF5" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d="M0 34 L20 30 L40 32 L60 24 L80 26 L100 18 L120 20 L140 12 L160 8 L160 44 L0 44 Z" fill="url(#lp-spark)" />
      <path d="M0 34 L20 30 L40 32 L60 24 L80 26 L100 18 L120 20 L140 12 L160 8" fill="none" stroke="#3B54E6" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

const outlookBars = [
  [62, 30], [40, 52], [70, 24], [34, 46], [56, 38], [28, 64], [48, 34], [66, 28], [38, 44], [58, 30],
];

export function HeroPreview({ copy }: { copy: LandingCopy }) {
  const p = copy.preview;
  return (
    <figure className="relative" aria-label={p.summary}>
      <div aria-hidden="true" className="absolute -inset-6 rounded-[32px] bg-[radial-gradient(closest-side,rgba(79,107,245,0.18),transparent)] blur-2xl" />
      <div className={cn('relative rounded-[20px] border border-[var(--color-border-default)] bg-white shadow-[var(--lp-shadow-float)] overflow-hidden', styles.float)}>
        <div className="flex items-center justify-between gap-3 border-b border-[var(--color-border-subtle)] px-4 py-3 bg-[var(--color-surface-subtle)]">
          <div className="flex items-center gap-2 min-w-0">
            <span aria-hidden="true" className="hidden sm:flex gap-1.5 shrink-0">
              <span className="h-2.5 w-2.5 rounded-full bg-[#CBD5E1]" /><span className="h-2.5 w-2.5 rounded-full bg-[#CBD5E1]" /><span className="h-2.5 w-2.5 rounded-full bg-[#CBD5E1]" />
            </span>
            <span className="sm:ms-2 truncate text-[13px] font-semibold text-[var(--lp-navy)]">{p.business}</span>
            <span className="hidden sm:inline text-[12px] text-[var(--color-text-muted)]">· {p.period}</span>
          </div>
          <SampleTag>{p.caption}</SampleTag>
        </div>

        <div aria-hidden="true" className="grid gap-3 p-3 sm:p-4 sm:grid-cols-2">
          <div className="rounded-[var(--radius-xl)] border border-[var(--color-border-default)] p-4">
            <div className="flex items-center justify-between gap-2">
              <p className="text-[13px] font-semibold text-[var(--color-text-secondary)]">{p.health}</p>
              <span className="inline-flex items-center gap-1 rounded-full border border-[var(--color-health-stable-border)] bg-[var(--color-health-stable-bg)] px-2 py-0.5 text-[11px] font-semibold text-[var(--color-health-stable)]">
                <CheckCircle2 className="w-3 h-3" />{p.band}
              </span>
            </div>
            <div className="mt-2 flex flex-col items-center">
              <Gauge value={72} />
              <p dir="ltr" className="-mt-7 font-heading text-[30px] font-extrabold text-[var(--lp-navy)] tabular-nums leading-none">72<span className="text-[14px] font-semibold text-[var(--color-text-muted)]">/100</span></p>
            </div>
            <p className="mt-3 flex items-center gap-1.5 text-[12px] text-[var(--color-text-secondary)]"><TrendingUp className="w-3.5 h-3.5 shrink-0 text-[var(--color-health-strong)]" />{p.healthNote}</p>
          </div>

          <div className="rounded-[var(--radius-xl)] border border-[var(--color-border-default)] p-4 flex flex-col">
            <p className="text-[13px] font-semibold text-[var(--color-text-secondary)]">{p.cash}</p>
            <p className="mt-2 font-heading text-[22px] sm:text-[24px] font-extrabold text-[var(--lp-navy)] leading-tight">{money('1,842,500')}</p>
            <p className="mt-1 text-[12px] font-semibold text-[var(--color-health-strong)]">{p.cashDelta}</p>
            <div className="mt-auto pt-3" dir="ltr"><Sparkline /></div>
          </div>

          <div className="rounded-[var(--radius-xl)] border border-[var(--color-border-default)] p-4">
            <div className="flex items-center justify-between gap-2">
              <p className="text-[13px] font-semibold text-[var(--color-text-secondary)]">{p.outlook}</p>
              <p className="text-[11px] text-[var(--color-text-secondary)]">{p.lowest}: <span className="font-semibold text-[var(--color-health-attention)]">{p.lowestWhen}</span></p>
            </div>
            <div dir="ltr" className="mt-3 flex h-20 items-end gap-1.5">
              {outlookBars.map(([inflow, outflow], index) => (
                <div key={index} className="flex flex-1 items-end gap-[2px] h-full">
                  <span className="flex-1 rounded-t-[3px] bg-[#4F6BF5]" style={{ height: `${inflow}%` }} />
                  <span className={cn('flex-1 rounded-t-[3px]', index === 5 ? 'bg-[#F59E0B]' : 'bg-[#CBD5E1]')} style={{ height: `${outflow}%` }} />
                </div>
              ))}
            </div>
            <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-[var(--color-text-secondary)]">
              <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm bg-[#4F6BF5]" />{p.expectedIn}</span>
              <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm bg-[#CBD5E1]" />{p.expectedOut}</span>
            </div>
          </div>

          <div className="rounded-[var(--radius-xl)] border border-[var(--color-border-default)] p-4">
            <p className="text-[13px] font-semibold text-[var(--color-text-secondary)]">{p.upcoming}</p>
            <ul className="mt-2 divide-y divide-[var(--color-border-subtle)]">
              {p.items.map(item => (
                <li key={item.label} className="flex items-center gap-3 py-2">
                  <span className={cn('flex h-7 w-7 shrink-0 items-center justify-center rounded-full', item.direction === 'in' ? 'bg-[var(--color-health-strong-bg)] text-[var(--color-health-strong)]' : 'bg-[var(--lp-tint)] text-[var(--lp-blue)]')}>
                    {item.direction === 'in' ? <ArrowDownLeft className="w-3.5 h-3.5" /> : <ArrowUpRight className="w-3.5 h-3.5" />}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-baseline justify-between gap-2">
                      <span className="min-w-0 text-[12px] font-semibold text-[var(--lp-navy)]">{item.label}</span>
                      <span className="shrink-0 text-[12px] font-semibold text-[var(--lp-navy)]">{money(item.amount)}</span>
                    </span>
                    <span className="block text-[11px] text-[var(--color-text-secondary)]">{item.when}</span>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </figure>
  );
}

/* Cash section: a 30-day projected balance with a visible dip in week three. */
const balance = [62, 64, 63, 66, 68, 67, 70, 69, 66, 63, 60, 56, 52, 47, 42, 37, 33, 30, 31, 36, 42, 49, 55, 58, 61, 63, 64, 66, 67, 69];

export function ForecastVisual({ copy }: { copy: LandingCopy }) {
  const c = copy.cash;
  const width = 300, height = 120;
  const points = balance.map((value, index) => `${(index / (balance.length - 1)) * width},${height - value * 1.4}`).join(' ');
  return (
    <figure aria-label={c.chartSummary} className="relative rounded-[20px] border border-white/10 bg-[var(--lp-navy-raised)] p-4 sm:p-6 shadow-[0_30px_60px_-30px_rgba(0,0,0,0.6)]">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="font-heading text-[16px] font-semibold text-white">{c.chartTitle}</p>
        <SampleTag tone="dark">{c.chartCaption}</SampleTag>
      </div>
      <div aria-hidden="true">
        <div dir="ltr" className="relative mt-5">
          <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-[150px] sm:h-[190px]" preserveAspectRatio="none">
            <defs>
              <linearGradient id="lp-forecast" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0" stopColor="#60A5FA" stopOpacity="0.35" />
                <stop offset="1" stopColor="#60A5FA" stopOpacity="0" />
              </linearGradient>
            </defs>
            {[30, 60, 90].map(y => <line key={y} x1="0" x2={width} y1={y} y2={y} stroke="rgba(255,255,255,0.08)" strokeDasharray="3 4" vectorEffect="non-scaling-stroke" />)}
            <rect x={width * 0.43} y="0" width={width * 0.2} height={height} fill="rgba(245,158,11,0.16)" />
            <polygon points={`0,${height} ${points} ${width},${height}`} fill="url(#lp-forecast)" />
            <polyline points={points} fill="none" stroke="#93C5FD" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
          </svg>
          <div dir="ltr" className="mt-2 flex justify-between text-[11px] text-slate-400 tabular-nums"><span>1</span><span>10</span><span>20</span><span>30</span></div>
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
          <div className="rounded-[var(--radius-xl)] border border-amber-300/30 bg-amber-400/10 p-3">
            <p className="flex items-center gap-2 text-[13px] font-semibold text-amber-200"><CalendarClock className="w-4 h-4 shrink-0" />{c.pressure}</p>
            <p className="mt-1 text-[12px] text-amber-100/90">{c.pressureBody}</p>
          </div>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-[12px] text-slate-300">
            <span className="inline-flex items-center gap-1.5"><span className="h-0.5 w-4 rounded bg-[#93C5FD]" />{c.legendBalance}</span>
            <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-3 rounded-sm bg-amber-400/40" />{c.legendPressure}</span>
          </div>
        </div>
      </div>
    </figure>
  );
}

export function EstimateVisual({ copy }: { copy: LandingCopy }) {
  const e = copy.planning;
  return (
    <figure aria-label={`${e.estimateLabel} · ${e.estimateCaption}`} className={cn('rounded-[20px] border border-[var(--color-border-default)] bg-white p-5 sm:p-6 shadow-[var(--lp-shadow-float)]', styles.lift)}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-[12px] font-semibold text-[var(--color-text-secondary)]">{e.estimateLabel}</p>
          <p className="font-heading text-[18px] font-bold text-[var(--lp-navy)]">{e.estimateRef}</p>
        </div>
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1 rounded-full border border-[var(--color-health-strong-border)] bg-[var(--color-health-strong-bg)] px-2.5 py-1 text-[11px] font-semibold text-[var(--color-health-strong)]"><CheckCircle2 className="w-3 h-3" aria-hidden="true" />{e.estimateStatus}</span>
          <SampleTag>{e.estimateCaption}</SampleTag>
        </div>
      </div>
      <dl className="mt-5 divide-y divide-[var(--color-border-subtle)] text-[14px]">
        {e.lines.map(line => (
          <div key={line.label} className="flex items-center justify-between gap-4 py-2.5">
            <dt className="text-[var(--color-text-secondary)]">{line.label}</dt>
            <dd className="font-medium text-[var(--lp-navy)]">{money(line.amount)}</dd>
          </div>
        ))}
        <div className="flex items-center justify-between gap-4 py-2.5">
          <dt className="font-semibold text-[var(--lp-navy)]">{e.totalCost}</dt>
          <dd className="font-semibold text-[var(--lp-navy)]">{money(e.totalCostAmount)}</dd>
        </div>
      </dl>
      <div className="mt-4 grid grid-cols-2 gap-3">
        <div className="rounded-[var(--radius-xl)] bg-[var(--lp-tint)] p-3">
          <p className="text-[12px] text-[var(--color-text-secondary)]">{e.price}</p>
          <p className="mt-1 font-heading text-[17px] font-bold text-[var(--lp-blue)]">{money(e.priceAmount)}</p>
        </div>
        <div className="rounded-[var(--radius-xl)] bg-[var(--color-health-strong-bg)] p-3">
          <p className="text-[12px] text-[var(--color-text-secondary)]">{e.margin}</p>
          <p dir="ltr" className="mt-1 font-heading text-[17px] font-bold text-[var(--color-health-strong)] tabular-nums text-start">{e.marginValue}</p>
        </div>
      </div>
    </figure>
  );
}

const reasonTone: Record<number, string> = {
  0: 'text-[var(--color-health-strong)] bg-[var(--color-health-strong-bg)] border-[var(--color-health-strong-border)]',
  1: 'text-[var(--color-health-stable)] bg-[var(--color-health-stable-bg)] border-[var(--color-health-stable-border)]',
  2: 'text-[var(--color-health-attention)] bg-[var(--color-health-attention-bg)] border-[var(--color-health-attention-border)]',
};

export function InsightVisual({ copy }: { copy: LandingCopy }) {
  const i = copy.intelligence;
  return (
    <figure aria-label={`${i.scoreLabel} · ${i.assistant} · ${i.previewCaption}`} className="relative grid gap-4">
      <div className={cn('rounded-[20px] border border-[var(--color-border-default)] bg-white p-5 shadow-[var(--lp-shadow-card)]', styles.lift)}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="font-heading text-[16px] font-semibold text-[var(--lp-navy)]">{i.scoreLabel}</p>
          <SampleTag>{i.previewCaption}</SampleTag>
        </div>
        <ul className="mt-4 grid gap-2.5">
          {i.reasons.map((reason, index) => (
            <li key={reason.label} className="flex items-center justify-between gap-3">
              <span className="text-[14px] text-[var(--color-text-secondary)]">{reason.label}</span>
              <span className={cn('rounded-full border px-2.5 py-0.5 text-[12px] font-semibold', reasonTone[index])}>{reason.state}</span>
            </li>
          ))}
        </ul>
      </div>
      <div className={cn('rounded-[20px] border border-[var(--color-border-default)] bg-white p-5 shadow-[var(--lp-shadow-card)]', styles.lift)}>
        <p className="flex items-center gap-2 text-[13px] font-semibold text-[var(--lp-blue)]"><Sparkles aria-hidden="true" className="w-4 h-4" /><span lang="en" dir="ltr">{i.assistant}</span></p>
        <p className="mt-3 ms-auto w-fit max-w-[90%] rounded-[16px] rounded-se-[4px] bg-[var(--lp-blue)] px-4 py-2.5 text-[14px] text-white">{i.question}</p>
        <p className="mt-3 max-w-[95%] rounded-[16px] rounded-ss-[4px] bg-[var(--color-surface-hover)] px-4 py-3 text-[14px] leading-[1.6] text-[var(--color-text-primary)]">{i.answer}</p>
      </div>
    </figure>
  );
}
