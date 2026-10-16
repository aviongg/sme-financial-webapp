"use client";
import { useEffect, useId, useMemo, useRef, useState } from 'react';
import type { Account } from '@/lib/api/dev-a/account-contracts';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { normalizeAmountInput } from '@/lib/utils/amount-input';
import { karachiToday } from '../transactions/transaction-input';
import { transferEn, transferUr, type TransferMessageKey } from './transfer-copy';
import { validateTransferInput, type TransferDraft, type TransferInputErrors } from './transfer-input';
import { canPresentTransfer, transferFailure, transferFieldErrors, TransferSubmission, type TransferPhase } from './transfer-submission';
import { transfersApi } from '@/lib/api/dev-a/transfers';
import type { TransactionDetailData } from '@/lib/api/dev-a/transaction-contracts';
import { descriptionLimit } from '@/lib/api/dev-a/transaction-contracts';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';

export function PostTransferForm({
  accounts,
  onTransferred,
  onClose
}: {
  accounts: Account[];
  onTransferred: (transaction: TransactionDetailData) => void;
  onClose: () => void;
}) {
  const session = useSession();
  const { locale, direction } = useLanguage();
  const formId = useId();
  const copy = locale === 'ur' ? transferUr : transferEn;
  const context = JSON.stringify([session.epoch, session.user?.id, session.business?.businessId, session.business?.role, session.business?.membershipStatus, session.status]);
  const business = session.business?.businessId;

  const eligibleAccounts = useMemo(
    () => accounts.filter(a => a.status === 'ACTIVE' && a.currency === 'PKR' && a.currencyDecimals === 2 && a.balanceStatus === 'AVAILABLE'),
    [accounts]
  );

  const [draft, setDraft] = useState<TransferDraft>({
    sourceAccountId: eligibleAccounts[0]?.id ?? '',
    destinationAccountId: eligibleAccounts[1]?.id ?? '',
    amountText: '',
    dateText: karachiToday(),
    descriptionText: '',
    confirmed: false
  });

  const [errors, setErrors] = useState<TransferInputErrors>({});
  const [phase, setPhase] = useState<TransferPhase>('idle');
  const [message, setMessage] = useState<TransferMessageKey | null>(null);

  const entryRef = useRef<{ key: string; submission: TransferSubmission } | null>(null);

  const sourceAccount = eligibleAccounts.find(a => a.id === draft.sourceAccountId);
  const destAccount = eligibleAccounts.find(a => a.id === draft.destinationAccountId);
  const normalizedAmount = normalizeAmountInput(draft.amountText, 'PKR');

  const movementSummary = useMemo(() => {
    if (!sourceAccount || !destAccount || !normalizedAmount?.canonical) return null;
    return copy.movementSummary(normalizedAmount.canonical, sourceAccount.accountName, destAccount.accountName);
  }, [copy, sourceAccount, destAccount, normalizedAmount]);

  useEffect(() => {
    const entry = {
      key: context,
      submission: new TransferSubmission((payload, key, signal) => transfersApi.post(payload, key, signal, business))
    };
    entryRef.current = entry;
    return () => {
      entry.submission.invalidate();
      if (entryRef.current === entry) entryRef.current = null;
    };
  }, [context, business]);

  if (session.status !== 'authenticated' || !canPresentTransfer(session.business)) return null;

  const submit = async () => {
    const entry = entryRef.current;
    if (!entry || entry.key !== context || !['idle', 'uncertain'].includes(entry.submission.phase)) return;

    const normalizedDraft: TransferDraft = {
      ...draft,
      amountText: normalizedAmount?.canonical ?? draft.amountText
    };

    const validation = entry.submission.phase === 'uncertain'
      ? {}
      : validateTransferInput(normalizedDraft, eligibleAccounts, descriptionLimit);

    if (!normalizedAmount && !validation.amountText) {
      validation.amountText = 'positiveAmount';
    }

    setErrors(validation);
    if (Object.keys(validation).length > 0) {
      setMessage('validation');
      return;
    }

    const promise = entry.submission.submit(normalizedDraft);
    setPhase(entry.submission.phase);
    setMessage(null);

    try {
      const transaction = await promise;
      if (entryRef.current === entry && entry.submission.phase === 'complete') {
        onTransferred(transaction);
      }
    } catch (error) {
      if (entryRef.current !== entry || entry.submission.phase === 'invalidated') return;
      setMessage(transferFailure(error));
      setPhase(entry.submission.phase);
      setErrors(transferFieldErrors(error));
    }
  };

  const busy = phase === 'submitting';
  const locked = ['uncertain', 'conflict', 'denied'].includes(phase);
  const blocked = phase === 'conflict' || phase === 'denied';

  return (
    <Card className="border-2 border-[var(--color-brand-primary)] space-y-4">
      <form
        id={formId}
        dir={direction}
        aria-label={copy.title}
        onSubmit={e => {
          e.preventDefault();
          void submit();
        }}
        className="space-y-4"
      >
        <h2 className="text-lg font-semibold">{copy.title}</h2>

        {eligibleAccounts.length < 2 && (
          <p role="alert" className="text-sm text-[var(--color-status-danger)]">
            {copy.noEligibleAccounts}
          </p>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <Select
              label={copy.fromAccount}
              value={draft.sourceAccountId}
              disabled={locked || busy || eligibleAccounts.length < 2}
              options={[
                { value: '', label: copy.chooseSource },
                ...eligibleAccounts.map(a => ({ value: a.id, label: `${a.accountName} (${a.currency})` }))
              ]}
              onChange={e => {
                setDraft(prev => ({ ...prev, sourceAccountId: e.target.value, confirmed: false }));
                setErrors({});
                setMessage(null);
              }}
            />
            {errors.sourceAccountId && (
              <p role="alert" className="text-xs text-[var(--color-status-danger)] mt-1">
                {copy[errors.sourceAccountId]}
              </p>
            )}
          </div>

          <div>
            <Select
              label={copy.toAccount}
              value={draft.destinationAccountId}
              disabled={locked || busy || eligibleAccounts.length < 2}
              options={[
                { value: '', label: copy.chooseDestination },
                ...eligibleAccounts.map(a => ({ value: a.id, label: `${a.accountName} (${a.currency})` }))
              ]}
              onChange={e => {
                setDraft(prev => ({ ...prev, destinationAccountId: e.target.value, confirmed: false }));
                setErrors({});
                setMessage(null);
              }}
            />
            {errors.destinationAccountId && (
              <p role="alert" className="text-xs text-[var(--color-status-danger)] mt-1">
                {copy[errors.destinationAccountId]}
              </p>
            )}
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <Input
              label={copy.amount + ' (PKR)'}
              type="text"
              inputMode="decimal"
              dir="ltr"
              value={draft.amountText}
              disabled={locked || busy}
              onChange={e => {
                setDraft(prev => ({ ...prev, amountText: e.target.value, confirmed: false }));
                setErrors({});
                setMessage(null);
              }}
              placeholder="e.g. 5000.00"
            />
            {normalizedAmount && normalizedAmount.canonical !== draft.amountText && (
              <p className="text-xs text-[var(--color-text-muted)] mt-1">
                Preview: <bdi dir="ltr" className="tabular-nums font-semibold">{normalizedAmount.canonical} PKR</bdi>
              </p>
            )}
            {errors.amountText && (
              <p role="alert" className="text-xs text-[var(--color-status-danger)] mt-1">
                {copy[errors.amountText]}
              </p>
            )}
          </div>

          <div>
            <Input
              label={copy.date}
              type="date"
              dir="ltr"
              value={draft.dateText}
              disabled={locked || busy}
              onChange={e => {
                setDraft(prev => ({ ...prev, dateText: e.target.value, confirmed: false }));
                setErrors({});
                setMessage(null);
              }}
            />
            {errors.dateText && (
              <p role="alert" className="text-xs text-[var(--color-status-danger)] mt-1">
                {copy[errors.dateText]}
              </p>
            )}
          </div>
        </div>

        <div>
          <Input
            label={copy.noteOptional}
            type="text"
            value={draft.descriptionText}
            disabled={locked || busy}
            onChange={e => {
              setDraft(prev => ({ ...prev, descriptionText: e.target.value }));
              setErrors({});
              setMessage(null);
            }}
            placeholder="e.g. Replenish petty cash"
            maxLength={descriptionLimit}
          />
          {errors.descriptionText && (
            <p role="alert" className="text-xs text-[var(--color-status-danger)] mt-1">
              {copy[errors.descriptionText]}
            </p>
          )}
        </div>

        {movementSummary && (
          <Card className="bg-[var(--color-bg-subtle)] border-l-4 border-[var(--color-brand-primary)]">
            <p className="font-semibold text-sm" role="status">
              {movementSummary}
            </p>
          </Card>
        )}

        <div className="flex items-start gap-2 pt-1">
          <input
            id={`${formId}-confirm`}
            type="checkbox"
            checked={draft.confirmed}
            disabled={locked || busy}
            onChange={e => {
              setDraft(prev => ({ ...prev, confirmed: e.target.checked }));
              setErrors(prev => ({ ...prev, confirmed: undefined }));
              setMessage(null);
            }}
            className="mt-1 h-4 w-4 rounded border-gray-300 text-[var(--color-brand-primary)] focus:ring-[var(--color-brand-primary)]"
          />
          <label htmlFor={`${formId}-confirm`} className="text-sm select-none cursor-pointer">
            {copy.confirmMovement}
          </label>
        </div>
        {errors.confirmed && (
          <p role="alert" className="text-xs text-[var(--color-status-danger)]">
            {copy[errors.confirmed]}
          </p>
        )}

        {message && (
          <p role="alert" className="text-sm font-semibold text-[var(--color-status-danger)]">
            {copy[message]}
          </p>
        )}

        <div className="flex flex-wrap items-center gap-3 pt-2">
          {phase === 'uncertain' ? (
            <Button type="button" onClick={() => void submit()} disabled={busy}>
              {copy.retry}
            </Button>
          ) : (
            <Button type="submit" disabled={busy || blocked || eligibleAccounts.length < 2}>
              {busy ? copy.submitting : copy.moveMoney}
            </Button>
          )}
          <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
            {copy.cancel}
          </Button>
        </div>
      </form>
    </Card>
  );
}
