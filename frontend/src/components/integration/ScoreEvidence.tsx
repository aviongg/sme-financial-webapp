"use client";
import type { Score } from '@/lib/api/finance';
import { useLanguage } from '@/lib/i18n/context';
import { liveLabel } from '@/lib/i18n/labels';
import { Card } from '@/components/ui/Card';
export function ScoreEvidence({ score, compact = false }: { score: Score; compact?: boolean }) {
  const { t } = useLanguage(); const explanation = score.explanation;
  return <Card padding="lg" className="space-y-4">
    <h2 className="font-semibold text-lg">{t.live.evidence}</h2>
    {!explanation ? <p>{t.live.legacyScore}</p> : <>
      <p>{t.live.historyAvailable}: <span className="tabular-nums">{explanation.historyMonthsAvailable}</span></p>
      <p className="text-sm">{t.live.selfDeclaredHelp}</p>
      {!compact && <div className="grid gap-4 lg:grid-cols-2">{Object.entries(explanation.components).map(([key, component]) => <section className="rounded-xl border border-[var(--color-border-default)] p-4 space-y-3" key={key}>
        <h3 className="font-semibold">{liveLabel(t, key)} · <span className="tabular-nums">{component.score ?? '—'}</span></h3>
        <p>{component.status === 'AVAILABLE' ? t.live.available : t.live.unavailable} · {component.evidenceType === 'SELF_DECLARED' ? t.live.selfDeclared : t.live.calculated}</p>
        <p className="text-sm">{liveLabel(t, component.basis)}</p>
        <p className="text-xs">{t.live.baseWeight}: {Number((component.baseWeight * 100).toFixed(2))}% · {t.live.effectiveWeight}: {Number((component.effectiveWeight * 100).toFixed(2))}% · {t.live.monthsUsed}: {component.historyMonthsUsed}</p>
        <dl className="space-y-2 text-sm">{component.drivers.map(driver => <div key={driver.key} className="flex justify-between gap-4 flex-wrap"><dt className="text-[var(--color-text-muted)]">{liveLabel(t, driver.key)}</dt><dd className="tabular-nums break-words max-w-full">{driver.value == null ? t.live.unavailable : ['category', 'boolean'].includes(driver.unit) ? liveLabel(t, driver.value) : <bdi dir="ltr">{driver.value}{driver.unit === 'PKR' ? ' PKR' : ['months','days','points','ratio'].includes(driver.unit) ? ' ' + liveLabel(t, driver.unit) : ''}</bdi>}</dd></div>)}</dl>
        {explanation.previousMonth && <p className="text-sm">{t.live.movement}: <bdi dir="ltr">{component.previousScore ?? '—'} → {component.score ?? '—'}{component.delta != null ? ` (${component.delta > 0 ? '+' : ''}${component.delta})` : ''}</bdi></p>}
      </section>)}</div>}
      <p className="text-sm text-[var(--color-text-muted)]">{t.live.normalization}</p>
    </>}
    <p className="text-xs text-[var(--color-text-muted)]">{t.live.methodology}: <bdi dir="ltr">{score.methodologyVersion || '—'}</bdi> · {t.live.computedAt}: <bdi dir="ltr">{score.computedAt}</bdi></p>
  </Card>;
}
