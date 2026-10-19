"use client";

import React from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import type { ObligationDetailResponse } from '@/lib/api/dev-a/obligations';
import { obligationEn, obligationUr } from './obligation-copy';

interface ObligationDetailSheetProps {
  isOpen: boolean;
  onClose: () => void;
  obligation: ObligationDetailResponse | null;
  onRecordSettlement: (ob: ObligationDetailResponse) => void;
  onVoid: (ob: ObligationDetailResponse) => void;
  canEdit: boolean;
}

function formatPkr(val: number | string | undefined): string {
  if (val === undefined || val === null) return '0.00';
  const num = typeof val === 'number' ? val : parseFloat(val);
  if (isNaN(num)) return '0.00';
  return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function ObligationDetailSheet({
  isOpen,
  onClose,
  obligation,
  onRecordSettlement,
  onVoid,
  canEdit,
}: ObligationDetailSheetProps) {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? obligationUr : obligationEn;

  if (!isOpen || !obligation) return null;

  const isReceivable = obligation.direction === 'RECEIVABLE';
  const isOpenStatus = obligation.status === 'OPEN';
  const hasOutstanding = parseFloat(obligation.outstandingAmount) > 0;
  const canBeSettled = isOpenStatus && hasOutstanding && canEdit;
  const canBeVoided = isOpenStatus && parseFloat(obligation.paidAmount) === 0 && canEdit;

  return (
    <Sheet
      isOpen={isOpen}
      onClose={onClose}
      title={obligation.referenceNumber || copy.detailTitle}
      description={`${obligation.counterpartyName} (${isReceivable ? copy.customerLabel : copy.supplierLabel})`}
    >
      <div className="space-y-5 text-xs text-slate-700 dark:text-slate-300 max-h-[75vh] overflow-y-auto pr-1" dir={textDir}>
        {/* Status badges header */}
        <div className="flex flex-wrap items-center gap-2 pb-3 border-b border-slate-200 dark:border-slate-800">
          <Badge variant={isReceivable ? 'info' : 'warning'}>
            {isReceivable ? copy.directionReceivable : copy.directionPayable}
          </Badge>

          <Badge variant={isOpenStatus ? 'success' : 'neutral'}>
            {isOpenStatus ? copy.statusOpen : copy.statusVoided}
          </Badge>

          <Badge
            variant={
              obligation.settlementStatus === 'FULLY_PAID'
                ? 'success'
                : obligation.settlementStatus === 'PARTIALLY_PAID'
                ? 'warning'
                : 'neutral'
            }
          >
            {obligation.settlementStatus === 'FULLY_PAID'
              ? copy.settlementFullyPaid
              : obligation.settlementStatus === 'PARTIALLY_PAID'
              ? copy.settlementPartiallyPaid
              : copy.settlementUnpaid}
          </Badge>

          <Badge variant="neutral">
            {obligation.agingBucket.replace(/_/g, ' ')}
          </Badge>
        </div>

        {/* Financial Overview Cards */}
        <div className="grid grid-cols-3 gap-2 text-center p-3 rounded-xl bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700">
          <div>
            <div className="text-[10px] text-slate-500 uppercase">{copy.colOriginal}</div>
            <div className="text-sm font-bold text-slate-800 dark:text-slate-100 mt-1">
              PKR {formatPkr(obligation.originalAmount)}
            </div>
          </div>
          <div>
            <div className="text-[10px] text-slate-500 uppercase">{copy.colPaid}</div>
            <div className="text-sm font-bold text-slate-800 dark:text-slate-100 mt-1">
              PKR {formatPkr(obligation.paidAmount)}
            </div>
          </div>
          <div>
            <div className="text-[10px] text-teal-600 dark:text-teal-400 uppercase font-semibold">
              {copy.colOutstanding}
            </div>
            <div className="text-sm font-extrabold text-teal-700 dark:text-teal-300 mt-1">
              PKR {formatPkr(obligation.outstandingAmount)}
            </div>
          </div>
        </div>

        {/* Obligation Metadata */}
        <div className="space-y-2 rounded-lg border border-slate-200 dark:border-slate-800 p-3 bg-white dark:bg-slate-900/50">
          <div className="flex justify-between py-1 border-b border-slate-100 dark:border-slate-800/80">
            <span className="text-slate-500">{copy.colParty}</span>
            <span className="font-semibold text-slate-800 dark:text-slate-200">{obligation.counterpartyName}</span>
          </div>
          <div className="flex justify-between py-1 border-b border-slate-100 dark:border-slate-800/80">
            <span className="text-slate-500">{copy.colIssueDate}</span>
            <span className="font-mono text-slate-800 dark:text-slate-200">{obligation.issueDate}</span>
          </div>
          <div className="flex justify-between py-1 border-b border-slate-100 dark:border-slate-800/80">
            <span className="text-slate-500">{copy.colDueDate}</span>
            <span className="font-mono text-slate-800 dark:text-slate-200">{obligation.dueDate || copy.bucketUnknownDueDate}</span>
          </div>
          <div className="flex justify-between py-1 border-b border-slate-100 dark:border-slate-800/80">
            <span className="text-slate-500">Source Type</span>
            <span className="font-mono text-[11px] text-slate-700 dark:text-slate-300">{obligation.sourceType}</span>
          </div>
          {obligation.description && (
            <div className="pt-1">
              <span className="text-slate-500 block mb-1">{copy.descriptionLabel}:</span>
              <p className="text-slate-700 dark:text-slate-300 italic">{obligation.description}</p>
            </div>
          )}
          {obligation.status === 'VOIDED' && (
            <div className="mt-2 pt-2 border-t border-red-200 dark:border-red-900 text-red-700 dark:text-red-300">
              <div className="font-semibold">Voided on: {obligation.voidDate}</div>
              <div className="text-[11px]">Reason: {obligation.voidReason}</div>
            </div>
          )}
        </div>

        {/* Payment History / Allocations */}
        <div>
          <h4 className="text-xs font-semibold text-slate-800 dark:text-slate-200 mb-2">
            {copy.settlementsSection}
          </h4>
          {obligation.allocations && obligation.allocations.length > 0 ? (
            <div className="space-y-2">
              {obligation.allocations.map(alloc => {
                const isCancelled = alloc.status === 'CANCELLED';
                return (
                  <div
                    key={alloc.id}
                    className={`rounded-lg border p-2.5 flex items-center justify-between gap-3 ${
                      isCancelled
                        ? 'border-red-200 dark:border-red-900 bg-red-50/50 dark:bg-red-950/20 opacity-75'
                        : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900'
                    }`}
                  >
                    <div>
                      <div className="flex items-center gap-1.5">
                        <span className="font-mono text-xs font-semibold text-slate-800 dark:text-slate-200">
                          PKR {formatPkr(alloc.amount)}
                        </span>
                        <Badge variant={isCancelled ? 'error' : 'success'} size="sm">
                          {isCancelled ? copy.reversalBadge : copy.effectiveBadge}
                        </Badge>
                      </div>
                      <div className="text-[10px] text-slate-400 font-mono mt-0.5">
                        Date: {alloc.effectiveDate} • Tx: {(alloc.transactionId || alloc.financialTransactionId || '').substring(0, 8)}…
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="p-4 text-center rounded-lg border border-dashed border-slate-200 dark:border-slate-800 text-slate-400 text-xs">
              {copy.noAllocations}
            </div>
          )}
        </div>

        {/* Actions Footer */}
        <div className="pt-3 border-t border-slate-200 dark:border-slate-800 flex flex-wrap justify-end gap-2">
          {canBeVoided && (
            <Button
              type="button"
              variant="secondary"
              onClick={() => onVoid(obligation)}
              className="text-red-600 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-950/30"
            >
              {copy.voidObligation}
            </Button>
          )}

          {canBeSettled && (
            <Button
              type="button"
              variant="primary"
              onClick={() => onRecordSettlement(obligation)}
            >
              {isReceivable ? copy.recordPayment : copy.paySupplier}
            </Button>
          )}

          <Button type="button" variant="ghost" onClick={onClose}>
            {copy.close}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}
