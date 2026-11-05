"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Boxes, Calculator, Info, Plus } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { inventoryApi, type InventoryItemResponse } from '@/lib/api/inventory';
import {
  draftFromItem,
  initialItemDraft,
  inventoryItemKinds,
  isInventoryConflictCode,
  itemPayload,
  itemUpdatePayload,
  mapInventoryFieldErrors,
  validateItemDraft,
  type InventoryItemDraft,
  type InventoryItemKind,
  type InventoryValidationErrors,
} from '@/lib/inventory/form';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { inventoryCopy, type InventoryCopy } from '@/lib/i18n/translations/inventory';

const LIST_LIMIT = 500;

type Editor = { mode: 'create' } | { mode: 'edit'; item: InventoryItemResponse } | null;

export function InventoryRegister() {
  const { business } = useSession();
  // Remount on business switch so no draft or list from another business survives.
  return <InventoryRegisterInner key={business?.businessId ?? 'none'} />;
}

function InventoryRegisterInner() {
  const { locale, t } = useLanguage();
  const copy = inventoryCopy(locale);
  const { can, business } = useSession();
  const canRead = can('FINANCIAL_DATA_READ');
  const canWrite = can('RECORD_CREATE_UPDATE');
  const activeBusinessId = business?.businessId ?? null;

  const [query, setQuery] = useState('');
  const [kindFilter, setKindFilter] = useState<InventoryItemKind | ''>('');
  const [includeArchived, setIncludeArchived] = useState(false);
  const [items, setItems] = useState<InventoryItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<unknown>(null);
  const [attempt, setAttempt] = useState(0);
  const [editor, setEditor] = useState<Editor>(null);
  const [notice, setNotice] = useState('');
  const [actionError, setActionError] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const requestId = useRef(0);

  const reload = useCallback(() => setAttempt(value => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    const ticket = ++requestId.current;
    queueMicrotask(() => {
      if (controller.signal.aborted || ticket !== requestId.current) return;
      if (isDemoMode || !canRead || !activeBusinessId) {
        setItems([]);
        setLoading(false);
        return;
      }
      setLoading(true);
      setFailure(null);
      inventoryApi
        .list({ kind: kindFilter || undefined, q: query.trim() || undefined, includeArchived }, controller.signal)
        .then(response => {
          if (controller.signal.aborted || ticket !== requestId.current) return;
          if (response.businessId !== activeBusinessId || response.catalogOnly !== true || !Array.isArray(response.items)) {
            throw new ApiError(502, 'Invalid inventory catalog response.');
          }
          setItems(response.items);
          setLoading(false);
        })
        .catch(error => {
          if (controller.signal.aborted || ticket !== requestId.current || error?.name === 'AbortError') return;
          setItems([]);
          setFailure(error);
          setLoading(false);
        });
    });
    return () => controller.abort();
  }, [activeBusinessId, canRead, kindFilter, query, includeArchived, attempt]);

  if (isDemoMode || !canRead) {
    return (
      <LivePage title={copy.title}>
        <Card><p>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</p></Card>
      </LivePage>
    );
  }

  const archive = async (item: InventoryItemResponse) => {
    if (busyId || !window.confirm(copy.list.archiveConfirm)) return;
    setBusyId(item.id);
    setActionError('');
    setNotice('');
    try {
      await inventoryApi.archive(item.id, item.version);
      setNotice(copy.list.archived);
      reload();
    } catch (error) {
      setActionError(conflictMessage(error, copy) ?? localizedError(error, t));
    } finally {
      setBusyId(null);
    }
  };

  const saved = (message: string) => {
    setEditor(null);
    setNotice(message);
    reload();
  };

  return (
    <LivePage
      title={copy.title}
      actions={canWrite && !editor ? (
        <Button type="button" onClick={() => { setNotice(''); setEditor({ mode: 'create' }); }} data-testid="inventory-create-button">
          <Plus className="w-4 h-4 me-1.5" aria-hidden="true" />
          {copy.list.createButton}
        </Button>
      ) : undefined}
    >
      <div className="space-y-4 min-w-0" data-testid="inventory-register">
        <p className="max-w-3xl text-[var(--color-text-secondary)]">{copy.intro}</p>

        <div className="rounded-lg border border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] p-4 flex gap-3 min-w-0"
          data-testid="inventory-catalog-banner">
          <Info className="w-5 h-5 shrink-0 mt-0.5 text-[var(--color-brand-primary)]" aria-hidden="true" />
          <div className="min-w-0 space-y-1">
            <p className="font-semibold text-[var(--color-brand-primary)]">{copy.catalogBannerTitle}</p>
            <p className="text-sm">{copy.catalogDisclosure}</p>
            <p className="text-xs text-[var(--color-text-muted)]">{copy.stockNotTracked}</p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <Link href="/inventory/valuation-preview" data-testid="inventory-preview-link"
            className="inline-flex items-center gap-1.5 text-sm font-semibold text-[var(--color-brand-primary)] hover:underline">
            <Calculator className="w-4 h-4" aria-hidden="true" />
            {copy.previewLink}
          </Link>
        </div>

        {!canWrite && (
          <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" data-testid="inventory-readonly-banner">
            {copy.readOnlyRoleNotice}
          </div>
        )}

        {notice && <p role="status" className="text-sm font-medium text-[var(--color-status-success)]" data-testid="inventory-notice">{notice}</p>}
        {actionError && <p role="alert" className="text-sm text-[var(--color-status-error)]" data-testid="inventory-action-error">{actionError}</p>}

        {canWrite && editor && (
          <InventoryItemForm
            key={editor.mode === 'edit' ? `${editor.item.id}-${editor.item.version}` : 'create'}
            item={editor.mode === 'edit' ? editor.item : null}
            onCancel={() => setEditor(null)}
            onSaved={saved}
          />
        )}

        <Card className="space-y-4 min-w-0">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 items-end">
            <div className="sm:col-span-2 lg:col-span-1 min-w-0">
              <Input label={copy.list.searchLabel} placeholder={copy.list.searchPlaceholder} value={query}
                onChange={e => setQuery(e.target.value)} data-testid="inventory-search-input" maxLength={160} />
            </div>
            <Select label={copy.list.kindFilterLabel} value={kindFilter} data-testid="inventory-kind-filter"
              options={[{ value: '', label: copy.list.allKinds }, ...inventoryItemKinds.map(k => ({ value: k, label: copy.kinds[k] }))]}
              onChange={e => setKindFilter(e.target.value as InventoryItemKind | '')} />
            <label className="inline-flex items-center gap-2 text-sm cursor-pointer select-none min-w-0">
              <input type="checkbox" checked={includeArchived} onChange={e => setIncludeArchived(e.target.checked)}
                data-testid="inventory-include-archived" className="rounded border-[var(--color-border-default)]" />
              <span>{copy.list.includeArchived}</span>
            </label>
          </div>
        </Card>

        {loading && <Card><p role="status" data-testid="inventory-loading">{t.common.loading}</p></Card>}

        {!loading && Boolean(failure) && (
          <Card className="space-y-3" data-testid="inventory-error">
            <p role="alert" className="text-sm text-[var(--color-status-error)]">{localizedError(failure, t)}</p>
            <Button type="button" variant="secondary" onClick={reload}>{t.common.tryAgain}</Button>
          </Card>
        )}

        {!loading && !failure && items.length === 0 && (
          <Card className="py-10 text-center space-y-3" data-testid="inventory-empty-state">
            <div className="mx-auto w-12 h-12 rounded-full bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)] flex items-center justify-center">
              <Boxes className="w-6 h-6" aria-hidden="true" />
            </div>
            <h2 className="text-base font-semibold">{copy.list.emptyTitle}</h2>
            <p className="text-sm text-[var(--color-text-muted)] max-w-md mx-auto">{copy.list.emptyDescription}</p>
          </Card>
        )}

        {!loading && !failure && items.length > 0 && (
          <div className="space-y-3" data-testid="inventory-item-list">
            <p className="text-xs text-[var(--color-text-muted)]">
              {copy.list.count}: {items.length}{items.length >= LIST_LIMIT ? ` · ${copy.list.limitNotice}` : ''}
            </p>
            {items.map(item => (
              <Card key={item.id} className="min-w-0 space-y-3" data-testid={`inventory-item-${item.sku}`}>
                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3 min-w-0">
                  <div className="min-w-0 space-y-1.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <span dir="ltr" className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-mono font-semibold bg-slate-100 text-slate-800 border border-slate-300 [overflow-wrap:anywhere]">
                        {item.sku}
                      </span>
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-semibold bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)] border border-[var(--color-brand-border)]">
                        {copy.kinds[item.itemKind]}
                      </span>
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded text-xs font-semibold border ${item.status === 'ACTIVE'
                        ? 'bg-emerald-50 text-emerald-800 border-emerald-200' : 'bg-slate-100 text-slate-700 border-slate-300'}`}>
                        {copy.statuses[item.status]}
                      </span>
                    </div>
                    <h2 dir="auto" className="text-base font-semibold [overflow-wrap:anywhere]">{item.name}</h2>
                  </div>
                  {canWrite && item.status === 'ACTIVE' && (
                    <div className="flex flex-wrap gap-2 shrink-0">
                      <Button type="button" size="sm" variant="secondary" disabled={Boolean(busyId)}
                        onClick={() => { setNotice(''); setActionError(''); setEditor({ mode: 'edit', item }); }}
                        data-testid={`inventory-edit-${item.sku}`}>
                        {copy.list.edit}
                      </Button>
                      <Button type="button" size="sm" variant="destructive" disabled={Boolean(busyId)} isLoading={busyId === item.id}
                        onClick={() => void archive(item)} data-testid={`inventory-archive-${item.sku}`}>
                        {copy.list.archive}
                      </Button>
                    </div>
                  )}
                </div>
                <dl className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm min-w-0">
                  <div className="min-w-0"><dt className="text-xs text-[var(--color-text-muted)]">{copy.fields.unit}</dt>
                    <dd dir="auto" className="font-medium [overflow-wrap:anywhere]">{item.unit}</dd></div>
                  <div className="min-w-0"><dt className="text-xs text-[var(--color-text-muted)]">{copy.fields.category}</dt>
                    <dd dir="auto" className="font-medium [overflow-wrap:anywhere]">{item.category ?? '—'}</dd></div>
                  <div className="min-w-0"><dt className="text-xs text-[var(--color-text-muted)]">{copy.fields.reorderLevel}</dt>
                    <dd className="font-medium [overflow-wrap:anywhere]"><span dir="ltr">{item.reorderLevel ?? '—'}</span></dd></div>
                </dl>
              </Card>
            ))}
          </div>
        )}
      </div>
    </LivePage>
  );
}

function conflictMessage(error: unknown, copy: InventoryCopy): string | null {
  if (!(error instanceof ApiError) || !isInventoryConflictCode(error.code)) return null;
  return error.code === 'duplicate_inventory_sku' ? copy.form.duplicate
    : error.code === 'invalid_inventory_item_state' ? copy.form.archivedState
      : copy.form.stale;
}

function InventoryItemForm({ item, onCancel, onSaved }: {
  item: InventoryItemResponse | null;
  onCancel: () => void;
  onSaved: (message: string) => void;
}) {
  const { locale, t } = useLanguage();
  const copy = inventoryCopy(locale);
  const [draft, setDraft] = useState<InventoryItemDraft>(() => (item ? draftFromItem(item) : initialItemDraft()));
  const [errors, setErrors] = useState<InventoryValidationErrors>({});
  const [failure, setFailure] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const activeRequest = useRef<AbortController | null>(null);

  useEffect(() => () => activeRequest.current?.abort(), []);

  const change = <K extends keyof InventoryItemDraft>(key: K, value: InventoryItemDraft[K]) => {
    setDraft(current => ({ ...current, [key]: value }));
    setErrors(current => ({ ...current, [key]: undefined }));
  };
  const fieldError = (key: string) => (errors[key] ? copy.errors[errors[key]!] : undefined);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    const validation = validateItemDraft(draft);
    setErrors(validation);
    if (Object.values(validation).some(Boolean)) {
      setFailure(copy.form.validation);
      return;
    }
    setSubmitting(true);
    setFailure('');
    const controller = new AbortController();
    activeRequest.current = controller;
    try {
      if (item) await inventoryApi.update(item.id, itemUpdatePayload(draft, item.version), controller.signal);
      else await inventoryApi.create(itemPayload(draft), controller.signal);
      onSaved(item ? copy.list.saved : copy.list.created);
    } catch (error) {
      if (controller.signal.aborted) return;
      if (error instanceof ApiError && error.status === 400) setErrors(mapInventoryFieldErrors(error.fieldErrors));
      setFailure(conflictMessage(error, copy) ?? localizedError(error, t));
    } finally {
      if (activeRequest.current === controller) activeRequest.current = null;
      setSubmitting(false);
    }
  };

  return (
    <Card className="min-w-0" data-testid="inventory-item-form">
      <form className="space-y-4 min-w-0" onSubmit={submit} noValidate>
        <h2 className="text-base font-semibold">{item ? copy.form.editTitle : copy.form.createTitle}</h2>
        {failure && <p role="alert" className="text-sm text-[var(--color-status-error)]" data-testid="inventory-form-error">{failure}</p>}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 min-w-0">
          <div className="min-w-0">
            <Input label={copy.fields.sku} value={draft.sku} onChange={e => change('sku', e.target.value)} required dir="ltr"
              maxLength={40} autoCapitalize="characters" helperText={copy.fields.skuHelp} error={fieldError('sku')}
              data-testid="inventory-sku-input" />
          </div>
          <div className="min-w-0">
            <Input label={copy.fields.name} value={draft.name} onChange={e => change('name', e.target.value)} required dir="auto"
              maxLength={160} error={fieldError('name')} data-testid="inventory-name-input" />
          </div>
          <div className="min-w-0">
            <Input label={copy.fields.unit} value={draft.unit} onChange={e => change('unit', e.target.value)} required dir="auto"
              maxLength={40} helperText={copy.fields.unitHelp} error={fieldError('unit')} data-testid="inventory-unit-input" />
          </div>
          <Select label={copy.fields.itemKind} value={draft.itemKind} data-testid="inventory-kind-input"
            options={inventoryItemKinds.map(k => ({ value: k, label: copy.kinds[k] }))}
            onChange={e => change('itemKind', e.target.value as InventoryItemKind)} error={fieldError('itemKind')} />
          <div className="min-w-0">
            <Input label={copy.fields.category} value={draft.category} onChange={e => change('category', e.target.value)} isOptional
              dir="auto" maxLength={80} error={fieldError('category')} data-testid="inventory-category-input" />
          </div>
          <div className="min-w-0">
            <Input label={copy.fields.reorderLevel} value={draft.reorderLevel} onChange={e => change('reorderLevel', e.target.value)}
              isOptional dir="ltr" inputMode="decimal" helperText={copy.fields.reorderHelp} error={fieldError('reorderLevel')}
              data-testid="inventory-reorder-input" />
          </div>
        </div>
        {draft.itemKind === 'FINISHED_GOOD' && <p className="text-xs text-[var(--color-text-muted)]">{copy.form.finishedGoodNote}</p>}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" isLoading={submitting} disabled={submitting} data-testid="inventory-save-button">
            {item ? copy.form.save : copy.form.create}
          </Button>
          <Button type="button" variant="secondary" onClick={onCancel} disabled={submitting}>{copy.form.cancel}</Button>
        </div>
      </form>
    </Card>
  );
}
