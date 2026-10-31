"use client";
import { useEffect, useState } from 'react';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { projectsApi, type ProjectResponse } from '@/lib/api/projects';
import { ApiError } from '@/lib/api/client';
import type { SamplePlan } from '@/lib/api/samples';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { samplesCopy } from '@/lib/i18n/translations/samples';
import { formatMoney, recoveryIntents, sampleKinds, type FieldErrors, type MetadataDraft, type RecoveryIntent, type SampleKind } from '@/lib/samples/form';

export function PlanningBanner() {
  const { locale } = useLanguage(); const copy = samplesCopy(locale);
  return <div className="rounded-lg border border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] p-4 space-y-2" data-testid="samples-planning-banner">
    <p className="font-semibold text-[var(--color-brand-primary)]">{copy.planned}</p><p className="text-sm">{copy.disclosure}</p>
  </div>;
}
export function FailureNotice({ error, reload }: { error: unknown; reload?: () => void }) {
  const { locale, t } = useLanguage(); const copy = samplesCopy(locale);
  if (!error) return null;
  const conflict = error instanceof ApiError && error.status === 409;
  const detail = error instanceof ApiError ? copy.conflicts[error.code as keyof typeof copy.conflicts] : undefined;
  return <Card role="alert" className="space-y-3 border-amber-300" data-testid={conflict ? 'sample-conflict' : 'sample-error'}>
    {conflict && <p className="font-semibold">{copy.conflictTitle}</p>}
    <p>{detail ?? localizedError(error, t)}</p>
    {reload && <Button type="button" variant="secondary" onClick={reload} data-testid="sample-reload">{copy.reload}</Button>}
  </Card>;
}
export function MetadataFields({ draft, update, errors, intentLocked = false, disabled = false }: {
  draft: MetadataDraft; update: (patch: Partial<MetadataDraft>) => void; errors: FieldErrors; intentLocked?: boolean; disabled?: boolean;
}) {
  const { locale } = useLanguage(); const copy = samplesCopy(locale); const { business } = useSession();
  const businessId = business?.businessId;
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [projectFailure, setProjectFailure] = useState(false); const [loading, setLoading] = useState(true); const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    queueMicrotask(() => {
      if (controller.signal.aborted) return;
      setLoading(true); setProjectFailure(false);
      projectsApi.list({}, controller.signal).then(response => {
        if (controller.signal.aborted) return;
        if (response.businessId !== businessId) throw new ApiError(502, 'Invalid project list response.');
        setProjects(response.items.filter(item => item.status !== 'ARCHIVED')); setLoading(false);
      }).catch(error => {
        if (controller.signal.aborted || error?.name === 'AbortError') return;
        setProjects([]); setProjectFailure(true); setLoading(false);
      });
    });
    return () => controller.abort();
  }, [businessId, attempt]);
  const err = (field: string) => errors[field] ? copy.errors[errors[field]] : undefined;
  const retainedProject = draft.projectId && !projects.some(project => project.id === draft.projectId);
  return <fieldset disabled={disabled} className="min-w-0 space-y-4">
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 min-w-0">
      <Input label={copy.code} helperText={copy.codeHelp} dir="ltr" value={draft.code} onChange={e => update({ code: e.target.value })} required maxLength={40} error={err('code')} data-testid="sample-code-input" />
      <Select label={copy.kind} value={draft.kind} options={sampleKinds.map(kind => ({ value: kind, label: copy.kinds[kind] }))} onChange={e => update({ kind: e.target.value as SampleKind })} error={err('kind')} data-testid="sample-kind-select" />
    </div>
    <Input label={copy.name} value={draft.name} onChange={e => update({ name: e.target.value })} required maxLength={160} error={err('name')} data-testid="sample-name-input" />
    <Input label={copy.description} value={draft.description} onChange={e => update({ description: e.target.value })} maxLength={1000} error={err('description')} data-testid="sample-description-input" />
    <Select label={copy.recoveryIntent} value={draft.recoveryIntent} options={recoveryIntents.map(intent => ({ value: intent, label: copy.intents[intent] }))}
      onChange={e => update({ recoveryIntent: e.target.value as RecoveryIntent })} disabled={intentLocked} helperText={intentLocked ? copy.intentLocked : copy.intentHelp} error={err('recoveryIntent')} data-testid="sample-recovery-intent-select" />
    <Select label={copy.project} value={draft.projectId} disabled={loading} options={[
      { value: '', label: copy.noProject },
      ...(retainedProject ? [{ value: draft.projectId, label: copy.projectRetained, disabled: true }] : []),
      ...projects.map(project => ({ value: project.id, label: `${project.code} — ${project.name}` })),
    ]} onChange={e => update({ projectId: e.target.value })} helperText={copy.projectHelp} error={err('projectId')} data-testid="sample-project-select" />
    {loading && <p role="status" className="text-sm">{copy.loading}</p>}
    {projectFailure && <div role="alert" className="space-y-2 text-sm"><p>{copy.projectUnavailable}</p><Button type="button" variant="secondary" onClick={() => setAttempt(v => v + 1)}>{copy.retry}</Button></div>}
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 min-w-0">
      <Input label={copy.startDate} type="date" dir="ltr" value={draft.startDate} onChange={e => update({ startDate: e.target.value })} error={err('startDate')} data-testid="sample-start-date-input" />
      <Input label={copy.targetEndDate} type="date" dir="ltr" value={draft.targetEndDate} onChange={e => update({ targetEndDate: e.target.value })} error={err('targetEndDate')} data-testid="sample-end-date-input" />
    </div>
  </fieldset>;
}
export function PlanSummary({ plan, testPrefix = 'sample-summary' }: { plan: SamplePlan; testPrefix?: string }) {
  const { locale } = useLanguage(); const copy = samplesCopy(locale);
  const keys = ['plannedCost', 'plannedRecovery', 'plannedContribution'] as const;
  return <div className="space-y-3">
    <div className="grid grid-cols-1 md:grid-cols-3 gap-3 min-w-0">
      {keys.map(key => <div className="min-w-0 rounded-lg border border-[var(--color-border-subtle)] bg-[var(--color-surface-canvas)] p-4" key={key}>
        <p className="text-xs font-medium text-[var(--color-text-muted)]">{copy[key]}</p>
        <p dir="ltr" className="mt-2 text-lg font-semibold break-all" data-testid={`${testPrefix}-${key}`}>{formatMoney(plan[key], locale)}</p>
        <p className="text-[10px] font-semibold text-[var(--color-brand-primary)] mt-1">{copy.planned}</p>
      </div>)}
    </div>
    {plan.plannedRecovery === null && <p className="text-sm text-[var(--color-text-muted)]" data-testid={`${testPrefix}-unavailable`}>{copy.unknownRecovery}</p>}
    <p className="text-xs text-[var(--color-text-muted)]">{copy.authoritative} · {copy.intents[plan.recoveryIntent]}</p>
  </div>;
}
export function focusInvalid(form: HTMLFormElement | null) { requestAnimationFrame(() => form?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()); }