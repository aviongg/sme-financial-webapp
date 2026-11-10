'use client';

import React from 'react';
import Link from 'next/link';
import { CommercialEligibleDocument } from '@/lib/api/commercialRecognition';
import { getCommercialRecognitionCopy } from '@/lib/i18n/translations/commercialRecognition';

interface Props {
  documents: CommercialEligibleDocument[];
  loading: boolean;
  onRecognizeClick: (doc: CommercialEligibleDocument) => void;
  locale: string;
}

export function EligibleCommercialDocumentsTable({
  documents,
  loading,
  onRecognizeClick,
  locale,
}: Props) {
  const copy = getCommercialRecognitionCopy(locale);

  const formatMoney = (val?: string | number | null, curr = 'PKR') => {
    if (val === undefined || val === null) return '—';
    return `${curr} ${Number(val).toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  if (loading) {
    return (
      <div
        data-testid="eligible-documents-loading"
        className="rounded-xl border border-border bg-card p-8 text-center text-xs text-muted-foreground"
      >
        <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-primary border-t-transparent mr-2" />
        {copy.table.loading}
      </div>
    );
  }

  if (documents.length === 0) {
    return (
      <div
        data-testid="eligible-documents-empty"
        className="rounded-xl border border-dashed border-border bg-card/50 p-8 text-center text-xs text-muted-foreground"
      >
        {copy.table.noDocuments}
      </div>
    );
  }

  return (
    <div
      data-testid="eligible-documents-table"
      className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm"
      dir={locale === 'ur' ? 'rtl' : 'ltr'}
    >
      <table className="w-full text-left text-xs border-collapse">
        <thead className="border-b border-border bg-muted/50 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
          <tr>
            <th className="px-4 py-3">{copy.table.documentNumber}</th>
            <th className="px-4 py-3">{copy.table.counterparty}</th>
            <th className="px-4 py-3">{copy.table.documentDate}</th>
            <th className="px-4 py-3">{copy.table.dueDate}</th>
            <th className="px-4 py-3 text-right">{copy.table.totalAmount}</th>
            <th className="px-4 py-3 text-center">{copy.table.status}</th>
            <th className="px-4 py-3 text-right">{copy.table.outstanding}</th>
            <th className="px-4 py-3 text-right">{copy.table.actions}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border/60">
          {documents.map((doc) => {
            const isInvoice = doc.kind === 'INVOICE';
            const detailRoute = isInvoice ? '/records/receivables' : '/records/payables';

            return (
              <tr
                key={doc.documentId}
                data-testid={`document-row-${doc.documentId}`}
                className="hover:bg-muted/30 transition-colors"
              >
                <td className="px-4 py-3 font-mono font-medium text-foreground whitespace-nowrap">
                  {doc.documentNumber || doc.documentId.slice(0, 8)}
                </td>
                <td className="px-4 py-3 text-foreground font-medium">
                  {doc.counterpartyName || '—'}
                </td>
                <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                  {doc.documentDate || '—'}
                </td>
                <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                  {doc.dueDate || '—'}
                </td>
                <td className="px-4 py-3 text-right font-semibold text-foreground whitespace-nowrap">
                  {formatMoney(doc.total, doc.currency)}
                </td>
                <td className="px-4 py-3 text-center whitespace-nowrap">
                  {doc.isRecognized ? (
                    <span
                      data-testid={`doc-status-${doc.documentId}`}
                      className="inline-flex items-center gap-1 rounded-full bg-emerald-500/15 px-2.5 py-0.5 text-[11px] font-medium text-emerald-700 dark:text-emerald-400"
                    >
                      <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                      {copy.badges.recognized}
                    </span>
                  ) : (
                    <span
                      data-testid={`doc-status-${doc.documentId}`}
                      className="inline-flex items-center gap-1 rounded-full bg-amber-500/15 px-2.5 py-0.5 text-[11px] font-medium text-amber-700 dark:text-amber-400"
                    >
                      <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
                      {copy.badges.unrecognized}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  {doc.isRecognized ? (
                    <span className="font-semibold text-foreground">
                      {formatMoney(doc.outstandingAmount, doc.currency)}
                    </span>
                  ) : (
                    <span className="text-muted-foreground">—</span>
                  )}
                </td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  {doc.isRecognized ? (
                    <Link
                      href={detailRoute}
                      data-testid={`view-obligation-link-${doc.documentId}`}
                      className="inline-flex items-center text-xs font-semibold text-primary hover:underline"
                    >
                      {isInvoice ? copy.actions.viewInReceivables : copy.actions.viewInPayables} &rarr;
                    </Link>
                  ) : (
                    <button
                      type="button"
                      data-testid={`recognize-btn-${doc.documentId}`}
                      onClick={() => onRecognizeClick(doc)}
                      className="rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90 transition-colors shadow-sm"
                    >
                      {copy.actions.recognize}
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

