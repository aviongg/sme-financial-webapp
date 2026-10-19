"use client";

import React, { useEffect, useState } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { useSession } from '@/components/auth/SessionProvider';
import { canPresentPosting } from '@/components/finance/transactions/transaction-submission';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Dialog } from '@/components/ui/Dialog';
import {
  counterpartiesApi,
  counterpartyTypes,
  type CounterpartyListResponse,
  type CounterpartySummaryItem,
  type CounterpartyType,
} from '@/lib/api/dev-a/counterparties';
import { counterpartyEn, counterpartyUr } from './counterparty-copy';
import { CounterpartyModal } from './CounterpartyModal';

export function CounterpartiesWorkspace() {
  const { locale, direction } = useLanguage();
  const session = useSession();
  const copy = locale === 'ur' ? counterpartyUr : counterpartyEn;
  const isWritable = session.status === 'authenticated' && canPresentPosting(session.business);

  const [activeTab, setActiveTab] = useState<'ACTIVE' | 'ARCHIVED'>('ACTIVE');
  const [selectedType, setSelectedType] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [page, setPage] = useState(0);

  const [data, setData] = useState<CounterpartyListResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  // Modal states
  const [modalOpen, setModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<CounterpartySummaryItem | null>(null);

  // Confirmation dialogs
  const [archiveTarget, setArchiveTarget] = useState<CounterpartySummaryItem | null>(null);
  const [restoreTarget, setRestoreTarget] = useState<CounterpartySummaryItem | null>(null);
  const [actionInProgress, setActionInProgress] = useState(false);

  // Debounce search query
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(searchQuery);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  useEffect(() => {
    if (session.status !== 'authenticated') return;
    let active = true;

    counterpartiesApi
      .list({
        status: activeTab,
        type: selectedType === 'ALL' ? undefined : (selectedType as CounterpartyType),
        q: debouncedQuery.trim() || undefined,
        page,
        size: 20,
      })
      .then(res => {
        if (active) {
          setData(res);
          setError(null);
          setLoading(false);
        }
      })
      .catch(() => {
        if (active) {
          setError(copy.loadFailed);
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [activeTab, selectedType, debouncedQuery, page, session.status, refreshKey, copy.loadFailed]);

  const handleOpenAdd = () => {
    setEditingItem(null);
    setModalOpen(true);
  };

  const handleOpenEdit = (item: CounterpartySummaryItem) => {
    setEditingItem(item);
    setModalOpen(true);
  };

  const handleSaved = () => {
    setSuccessMsg(copy.saveSuccess);
    setTimeout(() => setSuccessMsg(null), 4000);
    setLoading(true);
    setRefreshKey(k => k + 1);
  };

  const handleConfirmArchive = async () => {
    if (!archiveTarget) return;
    setActionInProgress(true);
    try {
      await counterpartiesApi.archive(archiveTarget.id);
      setSuccessMsg(copy.archiveSuccess);
      setTimeout(() => setSuccessMsg(null), 4000);
      setArchiveTarget(null);
      setLoading(true);
      setRefreshKey(k => k + 1);
    } catch {
      setError(copy.loadFailed);
    } finally {
      setActionInProgress(false);
    }
  };

  const handleConfirmRestore = async () => {
    if (!restoreTarget) return;
    setActionInProgress(true);
    try {
      await counterpartiesApi.restore(restoreTarget.id);
      setSuccessMsg(copy.restoreSuccess);
      setTimeout(() => setSuccessMsg(null), 4000);
      setRestoreTarget(null);
      setLoading(true);
      setRefreshKey(k => k + 1);
    } catch {
      setError(copy.loadFailed);
    } finally {
      setActionInProgress(false);
    }
  };

  const handleRetry = () => {
    setLoading(true);
    setRefreshKey(k => k + 1);
  };

  const typeBadgeClass = (type: CounterpartyType) => {
    switch (type) {
      case 'CUSTOMER':
        return 'bg-blue-500/10 text-blue-700 dark:text-blue-300 border-blue-500/20';
      case 'SUPPLIER':
        return 'bg-purple-500/10 text-purple-700 dark:text-purple-300 border-purple-500/20';
      case 'BOTH':
        return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/20';
    }
  };

  return (
    <div dir={direction} className="space-y-5 min-w-0">
      {/* Header & Main Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-heading font-semibold text-[var(--color-text-primary)]">
            {copy.title}
          </h2>
          <p className="text-sm text-[var(--color-text-secondary)]">{copy.subtitle}</p>
        </div>
        {isWritable ? (
          <Button onClick={handleOpenAdd} className="shrink-0">
            {copy.addContact}
          </Button>
        ) : (
          <p className="text-xs text-[var(--color-text-muted)] italic">{copy.readOnlyNotice}</p>
        )}
      </div>

      {/* Success Notification */}
      {successMsg && (
        <div
          role="status"
          className="rounded-lg bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-900/50 p-3 text-sm text-emerald-800 dark:text-emerald-200 flex items-center justify-between"
        >
          <span>{successMsg}</span>
          <button
            type="button"
            onClick={() => setSuccessMsg(null)}
            className="text-emerald-600 hover:text-emerald-800 text-xs font-semibold"
          >
            &times;
          </button>
        </div>
      )}

      {/* Error Notification */}
      {error && (
        <Card>
          <div className="flex items-center justify-between">
            <p role="alert" className="text-sm text-rose-600 dark:text-rose-400">
              {error}
            </p>
            <Button variant="secondary" size="sm" onClick={handleRetry}>
              {copy.retry}
            </Button>
          </div>
        </Card>
      )}

      {/* Tabs & Filters */}
      <Card className="space-y-4 p-4">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          {/* Status Tabs */}
          <div className="flex items-center gap-1 border-b md:border-b-0 border-[var(--color-border)] pb-2 md:pb-0">
            <button
              type="button"
              onClick={() => {
                setActiveTab('ACTIVE');
                setPage(0);
                setLoading(true);
              }}
              className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                activeTab === 'ACTIVE'
                  ? 'bg-[var(--color-brand-primary)] text-white'
                  : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)]'
              }`}
            >
              {copy.active}
            </button>
            <button
              type="button"
              onClick={() => {
                setActiveTab('ARCHIVED');
                setPage(0);
                setLoading(true);
              }}
              className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                activeTab === 'ARCHIVED'
                  ? 'bg-[var(--color-brand-primary)] text-white'
                  : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)]'
              }`}
            >
              {copy.archived}
            </button>
          </div>

          {/* Search & Type Select */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
            <div className="w-full sm:w-64">
              <Input
                label={copy.search}
                type="search"
                placeholder={copy.searchPlaceholder}
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
              />
            </div>
            <div className="w-full sm:w-44">
              <Select
                label={copy.filterType}
                value={selectedType}
                options={[
                  { value: 'ALL', label: copy.allTypes },
                  ...counterpartyTypes.map(t => ({
                    value: t,
                    label: copy[t.toLowerCase() as 'customer' | 'supplier' | 'both'],
                  })),
                ]}
                onChange={e => {
                  setSelectedType(e.target.value);
                  setPage(0);
                }}
              />
            </div>
          </div>
        </div>
      </Card>

      {/* Content List / Table */}
      {loading ? (
        <div className="py-12 text-center text-sm text-[var(--color-text-muted)]" role="status">
          {copy.loading}
        </div>
      ) : !data || data.content.length === 0 ? (
        <Card className="py-12 text-center space-y-3">
          <h3 className="font-heading font-semibold text-base text-[var(--color-text-primary)]">
            {copy.emptyTitle}
          </h3>
          <p className="text-sm text-[var(--color-text-secondary)] max-w-md mx-auto">
            {debouncedQuery || selectedType !== 'ALL'
              ? copy.emptySearchDescription
              : copy.emptyDescription}
          </p>
          {isWritable && !debouncedQuery && selectedType === 'ALL' && activeTab === 'ACTIVE' && (
            <div className="pt-2">
              <Button onClick={handleOpenAdd}>{copy.addContact}</Button>
            </div>
          )}
        </Card>
      ) : (
        <div className="space-y-4">
          <div className="overflow-x-auto rounded-lg border border-[var(--color-border)]">
            <table className="w-full text-sm text-left border-collapse">
              <thead className="bg-[var(--color-background-secondary)] text-[var(--color-text-secondary)] border-b border-[var(--color-border)]">
                <tr>
                  <th className="px-4 py-3 font-semibold">{copy.name}</th>
                  <th className="px-4 py-3 font-semibold">{copy.type}</th>
                  <th className="px-4 py-3 font-semibold">{copy.contactInfo}</th>
                  <th className="px-4 py-3 font-semibold">{copy.taxRegistrationNumber}</th>
                  <th className="px-4 py-3 font-semibold text-right">{copy.actions}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)] bg-[var(--color-background-primary)]">
                {data.content.map(cp => (
                  <tr key={cp.id} className="hover:bg-[var(--color-surface-hover)] transition-colors">
                    <td className="px-4 py-3 font-medium text-[var(--color-text-primary)]">
                      <div className="space-y-0.5">
                        <div className="font-semibold [overflow-wrap:anywhere]">{cp.displayName}</div>
                        {cp.address && (
                          <div className="text-xs text-[var(--color-text-muted)] truncate max-w-xs">
                            {cp.address}
                          </div>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span
                        className={`inline-block px-2.5 py-0.5 text-xs font-semibold rounded border ${typeBadgeClass(
                          cp.type
                        )}`}
                      >
                        {copy[cp.type.toLowerCase() as 'customer' | 'supplier' | 'both']}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-[var(--color-text-secondary)]">
                      <div className="space-y-0.5">
                        {cp.phone && <div dir="ltr">{cp.phone}</div>}
                        {cp.email && <div dir="ltr">{cp.email}</div>}
                        {!cp.phone && !cp.email && <span className="text-[var(--color-text-muted)]">—</span>}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-xs text-[var(--color-text-secondary)] whitespace-nowrap">
                      {cp.taxRegistrationNumber ? (
                        <span className="font-mono">{cp.taxRegistrationNumber}</span>
                      ) : (
                        <span className="text-[var(--color-text-muted)]">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-right">
                      {isWritable && (
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => handleOpenEdit(cp)}
                          >
                            {copy.edit}
                          </Button>
                          {cp.status === 'ACTIVE' ? (
                            <Button
                              variant="secondary"
                              size="sm"
                              className="text-amber-700 hover:text-amber-900 border-amber-300"
                              onClick={() => setArchiveTarget(cp)}
                            >
                              {copy.archive}
                            </Button>
                          ) : (
                            <Button
                              variant="secondary"
                              size="sm"
                              className="text-emerald-700 hover:text-emerald-900 border-emerald-300"
                              onClick={() => setRestoreTarget(cp)}
                            >
                              {copy.restore}
                            </Button>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {data.totalPages > 1 && (
            <nav
              aria-label={copy.title}
              className="flex flex-wrap items-center justify-between gap-3 pt-2 text-sm text-[var(--color-text-secondary)]"
            >
              <div>
                <span>
                  {data.totalElements} {copy.totalContacts}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                >
                  &larr;
                </Button>
                <span>
                  {page + 1} / {data.totalPages}
                </span>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={page + 1 >= data.totalPages}
                  onClick={() => setPage(p => p + 1)}
                >
                  &rarr;
                </Button>
              </div>
            </nav>
          )}
        </div>
      )}

      {/* Create / Edit Modal */}
      {modalOpen && (
        <CounterpartyModal
          isOpen={modalOpen}
          onClose={() => {
            setModalOpen(false);
            setEditingItem(null);
          }}
          onSaved={handleSaved}
          counterparty={editingItem}
        />
      )}

      {/* Archive Confirmation Dialog */}
      {archiveTarget && (
        <Dialog
          isOpen={Boolean(archiveTarget)}
          onClose={() => setArchiveTarget(null)}
          title={copy.archiveConfirmTitle}
          description={copy.archiveConfirmMessage}
          maxWidth="sm"
        >
          <div className="space-y-4 pt-2">
            <p className="text-sm text-[var(--color-text-primary)] font-semibold">
              {archiveTarget.displayName}
            </p>
            <div className="flex items-center justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                disabled={actionInProgress}
                onClick={() => setArchiveTarget(null)}
              >
                {copy.cancel}
              </Button>
              <Button
                isLoading={actionInProgress}
                disabled={actionInProgress}
                className="bg-amber-600 hover:bg-amber-700 text-white"
                onClick={handleConfirmArchive}
              >
                {copy.confirmArchive}
              </Button>
            </div>
          </div>
        </Dialog>
      )}

      {/* Restore Confirmation Dialog */}
      {restoreTarget && (
        <Dialog
          isOpen={Boolean(restoreTarget)}
          onClose={() => setRestoreTarget(null)}
          title={copy.restoreConfirmTitle}
          description={copy.restoreConfirmMessage}
          maxWidth="sm"
        >
          <div className="space-y-4 pt-2">
            <p className="text-sm text-[var(--color-text-primary)] font-semibold">
              {restoreTarget.displayName}
            </p>
            <div className="flex items-center justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                disabled={actionInProgress}
                onClick={() => setRestoreTarget(null)}
              >
                {copy.cancel}
              </Button>
              <Button
                isLoading={actionInProgress}
                disabled={actionInProgress}
                className="bg-emerald-600 hover:bg-emerald-700 text-white"
                onClick={handleConfirmRestore}
              >
                {copy.confirmRestore}
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
}
