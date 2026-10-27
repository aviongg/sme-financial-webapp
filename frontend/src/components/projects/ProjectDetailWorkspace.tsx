"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  History,
  Info,
  Lock,
  Plus,
  RefreshCw,
  Trash2,
} from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import {
  projectsApi,
  type ProjectBudgetVersionResponse,
  type ProjectPlanningSummaryResponse,
  type ProjectResponse,
} from '@/lib/api/projects';
import {
  addBudgetLine,
  allowedProjectTransitions,
  canMutateProjectPlanning,
  changeBudgetLine,
  draftFromBudgetVersion,
  formatProjectMoney,
  formatProjectPercent,
  initialBudgetDraft,
  isBudgetVersionReadOnly,
  isProjectConflictCode,
  isTransitionBlockedByOpenDraft,
  mapProjectFieldErrors,
  MAX_PROJECT_BUDGET_LINES,
  metadataFromProject,
  previewBudgetCalculation,
  projectBudgetApprovePayload,
  projectBudgetCreatePayload,
  projectBudgetUpdatePayload,
  projectCostCategories,
  projectKinds,
  projectTransitionPayload,
  projectUpdatePayload,
  removeBudgetLine,
  validateProjectBudget,
  validateProjectMetadata,
  type ProjectBudgetDraft,
  type ProjectBudgetStatus,
  type ProjectConflictCode,
  type ProjectCostCategory,
  type ProjectKind,
  type ProjectMetadataDraft,
  type ProjectStatus,
  type ProjectValidationErrors,
} from '@/lib/projects/form';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { projectsCopy } from '@/lib/i18n/translations/projects';

const decimalInputProps = {
  type: 'text',
  inputMode: 'decimal',
  dir: 'ltr',
  autoComplete: 'off',
} as const;

function statusBadgeClass(status: ProjectStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'bg-emerald-50 text-emerald-800 border-emerald-200';
    case 'DRAFT':
      return 'bg-amber-50 text-amber-800 border-amber-200';
    case 'COMPLETED':
      return 'bg-blue-50 text-blue-800 border-blue-200';
    case 'CANCELLED':
      return 'bg-rose-50 text-rose-800 border-rose-200';
    case 'ARCHIVED':
      return 'bg-slate-100 text-slate-700 border-slate-300';
  }
}

function budgetStatusBadgeClass(status: ProjectBudgetStatus): string {
  switch (status) {
    case 'APPROVED':
      return 'bg-emerald-50 text-emerald-800 border-emerald-200';
    case 'DRAFT':
      return 'bg-amber-50 text-amber-800 border-amber-200';
    case 'SUPERSEDED':
      return 'bg-slate-100 text-slate-700 border-slate-300';
  }
}

function formatTimestamp(iso: string | null, locale: 'en' | 'ur'): string {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat(locale === 'ur' ? 'ur-PK' : 'en-PK', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function ProjectDetailWorkspace({ projectId }: { projectId: string }) {
  const { locale, t } = useLanguage();
  const copy = projectsCopy(locale);
  const router = useRouter();
  const { can, business } = useSession();
  const canRead = can('FINANCIAL_DATA_READ');
  const canWrite = can('RECORD_CREATE_UPDATE');
  const activeBusinessId = business?.businessId ?? null;

  const [project, setProject] = useState<ProjectResponse | null>(null);
  const [summary, setSummary] = useState<ProjectPlanningSummaryResponse | null>(null);
  const [budgets, setBudgets] = useState<ProjectBudgetVersionResponse[]>([]);
  const [loadedBusinessId, setLoadedBusinessId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadFailure, setLoadFailure] = useState<unknown>(null);

  // Metadata editing state
  const [editingMetadata, setEditingMetadata] = useState(false);
  const [metadataDraft, setMetadataDraft] = useState<ProjectMetadataDraft | null>(null);
  const [metadataErrors, setMetadataErrors] = useState<ProjectValidationErrors>({});
  const [savingMetadata, setSavingMetadata] = useState(false);
  const [confirmDeleteProject, setConfirmDeleteProject] = useState(false);
  const [deletingProject, setDeletingProject] = useState(false);

  // Lifecycle transition state
  const [targetStatus, setTargetStatus] = useState<ProjectStatus | ''>('');
  const [transitionReason, setTransitionReason] = useState('');
  const [transitioning, setTransitioning] = useState(false);

  // Budget draft editor state
  const [budgetEditorOpen, setBudgetEditorOpen] = useState(false);
  const [budgetDraft, setBudgetDraft] = useState<ProjectBudgetDraft>(initialBudgetDraft);
  const [budgetDirty, setBudgetDirty] = useState(false);
  const [budgetErrors, setBudgetErrors] = useState<ProjectValidationErrors>({});
  const [savingBudget, setSavingBudget] = useState(false);
  const [confirmDiscardDraft, setConfirmDiscardDraft] = useState(false);
  const [discardingDraft, setDiscardingDraft] = useState(false);
  const [confirmApproveDraft, setConfirmApproveDraft] = useState(false);
  const [approvingDraft, setApprovingDraft] = useState(false);

  // History line expansion
  const [expandedRevisionIds, setExpandedRevisionIds] = useState<Record<string, boolean>>({});

  // Action error & conflict preservation state
  const [actionFailure, setActionFailure] = useState<unknown>(null);
  const [conflictCode, setConflictCode] = useState<ProjectConflictCode | null>(null);

  const nextLineId = useRef(10);
  const requestId = useRef(0);
  const budgetFormRef = useRef<HTMLFormElement>(null);

  useEffect(() => {
    if (!budgetDirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [budgetDirty]);

  const loadWorkspace = useCallback(
    async (preserveDirtyDraft = false) => {
      if (isDemoMode || !canRead || !activeBusinessId) {
        setProject(null);
        setSummary(null);
        setBudgets([]);
        setLoadedBusinessId(null);
        setLoading(false);
        return;
      }

      const ticket = ++requestId.current;
      if (!preserveDirtyDraft) {
        setLoading(true);
      }
      setLoadFailure(null);

      try {
        const [projRes, sumRes, budRes] = await Promise.all([
          projectsApi.get(projectId),
          projectsApi.getPlanningSummary(projectId),
          projectsApi.listBudgets(projectId),
        ]);
        if (ticket !== requestId.current) return;
        if (
          projRes.businessId !== activeBusinessId ||
          sumRes.businessId !== activeBusinessId ||
          projRes.baseCurrency !== 'PKR' ||
          sumRes.baseCurrency !== 'PKR' ||
          projRes.planningOnly !== true ||
          sumRes.planningOnly !== true
        ) {
          throw new ApiError(502, 'Invalid project planning workspace response.');
        }

        setProject(projRes);
        setSummary(sumRes);
        setBudgets(budRes);
        setLoadedBusinessId(activeBusinessId);
        setConflictCode(null);
        setActionFailure(null);

        const transitions = allowedProjectTransitions(projRes.status);
        setTargetStatus(transitions[0] ?? '');

        if (!preserveDirtyDraft) {
          setMetadataDraft(metadataFromProject(projRes));
          if (projRes.latestDraftBudget) {
            setBudgetDraft(draftFromBudgetVersion(projRes.latestDraftBudget));
            nextLineId.current = projRes.latestDraftBudget.lines.length + 10;
            setBudgetEditorOpen(true);
          } else if (projRes.activeApprovedBudget) {
            setBudgetDraft(draftFromBudgetVersion(projRes.activeApprovedBudget));
            nextLineId.current = projRes.activeApprovedBudget.lines.length + 10;
            setBudgetEditorOpen(false);
          } else {
            setBudgetDraft(initialBudgetDraft());
            nextLineId.current = 10;
            setBudgetEditorOpen(canMutateProjectPlanning(projRes.status, canWrite));
          }
          setBudgetDirty(false);
          setBudgetErrors({});
        }
      } catch (error) {
        if (ticket !== requestId.current || (error instanceof Error && error.name === 'AbortError')) {
          return;
        }
        setProject(null);
        setSummary(null);
        setBudgets([]);
        setLoadedBusinessId(null);
        setLoadFailure(error);
      } finally {
        if (ticket === requestId.current) {
          setLoading(false);
        }
      }
    },
    [activeBusinessId, canRead, canWrite, projectId]
  );

  useEffect(() => {
    queueMicrotask(() => {
      setProject(null);
      setSummary(null);
      setBudgets([]);
      setLoadedBusinessId(null);
      setEditingMetadata(false);
      setBudgetDirty(false);
      void loadWorkspace(false);
    });
  }, [activeBusinessId, projectId, loadWorkspace]);

  const handleActionError = (error: unknown, isBudgetForm = false) => {
    if (error instanceof Error && error.name === 'AbortError') return;
    setActionFailure(error);
    if (error instanceof ApiError) {
      if (isProjectConflictCode(error.code)) {
        setConflictCode(error.code);
      }
      if (error.fieldErrors) {
        const mapped = mapProjectFieldErrors(error.fieldErrors);
        if (isBudgetForm) {
          setBudgetErrors(mapped);
        } else {
          setMetadataErrors(mapped);
        }
      }
    }
  };

  if (isDemoMode || !canRead) {
    return (
      <LivePage title={copy.detailTitle}>
        <Card>
          <p>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</p>
        </Card>
      </LivePage>
    );
  }

  const isCurrentBusinessLoaded = loadedBusinessId === activeBusinessId && project !== null && summary !== null;

  if (loading || (!isCurrentBusinessLoaded && !loadFailure)) {
    return (
      <LivePage title={copy.detailTitle}>
        <Card>
          <p role="status" data-testid="project-detail-loading">
            {t.common.loading}
          </p>
        </Card>
      </LivePage>
    );
  }

  if (Boolean(loadFailure) || !project || !summary) {
    return (
      <LivePage
        title={copy.detailTitle}
        actions={
          <Link href="/projects">
            <Button type="button" variant="secondary">
              <ArrowLeft className="w-4 h-4 me-1.5" aria-hidden="true" />
              {copy.form.backToList}
            </Button>
          </Link>
        }
      >
        <Card className="space-y-3" data-testid="project-detail-error">
          <p role="alert" className="text-sm text-[var(--color-semantic-danger)]">
            {localizedError(loadFailure, t)}
          </p>
          <Button type="button" variant="secondary" onClick={() => void loadWorkspace(false)}>
            {t.common.tryAgain}
          </Button>
        </Card>
      </LivePage>
    );
  }

  const canMutate = canMutateProjectPlanning(project.status, canWrite);
  const openDraft = project.latestDraftBudget;
  const activeApproved = project.activeApprovedBudget;
  const availableTransitions = allowedProjectTransitions(project.status);
  const preview = previewBudgetCalculation(budgetDraft);

  const saveMetadataChanges = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!metadataDraft || savingMetadata) return;
    const issues = validateProjectMetadata(metadataDraft);
    setMetadataErrors(issues);
    setActionFailure(null);
    setConflictCode(null);
    if (Object.keys(issues).length > 0) return;

    setSavingMetadata(true);
    try {
      await projectsApi.update(project.id, projectUpdatePayload(metadataDraft, project.version));
      setEditingMetadata(false);
      await loadWorkspace(true);
    } catch (error) {
      handleActionError(error, false);
    } finally {
      setSavingMetadata(false);
    }
  };

  const handleDeleteDraftProject = async () => {
    if (deletingProject) return;
    setDeletingProject(true);
    setActionFailure(null);
    setConflictCode(null);
    try {
      await projectsApi.deleteDraftProject(project.id, project.version);
      setBudgetDirty(false);
      router.push('/projects');
    } catch (error) {
      handleActionError(error, false);
      setConfirmDeleteProject(false);
    } finally {
      setDeletingProject(false);
    }
  };

  const handleTransition = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!targetStatus || transitioning) return;
    setTransitioning(true);
    setActionFailure(null);
    setConflictCode(null);
    try {
      await projectsApi.transition(
        project.id,
        projectTransitionPayload(targetStatus, transitionReason, project.version)
      );
      setTransitionReason('');
      await loadWorkspace(true);
    } catch (error) {
      handleActionError(error, false);
    } finally {
      setTransitioning(false);
    }
  };

  const updateBudgetDraft = (next: ProjectBudgetDraft) => {
    setBudgetDraft(next);
    setBudgetDirty(true);
    setBudgetErrors({});
    setActionFailure(null);
  };

  const handleAddBudgetLine = () => {
    const next = addBudgetLine(budgetDraft, String(nextLineId.current++));
    if (next === budgetDraft) return;
    updateBudgetDraft(next);
  };

  const handleRemoveBudgetLine = (id: string) => {
    if (budgetDraft.lines.length <= 1) return;
    updateBudgetDraft(removeBudgetLine(budgetDraft, id));
  };

  const handleSaveDraftBudget = async (event: React.FormEvent) => {
    event.preventDefault();
    if (savingBudget) return;
    const issues = validateProjectBudget(budgetDraft);
    setBudgetErrors(issues);
    setActionFailure(null);
    setConflictCode(null);
    if (Object.keys(issues).length > 0) {
      requestAnimationFrame(() => {
        const field = budgetFormRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]');
        field?.focus();
      });
      return;
    }

    setSavingBudget(true);
    try {
      if (openDraft) {
        await projectsApi.updateDraftBudget(
          project.id,
          openDraft.id,
          projectBudgetUpdatePayload(budgetDraft, openDraft.version)
        );
      } else {
        await projectsApi.createDraftBudget(
          project.id,
          projectBudgetCreatePayload(budgetDraft, project.version)
        );
      }
      setBudgetDirty(false);
      await loadWorkspace(false);
    } catch (error) {
      handleActionError(error, true);
    } finally {
      setSavingBudget(false);
    }
  };

  const handleDiscardDraftBudget = async () => {
    if (!openDraft || discardingDraft) return;
    setDiscardingDraft(true);
    setActionFailure(null);
    setConflictCode(null);
    try {
      await projectsApi.deleteDraftBudget(
        project.id,
        openDraft.id,
        project.version,
        openDraft.version
      );
      setConfirmDiscardDraft(false);
      setBudgetDirty(false);
      await loadWorkspace(false);
    } catch (error) {
      handleActionError(error, true);
      setConfirmDiscardDraft(false);
    } finally {
      setDiscardingDraft(false);
    }
  };

  const handleApproveDraftBudget = async () => {
    if (!openDraft || approvingDraft) return;
    setApprovingDraft(true);
    setActionFailure(null);
    setConflictCode(null);
    try {
      await projectsApi.approveBudget(
        project.id,
        openDraft.id,
        projectBudgetApprovePayload(project.version, openDraft.version)
      );
      setConfirmApproveDraft(false);
      setBudgetDirty(false);
      await loadWorkspace(false);
    } catch (error) {
      handleActionError(error, true);
      setConfirmApproveDraft(false);
    } finally {
      setApprovingDraft(false);
    }
  };

  const metadataErrorFor = (key: string) =>
    metadataErrors[key] ? copy.errors[metadataErrors[key]] : undefined;
  const budgetErrorFor = (key: string) =>
    budgetErrors[key] ? copy.errors[budgetErrors[key]] : undefined;

  return (
    <LivePage
      title={`${project.code} — ${project.name}`}
      actions={
        <Link href="/projects" data-testid="detail-back-to-projects">
          <Button type="button" variant="secondary">
            <ArrowLeft className="w-4 h-4 me-1.5" aria-hidden="true" />
            {copy.form.backToList}
          </Button>
        </Link>
      }
    >
      <div className="space-y-6" data-testid="project-detail-workspace">
        {/* Phase A Planning & Actuals Boundary Disclosure */}
        <div
          className="rounded-lg border border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] p-4 flex gap-3"
          data-testid="detail-planning-banner"
        >
          <Info className="w-5 h-5 shrink-0 mt-0.5 text-[var(--color-brand-primary)]" aria-hidden="true" />
          <div className="min-w-0 space-y-1">
            <p className="font-semibold text-[var(--color-brand-primary)]">{copy.planningBannerTitle}</p>
            <p className="text-sm">{copy.planningDisclosure}</p>
            <p className="text-xs font-medium text-[var(--color-text-secondary)]">
              {copy.actualsUnavailableNotice}
            </p>
            <p className="text-xs text-[var(--color-text-muted)]">{copy.limitsDisclosure}</p>
          </div>
        </div>

        {/* Role / Lifecycle Read-Only Notices */}
        {!canWrite && (
          <div
            className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-950"
            data-testid="detail-readonly-role-banner"
          >
            {copy.readOnlyRoleNotice}
          </div>
        )}
        {project.status === 'ARCHIVED' && (
          <div
            className="rounded-lg border border-slate-300 bg-slate-100 p-3 text-sm text-slate-800"
            data-testid="detail-archived-banner"
          >
            {copy.archivedReadOnlyNotice}
          </div>
        )}
        {(project.status === 'COMPLETED' || project.status === 'CANCELLED') && (
          <div
            className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900"
            data-testid="detail-closed-banner"
          >
            {copy.closedReadOnlyNotice}
          </div>
        )}

        {/* Conflict Reload Banner (Preserves unsaved draft edits) */}
        {conflictCode && (
          <div
            role="alert"
            className="rounded-lg border border-amber-400 bg-amber-50 p-4 space-y-3"
            data-testid="detail-conflict-banner"
          >
            <div className="flex items-start gap-2.5">
              <AlertTriangle className="w-5 h-5 text-amber-700 shrink-0 mt-0.5" aria-hidden="true" />
              <div className="space-y-1">
                <p className="font-semibold text-sm text-amber-950">{copy.conflicts.bannerTitle}</p>
                <p className="text-sm text-amber-900">{copy.conflicts[conflictCode]}</p>
              </div>
            </div>
            <Button
              type="button"
              variant="secondary"
              onClick={() => void loadWorkspace(true)}
              data-testid="reload-latest-state-button"
            >
              <RefreshCw className="w-4 h-4 me-1.5" aria-hidden="true" />
              {copy.conflicts.reloadLatest}
            </Button>
          </div>
        )}

        {Boolean(actionFailure) && !conflictCode && (
          <div
            role="alert"
            className="rounded-lg border border-rose-300 bg-rose-50 p-3 text-sm text-rose-900"
            data-testid="detail-action-error"
          >
            {localizedError(actionFailure, t)}
          </div>
        )}

        {/* Section 1: Project Metadata & Edit */}
        <Card className="space-y-4" data-testid="project-metadata-card">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="space-y-1.5 min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span
                  dir="ltr"
                  className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-mono font-semibold bg-slate-100 text-slate-800 border border-slate-300"
                  data-testid="detail-project-code"
                >
                  {project.code}
                </span>
                <span
                  className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-semibold bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)] border border-[var(--color-brand-border)]"
                  data-testid="detail-project-kind"
                >
                  {copy.kinds[project.kind]}
                </span>
                <span
                  className={`inline-flex items-center px-2.5 py-0.5 rounded text-xs font-semibold border ${statusBadgeClass(project.status)}`}
                  data-testid="detail-project-status"
                >
                  {copy.statuses[project.status]}
                </span>
                <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold uppercase tracking-wider bg-indigo-50 text-indigo-800 border border-indigo-200">
                  {copy.form.currencyFixedLabel}
                </span>
              </div>
              <h2 className="text-xl font-bold break-words" data-testid="detail-project-name">
                {project.name}
              </h2>
              {project.description && (
                <p className="text-sm text-[var(--color-text-secondary)] whitespace-pre-line break-words">
                  {project.description}
                </p>
              )}
              <div className="flex flex-wrap gap-4 text-xs text-[var(--color-text-muted)] pt-1">
                <span>
                  {copy.fields.startDate}: <strong dir="ltr">{project.startDate ?? '—'}</strong>
                </span>
                <span>
                  {copy.fields.targetEndDate}: <strong dir="ltr">{project.targetEndDate ?? '—'}</strong>
                </span>
              </div>
            </div>

            {canMutate && !editingMetadata && (
              <div className="flex flex-wrap items-center gap-2">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    setMetadataDraft(metadataFromProject(project));
                    setMetadataErrors({});
                    setEditingMetadata(true);
                  }}
                  data-testid="edit-metadata-button"
                >
                  {copy.form.editMetadata}
                </Button>
                {project.canDeleteDraftProject && (
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => setConfirmDeleteProject(true)}
                    data-testid="delete-draft-project-button"
                  >
                    <Trash2 className="w-4 h-4 me-1" aria-hidden="true" />
                    {copy.form.deleteDraftProject}
                  </Button>
                )}
              </div>
            )}
          </div>

          {confirmDeleteProject && (
            <div
              className="rounded-lg border border-rose-300 bg-rose-50 p-4 space-y-3"
              data-testid="confirm-delete-project-dialog"
            >
              <p className="text-sm font-medium text-rose-950">{copy.form.deleteDraftProjectConfirm}</p>
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  onClick={handleDeleteDraftProject}
                  disabled={deletingProject}
                  data-testid="confirm-delete-project-submit"
                >
                  {t.common.delete}
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setConfirmDeleteProject(false)}
                >
                  {t.common.cancel}
                </Button>
              </div>
            </div>
          )}

          {editingMetadata && metadataDraft && (
            <form
              onSubmit={saveMetadataChanges}
              noValidate
              className="space-y-4 pt-4 border-t border-[var(--color-border-subtle)]"
              data-testid="edit-metadata-form"
            >
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input
                  dir="ltr"
                  label={copy.fields.code}
                  value={metadataDraft.code}
                  maxLength={40}
                  required
                  data-testid="edit-project-code-input"
                  error={metadataErrorFor('code')}
                  onChange={e => setMetadataDraft({ ...metadataDraft, code: e.target.value })}
                />
                <Select
                  label={copy.fields.kind}
                  value={metadataDraft.kind}
                  data-testid="edit-project-kind-select"
                  error={metadataErrorFor('kind')}
                  options={projectKinds.map(k => ({ value: k, label: copy.kinds[k] }))}
                  onChange={e =>
                    setMetadataDraft({ ...metadataDraft, kind: e.target.value as ProjectKind })
                  }
                />
              </div>
              <Input
                label={copy.fields.name}
                value={metadataDraft.name}
                maxLength={160}
                required
                data-testid="edit-project-name-input"
                error={metadataErrorFor('name')}
                onChange={e => setMetadataDraft({ ...metadataDraft, name: e.target.value })}
              />
              <div className="space-y-1.5">
                <label
                  htmlFor="edit-project-description"
                  className="block text-sm font-medium text-[var(--color-text-primary)]"
                >
                  {copy.fields.description}
                </label>
                <textarea
                  id="edit-project-description"
                  rows={2}
                  maxLength={1000}
                  value={metadataDraft.description}
                  data-testid="edit-project-description-input"
                  onChange={e => setMetadataDraft({ ...metadataDraft, description: e.target.value })}
                  className="w-full rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-[var(--color-surface-card)] px-3 py-2 text-sm"
                />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input
                  type="date"
                  dir="ltr"
                  label={copy.fields.startDate}
                  value={metadataDraft.startDate}
                  data-testid="edit-project-start-date-input"
                  error={metadataErrorFor('startDate')}
                  onChange={e => setMetadataDraft({ ...metadataDraft, startDate: e.target.value })}
                />
                <Input
                  type="date"
                  dir="ltr"
                  label={copy.fields.targetEndDate}
                  value={metadataDraft.targetEndDate}
                  data-testid="edit-project-target-end-date-input"
                  error={metadataErrorFor('targetEndDate')}
                  onChange={e =>
                    setMetadataDraft({ ...metadataDraft, targetEndDate: e.target.value })
                  }
                />
              </div>
              <div className="flex flex-wrap gap-2">
                <Button type="submit" disabled={savingMetadata} data-testid="save-metadata-submit">
                  {savingMetadata ? copy.form.savingMetadata : copy.form.saveMetadata}
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setEditingMetadata(false)}
                >
                  {copy.form.cancelEdit}
                </Button>
              </div>
            </form>
          )}
        </Card>

        {/* Section 2: Server-Authoritative Planned Financial Summary */}
        <Card className="space-y-4" data-testid="planned-financial-summary-card">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-lg font-semibold">{copy.summaryCards.activeApprovedRevision}</h2>
              <p className="text-xs text-[var(--color-text-muted)]">
                {copy.summaryCards.precisionNote}
              </p>
            </div>
            <span className="inline-flex items-center px-2.5 py-1 rounded text-xs font-bold uppercase tracking-wider bg-indigo-50 text-indigo-800 border border-indigo-200">
              {copy.summaryCards.authoritativeBadge}
            </span>
          </div>

          {activeApproved ? (
            <>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <div
                  className="rounded-lg border border-[var(--color-border-default)] bg-[var(--color-surface-canvas)] p-4 space-y-1"
                  data-testid="summary-planned-revenue"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-medium text-[var(--color-text-muted)]">
                      {copy.summaryCards.plannedRevenue}
                    </span>
                    <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)]">
                      {copy.summaryCards.plannedBadge}
                    </span>
                  </div>
                  <p dir="ltr" className="text-lg font-bold break-all">
                    {formatProjectMoney(activeApproved.plannedRevenue, locale)}
                  </p>
                </div>

                <div
                  className="rounded-lg border border-[var(--color-border-default)] bg-[var(--color-surface-canvas)] p-4 space-y-1"
                  data-testid="summary-planned-cost"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-medium text-[var(--color-text-muted)]">
                      {copy.summaryCards.plannedCost}
                    </span>
                    <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)]">
                      {copy.summaryCards.plannedBadge}
                    </span>
                  </div>
                  <p dir="ltr" className="text-lg font-bold break-all">
                    {formatProjectMoney(activeApproved.plannedCost, locale)}
                  </p>
                </div>

                <div
                  className="rounded-lg border border-[var(--color-border-default)] bg-[var(--color-surface-canvas)] p-4 space-y-1"
                  data-testid="summary-planned-margin"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-medium text-[var(--color-text-muted)]">
                      {copy.summaryCards.plannedMargin}
                    </span>
                    <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)]">
                      {copy.summaryCards.plannedBadge}
                    </span>
                  </div>
                  <p dir="ltr" className="text-lg font-bold break-all">
                    {formatProjectMoney(activeApproved.plannedMargin, locale)}
                  </p>
                </div>

                <div
                  className="rounded-lg border border-[var(--color-border-default)] bg-[var(--color-surface-canvas)] p-4 space-y-1"
                  data-testid="summary-planned-margin-pct"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-medium text-[var(--color-text-muted)]">
                      {copy.summaryCards.plannedMarginPct}
                    </span>
                    <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)]">
                      {copy.summaryCards.plannedBadge}
                    </span>
                  </div>
                  <p dir="ltr" className="text-lg font-bold break-all">
                    {activeApproved.plannedMarginPct !== null
                      ? formatProjectPercent(activeApproved.plannedMarginPct)
                      : '—'}
                  </p>
                  {activeApproved.unavailableReasons.plannedMarginPct === 'ZERO_PLANNED_REVENUE' && (
                    <p className="text-xs text-[var(--color-text-muted)]">
                      {copy.summaryCards.zeroPlannedRevenueReason}
                    </p>
                  )}
                </div>
              </div>

              <div className="space-y-2 pt-2">
                <h3 className="text-sm font-semibold">{copy.summaryCards.categoryBreakdown}</h3>
                <div className="grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-5 gap-2.5">
                  {projectCostCategories.map(category => (
                    <div
                      key={category}
                      className="rounded border border-[var(--color-border-subtle)] p-2.5 bg-[var(--color-surface-canvas)]"
                      data-testid={`summary-category-${category}`}
                    >
                      <div className="flex items-center justify-between text-xs text-[var(--color-text-muted)]">
                        <span>{copy.categories[category]}</span>
                        <span className="text-[10px] font-semibold">
                          {copy.summaryCards.plannedBadge}
                        </span>
                      </div>
                      <p dir="ltr" className="mt-1 text-sm font-semibold break-all">
                        {formatProjectMoney(
                          activeApproved.categorySubtotals[category] ?? '0.00',
                          locale
                        )}
                      </p>
                    </div>
                  ))}
                </div>
              </div>
            </>
          ) : (
            <p className="text-sm text-[var(--color-text-muted)]" data-testid="no-approved-budget-msg">
              {copy.summaryCards.noApprovedBudget}
            </p>
          )}
        </Card>

        {/* Section 3: Budget Editor (Open Draft or Create New Revision) */}
        {canMutate && (
          <Card className="space-y-5" data-testid="budget-editor-card">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold">
                  {openDraft
                    ? `${copy.budgetEditor.editingDraftTitle} (${copy.budgetHistory.revisionPrefix} ${openDraft.revision})`
                    : copy.budgetEditor.sectionTitle}
                </h2>
                <p className="text-xs text-[var(--color-text-muted)]">
                  {copy.budgetEditor.decimalHelp}
                </p>
              </div>
              {!openDraft && !budgetEditorOpen && (
                <Button
                  type="button"
                  onClick={() => {
                    if (activeApproved) {
                      setBudgetDraft(draftFromBudgetVersion(activeApproved));
                      nextLineId.current = activeApproved.lines.length + 10;
                    } else {
                      setBudgetDraft(initialBudgetDraft());
                      nextLineId.current = 10;
                    }
                    setBudgetEditorOpen(true);
                  }}
                  data-testid="start-new-budget-revision-button"
                >
                  <Plus className="w-4 h-4 me-1.5" aria-hidden="true" />
                  {activeApproved
                    ? copy.budgetEditor.startRevisionFromApproved
                    : copy.budgetEditor.startBlankDraft}
                </Button>
              )}
            </div>

            {(openDraft || budgetEditorOpen) && (
              <form
                ref={budgetFormRef}
                onSubmit={handleSaveDraftBudget}
                noValidate
                className="space-y-5"
                data-testid="budget-editor-form"
              >
                <div className="max-w-md">
                  <Input
                    {...decimalInputProps}
                    label={copy.fields.plannedRevenue}
                    helperText={copy.fields.plannedRevenueHelp}
                    value={budgetDraft.plannedRevenue}
                    maxLength={25}
                    required
                    data-testid="budget-planned-revenue-input"
                    error={budgetErrorFor('plannedRevenue')}
                    onChange={e =>
                      updateBudgetDraft({ ...budgetDraft, plannedRevenue: e.target.value })
                    }
                  />
                </div>

                <div className="space-y-4">
                  {budgetDraft.lines.map((line, index) => (
                    <fieldset
                      key={line.id}
                      data-testid={`budget-line-${index}`}
                      className="min-w-0 rounded-lg border border-[var(--color-border-default)] p-3 sm:p-4 space-y-3"
                    >
                      <legend className="px-2 text-sm font-semibold">
                        {copy.budgetEditor.linePrefix} {index + 1}
                      </legend>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <Input
                          label={copy.fields.lineDescription}
                          value={line.description}
                          maxLength={200}
                          required
                          data-testid={`budget-line-description-${index}`}
                          error={budgetErrorFor(`lines[${index}].description`)}
                          onChange={e =>
                            updateBudgetDraft(
                              changeBudgetLine(budgetDraft, line.id, {
                                description: e.target.value,
                              })
                            )
                          }
                        />
                        <Select
                          label={copy.fields.lineCategory}
                          value={line.category}
                          data-testid={`budget-line-category-${index}`}
                          error={budgetErrorFor(`lines[${index}].category`)}
                          options={projectCostCategories.map(c => ({
                            value: c,
                            label: copy.categories[c],
                          }))}
                          onChange={e =>
                            updateBudgetDraft(
                              changeBudgetLine(budgetDraft, line.id, {
                                category: e.target.value as ProjectCostCategory,
                              })
                            )
                          }
                        />
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                        <Input
                          {...decimalInputProps}
                          label={copy.fields.lineQuantity}
                          value={line.quantity}
                          maxLength={25}
                          required
                          data-testid={`budget-line-quantity-${index}`}
                          error={budgetErrorFor(`lines[${index}].quantity`)}
                          onChange={e =>
                            updateBudgetDraft(
                              changeBudgetLine(budgetDraft, line.id, {
                                quantity: e.target.value,
                              })
                            )
                          }
                        />
                        <Input
                          label={copy.fields.lineUnit}
                          value={line.unit}
                          maxLength={40}
                          required
                          data-testid={`budget-line-unit-${index}`}
                          error={budgetErrorFor(`lines[${index}].unit`)}
                          onChange={e =>
                            updateBudgetDraft(
                              changeBudgetLine(budgetDraft, line.id, {
                                unit: e.target.value,
                              })
                            )
                          }
                        />
                        <Input
                          {...decimalInputProps}
                          label={copy.fields.lineUnitCost}
                          value={line.unitCost}
                          maxLength={25}
                          required
                          data-testid={`budget-line-unit-cost-${index}`}
                          error={budgetErrorFor(`lines[${index}].unitCost`)}
                          onChange={e =>
                            updateBudgetDraft(
                              changeBudgetLine(budgetDraft, line.id, {
                                unitCost: e.target.value,
                              })
                            )
                          }
                        />
                      </div>

                      <div className="flex flex-wrap items-center justify-between gap-2 pt-1 text-xs">
                        <span className="text-[var(--color-text-muted)]">
                          {copy.fields.plannedLineCost}:{' '}
                          <strong dir="ltr" data-testid={`budget-line-preview-cost-${index}`}>
                            {formatProjectMoney(preview.lineCosts[index] ?? null, locale)}
                          </strong>
                        </span>
                        {budgetDraft.lines.length > 1 && (
                          <Button
                            type="button"
                            variant="secondary"
                            onClick={() => handleRemoveBudgetLine(line.id)}
                            data-testid={`remove-budget-line-${index}`}
                          >
                            <Trash2 className="w-4 h-4 me-1" aria-hidden="true" />
                            {copy.budgetEditor.removeLine}
                          </Button>
                        )}
                      </div>
                    </fieldset>
                  ))}
                </div>

                {/* Live Draft UX Preview Box */}
                <div
                  className="rounded-lg border border-dashed border-[var(--color-border-default)] bg-[var(--color-surface-canvas)] p-4 space-y-2"
                  data-testid="budget-draft-preview-box"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-bold uppercase tracking-wider text-amber-800">
                      {copy.summaryCards.previewBadge}
                    </span>
                    <span className="text-xs font-semibold text-[var(--color-brand-primary)]">
                      {copy.summaryCards.plannedBadge}
                    </span>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 text-sm">
                    <div>
                      <span className="text-xs text-[var(--color-text-muted)] block">
                        {copy.summaryCards.plannedRevenue}
                      </span>
                      <strong dir="ltr" data-testid="preview-planned-revenue">
                        {formatProjectMoney(preview.plannedRevenue, locale)}
                      </strong>
                    </div>
                    <div>
                      <span className="text-xs text-[var(--color-text-muted)] block">
                        {copy.summaryCards.plannedCost}
                      </span>
                      <strong dir="ltr" data-testid="preview-planned-cost">
                        {formatProjectMoney(preview.plannedCost, locale)}
                      </strong>
                    </div>
                    <div>
                      <span className="text-xs text-[var(--color-text-muted)] block">
                        {copy.summaryCards.plannedMargin}
                      </span>
                      <strong dir="ltr" data-testid="preview-planned-margin">
                        {formatProjectMoney(preview.plannedMargin, locale)}
                      </strong>
                    </div>
                    <div>
                      <span className="text-xs text-[var(--color-text-muted)] block">
                        {copy.summaryCards.plannedMarginPct}
                      </span>
                      <strong dir="ltr" data-testid="preview-planned-margin-pct">
                        {preview.plannedMarginPct !== null
                          ? formatProjectPercent(preview.plannedMarginPct)
                          : '—'}
                      </strong>
                      {preview.zeroPlannedRevenue && (
                        <span className="block text-[11px] text-[var(--color-text-muted)]">
                          {copy.summaryCards.zeroPlannedRevenueReason}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-3">
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={handleAddBudgetLine}
                    disabled={budgetDraft.lines.length >= MAX_PROJECT_BUDGET_LINES}
                    data-testid="add-budget-line-button"
                  >
                    <Plus className="w-4 h-4 me-1.5" aria-hidden="true" />
                    {copy.budgetEditor.addLine}
                  </Button>

                  <div className="flex flex-wrap items-center gap-2">
                    <Button
                      type="submit"
                      disabled={savingBudget}
                      data-testid="save-draft-budget-button"
                    >
                      {savingBudget
                        ? copy.budgetEditor.savingDraft
                        : openDraft
                        ? copy.budgetEditor.saveDraftChanges
                        : copy.budgetEditor.saveNewDraft}
                    </Button>

                    {openDraft && !budgetDirty && (
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => setConfirmApproveDraft(true)}
                        data-testid="approve-draft-budget-button"
                      >
                        <CheckCircle2 className="w-4 h-4 me-1.5" aria-hidden="true" />
                        {copy.budgetHistory.approveAction}
                      </Button>
                    )}

                    {openDraft && (
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => setConfirmDiscardDraft(true)}
                        data-testid="discard-draft-budget-button"
                      >
                        <Trash2 className="w-4 h-4 me-1" aria-hidden="true" />
                        {copy.budgetEditor.discardDraft}
                      </Button>
                    )}
                  </div>
                </div>
              </form>
            )}

            {/* Approval Confirmation Box */}
            {openDraft && confirmApproveDraft && (
              <div
                className="rounded-lg border border-emerald-300 bg-emerald-50 p-4 space-y-3"
                data-testid="confirm-approve-budget-dialog"
              >
                <p className="text-sm font-medium text-emerald-950">
                  {activeApproved
                    ? copy.budgetHistory.approveConfirmWithPrior
                    : copy.budgetHistory.approveConfirmFirst}
                </p>
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    onClick={handleApproveDraftBudget}
                    disabled={approvingDraft}
                    data-testid="confirm-approve-budget-submit"
                  >
                    {approvingDraft ? copy.budgetHistory.approving : t.common.confirm}
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => setConfirmApproveDraft(false)}
                  >
                    {t.common.cancel}
                  </Button>
                </div>
              </div>
            )}

            {/* Discard Draft Confirmation Box */}
            {openDraft && confirmDiscardDraft && (
              <div
                className="rounded-lg border border-rose-300 bg-rose-50 p-4 space-y-3"
                data-testid="confirm-discard-budget-dialog"
              >
                <p className="text-sm font-medium text-rose-950">
                  {copy.budgetEditor.discardDraftConfirm}
                </p>
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    onClick={handleDiscardDraftBudget}
                    disabled={discardingDraft}
                    data-testid="confirm-discard-budget-submit"
                  >
                    {t.common.delete}
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => setConfirmDiscardDraft(false)}
                  >
                    {t.common.cancel}
                  </Button>
                </div>
              </div>
            )}
          </Card>
        )}

        {/* Section 4: Budget Revision History (Read-Only for Approved & Superseded) */}
        <Card className="space-y-4" data-testid="budget-history-card">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <History className="w-5 h-5 text-[var(--color-brand-primary)]" aria-hidden="true" />
              <h2 className="text-lg font-semibold">{copy.budgetHistory.title}</h2>
            </div>
            <p className="text-xs text-[var(--color-text-muted)]">
              {copy.budgetHistory.immutableNotice}
            </p>
          </div>

          {budgets.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]" data-testid="budget-history-empty">
              {copy.budgetHistory.emptyHistory}
            </p>
          ) : (
            <div className="space-y-3" data-testid="budget-history-list">
              {budgets.map(version => {
                const readOnly = isBudgetVersionReadOnly(version.status);
                const expanded = Boolean(expandedRevisionIds[version.id]);
                return (
                  <div
                    key={version.id}
                    data-testid={`budget-revision-${version.revision}`}
                    className="rounded-lg border border-[var(--color-border-default)] p-4 space-y-3 bg-[var(--color-surface-canvas)]"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-semibold text-sm">
                          {copy.budgetHistory.revisionPrefix} {version.revision}
                        </span>
                        <span
                          className={`inline-flex items-center px-2.5 py-0.5 rounded text-xs font-semibold border ${budgetStatusBadgeClass(version.status)}`}
                          data-testid={`budget-revision-status-${version.revision}`}
                        >
                          {copy.budgetStatuses[version.status]}
                        </span>
                        {readOnly && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-300">
                            <Lock className="w-3 h-3" aria-hidden="true" />
                            {copy.budgetHistory.readOnlyBadge}
                          </span>
                        )}
                      </div>

                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() =>
                          setExpandedRevisionIds(prev => ({
                            ...prev,
                            [version.id]: !prev[version.id],
                          }))
                        }
                        data-testid={`toggle-revision-lines-${version.revision}`}
                      >
                        {expanded ? copy.budgetHistory.hideLines : copy.budgetHistory.inspectRevision}
                      </Button>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 text-sm">
                      <div>
                        <span className="text-xs text-[var(--color-text-muted)] block">
                          {copy.summaryCards.plannedRevenue} ({copy.summaryCards.plannedBadge})
                        </span>
                        <strong dir="ltr">{formatProjectMoney(version.plannedRevenue, locale)}</strong>
                      </div>
                      <div>
                        <span className="text-xs text-[var(--color-text-muted)] block">
                          {copy.summaryCards.plannedCost} ({copy.summaryCards.plannedBadge})
                        </span>
                        <strong dir="ltr">{formatProjectMoney(version.plannedCost, locale)}</strong>
                      </div>
                      <div>
                        <span className="text-xs text-[var(--color-text-muted)] block">
                          {copy.summaryCards.plannedMargin} ({copy.summaryCards.plannedBadge})
                        </span>
                        <strong dir="ltr">{formatProjectMoney(version.plannedMargin, locale)}</strong>
                      </div>
                      <div>
                        <span className="text-xs text-[var(--color-text-muted)] block">
                          {copy.summaryCards.plannedMarginPct} ({copy.summaryCards.plannedBadge})
                        </span>
                        <strong dir="ltr">
                          {version.plannedMarginPct !== null
                            ? formatProjectPercent(version.plannedMarginPct)
                            : '—'}
                        </strong>
                      </div>
                    </div>

                    <div className="flex flex-wrap gap-4 text-xs text-[var(--color-text-muted)]">
                      <span>
                        {copy.budgetHistory.createdAt}: {formatTimestamp(version.createdAt, locale)}
                      </span>
                      {version.approvedAt && (
                        <span>
                          {copy.budgetHistory.approvedAt}:{' '}
                          {formatTimestamp(version.approvedAt, locale)}
                        </span>
                      )}
                      {version.supersededAt && (
                        <span>
                          {copy.budgetHistory.supersededAt}:{' '}
                          {formatTimestamp(version.supersededAt, locale)}
                        </span>
                      )}
                    </div>

                    {expanded && (
                      <div
                        className="pt-3 border-t border-[var(--color-border-subtle)] space-y-2"
                        data-testid={`revision-lines-panel-${version.revision}`}
                      >
                        {version.lines.map(line => (
                          <div
                            key={line.id}
                            className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded border border-[var(--color-border-subtle)] bg-[var(--color-surface-card)] p-2.5 text-xs"
                          >
                            <div className="min-w-0 space-y-0.5">
                              <span className="font-semibold">
                                #{line.lineNumber} · {copy.categories[line.category]} —{' '}
                                {line.description}
                              </span>
                              <p dir="ltr" className="text-[var(--color-text-muted)]">
                                {line.quantity} {line.unit} × {formatProjectMoney(line.unitCost, locale)}
                              </p>
                            </div>
                            <strong dir="ltr" className="shrink-0">
                              {formatProjectMoney(line.plannedLineCost, locale)}
                            </strong>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </Card>

        {/* Section 5: Project Lifecycle Transitions */}
        {canWrite && (
          <Card className="space-y-4" data-testid="project-lifecycle-card">
            <h2 className="text-lg font-semibold">{copy.lifecycle.title}</h2>
            {availableTransitions.length === 0 ? (
              <p className="text-sm text-[var(--color-text-muted)]">{copy.lifecycle.noTransitions}</p>
            ) : (
              <form
                onSubmit={handleTransition}
                className="space-y-4"
                data-testid="project-transition-form"
              >
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Select
                    label={copy.lifecycle.targetStatus}
                    value={targetStatus}
                    data-testid="transition-target-status-select"
                    options={availableTransitions.map(s => ({
                      value: s,
                      label: copy.statuses[s],
                    }))}
                    onChange={e => setTargetStatus(e.target.value as ProjectStatus)}
                  />
                  <Input
                    label={copy.fields.transitionReason}
                    value={transitionReason}
                    maxLength={500}
                    data-testid="transition-reason-input"
                    onChange={e => setTransitionReason(e.target.value)}
                  />
                </div>

                {targetStatus !== '' &&
                  isTransitionBlockedByOpenDraft(targetStatus, Boolean(openDraft)) && (
                    <p
                      className="text-xs text-amber-800 font-medium"
                      data-testid="open-draft-transition-warning"
                    >
                      {copy.lifecycle.openDraftBlocksClose}
                    </p>
                  )}

                <Button
                  type="submit"
                  variant="secondary"
                  disabled={transitioning || !targetStatus}
                  data-testid="submit-project-transition-button"
                >
                  {transitioning ? copy.lifecycle.transitioning : copy.lifecycle.applyTransition}
                </Button>
              </form>
            )}
          </Card>
        )}
      </div>
    </LivePage>
  );
}
