"use client";

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Info } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { projectsApi } from '@/lib/api/projects';
import {
  initialProjectMetadata,
  isProjectConflictCode,
  mapProjectFieldErrors,
  projectCreatePayload,
  projectKinds,
  validateProjectMetadata,
  type ProjectKind,
  type ProjectMetadataDraft,
  type ProjectValidationErrors,
} from '@/lib/projects/form';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { projectsCopy } from '@/lib/i18n/translations/projects';

export function ProjectForm() {
  const { business } = useSession();
  return <ProjectFormInner key={business?.businessId ?? 'none'} />;
}

function ProjectFormInner() {
  const { locale, t } = useLanguage();
  const copy = projectsCopy(locale);
  const router = useRouter();
  const { can, business } = useSession();
  const canRead = can('FINANCIAL_DATA_READ');
  const canWrite = can('RECORD_CREATE_UPDATE');

  const [draft, setDraft] = useState<ProjectMetadataDraft>(() => initialProjectMetadata('PROJECT'));
  const [errors, setErrors] = useState<ProjectValidationErrors>({});
  const [failure, setFailure] = useState<unknown>(null);
  const [conflictCode, setConflictCode] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [dirty, setDirty] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  const activeRequest = useRef<AbortController | null>(null);

  useEffect(() => () => activeRequest.current?.abort(), []);

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const updateField = (patch: Partial<ProjectMetadataDraft>) => {
    setDraft(prev => ({ ...prev, ...patch }));
    setDirty(true);
    setErrors({});
    setFailure(null);
    setConflictCode(null);
  };

  const focusInvalid = () =>
    requestAnimationFrame(() => {
      const field = formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]');
      field?.focus();
    });

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    const issues = validateProjectMetadata(draft);
    setErrors(issues);
    setFailure(null);
    setConflictCode(null);
    if (Object.keys(issues).length > 0) {
      focusInvalid();
      return;
    }

    const controller = new AbortController();
    activeRequest.current?.abort();
    activeRequest.current = controller;
    setSubmitting(true);

    try {
      const created = await projectsApi.create(projectCreatePayload(draft), controller.signal);
      if (controller.signal.aborted) return;
      if (
        created.businessId !== business?.businessId ||
        created.baseCurrency !== 'PKR' ||
        created.planningOnly !== true
      ) {
        throw new ApiError(502, 'Invalid project creation response.');
      }
      setDirty(false);
      router.push(`/projects/${created.id}`);
    } catch (error) {
      if (controller.signal.aborted || (error instanceof Error && error.name === 'AbortError')) {
        return;
      }
      setFailure(error);
      if (error instanceof ApiError) {
        if (error.fieldErrors) {
          setErrors(mapProjectFieldErrors(error.fieldErrors));
          focusInvalid();
        }
        if (error.code === 'duplicate_project_code') {
          setErrors({ code: 'code' });
          setConflictCode('duplicate_project_code');
          focusInvalid();
        } else if (isProjectConflictCode(error.code)) {
          setConflictCode(error.code);
        }
      }
    } finally {
      if (activeRequest.current === controller) {
        activeRequest.current = null;
        setSubmitting(false);
      }
    }
  };

  const errorFor = (key: string) => (errors[key] ? copy.errors[errors[key]] : undefined);

  if (isDemoMode || !canRead) {
    return (
      <LivePage title={copy.newTitle}>
        <Card>
          <p>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</p>
        </Card>
      </LivePage>
    );
  }

  if (!canWrite) {
    return (
      <LivePage title={copy.newTitle}>
        <Card className="space-y-3" data-testid="project-create-forbidden">
          <p role="alert">{copy.readOnlyRoleNotice}</p>
          <Link href="/projects">
            <Button type="button" variant="secondary">
              {copy.form.backToList}
            </Button>
          </Link>
        </Card>
      </LivePage>
    );
  }

  return (
    <LivePage
      title={copy.newTitle}
      actions={
        <Link href="/projects" data-testid="back-to-projects-link">
          <Button type="button" variant="secondary">
            <ArrowLeft className="w-4 h-4 me-1.5" aria-hidden="true" />
            {copy.form.backToList}
          </Button>
        </Link>
      }
    >
      <div className="space-y-6" data-testid="project-create-view">
        <div className="rounded-lg border border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] p-4 flex gap-3">
          <Info className="w-5 h-5 shrink-0 mt-0.5 text-[var(--color-brand-primary)]" aria-hidden="true" />
          <div className="min-w-0 space-y-1">
            <p className="font-semibold text-[var(--color-brand-primary)]">{copy.planningBannerTitle}</p>
            <p className="text-sm">{copy.planningDisclosure}</p>
            <p className="text-xs text-[var(--color-text-muted)]">{copy.limitsDisclosure}</p>
          </div>
        </div>

        <form ref={formRef} onSubmit={submit} noValidate className="space-y-6" data-testid="project-create-form">
          <Card className="space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-lg font-semibold">{copy.form.metadataSection}</h2>
              <span className="inline-flex items-center px-2.5 py-1 rounded text-xs font-semibold bg-indigo-50 text-indigo-800 border border-indigo-200">
                {copy.form.currencyFixedLabel}
              </span>
            </div>

            {conflictCode && isProjectConflictCode(conflictCode) && (
              <div
                role="alert"
                className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-950"
                data-testid="project-create-conflict"
              >
                {copy.conflicts[conflictCode]}
              </div>
            )}

            {Boolean(failure) && !conflictCode && (
              <p role="alert" className="text-sm text-[var(--color-semantic-danger)]">
                {localizedError(failure, t)}
              </p>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                dir="ltr"
                label={copy.fields.code}
                helperText={copy.fields.codeHelp}
                value={draft.code}
                maxLength={40}
                required
                data-testid="project-code-input"
                error={errorFor('code')}
                onChange={e => updateField({ code: e.target.value })}
              />
              <Select
                label={copy.fields.kind}
                value={draft.kind}
                data-testid="project-kind-select"
                error={errorFor('kind')}
                options={projectKinds.map(k => ({ value: k, label: copy.kinds[k] }))}
                onChange={e => updateField({ kind: e.target.value as ProjectKind })}
              />
            </div>

            <Input
              label={copy.fields.name}
              value={draft.name}
              maxLength={160}
              required
              data-testid="project-name-input"
              error={errorFor('name')}
              onChange={e => updateField({ name: e.target.value })}
            />

            <div className="space-y-1.5">
              <label
                htmlFor="project-description-input"
                className="block text-sm font-medium text-[var(--color-text-primary)]"
              >
                {copy.fields.description}
              </label>
              <textarea
                id="project-description-input"
                rows={3}
                maxLength={1000}
                value={draft.description}
                data-testid="project-description-input"
                aria-invalid={Boolean(errors.description)}
                onChange={e => updateField({ description: e.target.value })}
                className="w-full rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-[var(--color-surface-card)] px-3 py-2 text-sm text-[var(--color-text-primary)] focus:outline-2 focus:outline-[var(--color-brand-primary)]"
              />
              {errorFor('description') && (
                <p role="alert" className="text-xs text-[var(--color-semantic-danger)]">
                  {errorFor('description')}
                </p>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                type="date"
                dir="ltr"
                label={copy.fields.startDate}
                value={draft.startDate}
                data-testid="project-start-date-input"
                error={errorFor('startDate')}
                onChange={e => updateField({ startDate: e.target.value })}
              />
              <Input
                type="date"
                dir="ltr"
                label={copy.fields.targetEndDate}
                value={draft.targetEndDate}
                data-testid="project-target-end-date-input"
                error={errorFor('targetEndDate')}
                onChange={e => updateField({ targetEndDate: e.target.value })}
              />
            </div>

            <div className="flex flex-wrap items-center gap-3 pt-2">
              <Button type="submit" disabled={submitting} data-testid="project-create-submit">
                {submitting ? copy.form.creating : copy.form.createSubmit}
              </Button>
              <Link href="/projects">
                <Button type="button" variant="secondary">
                  {copy.form.cancelEdit}
                </Button>
              </Link>
            </div>
          </Card>
        </form>
      </div>
    </LivePage>
  );
}
