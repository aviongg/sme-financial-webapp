import React from 'react';
import { CurrencyExposureSummary } from '@/lib/api/dev-a/fx';
import { FxCopy } from '@/lib/i18n/translations/fx';

interface Props {
  exposures: CurrencyExposureSummary[];
  copy: FxCopy;
}

export function FxCurrencyExposureTable({ exposures, copy }: Props) {
  const formatNativeMoney = (val: number, currency: string, minorUnits = 2) => {
    return `${currency} ${Number(val).toLocaleString(undefined, {
      minimumFractionDigits: minorUnits,
      maximumFractionDigits: minorUnits,
    })}`;
  };

  return (
    <div
      data-testid="fx-currency-exposure-table"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="border-b border-border/60 pb-3">
        <h3 className="text-base font-bold text-foreground">{copy.exposureTitle}</h3>
        <p className="text-xs text-muted-foreground">{copy.exposureSubtitle}</p>
      </div>

      {exposures.length === 0 ? (
        <div className="py-8 text-center text-sm text-muted-foreground" data-testid="no-exposures-message">
          {copy.noExposuresFound}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border/60 text-xs font-semibold text-muted-foreground">
                <th className="pb-2">{copy.currencyCol}</th>
                <th className="pb-2 text-right">{copy.outstandingReceivables}</th>
                <th className="pb-2 text-right">{copy.outstandingPayables}</th>
                <th className="pb-2 text-right">{copy.netNativeExposure}</th>
                <th className="pb-2 text-right">{copy.settledVolume}</th>
                <th className="pb-2 text-center">{copy.obligationsCount}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {exposures.map((item) => {
                const isNetPositive = item.netExposure > 0;
                const isNetNegative = item.netExposure < 0;

                return (
                  <tr
                    key={item.currency}
                    data-testid={`exposure-row-${item.currency}`}
                    className="hover:bg-muted/30 transition-colors"
                  >
                    <td className="py-3 font-medium text-foreground">
                      <div className="flex items-center gap-2">
                        <span className="rounded bg-muted px-2 py-0.5 text-xs font-bold text-foreground">
                          {item.currency}
                        </span>
                        <span className="text-xs text-muted-foreground">{item.displayName}</span>
                      </div>
                    </td>

                    <td
                      data-testid={`exposure-receivables-${item.currency}`}
                      className="py-3 text-right font-medium text-emerald-600 dark:text-emerald-400"
                    >
                      {formatNativeMoney(item.outstandingReceivables, item.currency, item.minorUnits)}
                    </td>

                    <td
                      data-testid={`exposure-payables-${item.currency}`}
                      className="py-3 text-right font-medium text-rose-600 dark:text-rose-400"
                    >
                      {formatNativeMoney(item.outstandingPayables, item.currency, item.minorUnits)}
                    </td>

                    <td
                      data-testid={`exposure-net-${item.currency}`}
                      className={`py-3 text-right font-bold ${isNetPositive
                        ? 'text-emerald-600 dark:text-emerald-400'
                        : isNetNegative
                          ? 'text-rose-600 dark:text-rose-400'
                          : 'text-muted-foreground'
                        }`}
                    >
                      {isNetPositive ? '+' : ''}
                      {formatNativeMoney(item.netExposure, item.currency, item.minorUnits)}
                    </td>

                    <td className="py-3 text-right text-xs text-muted-foreground">
                      <div>Rec: {formatNativeMoney(item.settledReceivablesVolume, item.currency, item.minorUnits)}</div>
                      <div>Pay: {formatNativeMoney(item.settledPayablesVolume, item.currency, item.minorUnits)}</div>
                    </td>

                    <td className="py-3 text-center text-xs text-muted-foreground">
                      <span className="rounded-full bg-muted px-2 py-0.5 font-semibold">
                        {item.totalObligationsCount}
                      </span>
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

