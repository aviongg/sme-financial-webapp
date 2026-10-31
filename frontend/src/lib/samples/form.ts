export const sampleKinds = ['SAMPLE', 'PRODUCTION_COST_ACTIVITY'] as const;
export const sampleStatuses = ['DRAFT', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'ARCHIVED'] as const;
export const recoveryIntents = ['FREE', 'PAID', 'PARTIAL'] as const;
export const costCategories = ['MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER'] as const;
export type SampleKind = typeof sampleKinds[number];
export type SampleStatus = typeof sampleStatuses[number];
export type RecoveryIntent = typeof recoveryIntents[number];
export type CostCategory = typeof costCategories[number];
export type PlanStatus = 'DRAFT' | 'APPROVED' | 'SUPERSEDED';
export interface MetadataDraft {
  code: string; name: string; kind: SampleKind; description: string; recoveryIntent: RecoveryIntent;
  projectId: string; startDate: string; targetEndDate: string;
}
export interface LineInput { category: CostCategory; description: string; quantity: string; unit: string; unitCost: string }
export interface LineDraft extends LineInput { id: string }
export interface PlanDraft { plannedRecovery: string; lines: LineDraft[] }
export interface CreatePayload {
  code: string; name: string; kind: SampleKind; description: string | null; recoveryIntent: RecoveryIntent;
  projectId: string | null; startDate: string | null; targetEndDate: string | null;
}
export interface PlanPayload { plannedRecovery: string | null; lines: LineInput[] }
export type Issue = 'required' | 'code' | 'text' | 'date' | 'dateOrder' | 'decimal' | 'lines' | 'invalid';
export type FieldErrors = Record<string, Issue>;
export const MAX_PLAN_LINES = 100;
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const quantity = /^\d{1,9}(?:\.\d{1,6})?$/;
const unitCost = /^\d{1,12}(?:\.\d{1,6})?$/;
const recovery = /^\d{1,12}(?:\.\d{1,4})?$/;
const controls = /[\u0000-\u001f\u007f]/;

export function initialMetadata(): MetadataDraft {
  return { code: '', name: '', kind: 'SAMPLE', description: '', recoveryIntent: 'FREE', projectId: '', startDate: '', targetEndDate: '' };
}
export function metadataFromActivity(value: CreatePayload): MetadataDraft {
  return { code: value.code, name: value.name, kind: value.kind, description: value.description ?? '', recoveryIntent: value.recoveryIntent,
    projectId: value.projectId ?? '', startDate: value.startDate ?? '', targetEndDate: value.targetEndDate ?? '' };
}
export function emptyLine(id: string): LineDraft { return { id, category: 'MATERIAL', description: '', quantity: '', unit: '', unitCost: '' }; }
export function initialPlan(): PlanDraft { return { plannedRecovery: '', lines: [emptyLine('0')] }; }
function normalizeDecimal(value: string): string { return value.replace(/(\.\d*?[1-9])0+$/, '$1').replace(/\.0+$/, ''); }
export function draftFromPlan(value: { plannedRecovery: string | null; lines: LineInput[] }): PlanDraft {
  return { plannedRecovery: value.plannedRecovery === null ? '' : normalizeDecimal(value.plannedRecovery),
    lines: value.lines.map((line, index) => ({ id: String(index), category: line.category, description: line.description,
      quantity: normalizeDecimal(line.quantity), unit: line.unit, unitCost: normalizeDecimal(line.unitCost) })) };
}
export function createPayload(value: MetadataDraft): CreatePayload {
  return { code: value.code.trim().toUpperCase(), name: value.name.trim(), kind: value.kind,
    description: value.description.trim() || null, recoveryIntent: value.recoveryIntent, projectId: value.projectId || null,
    startDate: value.startDate || null, targetEndDate: value.targetEndDate || null };
}
// Explicit field selection: never serialize tenant, actor, status or browser totals.
export function planPayload(value: PlanDraft, intent: RecoveryIntent): PlanPayload {
  return { plannedRecovery: intent === 'FREE' ? '0' : value.plannedRecovery === '' ? null : value.plannedRecovery,
    lines: value.lines.map(line => ({ category: line.category, description: line.description.trim(), quantity: line.quantity,
      unit: line.unit.trim(), unitCost: line.unitCost })) };
}
function validDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
export function validateMetadata(value: MetadataDraft): FieldErrors {
  const errors: FieldErrors = {};
  if (!value.code.trim()) errors.code = 'required';
  else if (!/^[A-Za-z0-9][A-Za-z0-9._/-]{0,39}$/.test(value.code.trim())) errors.code = 'code';
  if (!value.name.trim()) errors.name = 'required';
  else if (value.name.trim().length > 160 || controls.test(value.name)) errors.name = 'text';
  if (value.description.trim().length > 1000 || controls.test(value.description)) errors.description = 'text';
  if (!sampleKinds.includes(value.kind)) errors.kind = 'invalid';
  if (!recoveryIntents.includes(value.recoveryIntent)) errors.recoveryIntent = 'invalid';
  if (value.projectId && !uuid.test(value.projectId)) errors.projectId = 'invalid';
  if (value.startDate && !validDate(value.startDate)) errors.startDate = 'date';
  if (value.targetEndDate && !validDate(value.targetEndDate)) errors.targetEndDate = 'date';
  if (value.startDate && value.targetEndDate && value.targetEndDate < value.startDate) errors.targetEndDate = 'dateOrder';
  return errors;
}
export function validatePlan(value: PlanDraft, intent: RecoveryIntent): FieldErrors {
  const errors: FieldErrors = {};
  if (intent !== 'FREE' && value.plannedRecovery !== '' && !recovery.test(value.plannedRecovery)) errors.plannedRecovery = 'decimal';
  if (value.lines.length < 1 || value.lines.length > MAX_PLAN_LINES) errors.lines = 'lines';
  value.lines.forEach((line, index) => {
    const prefix = `lines[${index}].`;
    if (!costCategories.includes(line.category)) errors[prefix + 'category'] = 'invalid';
    if (!line.description.trim() || line.description.trim().length > 200 || controls.test(line.description)) errors[prefix + 'description'] = 'text';
    if (!line.unit.trim() || line.unit.trim().length > 40 || controls.test(line.unit)) errors[prefix + 'unit'] = 'text';
    if (!quantity.test(line.quantity)) errors[prefix + 'quantity'] = 'decimal';
    if (!unitCost.test(line.unitCost)) errors[prefix + 'unitCost'] = 'decimal';
  });
  return errors;
}
export function mapFieldErrors(errors: Record<string, string>): FieldErrors {
  return Object.fromEntries(Object.entries(errors).map(([field, reason]) => [field,
    reason === 'required' ? 'required' : reason.includes('decimal') ? 'decimal' : reason === 'date_order_invalid' ? 'dateOrder' :
    reason.includes('date') ? 'date' : reason.includes('code') ? 'code' : reason.includes('line_count') ? 'lines' :
    reason.includes('text') ? 'text' : 'invalid'])) as FieldErrors;
}
export function allowedTransitions(status: SampleStatus): SampleStatus[] {
  switch (status) {
    case 'DRAFT': return ['ACTIVE', 'CANCELLED', 'ARCHIVED'];
    case 'ACTIVE': return ['COMPLETED', 'CANCELLED', 'ARCHIVED'];
    case 'COMPLETED': case 'CANCELLED': return ['ARCHIVED'];
    case 'ARCHIVED': return [];
  }
}
export function canEdit(status: SampleStatus, canWrite: boolean): boolean { return canWrite && (status === 'DRAFT' || status === 'ACTIVE'); }
export function transitionBlocked(status: SampleStatus, hasDraft: boolean): boolean { return hasDraft && (status === 'COMPLETED' || status === 'CANCELLED' || status === 'ARCHIVED'); }
/** Groups server digits without converting financial values to floating point or rounding them. */
export function formatMoney(value: string | null, locale: 'en' | 'ur'): string {
  if (value === null || !/^-?\d+(?:\.\d+)?$/.test(value)) return '—';
  const [whole, fraction] = value.replace(/^-/, '').split('.');
  const digits = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction ? `.${fraction}` : '');
  const sign = value.startsWith('-') ? '- ' : '';
  return locale === 'ur' ? `${sign}${digits} روپے` : `${sign}PKR ${digits}`;
}