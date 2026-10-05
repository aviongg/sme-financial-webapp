import type { Score } from './finance';
import type { ScoreResult, HealthBand } from '../../types/financial';
/** Presentation only: never calculate or substitute a financial score/band. */
export function scorePresentation(score: Score): ScoreResult | null {
  const band = scoreBand(score.band);
  if (score.compositeScore == null || !band) return null;
  return { composite_score: score.compositeScore, component_scores: score.componentScores,
    weakest_component: score.weakestComponent || '', data_completeness: Math.round(score.dataCompleteness * 100),
    is_provisional: score.explanation ? score.explanation.historyMonthsAvailable < 3 : undefined, score_band: band as HealthBand };
}
export function scoreBand(value: string): HealthBand | null {
  return ({ strong: 'strong', stable: 'stable', 'needs attention': 'attention', 'at risk': 'risk', attention: 'attention', risk: 'risk' } as Record<string, HealthBand>)[value?.toLowerCase()] || null;
}
export function adviceMatchesScore(advice: {businessId: string; month: string; sourceComputedAt: string}, score: Score) {
  return advice.businessId === score.businessId && advice.month === score.month && advice.sourceComputedAt === score.computedAt;
}
