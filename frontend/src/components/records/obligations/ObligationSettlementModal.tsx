"use client";

import React, { useState, useEffect } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { Dialog } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { accountsApi } from '@/lib/api/dev-a/accounts';
import type { Account } from '@/lib/api/dev-a/account-contracts';
import {
  obligationsApi,
  type ObligationDetailResponse,
  type ObligationSettlementRequest,
} from '@/lib/api/dev-a/obligations';
import { obligationEn, obligationUr } from './obligation-copy';

interface ObligationSettlementModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  obligation: ObligationDetailResponse | null;
}

function formatPkr(val: number | string | null | undefined): string {
  if (val === undefined || val === null) return '0.00';
  const num = typeof val === 'number' ? val : parseFloat(val);
  if (isNaN(num)) return '0.00';
  return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function ObligationSettlementModal({
  isOpen,
  onClose,
  onSuccess,
  obligation,
}: ObligationSettlementModalProps) {
  const { locale } = useLanguage();
  const copy = locale === 'ur' ? obligationUr : obligationEn;

  if (!obligation) return null;

  const isReceivable = obligation.direction === 'RECEIVABLE';

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title={isReceivable ? copy.settlementModalReceivableTitle : copy.settlementModalPayableTitle}
    >
      {isOpen && (
        <ObligationSettlementForm
          key={obligation.id}
          obligation={obligation}
          onClose={onClose}
          onSuccess={onSuccess}
        />
      )}
    </Dialog>
  );
}

interface ObligationSettlementFormProps {
  obligation: ObligationDetailResponse;
  onClose: () => void;
  onSuccess: () => void;
}

function ObligationSettlementForm({
  obligation,
  onClose,
  onSuccess,
}: ObligationSettlementFormProps) {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? obligationUr : obligationEn;

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loadingAccounts, setLoadingAccounts] = useState(true);

  const [accountId, setAccountId] = useState('');
  const [amount, setAmount] = useState(obligation.outstandingAmount);
  const [settlementDate, setSettlementDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [reference, setReference] = useState('');
  const [description, setDescription] = useState('');

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    accountsApi
      .list({ status: 'ACTIVE', currency: 'PKR', size: 100 })
      .then(res => {
        if (active) {
          setAccounts(res.items);
          if (res.items.length > 0) {
            setAccountId(res.items[0].id);
          }
          setLoadingAccounts(false);
        }
      })
      .catch(() => {
        if (active) {
          setError(copy.loadFailed);
          setLoadingAccounts(false);
        }
      });

    return () => {
      active = false;
    };
  }, [copy.loadFailed]);

  const maxOutstanding = parseFloat(obligation.outstandingAmount);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!accountId) {
      setError(copy.accountRequired);
      return;
    }

    const trimmedAmount = amount.trim();
    const parsedAmount = parseFloat(trimmedAmount);
    if (!trimmedAmount || isNaN(parsedAmount) || parsedAmount <= 0) {
      setError(copy.amountRequired);
      return;
    }

    if (parsedAmount > maxOutstanding) {
      setError(copy.amountExceedsOutstanding);
      return;
    }

    if (!settlementDate) {
      setError(copy.issueDateRequired);
      return;
    }

    if (settlementDate < obligation.issueDate) {
      setError(copy.settlementDatePrecedesIssue);
      return;
    }

    setSubmitting(true);
    try {
      const payload: ObligationSettlementRequest = {
        accountId,
        amount: parsedAmount.toFixed(2),
        settlementDate,
        reference: reference.trim() || null,
        description: description.trim() || null,
      };

      // Generate client UUID for idempotency key
      const idempotencyKey = typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : 'settle-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9);

      await obligationsApi.recordSettlement(obligation.id, payload, idempotencyKey);
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : copy.loadFailed;
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4" dir={textDir}>
      {/* Obligation context summary banner */}
      <div className="rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700 p-3.5 text-xs">
        <div className="flex justify-between items-center mb-1.5">
          <span className="font-semibold text-slate-800 dark:text-slate-100 text-sm">
            {obligation.counterpartyName}
          </span>
          <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-200/60 dark:bg-slate-700 text-slate-700 dark:text-slate-300">
            {obligation.referenceNumber || 'NO REF'}
          </span>
        </div>
        <div className="grid grid-cols-3 gap-2 mt-2 pt-2 border-t border-slate-200/60 dark:border-slate-700 text-slate-600 dark:text-slate-400">
          <div>
            <div className="text-[10px] uppercase tracking-wider">{copy.colOriginal}</div>
            <div className="font-semibold text-slate-800 dark:text-slate-200 text-xs mt-0.5">
              PKR {formatPkr(obligation.originalAmount)}
            </div>
          </div>
          <div>
            <div className="text-[10px] uppercase tracking-wider">{copy.colPaid}</div>
            <div className="font-semibold text-slate-800 dark:text-slate-200 text-xs mt-0.5">
              PKR {formatPkr(obligation.paidAmount)}
            </div>
          </div>
          <div>
            <div className="text-[10px] uppercase tracking-wider text-teal-600 dark:text-teal-400">{copy.colOutstanding}</div>
            <div className="font-bold text-teal-700 dark:text-teal-300 text-xs mt-0.5">
              PKR {formatPkr(obligation.outstandingAmount)}
            </div>
          </div>
        </div>
      </div>

      {error && (
        <div
          role="alert"
          className="rounded-lg bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-800 p-3 text-xs text-red-700 dark:text-red-300"
        >
          {error}
        </div>
      )}

      {/* Account Selector */}
      <div>
        <label htmlFor="settlement-account" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
          {copy.settlementAccountLabel} *
        </label>
        {loadingAccounts ? (
          <div className="text-xs text-slate-500 py-2">Loading accounts…</div>
        ) : (
          <select
            id="settlement-account"
            required
            value={accountId}
            onChange={e => setAccountId(e.target.value)}
            className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-xs text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-teal-500"
          >
            {accounts.map(acc => (
              <option key={acc.id} value={acc.id}>
                {acc.accountName} ({acc.accountType}) — Bal: PKR {formatPkr(acc.currentBalance)}
              </option>
            ))}
          </select>
        )}
      </div>

      {/* Settlement Amount */}
      <div>
        <Input
          id="settlement-amount"
          label={copy.settlementAmountLabel}
          type="number"
          step="0.01"
          min="0.01"
          max={obligation.outstandingAmount}
          required
          value={amount}
          onChange={e => setAmount(e.target.value)}
          placeholder="0.00"
          rightAddon={
            <button
              type="button"
              onClick={() => setAmount(obligation.outstandingAmount)}
              className="text-[11px] font-medium text-teal-600 dark:text-teal-400 hover:underline px-2"
            >
              Max
            </button>
          }
        />
      </div>

      {/* Settlement Date */}
      <div>
        <Input
          id="settlement-date"
          label={copy.settlementDateLabel}
          type="date"
          required
          value={settlementDate}
          onChange={e => setSettlementDate(e.target.value)}
        />
      </div>

      {/* Payment Reference */}
      <div>
        <Input
          id="settlement-ref"
          label={copy.settlementRefLabel}
          type="text"
          maxLength={100}
          value={reference}
          onChange={e => setReference(e.target.value)}
          placeholder={copy.settlementRefPlaceholder}
          isOptional
        />
      </div>

      {/* Payment Description */}
      <div>
        <label htmlFor="settlement-desc" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
          {copy.settlementDescLabel}
        </label>
        <textarea
          id="settlement-desc"
          rows={2}
          maxLength={1000}
          value={description}
          onChange={e => setDescription(e.target.value)}
          placeholder={copy.settlementDescPlaceholder}
          className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 p-2 text-xs text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-teal-500"
        />
      </div>

      {/* Submit Actions */}
      <div className="flex justify-end gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
        <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
          {copy.cancel}
        </Button>
        <Button type="submit" variant="primary" disabled={submitting || accounts.length === 0}>
          {submitting ? copy.submittingSettlement : copy.confirmSettlement}
        </Button>
      </div>
    </form>
  );
}
