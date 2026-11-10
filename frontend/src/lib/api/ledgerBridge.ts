import { apiClient } from './client';
import type { MonthlyRecordResponse } from '@/types/financial';

export interface LedgerCompletenessReport {
  hasDirectSaleReceipts: boolean;
  hasReceivableCollections: boolean;
  hasOperatingDisbursements: boolean;
  hasPayableSettlements: boolean;
  hasBankTransfers: boolean;
  hasReversals: boolean;
  hasConfirmedOcrReceipts: boolean;
  hasManualAdjustments: boolean;
  hasManualTaxAccrual: boolean;
  hasManualFinancingPayment: boolean;
  readinessStatus: 'READY_FOR_PREVIEW' | 'GAPS_DETECTED' | 'NO_TRANSACTIONS';
  dataGaps: string[];
  warnings: string[];
}

export interface LedgerMonthlyProjectionResponse {
  businessId: string;
  yearMonth: string;
  ledgerCashInflow: number;
  ledgerCashOutflow: number;
  ledgerNetCashFlow: number;
  ledgerOperatingRevenue: number;
  ledgerOperatingExpenses: number;
  asOfReceivablesOutstanding: number;
  asOfPayablesOutstanding: number;
  currentCashBalanceEom: number;
  hasExistingMonthlyRecord: boolean;
  existingMonthlyRecord: MonthlyRecordResponse | null;
  completeness: LedgerCompletenessReport;
  calculationVersion: string;
  explanation: string;
}

export interface LedgerCutoverRequest {
  yearMonth: string;
  manualCogs?: number | null;
  manualInventoryValue?: number | null;
  manualLoanOutstanding?: number | null;
  manualInterestExpense?: number | null;
  financingType?: string | null;
  expectedVersion?: number | null;
  confirmationNote?: string | null;
}

export interface LedgerCutoverResponse {
  id: string;
  businessId: string;
  yearMonth: string;
  version: number;
  monthlyRecord: MonthlyRecordResponse;
  bridgeProvenance: string;
  cutoverAt: string;
  message: string;
}

export const ledgerBridgeApi = {
  getMonthlyProjection: (yearMonth: string): Promise<LedgerMonthlyProjectionResponse> => {
    return apiClient<LedgerMonthlyProjectionResponse>(
      `/finance/ledger-bridge/monthly-projection?yearMonth=${encodeURIComponent(yearMonth)}`
    );
  },

  applyLedgerProjection: (request: LedgerCutoverRequest): Promise<LedgerCutoverResponse> => {
    return apiClient<LedgerCutoverResponse>(
      '/finance/ledger-bridge/apply-projection',
      {
        method: 'POST',
        body: JSON.stringify(request),
      }
    );
  },
};
