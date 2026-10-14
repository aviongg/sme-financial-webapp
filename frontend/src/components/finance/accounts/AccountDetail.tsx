"use client";
import { useCallback, useEffect, useRef } from 'react';
import { accountsApi } from '@/lib/api/dev-a/accounts';
import { useLiveResource } from '@/components/integration/useLiveResource';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
import { AccountMoneyValue } from './AccountMoneyValue';
import { accountEn, accountUr, accountErrorKey } from './account-copy';

export function AccountDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const { locale } = useLanguage();
  const copy = locale === 'ur' ? accountUr : accountEn;
  const host = useRef<HTMLDivElement>(null);
  const resource = useLiveResource(useCallback(async (signal: AbortSignal) => {
    try { return { account: await accountsApi.detail(id, signal), error: null }; }
    catch (error) {
      if (error instanceof Error && error.name === 'AbortError') throw error;
      return { account: null, error: accountErrorKey(error, true) };
    }
  }, [id]));

  // Add feature-local dialog naming, focus containment and background inertness
  // around the existing Sheet without changing the shared primitive.
  useEffect(() => {
    const dialog = host.current?.querySelector<HTMLElement>('[role="dialog"]');
    if (!dialog) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialog.setAttribute('aria-label', copy.detailTitle);
    const close = dialog.querySelector<HTMLButtonElement>('button');
    close?.setAttribute('aria-label', copy.close);
    const panel = dialog.querySelector<HTMLElement>('.relative');
    if (panel) { panel.style.maxHeight = '90dvh'; panel.style.overflowY = 'auto'; }
    const inert: { element: HTMLElement; previous: boolean }[] = [];
    let node: HTMLElement = dialog;
    while (node.parentElement) {
      for (const sibling of Array.from(node.parentElement.children)) {
        if (sibling !== node && sibling instanceof HTMLElement) {
          inert.push({ element: sibling, previous: sibling.inert }); sibling.inert = true;
        }
      }
      if (node.parentElement === document.body) break;
      node = node.parentElement;
    }
    const focusables = () => Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), [tabindex="0"]'));
    close?.focus();
    const trap = (event: KeyboardEvent) => {
      if (event.key !== 'Tab') return;
      const elements = focusables(), first = elements[0], last = elements.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    const contain = (event: FocusEvent) => { if (!dialog.contains(event.target as Node)) close?.focus(); };
    window.addEventListener('keydown', trap, true);
    document.addEventListener('focusin', contain);
    return () => {
      window.removeEventListener('keydown', trap, true); document.removeEventListener('focusin', contain);
      for (const item of inert) item.element.inert = item.previous;
      if (previous?.isConnected && !previous.closest('[inert]')) previous.focus();
    };
  }, [copy.close, copy.detailTitle]);

  const account = resource.data?.account;
  const error = resource.data?.error;
  const row = (label: string, value: React.ReactNode) => <div className="space-y-1 min-w-0"><dt className="text-sm text-[var(--color-text-muted)]">{label}</dt><dd className="break-words [overflow-wrap:anywhere]">{value}</dd></div>;
  return <div ref={host}><Sheet isOpen onClose={onClose} title={copy.detailTitle}>
    {resource.loading ? <p role="status">{copy.detailLoading}</p> : error || resource.error ? <div className="space-y-3"><p role="alert">{error ? copy[error] : copy.failed}</p><Button variant="secondary" onClick={resource.retry}>{copy.retry}</Button></div> : account && <div className="space-y-4 min-w-0">
      <h3 className="font-semibold break-words [overflow-wrap:anywhere]">{account.accountName}</h3>
      <dl className="space-y-4">
        {row(copy.type, copy[account.accountType])}{row(copy.status, copy[account.status])}
        {row(copy.currency, <bdi dir="ltr">{account.currency}</bdi>)}
        {row(copy.current, <AccountMoneyValue value={account.currentBalance} currency={account.currency} scale={account.currencyDecimals} copy={copy} />)}
        {account.balanceStatus === 'UNAVAILABLE' && row(copy.unavailable, copy[account.balanceUnavailableReason])}
        {row(copy.opening, <AccountMoneyValue value={account.openingBalance} currency={account.currency} scale={account.currencyDecimals} copy={copy} />)}
        {row(copy.openingDate, <bdi dir="ltr">{account.openingDate}</bdi>)}
        {row(copy.cutoff, <bdi dir="ltr">{account.effectiveCutoffDate}</bdi>)}
        {row(copy.observed, <bdi dir="ltr" className="font-sans">{account.observationAt}</bdi>)}
        {account.institutionName !== null && row(copy.institution, account.institutionName)}
        {account.accountIdentifier !== null && row(copy.identifier, <bdi dir="ltr">{account.accountIdentifier}</bdi>)}
        {account.notes !== null && row(copy.notes, account.notes)}
      </dl><p className="text-sm text-[var(--color-text-muted)]">{copy.dateHelp}</p>
    </div>}
  </Sheet></div>;
}
