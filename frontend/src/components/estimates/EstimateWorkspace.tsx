"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { AlertTriangle, ArrowRightLeft, Calculator, CheckCircle2, CopyPlus, Save } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import {
  estimateRecordsApi, estimatesApi, isTrustedEstimate, isTrustedRevision, type ConversionIssue, type EstimateConversionResultResponse,
  type EstimateDetailResponse, type EstimateRevisionResponse, type PricingPreviewResponse,
} from '@/lib/api/estimates';
import { projectsApi, type ProjectResponse } from '@/lib/api/projects';
import {
  addLine, draftFromResult, formatEstimateMoney, identityPayload, initialEstimate, previewRequest, removeLine, validateEstimate,
  validateIdentity, type EstimateDraft, type EstimateErrors, type EstimateIdentityDraft, type IdentityIssue,
} from '@/lib/estimates/form';
import { useLanguage } from '@/lib/i18n/context';
import { estimatePlanningCopy, type EstimatePlanningCopy } from '@/lib/i18n/translations/estimatePlanning';
import { estimatesCopy, type EstimatesCopy } from '@/lib/i18n/translations/estimates';
import { EstimateCostLines, EstimatePricingInputs, EstimateResults, serverIssues } from './EstimateInputs';
import { PlanningOnlyBanner, revisionBadgeClass } from './EstimateRegister';

type Load = 'loading' | 'ready' | 'notFound' | 'forbidden' | 'error';
type Busy = 'preview' | 'save' | 'identity' | 'approve' | 'discard' | 'archive' | 'delete' | 'next' | 'convert' | null;
type DialogKind = 'approve' | 'discard' | 'archive' | 'delete' | 'convert' | null;
const muted = 'text-sm text-[var(--color-text-muted)]';
const emptyIdentity: EstimateIdentityDraft = { code: '', name: '', description: '' };

function when(value: string | null, locale: 'en' | 'ur'): string {
  if (!value) return '—';
  return new Date(value).toLocaleString(locale === 'ur' ? 'ur-PK' : 'en-GB', { dateStyle: 'medium', timeStyle: 'short' });
}
function eligible(project: ProjectResponse): boolean {
  return (project.status === 'DRAFT' || project.status === 'ACTIVE') && project.latestDraftBudget === null;
}

export function EstimateWorkspace({ estimateId }: { estimateId?: string }) {
  const { locale } = useLanguage();
  const copy = estimatesCopy(locale);
  const plan = estimatePlanningCopy(locale);
  const router = useRouter();
  const { can, business } = useSession();
  const canRead = can('FINANCIAL_DATA_READ');
  const canWrite = can('RECORD_CREATE_UPDATE');
  const activeBusinessId = business?.businessId ?? null;
  const isNew = !estimateId;

  const [detail, setDetail] = useState<EstimateDetailResponse | null>(null);
  const [load, setLoad] = useState<Load>('loading');
  const [attempt, setAttempt] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [identity, setIdentity] = useState<EstimateIdentityDraft>(emptyIdentity);
  const [identityErrors, setIdentityErrors] = useState<Partial<Record<keyof EstimateIdentityDraft, IdentityIssue>>>({});
  const [draft, setDraft] = useState<EstimateDraft>(initialEstimate);
  const [errors, setErrors] = useState<EstimateErrors>({});
  const [preview, setPreview] = useState<PricingPreviewResponse | null>(null);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState<Busy>(null);
  const [dialog, setDialog] = useState<DialogKind>(null);
  const [conflict, setConflict] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [projects, setProjects] = useState<ProjectResponse[] | null>(null);
  const [targetId, setTargetId] = useState('');
  const [converted, setConverted] = useState<EstimateConversionResultResponse | null>(null);
  const nextLine = useRef(1);
  const requestId = useRef(0);
  const addRef = useRef<HTMLButtonElement>(null);
  const formRef = useRef<HTMLFormElement>(null);

  /** Adopt a server detail; edits are reset only here, so a 409 keeps the user's unsaved inputs. */
  const accept = useCallback((next: EstimateDetailResponse, preferred?: string | null) => {
    setDetail(next);
    const revisions = next.revisions;
    const chosen = revisions.find(revision => revision.id === preferred)
      ?? revisions.find(revision => revision.status === 'DRAFT') ?? revisions.find(revision => revision.status === 'APPROVED') ?? revisions[0] ?? null;
    setSelectedId(chosen?.id ?? null);
    setIdentity({ code: next.estimate.code, name: next.estimate.name, description: next.estimate.description ?? '' });
    if (chosen?.status === 'DRAFT') setDraft(draftFromResult(chosen.result, `r${chosen.revision}`));
    setIdentityErrors({}); setErrors({}); setPreview(null); setDirty(false); setConflict(false);
  }, []);

  const fetchDetail = useCallback(async (id: string, signal?: AbortSignal) => {
    const response = await estimateRecordsApi.get(id, signal);
    if (!activeBusinessId || !isTrustedEstimate(response.estimate, activeBusinessId) || response.estimate.id !== id
      || !response.revisions.every(revision => isTrustedRevision(revision, activeBusinessId, id))) throw new ApiError(502, 'Invalid estimate response.');
    return response;
  }, [activeBusinessId]);

  // Load (or reset) whenever the estimate or active business changes; never show another business's estimate.
  useEffect(() => {
    const ticket = ++requestId.current;
    const controller = new AbortController();
    queueMicrotask(() => {
      if (ticket !== requestId.current) return;
      setDialog(null); setNotice(null); setActionError(null); setConverted(null); setProjects(null); setTargetId('');
      if (isDemoMode || !canRead || !activeBusinessId) { setLoad('ready'); return; }
      if (!estimateId) {
        setDetail(null); setSelectedId(null); setIdentity(emptyIdentity); setDraft(initialEstimate());
        setErrors({}); setIdentityErrors({}); setPreview(null); setDirty(false); setConflict(false); setLoad('ready');
        return;
      }
      setLoad('loading');
      fetchDetail(estimateId, controller.signal).then(response => {
        if (ticket !== requestId.current) return;
        accept(response); setLoad('ready');
      }).catch(error => {
        if (ticket !== requestId.current || error?.name === 'AbortError') return;
        setDetail(null);
        setLoad(error instanceof ApiError && error.status === 404 ? 'notFound' : error instanceof ApiError && error.status === 403 ? 'forbidden' : 'error');
      });
    });
    return () => controller.abort();
  }, [accept, activeBusinessId, attempt, canRead, estimateId, fetchDetail]);

  const estimate = detail?.estimate ?? null;
  const selected = detail?.revisions.find(revision => revision.id === selectedId) ?? null;
  const active = isNew || estimate?.status === 'ACTIVE';
  const editing = canWrite && active && (isNew || selected?.status === 'DRAFT');
  const approvedRevision = detail?.revisions.find(revision => revision.status === 'APPROVED') ?? null;
  const draftRevision = detail?.revisions.find(revision => revision.status === 'DRAFT') ?? null;
  const showProjects = Boolean(selected && (selected.conversion || (selected.status === 'APPROVED' && selected.conversionReadiness.convertible)));

  // Project names for lineage, and eligible targets for conversion; read-only F05 data.
  useEffect(() => {
    if (!showProjects || !activeBusinessId || isDemoMode) return;
    const controller = new AbortController();
    projectsApi.list({}, controller.signal).then(response => {
      if (controller.signal.aborted || response.businessId !== activeBusinessId) return;
      setProjects(response.items);
    }).catch(() => { if (!controller.signal.aborted) setProjects([]); });
    return () => controller.abort();
  }, [activeBusinessId, showProjects, attempt, selectedId]);

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const edit = (next: EstimateDraft) => { setDraft(next); setDirty(true); setErrors({}); setPreview(null); setNotice(null); };
  const editIdentity = (patch: Partial<EstimateIdentityDraft>) => { setIdentity(current => ({ ...current, ...patch })); setDirty(true); setIdentityErrors({}); setNotice(null); };
  const focusLine = (index: number) => requestAnimationFrame(() => {
    const input = formRef.current?.querySelector<HTMLInputElement>(`[data-testid="line-description-${index}"]`);
    if (input) input.focus(); else addRef.current?.focus();
  });
  const add = () => { const next = addLine(draft, `n${nextLine.current++}`); if (next === draft) return; edit(next); focusLine(next.lines.length - 1); };
  const remove = (id: string, index: number) => { const next = removeLine(draft, id); edit(next); focusLine(Math.min(index, next.lines.length - 1)); };
  const focusInvalid = () => requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());

  const select = (revision: EstimateRevisionResponse) => {
    setSelectedId(revision.id); setNotice(null); setActionError(null); setConverted(null); setTargetId('');
    if (revision.status === 'DRAFT') { setDraft(draftFromResult(revision.result, `r${revision.revision}`)); setPreview(null); setErrors({}); setDirty(false); }
  };

  const fail = (error: unknown) => {
    if (error instanceof DOMException && error.name === 'AbortError') return;
    if (error instanceof ApiError) {
      if (error.status === 409 && error.code?.startsWith('stale_')) { setConflict(true); setActionError(null); return; }
      if (error.status === 400 && error.fieldErrors) {
        const fields = error.fieldErrors;
        setErrors(serverIssues(error));
        setIdentityErrors({ ...(fields.code ? { code: 'codeInvalid' as const } : {}), ...(fields.name ? { name: 'nameRequired' as const } : {}),
          ...(fields.description ? { description: 'descriptionLength' as const } : {}) });
        focusInvalid();
      }
      setActionError((error.code && plan.errors[error.code]) || (error.status === 403 ? plan.forbidden : plan.requestFailed));
      return;
    }
    setActionError(plan.requestFailed);
  };
  const refresh = async (preferred?: string | null) => { if (estimateId) accept(await fetchDetail(estimateId), preferred); };
  const run = async (kind: Busy, action: () => Promise<void>) => {
    if (busy) return;
    setBusy(kind); setActionError(null); setNotice(null);
    try { await action(); } catch (error) { fail(error); } finally { setBusy(null); }
  };

  const validInputs = (): boolean => {
    const issues = validateEstimate(draft);
    setErrors(issues);
    if (Object.keys(issues).length) { focusInvalid(); return false; }
    return true;
  };
  const calculate = () => run('preview', async () => {
    if (!validInputs()) return;
    const result = await estimatesApi.preview(previewRequest(draft));
    if (result.businessId !== activeBusinessId || result.planningOnly !== true) throw new ApiError(502, 'Invalid planning preview response.');
    setPreview(result);
  });
  const save = () => run('save', async () => {
    if (isNew) {
      const issues = validateIdentity(identity);
      setIdentityErrors(issues);
      if (!validInputs() || Object.keys(issues).length) { focusInvalid(); return; }
      const created = await estimateRecordsApi.create(identityPayload(identity), previewRequest(draft));
      setDirty(false);
      router.replace(`/estimates/${created.estimate.id}`);
      return;
    }
    if (!selected || !estimateId || !validInputs()) return;
    await estimateRecordsApi.updateRevision(estimateId, selected.id, previewRequest(draft), selected.version);
    await refresh(selected.id);
    setNotice(plan.saved);
  });
  const saveIdentity = () => run('identity', async () => {
    if (!estimate || !estimateId) return;
    const issues = validateIdentity(identity);
    setIdentityErrors(issues);
    if (Object.keys(issues).length) { focusInvalid(); return; }
    accept(await estimateRecordsApi.update(estimateId, identityPayload(identity), estimate.version), selectedId);
    setNotice(plan.saved);
  });
  const approve = () => run('approve', async () => {
    if (!selected || !estimateId) return;
    setDialog(null);
    await estimateRecordsApi.approve(estimateId, selected.id, selected.version);
    await refresh(selected.id);
    setNotice(plan.approved);
  });
  const discard = () => run('discard', async () => {
    if (!selected || !estimateId) return;
    setDialog(null);
    await estimateRecordsApi.removeRevision(estimateId, selected.id, selected.version);
    await refresh(null);
  });
  const nextRevision = () => run('next', async () => {
    if (!approvedRevision || !estimate || !estimateId) return;
    const created = await estimateRecordsApi.createRevision(estimateId, previewRequest(draftFromResult(approvedRevision.result, 'copy')), estimate.version);
    await refresh(created.id);
  });
  const archive = () => run('archive', async () => {
    if (!estimate || !estimateId) return;
    setDialog(null);
    accept(await estimateRecordsApi.archive(estimateId, estimate.version), selectedId);
  });
  const deleteEstimate = () => run('delete', async () => {
    if (!estimate || !estimateId) return;
    setDialog(null);
    await estimateRecordsApi.remove(estimateId, estimate.version);
    setDirty(false);
    router.push('/estimates');
  });
  const target = projects?.find(project => project.id === targetId) ?? null;
  const convert = () => run('convert', async () => {
    if (!selected || !estimateId || !target) return;
    setDialog(null);
    try {
      const result = await estimateRecordsApi.convert(estimateId, selected.id, target.id, target.version);
      setConverted(result);
      await refresh(selected.id);
      setConverted(result);
    } catch (error) {
      // A precision rejection is reflected by the revision's own readiness, so reload it to show the exact values.
      if (error instanceof ApiError && error.status === 422) await refresh(selected.id).catch(() => {});
      throw error;
    }
  });

  if (isDemoMode || !canRead) return <LivePage title={plan.registerTitle}>
    <Card data-testid="estimate-no-access"><p>{isDemoMode ? plan.demoUnavailable : plan.noAccess}</p></Card>
  </LivePage>;
  if (isNew && !canWrite) return <LivePage title={plan.newTitle}>
    <Card data-testid="estimate-create-forbidden" className="space-y-3"><p>{plan.createForbidden}</p>
      <Link href="/estimates" className="text-sm underline">{plan.backToRegister}</Link></Card>
  </LivePage>;
  if (load === 'loading') return <LivePage title={plan.registerTitle}><Card><p role="status" data-testid="estimate-loading">{plan.loading}</p></Card></LivePage>;
  if (load !== 'ready' || (!isNew && !detail)) return <LivePage title={plan.registerTitle}>
    <Card className="space-y-3" data-testid={load === 'notFound' ? 'estimate-not-found' : 'estimate-load-error'}>
      <p role="alert">{load === 'notFound' ? plan.notFound : load === 'forbidden' ? plan.forbidden : plan.requestFailed}</p>
      <div className="flex flex-wrap gap-3">
        {load === 'error' && <Button type="button" variant="secondary" onClick={() => setAttempt(value => value + 1)}>{plan.retry}</Button>}
        <Link href="/estimates" className="text-sm underline self-center">{plan.backToRegister}</Link>
      </div>
    </Card>
  </LivePage>;

  const shownResult = editing ? (preview ?? (dirty || isNew ? null : selected?.result ?? null)) : selected?.result ?? null;
  const identityError = (key: keyof EstimateIdentityDraft) => identityErrors[key] ? plan.identityErrors[identityErrors[key]!] : undefined;
  const title = isNew ? plan.newTitle : `${estimate!.code} · ${estimate!.name}`;

  return <LivePage title={title} actions={!isNew && estimate ? <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold border ${revisionBadgeClass(estimate.status)}`}
    data-testid="estimate-status-badge">{plan.statuses[estimate.status]}</span> : undefined}>
    <div className="space-y-6" data-testid="estimate-workspace">
      <Link href="/estimates" className="text-sm underline" data-testid="estimate-back">{plan.backToRegister}</Link>
      <PlanningOnlyBanner copy={plan}/>
      {!canWrite && <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" data-testid="estimate-readonly-banner">{plan.readOnlyRole}</div>}
      {estimate?.status === 'ARCHIVED' && <div className="rounded-lg border border-slate-300 bg-slate-50 p-3 text-sm" data-testid="estimate-archived-banner">{plan.archivedBanner}</div>}
      {conflict && <div role="alert" className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 flex flex-wrap items-center gap-3" data-testid="estimate-conflict-banner">
        <AlertTriangle className="w-5 h-5 shrink-0" aria-hidden="true"/><span className="flex-1 min-w-0">{plan.conflict}</span>
        <Button type="button" variant="secondary" size="sm" data-testid="estimate-conflict-reload" onClick={() => run(null, () => refresh(selectedId))}>{plan.reload}</Button>
      </div>}
      {actionError && <p role="alert" className="text-sm text-[var(--color-status-error)]" data-testid="estimate-action-error">{actionError}</p>}
      <div aria-live="polite" data-testid="estimate-notice" className="text-sm text-emerald-700 empty:hidden">{notice}</div>

      <form ref={formRef} noValidate onSubmit={event => { event.preventDefault(); save(); }} className="space-y-6" data-testid="estimate-form">
        <Card className="space-y-4 min-w-0" data-testid="estimate-identity">
          <h2 className="font-semibold text-lg">{plan.details}</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input label={plan.code} helperText={plan.codeHelp} value={identity.code} maxLength={40} dir="ltr" autoComplete="off" required
              data-testid="estimate-code" readOnly={!canWrite || !active} error={identityError('code')} onChange={event => editIdentity({ code: event.target.value })}/>
            <Input label={plan.name} value={identity.name} maxLength={160} required data-testid="estimate-name" readOnly={!canWrite || !active}
              error={identityError('name')} onChange={event => editIdentity({ name: event.target.value })}/>
          </div>
          <Input label={plan.description} value={identity.description} maxLength={1000} data-testid="estimate-description" readOnly={!canWrite || !active}
            error={identityError('description')} onChange={event => editIdentity({ description: event.target.value })}/>
          {!isNew && canWrite && active && <Button type="button" variant="secondary" leftIcon={<Save className="w-4 h-4" aria-hidden="true"/>}
            isLoading={busy === 'identity'} data-testid="estimate-save-details" onClick={saveIdentity}>{plan.saveDetails}</Button>}
        </Card>

        {!isNew && detail && <RevisionHistory detail={detail} selectedId={selectedId} plan={plan} copy={copy} locale={locale} onSelect={select}/>}

        {editing && <>
          <div className="space-y-1"><h2 className="text-lg font-semibold" data-testid="estimate-editor-heading">
            {isNew ? plan.draftEditor : `${plan.revision(selected!.revision)} · ${plan.revisionStatuses.DRAFT}`}</h2>
            <p className={muted}>{plan.draftHelp}</p></div>
          <EstimateCostLines draft={draft} errors={errors} copy={copy} edit={edit} locale={locale} result={shownResult} add={add} remove={remove} addRef={addRef}/>
          <Card className="space-y-5 min-w-0">
            <h2 className="font-semibold text-lg">{copy.pricing}</h2>
            <EstimatePricingInputs draft={draft} errors={errors} copy={copy} edit={edit}/>
            {Object.keys(errors).length > 0 && <p role="alert" data-testid="estimate-validation" className="text-sm text-[var(--color-status-error)]">{copy.validation}</p>}
            <div className="flex flex-wrap items-center gap-3">
              <Button type="button" variant="secondary" leftIcon={<Calculator className="w-4 h-4" aria-hidden="true"/>} isLoading={busy === 'preview'}
                data-testid="estimate-preview" onClick={calculate}>{busy === 'preview' ? plan.calculating : plan.calculate}</Button>
              <Button type="submit" leftIcon={<Save className="w-4 h-4" aria-hidden="true"/>} isLoading={busy === 'save'} data-testid="estimate-save">
                {busy === 'save' ? plan.saving : isNew ? plan.saveEstimate : plan.saveDraft}</Button>
              {!isNew && selected && <>
                <Button type="button" leftIcon={<CheckCircle2 className="w-4 h-4" aria-hidden="true"/>} disabled={dirty || busy !== null}
                  data-testid="estimate-approve" onClick={() => setDialog('approve')}>{plan.approve}</Button>
                <Button type="button" variant="ghost" disabled={busy !== null} data-testid="estimate-discard" onClick={() => setDialog('discard')}>{plan.discardDraft}</Button>
              </>}
            </div>
            {dirty && !isNew && <p className="text-xs text-[var(--color-text-muted)]" data-testid="estimate-unsaved">{plan.previewStale}</p>}
          </Card>
        </>}
      </form>

      {!editing && selected && <ReadOnlyLines revision={selected} copy={copy} plan={plan} locale={locale}/>}
      {shownResult && <EstimateResults result={shownResult} copy={copy} locale={locale}
        heading={editing && preview ? copy.results : plan.storedResult} subtitle={editing && preview ? copy.calculated : plan.storedSubtitle}
        notes={<div className="space-y-2 text-xs text-[var(--color-text-muted)]"><p>{plan.precisionNote}</p><p>{plan.exclusionsNote}</p></div>}/>}

      {!isNew && canWrite && active && !draftRevision && approvedRevision && <Card className="space-y-2" data-testid="estimate-next-revision-card">
        <p className={muted}>{plan.nextRevisionHelp}</p>
        <Button type="button" variant="secondary" leftIcon={<CopyPlus className="w-4 h-4" aria-hidden="true"/>} isLoading={busy === 'next'}
          data-testid="estimate-next-revision" onClick={nextRevision}>{plan.nextRevision}</Button>
      </Card>}

      {selected && selected.status !== 'DRAFT' && <ConversionPanel revision={selected} plan={plan} locale={locale} projects={projects}
        canConvert={canWrite && active} targetId={targetId} setTargetId={setTargetId} busy={busy === 'convert'}
        converted={converted} onReview={() => setDialog('convert')}/>}

      {!isNew && canWrite && active && estimate && <div className="flex flex-wrap gap-3 border-t border-[var(--color-border-subtle)] pt-4">
        <Button type="button" variant="secondary" disabled={busy !== null} data-testid="estimate-archive" onClick={() => setDialog('archive')}>{plan.archive}</Button>
        {!estimate.hasApprovedHistory && <Button type="button" variant="destructive" disabled={busy !== null} data-testid="estimate-delete"
          onClick={() => setDialog('delete')}>{plan.deleteEstimate}</Button>}
      </div>}
    </div>

    <Dialog isOpen={dialog === 'approve'} onClose={() => setDialog(null)} title={plan.approveTitle}>
      <div className="space-y-4" data-testid="estimate-approve-dialog"><p className="text-sm">{plan.approveBody}</p>
        <p className="text-sm font-semibold">{plan.approveNoPosting}</p>
        <DialogActions plan={plan} confirm={plan.approveConfirm} testId="estimate-approve-confirm" onConfirm={approve} onCancel={() => setDialog(null)}/></div>
    </Dialog>
    <Dialog isOpen={dialog === 'discard'} onClose={() => setDialog(null)} title={plan.discardTitle}>
      <div className="space-y-4" data-testid="estimate-discard-dialog"><p className="text-sm">{plan.discardBody}</p>
        <DialogActions plan={plan} confirm={plan.discardConfirm} testId="estimate-discard-confirm" onConfirm={discard} onCancel={() => setDialog(null)}/></div>
    </Dialog>
    <Dialog isOpen={dialog === 'archive'} onClose={() => setDialog(null)} title={plan.archiveTitle}>
      <div className="space-y-4" data-testid="estimate-archive-dialog"><p className="text-sm">{plan.archiveBody}</p>
        <DialogActions plan={plan} confirm={plan.archiveConfirm} testId="estimate-archive-confirm" onConfirm={archive} onCancel={() => setDialog(null)}/></div>
    </Dialog>
    <Dialog isOpen={dialog === 'delete'} onClose={() => setDialog(null)} title={plan.deleteTitle}>
      <div className="space-y-4" data-testid="estimate-delete-dialog"><p className="text-sm">{plan.deleteBody}</p>
        <DialogActions plan={plan} confirm={plan.deleteConfirm} testId="estimate-delete-confirm" onConfirm={deleteEstimate} onCancel={() => setDialog(null)}/></div>
    </Dialog>
    <Dialog isOpen={dialog === 'convert' && Boolean(selected && target)} onClose={() => setDialog(null)} title={plan.conversion.confirmTitle}>
      {selected && target && <div className="space-y-4" data-testid="estimate-convert-dialog">
        <dl className="space-y-2 text-sm">
          <div><dt className={muted}>{plan.conversion.source}</dt><dd className="font-medium">{plan.revision(selected.revision)} · {plan.revisionStatuses[selected.status]}</dd>
            <dd className="tabular-nums [overflow-wrap:anywhere]" data-testid="estimate-convert-source-totals"><bdi>{plan.conversion.sourceTotals(
              formatEstimateMoney(selected.result.totalPlannedCost, locale), formatEstimateMoney(selected.result.plannedBatchPrice, locale))}</bdi></dd></div>
          <div><dt className={muted}>{plan.conversion.target}</dt><dd className="font-medium" data-testid="estimate-convert-target"><bdi dir="ltr">{target.code}</bdi> · {target.name}</dd></div>
        </dl>
        <p className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm font-semibold text-amber-900" data-testid="estimate-convert-warning">{plan.conversion.warning}</p>
        <DialogActions plan={plan} confirm={plan.conversion.confirm} testId="estimate-convert-confirm" onConfirm={convert} onCancel={() => setDialog(null)}/>
      </div>}
    </Dialog>
  </LivePage>;
}

function DialogActions({ plan, confirm, testId, onConfirm, onCancel }: { plan: EstimatePlanningCopy; confirm: string; testId: string; onConfirm: () => void; onCancel: () => void }) {
  return <div className="flex flex-wrap justify-end gap-3">
    <Button type="button" variant="secondary" onClick={onCancel} data-testid={`${testId}-cancel`}>{plan.cancel}</Button>
    <Button type="button" onClick={onConfirm} data-testid={testId} autoFocus>{confirm}</Button>
  </div>;
}

function RevisionHistory({ detail, selectedId, plan, copy, locale, onSelect }: {
  detail: EstimateDetailResponse; selectedId: string | null; plan: EstimatePlanningCopy; copy: EstimatesCopy; locale: 'en' | 'ur';
  onSelect: (revision: EstimateRevisionResponse) => void;
}) {
  const byId = new Map(detail.revisions.map(revision => [revision.id, revision.revision]));
  return <Card className="space-y-3 min-w-0" data-testid="estimate-history">
    <h2 className="font-semibold text-lg">{plan.history}</h2>
    <ul className="divide-y divide-[var(--color-border-subtle)]">
      {detail.revisions.map(revision => <li key={revision.id} data-testid={`estimate-history-${revision.revision}`}
        className="flex flex-wrap items-center gap-x-4 gap-y-1 py-2 text-sm">
        <span className="font-semibold">{plan.revision(revision.revision)}</span>
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold border ${revisionBadgeClass(revision.status)}`}
          data-testid={`estimate-history-status-${revision.revision}`}>{plan.revisionStatuses[revision.status]}</span>
        <span className="tabular-nums [overflow-wrap:anywhere]"><bdi>{copy.batchPrice}: {formatEstimateMoney(revision.result.plannedBatchPrice, locale)}</bdi></span>
        {revision.approvedAt && <span className={muted}>{plan.approvedOn(when(revision.approvedAt, locale))}</span>}
        {revision.supersededByVersionId && <span className={muted}>{plan.supersededBy(byId.get(revision.supersededByVersionId) ?? 0)}</span>}
        <span className="ms-auto">{revision.id === selectedId
          ? <span className="text-xs font-semibold" aria-current="true">{plan.viewing}</span>
          : <Button type="button" variant="ghost" size="sm" data-testid={`estimate-history-view-${revision.revision}`} onClick={() => onSelect(revision)}>{plan.view}</Button>}</span>
      </li>)}
    </ul>
  </Card>;
}

function ReadOnlyLines({ revision, copy, plan, locale }: { revision: EstimateRevisionResponse; copy: EstimatesCopy; plan: EstimatePlanningCopy; locale: 'en' | 'ur' }) {
  const result = revision.result;
  return <Card className="space-y-3 min-w-0" data-testid="estimate-readonly-revision">
    <div className="flex flex-wrap items-center gap-2"><h2 className="font-semibold text-lg">{plan.revision(revision.revision)}</h2>
      <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold border ${revisionBadgeClass(revision.status)}`}
        data-testid="estimate-selected-status">{plan.revisionStatuses[revision.status]}</span></div>
    <p className={muted}>{plan.readOnlyRevision}</p>
    <ul className="space-y-2">
      {result.lines.map((line, index) => <li key={line.lineNumber} data-testid={`estimate-readonly-line-${index}`}
        className="rounded-lg border border-[var(--color-border-default)] p-3 grid grid-cols-1 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1.6fr)] gap-2 text-sm">
        <span className="min-w-0 break-words"><span className="font-medium">{line.description}</span> · {copy.groups[line.costGroup]}</span>
        <span dir="ltr" className="tabular-nums text-start [overflow-wrap:anywhere]">{line.quantity} {line.unit} × {line.unitCost}</span>
        <span className="font-semibold tabular-nums text-start sm:text-end [overflow-wrap:anywhere]" data-testid={`estimate-readonly-line-cost-${index}`}>
          <bdi>{formatEstimateMoney(line.plannedCost, locale)}</bdi></span>
      </li>)}
    </ul>
    <p className={muted}>{copy.pricingMode}: {copy.modes[result.pricingMode]} · <bdi dir="ltr">{result.pricingValue}</bdi>
      {result.saleableQuantity !== null && <> · {copy.saleableQuantity}: <bdi dir="ltr">{result.saleableQuantity}</bdi></>}</p>
  </Card>;
}

function issueText(issue: ConversionIssue, plan: EstimatePlanningCopy): string {
  const reason = issue.reason === 'scale_exceeded' ? plan.conversion.reasons.scale_exceeded(issue.target.scale)
    : issue.reason === 'integer_digits_exceeded' ? plan.conversion.reasons.integer_digits_exceeded(issue.target.maxIntegerDigits)
      : plan.conversion.reasons.inexact_division;
  return reason;
}

function ConversionPanel({ revision, plan, locale, projects, canConvert, targetId, setTargetId, busy, converted, onReview }: {
  revision: EstimateRevisionResponse; plan: EstimatePlanningCopy; locale: 'en' | 'ur'; projects: ProjectResponse[] | null; canConvert: boolean;
  targetId: string; setTargetId: (value: string) => void; busy: boolean; converted: EstimateConversionResultResponse | null; onReview: () => void;
}) {
  const conversion = revision.conversion;
  const c = plan.conversion;
  const projectCode = (id: string) => projects?.find(project => project.id === id)?.code ?? id;
  if (conversion) return <Card className="space-y-3 min-w-0" data-testid="estimate-conversion-lineage">
    <h2 className="font-semibold text-lg flex items-center gap-2"><ArrowRightLeft className="w-5 h-5" aria-hidden="true"/>{c.lineage}</h2>
    {converted && <p role="status" className="text-sm text-emerald-700" data-testid="estimate-conversion-success">
      {converted.replayed ? c.replayed : c.success(projectCode(converted.conversion.projectId))}</p>}
    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
      <div><dt className={muted}>{c.target}</dt><dd className="font-medium" data-testid="estimate-lineage-project"><bdi dir="ltr">{projectCode(conversion.projectId)}</bdi></dd></div>
      <div><dt className={muted}>{c.convertedAt}</dt><dd>{when(conversion.convertedAt, locale)}</dd></div>
      <div><dt className={muted}>{c.contract}</dt><dd dir="ltr" className="font-mono text-start">{conversion.conversionContractVersion}</dd></div>
      <div><dt className={muted}>{plan.planningOnly}</dt><dd className="font-semibold">{c.convertedTo(projectCode(conversion.projectId))}</dd></div>
    </dl>
    {converted?.replayed && converted.budget === null && <p className={muted}>{c.budgetDiscarded}</p>}
    <p className="text-sm font-semibold">{c.warning}</p>
    <Link href={`/projects/${conversion.projectId}`} data-testid="estimate-lineage-open-project" className="inline-flex text-sm underline">{c.openProject}</Link>
  </Card>;

  if (revision.status !== 'APPROVED') return <Card data-testid="estimate-conversion-unavailable"><p className={muted}>{c.onlyApproved}</p></Card>;

  if (!revision.conversionReadiness.convertible) return <Card className="space-y-3 min-w-0 border-amber-300" data-testid="estimate-conversion-limitation">
    <h2 className="font-semibold text-lg">{c.limitationTitle}</h2>
    <p className="text-sm">{c.limitationBody}</p>
    <ul className="space-y-2">
      {revision.conversionReadiness.issues.map((issue, index) => <li key={`${issue.field}-${index}`} data-testid={`estimate-conversion-issue-${index}`}
        className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm space-y-1">
        <p className="font-semibold">{issue.lineNumber ? c.lineLabel(issue.lineNumber) : c.estimateLevel} · {c.fields[issue.field.replace(/^lines\[\d+\]\./, '')] ?? issue.field}</p>
        <p dir="ltr" className="font-mono text-start [overflow-wrap:anywhere]" data-testid={`estimate-conversion-issue-value-${index}`}>{issue.sourceValue}</p>
        <p>{issueText(issue, plan)}</p>
      </li>)}
    </ul>
  </Card>;

  if (!canConvert) return null;
  const options = (projects ?? []).filter(eligible);
  return <Card className="space-y-4 min-w-0" data-testid="estimate-conversion-panel">
    <h2 className="font-semibold text-lg flex items-center gap-2"><ArrowRightLeft className="w-5 h-5" aria-hidden="true"/>{c.heading}</h2>
    <p className={muted}>{c.intro}</p>
    <p className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm font-semibold text-amber-900">{c.warning}</p>
    {projects !== null && options.length === 0 ? <p className="text-sm" data-testid="estimate-conversion-no-projects">{c.noProjects}</p>
      : <div className="max-w-xl"><Select label={c.target} value={targetId} data-testid="estimate-conversion-target" onChange={event => setTargetId(event.target.value)}
          options={[{ value: '', label: c.choose }, ...options.map(project => ({ value: project.id, label: `${project.code} · ${project.name}` }))]}/></div>}
    <Button type="button" leftIcon={<ArrowRightLeft className="w-4 h-4" aria-hidden="true"/>} disabled={!targetId || busy} isLoading={busy}
      data-testid="estimate-conversion-review" onClick={onReview}>{busy ? c.converting : c.review}</Button>
  </Card>;
}
