"use client";
import { useEffect, useId, useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Card } from '@/components/ui/Card';
import { accountTypes } from '@/lib/api/dev-a/account-contracts';
import { currencyScales } from '@/lib/utils/decimal-display';
import { AccountCreateSubmission, accountCreateFailure, canPresentAccountCreate, validateOpeningSetup, type CreatePhase, type OpeningSetupDraft, type OpeningField, type OpeningInputErrors } from '@/lib/api/dev-a/account-create-contracts';
import { accountsApi } from '@/lib/api/dev-a/accounts';
import { ApiError } from '@/lib/api/client';
import type { AccountDetailData } from '@/lib/api/dev-a/account-contracts';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { accountCreateEn, accountCreateUr, accountEn, accountUr, type AccountCopy, type AccountCreateCopy } from './account-copy';

export function CreateAccountForm({ onCreated, onClose }: { onCreated: (account: AccountDetailData) => void; onClose: () => void }) {
  const session = useSession();
  const { locale } = useLanguage();
  const copy = locale === 'ur' ? accountCreateUr : accountCreateEn;
  const accountCopy = locale === 'ur' ? accountUr : accountEn;
  const contextKey = JSON.stringify([session.epoch, session.user?.id, session.business?.businessId, session.business?.role, session.business?.membershipStatus, session.status]);
  const entryRef = useRef<{ key: string; submission: AccountCreateSubmission } | null>(null);
  const [draft, setDraft] = useState<OpeningSetupDraft>({ accountName: '', accountType: 'BANK', currency: 'PKR', openingBalance: '', openingDate: '' });
  const [errors, setErrors] = useState<OpeningInputErrors>({});
  const [phase, setPhase] = useState<CreatePhase>('idle');
  const [failure, setFailure] = useState<'validation' | 'duplicate' | 'conflict' | 'denied' | 'contextEnded' | 'uncertain' | null>(null);
  useEffect(() => {
    const entry = { key: contextKey, submission: new AccountCreateSubmission(accountsApi.create) };
    entryRef.current = entry;
    return () => { entry.submission.invalidate(); if (entryRef.current === entry) entryRef.current = null; };
  }, [contextKey]);
  if (session.status !== 'authenticated' || !canPresentAccountCreate(session.business)) return null;
  const locked = ['uncertain', 'conflict', 'denied'].includes(phase);
  const submit = async () => {
    const entry = entryRef.current;
    if (!entry || entry.key !== contextKey || !['idle', 'uncertain'].includes(entry.submission.phase)) return;
    const validation = validateOpeningSetup(draft);
    setErrors(validation);
    if (Object.keys(validation).length) { setFailure('validation'); return; }
    const pending = entry.submission.submit(draft); // Synchronous lock before the first await.
    setPhase(entry.submission.phase); setFailure(null);
    try {
      const account = await pending;
      if (entryRef.current === entry && entry.submission.phase === 'complete') onCreated(account);
    } catch (error) {
      if (entryRef.current !== entry || entry.submission.phase === 'invalidated') return;
      const kind = accountCreateFailure(error);
      setFailure(kind === 'session' ? 'contextEnded' : kind);
      if (kind === 'duplicate') setErrors({ accountName: 'duplicate' });
      if (kind === 'validation' && error instanceof ApiError) {
        const fieldCopy = { accountName: 'nameRequired', accountType: 'typeInvalid', currency: 'currencyInvalid', openingBalance: 'amountInvalid', openingDate: 'dateInvalid' } as const;
        const fields: OpeningInputErrors = {};
        for (const field of Object.keys(fieldCopy) as OpeningField[]) if (error.fieldErrors?.[field]) fields[field] = fieldCopy[field];
        setErrors(fields);
      }
      setPhase(entry.submission.phase);
    }
  };
  return <OpeningSetupForm draft={draft} onChange={(field, value) => { if (phase !== 'idle' || entryRef.current?.submission.phase !== 'idle') return; setDraft(previous => ({ ...previous, [field]: value })); setErrors(previous => ({ ...previous, [field]: undefined })); setFailure(null); }} onSubmit={() => { void submit(); }} onClose={onClose} errors={errors} copy={copy} accountCopy={accountCopy}
    busy={phase === 'submitting'} locked={locked} submitDisabled={phase === 'conflict' || phase === 'denied'} message={failure ? copy[failure] : undefined} />;
}

export function OpeningSetupForm({ draft, onChange, onSubmit, onClose, errors, copy, accountCopy, busy = false, locked = false, submitDisabled = false, message }: {
  draft: OpeningSetupDraft;
  onChange: (field: OpeningField, value: string) => void;
  onSubmit: () => void;
  onClose: () => void;
  errors: OpeningInputErrors;
  copy: AccountCreateCopy;
  accountCopy: AccountCopy;
  busy?: boolean;
  locked?: boolean;
  submitDisabled?: boolean;
  message?: string;
}) {
  const titleId = useId(), helpId = useId();
  const nameRef = useRef<HTMLInputElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  useEffect(() => { nameRef.current?.focus(); }, []);
  useEffect(() => { if (Object.keys(errors).length) formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(); }, [errors]);
  const error = (field: OpeningField) => errors[field] ? copy[errors[field]] : undefined;
  return <Card className="min-w-0">
    <form ref={formRef} aria-labelledby={titleId} aria-describedby={helpId} aria-busy={busy} noValidate onSubmit={event => { event.preventDefault(); if (!busy && !submitDisabled) onSubmit(); }} className="space-y-4">
      <h2 id={titleId} className="font-heading font-semibold">{copy.add}</h2>
      <div id={helpId} className="text-sm space-y-2 text-[var(--color-text-secondary)]"><p>{copy.openingHelp}</p><p>{copy.revenueHelp}</p><p>{copy.futureHelp}</p></div>
      <fieldset disabled={busy || locked} className="grid min-w-0 gap-4 sm:grid-cols-2">
        <Input ref={nameRef} label={copy.name} required maxLength={100} value={draft.accountName} onChange={event => onChange('accountName', event.target.value)} error={error('accountName')} />
        <Select label={accountCopy.type} required value={draft.accountType} options={accountTypes.map(value => ({ value, label: accountCopy[value] }))} onChange={event => onChange('accountType', event.target.value)} error={error('accountType')} />
        <Select label={accountCopy.currency} required value={draft.currency} options={Object.keys(currencyScales).map(value => ({ value, label: value }))} onChange={event => onChange('currency', event.target.value)} error={error('currency')} />
        <Input label={accountCopy.opening} required type="text" inputMode="decimal" dir="ltr" className="font-sans tabular-nums [unicode-bidi:isolate]" autoComplete="off" spellCheck={false} value={draft.openingBalance} onChange={event => onChange('openingBalance', event.target.value)} error={error('openingBalance')} helperText={copy.amountHelp} />
        <Input label={accountCopy.openingDate} required type="date" dir="ltr" className="font-sans [unicode-bidi:isolate]" value={draft.openingDate} onChange={event => onChange('openingDate', event.target.value)} error={error('openingDate')} />
      </fieldset>
      {message && <p role="alert" className="text-sm">{message}</p>}
      {locked && <p className="text-sm">{copy.uncertainExit}</p>}
      <div className="flex flex-wrap gap-3">
        <Button type="submit" isLoading={busy} disabled={submitDisabled}>{locked ? copy.retryOriginal : copy.save}</Button>
        <Button type="button" variant="secondary" disabled={busy || locked} onClick={onClose}>{copy.cancel}</Button>
      </div>
    </form>
  </Card>;
}
