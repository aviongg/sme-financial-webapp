"use client";
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { transactionsApi } from '@/lib/api/dev-a/transactions';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { useLiveResource } from '@/components/integration/useLiveResource';
import { AccountMoneyValue } from '../accounts/AccountMoneyValue';
import { accountEn, accountUr } from '../accounts/account-copy';
import { transactionEn, transactionUr, transactionErrorKey } from './transaction-copy';
import { canPresentPosting } from './transaction-submission';

export function TransactionDetail({
  id,
  onClose,
  onNavigate,
  onReversed
}: {
  id: string;
  onClose: () => void;
  onNavigate?: (id: string) => void;
  onReversed?: () => void;
}) {
  const { locale } = useLanguage(), session = useSession(), business = session.business?.businessId;
  const copy = locale === 'ur' ? transactionUr : transactionEn, moneyCopy = locale === 'ur' ? accountUr : accountEn;
  const host = useRef<HTMLDivElement>(null);
  const canPost = session.status === 'authenticated' && canPresentPosting(session.business);

  const [showReverseModal, setShowReverseModal] = useState(false);
  const [reason, setReason] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [idempotencyKey] = useState(() => (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : 'rev-' + Date.now()));
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const resource = useLiveResource(useCallback(async (signal: AbortSignal) => {
    try { return { transaction: await transactionsApi.detail(id, signal, business), error: null }; }
    catch (error) { if (error instanceof Error && error.name === 'AbortError') throw error; return { transaction: null, error: transactionErrorKey(error) }; }
  }, [id, business]));

  useEffect(() => {
    const dialog = host.current?.querySelector<HTMLElement>('[role="dialog"]');
    if (!dialog) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialog.setAttribute('aria-label', copy.detailTitle);
    const close = dialog.querySelector<HTMLButtonElement>('button'); close?.setAttribute('aria-label', copy.close);
    const panel = dialog.querySelector<HTMLElement>('.relative'); if (panel) { panel.style.maxHeight = '90dvh'; panel.style.overflowY = 'auto'; }
    const inert: { element: HTMLElement; previous: boolean }[] = [];
    let node: HTMLElement = dialog;
    while (node.parentElement) {
      for (const sibling of Array.from(node.parentElement.children)) if (sibling !== node && sibling instanceof HTMLElement) { inert.push({ element: sibling, previous: sibling.inert }); sibling.inert = true; }
      if (node.parentElement === document.body) break; node = node.parentElement;
    }
    close?.focus();
    const trap = (event: KeyboardEvent) => {
      if (event.key !== 'Tab') return;
      const elements = Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [tabindex="0"]'));
      const first = elements[0], last = elements.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    const contain = (event: FocusEvent) => { if (!dialog.contains(event.target as Node)) close?.focus(); };
    window.addEventListener('keydown', trap, true); document.addEventListener('focusin', contain);
    return () => { window.removeEventListener('keydown', trap, true); document.removeEventListener('focusin', contain); for (const item of inert) item.element.inert = item.previous; if (previous?.isConnected && !previous.closest('[inert]')) previous.focus(); };
  }, [copy.detailTitle, copy.close, showReverseModal]);

  const t = resource.data?.transaction, error = resource.data?.error;
  const row = (label: string, value: React.ReactNode) => <div className="space-y-1 min-w-0"><dt className="text-sm text-[var(--color-text-muted)]">{label}</dt><dd className="[overflow-wrap:anywhere]">{value}</dd></div>;
  const exact = (value: string) => <AccountMoneyValue value={value} currency="PKR" scale={2} copy={moneyCopy} />;

  const handleReverse = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!confirmed || reason.trim().length < 5 || reason.trim().length > 255 || submitting || !t) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await transactionsApi.reverse(t.id, reason, idempotencyKey, undefined, business);
      setShowReverseModal(false);
      onReversed?.();
      resource.retry();
    } catch (err: unknown) {
      const errKey = transactionErrorKey(err);
      setSubmitError(copy[errKey] ?? copy.failed);
    } finally {
      setSubmitting(false);
    }
  };

  return <div ref={host}><Sheet isOpen onClose={onClose} title={copy.detailTitle}>
    {resource.loading ? <p role="status">{copy.loading}</p> : error || resource.error ? <div className="space-y-3"><p role="alert">{error ? copy[error] : copy.failed}</p><Button variant="secondary" onClick={resource.retry}>{copy.retry}</Button></div> : t && <div className="space-y-6">
      {t.isReversal && (
        <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm space-y-1.5">
          <div className="flex items-center gap-2">
            <span className="inline-block rounded px-2 py-0.5 text-xs font-semibold bg-amber-500/20 text-amber-700 dark:text-amber-300 border border-amber-500/30">{copy.reversalBadge}</span>
            <span className="font-semibold text-amber-800 dark:text-amber-200">{copy.reversalOf}</span>
          </div>
          {t.reversedTransactionId && onNavigate ? (
            <button type="button" className="text-left font-mono text-xs text-[var(--color-brand-primary)] underline hover:no-underline" onClick={() => onNavigate(t.reversedTransactionId!)}>
              {copy.viewOriginal}: <bdi dir="ltr">{t.reversedTransactionId}</bdi>
            </button>
          ) : t.reversedTransactionId ? (
            <p className="font-mono text-xs text-[var(--color-text-muted)]"><bdi dir="ltr">{t.reversedTransactionId}</bdi></p>
          ) : null}
        </div>
      )}

      {t.isReversed && (
        <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 p-3 text-sm space-y-1.5">
          <div className="flex items-center gap-2">
            <span className="inline-block rounded px-2 py-0.5 text-xs font-semibold bg-rose-500/20 text-rose-700 dark:text-rose-300 border border-rose-500/30">{copy.reversedBadge}</span>
            <span className="font-semibold text-rose-800 dark:text-rose-200">{copy.alreadyReversed}</span>
          </div>
          {t.reversedByTransactionId && onNavigate ? (
            <button type="button" className="text-left font-mono text-xs text-[var(--color-brand-primary)] underline hover:no-underline" onClick={() => onNavigate(t.reversedByTransactionId!)}>
              {copy.viewReversal}: <bdi dir="ltr">{t.reversedByTransactionId}</bdi>
            </button>
          ) : t.reversedByTransactionId ? (
            <p className="font-mono text-xs text-[var(--color-text-muted)]"><bdi dir="ltr">{t.reversedByTransactionId}</bdi></p>
          ) : null}
        </div>
      )}

      <dl className="space-y-4 min-w-0">
        {row(copy.id, <bdi dir="ltr">{t.id}</bdi>)}
        {row(copy.account, t.accountName)}
        {row(copy.classification, copy[t.eventType])}
        {row(copy.movement, copy[t.movementDirection])}
        {row(copy.amount, t.isReversed ? <span className="line-through opacity-70">{exact(t.amount)}</span> : exact(t.amount))}
        {row(copy.resulting, exact(t.resultingBalance))}
        {row(copy.date, <bdi dir="ltr">{t.effectiveDate}</bdi>)}
        {row(copy.postedAt, <bdi dir="ltr">{t.createdAt}</bdi>)}
        {t.description !== null && row(copy.description, t.description)}
        {row(copy.actor, <bdi dir="ltr">{t.createdByUserId}</bdi>)}
        {t.reversalReason && row(copy.reversalReasonLabel, t.reversalReason)}
        {t.eventType === 'INTERNAL_TRANSFER' ? (
          <>
            {t.sourceAccountName && row(copy.fromAccount, t.sourceAccountName)}
            {t.destinationAccountName && row(copy.toAccount, t.destinationAccountName)}
            {t.movements.map(m => (
              <div key={m.id} className="space-y-1 min-w-0">
                <dt className="text-sm text-[var(--color-text-muted)]">{copy.movement} ({copy[m.movementDirection]}):</dt>
                <dd className="[overflow-wrap:anywhere]"><bdi dir="ltr">{m.id}</bdi></dd>
              </div>
            ))}
          </>
        ) : (
          t.movements[0] && row(copy.movement, <bdi dir="ltr">{t.movements[0].id}</bdi>)
        )}
      </dl>

      {/* Reversal action for eligible transactions */}
      {canPost && !t.isReversal && !t.isReversed && !showReverseModal && (
        <div className="pt-2 border-t border-[var(--color-border)]">
          <Button
            variant="secondary"
            className="w-full text-rose-600 border-rose-300 hover:bg-rose-50 dark:text-rose-400 dark:border-rose-900/50 dark:hover:bg-rose-950/30"
            onClick={() => setShowReverseModal(true)}
          >
            {copy.reverseEntry}
          </Button>
        </div>
      )}

      {/* Reversal Confirmation Form */}
      {canPost && !t.isReversal && !t.isReversed && showReverseModal && (
        <Card className="border-2 border-rose-300 dark:border-rose-800/60 bg-rose-50/50 dark:bg-rose-950/20 space-y-4">
          <div className="space-y-1">
            <h3 className="font-semibold text-rose-900 dark:text-rose-100">{copy.reverseTitle}</h3>
            <p className="text-sm text-rose-700 dark:text-rose-300">{copy.reverseNotice}</p>
          </div>

          <div className="text-xs text-[var(--color-text-muted)] bg-[var(--color-bg-subtle)] p-2.5 rounded">
            <span className="font-medium text-[var(--color-text-primary)]">
              {t.eventType === 'DIRECT_SALE_RECEIPT'
                ? copy.reverseImpactReceipt
                : t.eventType === 'OPERATING_EXPENSE_PAYMENT'
                ? copy.reverseImpactExpense
                : copy.reverseImpactTransfer}
            </span>
          </div>

          <form onSubmit={handleReverse} className="space-y-4">
            <div className="space-y-1.5">
              <label htmlFor="reversal-reason" className="block text-sm font-medium">
                {copy.reverseReason} <span className="text-rose-500">*</span>
              </label>
              <textarea
                id="reversal-reason"
                required
                minLength={5}
                maxLength={255}
                rows={3}
                value={reason}
                onChange={e => setReason(e.target.value)}
                placeholder={copy.reverseReasonHelp}
                className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-surface)] p-2 text-sm focus:outline-2 focus:outline-[var(--color-brand-primary)]"
                disabled={submitting}
              />
              <p className="text-xs text-[var(--color-text-muted)]">{reason.length} / 255</p>
            </div>

            <label className="flex items-start gap-2.5 cursor-pointer text-sm">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={e => setConfirmed(e.target.checked)}
                className="mt-0.5 h-4 w-4 rounded border-[var(--color-border)]"
                disabled={submitting}
              />
              <span>{copy.reverseConfirm}</span>
            </label>

            {submitError && (
              <p role="alert" className="text-sm text-rose-600 dark:text-rose-400 font-medium">
                {submitError}
              </p>
            )}

            <div className="flex flex-wrap items-center gap-3 pt-2">
              <Button
                type="submit"
                disabled={!confirmed || reason.trim().length < 5 || reason.trim().length > 255 || submitting}
                className="bg-rose-600 text-white hover:bg-rose-700"
              >
                {submitting ? copy.reversing : copy.reverseSubmit}
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={submitting}
                onClick={() => { setShowReverseModal(false); setSubmitError(null); }}
              >
                {copy.cancel}
              </Button>
            </div>
          </form>
        </Card>
      )}
    </div>}
  </Sheet></div>;
}
