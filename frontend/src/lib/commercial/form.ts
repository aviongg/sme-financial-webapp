/**
 * F04 Phase A commercial document editor state and payload builders.
 * The browser never computes authoritative amounts: line amounts and totals come only from the backend
 * preview and persisted responses. Client checks mirror backend formats so obviously malformed input is
 * flagged early; the server remains the sole authority (for example for line_amount_not_exact).
 */
export const commercialKinds = ['INVOICE', 'BILL'] as const;
export const commercialStatuses = ['DRAFT', 'FINALIZED', 'VOID'] as const;

export type CommercialKind = typeof commercialKinds[number];
export type CommercialStatus = typeof commercialStatuses[number];

export const MAX_COMMERCIAL_LINES = 100;
export const MAX_COUNTERPARTY_LENGTH = 160;
export const MAX_NOTES_LENGTH = 1000;
export const MAX_LINE_DESCRIPTION_LENGTH = 200;
export const MAX_UNIT_LENGTH = 40;
export const MAX_VOID_REASON_LENGTH = 500;

export interface CommercialLineInput {
  description: string;
  quantity: string;
  unit: string | null;
  unitPrice: string;
}

export interface CommercialLineDraft {
  id: string;
  description: string;
  quantity: string;
  unit: string;
  unitPrice: string;
}

export interface CommercialDocumentDraft {
  counterpartyName: string;
  documentDate: string;
  dueDate: string;
  notes: string;
  lines: CommercialLineDraft[];
}

export interface CommercialPreviewPayload {
  lines: CommercialLineInput[];
}

export interface CommercialCreatePayload {
  counterpartyName: string | null;
  documentDate: string | null;
  dueDate: string | null;
  notes: string | null;
  lines: CommercialLineInput[];
}

export interface CommercialUpdatePayload extends CommercialCreatePayload {
  expectedVersion: number;
}

export interface CommercialFinalizePayload {
  expectedVersion: number;
}

export interface CommercialVoidPayload {
  expectedVersion: number;
  reason: string;
}

export type CommercialValidationIssue =
  | 'required'
  | 'tooLong'
  | 'text'
  | 'date'
  | 'dateOrder'
  | 'decimal'
  | 'decimalPlaces'
  | 'range'
  | 'positive'
  | 'notExact'
  | 'lines'
  | 'invalid';

export type CommercialValidationErrors = Record<string, CommercialValidationIssue>;

export const commercialConflictCodes = [
  'stale_document_version',
  'document_not_draft',
  'document_already_finalized',
  'document_already_void',
  'invalid_document_transition',
  'document_delete_forbidden',
] as const;
export type CommercialConflictCode = typeof commercialConflictCodes[number];

export const commercialFinalizeBlockers = [
  'counterparty_required',
  'document_date_required',
  'lines_required',
  'total_must_be_positive',
] as const;
export type CommercialFinalizeBlocker = typeof commercialFinalizeBlockers[number];

const decimalPattern = /^(\d+)(?:\.(\d+))?$/;
const isoDatePattern = /^\d{4}-\d{2}-\d{2}$/;
const controlPattern = /[\u0000-\u001f\u007f-\u009f]/;
const controlExceptNewlinePattern = /[\u0000-\u0009\u000b-\u001f\u007f-\u009f]/;

export function commercialPath(kind: CommercialKind): '/invoices' | '/bills' {
  return kind === 'INVOICE' ? '/invoices' : '/bills';
}

export function emptyLine(id: string): CommercialLineDraft {
  return { id, description: '', quantity: '1', unit: '', unitPrice: '' };
}

export function initialCommercialDraft(today: string, firstLineId = 'line-1'): CommercialDocumentDraft {
  return { counterpartyName: '', documentDate: today, dueDate: '', notes: '', lines: [emptyLine(firstLineId)] };
}

export interface CommercialDocumentLike {
  counterpartyName: string | null;
  documentDate: string | null;
  dueDate: string | null;
  notes: string | null;
  lines: { id: string; description: string; quantity: string; unit: string | null; unitPrice: string }[] | null;
}

export function draftFromDocument(document: CommercialDocumentLike): CommercialDocumentDraft {
  return {
    counterpartyName: document.counterpartyName ?? '',
    documentDate: document.documentDate ?? '',
    dueDate: document.dueDate ?? '',
    notes: document.notes ?? '',
    lines: (document.lines ?? []).map(line => ({
      id: line.id,
      description: line.description,
      quantity: line.quantity,
      unit: line.unit ?? '',
      unitPrice: line.unitPrice,
    })),
  };
}

export function addCommercialLine(draft: CommercialDocumentDraft, id: string): CommercialDocumentDraft {
  if (draft.lines.length >= MAX_COMMERCIAL_LINES) return draft;
  return { ...draft, lines: [...draft.lines, emptyLine(id)] };
}

export function changeCommercialLine(
  draft: CommercialDocumentDraft,
  id: string,
  field: keyof Omit<CommercialLineDraft, 'id'>,
  value: string
): CommercialDocumentDraft {
  return { ...draft, lines: draft.lines.map(line => (line.id === id ? { ...line, [field]: value } : line)) };
}

export function removeCommercialLine(draft: CommercialDocumentDraft, id: string): CommercialDocumentDraft {
  return { ...draft, lines: draft.lines.filter(line => line.id !== id) };
}

function text(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function length(value: string): number {
  return Array.from(value).length;
}

function decimalIssue(value: string, integerDigits: number, fractionDigits: number): CommercialValidationIssue | null {
  const trimmed = value.trim();
  if (!trimmed) return 'required';
  const match = decimalPattern.exec(trimmed);
  if (!match || trimmed.length > 40) return 'decimal';
  if (match[2] && match[2].length > fractionDigits) return 'decimalPlaces';
  if (match[1].replace(/^0+(?=\d)/, '').length > integerDigits) return 'range';
  return null;
}

function isPositiveDecimal(value: string): boolean {
  return /[1-9]/.test(value.trim());
}

function validDate(value: string): boolean {
  if (!isoDatePattern.test(value)) return false;
  const [year, month, day] = value.split('-').map(part => Number.parseInt(part, 10));
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

function textIssue(value: string, max: number, multiline = false): CommercialValidationIssue | null {
  const trimmed = value.trim();
  if ((multiline ? controlExceptNewlinePattern : controlPattern).test(trimmed)) return 'text';
  if (length(trimmed) > max) return 'tooLong';
  return null;
}

/** Field-level checks for the lines only (used before requesting a backend preview). */
export function validateCommercialLines(lines: CommercialLineDraft[]): CommercialValidationErrors {
  const errors: CommercialValidationErrors = {};
  if (lines.length > MAX_COMMERCIAL_LINES) errors.lines = 'lines';
  lines.forEach((line, index) => {
    const path = `lines[${index}]`;
    if (!line.description.trim()) errors[`${path}.description`] = 'required';
    else {
      const issue = textIssue(line.description, MAX_LINE_DESCRIPTION_LENGTH);
      if (issue) errors[`${path}.description`] = issue;
    }
    const unitIssue = textIssue(line.unit, MAX_UNIT_LENGTH);
    if (unitIssue) errors[`${path}.unit`] = unitIssue;
    const quantityIssue = decimalIssue(line.quantity, 9, 6);
    if (quantityIssue) errors[`${path}.quantity`] = quantityIssue;
    else if (!isPositiveDecimal(line.quantity)) errors[`${path}.quantity`] = 'positive';
    const priceIssue = decimalIssue(line.unitPrice, 12, 2);
    if (priceIssue) errors[`${path}.unitPrice`] = priceIssue;
  });
  return errors;
}

export function validateCommercialDraft(draft: CommercialDocumentDraft): CommercialValidationErrors {
  const errors: CommercialValidationErrors = validateCommercialLines(draft.lines);
  const counterpartyIssue = textIssue(draft.counterpartyName, MAX_COUNTERPARTY_LENGTH);
  if (counterpartyIssue) errors.counterpartyName = counterpartyIssue;
  const notesIssue = textIssue(draft.notes, MAX_NOTES_LENGTH, true);
  if (notesIssue) errors.notes = notesIssue;
  const documentDate = draft.documentDate.trim();
  const dueDate = draft.dueDate.trim();
  if (documentDate && !validDate(documentDate)) errors.documentDate = 'date';
  if (dueDate && !validDate(dueDate)) errors.dueDate = 'date';
  if (!errors.documentDate && !errors.dueDate && documentDate && dueDate && dueDate < documentDate) errors.dueDate = 'dateOrder';
  return errors;
}

export function validateVoidReason(reason: string): CommercialValidationIssue | null {
  if (!reason.trim()) return 'required';
  return textIssue(reason, MAX_VOID_REASON_LENGTH);
}

export function commercialLinesPayload(lines: CommercialLineDraft[]): CommercialLineInput[] {
  return lines.map(line => ({
    description: line.description.trim(),
    quantity: line.quantity.trim(),
    unit: text(line.unit),
    unitPrice: line.unitPrice.trim(),
  }));
}

/** Only client-owned fields are ever sent; totals, status, numbers and identities are server-owned. */
export function commercialCreatePayload(draft: CommercialDocumentDraft): CommercialCreatePayload {
  return {
    counterpartyName: text(draft.counterpartyName),
    documentDate: text(draft.documentDate),
    dueDate: text(draft.dueDate),
    notes: text(draft.notes),
    lines: commercialLinesPayload(draft.lines),
  };
}

export function commercialUpdatePayload(draft: CommercialDocumentDraft, expectedVersion: number): CommercialUpdatePayload {
  return { ...commercialCreatePayload(draft), expectedVersion };
}

export function commercialPreviewPayload(draft: CommercialDocumentDraft): CommercialPreviewPayload {
  return { lines: commercialLinesPayload(draft.lines) };
}

export function commercialFinalizePayload(expectedVersion: number): CommercialFinalizePayload {
  return { expectedVersion };
}

export function commercialVoidPayload(expectedVersion: number, reason: string): CommercialVoidPayload {
  return { expectedVersion, reason: reason.trim() };
}

const serverIssueMap: Record<string, CommercialValidationIssue> = {
  required: 'required',
  too_long: 'tooLong',
  text_invalid: 'text',
  date_invalid: 'date',
  due_date_before_document_date: 'dateOrder',
  decimal_invalid: 'decimal',
  string_required: 'decimal',
  too_many_decimal_places: 'decimalPlaces',
  out_of_range: 'range',
  line_amount_out_of_range: 'range',
  total_out_of_range: 'range',
  quantity_must_be_positive: 'positive',
  line_amount_not_exact: 'notExact',
  line_count_invalid: 'lines',
};

export function mapCommercialFieldErrors(errors: Record<string, string> | undefined): CommercialValidationErrors {
  const mapped: CommercialValidationErrors = {};
  for (const [field, code] of Object.entries(errors ?? {})) mapped[field] = serverIssueMap[code] ?? 'invalid';
  return mapped;
}

export function isCommercialConflictCode(code: string | undefined): code is CommercialConflictCode {
  return typeof code === 'string' && (commercialConflictCodes as readonly string[]).includes(code);
}

export function isCommercialFinalizeBlocker(code: string | undefined): code is CommercialFinalizeBlocker {
  return typeof code === 'string' && (commercialFinalizeBlockers as readonly string[]).includes(code);
}

export interface CommercialActions {
  edit: boolean;
  remove: boolean;
  finalize: boolean;
  void: boolean;
}

export function commercialActions(status: CommercialStatus, canWrite: boolean): CommercialActions {
  return {
    edit: canWrite && status === 'DRAFT',
    remove: canWrite && status === 'DRAFT',
    finalize: canWrite && status === 'DRAFT',
    void: canWrite && status === 'FINALIZED',
  };
}

/** Exact string formatting of a server amount; never parses into a floating-point number. */
export function formatCommercialMoney(value: string | null, locale: 'en' | 'ur'): string {
  if (value === null || !/^\d+(?:\.\d+)?$/.test(value)) return '—';
  const [whole, fraction] = value.split('.');
  const digits = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction ? `.${fraction}` : '');
  return locale === 'ur' ? `${digits} روپے` : `PKR ${digits}`;
}

export function todayIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** Local calendar date of a server timestamp (the server serializes instants in UTC). */
export function localDateOf(timestamp: string | null): string | null {
  if (!timestamp) return null;
  const date = new Date(timestamp);
  return Number.isNaN(date.getTime()) ? null : todayIso(date);
}
