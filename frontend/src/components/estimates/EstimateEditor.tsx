"use client";

import { useEffect, useRef, useState } from 'react';
import { Calculator, Info, Plus, Trash2 } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { costGroups, estimatesApi, pricingModes, type CostGroup, type PricingMode, type PricingPreviewResponse } from '@/lib/api/estimates';
import { addLine, changeLine, changeMode, formatEstimateMoney, formatEstimatePercent, initialEstimate, MAX_ESTIMATE_LINES,
  previewRequest, removeLine, validateEstimate, type EstimateDraft, type EstimateErrors } from '@/lib/estimates/form';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { estimatesCopy, type EstimatesCopy } from '@/lib/i18n/translations/estimates';

type PreviewState = 'idle' | 'loading' | 'ready' | 'stale' | 'cancelled';
const muted = 'text-sm text-[var(--color-text-muted)]';
const decimalInput = { type: 'text', inputMode: 'decimal', dir: 'ltr', autoComplete: 'off' } as const;

function serverIssues(error: ApiError): EstimateErrors {
  const issues: EstimateErrors = {};
  for (const [field, code] of Object.entries(error.fieldErrors || {})) {
    issues[field] = code === 'required' ? 'required' : code === 'decimal_invalid' ? 'decimal' :
      code === 'margin_out_of_range' ? 'marginRange' : code === 'line_count_invalid' ? 'lines' :
      code === 'text_invalid' && field.endsWith('.description') ? 'description' :
      code === 'text_invalid' && field.endsWith('.unit') ? 'unit' : 'invalid';
  }
  return issues;
}

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
  const errorFor = (key: string) => errors[key] ? copy.errors[errors[key]] : undefined;

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
      <Card className="space-y-5 min-w-0">
        <div className="space-y-2"><h2 className="font-semibold text-lg">{copy.costs}</h2>
          <p className={muted}>{copy.costsHelp}</p><p className="text-xs text-[var(--color-text-muted)]">{copy.decimalHelp}</p></div>
        {draft.lines.length === 0 && <p className="rounded-lg border border-dashed p-5 text-sm">{copy.noLines}</p>}
        <div className="space-y-4">
          {draft.lines.map((line, index) => <fieldset key={line.id} data-testid={`estimate-line-${index}`} className="min-w-0 rounded-lg border border-[var(--color-border-default)] p-3 sm:p-4">
            <legend className="px-2 text-sm font-semibold">{copy.line} {index + 1}</legend>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input label={copy.description} value={line.description} maxLength={200} required data-testid={`line-description-${index}`}
                error={errorFor(`lines[${index}].description`)} onChange={event => edit(changeLine(draft, line.id, { description: event.target.value }))}/>
              <Select label={copy.group} value={line.costGroup} data-testid={`line-group-${index}`}
                error={errorFor(`lines[${index}].costGroup`)} options={costGroups.map(value => ({ value, label: copy.groups[value] }))}
                onChange={event => edit(changeLine(draft, line.id, { costGroup: event.target.value as CostGroup }))}/>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-4">
              <Input {...decimalInput} label={copy.quantity} value={line.quantity} maxLength={25} required data-testid={`line-quantity-${index}`}
                error={errorFor(`lines[${index}].quantity`)} onChange={event => edit(changeLine(draft, line.id, { quantity: event.target.value }))}/>
              <Input label={copy.unit} value={line.unit} maxLength={40} required data-testid={`line-unit-${index}`}
                error={errorFor(`lines[${index}].unit`)} onChange={event => edit(changeLine(draft, line.id, { unit: event.target.value }))}/>
              <Input {...decimalInput} label={copy.unitCost} value={line.unitCost} maxLength={25} required data-testid={`line-unit-cost-${index}`}
                error={errorFor(`lines[${index}].unitCost`)} onChange={event => edit(changeLine(draft, line.id, { unitCost: event.target.value }))}/>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3 pt-4 mt-4 border-t border-[var(--color-border-subtle)]">
              <div className="min-w-0 flex-1"><span className={muted}>{copy.lineTotal}</span>
                <p className="font-semibold tabular-nums [overflow-wrap:anywhere]" data-testid={`line-total-${index}`}><bdi>{formatEstimateMoney(result?.lines[index]?.plannedCost ?? null, locale)}</bdi></p></div>
              <Button type="button" variant="ghost" data-testid={`line-remove-${index}`} aria-label={`${copy.removeLine} ${index + 1}`}
                leftIcon={<Trash2/>} onClick={() => remove(line.id, index)}>{copy.removeLine}</Button>
            </div>
          </fieldset>)}
        </div>
        {errors.lines && <p role="alert" className="text-sm text-[var(--color-status-error)]">{errorFor('lines')}</p>}
        <div className="flex flex-wrap items-center gap-3"><Button ref={addRef} type="button" variant="secondary" leftIcon={<Plus/>}
          data-testid="estimate-add-line" disabled={draft.lines.length >= MAX_ESTIMATE_LINES} onClick={add}>{copy.addLine}</Button>
          <span className={muted}>{copy.lineLimit}</span></div>
      </Card>

      <Card className="space-y-5 min-w-0">
        <h2 className="font-semibold text-lg">{copy.pricing}</h2>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <div className="space-y-4">
            <Select label={copy.pricingMode} value={draft.pricingMode} data-testid="estimate-pricing-mode"
              options={pricingModes.map(value => ({ value, label: copy.modes[value] }))}
              onChange={event => edit(changeMode(draft, event.target.value as PricingMode))}/>
            <Input {...decimalInput} label={draft.pricingMode === 'PROPOSED_PRICE' ? copy.price : draft.pricingMode === 'TARGET_MARGIN' ? copy.marginTarget : copy.markupTarget}
              helperText={draft.pricingMode === 'PROPOSED_PRICE' ? copy.priceHelp : draft.pricingMode === 'TARGET_MARGIN' ? copy.marginHelp : copy.markupHelp}
              required maxLength={25} data-testid="estimate-pricing-value" value={draft.pricingValues[draft.pricingMode]} error={errorFor('pricingValue')}
              onChange={event => edit({ ...draft, pricingValues: { ...draft.pricingValues, [draft.pricingMode]: event.target.value } })}/>
          </div>
          <div className="rounded-lg bg-[var(--color-surface-subtle)] p-4 space-y-3 text-sm">
            <h3 className="font-semibold">{copy.distinction}</h3><p>{copy.marginExplanation}</p><p>{copy.markupExplanation}</p>
          </div>
        </div>
        <div className="max-w-xl"><Input {...decimalInput} label={copy.saleableQuantity} helperText={copy.saleableHelp}
          data-testid="estimate-saleable-quantity" value={draft.saleableQuantity} maxLength={25} error={errorFor('saleableQuantity')}
          onChange={event => edit({ ...draft, saleableQuantity: event.target.value })}/></div>
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
    {result && <div ref={resultsRef} tabIndex={-1} role="region" aria-label={copy.results} data-testid="estimate-results" className="space-y-4 outline-none">
      <div className="space-y-1"><h2 className="text-lg font-semibold">{copy.results}</h2><p className={muted}>{copy.calculated} · {copy.modes[result.pricingMode]}</p></div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card className="min-w-0 space-y-4 border-[var(--color-brand-border)] bg-[var(--color-brand-surface)]">
          <Metric label={copy.batchPrice} value={formatEstimateMoney(result.plannedBatchPrice, locale)} testId="estimate-batch-price" large/>
          <Metric label={copy.perUnit} value={formatEstimateMoney(result.plannedPerUnitPrice, locale)} testId="estimate-per-unit"
            detail={result.unavailableReasons.plannedPerUnitPrice === 'SALEABLE_QUANTITY_UNKNOWN' ? copy.unknownQuantity : result.unavailableReasons.plannedPerUnitPrice === 'ZERO_SALEABLE_QUANTITY' ? copy.zeroQuantity : undefined}/>
        </Card>
        <Card className="min-w-0 space-y-4"><Metric label={copy.contribution} value={formatEstimateMoney(result.plannedContribution, locale)} testId="estimate-contribution" detail={copy.contributionHelp}/>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Metric label={copy.margin} value={formatEstimatePercent(result.plannedMargin)} testId="estimate-margin" detail={result.unavailableReasons.plannedMargin ? copy.zeroPrice : undefined}/>
            <Metric label={copy.markup} value={formatEstimatePercent(result.plannedMarkup)} testId="estimate-markup" detail={result.unavailableReasons.plannedMarkup ? copy.zeroCost : undefined}/>
          </div>
        </Card>
      </div>
      <Card className="space-y-4 min-w-0"><h3 className="font-semibold">{copy.breakdown}</h3>
        <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {costGroups.map(group => <div key={group} className="min-w-0"><dt className={muted}>{copy.groups[group]}</dt>
            <dd data-testid={`estimate-group-${group}`} className="font-medium tabular-nums [overflow-wrap:anywhere]"><bdi>{formatEstimateMoney(result.groupSubtotals[group], locale)}</bdi></dd></div>)}
        </dl>
        <div className="border-t border-[var(--color-border-subtle)] pt-4"><Metric label={copy.totalCost} value={formatEstimateMoney(result.totalPlannedCost, locale)} testId="estimate-total-cost"/></div>
      </Card>
      <PlanningNotes copy={copy}/>
    </div>}
  </LivePage>;
}

function Metric({ label, value, testId, detail, large = false }: { label: string; value: string; testId: string; detail?: string; large?: boolean }) {
  return <div className="min-w-0 space-y-1"><p className={muted}>{label}</p>
    <p data-testid={testId} className={`${large ? 'text-2xl sm:text-3xl' : 'text-lg'} font-semibold tabular-nums [overflow-wrap:anywhere]`}><bdi>{value}</bdi></p>
    {detail && <p className="text-xs text-[var(--color-text-muted)]">{detail}</p>}</div>;
}
function PlanningNotes({ copy }: { copy: EstimatesCopy }) {
  return <div className="space-y-2 text-xs text-[var(--color-text-muted)]"><p>{copy.precision}</p><p>{copy.exclusions}</p></div>;
}
