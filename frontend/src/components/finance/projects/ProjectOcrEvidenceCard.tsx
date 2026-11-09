'use client';

import React, { useState } from 'react';
import { ProjectOcrEvidenceItem, ProjectOcrEvidenceStatus } from '@/lib/api/projectActuals';
import { ProjectActualsTranslations } from '@/lib/i18n/translations/projectActuals';

interface Props {
  evidenceList: ProjectOcrEvidenceItem[];
  baseCurrency: string;
  copy: ProjectActualsTranslations;
  onLinkClick: () => void;
  onConfirmMatchClick: (item: ProjectOcrEvidenceItem) => void;
  onInvalidate: (linkId: string, reason: string) => Promise<void>;
}

export function ProjectOcrEvidenceCard({
  evidenceList,
  baseCurrency,
  copy,
  onLinkClick,
  onConfirmMatchClick,
  onInvalidate,
}: Props) {
  const [invalidatingId, setInvalidatingId] = useState<string | null>(null);
  const [invalidateReason, setInvalidateReason] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const formatMoney = (val?: number | null, curr = baseCurrency) => {
    if (val === undefined || val === null) return '—';
    return `${curr} ${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  const handleConfirmInvalidate = async () => {
    if (!invalidatingId) return;
    setIsSubmitting(true);
    try {
      await onInvalidate(invalidatingId, invalidateReason || 'Invalidated by user');
      setInvalidatingId(null);
      setInvalidateReason('');
    } finally {
      setIsSubmitting(false);
    }
  };

  const renderStatusBadge = (status: ProjectOcrEvidenceStatus, id: string) => {
    switch (status) {
      case 'ATTRIBUTED':
        return (
          <span
            data-testid={`ocr-status-${id}`}
            className="inline-flex items-center rounded-full bg-emerald-500/10 px-2 py-0.5 text-[11px] font-semibold text-emerald-700 dark:text-emerald-400 border border-emerald-500/20"
          >
            ATTRIBUTED
          </span>
        );
      case 'MATCHED_TO_POSTED_SOURCE':
        return (
          <span
            data-testid={`ocr-status-${id}`}
            className="inline-flex items-center rounded-full bg-blue-500/10 px-2 py-0.5 text-[11px] font-semibold text-blue-700 dark:text-blue-400 border border-blue-500/20"
          >
            MATCHED TO SOURCE
          </span>
        );
      case 'REVIEWED_UNMATCHED':
        return (
          <span
            data-testid={`ocr-status-${id}`}
            className="inline-flex items-center rounded-full bg-amber-500/10 px-2 py-0.5 text-[11px] font-semibold text-amber-700 dark:text-amber-400 border border-amber-500/20"
          >
            REVIEWED UNMATCHED
          </span>
        );
      case 'EXTRACTED_UNREVIEWED':
        return (
          <span
            data-testid={`ocr-status-${id}`}
            className="inline-flex items-center rounded-full bg-zinc-500/10 px-2 py-0.5 text-[11px] font-semibold text-zinc-700 dark:text-zinc-400 border border-zinc-500/20"
          >
            EXTRACTED UNREVIEWED
          </span>
        );
      case 'REVERSED':
        return (
          <span
            data-testid={`ocr-status-${id}`}
            className="inline-flex items-center rounded-full bg-rose-500/10 px-2 py-0.5 text-[11px] font-semibold text-rose-700 dark:text-rose-400 border border-rose-500/20"
          >
            REVERSED
          </span>
        );
      case 'INVALIDATED':
      default:
        return (
          <span
            data-testid={`ocr-status-${id}`}
            className="inline-flex items-center rounded-full bg-zinc-500/10 px-2 py-0.5 text-[11px] font-medium text-zinc-500 line-through"
          >
            INVALIDATED
          </span>
        );
    }
  };

  return (
    <div
      data-testid="project-ocr-evidence-card"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <div>
          <h2 data-testid="ocr-evidence-title" className="text-base font-bold text-foreground">
            {copy.ocrEvidence.title}
          </h2>
          <p className="text-xs text-muted-foreground">{copy.ocrEvidence.subtitle}</p>
        </div>
        <button
          type="button"
          data-testid="link-ocr-btn"
          onClick={onLinkClick}
          className="rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90 transition-colors"
        >
          + {copy.ocrEvidence.linkOcrBtn}
        </button>
      </div>

      {/* Central Financial Rule Banner */}
      <div
        data-testid="ocr-rule-banner"
        className="rounded-lg bg-blue-500/10 border border-blue-500/20 p-3 text-xs text-blue-900 dark:text-blue-300 flex items-start gap-2"
      >
        <span className="font-bold">ℹ️</span>
        <p className="font-medium">{copy.ocrEvidence.evidenceRuleBanner}</p>
      </div>

      {evidenceList.length === 0 ? (
        <div data-testid="no-ocr-evidence-msg" className="py-8 text-center text-xs text-muted-foreground">
          {copy.ocrEvidence.noEvidence}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table data-testid="ocr-evidence-table" className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-border/60 text-muted-foreground">
                <th className="py-2.5 px-3 font-semibold">{copy.ocrEvidence.filename}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.ocrEvidence.counterparty}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.ocrEvidence.date}</th>
                <th className="py-2.5 px-3 font-semibold text-right">{copy.ocrEvidence.ocrAmount}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.ocrEvidence.status}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.ocrEvidence.matchedSource}</th>
                <th className="py-2.5 px-3 font-semibold text-center">{copy.ocrEvidence.actions}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/30">
              {evidenceList.map((item) => (
                <tr
                  key={item.id}
                  data-testid={`ocr-evidence-row-${item.id}`}
                  className="hover:bg-muted/30 transition-colors"
                >
                  <td className="py-2.5 px-3">
                    <div className="flex flex-col gap-1">
                      <span data-testid={`ocr-doc-${item.id}`} className="font-medium text-foreground">
                        {item.originalFilename || `Doc ${item.documentId.slice(0, 8)}...`}
                      </span>
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground uppercase font-mono">
                          {item.documentType || 'Receipt'}
                        </span>
                        {item.isDuplicate && (
                          <span
                            data-testid={`split-badge-${item.id}`}
                            className="rounded bg-indigo-500/10 border border-indigo-500/20 px-1.5 py-0.5 text-[10px] font-semibold text-indigo-700 dark:text-indigo-400"
                          >
                            🔗 {copy.ocrEvidence.duplicateBadge} ({item.duplicateCount})
                          </span>
                        )}
                      </div>
                    </div>
                  </td>
                  <td className="py-2.5 px-3 text-muted-foreground" data-testid={`ocr-vendor-${item.id}`}>
                    {item.counterpartyName || '—'}
                  </td>
                  <td className="py-2.5 px-3 text-muted-foreground whitespace-nowrap" data-testid={`ocr-date-${item.id}`}>
                    {item.ocrDate || '—'}
                  </td>
                  <td className="py-2.5 px-3 text-right font-medium">
                    <div className="flex flex-col items-end">
                      <span data-testid={`ocr-amount-${item.id}`}>
                        {formatMoney(item.ocrAmount, item.ocrCurrency)}
                      </span>
                      {item.hasDiscrepancy && (
                        <span
                          data-testid={`discrepancy-badge-${item.id}`}
                          title={item.discrepancyNotes || copy.ocrEvidence.discrepancyTooltip}
                          className="mt-0.5 rounded bg-rose-500/10 border border-rose-500/20 px-1 py-0.5 text-[10px] font-semibold text-rose-700 dark:text-rose-400"
                        >
                          ⚠️ Discrepancy: {formatMoney(item.discrepancyAmount, item.ocrCurrency)}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="py-2.5 px-3 whitespace-nowrap">
                    {renderStatusBadge(item.evidenceStatus, item.id)}
                  </td>
                  <td className="py-2.5 px-3 text-muted-foreground">
                    {item.financialSourceId ? (
                      <div className="flex flex-col text-[11px]">
                        <span className="font-mono text-foreground">
                          {item.financialSourceType || 'TRANSACTION'}: {item.financialSourceId.slice(0, 8)}...
                        </span>
                        {item.matchedTransactionAmount !== undefined && (
                          <span className="text-[10px] text-muted-foreground">
                            Posted: {formatMoney(item.matchedTransactionAmount, item.ocrCurrency)}
                          </span>
                        )}
                      </div>
                    ) : (
                      <span className="text-zinc-400 italic">Unmatched</span>
                    )}
                  </td>
                  <td className="py-2.5 px-3 text-center whitespace-nowrap">
                    <div className="flex items-center justify-center gap-1.5">
                      {(item.evidenceStatus === 'REVIEWED_UNMATCHED' || item.evidenceStatus === 'EXTRACTED_UNREVIEWED') && (
                        <button
                          type="button"
                          data-testid={`confirm-match-btn-${item.id}`}
                          onClick={() => onConfirmMatchClick(item)}
                          className="rounded px-2 py-1 text-[11px] font-semibold text-primary hover:bg-primary/10 transition-colors"
                        >
                          {copy.ocrEvidence.confirmMatch}
                        </button>
                      )}
                      {item.evidenceStatus !== 'INVALIDATED' && item.evidenceStatus !== 'REVERSED' && (
                        <button
                          type="button"
                          data-testid={`invalidate-ocr-btn-${item.id}`}
                          onClick={() => setInvalidatingId(item.id)}
                          className="rounded px-2 py-1 text-[11px] font-semibold text-destructive hover:bg-destructive/10 transition-colors"
                        >
                          {copy.ocrEvidence.invalidate}
                        </button>
                      )}
                      {item.evidenceStatus === 'INVALIDATED' && (
                        <span className="text-[11px] text-muted-foreground italic">
                          {copy.ocrEvidence.invalidated}
                        </span>
                      )}
                      {item.evidenceStatus === 'REVERSED' && (
                        <span className="text-[11px] text-rose-500 italic">
                          {copy.ocrEvidence.reversed}
                        </span>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Invalidate Confirmation Modal */}
      {invalidatingId && (
        <div
          data-testid="invalidate-confirm-dialog"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
        >
          <div className="w-full max-w-sm rounded-xl border border-border bg-card p-5 text-card-foreground shadow-lg space-y-4">
            <div>
              <h3 className="text-sm font-bold text-foreground">
                {copy.ocrEvidence.invalidateConfirmTitle}
              </h3>
              <p className="mt-1 text-xs text-muted-foreground">
                {copy.ocrEvidence.invalidateConfirmDesc}
              </p>
            </div>
            <div>
              <input
                type="text"
                data-testid="invalidate-reason-input"
                placeholder={copy.ocrEvidence.invalidateReasonPlaceholder}
                value={invalidateReason}
                onChange={(e) => setInvalidateReason(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                data-testid="cancel-invalidate-btn"
                disabled={isSubmitting}
                onClick={() => {
                  setInvalidatingId(null);
                  setInvalidateReason('');
                }}
                className="rounded-md border border-input bg-background px-3 py-1.5 text-xs font-semibold hover:bg-muted transition-colors"
              >
                {copy.ocrEvidence.cancel}
              </button>
              <button
                type="button"
                data-testid="confirm-invalidate-btn"
                disabled={isSubmitting}
                onClick={handleConfirmInvalidate}
                className="rounded-md bg-destructive px-3 py-1.5 text-xs font-semibold text-destructive-foreground hover:bg-destructive/90 transition-colors"
              >
                {isSubmitting ? '...' : copy.ocrEvidence.confirmInvalidate}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
