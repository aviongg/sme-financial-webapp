import { apiClient } from './client';

export const costGroups = ['MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER'] as const;
export const pricingModes = ['PROPOSED_PRICE', 'TARGET_MARGIN', 'TARGET_MARKUP'] as const;
export type CostGroup = typeof costGroups[number];
export type PricingMode = typeof pricingModes[number];
export interface EstimateLine {
  costGroup: CostGroup;
  description: string;
  quantity: string;
  unit: string;
  unitCost: string;
}
export interface PricingPreviewRequest {
  lines: EstimateLine[];
  saleableQuantity: string | null;
  pricingMode: PricingMode;
  pricingValue: string;
}
export interface PricingPreviewResponse {
  businessId: string;
  currency: 'PKR';
  currencyBasis: 'APPLICATION_FIXED_PKR';
  planningOnly: true;
  lines: (EstimateLine & { lineNumber: number; plannedCost: string })[];
  groupSubtotals: Record<CostGroup, string>;
  totalPlannedCost: string;
  saleableQuantity: string | null;
  pricingMode: PricingMode;
  pricingValue: string;
  plannedBatchPrice: string;
  plannedContribution: string;
  plannedMargin: string | null;
  plannedMarkup: string | null;
  plannedPerUnitPrice: string | null;
  unavailableReasons: {
    plannedMargin: 'ZERO_PRICE' | null;
    plannedMarkup: 'ZERO_COST' | null;
    plannedPerUnitPrice: 'SALEABLE_QUANTITY_UNKNOWN' | 'ZERO_SALEABLE_QUANTITY' | null;
  };
  calculation: {
    version: 'f06-preview-v1';
    divisionScale: number;
    divisionRounding: 'HALF_EVEN';
    accountingRoundingPolicy: null;
  };
}

/** Only manually entered planning assumptions cross the boundary. No tenant, totals or posting data. */
export function estimatePayload(input: PricingPreviewRequest): PricingPreviewRequest {
  return {
    lines: input.lines.map(line => ({ costGroup: line.costGroup, description: line.description,
      quantity: line.quantity, unit: line.unit, unitCost: line.unitCost })),
    saleableQuantity: input.saleableQuantity,
    pricingMode: input.pricingMode,
    pricingValue: input.pricingValue,
  };
}
export const estimatesApi = {
  preview: (input: PricingPreviewRequest, signal?: AbortSignal) => apiClient<PricingPreviewResponse>(
    '/estimates/preview', { method: 'POST', body: JSON.stringify(estimatePayload(input)), signal }),
};
