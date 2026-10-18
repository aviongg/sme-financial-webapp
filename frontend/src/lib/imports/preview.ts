import { normalizeAmountInput } from '../utils/amount-input';
import { currencyScales, isSupportedCurrency } from '../utils/decimal-display';
import type { SourceRow, SourceTable } from './delimited';

export const fields = ['date', 'description', 'amount', 'direction', 'moneyIn', 'moneyOut', 'reference'] as const;
export type ImportField = typeof fields[number];
export type ColumnMapping = Record<ImportField, number | null>;
export const emptyMapping = (): ColumnMapping => ({ date: null, description: null, amount: null, direction: null, moneyIn: null, moneyOut: null, reference: null });
export type DateFormat = 'YYYY-MM-DD' | 'DD/MM/YYYY' | 'MM/DD/YYYY';
export type AmountMode = 'direction' | 'separate';
export type Classification = '' | 'DIRECT_SALE_RECEIPT' | 'OPERATING_EXPENSE_PAYMENT';
export const classifications: Classification[] = ['', 'DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT'];
export interface ImportAccount { id: string; currency: string; currencyDecimals: number; status: string; balanceStatus: string; openingDate: string }
export type IssueCode = 'unmapped' | 'columnReused' | 'badMapping' | 'rowShape' | 'dateInvalid' | 'descriptionMissing' | 'descriptionLong' | 'amountMissing' | 'amountInvalid' | 'negative' | 'zero' | 'conflictingAmounts' | 'directionInvalid' | 'tokensInvalid' | 'accountMissing' | 'accountUnavailable' | 'currencyInvalid' | 'currencyMismatch' | 'unsupportedCurrency' | 'purposeMissing' | 'purposeMismatch' | 'beforeOpening' | 'futureDate';
export interface RowIssue { field: ImportField | 'row' | 'account' | 'currency' | 'purpose'; code: IssueCode }
export interface PreviewOptions {
 headings: boolean; mapping: ColumnMapping; mode: AmountMode; dateFormat: DateFormat; currency: string;
 account: ImportAccount | null; inToken: string; outToken: string; classification: Classification;
 overrides: Readonly<Record<number, Classification>>; excluded: ReadonlySet<number>; today?: string;
}
export interface PreviewRow {
 source: SourceRow; date: string | null; description: string; reference: string;
 amount: string | null; direction: 'IN' | 'OUT' | null; classification: Classification;
 originalAmounts: { field: ImportField; text: string; normalized: string | null; rounded: boolean; blank: boolean }[];
 issues: RowIssue[]; duplicateRows: number[]; excluded: boolean; ready: boolean;
}
export const westernDigits = (text: string) => text.replace(/[۰-۹٠-٩]/g, digit => String(digit.charCodeAt(0) - (digit >= '۰' ? 0x6f0 : 0x660)));
export function parseImportDate(raw: string, format: DateFormat): string | null {
 if (!['YYYY-MM-DD', 'DD/MM/YYYY', 'MM/DD/YYYY'].includes(format)) return null;
 const text = westernDigits(raw.trim());
 const match = format === 'YYYY-MM-DD' ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(text) : /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text);
 if (!match) return null;
 const year = Number(format === 'YYYY-MM-DD' ? match[1] : match[3]);
 const month = Number(format === 'YYYY-MM-DD' ? match[2] : format === 'DD/MM/YYYY' ? match[2] : match[1]);
 const day = Number(format === 'YYYY-MM-DD' ? match[3] : format === 'DD/MM/YYYY' ? match[1] : match[2]);
 const days = [31, year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
 if (year < 1 || month < 1 || month > 12 || day < 1 || day > days[month - 1]) return null;
 return String(year).padStart(4, '0') + '-' + String(month).padStart(2, '0') + '-' + String(day).padStart(2, '0');
}
export function importToday(now = new Date()): string {
 const parts = new Intl.DateTimeFormat('en-US-u-nu-latn', { timeZone: 'Asia/Karachi', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
 const part = (kind: string) => parts.find(p => p.type === kind)!.value;
 return part('year') + '-' + part('month') + '-' + part('day');
}
export function activeFields(mode: AmountMode): ImportField[] { return ['date', 'description', ...(mode === 'direction' ? ['amount', 'direction'] as const : ['moneyIn', 'moneyOut'] as const), 'reference']; }
export function mappingIssues(mapping: ColumnMapping, columns: number, mode: AmountMode): RowIssue[] {
 const issues: RowIssue[] = [], occupied = new Map<number, ImportField>();
 for (const field of activeFields(mode)) {
  const column = mapping[field];
  if (column === null) { if (field !== 'reference') issues.push({ field, code: 'unmapped' }); continue; }
  if (!Number.isInteger(column) || column < 0 || column >= columns) { issues.push({ field, code: 'badMapping' }); continue; }
  const previous = occupied.get(column);
  if (previous) { issues.push({ field, code: 'columnReused' }); if (!issues.some(i => i.field === previous && i.code === 'columnReused')) issues.push({ field: previous, code: 'columnReused' }); }
  else occupied.set(column, field);
 }
 return issues;
}
function amountCell(text: string, currency: string) {
 const blank = text.trim() === '';
 const signed = westernDigits(text.trim()).startsWith('-');
 const result = blank || signed ? null : normalizeAmountInput(text, currency);
 return { text, blank, signed, normalized: result?.canonical ?? null, rounded: result?.rounded ?? false, nonzeroInput: result !== null && /[1-9]/.test(westernDigits(text)) };
}
export function reviewImport(table: SourceTable, options: PreviewOptions): PreviewRow[] {
 const { mapping, currency } = options;
 const globalIssues = mappingIssues(mapping, table.columns, options.mode);
 const knownCurrency = isSupportedCurrency(currency);
 const inToken = options.inToken.trim().toLocaleLowerCase('en'), outToken = options.outToken.trim().toLocaleLowerCase('en');
 const rows = (options.headings ? table.rows.slice(1) : table.rows).map((source): PreviewRow => {
  const cell = (field: ImportField) => mapping[field] === null ? '' : source.cells[mapping[field]!] ?? '';
  const issues: RowIssue[] = globalIssues.map(issue => ({ ...issue }));
  const add = (field: RowIssue['field'], code: IssueCode) => issues.push({ field, code });
  if (activeFields(options.mode).some(field => field !== 'reference' && mapping[field] !== null && mapping[field]! >= source.cells.length)) add('row', 'rowShape');
  const date = parseImportDate(cell('date'), options.dateFormat), description = cell('description').trim(), reference = cell('reference');
  if (!date) add('date', 'dateInvalid');
  if (!description) add('description', 'descriptionMissing');
  else if (description.length > 255) add('description', 'descriptionLong');
  if (!knownCurrency) add('currency', 'currencyInvalid');
  else if (currency !== 'PKR') add('currency', 'unsupportedCurrency');
  if (!options.account) add('account', 'accountMissing');
  else {
   if (options.account.status !== 'ACTIVE' || options.account.balanceStatus !== 'AVAILABLE') add('account', 'accountUnavailable');
   if (options.account.currency !== currency || !isSupportedCurrency(options.account.currency) || options.account.currencyDecimals !== currencyScales[options.account.currency]) add('currency', 'currencyMismatch');
   if (date && date < options.account.openingDate) add('date', 'beforeOpening');
  }
  if (date && date > (options.today ?? importToday())) add('date', 'futureDate');
  let amount: string | null = null, direction: PreviewRow['direction'] = null;
  const originalAmounts: PreviewRow['originalAmounts'] = [];
  const readAmount = (field: ImportField) => {
   const a = amountCell(cell(field), currency); originalAmounts.push({ field, text: a.text, normalized: a.normalized, rounded: a.rounded, blank: a.blank });
   if (a.signed) add(field, 'negative'); else if (!a.blank && !a.normalized) add(field, 'amountInvalid');
   return a;
  };
  if (options.mode === 'direction') {
   const a = readAmount('amount'); amount = a.normalized;
   if (a.blank) add('amount', 'amountMissing');
   else if (a.normalized && !a.normalized.replace(/[0.]/g, '')) add('amount', 'zero');
   if (!inToken || !outToken || inToken === outToken) add('direction', 'tokensInvalid');
   else { const token = cell('direction').trim().toLocaleLowerCase('en'); direction = token === inToken ? 'IN' : token === outToken ? 'OUT' : null; if (!direction) add('direction', 'directionInvalid'); }
  } else {
   const incoming = readAmount('moneyIn'), outgoing = readAmount('moneyOut');
   if (incoming.nonzeroInput && outgoing.nonzeroInput) { add('moneyIn', 'conflictingAmounts'); add('moneyOut', 'conflictingAmounts'); }
   else if ((incoming.blank || incoming.normalized) && (outgoing.blank || outgoing.normalized)) {
    if (incoming.nonzeroInput) { direction = 'IN'; amount = incoming.normalized; }
    else if (outgoing.nonzeroInput) { direction = 'OUT'; amount = outgoing.normalized; }
    if (!amount || !amount.replace(/[0.]/g, '')) add('row', incoming.blank && outgoing.blank ? 'amountMissing' : 'zero');
   }
  }
  const classification = Object.prototype.hasOwnProperty.call(options.overrides, source.rowNumber) ? options.overrides[source.rowNumber] : options.classification;
  if (!classification || !classifications.includes(classification)) add('purpose', 'purposeMissing');
  else if (direction && (classification === 'DIRECT_SALE_RECEIPT' ? direction !== 'IN' : direction !== 'OUT')) add('purpose', 'purposeMismatch');
  const excluded = options.excluded.has(source.rowNumber);
  return { source, date, description, reference, amount, direction, classification, originalAmounts, issues, duplicateRows: [], excluded, ready: !excluded && issues.length === 0 };
 });
 const candidates = new Map<string, PreviewRow[]>();
 for (const row of rows) {
  if (!row.date || !row.description || !row.amount || !row.direction) continue;
  const key = JSON.stringify([row.date, row.description, row.amount, row.direction]);
  const matches = candidates.get(key) ?? []; matches.push(row); candidates.set(key, matches);
 }
 for (const matches of candidates.values()) if (matches.length > 1) for (const row of matches) row.duplicateRows = matches.filter(other => other !== row).map(other => other.source.rowNumber);
 return rows;
}
export function previewCounts(rows: readonly PreviewRow[]) {
 return { source: rows.length, included: rows.filter(r => !r.excluded).length, excluded: rows.filter(r => r.excluded).length, issues: rows.filter(r => !r.excluded && r.issues.length > 0).length, ready: rows.filter(r => r.ready).length };
}
export function excludeRows(current: ReadonlySet<number>, rowNumbers: readonly number[], exclude: boolean): Set<number> {
 const next = new Set(current); for (const row of rowNumbers) { if (exclude) next.add(row); else next.delete(row); } return next;
}
