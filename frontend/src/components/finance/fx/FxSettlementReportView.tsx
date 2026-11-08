import React, { useState, useEffect, useCallback } from 'react';
import {
  FxSettlementReportItem,
  FxSettlementReportFilter,
  SettlementDirection,
  FxSettlementStatus,
  RecognitionEvidenceStatus,
  getSettlementsReport,
  downloadSettlementsCsvUrl,
} from '@/lib/api/dev-a/fx';
import { FxCopy } from '@/lib/i18n/translations/fx';

interface Props {
  copy: FxCopy;
  currencies: string[];
}

export function FxSettlementReportView({ copy, currencies }: Props) {
  const [items, setItems] = useState<FxSettlementReportItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [filterCurrency, setFilterCurrency] = useState<string>('');
  const [filterDirection, setFilterDirection] = useState<string>('');
  const [filterStatus, setFilterStatus] = useState<string>('');
  const [filterEvidence, setFilterEvidence] = useState<string>('');

  const loadReport = useCallback(async () => {
    setLoading(true);
    try {
      const filter: FxSettlementReportFilter = {
        currency: filterCurrency || undefined,
        direction: (filterDirection as SettlementDirection) || undefined,
        status: (filterStatus as FxSettlementStatus) || undefined,
        evidenceStatus: (filterEvidence as RecognitionEvidenceStatus) || undefined,
        limit: 100,
      };
      const res = await getSettlementsReport(filter);
      setItems(res || []);
    } catch (e) {
      console.error('Failed to load FX settlement report', e);
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [filterCurrency, filterDirection, filterStatus, filterEvidence]);

  useEffect(() => {
    loadReport();
  }, [loadReport]);

  const currentFilter: FxSettlementReportFilter = {
    currency: filterCurrency || undefined,
    direction: (filterDirection as SettlementDirection) || undefined,
    status: (filterStatus as FxSettlementStatus) || undefined,
    evidenceStatus: (filterEvidence as RecognitionEvidenceStatus) || undefined,
  };

  const csvUrl = downloadSettlementsCsvUrl(currentFilter);

  const formatMoney = (val?: number | null, curr = 'PKR') => {
    if (val === undefined || val === null) return '0.00';
    return `${curr} ${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  return (
    <div
      data-testid="fx-settlement-report-view"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-5"
    >
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/60 pb-4">
        <div>
          <h3 className="text-base font-bold text-foreground">{copy.reportTitle}</h3>
          <p className="text-xs text-muted-foreground">{copy.reportSubtitle}</p>
        </div>

        <div className="flex items-center gap-2">
          <a
            href={csvUrl}
            download
            data-testid="export-csv-btn"
            className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground hover:bg-muted/60 transition-colors shadow-xs"
          >
            <svg
              className="h-3.5 w-3.5 text-muted-foreground"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            {copy.exportCsv}
          </a>

          <button
            type="button"
            onClick={() => window.print()}
            data-testid="print-statement-btn"
            className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground hover:bg-muted/60 transition-colors shadow-xs"
          >
            <svg
              className="h-3.5 w-3.5 text-muted-foreground"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
            </svg>
            {copy.printStatement}
          </button>
        </div>
      </div>

      {/* Filter Controls */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div>
          <label className="text-[11px] font-medium text-muted-foreground">{copy.filterCurrency}</label>
          <select
            data-testid="filter-currency-select"
            value={filterCurrency}
            onChange={(e) => setFilterCurrency(e.target.value)}
            className="mt-1 block w-full rounded-lg border border-border bg-background px-2.5 py-1.5 text-xs text-foreground focus:border-primary focus:outline-none"
          >
            <option value="">{copy.filterAllCurrencies}</option>
            {currencies.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="text-[11px] font-medium text-muted-foreground">{copy.filterDirection}</label>
          <select
            data-testid="filter-direction-select"
            value={filterDirection}
            onChange={(e) => setFilterDirection(e.target.value)}
            className="mt-1 block w-full rounded-lg border border-border bg-background px-2.5 py-1.5 text-xs text-foreground focus:border-primary focus:outline-none"
          >
            <option value="">{copy.filterAllDirections}</option>
            <option value="PAYABLE_SETTLEMENT">{copy.directionPayable}</option>
            <option value="RECEIVABLE_COLLECTION">{copy.directionReceivable}</option>
          </select>
        </div>

        <div>
          <label className="text-[11px] font-medium text-muted-foreground">{copy.filterStatus}</label>
          <select
            data-testid="filter-status-select"
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value)}
            className="mt-1 block w-full rounded-lg border border-border bg-background px-2.5 py-1.5 text-xs text-foreground focus:border-primary focus:outline-none"
          >
            <option value="">{copy.filterAllStatuses}</option>
            <option value="SETTLED">{copy.statusSettled}</option>
            <option value="REVERSED">{copy.statusReversed}</option>
          </select>
        </div>

        <div>
          <label className="text-[11px] font-medium text-muted-foreground">{copy.filterEvidence}</label>
          <select
            data-testid="filter-evidence-select"
            value={filterEvidence}
            onChange={(e) => setFilterEvidence(e.target.value)}
            className="mt-1 block w-full rounded-lg border border-border bg-background px-2.5 py-1.5 text-xs text-foreground focus:border-primary focus:outline-none"
          >
            <option value="">{copy.filterAllEvidence}</option>
            <option value="AVAILABLE">{copy.evidenceAvailable}</option>
            <option value="UNAVAILABLE">{copy.evidenceUnavailable}</option>
            <option value="EXCLUDED">{copy.evidenceExcluded}</option>
          </select>
        </div>
      </div>

      {/* Table */}
      {loading ? (
        <div className="py-12 text-center text-xs text-muted-foreground">Loading audit records...</div>
      ) : items.length === 0 ? (
        <div className="py-10 text-center text-sm text-muted-foreground" data-testid="no-report-items-message">
          {copy.noReportItemsFound}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-border/60 text-muted-foreground font-semibold">
                <th className="pb-2">{copy.dateCol} / Ref</th>
                <th className="pb-2">{copy.counterpartyCol}</th>
                <th className="pb-2 text-right">{copy.settledAmountCol}</th>
                <th className="pb-2 text-right">{copy.exchangeRateCol}</th>
                <th className="pb-2 text-right">{copy.accountMovementCol}</th>
                <th className="pb-2 text-right">{copy.realizedFxCol}</th>
                <th className="pb-2 text-center">{copy.evidenceProvenanceCol}</th>
                <th className="pb-2 text-center">{copy.filterStatus}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {items.map((item) => {
                const isGain = item.realizedFxImpact === 'GAIN';
                const isLoss = item.realizedFxImpact === 'LOSS';

                return (
                  <tr
                    key={item.settlementId}
                    data-testid={`report-item-row-${item.settlementId}`}
                    className="hover:bg-muted/20 transition-colors"
                  >
                    <td className="py-3 font-medium text-foreground">
                      <div>{item.settlementDate}</div>
                      <div className="text-[10px] text-muted-foreground font-mono">
                        {item.referenceNumber || item.settlementId.substring(0, 8)}
                      </div>
                    </td>

                    <td className="py-3">
                      <div className="font-medium text-foreground">{item.counterpartyName}</div>
                      <div className="text-[10px] text-muted-foreground">
                        {item.direction === 'PAYABLE_SETTLEMENT' ? copy.directionPayable : copy.directionReceivable}
                      </div>
                    </td>

                    <td className="py-3 text-right font-medium text-foreground">
                      {item.obligationCurrency} {Number(item.obligationAmount).toFixed(2)}
                    </td>

                    <td className="py-3 text-right font-mono text-muted-foreground">
                      {Number(item.exchangeRate).toFixed(4)}
                    </td>

                    <td className="py-3 text-right font-medium text-foreground">
                      <div>{formatMoney(item.totalAccountMovement, item.accountCurrency)}</div>
                      <div className="text-[10px] text-muted-foreground">
                        Fee: {formatMoney(item.feeAmount, item.feeCurrency)}
                      </div>
                    </td>

                    <td className="py-3 text-right">
                      {item.recognitionEvidenceStatus === 'AVAILABLE' && item.realizedFxDifference !== null ? (
                        <span
                          className={`font-bold ${
                            isGain
                              ? 'text-emerald-600 dark:text-emerald-400'
                              : isLoss
                              ? 'text-rose-600 dark:text-rose-400'
                              : 'text-muted-foreground'
                          }`}
                        >
                          {isGain ? '+' : isLoss ? '-' : ''}
                          {formatMoney(item.realizedFxDifference, item.accountCurrency)}
                        </span>
                      ) : (
                        <span className="text-[10px] text-muted-foreground italic">
                          {item.recognitionEvidenceStatus === 'EXCLUDED' ? copy.evidenceExcluded : copy.evidenceUnavailable}
                        </span>
                      )}
                    </td>

                    <td className="py-3 text-center">
                      <span
                        className={`inline-block rounded px-2 py-0.5 text-[10px] font-semibold ${
                          item.recognitionProvenance === 'VERIFIED_RECOGNITION_EVIDENCE'
                            ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border border-emerald-500/20'
                            : item.recognitionProvenance === 'USER_DECLARED_RECOGNITION'
                            ? 'bg-blue-500/10 text-blue-700 dark:text-blue-300 border border-blue-500/20'
                            : item.recognitionProvenance === 'HISTORICAL_REFERENCE'
                            ? 'bg-purple-500/10 text-purple-700 dark:text-purple-300 border border-purple-500/20'
                            : 'bg-muted text-muted-foreground'
                        }`}
                      >
                        {item.recognitionProvenance === 'VERIFIED_RECOGNITION_EVIDENCE'
                          ? copy.provenanceVerified
                          : item.recognitionProvenance === 'USER_DECLARED_RECOGNITION'
                          ? copy.provenanceUserDeclared
                          : item.recognitionProvenance === 'HISTORICAL_REFERENCE'
                          ? copy.provenanceHistorical
                          : copy.provenanceUnavailable}
                      </span>
                    </td>

                    <td className="py-3 text-center">
                      <span
                        className={`inline-block rounded px-2 py-0.5 text-[10px] font-semibold ${
                          item.status === 'SETTLED'
                            ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
                            : 'bg-rose-500/10 text-rose-700 dark:text-rose-300'
                        }`}
                      >
                        {item.status === 'SETTLED' ? copy.statusSettled : copy.statusReversed}
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

