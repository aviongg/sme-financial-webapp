"use client";
import { Card } from '@/components/ui/Card';
import { useLanguage } from '@/lib/i18n/context';
import { fxCopy } from '@/lib/i18n/translations/fx';
import type { FxPreviewResponse } from '@/lib/api/dev-a/fx';

interface FxPreviewCardProps {
  preview: FxPreviewResponse;
}

export function FxPreviewCard({ preview }: FxPreviewCardProps) {
  const { locale } = useLanguage();
  const copy = fxCopy(locale);
  const isOutgoing = preview.direction === 'OUTGOING';

  const formatCurrency = (amount: number, curr: string) => {
    return new Intl.NumberFormat(locale === 'ur' ? 'ur-PK' : 'en-US', {
      style: 'currency',
      currency: curr,
      maximumFractionDigits: curr === 'JPY' ? 0 : curr === 'KWD' || curr === 'BHD' || curr === 'OMR' ? 3 : 2,
      minimumFractionDigits: curr === 'JPY' ? 0 : curr === 'KWD' || curr === 'BHD' || curr === 'OMR' ? 3 : 2,
    }).format(amount);
  };

  return (
    <div className="space-y-4" data-testid="fx-preview-results">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {/* Main Result Card */}
        <Card className={`p-5 border-2 ${isOutgoing ? 'border-amber-500/40 bg-amber-500/5' : 'border-emerald-500/40 bg-emerald-500/5'}`}>
          <div className="text-xs font-semibold uppercase tracking-wider text-[var(--color-text-secondary)]">
            {isOutgoing ? copy.totalDebit : copy.netReceipt}
          </div>
          <div className="mt-2 text-2xl font-bold tracking-tight text-[var(--color-text-primary)]" data-testid="fx-primary-settlement-amount">
            {formatCurrency(isOutgoing ? preview.totalSettlementDebited : preview.netSettlementAmount, preview.settlementCurrency)}
          </div>
          <p className="mt-1 text-xs text-[var(--color-text-secondary)]">
            {isOutgoing
              ? `${copy.grossSettlement} + ${copy.totalFees}`
              : `${copy.grossSettlement} - ${copy.totalFees}`}
          </p>
        </Card>

        {/* Converted Gross Principal */}
        <Card className="p-5">
          <div className="text-xs font-semibold uppercase tracking-wider text-[var(--color-text-secondary)]">
            {copy.grossSettlement}
          </div>
          <div className="mt-2 text-2xl font-bold tracking-tight text-[var(--color-text-primary)]" data-testid="fx-gross-settlement-amount">
            {formatCurrency(preview.grossSettlementAmount, preview.settlementCurrency)}
          </div>
          <p className="mt-1 text-xs text-[var(--color-text-secondary)]">
            {preview.principalAmount} {preview.principalCurrency} @ {preview.rate}
          </p>
        </Card>

        {/* Effective Rate */}
        <Card className="p-5">
          <div className="text-xs font-semibold uppercase tracking-wider text-[var(--color-text-secondary)]">
            {copy.effectiveRate}
          </div>
          <div className="mt-2 text-2xl font-bold tracking-tight text-[var(--color-text-primary)]" data-testid="fx-effective-rate">
            {preview.effectiveRate.toFixed(6)}
          </div>
          <p className="mt-1 text-xs text-[var(--color-text-secondary)]">
            {preview.settlementCurrency} per 1 {preview.principalCurrency} (incl. fees)
          </p>
        </Card>
      </div>

      {/* Fee Breakdown Card */}
      <Card className="p-5">
        <h4 className="text-sm font-semibold text-[var(--color-text-primary)]">{copy.feesAndDeductions}</h4>
        <div className="mt-3 grid grid-cols-1 sm:grid-cols-3 gap-4 text-sm">
          <div>
            <span className="text-[var(--color-text-secondary)]">{copy.fixedFee}:</span>{' '}
            <span className="font-semibold" data-testid="fx-fixed-fee">
              {formatCurrency(preview.fixedFeeAmount, preview.settlementCurrency)}
            </span>
          </div>
          <div>
            <span className="text-[var(--color-text-secondary)]">{copy.percentageFee}:</span>{' '}
            <span className="font-semibold" data-testid="fx-percentage-fee">
              {formatCurrency(preview.percentageFeeAmount, preview.settlementCurrency)}
            </span>
          </div>
          <div>
            <span className="text-[var(--color-text-secondary)]">{copy.totalFees}:</span>{' '}
            <span className="font-semibold text-amber-600 dark:text-amber-400" data-testid="fx-total-fees">
              {formatCurrency(preview.totalFeeAmount, preview.settlementCurrency)}
            </span>
          </div>
        </div>
      </Card>
    </div>
  );
}

