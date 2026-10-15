"use client";
import { normalizeAmountInput } from '@/lib/utils/amount-input';
import { CreateAccountForm } from '../accounts/CreateAccountForm';
import { loadTransactionAccounts } from './transaction-accounts';
import { useEffect, useId, useRef, useState } from 'react';
import type { Account } from '@/lib/api/dev-a/account-contracts';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { isActivePkr, karachiToday, postingChoices, validatePostingInput, type PostingDraft, type PostingInputErrors } from './transaction-input';
import { transactionEn, transactionUr, type TransactionCopy } from './transaction-copy';
import { TransactionSubmission, canPresentPosting, postingFailure, postingFieldErrors, type PostingPhase } from './transaction-submission';
import { transactionsApi } from '@/lib/api/dev-a/transactions';
import { descriptionLimit, type TransactionDetailData } from '@/lib/api/dev-a/transaction-contracts';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';

export function PostTransactionForm({ accounts, onPosted, onClose }: { accounts: Account[]; onPosted: (transaction: TransactionDetailData) => void; onClose: () => void }) {
  const session = useSession(), { locale } = useLanguage(), copy = locale === 'ur' ? transactionUr : transactionEn;
  const context = JSON.stringify([session.epoch, session.user?.id, session.business?.businessId, session.business?.role, session.business?.membershipStatus, session.status]);
  const entryRef = useRef<{ key: string; submission: TransactionSubmission } | null>(null);
  const [draft, setDraft] = useState<PostingDraft>({ selectedAccount: '', event: 'DIRECT_SALE_RECEIPT', amountText: '', dateText: karachiToday(), descriptionText: '', confirmed: false });
  const [creating, setCreating] = useState(false);
  const [createdAccounts, setCreatedAccounts] = useState<Account[]>([]);
  const [accountRefreshError, setAccountRefreshError] = useState(false);
  const refreshController = useRef<AbortController | null>(null);
  const accountOptions = [...accounts.filter(a => !createdAccounts.some(b => b.id === a.id)), ...createdAccounts];
  const [errors, setErrors] = useState<PostingInputErrors>({}), [phase, setPhase] = useState<PostingPhase>('idle');
  const [message, setMessage] = useState<keyof TransactionCopy | null>(null);
  const business = session.business?.businessId;
  useEffect(() => {
    const entry = { key: context, submission: new TransactionSubmission((payload, key, signal) => transactionsApi.post(payload, key, signal, business)) };
    entryRef.current = entry;
    return () => { refreshController.current?.abort(); entry.submission.invalidate(); if (entryRef.current === entry) entryRef.current = null; };
  }, [context, business]);
  if (session.status !== 'authenticated' || !canPresentPosting(session.business)) return null;
  const submit = async () => {
    const entry = entryRef.current;
    if (!entry || entry.key !== context || !['idle', 'uncertain'].includes(entry.submission.phase)) return;
    const amount = normalizeAmountInput(draft.amountText, 'PKR');
    const normalizedDraft = { ...draft, amountText: amount?.canonical ?? draft.amountText };
    const validation = entry.submission.phase === 'uncertain' ? {} : validatePostingInput(normalizedDraft, accountOptions.find(a => a.id === draft.selectedAccount), descriptionLimit);
    if (!amount) validation.amountText = 'positiveAmount';
    setErrors(validation);
    if (Object.keys(validation).length) { setMessage('validation'); return; }
    const promise = entry.submission.submit(normalizedDraft); setPhase(entry.submission.phase); setMessage(null);
    try {
      const transaction = await promise;
      if (entryRef.current === entry && entry.submission.phase === 'complete') onPosted(transaction);
    } catch (error) {
      if (entryRef.current !== entry || entry.submission.phase === 'invalidated') return;
      setMessage(postingFailure(error)); setPhase(entry.submission.phase);
      setErrors(postingFieldErrors(error));
    }
  };
  if (creating) return <CreateAccountForm fixedCurrency="PKR" onClose={() => setCreating(false)} onCreated={async account => {
    const entry = entryRef.current;
    refreshController.current?.abort(); const controller = new AbortController(); refreshController.current = controller;
    setCreatedAccounts(previous => [...previous.filter(a => a.id !== account.id), account]);
    setDraft(previous => ({ ...previous, selectedAccount: isActivePkr(account) ? account.id : previous.selectedAccount, confirmed: false }));
    setCreating(false); setAccountRefreshError(false);
    try { const refreshed = await loadTransactionAccounts(controller.signal); if (entryRef.current === entry && !controller.signal.aborted) setCreatedAccounts(refreshed); }
    catch { if (entryRef.current === entry && !controller.signal.aborted) setAccountRefreshError(true); }
  }} />;
  return <><Button variant="secondary" disabled={phase !== 'idle'} onClick={() => {if (entryRef.current?.submission.phase === 'idle') setCreating(true);}}>{copy.createAccount}</Button>
    {accountRefreshError && <p role="alert">{copy.accountRefreshFailed}</p>}
    <PostingFormView draft={draft} accounts={accountOptions} descriptionLimit={descriptionLimit} errors={errors} copy={copy}
    busy={phase === 'submitting'} locked={['uncertain', 'conflict', 'denied'].includes(phase)} blocked={phase === 'conflict' || phase === 'denied'} message={message ? copy[message] : undefined}
    onChange={patch => { if (phase === 'idle' && entryRef.current?.submission.phase === 'idle') { setDraft(previous => ({ ...previous, ...patch })); setErrors({}); setMessage(null); } }}
    onSubmit={() => { void submit(); }} onClose={onClose} /></>;
}

/** Controlled presentation; request wiring requires the ready contract. */
export function PostingFormView({ draft, accounts, descriptionLimit, errors, copy, busy = false, locked = false, blocked = false, message, onChange, onSubmit, onClose }: {
  draft: PostingDraft; accounts: Account[]; descriptionLimit: number; errors: PostingInputErrors; copy: TransactionCopy;
  busy?: boolean; locked?: boolean; blocked?: boolean; message?: string;
  onChange: (patch: Partial<PostingDraft>) => void; onSubmit: () => void; onClose: () => void;
}) {
  const id = useId(), form = useRef<HTMLFormElement>(null), accountInput = useRef<HTMLSelectElement>(null);
  useEffect(() => { accountInput.current?.focus(); }, []);
  useEffect(() => { if (Object.keys(errors).length) form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(); }, [errors]);
  const preview = normalizeAmountInput(draft.amountText, 'PKR');
  const eligible = accounts.filter(isActivePkr), selected = eligible.find(account => account.id === draft.selectedAccount);
  const error = (field: keyof PostingDraft) => errors[field] ? copy[errors[field]] : undefined;
  return <Card className="min-w-0"><form ref={form} aria-labelledby={id} aria-busy={busy} noValidate className="space-y-4" onSubmit={event => { event.preventDefault(); if (!busy && !blocked) onSubmit(); }}>
    <h2 id={id} className="font-heading font-semibold">{copy.add}</h2>
    <div className="space-y-2 text-sm"><p>{copy.saleHelp}</p><p>{copy.expenseHelp}</p><p>{copy.excluded}</p></div>
    <p className="text-sm">{copy.availableOnly}</p>
    <fieldset disabled={busy || locked} className="grid min-w-0 gap-4 sm:grid-cols-2">
      <Select ref={accountInput} label={copy.account} required value={draft.selectedAccount} options={[{ value: '', label: copy.chooseAccount }, ...eligible.map(account => ({ value: account.id, label: account.accountName + ' · PKR' }))]} error={error('selectedAccount')} onChange={event => onChange({ selectedAccount: event.target.value, confirmed: false })} />
      <Select label={copy.event} required value={draft.event} options={postingChoices.map(value => ({ value, label: copy[value] }))} error={error('event')} onChange={event => onChange({ event: event.target.value as PostingDraft['event'], confirmed: false })} />
      <Input label={copy.amount} required type="text" inputMode="decimal" dir="ltr" className="font-sans tabular-nums [unicode-bidi:isolate]" autoComplete="off" value={draft.amountText} helperText={copy.amountHelp} error={error('amountText')} onChange={event => onChange({ amountText: event.target.value, confirmed: false })} />
      <Input label={copy.date} required type="date" dir="ltr" className="font-sans [unicode-bidi:isolate]" min={selected?.openingDate} max={karachiToday()} value={draft.dateText} error={error('dateText')} onChange={event => onChange({ dateText: event.target.value, confirmed: false })} />
      <Input label={copy.description} maxLength={descriptionLimit} value={draft.descriptionText} error={error('descriptionText')} onChange={event => onChange({ descriptionText: event.target.value, confirmed: false })} />
      <label className="flex items-start gap-3 text-sm"><input type="checkbox" className="mt-1 accent-[var(--color-brand-primary)]" checked={draft.confirmed} aria-invalid={!!errors.confirmed} aria-describedby={errors.confirmed ? id + '-confirm-error' : undefined} onChange={event => onChange({ confirmed: event.target.checked })} /><span>{copy.confirm}</span></label>
    </fieldset>
    {preview && <p role="status">{preview.rounded ? copy.rounded : copy.recordedAmount}: <bdi dir="ltr">{preview.canonical} PKR</bdi></p>}
    {errors.confirmed && <p id={id + '-confirm-error'} role="alert" className="text-sm">{copy.confirmRequired}</p>}
    {message && <p role="alert" className="text-sm">{message}</p>}
    {!eligible.length && <p role="status">{copy.noAccounts}</p>}
    <div className="flex flex-wrap gap-3"><Button type="submit" isLoading={busy} disabled={blocked || !eligible.length}>{locked ? copy.retryOriginal : copy.add}</Button><Button type="button" variant="secondary" disabled={busy || locked} onClick={onClose}>{copy.cancel}</Button></div>
  </form></Card>;
}
