"use client";
import { useCallback, useState } from 'react';
import Link from 'next/link';
import { financeApi } from '@/lib/api/finance';
import { adviceMatchesScore, scorePresentation, scoreBand } from '@/lib/api/score-presentation';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { ScoreEvidence } from './ScoreEvidence';
import { RecommendationActions } from './RecommendationActions';
import { Card } from '@/components/ui/Card';
import { HealthScoreHero } from '@/components/dashboard/HealthScoreHero';
import { HealthComponents } from '@/components/dashboard/HealthComponents';
import { useLanguage } from '@/lib/i18n/context';
import { interpolate, liveLabel } from '@/lib/i18n/labels';
export function LiveHealth() {
  const { t } = useLanguage();
  const [month, setMonth] = useState(() => typeof window === 'undefined' ? '' : new URLSearchParams(window.location.search).get('month') || '');
  const resource = useLiveResource(useCallback((signal: AbortSignal) => financeApi.scoreHistory(signal), []));
  const scores = resource.data || [], selected = /^\d{4}-(0[1-9]|1[0-2])$/.test(month) ? month : scores[0]?.month || '';
  return <LivePage title={t.nav.healthPillars} {...resource} actions={<Link className="underline" href="/">{t.componentBreakdown.returnToDashboard}</Link>}>
    {scores.length ? <><Card padding="lg" className="space-y-3"><h2 className="font-semibold">{t.live.scoreHistory}</h2><p className="text-sm">{t.live.historyHelp}</p><div className="overflow-x-auto"><table className="w-full text-start text-sm"><thead><tr><th className="text-start p-2">{t.live.month}</th><th className="text-start p-2">{t.live.score}</th><th className="text-start p-2">{t.live.status}</th><th className="text-start p-2">{t.live.movement}</th></tr></thead><tbody>{scores.map(s => <tr key={s.month} className={s.month === selected ? 'bg-[var(--color-brand-surface)]' : ''}><td className="p-2"><button className="underline font-semibold" onClick={() => setMonth(s.month)} aria-pressed={selected === s.month}>{s.month}</button></td><td className="p-2 tabular-nums">{s.compositeScore ?? '—'}</td><td className="p-2">{(t.healthStates as Record<string,string>)[scoreBand(s.band) || ''] || t.live.unavailable}</td><td className="p-2 tabular-nums">{s.explanation?.overallDelta != null ? (s.explanation.overallDelta > 0 ? '+' : '') + s.explanation.overallDelta : '—'}</td></tr>)}</tbody></table></div></Card><HealthMonth key={selected} month={selected}/></> : <Card padding="lg">{t.live.noScore}</Card>}
  </LivePage>;
}
function HealthMonth({ month }: { month: string }) {
  const { t } = useLanguage();
  const resource = useLiveResource(useCallback(async (signal: AbortSignal) => { const [score, insights, recommendations] = await Promise.all([financeApi.score(month, signal), financeApi.insights(month, signal), financeApi.recommendations(month, signal)]); return { score, insights, recommendations }; }, [month]));
  if (resource.loading) return <p role="status">{t.common.loading}</p>;
  if (resource.error) return <p role="alert">{resource.error} <button className="underline" onClick={resource.retry}>{t.common.tryAgain}</button></p>;
  const score = resource.data?.score;
  if (!score) return <Card padding="lg">{t.live.noScore}</Card>;
  const insights = resource.data!.insights.filter(a => adviceMatchesScore(a, score)), recommendations = resource.data!.recommendations.filter(a => adviceMatchesScore(a, score));
  const changed = insights.length !== resource.data!.insights.length || recommendations.length !== resource.data!.recommendations.length;
  const presentation = scorePresentation(score), explanation = score.explanation;
  return <div className="space-y-5">{changed && <p role="status">{t.live.sourceChanged} <button className="underline" onClick={resource.retry}>{t.live.refresh}</button></p>}
    {presentation && <HealthScoreHero scoreResult={presentation} explanation={{ summary: explanation ? interpolate(t.live.scoreSummary, { months: explanation.historyMonthsAvailable }) : t.live.legacyScore, weakestReason: t.live.weakestReason }}/>}
    <Card padding="lg" className="space-y-2"><h2 className="font-semibold">{t.live.movement} · {month}</h2>{explanation?.previousMonth ? <><p><bdi dir="ltr">{explanation.previousMonth} → {month}: {explanation.previousScore ?? '—'} → {score.compositeScore ?? '—'}</bdi></p>{explanation.majorChanges.map(change => <p key={change.component}>{liveLabel(t, change.component)}: <bdi dir="ltr">{change.previousScore ?? '—'} → {change.currentScore ?? '—'}</bdi></p>)}</> : <p>{t.live.noPrevious}</p>}</Card>
    <HealthComponents componentScores={score.componentScores} weakestComponent={score.weakestComponent || undefined}/><ScoreEvidence score={score}/>
    <Card padding="lg" className="space-y-3"><h2 className="font-semibold">{t.live.insights}</h2>{insights.length ? insights.map(item => <p key={item.id} dir={item.language === 'ur' ? 'rtl' : 'ltr'}>{item.text}</p>) : <p>{t.live.noAdvice}</p>}</Card>
    <Card padding="lg" className="space-y-4"><h2 className="font-semibold">{t.live.recommendations}</h2>{recommendations.length ? recommendations.map(item => <article key={item.id + item.sourceComputedAt} className="border-t pt-3 space-y-3"><p dir={item.language === 'ur' ? 'rtl' : 'ltr'}>{item.text}</p><RecommendationActions advice={item} onUpdated={resource.retry}/></article>) : <p>{t.live.noAdvice}</p>}</Card>
  </div>;
}
