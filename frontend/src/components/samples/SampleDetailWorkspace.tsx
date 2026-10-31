"use client";
import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { samplesApi, type SampleActivity, type SamplePlan } from '@/lib/api/samples';
import { allowedTransitions, canEdit, costCategories, createPayload, draftFromPlan, formatMoney, initialMetadata, initialPlan, mapFieldErrors, metadataFromActivity, planPayload, transitionBlocked, validateMetadata, validatePlan, type FieldErrors, type MetadataDraft, type SampleStatus } from '@/lib/samples/form';
import { useLanguage } from '@/lib/i18n/context';
import { samplesCopy } from '@/lib/i18n/translations/samples';
import { FailureNotice, focusInvalid, MetadataFields, PlanningBanner, PlanSummary } from './SampleShared';
import { SamplePlanEditor } from './SamplePlanEditor';

export function SampleDetailWorkspace({ activityId }: { activityId: string }) {
  const { business } = useSession(); return <Workspace key={`${business?.businessId ?? 'none'}:${activityId}`} activityId={activityId} />;
}
function Workspace({ activityId }: { activityId: string }) {
  const { locale } = useLanguage(); const copy = samplesCopy(locale); const router = useRouter(); const { can, business } = useSession();
  const businessId = business?.businessId; const canRead = can('FINANCIAL_DATA_READ'); const canWrite = can('RECORD_CREATE_UPDATE');
  const [activity, setActivity] = useState<SampleActivity | null>(null); const [plans, setPlans] = useState<SamplePlan[]>([]);
  const [metadata, setMetadata] = useState(initialMetadata); const [metadataEditing, setMetadataEditing] = useState(false); const [metadataDirty, setMetadataDirty] = useState(false);
  const [draft, setDraft] = useState(initialPlan); const [editorOpen, setEditorOpen] = useState(false); const [planDirty, setPlanDirty] = useState(false);
  const [targetStatus, setTargetStatus] = useState<SampleStatus | ''>(''); const [reason, setReason] = useState('');
  const [metadataErrors, setMetadataErrors] = useState<FieldErrors>({}); const [planErrors, setPlanErrors] = useState<FieldErrors>({});
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [failure, setFailure] = useState<unknown>(null); const [saved, setSaved] = useState(false);
  const readRequest = useRef<AbortController | null>(null); const writeRequest = useRef<AbortController | null>(null); const metadataForm = useRef<HTMLFormElement>(null);
  const conflict = failure instanceof ApiError && failure.status === 409;
  const load = useCallback(async (signal: AbortSignal) => {
    const [summary, history] = await Promise.all([samplesApi.summary(activityId, signal), samplesApi.listPlans(activityId, signal)]);
    if (signal.aborted) return;
    const current = summary.activity;
    if (summary.businessId !== businessId || summary.financialBasis !== 'PLANNED_ONLY' || summary.baseCurrency !== 'PKR' || current.businessId !== businessId || current.id !== activityId || history.some(plan => plan.businessId !== businessId || plan.activityId !== activityId || plan.financialBasis !== 'PLANNED_ONLY')) throw new ApiError(502, 'Invalid sample planning response.');
    setActivity(current); setPlans(history); setMetadata(metadataFromActivity(current)); setMetadataEditing(false); setMetadataDirty(false);
    setDraft(current.latestDraftPlan ? draftFromPlan(current.latestDraftPlan) : initialPlan());
    setEditorOpen(Boolean(current.latestDraftPlan) || !current.activeApprovedPlan); setPlanDirty(false); setMetadataErrors({}); setPlanErrors({});
    setTargetStatus(''); setReason('');
  }, [activityId, businessId, setActivity, setPlans, setMetadata, setMetadataEditing, setMetadataDirty, setDraft, setEditorOpen, setPlanDirty, setMetadataErrors, setPlanErrors, setTargetStatus, setReason]);
  const reload = useCallback(async () => {
    const controller = new AbortController(); readRequest.current?.abort(); readRequest.current = controller;
    setLoading(true); setFailure(null); setSaved(false);
    try { await load(controller.signal); } catch (error) { if (!controller.signal.aborted) { setFailure(error); setActivity(null); setPlans([]); } }
    finally { if (!controller.signal.aborted) setLoading(false); }
  }, [load, setLoading, setFailure, setSaved, setActivity, setPlans]);
  useEffect(() => {
    let active = true;
    if (canRead && businessId && !isDemoMode) { queueMicrotask(() => { if (active) void reload(); }); }
    return () => { active = false; readRequest.current?.abort(); writeRequest.current?.abort(); };
  }, [canRead, businessId, reload]);
  useEffect(() => {
    if (!planDirty && !metadataDirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn);
  }, [planDirty, metadataDirty]);
  const reloadLatest = () => { if ((!planDirty && !metadataDirty) || window.confirm(copy.reloadConfirm)) void reload(); };
  const mutate = async (action: (signal: AbortSignal) => Promise<unknown>, deleted = false, fields: 'metadata' | 'plan' = 'plan') => {
    if (busy || !canWrite || conflict) return;
    const controller = new AbortController(); writeRequest.current = controller; setBusy(true); setFailure(null); setSaved(false);
    try {
      await action(controller.signal); if (controller.signal.aborted) return;
      if (deleted) { setMetadataDirty(false); setPlanDirty(false); router.push('/samples'); return; }
      await load(controller.signal); if (!controller.signal.aborted) setSaved(true);
    } catch (error) {
      if (controller.signal.aborted || (error instanceof Error && error.name === 'AbortError')) return;
      setFailure(error);
      if (error instanceof ApiError && error.fieldErrors) {
        if (fields === 'metadata') { setMetadataErrors(mapFieldErrors(error.fieldErrors)); focusInvalid(metadataForm.current); }
        else setPlanErrors(mapFieldErrors(error.fieldErrors));
      }
    } finally { if (!controller.signal.aborted) setBusy(false); }
  };
  const saveMetadata = async (event: React.FormEvent) => {
    event.preventDefault(); if (!activity) return;
    const issues = validateMetadata(metadata); setMetadataErrors(issues);
    if (Object.keys(issues).length) { focusInvalid(metadataForm.current); return; }
    await mutate(signal => samplesApi.update(activityId, { ...createPayload(metadata), expectedVersion: activity.version }, signal), false, 'metadata');
  };
  const savePlan = async () => {
    if (!activity) return;
    const issues = validatePlan(draft, activity.recoveryIntent); setPlanErrors(issues);
    if (Object.keys(issues).length) return;
    const payload = planPayload(draft, activity.recoveryIntent); const existing = activity.latestDraftPlan;
    await mutate(signal => existing ? samplesApi.updatePlan(activityId, existing.id, { ...payload, expectedPlanVersion: existing.version }, signal)
      : samplesApi.createPlan(activityId, { ...payload, expectedActivityVersion: activity.version }, signal));
  };
  const updateMetadata = (patch: Partial<MetadataDraft>) => { setMetadata(value => ({ ...value, ...patch })); setMetadataDirty(true); setMetadataErrors({}); setSaved(false); };
  if (isDemoMode || !canRead) return <LivePage title={copy.detailTitle}><Card>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</Card></LivePage>;
  const editable = activity ? canEdit(activity.status, canWrite) : false;
  const openDraft = activity?.latestDraftPlan;
  const currentPlan = openDraft ?? activity?.activeApprovedPlan;
  const disabled = busy || conflict;
  return <LivePage title={activity?.name ?? copy.detailTitle} actions={<Link href="/samples"><Button variant="secondary" className="max-w-[180px] sm:max-w-none h-auto min-h-10 py-2">{copy.back}</Button></Link>}>
    <div className="space-y-5 min-w-0" data-testid="sample-detail-view">
      <PlanningBanner />
      {loading && <Card role="status" data-testid="sample-loading">{copy.loading}</Card>}
      <FailureNotice error={failure} reload={reloadLatest} />
      {saved && <p role="status" className="text-sm text-emerald-800" data-testid="sample-saved-notice">{copy.savedNotice}</p>}
      {!loading && activity && <>
        {!canWrite && <Card data-testid="samples-readonly-banner">{copy.readOnly}</Card>}
        {activity.status === 'ARCHIVED' ? <Card>{copy.archived}</Card> : !canEdit(activity.status, true) && <Card>{copy.closed}</Card>}
        <Card className="space-y-4 min-w-0" data-testid="sample-metadata">
          <h2 className="text-lg font-semibold break-all" data-testid="sample-activity-name">{activity.name}</h2>
          <div className="flex flex-wrap gap-2 text-sm"><bdi dir="ltr" className="font-mono font-semibold break-all min-w-0 max-w-full">{activity.code}</bdi><span>{copy.kinds[activity.kind]}</span><span data-testid="sample-status">{copy.statuses[activity.status]}</span><span>{copy.intents[activity.recoveryIntent]}</span></div>
          {metadataEditing ? <form ref={metadataForm} onSubmit={saveMetadata} noValidate className="space-y-4">
            <MetadataFields draft={metadata} update={updateMetadata} errors={metadataErrors} intentLocked={activity.revisionCount > 0} disabled={disabled} />
            <div className="flex flex-wrap gap-3"><Button type="submit" disabled={disabled} data-testid="sample-save-metadata">{copy.save}</Button>
              <Button type="button" variant="secondary" disabled={busy} onClick={() => { setMetadata(metadataFromActivity(activity)); setMetadataEditing(false); setMetadataDirty(false); setMetadataErrors({}); }}>{copy.cancel}</Button></div>
          </form> : <>
            {activity.description && <p className="text-sm break-words">{activity.description}</p>}
            {activity.projectId && <p className="text-sm" data-testid="sample-project-context"><Link href={`/projects/${encodeURIComponent(activity.projectId)}`} className="underline">{copy.projectContext}</Link> · {copy.projectHelp}</p>}
            {(activity.startDate || activity.targetEndDate) && <p className="text-sm" dir="ltr">{activity.startDate ?? '—'} → {activity.targetEndDate ?? '—'}</p>}
            {editable && <div className="flex flex-wrap gap-3"><Button type="button" variant="secondary" disabled={disabled || planDirty} onClick={() => setMetadataEditing(true)} data-testid="sample-edit-metadata">{copy.edit}</Button>
              {activity.canDeleteDraftActivity && <Button type="button" variant="destructive" disabled={disabled || planDirty} onClick={() => { if (window.confirm(copy.deleteActivityConfirm)) void mutate(signal => samplesApi.deleteDraft(activityId, activity.version, signal), true); }} data-testid="sample-delete-draft">{copy.deleteActivity}</Button>}
            </div>}
          </>}
        </Card>
        <Card className="space-y-3" data-testid="sample-planned-summary">
          <h2 className="font-semibold">{openDraft ? copy.draftSummary : copy.approvedSummary}</h2>
          {currentPlan ? <PlanSummary plan={currentPlan} /> : <p>{copy.noApproved}</p>}
          {currentPlan && <details><summary className="cursor-pointer text-sm font-medium">{copy.categoryTotals}</summary><dl className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-3">
            {costCategories.map(category => <div key={category} className="min-w-0 text-sm"><dt>{copy.categories[category]}</dt><dd dir="ltr" className="break-all">{formatMoney(currentPlan.categorySubtotals[category], locale)}</dd></div>)}
          </dl></details>}
        </Card>
        {openDraft && activity.activeApprovedPlan && <Card className="space-y-3"><h2 className="font-semibold">{copy.approvedSummary}</h2><PlanSummary plan={activity.activeApprovedPlan} testPrefix="sample-approved-summary" /></Card>}
        {editable && !metadataEditing && <>
          {editorOpen ? <SamplePlanEditor draft={draft} update={value => { setDraft(value); setPlanDirty(true); setPlanErrors({}); setSaved(false); }} errors={planErrors} intent={activity.recoveryIntent} busy={disabled} dirty={planDirty || !openDraft} save={savePlan} cancel={() => { setDraft(openDraft ? draftFromPlan(openDraft) : initialPlan()); setPlanDirty(false); setPlanErrors({}); setEditorOpen(Boolean(openDraft)); }} /> : <Card className="space-y-3">
            <p className="text-sm">{copy.copyNotice}</p><Button type="button" disabled={disabled} data-testid="sample-new-revision" onClick={() => { setDraft(activity.activeApprovedPlan ? draftFromPlan(activity.activeApprovedPlan) : initialPlan()); setEditorOpen(true); setPlanDirty(true); setSaved(false); }}>{activity.activeApprovedPlan ? copy.newRevision : copy.firstPlan}</Button>
          </Card>}
          {openDraft && <Card className="space-y-3"><p className="text-sm">{planDirty ? copy.approveSaveFirst : copy.immutable}</p><div className="flex flex-wrap gap-3">
            <Button type="button" disabled={disabled || planDirty} data-testid="sample-plan-approve" onClick={() => { if (window.confirm(copy.approveConfirm)) void mutate(signal => samplesApi.approvePlan(activityId, openDraft.id, { expectedActivityVersion: activity.version, expectedPlanVersion: openDraft.version }, signal)); }}>{copy.approve}</Button>
            <Button type="button" variant="destructive" disabled={disabled} data-testid="sample-plan-discard" onClick={() => { if (window.confirm(copy.discardConfirm)) void mutate(signal => samplesApi.deletePlan(activityId, openDraft.id, activity.version, openDraft.version, signal)); }}>{copy.discardPlan}</Button>
          </div></Card>}
        </>}
        <Card className="space-y-4" data-testid="sample-lifecycle"><h2 className="font-semibold">{copy.lifecycle}</h2><p className="text-sm">{copy.completedMeaning}</p>
          {canWrite && allowedTransitions(activity.status).length > 0 && <form onSubmit={event => { event.preventDefault(); if (targetStatus) void mutate(signal => samplesApi.transition(activityId, { targetStatus, reason: reason.trim() || null, expectedVersion: activity.version }, signal)); }} className="space-y-4">
            <Select label={copy.targetStatus} value={targetStatus} disabled={disabled || planDirty || metadataEditing} options={[{ value: '', label: copy.targetStatus }, ...allowedTransitions(activity.status).map(status => ({ value: status, label: copy.statuses[status], disabled: transitionBlocked(status, Boolean(openDraft)) }))]} onChange={event => setTargetStatus(event.target.value as SampleStatus | '')} data-testid="sample-transition-select" />
            <Input label={copy.transitionReason} value={reason} maxLength={500} onChange={event => setReason(event.target.value)} disabled={disabled || planDirty || metadataEditing} />
            {openDraft && <p className="text-sm text-amber-800">{copy.draftBlocksClose}</p>}
            <Button type="submit" disabled={disabled || !targetStatus || planDirty || metadataEditing || transitionBlocked(targetStatus as SampleStatus, Boolean(openDraft))} data-testid="sample-transition-submit">{copy.transition}</Button>
          </form>}
        </Card>
        <section className="space-y-4" data-testid="sample-plan-history"><h2 className="text-lg font-semibold">{copy.history}</h2><p className="text-sm text-[var(--color-text-muted)]">{copy.immutable}</p>
          {!plans.length && <Card>{copy.noHistory}</Card>}
          {plans.map(plan => <Card key={plan.id} className="space-y-4 min-w-0" data-testid={`sample-plan-revision-${plan.revision}`}>
            <h3 className="font-semibold">{copy.revision} <bdi dir="ltr">{plan.revision}</bdi> · <span data-plan-status={plan.status}>{copy.planStatuses[plan.status]}</span></h3>
            <PlanSummary plan={plan} testPrefix={`sample-history-${plan.revision}`} />
            <div className="text-xs text-[var(--color-text-muted)] space-y-1"><p>{copy.created}: <bdi dir="ltr" className="break-all">{plan.createdAt}</bdi></p>{plan.approvedAt && <p>{copy.approved}: <bdi dir="ltr" className="break-all">{plan.approvedAt}</bdi></p>}{plan.supersededAt && <p>{copy.superseded}: <bdi dir="ltr" className="break-all">{plan.supersededAt}</bdi></p>}</div>
            <details><summary className="cursor-pointer text-sm font-medium">{copy.inspect}</summary><div className="space-y-3 mt-3">{plan.lines.map(line => <dl key={line.id} className="min-w-0 rounded-md border border-[var(--color-border-subtle)] p-3 space-y-2">
              <div className="text-sm"><dt className="font-medium">{copy.categories[line.category]} · <bdi>{line.lineNumber}</bdi></dt><dd className="break-words">{line.description}</dd></div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-sm"><div><dt>{copy.quantity}</dt><dd className="break-all"><bdi dir="ltr">{line.quantity}</bdi> {line.unit}</dd></div><div><dt>{copy.unitCost}</dt><dd dir="ltr" className="break-all">{formatMoney(line.unitCost, locale)}</dd></div><div><dt>{copy.lineCost}</dt><dd dir="ltr" className="break-all">{formatMoney(line.plannedLineCost, locale)}</dd></div></div>
            </dl>)}</div></details>
          </Card>)}
        </section>
      </>}
    </div>
  </LivePage>;
}