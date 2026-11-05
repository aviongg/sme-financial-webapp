"use client";

import type { ReactNode, Ref, RefObject } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import type { ApiError } from '@/lib/api/client';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { costGroups, pricingModes, type CostGroup, type PricingMode, type PricingPreviewResponse } from '@/lib/api/estimates';
import { changeLine, changeMode, formatEstimateMoney, formatEstimatePercent, MAX_ESTIMATE_LINES, type EstimateDraft, type EstimateErrors } from '@/lib/estimates/form';
import type { EstimatesCopy } from '@/lib/i18n/translations/estimates';

const muted = 'text-sm text-[var(--color-text-muted)]';
const decimalInput = { type: 'text', inputMode: 'decimal', dir: 'ltr', autoComplete: 'off' } as const;

/** Maps the stable f06-preview-v1 field reason codes onto the localized editor messages. */
export function serverIssues(error: ApiError): EstimateErrors {
  const issues: EstimateErrors = {};
  for (const [field, code] of Object.entries(error.fieldErrors || {})) {
    issues[field] = code === 'required' ? 'required' : code === 'decimal_invalid' ? 'decimal' :
      code === 'margin_out_of_range' ? 'marginRange' : code === 'line_count_invalid' ? 'lines' :
      code === 'text_invalid' && field.endsWith('.description') ? 'description' :
      code === 'text_invalid' && field.endsWith('.unit') ? 'unit' : 'invalid';
  }
  return issues;
}

interface Shared {
  draft: EstimateDraft;
  errors: EstimateErrors;
  copy: EstimatesCopy;
  edit: (next: EstimateDraft) => void;
}

/** The Phase A cost-line editor, shared by the stateless preview and persisted DRAFT revisions. */
export function EstimateCostLines({ draft, errors, copy, edit, locale, result, add, remove, addRef }: Shared & {
  locale: 'en' | 'ur';
  result: PricingPreviewResponse | null;
  add: () => void;
  remove: (id: string, index: number) => void;
  addRef: RefObject<HTMLButtonElement | null>;
}) {
  const errorFor = (key: string) => errors[key] ? copy.errors[errors[key]] : undefined;
  return <Card className="space-y-5 min-w-0">
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
  </Card>;
}

/** The Phase A pricing approach, value and saleable-quantity inputs. */
export function EstimatePricingInputs({ draft, errors, copy, edit }: Shared) {
  const errorFor = (key: string) => errors[key] ? copy.errors[errors[key]] : undefined;
  return <>
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
  </>;
}

/** Server-calculated f06-preview-v1 results, identical for a preview and a stored revision. */
export function EstimateResults({ result, copy, locale, ref, heading, subtitle, notes }: {
  result: PricingPreviewResponse;
  copy: EstimatesCopy;
  locale: 'en' | 'ur';
  ref?: Ref<HTMLDivElement>;
  heading?: string;
  subtitle?: string;
  notes?: ReactNode;
}) {
  return <div ref={ref} tabIndex={-1} role="region" aria-label={heading ?? copy.results} data-testid="estimate-results" className="space-y-4 outline-none">
    <div className="space-y-1"><h2 className="text-lg font-semibold">{heading ?? copy.results}</h2>
      <p className={muted}>{subtitle ?? copy.calculated} · {copy.modes[result.pricingMode]}</p></div>
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
    {notes ?? <div className="space-y-2 text-xs text-[var(--color-text-muted)]"><p>{copy.precision}</p><p>{copy.exclusions}</p></div>}
  </div>;
}

function Metric({ label, value, testId, detail, large = false }: { label: string; value: string; testId: string; detail?: string; large?: boolean }) {
  return <div className="min-w-0 space-y-1"><p className={muted}>{label}</p>
    <p data-testid={testId} className={`${large ? 'text-2xl sm:text-3xl' : 'text-lg'} font-semibold tabular-nums [overflow-wrap:anywhere]`}><bdi>{value}</bdi></p>
    {detail && <p className="text-xs text-[var(--color-text-muted)]">{detail}</p>}</div>;
}
