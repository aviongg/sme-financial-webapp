"use client";

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { AlertTriangle, ArrowDown, ArrowLeft, ArrowUp, Plus, Trash2 } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { inventoryApi, type ValuationPreviewResponse, type ValuationState } from '@/lib/api/inventory';
import {
  initialPreviewDraft,
  mapInventoryFieldErrors,
  MAX_PREVIEW_STEPS,
  newPreviewStep,
  previewPayload,
  validatePreviewDraft,
  type InventoryValidationErrors,
  type PreviewDraft,
  type PreviewStepDraft,
  type ValuationStepType,
} from '@/lib/inventory/form';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { inventoryCopy } from '@/lib/i18n/translations/inventory';

export function ValuationPreviewSandbox() {
  const { business } = useSession();
  // Inputs never survive a business switch.
  return <ValuationPreviewSandboxInner key={business?.businessId ?? 'none'} />;
}

function ValuationPreviewSandboxInner() {
  const { locale, t } = useLanguage();
  const copy = inventoryCopy(locale);
  const preview = copy.preview;
  const { can, business } = useSession();
  const canRead = can('FINANCIAL_DATA_READ');
  const [draft, setDraft] = useState<PreviewDraft>(initialPreviewDraft);
  const [errors, setErrors] = useState<InventoryValidationErrors>({});
  const [result, setResult] = useState<ValuationPreviewResponse | null>(null);
  const [stale, setStale] = useState(false);
  const [failure, setFailure] = useState('');
  const [calculating, setCalculating] = useState(false);
  const activeRequest = useRef<AbortController | null>(null);

  useEffect(() => () => activeRequest.current?.abort(), []);

  if (isDemoMode || !canRead) {
    return (
      <LivePage title={preview.title}>
        <Card><p>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</p></Card>
      </LivePage>
    );
  }

  const edit = (next: PreviewDraft, clearedKeys: string[] = []) => {
    setDraft(next);
    if (result) setStale(true);
    if (clearedKeys.length) setErrors(current => {
      const copyErrors = { ...current };
      clearedKeys.forEach(key => delete copyErrors[key]);
      return copyErrors;
    });
  };
  // Step indexes shift when steps move, so any step-scoped error is cleared on structural edits.
  const structural = (steps: PreviewStepDraft[]) => {
    setErrors(current => Object.fromEntries(Object.entries(current).filter(([key]) => !key.startsWith('steps'))));
    edit({ ...draft, steps });
  };
  const updateStep = (index: number, patch: Partial<PreviewStepDraft>) => edit(
    { ...draft, steps: draft.steps.map((step, i) => (i === index ? { ...step, ...patch } : step)) },
    Object.keys(patch).map(key => `steps[${index}].${key}`)
  );
  const addStep = (type: ValuationStepType) => draft.steps.length < MAX_PREVIEW_STEPS && structural([...draft.steps, newPreviewStep(type)]);
  const removeStep = (index: number) => structural(draft.steps.filter((_, i) => i !== index));
  const moveStep = (index: number, offset: number) => {
    const target = index + offset;
    if (target < 0 || target >= draft.steps.length) return;
    const steps = [...draft.steps];
    [steps[index], steps[target]] = [steps[target], steps[index]];
    structural(steps);
  };
  const fieldError = (key: string) => (errors[key] ? copy.errors[errors[key]!] : undefined);

  const calculate = async (event: React.FormEvent) => {
    event.preventDefault();
    const validation = validatePreviewDraft(draft);
    setErrors(validation);
    if (Object.values(validation).some(Boolean)) {
      setFailure(copy.form.validation);
      return;
    }
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    setCalculating(true);
    setFailure('');
    try {
      const response = await inventoryApi.previewValuation(previewPayload(draft), controller.signal);
      if (controller.signal.aborted) return;
      if (response.authoritative !== false || response.persisted !== false || response.businessId !== business?.businessId
        || response.mode !== 'PLANNING_VALUATION_PREVIEW') {
        throw new ApiError(502, 'Invalid valuation preview response.');
      }
      setResult(response);
      setStale(false);
    } catch (error) {
      if (controller.signal.aborted || (error as Error)?.name === 'AbortError') return;
      if (error instanceof ApiError && error.status === 400) setErrors(mapInventoryFieldErrors(error.fieldErrors));
      setFailure(localizedError(error, t));
    } finally {
      if (activeRequest.current === controller) {
        activeRequest.current = null;
        setCalculating(false);
      }
    }
  };

  const average = (state: ValuationState) => state.averageUnitCost ?? preview.noStockAverage;

  return (
    <LivePage title={preview.title} actions={
      <Link href="/inventory" data-testid="valuation-back-link">
        <Button type="button" variant="secondary">
          <ArrowLeft className="w-4 h-4 me-1.5 rtl:rotate-180" aria-hidden="true" />
          {copy.nav}
        </Button>
      </Link>
    }>
      <div className="space-y-4 min-w-0" data-testid="valuation-preview-sandbox">
        <div role="note" data-testid="valuation-preview-banner"
          className="rounded-lg border-2 border-amber-400 bg-amber-50 p-4 flex gap-3 min-w-0 text-amber-950">
          <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" aria-hidden="true" />
          <div className="min-w-0 space-y-1.5">
            <p className="font-bold tracking-wide [overflow-wrap:anywhere]">{preview.banner}</p>
            <p className="text-sm">{preview.disclosure}</p>
            <p className="text-xs font-semibold">{preview.nonAuthoritative}</p>
          </div>
        </div>
        <div className="rounded-lg border border-[var(--color-border-default)] bg-[var(--color-surface-subtle)] p-3 text-sm space-y-1 min-w-0"
          data-testid="valuation-policy-notice">
          <p className="font-semibold">{preview.policyNotice}</p>
          <p className="text-[var(--color-text-secondary)]">{preview.policyDetail}</p>
          <p className="text-xs text-[var(--color-text-muted)]">{preview.unsaved}</p>
        </div>

        <form onSubmit={calculate} noValidate className="space-y-4 min-w-0">
          <Card className="space-y-4 min-w-0">
            <h2 className="text-base font-semibold">{preview.opening}</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 min-w-0">
              <div className="min-w-0">
                <Input label={preview.openingQuantity} value={draft.openingQuantity} dir="ltr" inputMode="decimal"
                  onChange={e => edit({ ...draft, openingQuantity: e.target.value }, ['openingQuantity'])}
                  error={fieldError('openingQuantity')} data-testid="valuation-opening-quantity" />
              </div>
              <div className="min-w-0">
                <Input label={preview.openingValue} value={draft.openingValue} dir="ltr" inputMode="decimal"
                  onChange={e => edit({ ...draft, openingValue: e.target.value }, ['openingValue'])}
                  error={fieldError('openingValue')} data-testid="valuation-opening-value" />
              </div>
            </div>
            <p className="text-xs text-[var(--color-text-muted)]">{preview.openingHelp}</p>
          </Card>

          <Card className="space-y-4 min-w-0">
            <div className="space-y-1">
              <h2 className="text-base font-semibold">{preview.steps}</h2>
              <p className="text-sm text-[var(--color-text-secondary)]">{preview.stepsHelp}</p>
            </div>
            {draft.steps.length === 0 && <p className="text-sm text-[var(--color-text-muted)]">{preview.noSteps}</p>}
            <ol className="space-y-3 min-w-0">
              {draft.steps.map((step, index) => (
                <li key={step.key} className="rounded-md border border-[var(--color-border-subtle)] p-3 space-y-3 min-w-0"
                  data-testid={`valuation-step-${index}`}>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm font-semibold">
                      {preview.step} {index + 1} · {preview.types[step.type]}
                    </p>
                    <div className="flex items-center gap-1">
                      <Button type="button" size="sm" variant="ghost" aria-label={preview.moveUp} disabled={index === 0}
                        onClick={() => moveStep(index, -1)}><ArrowUp className="w-4 h-4" aria-hidden="true" /></Button>
                      <Button type="button" size="sm" variant="ghost" aria-label={preview.moveDown} disabled={index === draft.steps.length - 1}
                        onClick={() => moveStep(index, 1)}><ArrowDown className="w-4 h-4" aria-hidden="true" /></Button>
                      <Button type="button" size="sm" variant="ghost" aria-label={preview.remove} onClick={() => removeStep(index)}
                        data-testid={`valuation-remove-step-${index}`}><Trash2 className="w-4 h-4" aria-hidden="true" /></Button>
                    </div>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 min-w-0">
                    <div className="min-w-0">
                      <Input label={preview.quantity} value={step.quantity} dir="ltr" inputMode="decimal"
                        onChange={e => updateStep(index, { quantity: e.target.value })}
                        error={fieldError(`steps[${index}].quantity`)} data-testid={`valuation-step-${index}-quantity`} />
                    </div>
                    {step.type === 'RECEIPT' ? (
                      <div className="min-w-0">
                        <Input label={preview.receiptValue} value={step.value} dir="ltr" inputMode="decimal"
                          onChange={e => updateStep(index, { value: e.target.value })}
                          error={fieldError(`steps[${index}].value`)} data-testid={`valuation-step-${index}-value`} />
                      </div>
                    ) : (
                      <p className="self-end text-xs text-[var(--color-text-muted)] pb-2">{preview.issueValueDerived}</p>
                    )}
                  </div>
                </li>
              ))}
            </ol>
            <div className="flex flex-wrap items-center gap-2">
              <Button type="button" variant="secondary" onClick={() => addStep('RECEIPT')} disabled={draft.steps.length >= MAX_PREVIEW_STEPS}
                data-testid="valuation-add-receipt">
                <Plus className="w-4 h-4 me-1.5" aria-hidden="true" />{preview.addReceipt}
              </Button>
              <Button type="button" variant="secondary" onClick={() => addStep('ISSUE')} disabled={draft.steps.length >= MAX_PREVIEW_STEPS}
                data-testid="valuation-add-issue">
                <Plus className="w-4 h-4 me-1.5" aria-hidden="true" />{preview.addIssue}
              </Button>
              <span className="text-xs text-[var(--color-text-muted)]">{preview.stepLimit}</span>
            </div>
          </Card>

          {failure && <p role="alert" className="text-sm text-[var(--color-status-error)]" data-testid="valuation-error">{failure}</p>}
          <Button type="submit" isLoading={calculating} disabled={calculating} data-testid="valuation-calculate">
            {calculating ? preview.calculating : preview.calculate}
          </Button>
        </form>

        {!result && <p className="text-sm text-[var(--color-text-muted)]">{preview.ready}</p>}
        {result && (
          <Card className="space-y-4 min-w-0" data-testid="valuation-results" aria-live="polite">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-base font-semibold">{preview.results}</h2>
              <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold uppercase tracking-wider bg-amber-50 text-amber-900 border border-amber-300"
                data-testid="valuation-result-badge">
                {preview.nonAuthoritative}
              </span>
            </div>
            {stale && <p role="status" className="text-sm font-medium text-[var(--color-status-warning)]" data-testid="valuation-stale">{preview.stale}</p>}
            <p className="text-xs text-[var(--color-text-muted)]">
              {preview.policyVersion}: <span dir="ltr" data-testid="valuation-policy-version">{result.policy.version}</span>
            </p>
            <ol className="space-y-2 min-w-0">
              {result.steps.map(step => (
                <li key={step.index} className="rounded-md border border-[var(--color-border-subtle)] p-3 min-w-0"
                  data-testid={`valuation-result-step-${step.index}`}>
                  <p className="text-sm font-semibold">
                    {preview.step} {step.index + 1} · {preview.types[step.type]} · <span dir="ltr">{step.quantity}</span>
                  </p>
                  <dl className="mt-2 grid grid-cols-1 sm:grid-cols-4 gap-2 text-sm min-w-0">
                    <Figure label={step.type === 'RECEIPT' ? preview.receiptValue : preview.issueValue}
                      value={(step.type === 'RECEIPT' ? step.receiptValue : step.issueValue) ?? '—'}
                      testId={`valuation-result-step-${step.index}-movement`} />
                    <Figure label={preview.resultQuantity} value={step.after.quantity} />
                    <Figure label={preview.resultValue} value={step.after.value} testId={`valuation-result-step-${step.index}-value`} />
                    <Figure label={preview.resultAverage} value={average(step.after)} numeric={step.after.averageUnitCost !== null} />
                  </dl>
                  {step.fullDepletion && <p className="mt-2 text-xs font-medium text-[var(--color-text-secondary)]">{preview.fullDepletion}</p>}
                </li>
              ))}
            </ol>
            <div className="rounded-md bg-[var(--color-surface-canvas)] border border-[var(--color-border-default)] p-3 space-y-2 min-w-0"
              data-testid="valuation-closing">
              <p className="text-sm font-semibold">{preview.closing}</p>
              <dl className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-sm min-w-0">
                <Figure label={preview.resultQuantity} value={result.closing.quantity} testId="valuation-closing-quantity" />
                <Figure label={preview.resultValue} value={result.closing.value} testId="valuation-closing-value" />
                <Figure label={preview.resultAverage} value={average(result.closing)} numeric={result.closing.averageUnitCost !== null}
                  testId="valuation-closing-average" />
              </dl>
            </div>
            <div className="space-y-1 text-sm min-w-0">
              <p className="font-semibold">{preview.totals}</p>
              <p>{preview.received}: <span dir="ltr">{result.totals.receivedQuantity} · {result.totals.receivedValue}</span></p>
              <p>{preview.issued}: <span dir="ltr" data-testid="valuation-total-issued">{result.totals.issuedQuantity} · {result.totals.issuedValue}</span></p>
              {result.totals.valueConserved && <p className="text-xs text-[var(--color-text-muted)]">{preview.conserved}</p>}
            </div>
          </Card>
        )}
      </div>
    </LivePage>
  );
}

function Figure({ label, value, numeric = true, testId }: { label: string; value: string; numeric?: boolean; testId?: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-[var(--color-text-muted)]">{label}</dt>
      {/* Only the number is isolated as LTR, so the figure still aligns with its label in RTL. */}
      <dd className="font-semibold [overflow-wrap:anywhere]">
        <span dir={numeric ? 'ltr' : undefined} data-testid={testId}>{value}</span>
      </dd>
    </div>
  );
}
