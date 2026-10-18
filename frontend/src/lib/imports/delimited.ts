export const IMPORT_LIMITS = Object.freeze({ bytes: 512 * 1024, characters: 524288, rows: 1000, columns: 32, field: 2000, lines: 5000 });
export type ParseErrorCode = 'sourceLimit' | 'rowLimit' | 'columnLimit' | 'fieldLimit' | 'lineLimit' | 'quoting' | 'control' | 'empty' | 'encoding' | 'fileType';
export class ImportParseError extends Error {
  constructor(public code: ParseErrorCode, public line = 1) { super(code); this.name = 'ImportParseError'; }
}
export interface SourceRow { rowNumber: number; endLine: number; cells: string[]; rawCells: string[] }
export interface SourceTable { rows: SourceRow[]; blankRows: number; columns: number; delimiter: ',' | '\t' }
/** Local, bounded lexical parsing only; never interprets formulas, dates or money. */
export function parseDelimited(input: string, delimiter: ',' | '\t'): SourceTable {
  if (delimiter !== ',' && delimiter !== '\t') throw new ImportParseError('fileType');
  if (input.length > IMPORT_LIMITS.characters) throw new ImportParseError('sourceLimit');
  if (new TextEncoder().encode(input).byteLength > IMPORT_LIMITS.bytes) throw new ImportParseError('sourceLimit');
  const text = input.startsWith('\uFEFF') ? input.slice(1) : input;
  const rows: SourceRow[] = [];
  let blankRows = 0, line = 1, rowStart = 1, cell = '', raw = '', cells: string[] = [], rawCells: string[] = [], state: 'bare' | 'quoted' | 'closed' = 'bare';
  const append = (decoded: string, original = decoded) => {
    cell += decoded; raw += original;
    if (cell.length > IMPORT_LIMITS.field || raw.length > IMPORT_LIMITS.field) throw new ImportParseError('fieldLimit', line);
  };
  const finishCell = () => {
    cells.push(cell); rawCells.push(raw); cell = ''; raw = ''; state = 'bare';
    if (cells.length > IMPORT_LIMITS.columns) throw new ImportParseError('columnLimit', line);
  };
  const finishRow = () => {
    finishCell();
    if (cells.length === 1 && rawCells[0].trim() === '') blankRows++;
    else {
      rows.push({ rowNumber: rowStart, endLine: line, cells, rawCells });
      if (rows.length > IMPORT_LIMITS.rows) throw new ImportParseError('rowLimit', rowStart);
    }
    cells = []; rawCells = [];
  };
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (char === '\0') throw new ImportParseError('control', line);
    const newline = char === '\r' || char === '\n';
    if (state === 'quoted') {
      if (char === '"') {
        if (text[i + 1] === '"') { append('"', '""'); i++; }
        else { raw += char; state = 'closed'; }
      } else if (newline) {
        const sequence = char === '\r' && text[i + 1] === '\n' ? '\r\n' : char;
        append(sequence); if (sequence.length === 2) i++;
        line++; if (line > IMPORT_LIMITS.lines) throw new ImportParseError('lineLimit', line);
      } else append(char);
      if (raw.length > IMPORT_LIMITS.field) throw new ImportParseError('fieldLimit', line);
      continue;
    }
    if (char === delimiter) { finishCell(); if (cells.length >= IMPORT_LIMITS.columns) throw new ImportParseError('columnLimit', line); continue; }
    if (newline) {
      finishRow(); if (char === '\r' && text[i + 1] === '\n') i++;
      line++; rowStart = line; if (line > IMPORT_LIMITS.lines) throw new ImportParseError('lineLimit', line);
      continue;
    }
    if (state === 'closed') throw new ImportParseError('quoting', line);
    if (char === '"') {
      if (raw.length) throw new ImportParseError('quoting', line);
      raw = '"'; state = 'quoted';
    } else append(char);
  }
  if (state === 'quoted') throw new ImportParseError('quoting', rowStart);
  if (raw.length || cells.length || state === 'closed') finishRow();
  if (!rows.length) throw new ImportParseError('empty');
  return { rows, blankRows, columns: Math.max(...rows.map(row => row.cells.length)), delimiter };
}
export function validateCsvFile(name: string, size: number) {
  if (!/\.csv$/i.test(name)) throw new ImportParseError('fileType');
  if (size > IMPORT_LIMITS.bytes) throw new ImportParseError('sourceLimit');
}
export function decodeCsv(buffer: ArrayBuffer): string {
  if (buffer.byteLength > IMPORT_LIMITS.bytes) throw new ImportParseError('sourceLimit');
  try { return new TextDecoder('utf-8', { fatal: true }).decode(buffer); }
  catch { throw new ImportParseError('encoding'); }
}
