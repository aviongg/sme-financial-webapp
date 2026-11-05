"use client";

import { useEffect, useRef, useState } from 'react';
import { Calculator, Info } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { estimatesApi, type PricingPreviewResponse } from '@/lib/api/estimates';
import { addLine, initialEstimate, previewRequest, removeLine, validateEstimate, type EstimateDraft, type EstimateErrors } from '@/lib/estimates/form';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { estimatesCopy } from '@/lib/i18n/translations/estimates';
import { EstimateCostLines, EstimatePricingInputs, EstimateResults, serverIssues } from './EstimateInputs';

type PreviewState = 'idle' | 'loading' | 'ready' | 'stale' | 'cancelled';
const muted = 'text-sm text-[var(--color-text-muted)]';

export function EstimateEditor() {
  const { locale, t } = useLanguage();
  const copy = estimatesCopy(locale);
  const { can, business } = useSession();
  const [draft, setDraft] = useState<EstimateDraft>(initialEstimate);
  const [errors, setErrors] = useState<EstimateErrors>({});
  const [failure, setFailure] = useState<unknown>(null);
  const [result, setResult] = useState<PricingPreviewResponse | null>(null);
  const [state, setState] = useState<PreviewState>('idle');
  const [dirty, setDirty] = useState(false);
  const nextLine = useRef(1);
  const revision = useRef(0);
  const activeRequest = useRef<AbortController | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const addRef = useRef<HTMLButtonElement>(null);
  const resultsRef = useRef<HTMLDivElement>(null);

  useEffect(() => () => { revision.current++; activeRequest.current?.abort(); }, []);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const edit = (next: EstimateDraft) => {
    revision.current++;
    activeRequest.current?.abort();
    activeRequest.current = null;
    setDraft(next); setDirty(true); setErrors({}); setFailure(null); setResult(null);
    setState(state === 'idle' ? 'idle' : 'stale');
  };
  const focusLine = (index: number) => requestAnimationFrame(() => {
    const input = formRef.current?.querySelector<HTMLInputElement>(`[data-testid="line-description-${index}"]`);
    if (input) input.focus(); else addRef.current?.focus();
  });
  const add = () => {
    const next = addLine(draft, String(nextLine.current++));
    if (next === draft) return;
    edit(next); focusLine(next.lines.length - 1);
  };
  const remove = (id: string, index: number) => {
    const next = removeLine(draft, id);
    edit(next); focusLine(Math.min(index, next.lines.length - 1));
  };
  const focusInvalid = () => requestAnimationFrame(() => {
    const field = formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]');
    if (field) field.focus(); else addRef.current?.focus();
  });
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (state === 'loading') return;
    const issues = validateEstimate(draft);
    setErrors(issues); setFailure(null); setResult(null);
    if (Object.keys(issues).length) { setState('stale'); focusInvalid(); return; }
    const controller = new AbortController(), ticket = ++revision.current;
    activeRequest.current?.abort(); activeRequest.current = controller;
    setState('loading');
    try {
      const preview = await estimatesApi.preview(previewRequest(draft), controller.signal);
      if (controller.signal.aborted || ticket !== revision.current) return;
      if (preview.businessId !== business?.businessId || preview.currency !== 'PKR' || preview.planningOnly !== true)
        throw new ApiError(502, 'Invalid planning preview response.');
      setResult(preview); setState('ready');
      requestAnimationFrame(() => resultsRef.current?.focus());
    } catch (error) {
      if (controller.signal.aborted || ticket !== revision.current) return;
      setFailure(error); setState('stale');
      if (error instanceof ApiError && error.fieldErrors) { setErrors(serverIssues(error)); focusInvalid(); }
    } finally {
      if (ticket === revision.current) activeRequest.current = null;
    }
  };
  const cancel = () => {
    revision.current++; activeRequest.current?.abort(); activeRequest.current = null;
    setState('cancelled'); setResult(null);
  };

  if (isDemoMode || !can('FINANCIAL_DATA_READ')) return <LivePage title={copy.title}>
    <Card><p>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</p></Card>
  </LivePage>;

  return <LivePage title={copy.title}>
    <div className="space-y-3">
      <p className="max-w-3xl text-[var(--color-text-secondary)]">{copy.intro}</p>
      <div className="rounded-lg border border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] p-4 flex gap-3">
        <Info className="w-5 h-5 shrink-0 mt-0.5 text-[var(--color-brand-primary)]" aria-hidden="true"/>
        <div className="min-w-0 space-y-1"><p className="font-semibold text-[var(--color-brand-primary)]">{copy.planning}</p>
          <p className="text-sm">{copy.disclosure}</p><p className={muted}>{copy.limits}</p></div>
      </div>
    </div>

    <form ref={formRef} onSubmit={submit} noValidate className="space-y-6" data-testid="estimate-form">
      <EstimateCostLines draft={draft} errors={errors} copy={copy} edit={edit} locale={locale} result={result} add={add} remove={remove} addRef={addRef}/>

      <Card className="space-y-5 min-w-0">
        <h2 className="font-semibold text-lg">{copy.pricing}</h2>
        <EstimatePricingInputs draft={draft} errors={errors} copy={copy} edit={edit}/>
        {Object.keys(errors).length > 0 && <p role="alert" data-testid="estimate-validation" className="text-sm text-[var(--color-status-error)]">{copy.validation}</p>}
        {failure !== null && <p role="alert" data-testid="estimate-error" className="text-sm text-[var(--color-status-error)]">{localizedError(failure, t)}</p>}
        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" leftIcon={<Calculator/>} size="lg" isLoading={state === 'loading'} data-testid="estimate-preview">
            {state === 'loading' ? copy.calculating : copy.preview}</Button>
          {state === 'loading' && <Button type="button" variant="secondary" onClick={cancel}>{copy.cancelCalculation}</Button>}
        </div>
        <p className="text-xs text-[var(--color-text-muted)]">{copy.unsaved}</p>
      </Card>
    </form>

    <div aria-live="polite" aria-atomic="true" data-testid="estimate-status">
      {state !== 'ready' && <p role="status" className={muted}>{state === 'loading' ? copy.calculating : state === 'stale' ? copy.stale : state === 'cancelled' ? copy.cancelled : copy.ready}</p>}
    </div>
    {result && <EstimateResults ref={resultsRef} result={result} copy={copy} locale={locale}/>}
  </LivePage>;
}
