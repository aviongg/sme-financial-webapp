import { apiClient } from '../client';
import { normalizeAmountInput } from '../../utils/amount-input';
import type { FundingEditorInput } from '@/components/finance/funding/FundingWorkspace';
import type { FundingComparisonView, FundingScenarioView } from '@/components/finance/funding/FundingResults';
import type { FundingTimelineRow } from '@/components/finance/funding/FundingTimeline';

export class InvalidFundingResponse extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InvalidFundingResponse';
  }
}

export type WireOpeningLiquiditySource = 'LEDGER_CASH' | 'MANUAL_ASSUMPTION';
export type WireCashFlowDirection = 'INFLOW' | 'OUTFLOW';

export interface WirePlannedCashEventInput {
  eventId: string;
  date: string;
  direction: WireCashFlowDirection;
  amount: string;
  description: string;
  category?: string;
}

export interface WireScenarioDefinition {
  scenarioName: string;
  description?: string;
  events: WirePlannedCashEventInput[];
}

export interface WireOpeningLiquidityRequest {
  source: WireOpeningLiquiditySource;
  manualAmount?: string | null;
  description?: string | null;
}

export interface WireFundingScenarioCompareRequest {
  currency?: string;
  asOfDate?: string;
  horizonDays?: number;
  openingLiquidity: WireOpeningLiquidityRequest;
  baselineScenario: WireScenarioDefinition;
  alternativeScenario?: WireScenarioDefinition | null;
}

export interface WireDailyFundingPosition {
  date: string;
  dayIndex?: number;
  openingPosition?: string;
  openingLiquidity?: string;
  inflows?: string;
  plannedInflows?: string;
  outflows?: string;
  plannedOutflows?: string;
  netMovement?: string;
  netPlannedMovement?: string;
  closingPosition?: string;
  closingLiquidity?: string;
  projectedPosition?: string;
  cumulativeProjectedPosition?: string;
  fundingDeficit?: string;
  deficit?: string;
  isDeficit?: boolean;
  isShortfall?: boolean;
  events?: Array<{
    eventId: string;
    date: string;
    direction: string;
    amount: string;
    description: string;
    category?: string;
  }>;
  eventsOnDay?: Array<{
    eventId: string;
    direction: string;
    amount: string;
    description: string;
  }>;
}

export interface WireScenarioResult {
  scenarioName: string;
  totalPlannedInflows: string;
  totalPlannedOutflows: string;
  netPlannedMovement: string;
  closingProjectedLiquidity: string;
  peakFundingRequirement: string;
  peakDeficitDate: string | null;
  firstDeficitDate: string | null;
  recoveryDate: string | null;
  deficitDayCount: number;
  hasDeficit: boolean;
  isRecovered: boolean;
  timeline: WireDailyFundingPosition[];
}

export interface WireDailyScenarioDifference {
  date: string;
  dayIndex?: number;
  baselineClosing?: string;
  baselineClosingPosition?: string;
  alternativeClosing?: string;
  alternativeClosingPosition?: string;
  liquidityDifference?: string;
  positionDifference?: string;
  baselineDeficit?: string;
  alternativeDeficit?: string;
  deficitDifference?: string;
}

export interface WireScenarioComparisonSummary {
  baselineScenarioName: string;
  alternativeScenarioName: string;
  peakFundingDifference: string;
  closingLiquidityDifference: string;
  totalInflowsDifference: string;
  totalOutflowsDifference: string;
  firstDeficitDateChangeDays: number | null;
  recoveryDateChangeDays: number | null;
  dailyDifferences: WireDailyScenarioDifference[];
}

export interface WireFundingScenarioCompareResponse {
  currency: string;
  asOfDate: string;
  horizonStart: string;
  horizonEnd: string;
  horizonDays: number;
  openingLiquidity: {
    source: 'POSTED_ACTUAL' | 'MANUAL_ASSUMPTION';
    amount: string;
    note: string;
  };
  baseline: WireScenarioResult;
  alternative: WireScenarioResult | null;
  comparison: WireScenarioComparisonSummary | null;
  provenance: {
    openingLiquiditySource: 'POSTED_ACTUAL' | 'MANUAL_ASSUMPTION';
    planningMode: string;
    calculatedAt: string;
    calculationEngine: string;
  };
  warnings: string[];
  limitations: string[];
}

/**
 * Authoritative API client for the C06 Funding Gap and Scenario Engine.
 * Converts frontend form models to the published C06 wire contract and
 * decodes the authoritative backend calculations into UI view models.
 */
export async function compareFundingScenarios(
  input: FundingEditorInput,
  signal?: AbortSignal
): Promise<FundingComparisonView> {
  const { assumptions, baseline, alternative } = input;

  // Build baseline events
  const baselineEvents: WirePlannedCashEventInput[] = baseline.map(e => {
    const norm = normalizeAmountInput(e.amountText, 'PKR');
    return {
      eventId: e.id,
      date: e.date,
      direction: e.direction === 'IN' ? 'INFLOW' : 'OUTFLOW',
      amount: norm ? norm.canonical : e.amountText,
      description: e.description.trim(),
      category: e.direction === 'IN' ? 'REVENUE' : 'OPERATING',
    };
  });

  // Build alternative events (if any provided)
  const hasAlternativeEvents = alternative.length > 0;
  const alternativeEvents: WirePlannedCashEventInput[] = alternative.map(e => {
    const norm = normalizeAmountInput(e.amountText, 'PKR');
    return {
      eventId: e.id,
      date: e.date,
      direction: e.direction === 'IN' ? 'INFLOW' : 'OUTFLOW',
      amount: norm ? norm.canonical : e.amountText,
      description: e.description.trim(),
      category: e.direction === 'IN' ? 'REVENUE' : 'OPERATING',
    };
  });

  const openingAmount = assumptions.source === 'MANUAL'
    ? (normalizeAmountInput(assumptions.openingText, 'PKR')?.canonical ?? assumptions.openingText)
    : null;

  const wireRequest: WireFundingScenarioCompareRequest = {
    currency: 'PKR',
    asOfDate: assumptions.startDate,
    horizonDays: assumptions.horizonDays,
    openingLiquidity: {
      source: assumptions.source === 'LEDGER' ? 'LEDGER_CASH' : 'MANUAL_ASSUMPTION',
      manualAmount: openingAmount,
      description: assumptions.source === 'MANUAL' ? 'User hypothetical opening liquidity assumption' : null,
    },
    baselineScenario: {
      scenarioName: 'Baseline Plan',
      description: 'Baseline Cash Trajectory',
      events: baselineEvents,
    },
    // If alternative has events or baseline has events, send alternative plan to compare
    alternativeScenario: hasAlternativeEvents || baselineEvents.length > 0 ? {
      scenarioName: 'Alternative Plan',
      description: 'Alternative What-If Plan',
      events: alternativeEvents,
    } : null,
  };

  const response = await apiClient<WireFundingScenarioCompareResponse>(
    '/finance/funding/scenarios/compare',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(wireRequest),
      signal,
    }
  );

  if (!response || typeof response !== 'object' || !response.baseline || !response.openingLiquidity) {
    throw new InvalidFundingResponse('Server returned an incomplete or malformed funding scenario response.');
  }

  const mapScenarioView = (scenario: WireScenarioResult): FundingScenarioView => ({
    peak: scenario.peakFundingRequirement ?? '0.00',
    firstDeficit: scenario.firstDeficitDate ?? null,
    recovery: scenario.recoveryDate ?? null,
    closing: scenario.closingProjectedLiquidity ?? '0.00',
    inflows: scenario.totalPlannedInflows ?? '0.00',
    outflows: scenario.totalPlannedOutflows ?? '0.00',
  });

  const baselineView = mapScenarioView(response.baseline);
  const alternativeView = response.alternative ? mapScenarioView(response.alternative) : { ...baselineView };

  // Map timeline rows from dailyDifferences if comparison exists, or from baseline timeline
  const rows: FundingTimelineRow[] = response.comparison?.dailyDifferences && response.comparison.dailyDifferences.length > 0
    ? response.comparison.dailyDifferences.map(d => ({
        date: String(d.date),
        baseline: d.baselineClosingPosition ?? d.baselineClosing ?? '0.00',
        alternative: d.alternativeClosingPosition ?? d.alternativeClosing ?? '0.00',
      }))
    : (response.baseline.timeline ?? []).map(t => ({
        date: String(t.date),
        baseline: t.closingPosition ?? t.closingLiquidity ?? '0.00',
        alternative: response.alternative
          ? (response.alternative.timeline?.find(at => String(at.date) === String(t.date))?.closingPosition
              ?? response.alternative.timeline?.find(at => String(at.date) === String(t.date))?.closingLiquidity
              ?? t.closingPosition ?? t.closingLiquidity ?? '0.00')
          : (t.closingPosition ?? t.closingLiquidity ?? '0.00'),
      }));

  return {
    baseline: baselineView,
    alternative: alternativeView,
    rows,
    opening: response.openingLiquidity.amount ?? '0.00',
    source: response.openingLiquidity.source === 'POSTED_ACTUAL' ? 'LEDGER' : 'MANUAL',
    asOf: response.asOfDate,
    evaluatedAt: response.provenance?.calculatedAt ?? new Date().toISOString(),
    fundingDifference: response.comparison?.peakFundingDifference ?? '0.00',
    closingDifference: response.comparison?.closingLiquidityDifference ?? '0.00',
  };
}

export const fundingApi = {
  compare: compareFundingScenarios,
};
