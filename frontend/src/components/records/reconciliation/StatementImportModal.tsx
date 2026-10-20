"use client";

import { useState, useId, useEffect } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { reconciliationCopyEn, reconciliationCopyUr } from './reconciliation-copy';
import { reconciliationApi, StatementEntryItem, StatementImportPayload, StatementImportResponse, MovementDirection } from '@/lib/api/dev-a/reconciliation';

interface Props {
  accountId: string;
  accountName: string;
  isOpen: boolean;
  onClose: () => void;
  onImportSuccess: (imported: StatementImportResponse) => void;
}

type ImportMode = 'CSV' | 'TSV';
type AmountFormat = 'SEPARATE' | 'SIGNED' | 'CREDIT_ONLY' | 'DEBIT_ONLY';

function parseDelimited(text: string, delimiter: string): string[][] {
  const clean = text.charCodeAt(0) === 0xFEFF ? text.slice(1) : text;
  const rows: string[][] = [];
  let currentRow: string[] = [];
  let currentCell = '';
  let insideQuote = false;

  for (let i = 0; i < clean.length; i++) {
    const char = clean[i];
    const nextChar = clean[i + 1];

    if (char === '"') {
      if (insideQuote && nextChar === '"') {
        currentCell += '"';
        i++; // skip escaped quote
      } else {
        insideQuote = !insideQuote;
      }
    } else if (char === delimiter && !insideQuote) {
      currentRow.push(currentCell.trim());
      currentCell = '';
    } else if ((char === '\r' || char === '\n') && !insideQuote) {
      if (char === '\r' && nextChar === '\n') {
        i++; // skip \n
      }
      currentRow.push(currentCell.trim());
      if (currentRow.some(c => c.length > 0)) {
        rows.push(currentRow);
      }
      currentRow = [];
      currentCell = '';
    } else {
      currentCell += char;
    }
  }

  if (currentCell.length > 0 || currentRow.length > 0) {
    currentRow.push(currentCell.trim());
    if (currentRow.some(c => c.length > 0)) {
      rows.push(currentRow);
    }
  }

  return rows;
}

function parseNaturalDate(val: string): string | null {
  const trimmed = val.trim();
  // YYYY-MM-DD
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    return trimmed;
  }
  // DD/MM/YYYY or DD-MM-YYYY
  const dmyMatch = trimmed.match(/^(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{4})$/);
  if (dmyMatch) {
    const day = dmyMatch[1].padStart(2, '0');
    const month = dmyMatch[2].padStart(2, '0');
    const year = dmyMatch[3];
    return `${year}-${month}-${day}`;
  }
  // Try Date constructor
  const parsed = new Date(trimmed);
  if (!isNaN(parsed.getTime())) {
    return parsed.toISOString().split('T')[0];
  }
  return null;
}

function parseAmount(val: string): number | null {
  if (!val) return null;
  // Remove currency codes, spaces, commas
  const clean = val.replace(/[^0-9.\-+]/g, '');
  if (!clean) return null;
  const num = parseFloat(clean);
  return isNaN(num) ? null : Math.round(num * 100) / 100;
}

export function StatementImportModal({ accountId, accountName, isOpen, onClose, onImportSuccess }: Props) {
  const { locale, direction } = useLanguage();
  const copy = locale === 'ur' ? reconciliationCopyUr : reconciliationCopyEn;

  const [mode, setMode] = useState<ImportMode>('CSV');
  const [sourceFilename, setSourceFilename] = useState<string>('bank_statement.csv');
  const [rawText, setRawText] = useState<string>('');
  const [hasHeader, setHasHeader] = useState<boolean>(true);

  // Column indexes (0-based)
  const [dateCol, setDateCol] = useState<number>(0);
  const [descCol, setDescCol] = useState<number>(1);
  const [refCol, setRefCol] = useState<number>(-1);

  const [amtFormat, setAmtFormat] = useState<AmountFormat>('SEPARATE');
  const [debitCol, setDebitCol] = useState<number>(2);
  const [creditCol, setCreditCol] = useState<number>(3);
  const [singleAmtCol, setSingleAmtCol] = useState<number>(2);

  const [parsedRows, setParsedRows] = useState<string[][]>([]);
  const [headers, setHeaders] = useState<string[]>([]);
  const [previewEntries, setPreviewEntries] = useState<StatementEntryItem[]>([]);
  const [errorCount, setErrorCount] = useState<number>(0);
  const [errorMessages, setErrorMessages] = useState<string[]>([]);

  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const modalId = useId();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setSourceFilename(file.name);
    const reader = new FileReader();
    reader.onload = (event) => {
      const text = (event.target?.result as string) || '';
      setRawText(text);
      processRawData(text, file.name.endsWith('.tsv') ? '\t' : ',');
    };
    reader.readAsText(file);
  };

  const handlePasteChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const text = e.target.value;
    setRawText(text);
    processRawData(text, '\t');
  };

  const processRawData = (text: string, delimiter: string) => {
    setSubmitError(null);
    if (!text.trim()) {
      setParsedRows([]);
      setHeaders([]);
      setPreviewEntries([]);
      setErrorCount(0);
      return;
    }

    const rows = parseDelimited(text, delimiter);
    if (rows.length === 0) return;

    setParsedRows(rows);

    if (hasHeader && rows.length > 0) {
      setHeaders(rows[0]);
    } else {
      setHeaders(rows[0].map((_, idx) => `Col ${idx + 1}`));
    }

    generatePreview(rows, hasHeader, dateCol, descCol, refCol, amtFormat, debitCol, creditCol, singleAmtCol);
  };

  const generatePreview = (
    rows: string[][],
    header: boolean,
    dCol: number,
    dsCol: number,
    rCol: number,
    format: AmountFormat,
    debCol: number,
    credCol: number,
    sCol: number
  ) => {
    const dataRows = header ? rows.slice(1) : rows;
    const entries: StatementEntryItem[] = [];
    let errors = 0;
    const errList: string[] = [];

    dataRows.forEach((row, idx) => {
      const rowNum = idx + (header ? 2 : 1);
      const rawDate = row[dCol] || '';
      const date = parseNaturalDate(rawDate);
      if (!date) {
        errors++;
        if (errList.length < 5) errList.push(`Row ${rowNum}: Invalid date "${rawDate}"`);
        return;
      }

      const desc = row[dsCol] || 'Bank Transaction';
      const ref = rCol >= 0 && row[rCol] ? row[rCol] : null;

      let direction: MovementDirection = 'INCREASE';
      let amount: number | null = null;

      if (format === 'SEPARATE') {
        const rawDebit = row[debCol];
        const rawCredit = row[credCol];
        const debAmt = parseAmount(rawDebit);
        const credAmt = parseAmount(rawCredit);

        if (debAmt && debAmt > 0) {
          direction = 'DECREASE';
          amount = debAmt;
        } else if (credAmt && credAmt > 0) {
          direction = 'INCREASE';
          amount = credAmt;
        } else {
          errors++;
          if (errList.length < 5) errList.push(`Row ${rowNum}: Both debit and credit are empty or zero`);
          return;
        }
      } else if (format === 'SIGNED') {
        const amt = parseAmount(row[sCol]);
        if (amt === null || amt === 0) {
          errors++;
          if (errList.length < 5) errList.push(`Row ${rowNum}: Invalid or zero amount`);
          return;
        }
        if (amt < 0) {
          direction = 'DECREASE';
          amount = Math.abs(amt);
        } else {
          direction = 'INCREASE';
          amount = amt;
        }
      } else if (format === 'CREDIT_ONLY') {
        const amt = parseAmount(row[sCol]);
        if (!amt || amt <= 0) {
          errors++;
          if (errList.length < 5) errList.push(`Row ${rowNum}: Invalid credit amount`);
          return;
        }
        direction = 'INCREASE';
        amount = amt;
      } else if (format === 'DEBIT_ONLY') {
        const amt = parseAmount(row[sCol]);
        if (!amt || amt <= 0) {
          errors++;
          if (errList.length < 5) errList.push(`Row ${rowNum}: Invalid debit amount`);
          return;
        }
        direction = 'DECREASE';
        amount = amt;
      }

      if (amount !== null && amount > 0) {
        entries.push({
          sourceRowNumber: rowNum,
          postedDate: date,
          direction,
          amount,
          description: desc,
          bankReference: ref
        });
      }
    });

    setPreviewEntries(entries);
    setErrorCount(errors);
    setErrorMessages(errList);
  };

  const handleMappingChange = () => {
    if (parsedRows.length > 0) {
      generatePreview(parsedRows, hasHeader, dateCol, descCol, refCol, amtFormat, debitCol, creditCol, singleAmtCol);
    }
  };

  const handleSaveImport = async () => {
    if (previewEntries.length === 0) {
      setSubmitError(copy.emptyEntries);
      return;
    }

    setSubmitting(true);
    setSubmitError(null);

    try {
      const payload: StatementImportPayload = {
        accountId,
        sourceFilename,
        mappingConfig: JSON.stringify({
          mode,
          hasHeader,
          dateCol,
          descCol,
          refCol,
          amtFormat,
          debitCol,
          creditCol,
          singleAmtCol
        }),
        entries: previewEntries
      };

      const idempotencyKey = `imp-${accountId.slice(0, 8)}-${Date.now()}`;
      const response = await reconciliationApi.importStatement(payload, idempotencyKey);
      onImportSuccess(response);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Import failed';
      setSubmitError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={`${modalId}-title`}
      className="fixed inset-0 z-50 overflow-y-auto bg-black/50 flex items-center justify-center p-4"
    >
      <div
        dir={direction}
        className="bg-white rounded-xl shadow-2xl max-w-4xl w-full max-h-[90vh] flex flex-col overflow-hidden text-[var(--color-text-primary)]"
      >
        {/* Modal Header */}
        <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center bg-[var(--color-brand-primary)] text-white">
          <div>
            <h2 id={`${modalId}-title`} className="text-xl font-bold">
              {copy.importModalTitle}
            </h2>
            <p className="text-xs text-teal-100">
              {accountName} · {copy.importModalSubtitle}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-white hover:bg-teal-800 p-1.5 rounded-lg focus-visible:outline-2 focus-visible:outline-white"
            aria-label={copy.closeBtn}
          >
            ✕
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1">
          {submitError && (
            <div className="p-3 bg-red-50 text-red-700 border border-red-200 rounded-lg text-sm" role="alert">
              {submitError}
            </div>
          )}

          {/* Mode Switch: CSV vs TSV */}
          <div className="flex gap-4 border-b border-gray-200 pb-3">
            <button
              type="button"
              onClick={() => { setMode('CSV'); setRawText(''); setPreviewEntries([]); }}
              className={`px-4 py-2 font-medium text-sm rounded-lg transition-colors ${
                mode === 'CSV'
                  ? 'bg-[var(--color-brand-primary)] text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              {copy.modeCsv}
            </button>
            <button
              type="button"
              onClick={() => { setMode('TSV'); setRawText(''); setPreviewEntries([]); }}
              className={`px-4 py-2 font-medium text-sm rounded-lg transition-colors ${
                mode === 'TSV'
                  ? 'bg-[var(--color-brand-primary)] text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              {copy.modeTsv}
            </button>
          </div>

          {/* Input Area */}
          {mode === 'CSV' ? (
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">
                {copy.fileLabel}
              </label>
              <input
                type="file"
                accept=".csv,.tsv"
                onChange={handleFileUpload}
                className="block w-full text-sm text-gray-500 file:mr-4 file:py-2.5 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-teal-50 file:text-[var(--color-brand-primary)] hover:file:bg-teal-100 cursor-pointer border border-gray-300 rounded-lg p-2"
              />
            </div>
          ) : (
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">
                {copy.pasteLabel}
              </label>
              <textarea
                rows={4}
                value={rawText}
                onChange={handlePasteChange}
                placeholder={copy.pastePlaceholder}
                className="w-full text-sm p-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[var(--color-brand-primary)] focus:border-transparent font-mono"
              />
            </div>
          )}

          {/* Column Mapping Section */}
          {headers.length > 0 && (
            <div className="bg-gray-50 p-4 rounded-xl border border-gray-200 space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-gray-900">{copy.mappingTitle}</h3>
                <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={hasHeader}
                    onChange={(e) => {
                      setHasHeader(e.target.checked);
                      if (parsedRows.length > 0) {
                        setHeaders(e.target.checked ? parsedRows[0] : parsedRows[0].map((_, i) => `Col ${i + 1}`));
                        generatePreview(parsedRows, e.target.checked, dateCol, descCol, refCol, amtFormat, debitCol, creditCol, singleAmtCol);
                      }
                    }}
                    className="rounded text-[var(--color-brand-primary)] focus:ring-[var(--color-brand-primary)]"
                  />
                  {copy.hasHeaderRow}
                </label>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                {/* Date Column */}
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1">{copy.mapDate}</label>
                  <select
                    value={dateCol}
                    onChange={(e) => { setDateCol(Number(e.target.value)); handleMappingChange(); }}
                    className="w-full text-sm p-2 border border-gray-300 rounded-lg bg-white"
                  >
                    {headers.map((h, i) => (
                      <option key={i} value={i}>{h || `Col ${i + 1}`}</option>
                    ))}
                  </select>
                </div>

                {/* Description Column */}
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1">{copy.mapDescription}</label>
                  <select
                    value={descCol}
                    onChange={(e) => { setDescCol(Number(e.target.value)); handleMappingChange(); }}
                    className="w-full text-sm p-2 border border-gray-300 rounded-lg bg-white"
                  >
                    {headers.map((h, i) => (
                      <option key={i} value={i}>{h || `Col ${i + 1}`}</option>
                    ))}
                  </select>
                </div>

                {/* Reference Column */}
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1">{copy.mapReference}</label>
                  <select
                    value={refCol}
                    onChange={(e) => { setRefCol(Number(e.target.value)); handleMappingChange(); }}
                    className="w-full text-sm p-2 border border-gray-300 rounded-lg bg-white"
                  >
                    <option value={-1}>None</option>
                    {headers.map((h, i) => (
                      <option key={i} value={i}>{h || `Col ${i + 1}`}</option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Amount Format */}
              <div className="space-y-3 pt-2 border-t border-gray-200">
                <label className="block text-xs font-semibold text-gray-600">{copy.mapAmountType}</label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                    <input
                      type="radio"
                      name="amtFormat"
                      value="SEPARATE"
                      checked={amtFormat === 'SEPARATE'}
                      onChange={() => { setAmtFormat('SEPARATE'); handleMappingChange(); }}
                      className="text-[var(--color-brand-primary)]"
                    />
                    {copy.amtFormatSeparate}
                  </label>
                  <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                    <input
                      type="radio"
                      name="amtFormat"
                      value="SIGNED"
                      checked={amtFormat === 'SIGNED'}
                      onChange={() => { setAmtFormat('SIGNED'); handleMappingChange(); }}
                      className="text-[var(--color-brand-primary)]"
                    />
                    {copy.amtFormatSigned}
                  </label>
                </div>

                {amtFormat === 'SEPARATE' ? (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 mb-1">{copy.mapDebit}</label>
                      <select
                        value={debitCol}
                        onChange={(e) => { setDebitCol(Number(e.target.value)); handleMappingChange(); }}
                        className="w-full text-sm p-2 border border-gray-300 rounded-lg bg-white"
                      >
                        {headers.map((h, i) => (
                          <option key={i} value={i}>{h || `Col ${i + 1}`}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 mb-1">{copy.mapCredit}</label>
                      <select
                        value={creditCol}
                        onChange={(e) => { setCreditCol(Number(e.target.value)); handleMappingChange(); }}
                        className="w-full text-sm p-2 border border-gray-300 rounded-lg bg-white"
                      >
                        {headers.map((h, i) => (
                          <option key={i} value={i}>{h || `Col ${i + 1}`}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                ) : (
                  <div className="pt-2">
                    <label className="block text-xs font-semibold text-gray-600 mb-1">{copy.mapAmount}</label>
                    <select
                      value={singleAmtCol}
                      onChange={(e) => { setSingleAmtCol(Number(e.target.value)); handleMappingChange(); }}
                      className="w-full text-sm p-2 border border-gray-300 rounded-lg bg-white sm:w-1/2"
                    >
                      {headers.map((h, i) => (
                        <option key={i} value={i}>{h || `Col ${i + 1}`}</option>
                      ))}
                    </select>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Preview Statistics */}
          {parsedRows.length > 0 && (
            <div className="space-y-3">
              <div className="flex items-center justify-between text-sm">
                <span className="font-semibold text-gray-900">{copy.previewTitle}</span>
                <div className="flex gap-4 text-xs font-medium">
                  <span className="text-gray-600">{copy.totalParsedRows}: {hasHeader ? parsedRows.length - 1 : parsedRows.length}</span>
                  <span className="text-emerald-700">{copy.validRows}: {previewEntries.length}</span>
                  {errorCount > 0 && <span className="text-red-600">{copy.errorRows}: {errorCount}</span>}
                </div>
              </div>

              {errorMessages.length > 0 && (
                <div className="p-2.5 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-800 space-y-1">
                  {errorMessages.map((msg, i) => <div key={i}>{msg}</div>)}
                </div>
              )}

              {/* Normalized Preview Table */}
              <div className="border border-gray-200 rounded-lg overflow-x-auto max-h-60">
                <table className="w-full text-xs text-left border-collapse">
                  <thead className="bg-gray-100 text-gray-700 sticky top-0">
                    <tr>
                      <th className="p-2 border-b">#</th>
                      <th className="p-2 border-b">{copy.colDate}</th>
                      <th className="p-2 border-b">{copy.colDirection}</th>
                      <th className="p-2 border-b">{copy.colAmount}</th>
                      <th className="p-2 border-b">{copy.colDescription}</th>
                      <th className="p-2 border-b">{copy.colReference}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {previewEntries.slice(0, 10).map((row, idx) => (
                      <tr key={idx} className="hover:bg-gray-50 border-b border-gray-100">
                        <td className="p-2 text-gray-400">{row.sourceRowNumber}</td>
                        <td className="p-2 whitespace-nowrap font-mono">{row.postedDate}</td>
                        <td className="p-2">
                          <span
                            className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-bold ${
                              row.direction === 'INCREASE'
                                ? 'bg-emerald-100 text-emerald-800'
                                : 'bg-red-100 text-red-800'
                            }`}
                          >
                            {row.direction === 'INCREASE' ? 'CR' : 'DR'}
                          </span>
                        </td>
                        <td className="p-2 whitespace-nowrap font-mono font-semibold">
                          {row.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                        </td>
                        <td className="p-2 truncate max-w-xs">{row.description}</td>
                        <td className="p-2 text-gray-500 truncate max-w-[120px]">{row.bankReference || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {previewEntries.length > 10 && (
                <p className="text-[11px] text-gray-500 text-center">
                  Showing first 10 of {previewEntries.length} valid entries.
                </p>
              )}
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-4 border-t border-gray-200 flex justify-end gap-3 bg-gray-50">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200 rounded-lg transition-colors"
          >
            {copy.cancelBtn}
          </button>
          <button
            type="button"
            disabled={previewEntries.length === 0 || submitting}
            onClick={handleSaveImport}
            className="px-5 py-2 text-sm font-semibold text-white bg-[var(--color-brand-primary)] hover:bg-teal-800 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg shadow-sm transition-all focus-visible:outline-2 focus-visible:outline-offset-2"
          >
            {submitting ? copy.loadingSave : copy.saveImportBtn}
          </button>
        </div>
      </div>
    </div>
  );
}
