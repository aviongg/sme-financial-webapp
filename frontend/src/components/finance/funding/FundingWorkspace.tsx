"use client";
import { useEffect, useRef, useState } from 'react';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { fundingCopy } from '@/lib/i18n/translations/funding';
import { ApiError } from '@/lib/api/client';
import { normalizeAmountInput } from '@/lib/utils/amount-input';
import { formatPkr } from '@/lib/utils/decimal-display';
import { isCalendarDate } from '@/lib/api/dev-a/account-contracts';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Dialog } from '@/components/ui/Dialog';
import { CashEventEditor } from './CashEventEditor';
import { FundingResults, type FundingComparisonView } from './FundingResults';
import { cloneEvents, endDate, eventErrors, type FundingAssumptions, type PlannedEvent } from './funding-input';
import { compareFundingScenarios } from '@/lib/api/dev-a/funding';
/** Editor input is not a wire DTO. Mapping must follow the published C06 contract. */
export type FundingEditorInput = { assumptions: FundingAssumptions; baseline: PlannedEvent[]; alternative: PlannedEvent[] };
export type FundingCompare = (input: FundingEditorInput, signal: AbortSignal) => Promise<FundingComparisonView>;
export function FundingWorkspace({ compare = compareFundingScenarios }: { compare?: FundingCompare }) {
 const session = useSession();
 const context = JSON.stringify([session.epoch, session.user?.id, session.business?.businessId, session.business?.role, session.business?.membershipStatus, session.status]);
 return <FundingForm key={context} compare={compare} authorized={session.status === 'authenticated' && session.can('FINANCIAL_DATA_READ')} />;
}
function FundingForm({ compare, authorized }: { compare?: FundingCompare; authorized: boolean }) {
 const { locale } = useLanguage(), copy = fundingCopy(locale);
 const [assumptions, setAssumptions] = useState<FundingAssumptions>(() => ({ startDate: new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Karachi', year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()), horizonDays: 30, source: 'LEDGER', openingText: '' }));
 const [confirmCopy, setConfirmCopy] = useState(false);
 const [baseline, setBaseline] = useState<PlannedEvent[]>([]), [alternative, setAlternative] = useState<PlannedEvent[]>([]);
 const [result, setResult] = useState<FundingComparisonView | null>(null), [busy, setBusy] = useState(false), [errors, setErrors] = useState(false);
 const [message, setMessage] = useState<'copied' | 'stale' | 'invalid' | 'error' | 'forbidden' | 'expired' | 'malformed' | 'unavailableLedger' | null>(null);
 const request = useRef<AbortController | null>(null), form = useRef<HTMLFormElement>(null), resultRegion = useRef<HTMLElement>(null);
 useEffect(() => () => request.current?.abort(), []);
 const finish = endDate(assumptions.startDate, assumptions.horizonDays), opening = normalizeAmountInput(assumptions.openingText, 'PKR');
 const isInvalidOpening = assumptions.source === 'MANUAL' && (!opening || opening.canonical.startsWith('-'));
 const change = () => { if (result) { setResult(null); setMessage('stale'); } else setMessage(null); };
 const updateAssumptions = (patch: Partial<FundingAssumptions>) => { change(); setAssumptions(previous => ({...previous, ...patch})); };
 const copyBaseline = () => { change(); setAlternative(cloneEvents(baseline, () => crypto.randomUUID())); setMessage('copied'); setConfirmCopy(false); };
 const submit = async () => {
  if (!compare || !authorized || request.current) return;
  setErrors(true);
  if (!isCalendarDate(assumptions.startDate) || !finish || isInvalidOpening || [...baseline, ...alternative].some(event => Object.keys(eventErrors(event, assumptions.startDate, finish)).length)) {
   setMessage('invalid'); requestAnimationFrame(() => form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()); return;
  }
  const controller = new AbortController(); request.current = controller; setBusy(true); setMessage(null); setResult(null);
  try {
   const response = await compare({ assumptions, baseline, alternative }, controller.signal);
   if (controller.signal.aborted || request.current !== controller) return;
   setResult(response); requestAnimationFrame(() => resultRegion.current?.focus());
  } catch (error) {
   if (controller.signal.aborted || request.current !== controller) return;
   setMessage(error instanceof ApiError ? error.status === 403 ? 'forbidden' : error.status === 401 ? 'expired' : error.status === 400 ? 'invalid' : error.status === 409 ? 'unavailableLedger' : 'error' : error instanceof Error && error.name === 'InvalidFundingResponse' ? 'malformed' : 'error');
  } finally { if (!controller.signal.aborted && request.current === controller) { request.current = null; setBusy(false); } }
 };
 return <div className="min-w-0 space-y-6">
  <p className="text-[var(--color-text-secondary)]">{copy.intro}</p>
  <Card className="border-s-4 border-s-[var(--color-brand-primary)]"><p className="text-sm">{copy.notice}</p></Card>
  {!compare && <p role="status" className="text-sm">{copy.contractPending}</p>}
  <form ref={form} noValidate aria-label={copy.assumptions} onSubmit={e => { e.preventDefault(); void submit(); }} className="min-w-0 space-y-5">
   <Card className="space-y-4"><h2 className="text-lg font-semibold">{copy.assumptions}</h2><fieldset disabled={busy || !authorized} className="grid min-w-0 gap-4 sm:grid-cols-2">
    <Input label={copy.start} type="date" dir="ltr" value={assumptions.startDate} onChange={e => updateAssumptions({startDate:e.target.value})} error={errors && !isCalendarDate(assumptions.startDate) ? copy.validDate : undefined} />
    <Select label={copy.horizon} value={String(assumptions.horizonDays)} onChange={e => updateAssumptions({horizonDays:Number(e.target.value)})} options={[7,14,30,60,90].map(days=>({value:String(days),label:`${days} ${copy.days}`}))} />
    <Input label={copy.currency} value="PKR" readOnly dir="ltr" />
    <Select label={copy.source} value={assumptions.source} onChange={e => updateAssumptions({source:e.target.value as FundingAssumptions['source']})} options={[{value:'LEDGER',label:copy.ledger},{value:'MANUAL',label:copy.manual}]} />
    <div className="sm:col-span-2">{assumptions.source === 'MANUAL' ? <><Input label={copy.opening} helperText={copy.openingHelp} value={assumptions.openingText} dir="ltr" inputMode="decimal" maxLength={128} onChange={e => updateAssumptions({openingText:e.target.value})} error={errors && isInvalidOpening ? copy.invalidOpening : undefined} />{opening && <p className="mt-2 text-xs">{opening.rounded ? copy.rounded : copy.wire}: <bdi dir="ltr">{formatPkr(opening.canonical)} PKR</bdi></p>}</> : <p className="text-sm text-[var(--color-text-secondary)]">{copy.ledgerHelp}</p>}</div>
   </fieldset>{finish && <p className="text-xs text-[var(--color-text-muted)]"><bdi dir="ltr">{assumptions.startDate} — {finish}</bdi> · Asia/Karachi</p>}</Card>
   <div className="flex flex-wrap items-center gap-3"><h2 className="text-lg font-semibold">{copy.events}</h2><Button type="button" variant="secondary" disabled={busy || !authorized} onClick={() => { if (alternative.length) setConfirmCopy(true); else copyBaseline(); }}>{copy.copy}</Button></div>
   <div className="grid min-w-0 gap-5 xl:grid-cols-2"><CashEventEditor name={copy.baseline} events={baseline} start={assumptions.startDate} end={finish} copy={copy} disabled={busy || !authorized} showErrors={errors} onChange={events => { change(); setBaseline(events); }} /><CashEventEditor name={copy.alternative} events={alternative} start={assumptions.startDate} end={finish} copy={copy} disabled={busy || !authorized} showErrors={errors} onChange={events => { change(); setAlternative(events); }} /></div>
   {message && <p role={['copied','stale'].includes(message) ? 'status' : 'alert'} className="text-sm">{copy[message]}</p>}
   <Button type="submit" disabled={!compare || !authorized} isLoading={busy}>{busy ? copy.comparing : copy.compare}</Button>
  </form>
  <Dialog isOpen={confirmCopy} onClose={() => setConfirmCopy(false)} title={copy.replaceAlternative} description={copy.replaceHelp}><div className="flex flex-wrap gap-3"><Button type="button" variant="secondary" onClick={() => setConfirmCopy(false)}>{copy.cancel}</Button><Button type="button" onClick={copyBaseline}>{copy.replace}</Button></div></Dialog>
  <section ref={resultRegion} tabIndex={-1} aria-label={copy.results} aria-live="polite" className="min-w-0 outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)]">{result ? <FundingResults result={result} copy={copy} /> : <Card><p className="text-sm text-[var(--color-text-secondary)]">{copy.pending}</p></Card>}</section>
 </div>;
}
