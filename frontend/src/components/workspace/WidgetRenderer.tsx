"use client";
import Link from 'next/link';
import { FileText } from 'lucide-react';
import type { DashboardWorkspaceResponse } from '@/lib/api/workspace';
import { completenessPercent } from '@/lib/api/finance';
import { adviceMatchesScore, scorePresentation } from '@/lib/api/score-presentation';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { interpolate, liveLabel } from '@/lib/i18n/labels';
import { workspaceCopy } from '@/lib/i18n/translations/workspace';
import { formatPKR } from '@/lib/utils/currency';
import { Card } from '@/components/ui/Card';
import { HealthScoreHero } from '@/components/dashboard/HealthScoreHero';
import { HealthComponents } from '@/components/dashboard/HealthComponents';
import { ScoreCompleteness } from '@/components/dashboard/ScoreCompleteness';
import { ScoreEvidence } from '@/components/integration/ScoreEvidence';
import { NextStep } from '@/components/dashboard/NextStep';
import { RecommendationActions } from '@/components/integration/RecommendationActions';
import { CashFlowTrend } from '@/components/dashboard/CashFlowTrend';
import { AiExplanationPanel } from '@/components/ai/AiExplanation';
import { RecordedTrends } from './RecordedTrends';

export function WidgetRenderer({ widgetKey, workspace, preview, onUpdated }: { widgetKey: string; workspace: DashboardWorkspaceResponse; preview: boolean; onUpdated: () => void }) {
  const { locale, t } = useLanguage(), copy = workspaceCopy(locale), { can } = useSession();
  const { dashboard, records } = workspace, { score, trendProjection: projection } = dashboard;
  const presentation = score ? scorePresentation(score) : null;
  const insight = score && dashboard.topInsight && adviceMatchesScore(dashboard.topInsight, score) ? dashboard.topInsight : null;
  const recommendation = score && dashboard.topRecommendation && adviceMatchesScore(dashboard.topRecommendation, score) ? dashboard.topRecommendation : null;
  switch (widgetKey) {
    case 'health-score': return presentation && score ? <HealthScoreHero className="h-full" scoreResult={presentation} explanation={{ summary: insight?.text || (score.explanation ? interpolate(t.live.scoreSummary, { months: score.explanation.historyMonthsAvailable }) : t.live.legacyScore), weakestReason: t.live.weakestReason }}/> : <Card padding="lg"><h2 className="font-semibold mb-2">{copy.names['health-score']}</h2><p>{t.live.noScore}</p></Card>;
    case 'health-components': return score ? <HealthComponents className="h-full" componentScores={score.componentScores} weakestComponent={score.weakestComponent || undefined}/> : <Unavailable title={copy.names['health-components']}/>;
    case 'score-completeness': return score ? <ScoreCompleteness completeness={completenessPercent(score.dataCompleteness)} componentScores={score.componentScores} actionHref={can('RECORD_CREATE_UPDATE') ? '/records/new' : '/health/components'}/> : <Unavailable title={copy.names['score-completeness']}/>;
    case 'financial-evidence': return score ? <ScoreEvidence score={score} compact/> : <Unavailable title={copy.names['financial-evidence']}/>;
    case 'next-step': return recommendation && score ? <NextStep className="h-full" recommendation={{ id: recommendation.id, title: t.live.recommendations, action: recommendation.text, impact: '', priority: recommendation.priority.toLowerCase() === 'high' ? 'high' : 'medium' }} actionHref={`/health/components?month=${score.month}`}/> : <Card padding="md"><h2 className="font-semibold mb-2">{copy.names['next-step']}</h2><p>{t.live.noAdvice}</p></Card>;
    case 'recommendation': return <Card padding="md" className="h-full space-y-3"><h2 className="font-semibold">{copy.names.recommendation}</h2>{recommendation ? preview ? <p className="text-sm">{recommendation.text}</p> : <RecommendationActions key={recommendation.id + recommendation.sourceComputedAt} advice={recommendation} onUpdated={onUpdated}/> : <p>{t.live.noAdvice}</p>}<Link href="/health/components" className="text-sm underline text-[var(--color-brand-primary)]">{copy.recommendations}</Link></Card>;
    case 'cash-flow': return <CashFlowTrend records={records} authoritative={{ summaryByPeriod: workspace.cashSummaryByPeriod, history: workspace.cashHistory }}/ >;
    case 'cash-projection': return <Card padding="md" className="h-full space-y-3"><h2 className="font-semibold">{t.live.projection}</h2>{projection?.projectedNetCashFlow != null ? <><p><bdi>{projection.projectedMonth}</bdi>: <strong>{formatPKR(projection.projectedNetCashFlow, { locale })}</strong></p><p className="text-sm">{liveLabel(t, projection.trendDirection)} · {t.live.confidence}: {liveLabel(t, projection.confidence)} · {t.live.monthsUsed}: {projection.historicalMonthsCount}</p><p className="text-xs text-[var(--color-text-muted)]">{t.live.projectionHelp}</p></> : <p className="text-sm">{t.live.projectionPending}</p>}</Card>;
    case 'financial-summary': return <RecordedTrends records={records}/>;
    case 'ai-explanation': return workspace.aiStatus.available ? preview ? <Card padding="md"><h2 className="font-semibold mb-2">{copy.names['ai-explanation']}</h2><p className="text-sm">{copy.explanationPreview}</p></Card> : <AiExplanationPanel key={`${score?.computedAt}:${records.map(record => record.updatedAt).join(',')}`} type="dashboard" month={score?.month}/> : null;
    case 'document-workflow': return can('DOCUMENT_READ') ? <Card padding="md" className="h-full space-y-3"><FileText className="h-6 w-6 text-[var(--color-brand-primary)]" aria-hidden="true"/><h2 className="font-semibold">{copy.names['document-workflow']}</h2><p className="text-sm text-[var(--color-text-muted)]">{copy.documentHelp}</p><Link href="/upload" className="text-sm underline text-[var(--color-brand-primary)]">{copy.viewDocuments}</Link></Card> : null;
    default: return null;
  }
}
function Unavailable({ title }: { title: string }) { const { t } = useLanguage(); return <Card padding="md"><h2 className="font-semibold mb-2">{title}</h2><p className="text-sm">{t.live.noScore}</p></Card>; }
