"use client";
import { useCallback, useEffect, useRef } from 'react';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { transactionsApi } from '@/lib/api/dev-a/transactions';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
import { useLiveResource } from '@/components/integration/useLiveResource';
import { AccountMoneyValue } from '../accounts/AccountMoneyValue';
import { accountEn, accountUr } from '../accounts/account-copy';
import { transactionEn, transactionUr, transactionErrorKey } from './transaction-copy';

export function TransactionDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const { locale } = useLanguage(), session = useSession(), business = session.business?.businessId;
  const copy = locale === 'ur' ? transactionUr : transactionEn, moneyCopy = locale === 'ur' ? accountUr : accountEn;
  const host = useRef<HTMLDivElement>(null);
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
      const elements = Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), [tabindex="0"]'));
      const first = elements[0], last = elements.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    const contain = (event: FocusEvent) => { if (!dialog.contains(event.target as Node)) close?.focus(); };
    window.addEventListener('keydown', trap, true); document.addEventListener('focusin', contain);
    return () => { window.removeEventListener('keydown', trap, true); document.removeEventListener('focusin', contain); for (const item of inert) item.element.inert = item.previous; if (previous?.isConnected && !previous.closest('[inert]')) previous.focus(); };
  }, [copy.detailTitle, copy.close]);
  const t = resource.data?.transaction, error = resource.data?.error;
  const row = (label: string, value: React.ReactNode) => <div className="space-y-1 min-w-0"><dt className="text-sm text-[var(--color-text-muted)]">{label}</dt><dd className="[overflow-wrap:anywhere]">{value}</dd></div>;
  const exact = (value: string) => <AccountMoneyValue value={value} currency="PKR" scale={2} copy={moneyCopy} />;
  return <div ref={host}><Sheet isOpen onClose={onClose} title={copy.detailTitle}>
    {resource.loading ? <p role="status">{copy.loading}</p> : error || resource.error ? <div className="space-y-3"><p role="alert">{error ? copy[error] : copy.failed}</p><Button variant="secondary" onClick={resource.retry}>{copy.retry}</Button></div> : t && <dl className="space-y-4 min-w-0">
      {row(copy.id, <bdi dir="ltr">{t.id}</bdi>)}{row(copy.account, t.accountName)}
      {row(copy.classification, copy[t.eventType])}{row(copy.movement, copy[t.movementDirection])}
      {row(copy.amount, exact(t.amount))}{row(copy.resulting, exact(t.resultingBalance))}
      {row(copy.date, <bdi dir="ltr">{t.effectiveDate}</bdi>)}{row(copy.postedAt, <bdi dir="ltr">{t.createdAt}</bdi>)}
      {t.description !== null && row(copy.description, t.description)}
      {row(copy.actor, <bdi dir="ltr">{t.createdByUserId}</bdi>)}
      {row(copy.movement, <bdi dir="ltr">{t.movements[0].id}</bdi>)}
    </dl>}
  </Sheet></div>;
}
