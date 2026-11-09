'use client';

import React, { useState } from 'react';
import { CommercialDocumentLinkResponse } from '@/lib/api/projectActuals';
import { ProjectActualsTranslations } from '@/lib/i18n/translations/projectActuals';

interface Props {
  documents: CommercialDocumentLinkResponse[];
  baseCurrency: string;
  copy: ProjectActualsTranslations;
  onLinkClick: () => void;
  onUnlink: (linkId: string, reason: string) => Promise<void>;
}

export function ProjectCommercialDocumentsCard({
  documents,
  baseCurrency,
  copy,
  onLinkClick,
  onUnlink,
}: Props) {
  const [unlinkingId, setUnlinkingId] = useState<string | null>(null);
  const [unlinkReason, setUnlinkReason] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const formatMoney = (val?: number | null, curr = baseCurrency) => {
    if (val === undefined || val === null) return '—';
    return `${curr} ${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  const handleConfirmUnlink = async () => {
    if (!unlinkingId) return;
    setIsSubmitting(true);
    try {
      await onUnlink(unlinkingId, unlinkReason);
      setUnlinkingId(null);
      setUnlinkReason('');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      data-testid="project-commercial-documents-card"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <div>
          <h2 data-testid="commercial-docs-title" className="text-base font-bold text-foreground">
            {copy.commercialDocuments.title}
          </h2>
          <p className="text-xs text-muted-foreground">{copy.commercialDocuments.subtitle}</p>
        </div>
        <button
          type="button"
          data-testid="link-commercial-doc-btn"
          onClick={onLinkClick}
          className="rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90 transition-colors"
        >
          + {copy.commercialDocuments.linkDocBtn}
        </button>
      </div>

      {/* Non-Posting Banner */}
      <div
        data-testid="non-posting-banner"
        className="rounded-lg bg-amber-500/10 border border-amber-500/20 p-3 text-xs text-amber-800 dark:text-amber-300 flex items-start gap-2"
      >
        <span className="font-bold">⚠️</span>
        <p>{copy.commercialDocuments.nonPostingBanner}</p>
      </div>

      {documents.length === 0 ? (
        <div data-testid="no-documents-msg" className="py-8 text-center text-xs text-muted-foreground">
          {copy.commercialDocuments.noDocuments}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table data-testid="commercial-documents-table" className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-border/60 text-muted-foreground bg-muted/20">
                <th className="py-2.5 px-3 font-semibold">{copy.commercialDocuments.kind}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.commercialDocuments.docNumber}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.commercialDocuments.counterparty}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.commercialDocuments.date}</th>
                <th className="py-2.5 px-3 font-semibold text-right">{copy.commercialDocuments.total}</th>
                <th className="py-2.5 px-3 font-semibold text-right">{copy.commercialDocuments.allocated}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.commercialDocuments.accountingStatus}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.commitments.status}</th>
                <th className="py-2.5 px-3 font-semibold text-center">{copy.commercialDocuments.actions}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {documents.map((item) => {
                const isBill = item.documentKind === 'BILL';
                const isUnlinked = item.linkStatus === 'UNLINKED';

                return (
                  <tr
                    key={item.id}
                    data-testid={`commercial-doc-row-${item.id}`}
                    className={`hover:bg-muted/30 transition-colors ${isUnlinked ? 'opacity-50' : ''}`}
                  >
                    <td className="py-2.5 px-3 font-medium">
                      <span
                        className={`rounded px-1.5 py-0.5 text-[11px] font-bold ${
                          isBill
                            ? 'bg-orange-500/10 text-orange-700 dark:text-orange-400'
                            : 'bg-indigo-500/10 text-indigo-700 dark:text-indigo-400'
                        }`}
                      >
                        {item.documentKind}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 font-mono font-medium text-foreground">
                      {item.documentNumber || '—'}
                    </td>
                    <td className="py-2.5 px-3 text-foreground font-medium">
                      {item.counterpartyName || '—'}
                      {item.notes && <p className="text-[10px] text-muted-foreground">{item.notes}</p>}
                    </td>
                    <td className="py-2.5 px-3 text-muted-foreground">{item.documentDate || '—'}</td>
                    <td className="py-2.5 px-3 text-right font-medium">
                      {formatMoney(item.documentTotal, item.currency)}
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold text-foreground">
                      {formatMoney(item.allocatedAmount, item.currency)}
                    </td>
                    <td className="py-2.5 px-3">
                      {item.isMatchedToFinancialObligation ? (
                        <span
                          data-testid={`matched-badge-${item.id}`}
                          className="rounded-full bg-blue-500/10 px-2 py-0.5 text-[10px] font-semibold text-blue-700 dark:text-blue-400 border border-blue-500/20"
                        >
                          {copy.commercialDocuments.matchedBadge}
                        </span>
                      ) : (
                        <span
                          data-testid={`unposted-badge-${item.id}`}
                          className="rounded-full bg-amber-500/10 px-2 py-0.5 text-[10px] font-semibold text-amber-700 dark:text-amber-400 border border-amber-500/20"
                        >
                          {copy.commercialDocuments.unpostedBadge}
                        </span>
                      )}
                    </td>
                    <td className="py-2.5 px-3">
                      <span
                        data-testid={`doc-link-status-${item.id}`}
                        className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                          item.linkStatus === 'ACTIVE'
                            ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
                            : 'bg-muted text-muted-foreground'
                        }`}
                      >
                        {item.linkStatus === 'ACTIVE' ? 'Active' : copy.commercialDocuments.unlinked}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-center">
                      {item.linkStatus === 'ACTIVE' ? (
                        <button
                          type="button"
                          data-testid={`unlink-doc-btn-${item.id}`}
                          onClick={() => setUnlinkingId(item.id)}
                          className="rounded px-2 py-1 text-[11px] font-medium text-rose-600 hover:bg-rose-500/10 transition-colors"
                        >
                          {copy.commercialDocuments.unlink}
                        </button>
                      ) : (
                        <span className="text-[10px] text-muted-foreground">—</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Unlink Confirmation Dialog */}
      {unlinkingId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4">
          <div
            data-testid="unlink-doc-dialog"
            className="w-full max-w-md rounded-xl border border-border bg-card p-5 text-card-foreground shadow-lg space-y-4"
          >
            <h3 className="text-sm font-bold text-foreground">{copy.commercialDocuments.unlinkConfirmTitle}</h3>
            <p className="text-xs text-muted-foreground">{copy.commercialDocuments.unlinkConfirmDesc}</p>
            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">Reason</label>
              <input
                type="text"
                value={unlinkReason}
                onChange={(e) => setUnlinkReason(e.target.value)}
                placeholder={copy.commercialDocuments.unlinkReasonPlaceholder}
                className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2 border-t border-border/60">
              <button
                type="button"
                onClick={() => {
                  setUnlinkingId(null);
                  setUnlinkReason('');
                }}
                disabled={isSubmitting}
                className="rounded-md border border-input bg-background px-3 py-1.5 text-xs font-semibold text-foreground hover:bg-muted"
              >
                {copy.commercialDocuments.cancel}
              </button>
              <button
                type="button"
                data-testid="confirm-unlink-doc-btn"
                onClick={handleConfirmUnlink}
                disabled={isSubmitting}
                className="rounded-md bg-rose-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-rose-700"
              >
                {isSubmitting ? 'Unlinking...' : copy.commercialDocuments.confirmUnlink}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
