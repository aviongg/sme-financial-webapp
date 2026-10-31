"use client";
import { useRef } from 'react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useLanguage } from '@/lib/i18n/context';
import { samplesCopy } from '@/lib/i18n/translations/samples';
import { costCategories, emptyLine, MAX_PLAN_LINES, type CostCategory, type FieldErrors, type LineInput, type PlanDraft, type RecoveryIntent } from '@/lib/samples/form';
import { focusInvalid } from './SampleShared';

export function SamplePlanEditor({ draft, update, errors, intent, busy, dirty, save, cancel }: {
  draft: PlanDraft; update: (draft: PlanDraft) => void; errors: FieldErrors; intent: RecoveryIntent; busy: boolean; dirty: boolean; save: () => Promise<void>; cancel: () => void;
}) {
  const { locale } = useLanguage(); const copy = samplesCopy(locale); const form = useRef<HTMLFormElement>(null); const nextId = useRef(100);
  const err = (key: string) => errors[key] ? copy.errors[errors[key]] : undefined;
  const updateLine = (id: string, patch: Partial<LineInput>) => update({ ...draft, lines: draft.lines.map(line => line.id === id ? { ...line, ...patch } : line) });
  return <form ref={form} noValidate onSubmit={async event => { event.preventDefault(); await save(); focusInvalid(form.current); }} data-testid="sample-plan-editor">
    <Card className="space-y-5 min-w-0">
      <h2 className="text-lg font-semibold">{copy.planEditor}</h2>
      <p className="text-sm text-[var(--color-text-muted)]">{copy.decimalHelp}</p>
      <p className="text-sm font-medium" data-testid="sample-plan-edit-status">{dirty ? copy.unsavedNotice : copy.authoritative}</p>
      <fieldset disabled={busy} className="space-y-4 min-w-0">
        <Input label={copy.plannedRecovery} value={intent === 'FREE' ? '0' : draft.plannedRecovery} disabled={intent === 'FREE'} dir="ltr" inputMode="decimal"
          helperText={intent === 'FREE' ? copy.freeRecovery : copy.recoveryHelp} error={err('plannedRecovery')}
          onChange={e => update({ ...draft, plannedRecovery: e.target.value })} data-testid="sample-planned-recovery-input" />
        {errors.lines && <p role="alert" className="text-sm text-red-700">{copy.errors[errors.lines]}</p>}
        {draft.lines.map((line, index) => <fieldset key={line.id} className="min-w-0 rounded-lg border border-[var(--color-border-default)] p-3 sm:p-4 space-y-4" data-testid={`sample-line-${index}`}>
          <legend className="text-sm font-semibold px-1">{copy.line} <bdi dir="ltr">{index + 1}</bdi></legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 min-w-0">
            <Select label={copy.category} value={line.category} options={costCategories.map(category => ({ value: category, label: copy.categories[category] }))}
              onChange={e => updateLine(line.id, { category: e.target.value as CostCategory })} error={err(`lines[${index}].category`)} data-testid={`sample-line-${index}-category`} />
            <Input label={copy.lineDescription} value={line.description} maxLength={200} required onChange={e => updateLine(line.id, { description: e.target.value })} error={err(`lines[${index}].description`)} data-testid={`sample-line-${index}-description`} />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 min-w-0">
            <Input label={copy.quantity} dir="ltr" inputMode="decimal" value={line.quantity} required onChange={e => updateLine(line.id, { quantity: e.target.value })} error={err(`lines[${index}].quantity`)} data-testid={`sample-line-${index}-quantity`} />
            <Input label={copy.unit} value={line.unit} maxLength={40} required onChange={e => updateLine(line.id, { unit: e.target.value })} error={err(`lines[${index}].unit`)} data-testid={`sample-line-${index}-unit`} />
            <Input label={copy.unitCost} dir="ltr" inputMode="decimal" value={line.unitCost} required onChange={e => updateLine(line.id, { unitCost: e.target.value })} error={err(`lines[${index}].unitCost`)} data-testid={`sample-line-${index}-unit-cost`} />
          </div>
          <Button type="button" variant="ghost" onClick={() => update({ ...draft, lines: draft.lines.filter(value => value.id !== line.id) })} data-testid={`sample-line-${index}-remove`}>{copy.removeLine}</Button>
        </fieldset>)}
        <div className="flex flex-wrap gap-3">
          <Button type="button" variant="secondary" disabled={draft.lines.length >= MAX_PLAN_LINES} onClick={() => update({ ...draft, lines: [...draft.lines, emptyLine(`new-${nextId.current++}`)] })} data-testid="sample-add-line">{copy.addLine}</Button>
          <Button type="submit" data-testid="sample-plan-save">{busy ? copy.saving : copy.savePlan}</Button>
          <Button type="button" variant="ghost" onClick={cancel} data-testid="sample-plan-cancel-edit">{copy.cancel}</Button>
        </div>
      </fieldset>
    </Card>
  </form>;
}