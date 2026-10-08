"use client";
import { useCallback } from 'react';
import Link from 'next/link';
import { financeApi, completenessPercent } from '@/lib/api/finance';
import { phaseOneApi } from '@/lib/api/phase-one';
import { scorePresentation, adviceMatchesScore } from '@/lib/api/score-presentation';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { ScoreEvidence } from './ScoreEvidence';
import { RecommendationActions } from './RecommendationActions';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { CashFlowTrend } from '@/components/dashboard/CashFlowTrend';
import { HealthScoreHero } from '@/components/dashboard/HealthScoreHero';
import { HealthComponents } from '@/components/dashboard/HealthComponents';
import { ScoreCompleteness } from '@/components/dashboard/ScoreCompleteness';
import { NextStep } from '@/components/dashboard/NextStep';
import { formatPKR } from '@/lib/utils/currency';
import { useLanguage } from '@/lib/i18n/context';
import { interpolate, liveLabel } from '@/lib/i18n/labels';
import { useSession } from '@/components/auth/SessionProvider';
export function LiveDashboard() {
  const { locale, t } = useLanguage(); const { can, business } = useSession();
  const resource = useLiveResource(useCallback(async (signal: AbortSignal) => { const [dashboard, records] = await Promise.all([financeApi.dashboard(signal), phaseOneApi.getMonthlyRecords(signal)]); return { dashboard, records }; }, []));
  const data = resource.data, score = data?.dashboard.score, projection = data?.dashboard.trendProjection;
  const presentation = score ? scorePresentation(score) : null;
  const insight = score && data?.dashboard.topInsight && adviceMatchesScore(data.dashboard.topInsight, score) ? data.dashboard.topInsight : null;
  const recommendation = score && data?.dashboard.topRecommendation && adviceMatchesScore(data.dashboard.topRecommendation, score) ? data.dashboard.topRecommendation : null;
  return <LivePage title={t.nav.dashboard} {...resource} actions={can('RECORD_CREATE_UPDATE') ? <Link href="/records/new"><Button size="sm">{t.dashboard.addMonthlyRecord}</Button></Link> : undefined}>
    {data && <><div className="flex flex-wrap gap-3 justify-between items-center"><h1 className="font-heading text-2xl font-bold">{business?.businessName}</h1><p className="text-sm">{t.live.savedRecords}: <span className="tabular-nums">{data.records.length}</span> · <bdi>{score?.month || '—'}</bdi></p></div>
    {presentation && score ? <><div className="grid gap-5 lg:grid-cols-3"><HealthScoreHero className="lg:col-span-2" scoreResult={presentation} explanation={{ summary: insight?.text || (score.explanation ? interpolate(t.live.scoreSummary, { months: score.explanation.historyMonthsAvailable }) : t.live.legacyScore), weakestReason: t.live.weakestReason }}/><div className="space-y-5"><ScoreCompleteness completeness={completenessPercent(score.dataCompleteness)} componentScores={score.componentScores} actionHref={can('RECORD_CREATE_UPDATE') ? '/records/new' : '/health/components'}/>{recommendation ? <><NextStep recommendation={{ id: recommendation.id, title: t.live.recommendations, action: recommendation.text, impact: '', priority: recommendation.priority.toLowerCase() === 'high' ? 'high' : 'medium' }} actionHref={`/health/components?month=${score.month}`}/><RecommendationActions key={recommendation.id + recommendation.sourceComputedAt} advice={recommendation} onUpdated={resource.retry}/></> : <Card padding="md">{t.live.noAdvice}</Card>}</div></div><HealthComponents componentScores={score.componentScores} weakestComponent={score.weakestComponent || undefined}/><ScoreEvidence score={score} compact/></> : <Card padding="lg">{t.live.noScore}</Card>}
    <CashFlowTrend records={data.records}/>
    <Card padding="lg" className="space-y-3"><h2 className="font-semibold">{t.live.projection}</h2>{projection?.projectedNetCashFlow != null ? <><p><bdi>{projection.projectedMonth}</bdi>: <strong>{formatPKR(projection.projectedNetCashFlow, { locale })}</strong></p><p>{liveLabel(t, projection.trendDirection)} · {t.live.confidence}: {liveLabel(t, projection.confidence)} · {t.live.monthsUsed}: {projection.historicalMonthsCount}</p><p className="text-sm">{t.live.projectionHelp}</p></> : <p>{t.live.projectionPending}</p>}</Card></>}
  </LivePage>;
}
