"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { AlertTriangle, ArrowLeft, Ban, CheckCircle2, Lock, Trash2 } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import {
  commercialApi,
  isTrustedCommercialDocument,
  type CommercialDocumentResponse,
  type CommercialPreviewResponse,
} from '@/lib/api/commercial';
import {
  addCommercialLine,
  changeCommercialLine,
  commercialActions,
  commercialCreatePayload,
  commercialFinalizePayload,
  commercialLinesPayload,
  commercialPath,
  commercialUpdatePayload,
  commercialVoidPayload,
  draftFromDocument,
  formatCommercialMoney,
  initialCommercialDraft,
  isCommercialConflictCode,
  isCommercialFinalizeBlocker,
  localDateOf,
  mapCommercialFieldErrors,
  removeCommercialLine,
  todayIso,
  validateCommercialDraft,
  validateCommercialLines,
  validateVoidReason,
  type CommercialConflictCode,
  type CommercialDocumentDraft,
  type CommercialKind,
  type CommercialValidationErrors,
  type CommercialValidationIssue,
} from '@/lib/commercial/form';
import { useLanguage } from '@/lib/i18n/context';
import { commercialCopy, type CommercialCopy } from '@/lib/i18n/translations/commercial';
import { CommercialLineEditor, CommercialLineTable } from './CommercialLineEditor';
import { RecordOnlyBanner, statusBadgeClass } from './CommercialDocumentList';

type LoadState = 'loading' | 'ready' | 'notFound' | 'forbidden' | 'error';
type Preview =
  | { state: 'empty' }
  | { state: 'pending'; data: CommercialPreviewResponse | null }
  | { state: 'ready'; data: CommercialPreviewResponse }
  | { state: 'invalid'; errors: CommercialValidationErrors }
  | { state: 'failed' };
type DialogKind = 'finalize' | 'void' | 'delete' | null;

function DetailField({ label, children, testId }: { label: string; children: React.ReactNode; testId?: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-[var(--color-text-muted)]">{label}</dt>
      <dd className="mt-0.5 font-medium break-words" data-testid={testId}>{children}</dd>
    </div>
  );
}

function TotalsPanel({
  copy,
  locale,
  subtotal,
  total,
  status,
}: {
  copy: CommercialCopy;
  locale: 'en' | 'ur';
  subtotal: string | null;
  total: string | null;
  status: React.ReactNode;
}) {
  return (
    <Card className="space-y-3" data-testid="commercial-totals">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-semibold">{copy.preview.title}</h2>
        <span className="text-[11px] font-semibold text-[var(--color-brand-primary)]">{copy.preview.authoritative}</span>
      </div>
      <dl className="space-y-2 text-sm">
        <div className="flex justify-between gap-3">
          <dt className="text-[var(--color-text-muted)]">{copy.preview.subtotal}</dt>
          <dd dir="ltr" className="tabular-nums" data-testid="commercial-preview-subtotal">{formatCommercialMoney(subtotal, locale)}</dd>
        </div>
        <div className="flex justify-between gap-3 border-t border-[var(--color-border-subtle)] pt-2 text-base">
          <dt className="font-semibold">{copy.preview.total}</dt>
          <dd dir="ltr" className="font-bold tabular-nums break-all" data-testid="commercial-preview-total">{formatCommercialMoney(total, locale)}</dd>
        </div>
      </dl>
      <div className="text-xs text-[var(--color-text-muted)] empty:hidden" data-testid="commercial-preview-status" aria-live="polite">{status}</div>
      <p className="text-xs text-[var(--color-text-muted)]">{copy.currencyNote}</p>
      <p className="inline-flex rounded border border-[var(--color-border-default)] px-2 py-0.5 text-[11px] font-semibold" data-testid="commercial-financial-effect">
        {copy.financialEffectNone}
      </p>
    </Card>
  );
}

export function CommercialDocumentWorkspace({ kind, documentId }: { kind: CommercialKind; documentId?: string }) {
  const { locale } = useLanguage();
  const copy = commercialCopy(locale);
  const kindCopy = copy.kinds[kind];
  const path = commercialPath(kind);
  const router = useRouter();
  const { can, business } = useSession();
  const canRead = can('FINANCIAL_DATA_READ');
  const canWrite = can('RECORD_CREATE_UPDATE');
  const activeBusinessId = business?.businessId ?? null;
  const isNew = !documentId;

  const [document, setDocument] = useState<CommercialDocumentResponse | null>(null);
  const [loadState, setLoadState] = useState<LoadState>('loading');
  const [attempt, setAttempt] = useState(0);
  const [draft, setDraft] = useState<CommercialDocumentDraft>(() => initialCommercialDraft(''));
  const [dirty, setDirty] = useState(false);
  const [showRequired, setShowRequired] = useState(false);
  const [formErrors, setFormErrors] = useState<CommercialValidationErrors>({});
  const [preview, setPreview] = useState<Preview>({ state: 'empty' });
  const [busy, setBusy] = useState<'save' | 'finalize' | 'void' | 'delete' | null>(null);
  const [dialog, setDialog] = useState<DialogKind>(null);
  const [voidReason, setVoidReason] = useState('');
  const [voidIssue, setVoidIssue] = useState<CommercialValidationIssue | null>(null);
  const [conflict, setConflict] = useState<CommercialConflictCode | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [savedNotice, setSavedNotice] = useState(false);
  const lineSeq = useRef(1);
  const requestId = useRef(0);
  const dialogFocus = useRef<HTMLButtonElement | null>(null);
  const reasonRef = useRef<HTMLTextAreaElement | null>(null);

  const nextLineId = () => `new-line-${++lineSeq.current}`;

  const accept = useCallback((next: CommercialDocumentResponse) => {
    setDocument(next);
    setDraft(draftFromDocument(next));
    setDirty(false);
    setShowRequired(false);
    setFormErrors({});
    setConflict(null);
    setActionError(null);
  }, []);

  // Load (or reset) whenever the document or active business changes; never show another business's data.
  useEffect(() => {
    const ticket = ++requestId.current;
    const controller = new AbortController();
    queueMicrotask(() => {
      if (ticket !== requestId.current) return;
      setConflict(null);
      setActionError(null);
      setDialog(null);
      setSavedNotice(false);
      if (isDemoMode || !canRead || !activeBusinessId) {
        setLoadState('ready');
        return;
      }
      if (isNew || !documentId) {
        setDocument(null);
        setDraft(initialCommercialDraft(todayIso()));
        setDirty(false);
        setShowRequired(false);
        setFormErrors({});
        setLoadState('ready');
        return;
      }
      setLoadState('loading');
      commercialApi(kind)
        .get(documentId, controller.signal)
        .then(response => {
          if (ticket !== requestId.current) return;
          if (!isTrustedCommercialDocument(response, activeBusinessId, kind) || response.id !== documentId) {
            throw new ApiError(502, 'Invalid commercial document response.');
          }
          accept(response);
          setLoadState('ready');
        })
        .catch(error => {
          if (ticket !== requestId.current || error?.name === 'AbortError') return;
          setDocument(null);
          setLoadState(error instanceof ApiError && error.status === 404 ? 'notFound'
            : error instanceof ApiError && error.status === 403 ? 'forbidden' : 'error');
        });
    });
    return () => controller.abort();
  }, [accept, activeBusinessId, attempt, canRead, documentId, isNew, kind]);

  const editable = canWrite && loadState === 'ready' && (isNew || document?.status === 'DRAFT');

  // Backend-authoritative preview: debounced, stale requests aborted, local format checks first.
  useEffect(() => {
    if (!editable) return;
    const controller = new AbortController();
    const lines = draft.lines;
    const timer = setTimeout(() => {
      if (lines.length === 0) {
        setPreview({ state: 'empty' });
        return;
      }
      const local = validateCommercialLines(lines);
      if (Object.keys(local).length > 0) {
        setPreview({ state: 'invalid', errors: local });
        return;
      }
      setPreview(previous => ({ state: 'pending', data: previous.state === 'ready' ? previous.data : null }));
      commercialApi(kind)
        .preview({ lines: commercialLinesPayload(lines) }, controller.signal)
        .then(response => {
          if (controller.signal.aborted) return;
          if (response.financialEffect !== 'NONE' || response.kind !== kind || response.lines.length !== lines.length) {
            throw new ApiError(502, 'Invalid commercial preview response.');
          }
          setPreview({ state: 'ready', data: response });
        })
        .catch(error => {
          if (controller.signal.aborted || error?.name === 'AbortError') return;
          setPreview(error instanceof ApiError && error.status === 400
            ? { state: 'invalid', errors: mapCommercialFieldErrors(error.fieldErrors) }
            : { state: 'failed' });
        });
    }, 300);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [draft.lines, editable, kind]);

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  useEffect(() => {
    if (!dialog) return;
    const timer = setTimeout(() => (dialog === 'void' ? reasonRef.current : dialogFocus.current)?.focus(), 0);
    return () => clearTimeout(timer);
  }, [dialog]);

  const update = (next: CommercialDocumentDraft) => {
    setDraft(next);
    setDirty(true);
    setSavedNotice(false);
  };

  const handleActionError = (error: unknown) => {
    if (error instanceof Error && error.name === 'AbortError') return;
    if (error instanceof ApiError) {
      if (isCommercialConflictCode(error.code)) {
        setConflict(error.code);
        setDialog(null);
        return;
      }
      if (error.status === 404) {
        setDialog(null);
        setLoadState('notFound');
        return;
      }
      if (error.status === 403) {
        setActionError(copy.forbidden);
        return;
      }
      if (isCommercialFinalizeBlocker(error.code)) {
        setActionError(copy.blockers[error.code]);
        setDialog(null);
        return;
      }
      if (error.status === 400) {
        setFormErrors(mapCommercialFieldErrors(error.fieldErrors));
        setShowRequired(true);
        setActionError(copy.preview.invalid);
        return;
      }
    }
    setActionError(copy.requestFailed);
  };

  const save = async () => {
    const errors = validateCommercialDraft(draft);
    setShowRequired(true);
    setFormErrors(errors);
    setActionError(null);
    if (Object.keys(errors).length > 0) {
      setActionError(copy.preview.invalid);
      return;
    }
    setBusy('save');
    try {
      const api = commercialApi(kind);
      if (isNew) {
        const created = await api.create(commercialCreatePayload(draft));
        if (!activeBusinessId || !isTrustedCommercialDocument(created, activeBusinessId, kind)) throw new ApiError(502, 'Invalid response.');
        setDirty(false);
        router.replace(`${path}/${created.id}`);
        return;
      }
      if (!document) return;
      const updated = await api.update(document.id, commercialUpdatePayload(draft, document.version));
      if (!activeBusinessId || !isTrustedCommercialDocument(updated, activeBusinessId, kind)) throw new ApiError(502, 'Invalid response.');
      accept(updated);
      setSavedNotice(true);
    } catch (error) {
      handleActionError(error);
    } finally {
      setBusy(null);
    }
  };

  const runTransition = async (action: 'finalize' | 'void' | 'delete') => {
    if (!document) return;
    if (action === 'void') {
      const issue = validateVoidReason(voidReason);
      setVoidIssue(issue);
      if (issue) return;
    }
    setBusy(action);
    setActionError(null);
    try {
      const api = commercialApi(kind);
      if (action === 'delete') {
        await api.remove(document.id, document.version);
        setDirty(false);
        router.push(path);
        return;
      }
      const next = action === 'finalize'
        ? await api.finalize(document.id, commercialFinalizePayload(document.version))
        : await api.voidDocument(document.id, commercialVoidPayload(document.version, voidReason));
      if (!activeBusinessId || !isTrustedCommercialDocument(next, activeBusinessId, kind)) throw new ApiError(502, 'Invalid response.');
      accept(next);
      setDialog(null);
      setVoidReason('');
    } catch (error) {
      handleActionError(error);
    } finally {
      setBusy(null);
    }
  };

  const backLink = (
    <Link href={path} data-testid="commercial-back-to-list"
      className="inline-flex items-center gap-1.5 h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border-default)] text-sm font-medium hover:border-[var(--color-brand-border)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]">
      <ArrowLeft className="w-4 h-4 rtl:rotate-180" aria-hidden="true" />
      {copy.editor.backToList}
    </Link>
  );
  const title = isNew ? kindCopy.newTitle : kindCopy.detailTitle;

  if (isDemoMode || !canRead) {
    return (
      <LivePage title={title}>
        <Card data-testid="commercial-no-access"><p>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</p></Card>
      </LivePage>
    );
  }
  if (isNew && !canWrite) {
    return (
      <LivePage title={title} actions={backLink}>
        <Card className="space-y-3" data-testid="commercial-create-forbidden">
          <p role="alert">{copy.readOnlyRole}</p>
        </Card>
      </LivePage>
    );
  }
  if (loadState === 'loading') {
    return <LivePage title={title} actions={backLink}><Card><p role="status" data-testid="commercial-loading">{copy.loading}</p></Card></LivePage>;
  }
  if (loadState !== 'ready') {
    const message = loadState === 'notFound' ? copy.notFound : loadState === 'forbidden' ? copy.forbidden : copy.requestFailed;
    return (
      <LivePage title={title} actions={backLink}>
        <Card className="space-y-3" data-testid={`commercial-${loadState === 'notFound' ? 'not-found' : loadState === 'forbidden' ? 'forbidden' : 'load-error'}`}>
          <p role="alert" className="text-sm">{message}</p>
          {loadState === 'error' && <Button type="button" variant="secondary" onClick={() => setAttempt(value => value + 1)}>{copy.retry}</Button>}
        </Card>
      </LivePage>
    );
  }

  const status = document?.status ?? 'DRAFT';
  const actions = commercialActions(status, canWrite);
  const blockers = document?.finalizeBlockers ?? [];
  const previewErrors = preview.state === 'invalid' ? preview.errors : {};
  const lineErrors: CommercialValidationErrors = { ...previewErrors, ...formErrors };
  const previewData = preview.state === 'ready' ? preview.data : preview.state === 'pending' ? preview.data : null;
  const lineAmounts = draft.lines.map((_, index) => previewData?.lines[index]?.lineAmount ?? null);
  const headerIssue = (field: string) => {
    const issue = formErrors[field];
    return issue && (issue !== 'required' || showRequired) ? copy.issues[issue] : undefined;
  };
  const previewStatus = preview.state === 'pending' ? copy.preview.pending
    : preview.state === 'invalid' ? copy.preview.invalid
    : preview.state === 'failed' ? copy.preview.failed
    : preview.state === 'empty' ? copy.preview.noLines
    : null;

  return (
    <LivePage title={title} actions={backLink}>
      <div className="space-y-4 leading-relaxed" data-testid="commercial-workspace" data-kind={kind} data-status={status}>
        <RecordOnlyBanner copy={copy} />

        {!canWrite && (
          <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" data-testid="commercial-readonly-role-banner">
            {copy.readOnlyRole}
          </div>
        )}

        {conflict && (
          <div className="rounded-lg border border-amber-400 bg-amber-50 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3" role="alert" data-testid="commercial-conflict-banner">
            <div className="flex gap-3 min-w-0">
              <AlertTriangle className="w-5 h-5 shrink-0 text-amber-700" aria-hidden="true" />
              <div className="min-w-0">
                <p className="font-semibold text-sm text-amber-950">{copy.conflicts.title}</p>
                <p className="text-sm text-amber-900">{copy.conflicts[conflict]}</p>
              </div>
            </div>
            <Button type="button" variant="secondary" onClick={() => setAttempt(value => value + 1)} data-testid="commercial-conflict-reload">
              {copy.conflicts.reload}
            </Button>
          </div>
        )}

        {actionError && !conflict && (
          <div role="alert" className="rounded-lg border border-[var(--color-status-error)] bg-red-50 p-3 text-sm text-red-900" data-testid="commercial-action-error">
            {actionError}
          </div>
        )}

        {savedNotice && !dirty && (
          <p role="status" className="text-sm text-emerald-800 inline-flex items-center gap-1.5" data-testid="commercial-saved-notice">
            <CheckCircle2 className="w-4 h-4" aria-hidden="true" />{copy.editor.saved}
          </p>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_20rem] gap-4 items-start">
          <div className="space-y-4 min-w-0">
            <Card className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 className="font-semibold">{copy.editor.detailsTitle}</h2>
                <div className="flex flex-wrap items-center gap-2">
                  <span dir="ltr" className="font-mono text-sm font-semibold" data-testid="commercial-document-number">
                    {document?.documentNumber ?? <span className="font-sans font-normal text-[var(--color-text-muted)]">{copy.list.noNumber}</span>}
                  </span>
                  <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold border ${statusBadgeClass(status)}`} data-testid="commercial-status-badge">
                    {copy.statuses[status]}
                  </span>
                </div>
              </div>

              {editable ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4" data-testid="commercial-editor">
                  <div className="sm:col-span-2">
                    <Input
                      label={kindCopy.counterparty}
                      helperText={kindCopy.counterpartyHelp}
                      value={draft.counterpartyName}
                      maxLength={320}
                      error={headerIssue('counterpartyName')}
                      onChange={event => update({ ...draft, counterpartyName: event.target.value })}
                      data-testid="commercial-counterparty-input"
                    />
                  </div>
                  <Input
                    type="date"
                    dir="ltr"
                    label={kindCopy.documentDate}
                    value={draft.documentDate}
                    error={headerIssue('documentDate')}
                    onChange={event => update({ ...draft, documentDate: event.target.value })}
                    data-testid="commercial-document-date-input"
                  />
                  <Input
                    type="date"
                    dir="ltr"
                    label={copy.fields.dueDate}
                    value={draft.dueDate}
                    error={headerIssue('dueDate')}
                    onChange={event => update({ ...draft, dueDate: event.target.value })}
                    data-testid="commercial-due-date-input"
                  />
                  <div className="sm:col-span-2 flex flex-col gap-1.5">
                    <label htmlFor="commercial-notes" className="text-[13px] font-semibold text-[var(--color-text-secondary)]">{copy.fields.notes}</label>
                    <textarea
                      id="commercial-notes"
                      rows={2}
                      maxLength={2000}
                      value={draft.notes}
                      onChange={event => update({ ...draft, notes: event.target.value })}
                      aria-invalid={Boolean(headerIssue('notes'))}
                      data-testid="commercial-notes-input"
                      className="w-full px-3 py-2 rounded-[var(--radius-md)] border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] text-[14px] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-primary)]/15"
                    />
                    {headerIssue('notes') && <p role="alert" className="text-[12px] font-medium text-[var(--color-status-error)]">{headerIssue('notes')}</p>}
                  </div>
                </div>
              ) : (
                document && (
                  <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm" data-testid="commercial-readonly-details">
                    <DetailField label={kindCopy.counterparty} testId="commercial-readonly-counterparty">{document.counterpartyName ?? '—'}</DetailField>
                    <DetailField label={kindCopy.documentDate}><span dir="ltr">{document.documentDate ?? '—'}</span></DetailField>
                    <DetailField label={copy.fields.dueDate}><span dir="ltr">{document.dueDate ?? '—'}</span></DetailField>
                    {document.finalizedAt && (
                      <DetailField label={copy.readOnly.finalizedAt}><span dir="ltr" data-testid="commercial-finalized-date">{localDateOf(document.finalizedAt)}</span></DetailField>
                    )}
                    {document.notes && <div className="sm:col-span-2"><DetailField label={copy.fields.notes}><span className="whitespace-pre-line">{document.notes}</span></DetailField></div>}
                    {document.voidedAt && (
                      <DetailField label={copy.readOnly.voidedAt}><span dir="ltr">{localDateOf(document.voidedAt)}</span></DetailField>
                    )}
                    {document.voidReason && (
                      <DetailField label={copy.readOnly.voidReason} testId="commercial-void-reason-display">{document.voidReason}</DetailField>
                    )}
                  </dl>
                )
              )}
            </Card>

            {document && status !== 'DRAFT' && (
              <div className="rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-subtle)] p-3 text-sm flex gap-2" data-testid="commercial-readonly-state">
                {status === 'VOID' ? <Ban className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" /> : <Lock className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />}
                <span>{copy.readOnly[status]}</span>
              </div>
            )}

            <Card className="space-y-3">
              <h2 className="font-semibold">{copy.editor.linesTitle}</h2>
              {editable ? (
                <CommercialLineEditor
                  copy={copy}
                  locale={locale}
                  lines={draft.lines}
                  lineAmounts={lineAmounts}
                  errors={lineErrors}
                  showRequired={showRequired}
                  disabled={busy !== null}
                  onChange={(id, field, value) => update(changeCommercialLine(draft, id, field, value))}
                  onAdd={() => update(addCommercialLine(draft, nextLineId()))}
                  onRemove={id => update(removeCommercialLine(draft, id))}
                />
              ) : (
                <CommercialLineTable copy={copy} locale={locale} lines={document?.lines ?? []} />
              )}
            </Card>
          </div>

          <div className="space-y-4 min-w-0 lg:sticky lg:top-4">
            <TotalsPanel
              copy={copy}
              locale={locale}
              subtotal={editable ? previewData?.subtotal ?? null : document?.subtotal ?? null}
              total={editable ? previewData?.total ?? null : document?.total ?? null}
              status={editable ? previewStatus : null}
            />

            {(editable || actions.void) && (
              <Card className="space-y-3" data-testid="commercial-actions">
                {editable && (
                  <Button type="button" className="w-full" onClick={save} isLoading={busy === 'save'} disabled={busy !== null} data-testid="commercial-save-draft">
                    {busy === 'save' ? copy.editor.saving : copy.editor.saveDraft}
                  </Button>
                )}
                {dirty && <p className="text-xs text-amber-800" data-testid="commercial-unsaved">{copy.editor.unsaved}</p>}
                {!isNew && actions.finalize && (
                  <>
                    {blockers.length > 0 && (
                      <div className="text-xs space-y-1" data-testid="commercial-blockers">
                        <p className="font-semibold">{copy.blockers.title}</p>
                        <ul className="list-disc ps-4">{blockers.map(blocker => <li key={blocker}>{copy.blockers[blocker]}</li>)}</ul>
                      </div>
                    )}
                    {dirty && <p className="text-xs">{copy.finalize.saveFirst}</p>}
                    <Button type="button" variant="secondary" className="w-full" disabled={busy !== null || dirty || blockers.length > 0}
                      onClick={() => setDialog('finalize')} data-testid="commercial-finalize-button">
                      {copy.finalize.button}
                    </Button>
                  </>
                )}
                {!isNew && actions.remove && (
                  <Button type="button" variant="ghost" className="w-full" disabled={busy !== null} onClick={() => setDialog('delete')}
                    leftIcon={<Trash2 className="w-4 h-4" aria-hidden="true" />} data-testid="commercial-delete-button">
                    {copy.remove.button}
                  </Button>
                )}
                {actions.void && (
                  <Button type="button" variant="destructive" className="w-full" disabled={busy !== null}
                    onClick={() => { setVoidReason(''); setVoidIssue(null); setDialog('void'); }} data-testid="commercial-void-button">
                    {copy.voiding.button}
                  </Button>
                )}
              </Card>
            )}
          </div>
        </div>
      </div>

      <Dialog isOpen={dialog === 'finalize'} onClose={() => busy === null && setDialog(null)} title={copy.finalize.title}>
        <div className="space-y-3 text-sm" data-testid="commercial-finalize-dialog">
          <p className="font-medium">{copy.finalize.body}</p>
          <p>{copy.finalize.notIntro}</p>
          <ul className="list-disc ps-5 space-y-0.5">{copy.finalize.notList.map(item => <li key={item}>{item}</li>)}</ul>
          <p className="text-[var(--color-text-muted)]">{copy.finalize.numberNote}</p>
          <div className="flex flex-wrap justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" ref={dialogFocus} onClick={() => setDialog(null)} disabled={busy !== null}>{copy.finalize.cancel}</Button>
            <Button type="button" onClick={() => runTransition('finalize')} isLoading={busy === 'finalize'} disabled={busy !== null} data-testid="commercial-finalize-confirm">
              {busy === 'finalize' ? copy.finalize.finalizing : copy.finalize.confirm}
            </Button>
          </div>
        </div>
      </Dialog>

      <Dialog isOpen={dialog === 'void'} onClose={() => busy === null && setDialog(null)} title={copy.voiding.title}>
        <div className="space-y-3 text-sm" data-testid="commercial-void-dialog">
          <p>{copy.voiding.body}</p>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="commercial-void-reason" className="text-[13px] font-semibold">{copy.voiding.reason}</label>
            <textarea
              id="commercial-void-reason"
              ref={reasonRef}
              rows={3}
              maxLength={1000}
              value={voidReason}
              onChange={event => { setVoidReason(event.target.value); setVoidIssue(null); }}
              aria-invalid={Boolean(voidIssue)}
              data-testid="commercial-void-reason"
              className="w-full px-3 py-2 rounded-[var(--radius-md)] border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-primary)]/15"
            />
            {voidIssue && <p role="alert" className="text-[12px] font-medium text-[var(--color-status-error)]" data-testid="commercial-void-reason-error">{copy.issues[voidIssue]}</p>}
          </div>
          <div className="flex flex-wrap justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setDialog(null)} disabled={busy !== null}>{copy.voiding.cancel}</Button>
            <Button type="button" variant="destructive" onClick={() => runTransition('void')} isLoading={busy === 'void'} disabled={busy !== null} data-testid="commercial-void-confirm">
              {busy === 'void' ? copy.voiding.working : copy.voiding.confirm}
            </Button>
          </div>
        </div>
      </Dialog>

      <Dialog isOpen={dialog === 'delete'} onClose={() => busy === null && setDialog(null)} title={copy.remove.title}>
        <div className="space-y-3 text-sm" data-testid="commercial-delete-dialog">
          <p>{copy.remove.body}</p>
          <div className="flex flex-wrap justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" ref={dialogFocus} onClick={() => setDialog(null)} disabled={busy !== null}>{copy.remove.cancel}</Button>
            <Button type="button" variant="destructive" onClick={() => runTransition('delete')} isLoading={busy === 'delete'} disabled={busy !== null} data-testid="commercial-delete-confirm">
              {busy === 'delete' ? copy.remove.working : copy.remove.confirm}
            </Button>
          </div>
        </div>
      </Dialog>
    </LivePage>
  );
}
