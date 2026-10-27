"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { FolderKanban, Info, Plus } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { projectsApi, type ProjectResponse } from '@/lib/api/projects';
import {
  formatProjectMoney,
  formatProjectPercent,
  projectKinds,
  projectStatuses,
  type ProjectKind,
  type ProjectStatus,
} from '@/lib/projects/form';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { projectsCopy } from '@/lib/i18n/translations/projects';

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

export function ProjectList() {
  const { locale, t } = useLanguage();
  const copy = projectsCopy(locale);
  const { can, business } = useSession();
  const canRead = can('FINANCIAL_DATA_READ');
  const canWrite = can('RECORD_CREATE_UPDATE');
  const activeBusinessId = business?.businessId ?? null;

  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<ProjectStatus | ''>('');
  const [kindFilter, setKindFilter] = useState<ProjectKind | ''>('');
  const [includeArchived, setIncludeArchived] = useState(false);

  const [items, setItems] = useState<ProjectResponse[]>([]);
  const [loadedBusinessId, setLoadedBusinessId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<unknown>(null);
  const [attempt, setAttempt] = useState(0);
  const requestId = useRef(0);

  const reload = useCallback(() => setAttempt(v => v + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    const ticket = ++requestId.current;

    queueMicrotask(() => {
      if (controller.signal.aborted || ticket !== requestId.current) {
        return;
      }
      if (isDemoMode || !canRead || !activeBusinessId) {
        setItems([]);
        setLoadedBusinessId(null);
        setLoading(false);
        return;
      }

      setLoading(true);
      setFailure(null);

      projectsApi
        .list(
          {
            status: statusFilter || undefined,
            kind: kindFilter || undefined,
            q: query.trim() || undefined,
            includeArchived,
          },
          controller.signal
        )
        .then(response => {
          if (controller.signal.aborted || ticket !== requestId.current) {
            return;
          }
          if (
            response.businessId !== activeBusinessId ||
            response.baseCurrency !== 'PKR' ||
            response.planningOnly !== true ||
            !Array.isArray(response.items)
          ) {
            throw new ApiError(502, 'Invalid project planning list response.');
          }
          setItems(response.items);
          setLoadedBusinessId(response.businessId);
          setLoading(false);
        })
        .catch(error => {
          if (controller.signal.aborted || ticket !== requestId.current || error?.name === 'AbortError') {
            return;
          }
          setItems([]);
          setLoadedBusinessId(null);
          setFailure(error);
          setLoading(false);
        });
    });

    return () => {
      controller.abort();
    };
  }, [activeBusinessId, canRead, statusFilter, kindFilter, query, includeArchived, attempt]);

  if (isDemoMode || !canRead) {
    return (
      <LivePage title={copy.title}>
        <Card>
          <p>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</p>
        </Card>
      </LivePage>
    );
  }

  // Never show stale previous-business items while switching active business
  const visibleItems = loadedBusinessId === activeBusinessId ? items : [];

  return (
    <LivePage
      title={copy.title}
      actions={
        canWrite ? (
          <Link href="/projects/new" data-testid="create-project-button">
            <Button type="button">
              <Plus className="w-4 h-4 me-1.5" aria-hidden="true" />
              {copy.list.createButton}
            </Button>
          </Link>
        ) : undefined
      }
    >
      <div className="space-y-4" data-testid="projects-list-view">
        <p className="max-w-3xl text-[var(--color-text-secondary)]">{copy.intro}</p>

        <div
          className="rounded-lg border border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] p-4 flex gap-3"
          data-testid="projects-planning-banner"
        >
          <Info className="w-5 h-5 shrink-0 mt-0.5 text-[var(--color-brand-primary)]" aria-hidden="true" />
          <div className="min-w-0 space-y-1">
            <p className="font-semibold text-[var(--color-brand-primary)]">{copy.planningBannerTitle}</p>
            <p className="text-sm">{copy.planningDisclosure}</p>
            <p className="text-xs text-[var(--color-text-muted)]">{copy.limitsDisclosure}</p>
          </div>
        </div>

        {!canWrite && (
          <div
            className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900"
            data-testid="projects-readonly-banner"
          >
            {copy.readOnlyRoleNotice}
          </div>
        )}

        <Card className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 items-end">
            <div className="sm:col-span-2">
              <Input
                label={copy.fields.name}
                placeholder={copy.list.searchPlaceholder}
                value={query}
                onChange={e => setQuery(e.target.value)}
                data-testid="projects-search-input"
              />
            </div>
            <Select
              label={copy.list.kindFilterLabel}
              value={kindFilter}
              data-testid="projects-kind-filter"
              options={[
                { value: '', label: copy.list.allKinds },
                ...projectKinds.map(k => ({ value: k, label: copy.kinds[k] })),
              ]}
              onChange={e => setKindFilter(e.target.value as ProjectKind | '')}
            />
            <Select
              label={copy.list.statusFilterLabel}
              value={statusFilter}
              data-testid="projects-status-filter"
              options={[
                { value: '', label: copy.list.allStatuses },
                ...projectStatuses.map(s => ({ value: s, label: copy.statuses[s] })),
              ]}
              onChange={e => setStatusFilter(e.target.value as ProjectStatus | '')}
            />
          </div>
          <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
            <label className="inline-flex items-center gap-2 text-sm cursor-pointer select-none">
              <input
                type="checkbox"
                checked={includeArchived}
                onChange={e => setIncludeArchived(e.target.checked)}
                data-testid="projects-include-archived"
                className="rounded border-[var(--color-border-default)]"
              />
              <span>{copy.list.includeArchived}</span>
            </label>
            <span className="text-xs font-medium text-[var(--color-text-muted)]">
              {copy.form.currencyFixedLabel}
            </span>
          </div>
        </Card>

        {loading && (
          <Card>
            <p role="status" data-testid="projects-loading">
              {t.common.loading}
            </p>
          </Card>
        )}

        {!loading && Boolean(failure) && (
          <Card className="space-y-3" data-testid="projects-error">
            <p role="alert" className="text-sm text-[var(--color-semantic-danger)]">
              {localizedError(failure, t)}
            </p>
            <Button type="button" variant="secondary" onClick={reload}>
              {t.common.tryAgain}
            </Button>
          </Card>
        )}

        {!loading && !failure && visibleItems.length === 0 && (
          <Card className="py-10 text-center space-y-3" data-testid="projects-empty-state">
            <div className="mx-auto w-12 h-12 rounded-full bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)] flex items-center justify-center">
              <FolderKanban className="w-6 h-6" aria-hidden="true" />
            </div>
            <h2 className="text-base font-semibold">{copy.list.emptyTitle}</h2>
            <p className="text-sm text-[var(--color-text-muted)] max-w-md mx-auto">
              {copy.list.emptyDescription}
            </p>
            {canWrite && (
              <div className="pt-2">
                <Link href="/projects/new" data-testid="empty-create-project-link">
                  <Button type="button">
                    <Plus className="w-4 h-4 me-1.5" aria-hidden="true" />
                    {copy.list.createButton}
                  </Button>
                </Link>
              </div>
            )}
          </Card>
        )}

        {!loading && !failure && visibleItems.length > 0 && (
          <div className="grid grid-cols-1 gap-4" data-testid="projects-card-list">
            {visibleItems.map(project => {
              const approved = project.activeApprovedBudget;
              const openDraft = project.latestDraftBudget;
              return (
                <Card
                  key={project.id}
                  className="space-y-4 min-w-0"
                  data-testid={`project-item-${project.code}`}
                >
                  <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
                    <div className="min-w-0 space-y-1.5">
                      <div className="flex flex-wrap items-center gap-2">
                        <span
                          dir="ltr"
                          className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-mono font-semibold bg-slate-100 text-slate-800 border border-slate-300"
                        >
                          {project.code}
                        </span>
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-semibold bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)] border border-[var(--color-brand-border)]">
                          {copy.kinds[project.kind]}
                        </span>
                        <span
                          className={`inline-flex items-center px-2.5 py-0.5 rounded text-xs font-semibold border ${statusBadgeClass(project.status)}`}
                        >
                          {copy.statuses[project.status]}
                        </span>
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold uppercase tracking-wider bg-indigo-50 text-indigo-800 border border-indigo-200">
                          {copy.summaryCards.plannedBadge}
                        </span>
                      </div>
                      <h2 className="text-lg font-semibold break-words">{project.name}</h2>
                      {project.description && (
                        <p className="text-sm text-[var(--color-text-secondary)] line-clamp-2 break-words">
                          {project.description}
                        </p>
                      )}
                    </div>

                    <div className="shrink-0">
                      <Link
                        href={`/projects/${project.id}`}
                        data-testid={`open-project-${project.code}`}
                      >
                        <Button type="button" variant="secondary">
                          {copy.list.viewAction}
                        </Button>
                      </Link>
                    </div>
                  </div>

                  {approved ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-2 border-t border-[var(--color-border-subtle)]">
                      <div className="rounded-md bg-[var(--color-surface-canvas)] p-3 border border-[var(--color-border-subtle)]">
                        <div className="flex items-center justify-between gap-1">
                          <span className="text-xs text-[var(--color-text-muted)]">
                            {copy.summaryCards.plannedRevenue}
                          </span>
                          <span className="text-[10px] font-bold text-[var(--color-brand-primary)]">
                            {copy.summaryCards.plannedBadge}
                          </span>
                        </div>
                        <p dir="ltr" className="mt-1 font-semibold text-sm break-all">
                          {formatProjectMoney(approved.plannedRevenue, locale)}
                        </p>
                      </div>

                      <div className="rounded-md bg-[var(--color-surface-canvas)] p-3 border border-[var(--color-border-subtle)]">
                        <div className="flex items-center justify-between gap-1">
                          <span className="text-xs text-[var(--color-text-muted)]">
                            {copy.summaryCards.plannedCost}
                          </span>
                          <span className="text-[10px] font-bold text-[var(--color-brand-primary)]">
                            {copy.summaryCards.plannedBadge}
                          </span>
                        </div>
                        <p dir="ltr" className="mt-1 font-semibold text-sm break-all">
                          {formatProjectMoney(approved.plannedCost, locale)}
                        </p>
                      </div>

                      <div className="rounded-md bg-[var(--color-surface-canvas)] p-3 border border-[var(--color-border-subtle)]">
                        <div className="flex items-center justify-between gap-1">
                          <span className="text-xs text-[var(--color-text-muted)]">
                            {copy.summaryCards.plannedMargin}
                          </span>
                          <span className="text-[10px] font-bold text-[var(--color-brand-primary)]">
                            {copy.summaryCards.plannedBadge}
                          </span>
                        </div>
                        <p dir="ltr" className="mt-1 font-semibold text-sm break-all">
                          {formatProjectMoney(approved.plannedMargin, locale)}
                        </p>
                      </div>

                      <div className="rounded-md bg-[var(--color-surface-canvas)] p-3 border border-[var(--color-border-subtle)]">
                        <div className="flex items-center justify-between gap-1">
                          <span className="text-xs text-[var(--color-text-muted)]">
                            {copy.summaryCards.plannedMarginPct}
                          </span>
                          <span className="text-[10px] font-bold text-[var(--color-brand-primary)]">
                            {copy.summaryCards.plannedBadge}
                          </span>
                        </div>
                        <p dir="ltr" className="mt-1 font-semibold text-sm break-all">
                          {approved.plannedMarginPct !== null
                            ? formatProjectPercent(approved.plannedMarginPct)
                            : '—'}
                        </p>
                      </div>
                    </div>
                  ) : (
                    <div className="pt-2 border-t border-[var(--color-border-subtle)] text-xs text-[var(--color-text-muted)]">
                      {copy.list.noApprovedBudget}
                    </div>
                  )}

                  <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-[var(--color-text-muted)]">
                    <div className="flex flex-wrap items-center gap-3">
                      {approved && (
                        <span>
                          {copy.summaryCards.activeApprovedRevision}:{' '}
                          <strong>
                            {copy.budgetHistory.revisionPrefix} {approved.revision}
                          </strong>
                        </span>
                      )}
                      {openDraft && (
                        <span className="text-amber-800 font-medium">
                          {copy.list.openDraftPresent} ({copy.budgetHistory.revisionPrefix}{' '}
                          {openDraft.revision})
                        </span>
                      )}
                      <span>
                        {copy.list.revisionsCount}: {project.revisionCount}
                      </span>
                    </div>
                    {(project.startDate || project.targetEndDate) && (
                      <span dir="ltr">
                        {project.startDate ?? '—'} → {project.targetEndDate ?? '—'}
                      </span>
                    )}
                  </div>
                </Card>
              );
            })}
          </div>
        )}
      </div>
    </LivePage>
  );
}
