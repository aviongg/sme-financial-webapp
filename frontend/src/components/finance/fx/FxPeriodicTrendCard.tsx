import React from 'react';
import { FxPeriodTrendSummary } from '@/lib/api/dev-a/fx';
import { FxCopy } from '@/lib/i18n/translations/fx';

interface Props {
  trends: FxPeriodTrendSummary[];
  reportingCurrency?: string;
  copy: FxCopy;
}

export function FxPeriodicTrendCard({ trends, reportingCurrency = 'PKR', copy }: Props) {
  const formatMoney = (val?: number | null) => {
    if (val === undefined || val === null) return '0.00';
    return `${reportingCurrency} ${Number(val).toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  return (
    <div
      data-testid="fx-periodic-trends-card"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="border-b border-border/60 pb-3">
        <h3 className="text-base font-bold text-foreground">{copy.trendTitle}</h3>
        <p className="text-xs text-muted-foreground">{copy.trendSubtitle}</p>
      </div>

      {trends.length === 0 ? (
        <div className="py-8 text-center text-sm text-muted-foreground" data-testid="no-trends-message">
          {copy.noTrendsFound}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border/60 text-xs font-semibold text-muted-foreground">
                <th className="pb-2">{copy.periodCol}</th>
                <th className="pb-2 text-center">{copy.settlementsCount}</th>
                <th className="pb-2 text-right">{copy.convertedVolume}</th>
                <th className="pb-2 text-right">{copy.realizedGainLoss}</th>
                <th className="pb-2 text-right">{copy.bankFees}</th>
                <th className="pb-2 text-right">{copy.cashImpact}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {trends.map((item) => {
                const isNetGain = item.netRealizedFx > 0;
                const isNetLoss = item.netRealizedFx < 0;

                return (
                  <tr
                    key={item.period}
                    data-testid={`trend-row-${item.period}`}
                    className="hover:bg-muted/30 transition-colors"
                  >
                    <td className="py-3 font-semibold text-foreground">
                      <span className="rounded bg-muted px-2 py-1 text-xs font-mono">{item.period}</span>
                    </td>

                    <td className="py-3 text-center text-xs text-muted-foreground">
                      <span className="rounded-full bg-muted px-2 py-0.5 font-semibold">
                        {item.settlementCount}
                      </span>
                    </td>

                    <td className="py-3 text-right font-medium text-foreground">
                      {formatMoney(item.totalConvertedPrincipal)}
                    </td>

                    <td className="py-3 text-right text-xs">
                      <div className="flex flex-col items-end gap-0.5">
                        <span
                          data-testid={`trend-net-fx-${item.period}`}
                          className={`font-bold ${
                            isNetGain
                              ? 'text-emerald-600 dark:text-emerald-400'
                              : isNetLoss
                              ? 'text-rose-600 dark:text-rose-400'
                              : 'text-muted-foreground'
                          }`}
                        >
                          {isNetGain ? '+' : ''}
                          {formatMoney(item.netRealizedFx)}
                        </span>
                        <span className="text-[10px] text-muted-foreground">
                          Gain: {formatMoney(item.totalRealizedGain)} | Loss: {formatMoney(item.totalRealizedLoss)}
                        </span>
                      </div>
                    </td>

                    <td className="py-3 text-right font-medium text-amber-600 dark:text-amber-400">
                      {formatMoney(item.totalBankFees)}
                    </td>

                    <td className="py-3 text-right font-bold text-foreground">
                      {formatMoney(item.netCashImpact)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

