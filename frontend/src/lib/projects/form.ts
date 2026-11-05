export const projectKinds = ['PROJECT', 'JOB', 'ORDER'] as const;
export const projectStatuses = ['DRAFT', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'ARCHIVED'] as const;
export const projectBudgetStatuses = ['DRAFT', 'APPROVED', 'SUPERSEDED'] as const;
export const projectCostCategories = ['MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER'] as const;

export type ProjectKind = typeof projectKinds[number];
export type ProjectStatus = typeof projectStatuses[number];
export type ProjectBudgetStatus = typeof projectBudgetStatuses[number];
export type ProjectCostCategory = typeof projectCostCategories[number];

export const MAX_PROJECT_BUDGET_LINES = 100;

export interface ProjectMetadataDraft {
  code: string;
  name: string;
  kind: ProjectKind;
  description: string;
  startDate: string;
  targetEndDate: string;
}

export interface ProjectBudgetLineInput {
  category: ProjectCostCategory;
  description: string;
  quantity: string;
  unit: string;
  unitCost: string;
}

export interface ProjectBudgetLineDraft extends ProjectBudgetLineInput {
  id: string;
}

export interface ProjectBudgetDraft {
  plannedRevenue: string;
  lines: ProjectBudgetLineDraft[];
}

export interface ProjectCreatePayload {
  code: string;
  name: string;
  kind: ProjectKind;
  description: string | null;
  startDate: string | null;
  targetEndDate: string | null;
}

export interface ProjectUpdatePayload extends ProjectCreatePayload {
  expectedVersion: number;
}

export interface ProjectTransitionPayload {
  targetStatus: ProjectStatus;
  reason: string | null;
  expectedVersion: number;
}

export interface ProjectBudgetCreatePayload {
  expectedProjectVersion: number;
  plannedRevenue: string;
  lines: ProjectBudgetLineInput[];
}

export interface ProjectBudgetUpdatePayload {
  expectedBudgetVersion: number;
  plannedRevenue: string;
  lines: ProjectBudgetLineInput[];
}

export interface ProjectBudgetApprovePayload {
  expectedProjectVersion: number;
  expectedBudgetVersion: number;
}

export interface ProjectBudgetPreviewCalculation {
  previewOnly: true;
  valid: boolean;
  plannedRevenue: string | null;
  plannedCost: string | null;
  plannedMargin: string | null;
  plannedMarginPct: string | null;
  zeroPlannedRevenue: boolean;
  lineCosts: (string | null)[];
  categorySubtotals: Record<ProjectCostCategory, string>;
}

export type ProjectValidationIssue =
  | 'required'
  | 'code'
  | 'name'
  | 'description'
  | 'date'
  | 'dateOrder'
  | 'decimal'
  | 'lines'
  | 'lineDescription'
  | 'unit'
  | 'invalid';

export type ProjectValidationErrors = Record<string, ProjectValidationIssue>;

const codePattern = /^[A-Za-z0-9][A-Za-z0-9._/-]{0,39}$/;
const isoDatePattern = /^\d{4}-\d{2}-\d{2}$/;
const revenuePattern = /^\d{1,12}(?:\.\d{1,4})?$/;
const quantityPattern = /^\d{1,9}(?:\.\d{1,6})?$/;
const unitCostPattern = /^\d{1,12}(?:\.\d{1,6})?$/;

const SCALE_12 = BigInt('1000000000000');
const SCALE_6 = BigInt(1000000);
const ZERO = BigInt(0);
const ONE = BigInt(1);
const TWO = BigInt(2);

export function initialProjectMetadata(kind: ProjectKind = 'PROJECT'): ProjectMetadataDraft {
  return {
    code: '',
    name: '',
    kind,
    description: '',
    startDate: '',
    targetEndDate: '',
  };
}

export function metadataFromProject(project: {
  code: string;
  name: string;
  kind: ProjectKind;
  description: string | null;
  startDate: string | null;
  targetEndDate: string | null;
}): ProjectMetadataDraft {
  return {
    code: project.code,
    name: project.name,
    kind: project.kind,
    description: project.description ?? '',
    startDate: project.startDate ?? '',
    targetEndDate: project.targetEndDate ?? '',
  };
}

export function emptyBudgetLine(id: string): ProjectBudgetLineDraft {
  return {
    id,
    category: 'MATERIAL',
    description: '',
    quantity: '',
    unit: '',
    unitCost: '',
  };
}

export function initialBudgetDraft(): ProjectBudgetDraft {
  return {
    plannedRevenue: '',
    lines: [emptyBudgetLine('0')],
  };
}

export function draftFromBudgetVersion(version: {
  plannedRevenue: string;
  lines: ReadonlyArray<{
    category: ProjectCostCategory;
    description: string;
    quantity: string;
    unit: string;
    unitCost: string;
  }>;
}): ProjectBudgetDraft {
  return {
    plannedRevenue: normalizeDecimalInput(version.plannedRevenue),
    lines:
      version.lines.length > 0
        ? version.lines.map((line, index) => ({
            id: String(index),
            category: line.category,
            description: line.description,
            quantity: normalizeDecimalInput(line.quantity),
            unit: line.unit,
            unitCost: normalizeDecimalInput(line.unitCost),
          }))
        : [emptyBudgetLine('0')],
  };
}

function normalizeDecimalInput(value: string): string {
  if (!value.includes('.')) {
    return value;
  }
  return value.replace(/(\.\d*?[1-9])0+$/, '$1').replace(/\.0+$/, '');
}

export function changeBudgetLine(
  draft: ProjectBudgetDraft,
  id: string,
  patch: Partial<ProjectBudgetLineInput>
): ProjectBudgetDraft {
  return {
    ...draft,
    lines: draft.lines.map(line => (line.id === id ? { ...line, ...patch } : line)),
  };
}

export function addBudgetLine(draft: ProjectBudgetDraft, id: string): ProjectBudgetDraft {
  if (draft.lines.length >= MAX_PROJECT_BUDGET_LINES) {
    return draft;
  }
  return {
    ...draft,
    lines: [...draft.lines, emptyBudgetLine(id)],
  };
}

export function removeBudgetLine(draft: ProjectBudgetDraft, id: string): ProjectBudgetDraft {
  return {
    ...draft,
    lines: draft.lines.filter(line => line.id !== id),
  };
}

export function validateProjectMetadata(draft: ProjectMetadataDraft): ProjectValidationErrors {
  const errors: ProjectValidationErrors = {};
  const trimmedCode = draft.code.trim();
  const trimmedName = draft.name.trim();
  const trimmedDescription = draft.description.trim();

  if (!trimmedCode) {
    errors.code = 'required';
  } else if (!codePattern.test(trimmedCode)) {
    errors.code = 'code';
  }

  if (!trimmedName) {
    errors.name = 'required';
  } else if (trimmedName.length > 160) {
    errors.name = 'name';
  }

  if (!projectKinds.includes(draft.kind)) {
    errors.kind = 'invalid';
  }

  if (trimmedDescription.length > 1000) {
    errors.description = 'description';
  }

  const startValid = draft.startDate === '' || isoDatePattern.test(draft.startDate);
  const endValid = draft.targetEndDate === '' || isoDatePattern.test(draft.targetEndDate);

  if (!startValid) {
    errors.startDate = 'date';
  }
  if (!endValid) {
    errors.targetEndDate = 'date';
  }
  if (
    startValid &&
    endValid &&
    draft.startDate !== '' &&
    draft.targetEndDate !== '' &&
    draft.targetEndDate < draft.startDate
  ) {
    errors.targetEndDate = 'dateOrder';
  }

  return errors;
}

export function validateProjectBudget(draft: ProjectBudgetDraft): ProjectValidationErrors {
  const errors: ProjectValidationErrors = {};
  const checkPattern = (key: string, value: string, pattern: RegExp) => {
    if (!value) {
      errors[key] = 'required';
    } else if (!pattern.test(value)) {
      errors[key] = 'decimal';
    }
  };

  checkPattern('plannedRevenue', draft.plannedRevenue, revenuePattern);

  if (!draft.lines.length || draft.lines.length > MAX_PROJECT_BUDGET_LINES) {
    errors.lines = 'lines';
  }

  draft.lines.forEach((line, index) => {
    const prefix = `lines[${index}]`;
    if (!projectCostCategories.includes(line.category)) {
      errors[`${prefix}.category`] = 'invalid';
    }
    if (!line.description.trim() || line.description.length > 200) {
      errors[`${prefix}.description`] = 'lineDescription';
    }
    if (!line.unit.trim() || line.unit.length > 40) {
      errors[`${prefix}.unit`] = 'unit';
    }
    checkPattern(`${prefix}.quantity`, line.quantity, quantityPattern);
    checkPattern(`${prefix}.unitCost`, line.unitCost, unitCostPattern);
  });

  return errors;
}

export function mapProjectFieldErrors(fieldErrors?: Record<string, string>): ProjectValidationErrors {
  const mapped: ProjectValidationErrors = {};
  if (!fieldErrors) {
    return mapped;
  }
  for (const [field, code] of Object.entries(fieldErrors)) {
    if (code === 'required') mapped[field] = 'required';
    else if (code === 'code_invalid') mapped[field] = 'code';
    else if (code === 'date_invalid') mapped[field] = 'date';
    else if (code === 'date_order_invalid') mapped[field] = 'dateOrder';
    else if (code === 'decimal_invalid') mapped[field] = 'decimal';
    else if (code === 'line_count_invalid') mapped[field] = 'lines';
    else if (code === 'text_invalid' && field === 'name') mapped[field] = 'name';
    else if (code === 'text_invalid' && field === 'description') mapped[field] = 'description';
    else if (code === 'text_invalid' && field.endsWith('.description')) mapped[field] = 'lineDescription';
    else if (code === 'text_invalid' && field.endsWith('.unit')) mapped[field] = 'unit';
    else mapped[field] = 'invalid';
  }
  return mapped;
}

/**
 * Only user-entered metadata crosses the boundary.
 * Tenant, actor, status, and currency are never accepted from the browser.
 */
export function projectCreatePayload(draft: ProjectMetadataDraft): ProjectCreatePayload {
  if (Object.keys(validateProjectMetadata(draft)).length > 0) {
    throw new Error('Invalid project metadata');
  }
  const description = draft.description.trim();
  return {
    code: draft.code.trim().toUpperCase(),
    name: draft.name.trim(),
    kind: draft.kind,
    description: description === '' ? null : description,
    startDate: draft.startDate === '' ? null : draft.startDate,
    targetEndDate: draft.targetEndDate === '' ? null : draft.targetEndDate,
  };
}

export function projectUpdatePayload(
  draft: ProjectMetadataDraft,
  expectedVersion: number
): ProjectUpdatePayload {
  return {
    ...projectCreatePayload(draft),
    expectedVersion,
  };
}

export function projectTransitionPayload(
  targetStatus: ProjectStatus,
  reason: string,
  expectedVersion: number
): ProjectTransitionPayload {
  const trimmedReason = reason.trim();
  return {
    targetStatus,
    reason: trimmedReason === '' ? null : trimmedReason,
    expectedVersion,
  };
}

/**
 * Only plannedRevenue and raw line assumptions cross the boundary.
 * Authoritative line totals, plannedCost, plannedMargin, and plannedMarginPct are computed on the backend.
 */
export function projectBudgetCreatePayload(
  draft: ProjectBudgetDraft,
  expectedProjectVersion: number
): ProjectBudgetCreatePayload {
  if (Object.keys(validateProjectBudget(draft)).length > 0) {
    throw new Error('Invalid project budget');
  }
  return {
    expectedProjectVersion,
    plannedRevenue: draft.plannedRevenue,
    lines: draft.lines.map(line => ({
      category: line.category,
      description: line.description.trim(),
      quantity: line.quantity,
      unit: line.unit.trim(),
      unitCost: line.unitCost,
    })),
  };
}

export function projectBudgetUpdatePayload(
  draft: ProjectBudgetDraft,
  expectedBudgetVersion: number
): ProjectBudgetUpdatePayload {
  if (Object.keys(validateProjectBudget(draft)).length > 0) {
    throw new Error('Invalid project budget');
  }
  return {
    expectedBudgetVersion,
    plannedRevenue: draft.plannedRevenue,
    lines: draft.lines.map(line => ({
      category: line.category,
      description: line.description.trim(),
      quantity: line.quantity,
      unit: line.unit.trim(),
      unitCost: line.unitCost,
    })),
  };
}

export function projectBudgetApprovePayload(
  expectedProjectVersion: number,
  expectedBudgetVersion: number
): ProjectBudgetApprovePayload {
  return {
    expectedProjectVersion,
    expectedBudgetVersion,
  };
}

function parseScaled12(value: string, pattern: RegExp): bigint | null {
  if (!pattern.test(value)) {
    return null;
  }
  const [whole, fraction = ''] = value.split('.');
  return BigInt(whole) * SCALE_12 + BigInt(fraction.padEnd(12, '0').slice(0, 12));
}

function parseScaled6(value: string, pattern: RegExp): bigint | null {
  if (!pattern.test(value)) {
    return null;
  }
  const [whole, fraction = ''] = value.split('.');
  return BigInt(whole) * SCALE_6 + BigInt(fraction.padEnd(6, '0').slice(0, 6));
}

function formatScaled12Amount(scaled: bigint): string {
  const negative = scaled < ZERO;
  const abs = negative ? -scaled : scaled;
  const whole = abs / SCALE_12;
  const rem = (abs % SCALE_12).toString().padStart(12, '0');
  const trimmedRem = rem.replace(/0+$/, '');
  const fraction = trimmedRem.length < 2 ? rem.slice(0, 2) : trimmedRem;
  return `${negative ? '-' : ''}${whole.toString()}.${fraction}`;
}

function divideHalfEven12(numerator: bigint, denominator: bigint): string {
  const negative = (numerator < ZERO) !== (denominator < ZERO);
  const num = numerator < ZERO ? -numerator : numerator;
  const den = denominator < ZERO ? -denominator : denominator;
  const scaledNumerator = num * SCALE_12;
  let quotient = scaledNumerator / den;
  const remainder = scaledNumerator % den;
  const twiceRemainder = remainder * TWO;
  if (twiceRemainder > den || (twiceRemainder === den && (quotient & ONE) === ONE)) {
    quotient += ONE;
  }
  if (quotient === ZERO) {
    return '0';
  }
  const whole = quotient / SCALE_12;
  const rem = (quotient % SCALE_12).toString().padStart(12, '0').replace(/0+$/, '');
  return `${negative ? '-' : ''}${whole.toString()}${rem ? `.${rem}` : ''}`;
}

/**
 * Exact BigInt preview calculation for UX while editing a draft budget.
 * Backend BigDecimal calculation remains authoritative for all saved/approved budgets.
 */
export function previewBudgetCalculation(draft: ProjectBudgetDraft): ProjectBudgetPreviewCalculation {
  const subtotalsScaled: Record<ProjectCostCategory, bigint> = {
    MATERIAL: ZERO,
    LABOUR: ZERO,
    OVERHEAD: ZERO,
    FREIGHT: ZERO,
    OTHER: ZERO,
  };
  const revenueScaled = parseScaled12(draft.plannedRevenue, revenuePattern);
  let totalCostScaled = ZERO;
  let allLinesValid = draft.lines.length >= 1 && draft.lines.length <= MAX_PROJECT_BUDGET_LINES;

  const lineCosts = draft.lines.map(line => {
    const qty = parseScaled6(line.quantity, quantityPattern);
    const unitCost = parseScaled6(line.unitCost, unitCostPattern);
    if (qty === null || unitCost === null || !projectCostCategories.includes(line.category)) {
      allLinesValid = false;
      return null;
    }
    const lineCostScaled = qty * unitCost; // 10^6 * 10^6 = 10^12
    totalCostScaled += lineCostScaled;
    subtotalsScaled[line.category] += lineCostScaled;
    return formatScaled12Amount(lineCostScaled);
  });

  const categorySubtotals: Record<ProjectCostCategory, string> = {
    MATERIAL: formatScaled12Amount(subtotalsScaled.MATERIAL),
    LABOUR: formatScaled12Amount(subtotalsScaled.LABOUR),
    OVERHEAD: formatScaled12Amount(subtotalsScaled.OVERHEAD),
    FREIGHT: formatScaled12Amount(subtotalsScaled.FREIGHT),
    OTHER: formatScaled12Amount(subtotalsScaled.OTHER),
  };

  if (revenueScaled === null || !allLinesValid) {
    return {
      previewOnly: true,
      valid: false,
      plannedRevenue: revenueScaled !== null ? formatScaled12Amount(revenueScaled) : null,
      plannedCost: allLinesValid ? formatScaled12Amount(totalCostScaled) : null,
      plannedMargin: null,
      plannedMarginPct: null,
      zeroPlannedRevenue: revenueScaled === ZERO,
      lineCosts,
      categorySubtotals,
    };
  }

  const marginScaled = revenueScaled - totalCostScaled;
  const zeroPlannedRevenue = revenueScaled === ZERO;
  const plannedMarginPct = zeroPlannedRevenue
    ? null
    : divideHalfEven12(marginScaled, revenueScaled);

  return {
    previewOnly: true,
    valid: true,
    plannedRevenue: formatScaled12Amount(revenueScaled),
    plannedCost: formatScaled12Amount(totalCostScaled),
    plannedMargin: formatScaled12Amount(marginScaled),
    plannedMarginPct,
    zeroPlannedRevenue,
    lineCosts,
    categorySubtotals,
  };
}

export function allowedProjectTransitions(status: ProjectStatus): ProjectStatus[] {
  switch (status) {
    case 'DRAFT':
      return ['ACTIVE', 'CANCELLED', 'ARCHIVED'];
    case 'ACTIVE':
      return ['COMPLETED', 'CANCELLED', 'ARCHIVED'];
    case 'COMPLETED':
    case 'CANCELLED':
      return ['ARCHIVED'];
    case 'ARCHIVED':
      return [];
  }
}

export function canMutateProjectPlanning(status: ProjectStatus, hasWritePermission: boolean): boolean {
  return hasWritePermission && (status === 'DRAFT' || status === 'ACTIVE');
}

export function isBudgetVersionReadOnly(status: ProjectBudgetStatus): boolean {
  return status === 'APPROVED' || status === 'SUPERSEDED';
}

export function isTransitionBlockedByOpenDraft(
  targetStatus: ProjectStatus,
  hasOpenDraftBudget: boolean
): boolean {
  return hasOpenDraftBudget && (targetStatus === 'COMPLETED' || targetStatus === 'ARCHIVED');
}

export const projectConflictCodes = [
  'stale_project_version',
  'stale_budget_version',
  'duplicate_project_code',
  'draft_budget_already_exists',
  'open_draft_budget_exists',
  'budget_immutable',
  'invalid_project_state',
  'invalid_project_transition',
  'invalid_budget_transition',
  'project_delete_forbidden',
] as const;

export type ProjectConflictCode = typeof projectConflictCodes[number];

export function isProjectConflictCode(code?: string): code is ProjectConflictCode {
  return typeof code === 'string' && (projectConflictCodes as readonly string[]).includes(code);
}

/**
 * Groups exact server-returned digits without applying any browser floating-point or currency rounding.
 */
export function formatProjectMoney(value: string | null, locale: 'en' | 'ur'): string {
  if (value === null || !/^-?\d+(?:\.\d+)?$/.test(value)) {
    return '—';
  }
  const negative = value.startsWith('-');
  const [whole, fraction] = value.replace(/^-/, '').split('.');
  const digits = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction ? `.${fraction}` : '');
  return locale === 'ur'
    ? `${negative ? '- ' : ''}${digits} روپے`
    : `${negative ? '- ' : ''}PKR ${digits}`;
}

/**
 * Renders an authoritative backend ratio as a percentage via exact string decimal-point shift.
 */
export function formatProjectPercent(value: string | null): string {
  if (value === null || !/^-?\d+(?:\.\d+)?$/.test(value)) {
    return '—';
  }
  const negative = value.startsWith('-');
  const [whole, fraction = ''] = value.replace(/^-/, '').split('.');
  const padded = fraction.padEnd(2, '0');
  const integer = `${whole}${padded.slice(0, 2)}`.replace(/^0+(?=\d)/, '');
  const remainder = padded.slice(2).replace(/0+$/, '');
  return `${negative ? '-' : ''}${integer}${remainder ? `.${remainder}` : ''}%`;
}
