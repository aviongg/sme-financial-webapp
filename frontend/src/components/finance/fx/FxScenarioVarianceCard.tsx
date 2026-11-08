"use client";
import { Card } from '@/components/ui/Card';
import { useLanguage } from '@/lib/i18n/context';
import { fxCopy } from '@/lib/i18n/translations/fx';
import type { RateScenarioVariance } from '@/lib/api/dev-a/fx';

interface FxScenarioVarianceCardProps {
  variance: RateScenarioVariance;
  settlementCurrency: string;
}

export function FxScenarioVarianceCard({ variance, settlementCurrency }: FxScenarioVarianceCardProps) {
  const { locale } = useLanguage();
  const copy = fxCopy(locale);

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat(locale === 'ur' ? 'ur-PK' : 'en-US', {
      style: 'currency',
      currency: settlementCurrency,
      maximumFractionDigits: settlementCurrency === 'JPY' ? 0 : settlementCurrency === 'KWD' || settlementCurrency === 'BHD' || settlementCurrency === 'OMR' ? 3 : 2,
      minimumFractionDigits: settlementCurrency === 'JPY' ? 0 : settlementCurrency === 'KWD' || settlementCurrency === 'BHD' || settlementCurrency === 'OMR' ? 3 : 2,
    }).format(amount);
  };

  const isFavorable = variance.impact === 'FAVORABLE';
  const isUnfavorable = variance.impact === 'UNFAVORABLE';

  return (
    <Card className="p-5 border-s-4 border-s-blue-500" data-testid="fx-scenario-variance-card">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold text-[var(--color-text-primary)]">
          {copy.varianceAnalysis}
        </h4>
        <span
          className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
            isFavorable
              ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/50 dark:text-emerald-300'
              : isUnfavorable
              ? 'bg-rose-100 text-rose-800 dark:bg-rose-900/50 dark:text-rose-300'
              : 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-300'
          }`}
          data-testid="fx-variance-impact-badge"
        >
          {isFavorable ? copy.favorable : isUnfavorable ? copy.unfavorable : copy.neutral}
        </span>
      </div>

      <div className="mt-4 grid grid-cols-1 sm:grid-cols-4 gap-4 text-sm">
        <div>
          <span className="text-[var(--color-text-secondary)]">{copy.comparisonRate}:</span>
          <div className="mt-1 font-semibold text-[var(--color-text-primary)]" data-testid="fx-comp-rate">
            {variance.comparisonRate}
          </div>
        </div>
        <div>
          <span className="text-[var(--color-text-secondary)]">{copy.grossSettlement}:</span>
          <div className="mt-1 font-semibold text-[var(--color-text-primary)]" data-testid="fx-comp-gross">
            {formatCurrency(variance.comparisonGrossAmount)}
          </div>
        </div>
        <div>
          <span className="text-[var(--color-text-secondary)]">{copy.varianceAmount}:</span>
          <div
            className={`mt-1 font-semibold ${
              isFavorable ? 'text-emerald-600 dark:text-emerald-400' : isUnfavorable ? 'text-rose-600 dark:text-rose-400' : ''
            }`}
            data-testid="fx-variance-amount"
          >
            {variance.varianceAmount > 0 ? `+${formatCurrency(variance.varianceAmount)}` : formatCurrency(variance.varianceAmount)}
          </div>
        </div>
        <div>
          <span className="text-[var(--color-text-secondary)]">{copy.variancePercentage}:</span>
          <div className="mt-1 font-semibold text-[var(--color-text-primary)]" data-testid="fx-variance-percent">
            {variance.variancePercentage > 0 ? `+${variance.variancePercentage.toFixed(4)}%` : `${variance.variancePercentage.toFixed(4)}%`}
          </div>
        </div>
      </div>
    </Card>
  );
}

