import type { EstimateLine, PricingMode, PricingPreviewRequest } from '../api/estimates';

export const MAX_ESTIMATE_LINES = 100;
export interface DraftLine extends EstimateLine { id: string }
export interface EstimateDraft {
  lines: DraftLine[];
  saleableQuantity: string;
  pricingMode: PricingMode;
  pricingValues: Record<PricingMode, string>;
}
export type EstimateIssue = 'required' | 'decimal' | 'percentage' | 'marginRange' | 'lines' | 'description' | 'unit' | 'invalid';
export type EstimateErrors = Record<string, EstimateIssue>;

export function emptyLine(id: string): DraftLine {
  return { id, costGroup: 'MATERIAL', description: '', quantity: '', unit: '', unitCost: '' };
}
export function initialEstimate(): EstimateDraft {
  return { lines: [emptyLine('0')], saleableQuantity: '', pricingMode: 'PROPOSED_PRICE',
    pricingValues: { PROPOSED_PRICE: '', TARGET_MARGIN: '', TARGET_MARKUP: '' } };
}
export function changeLine(draft: EstimateDraft, id: string, patch: Partial<EstimateLine>): EstimateDraft {
  return { ...draft, lines: draft.lines.map(line => line.id === id ? { ...line, ...patch } : line) };
}
export function addLine(draft: EstimateDraft, id: string): EstimateDraft {
  return draft.lines.length >= MAX_ESTIMATE_LINES ? draft : { ...draft, lines: [...draft.lines, emptyLine(id)] };
}
export function removeLine(draft: EstimateDraft, id: string): EstimateDraft {
  return { ...draft, lines: draft.lines.filter(line => line.id !== id) };
}
export function changeMode(draft: EstimateDraft, pricingMode: PricingMode): EstimateDraft {
  return { ...draft, pricingMode };
}
const decimalPattern = /^\d{1,12}(?:\.\d{1,12})?$/;
const percentagePattern = /^\d{1,14}(?:\.\d{1,10})?$/;

/** Decimal point movement only. Never computes a cost, price, contribution or ratio in the browser. */
export function percentageToRatio(value: string): string {
  if (!percentagePattern.test(value)) throw new Error('Invalid percentage');
  const [whole, fraction = ''] = value.split('.');
  const padded = whole.padStart(3, '0');
  return `${padded.slice(0, -2).replace(/^0+(?=\d)/, '')}.${padded.slice(-2)}${fraction}`;
}

export function validateEstimate(draft: EstimateDraft): EstimateErrors {
  const errors: EstimateErrors = {};
  const decimal = (key: string, value: string) => {
    if (!value) errors[key] = 'required';
    else if (!decimalPattern.test(value)) errors[key] = 'decimal';
  };
  if (!draft.lines.length || draft.lines.length > MAX_ESTIMATE_LINES) errors.lines = 'lines';
  draft.lines.forEach((line, index) => {
    const prefix = `lines[${index}]`;
    if (!line.description.trim() || line.description.length > 200) errors[`${prefix}.description`] = 'description';
    if (!line.unit.trim() || line.unit.length > 40) errors[`${prefix}.unit`] = 'unit';
    if (!['MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER'].includes(line.costGroup)) errors[`${prefix}.costGroup`] = 'invalid';
    decimal(`${prefix}.quantity`, line.quantity);
    decimal(`${prefix}.unitCost`, line.unitCost);
  });
  if (draft.saleableQuantity !== '') decimal('saleableQuantity', draft.saleableQuantity);
  const value = draft.pricingValues[draft.pricingMode];
  if (draft.pricingMode === 'PROPOSED_PRICE') decimal('pricingValue', value);
  else if (!value) errors.pricingValue = 'required';
  else if (!percentagePattern.test(value)) errors.pricingValue = 'percentage';
  else if (draft.pricingMode === 'TARGET_MARGIN' && value.split('.')[0].replace(/^0+/, '').length > 2)
    errors.pricingValue = 'marginRange';
  return errors;
}

export function previewRequest(draft: EstimateDraft): PricingPreviewRequest {
  if (Object.keys(validateEstimate(draft)).length) throw new Error('Invalid estimate');
  return { lines: draft.lines.map(({ costGroup, description, quantity, unit, unitCost }) =>
    ({ costGroup, description: description.trim(), quantity, unit: unit.trim(), unitCost })),
    saleableQuantity: draft.saleableQuantity === '' ? null : draft.saleableQuantity,
    pricingMode: draft.pricingMode,
    pricingValue: draft.pricingMode === 'PROPOSED_PRICE' ? draft.pricingValues.PROPOSED_PRICE : percentageToRatio(draft.pricingValues[draft.pricingMode]) };
}

/** Group exact server digits; do not apply an unapproved currency/settlement rounding rule. */
export function formatEstimateMoney(value: string | null, locale: 'en' | 'ur'): string {
  if (value === null) return '—';
  if (!/^-?\d+(?:\.\d+)?$/.test(value)) return '—';
  const negative = value.startsWith('-');
  const [whole, fraction] = value.replace(/^-/, '').split('.');
  const digits = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction ? `.${fraction}` : '');
  return locale === 'ur' ? `${negative ? '- ' : ''}${digits} روپے` : `${negative ? '- ' : ''}PKR ${digits}`;
}

/** Render an authoritative ratio as percent with string movement, keeping all returned precision. */
export function formatEstimatePercent(value: string | null): string {
  if (value === null || !/^-?\d+(?:\.\d+)?$/.test(value)) return '—';
  const negative = value.startsWith('-');
  const [whole, fraction = ''] = value.replace(/^-/, '').split('.');
  const padded = fraction.padEnd(2, '0');
  const integer = `${whole}${padded.slice(0, 2)}`.replace(/^0+(?=\d)/, '');
  const remainder = padded.slice(2).replace(/0+$/, '');
  return `${negative ? '-' : ''}${integer}${remainder ? `.${remainder}` : ''}%`;
}
