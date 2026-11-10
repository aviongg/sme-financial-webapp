'use client';

import React from 'react';
import Link from 'next/link';
import { CommercialEligibleDocument } from '@/lib/api/commercialRecognition';
import { getCommercialRecognitionCopy } from '@/lib/i18n/translations/commercialRecognition';

interface Props {
  recognizedDocs: CommercialEligibleDocument[];
  locale: string;
}

export function RecognizedObligationsTable({ recognizedDocs, locale }: Props) {
  const copy = getCommercialRecognitionCopy(locale);

  const formatMoney = (val?: string | number | null, curr = 'PKR') => {
    if (val === undefined || val === null) return '—';
    return `${curr} ${Number(val).toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  const formatDate = (dateStr?: string | null) => {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleDateString(locale === 'ur' ? 'ur-PK' : 'en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    } catch {
      return dateStr;
    }
  };

  if (recognizedDocs.length === 0) {
    return (
      <div
        data-testid="recognized-obligations-empty"
        className="rounded-xl border border-dashed border-border bg-card/50 p-8 text-center text-xs text-muted-foreground"
      >
        {copy.table.noDocuments}
      </div>
    );
  }

  return (
    <div
      data-testid="recognized-obligations-table"
      className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm"
      dir={locale === 'ur' ? 'rtl' : 'ltr'}
    >
      <table className="w-full text-left text-xs border-collapse">
        <thead className="border-b border-border bg-muted/50 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
          <tr>
            <th className="px-4 py-3">{copy.history.obligationNumber}</th>
            <th className="px-4 py-3">{copy.table.documentNumber}</th>
            <th className="px-4 py-3">{copy.history.direction}</th>
            <th className="px-4 py-3">{copy.table.counterparty}</th>
            <th className="px-4 py-3 text-right">{copy.history.originalAmount}</th>
            <th className="px-4 py-3 text-right">{copy.history.allocatedAmount}</th>
            <th className="px-4 py-3 text-right">{copy.history.outstandingAmount}</th>
            <th className="px-4 py-3 text-center">{copy.table.status}</th>
            <th className="px-4 py-3">{copy.history.recognizedAt}</th>
            <th className="px-4 py-3 text-right">{copy.table.actions}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border/60">
          {recognizedDocs.map((doc) => {
            const isInvoice = doc.kind === 'INVOICE';
            const detailRoute = isInvoice ? '/records/receivables' : '/records/payables';
            const isSettled =
              doc.outstandingAmount !== null && Number(doc.outstandingAmount) <= 0;

            return (
              <tr
                key={doc.documentId}
                data-testid={`recognized-row-${doc.obligationId || doc.documentId}`}
                className="hover:bg-muted/30 transition-colors"
              >
                <td className="px-4 py-3 font-mono font-bold text-foreground whitespace-nowrap">
                  {doc.obligationNumber || '—'}
                </td>
                <td className="px-4 py-3 font-mono text-muted-foreground whitespace-nowrap">
                  {doc.documentNumber || doc.documentId.slice(0, 8)}
                </td>
                <td className="px-4 py-3 whitespace-nowrap">
                  <span
                    className={`inline-block rounded px-2 py-0.5 text-[10px] font-semibold ${
                      isInvoice
                        ? 'bg-blue-500/15 text-blue-700 dark:text-blue-400'
                        : 'bg-purple-500/15 text-purple-700 dark:text-purple-400'
                    }`}
                  >
                    {isInvoice ? 'Receivable (AR)' : 'Payable (AP)'}
                  </span>
                </td>
                <td className="px-4 py-3 text-foreground font-medium">
                  {doc.counterpartyName || '—'}
                </td>
                <td className="px-4 py-3 text-right font-semibold text-foreground whitespace-nowrap">
                  {formatMoney(doc.originalAmount, doc.currency)}
                </td>
                <td className="px-4 py-3 text-right text-emerald-600 dark:text-emerald-400 font-medium whitespace-nowrap">
                  {formatMoney(doc.allocatedAmount, doc.currency)}
                </td>
                <td className="px-4 py-3 text-right font-bold text-foreground whitespace-nowrap">
                  {formatMoney(doc.outstandingAmount, doc.currency)}
                </td>
                <td className="px-4 py-3 text-center whitespace-nowrap">
                  {isSettled ? (
                    <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/15 px-2.5 py-0.5 text-[11px] font-medium text-emerald-700 dark:text-emerald-400">
                      {copy.badges.settled}
                    </span>
                  ) : Number(doc.allocatedAmount || 0) > 0 ? (
                    <span className="inline-flex items-center gap-1 rounded-full bg-blue-500/15 px-2.5 py-0.5 text-[11px] font-medium text-blue-700 dark:text-blue-400">
                      {copy.badges.partiallyPaid}
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/15 px-2.5 py-0.5 text-[11px] font-medium text-amber-700 dark:text-amber-400">
                      {copy.badges.open}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                  {formatDate(doc.recognitionTimestamp)}
                </td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <Link
                    href={detailRoute}
                    className="inline-flex items-center text-xs font-semibold text-primary hover:underline"
                  >
                    {isInvoice ? copy.actions.viewInReceivables : copy.actions.viewInPayables} &rarr;
                  </Link>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

