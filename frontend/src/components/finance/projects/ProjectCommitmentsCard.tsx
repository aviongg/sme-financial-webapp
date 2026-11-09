'use client';

import React, { useState } from 'react';
import { ProjectObligationItemResponse } from '@/lib/api/projectActuals';
import { ProjectActualsTranslations } from '@/lib/i18n/translations/projectActuals';

interface Props {
  obligations: ProjectObligationItemResponse[];
  baseCurrency: string;
  copy: ProjectActualsTranslations;
  onLinkClick: () => void;
  onUnlink: (linkId: string, reason: string) => Promise<void>;
}

export function ProjectCommitmentsCard({
  obligations,
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
      data-testid="project-commitments-card"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <div>
          <h2 data-testid="commitments-title" className="text-base font-bold text-foreground">
            {copy.commitments.title}
          </h2>
          <p className="text-xs text-muted-foreground">{copy.commitments.subtitle}</p>
        </div>
        <button
          type="button"
          data-testid="link-obligation-btn"
          onClick={onLinkClick}
          className="rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90 transition-colors"
        >
          + {copy.commitments.linkObligationBtn}
        </button>
      </div>

      {obligations.length === 0 ? (
        <div data-testid="no-obligations-msg" className="py-8 text-center text-xs text-muted-foreground">
          {copy.commitments.noObligations}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table data-testid="obligations-table" className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-border/60 text-muted-foreground bg-muted/20">
                <th className="py-2.5 px-3 font-semibold">{copy.commitments.direction}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.commitments.counterparty}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.commitments.reference}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.commitments.issueDate}</th>
                <th className="py-2.5 px-3 font-semibold text-right">{copy.commitments.originalAmount}</th>
                <th className="py-2.5 px-3 font-semibold text-right">{copy.commitments.allocatedAmount}</th>
                <th className="py-2.5 px-3 font-semibold text-right">{copy.commitments.settledAmount}</th>
                <th className="py-2.5 px-3 font-semibold text-right">{copy.commitments.outstandingAmount}</th>
                <th className="py-2.5 px-3 font-semibold">{copy.commitments.status}</th>
                <th className="py-2.5 px-3 font-semibold text-center">{copy.commitments.actions}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {obligations.map((item) => {
                const isPayable = item.direction === 'PAYABLE';
                const isUnlinked = item.linkStatus === 'UNLINKED';

                return (
                  <tr
                    key={item.linkId}
                    data-testid={`obligation-row-${item.linkId}`}
                    className={`hover:bg-muted/30 transition-colors ${isUnlinked ? 'opacity-50' : ''}`}
                  >
                    <td className="py-2.5 px-3 font-medium">
                      <span
                        className={`rounded px-1.5 py-0.5 text-[11px] font-bold ${
                          isPayable
                            ? 'bg-rose-500/10 text-rose-700 dark:text-rose-400'
                            : 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
                        }`}
                      >
                        {item.direction}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 font-medium text-foreground">
                      {item.counterpartyName}
                      {item.notes && <p className="text-[10px] text-muted-foreground">{item.notes}</p>}
                    </td>
                    <td className="py-2.5 px-3 font-mono text-muted-foreground">
                      {item.referenceNumber || '—'}
                    </td>
                    <td className="py-2.5 px-3 text-muted-foreground">{item.issueDate || '—'}</td>
                    <td className="py-2.5 px-3 text-right font-medium">
                      {formatMoney(item.originalAmount, item.currency)}
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold text-foreground">
                      {formatMoney(item.allocatedAmount, item.currency)}
                    </td>
                    <td className="py-2.5 px-3 text-right font-medium text-muted-foreground">
                      <div>{formatMoney(item.paidOrCollectedAmount, item.currency)}</div>
                      {item.hasAttributedPayment && (
                        <span
                          data-testid={`attributed-payment-badge-${item.linkId}`}
                          className="inline-block mt-0.5 rounded bg-blue-500/10 px-1 py-0.2 text-[9px] font-semibold text-blue-700 dark:text-blue-400"
                        >
                          C05A {formatMoney(item.attributedCashAmount, item.currency)}
                        </span>
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold text-foreground">
                      <span data-testid={`outstanding-val-${item.linkId}`}>
                        {formatMoney(item.outstandingAmount, item.currency)}
                      </span>
                    </td>
                    <td className="py-2.5 px-3">
                      <span
                        data-testid={`link-status-badge-${item.linkId}`}
                        className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                          item.linkStatus === 'ACTIVE'
                            ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
                            : 'bg-muted text-muted-foreground'
                        }`}
                      >
                        {item.linkStatus === 'ACTIVE' ? 'Active' : copy.commitments.unlinked}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-center">
                      {item.linkStatus === 'ACTIVE' ? (
                        <button
                          type="button"
                          data-testid={`unlink-obligation-btn-${item.linkId}`}
                          onClick={() => setUnlinkingId(item.linkId)}
                          className="rounded px-2 py-1 text-[11px] font-medium text-rose-600 hover:bg-rose-500/10 transition-colors"
                        >
                          {copy.commitments.unlink}
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
            data-testid="unlink-dialog"
            className="w-full max-w-md rounded-xl border border-border bg-card p-5 text-card-foreground shadow-lg space-y-4"
          >
            <h3 className="text-sm font-bold text-foreground">{copy.commitments.unlinkConfirmTitle}</h3>
            <p className="text-xs text-muted-foreground">{copy.commitments.unlinkConfirmDesc}</p>
            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">Reason</label>
              <input
                type="text"
                value={unlinkReason}
                onChange={(e) => setUnlinkReason(e.target.value)}
                placeholder={copy.commitments.unlinkReasonPlaceholder}
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
                {copy.commitments.cancel}
              </button>
              <button
                type="button"
                data-testid="confirm-unlink-btn"
                onClick={handleConfirmUnlink}
                disabled={isSubmitting}
                className="rounded-md bg-rose-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-rose-700"
              >
                {isSubmitting ? 'Unlinking...' : copy.commitments.confirmUnlink}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
