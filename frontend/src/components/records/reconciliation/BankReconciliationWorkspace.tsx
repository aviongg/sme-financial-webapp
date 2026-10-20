"use client";

import { useEffect, useState, useCallback } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { reconciliationCopyEn, reconciliationCopyUr } from './reconciliation-copy';
import {
  reconciliationApi,
  AccountReconciliationSummary,
  StatementEntryResponse,
  StatementEntryStatus
} from '@/lib/api/dev-a/reconciliation';
import { accountsApi } from '@/lib/api/dev-a/accounts';
import type { Account } from '@/lib/api/dev-a/account-contracts';
import { StatementImportModal } from './StatementImportModal';
import { MatchReviewSheet } from './MatchReviewSheet';
import { UnmatchedMovementList } from './UnmatchedMovementList';

type TabKey = 'ALL' | 'UNMATCHED' | 'MATCHED' | 'EXCLUDED' | 'UNMATCHED_LEDGER';

export function BankReconciliationWorkspace() {
  const { locale, direction } = useLanguage();
  const copy = locale === 'ur' ? reconciliationCopyUr : reconciliationCopyEn;

  // Account selection state
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<string>('');
  const [loadingAccounts, setLoadingAccounts] = useState<boolean>(true);

  // Reconciliation summary KPIs
  const [summary, setSummary] = useState<AccountReconciliationSummary | null>(null);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  // Tab & entries state
  const [activeTab, setActiveTab] = useState<TabKey>('ALL');
  const [entries, setEntries] = useState<StatementEntryResponse[]>([]);
  const [loadingEntries, setLoadingEntries] = useState<boolean>(false);
  const [entriesError, setEntriesError] = useState<string | null>(null);

  // Modal / Sheet state
  const [importModalOpen, setImportModalOpen] = useState<boolean>(false);
  const [reviewEntryId, setReviewEntryId] = useState<string | null>(null);

  const [refreshKey, setRefreshKey] = useState(0);

  const reloadData = useCallback(() => {
    setLoadingEntries(true);
    setRefreshKey((k) => k + 1);
  }, []);

  // Load Bank Accounts
  useEffect(() => {
    let cancelled = false;
    accountsApi.list({ accountType: 'BANK', status: 'ACTIVE' })
      .then((res) => {
        if (cancelled) return;
        const bankAccs = res.items || [];
        setAccounts(bankAccs);
        if (bankAccs.length > 0) {
          setSelectedAccountId((prev) => prev || bankAccs[0].id);
        }
      })
      .catch((err) => {
        if (cancelled) return;
        console.error('Failed to load bank accounts', err);
      })
      .finally(() => {
        if (!cancelled) setLoadingAccounts(false);
      });

    return () => { cancelled = true; };
  }, []);

  // Fetch summary and entries for selected account
  useEffect(() => {
    if (!selectedAccountId) return;
    let cancelled = false;

    reconciliationApi.getAccountSummary(selectedAccountId)
      .then((data) => {
        if (!cancelled) {
          setSummary(data);
          setSummaryError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) setSummaryError(err instanceof Error ? err.message : copy.errorLoadingSummary);
      });

    if (activeTab !== 'UNMATCHED_LEDGER') {
      const statusParam: StatementEntryStatus | undefined =
        activeTab === 'UNMATCHED' ? 'UNMATCHED' :
        activeTab === 'MATCHED' ? 'MATCHED' :
        activeTab === 'EXCLUDED' ? 'EXCLUDED' : undefined;

      reconciliationApi.listEntries(selectedAccountId, statusParam, 0, 100)
        .then((res) => {
          if (!cancelled) {
            setEntries(res.content || []);
            setEntriesError(null);
          }
        })
        .catch((err: unknown) => {
          if (!cancelled) setEntriesError(err instanceof Error ? err.message : copy.errorLoadingEntries);
        })
        .finally(() => {
          if (!cancelled) setLoadingEntries(false);
        });
    }

    return () => { cancelled = true; };
  }, [selectedAccountId, activeTab, refreshKey, copy.errorLoadingSummary, copy.errorLoadingEntries]);

  const selectedAccount = accounts.find((a) => a.id === selectedAccountId);

  return (
    <div dir={direction} className="space-y-6 text-[var(--color-text-primary)]">
      {/* Top Header & Account Selector */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
        <div className="space-y-1">
          <h2 className="text-xl sm:text-2xl font-bold text-[var(--color-brand-primary)]">
            {copy.title}
          </h2>
          <p className="text-xs sm:text-sm text-gray-500 max-w-2xl">
            {copy.subtitle}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Account Selector */}
          <div className="flex items-center gap-2">
            <label htmlFor="reconciliation-account-select" className="text-xs font-semibold text-gray-600 whitespace-nowrap">
              {copy.selectAccount}:
            </label>
            <select
              id="reconciliation-account-select"
              value={selectedAccountId}
              onChange={(e) => setSelectedAccountId(e.target.value)}
              disabled={loadingAccounts || accounts.length === 0}
              className="text-sm font-semibold p-2 border border-gray-300 rounded-lg bg-white shadow-sm focus:ring-2 focus:ring-[var(--color-brand-primary)] focus:border-transparent"
            >
              {accounts.map((acc) => (
                <option key={acc.id} value={acc.id}>
                  {acc.accountName} ({acc.currency})
                </option>
              ))}
            </select>
          </div>

          {/* Import Statement Button */}
          <button
            type="button"
            disabled={!selectedAccountId}
            onClick={() => setImportModalOpen(true)}
            className="px-4 py-2 text-sm font-semibold text-white bg-[var(--color-brand-primary)] hover:bg-teal-800 disabled:opacity-50 rounded-lg shadow-sm transition-all focus-visible:outline-2 focus-visible:outline-offset-2"
          >
            + {copy.importStatementBtn}
          </button>

          {/* Refresh Button */}
          <button
            type="button"
            onClick={reloadData}
            className="p-2 text-sm text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors border border-gray-200"
            title={copy.refreshBtn}
            aria-label={copy.refreshBtn}
          >
            ↻
          </button>
        </div>
      </div>

      {summaryError && (
        <div className="p-4 bg-red-50 text-red-700 border border-red-200 rounded-xl text-sm" role="alert">
          {summaryError}
        </div>
      )}

      {/* Summary KPI Cards Grid */}
      {summary && (
        <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
          <div className="bg-white p-3.5 rounded-xl border border-gray-200 shadow-xs space-y-1">
            <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider block truncate">
              {copy.kpiTotalEntries}
            </span>
            <span className="text-lg font-bold text-gray-900 block font-mono">
              {summary.totalStatementEntries}
            </span>
          </div>

          <div className="bg-white p-3.5 rounded-xl border border-gray-200 shadow-xs space-y-1">
            <span className="text-[11px] font-semibold text-emerald-700 uppercase tracking-wider block truncate">
              {copy.kpiMatched}
            </span>
            <span className="text-lg font-bold text-emerald-800 block font-mono">
              {summary.matchedEntries}
            </span>
          </div>

          <div className="bg-white p-3.5 rounded-xl border border-gray-200 shadow-xs space-y-1">
            <span className="text-[11px] font-semibold text-blue-700 uppercase tracking-wider block truncate">
              {copy.kpiUnmatched}
            </span>
            <span className="text-lg font-bold text-blue-800 block font-mono">
              {summary.unmatchedEntries}
            </span>
          </div>

          <div className="bg-white p-3.5 rounded-xl border border-gray-200 shadow-xs space-y-1">
            <span className="text-[11px] font-semibold text-amber-700 uppercase tracking-wider block truncate">
              {copy.kpiExcluded}
            </span>
            <span className="text-lg font-bold text-amber-800 block font-mono">
              {summary.excludedEntries}
            </span>
          </div>

          <div className="bg-white p-3.5 rounded-xl border border-gray-200 shadow-xs space-y-1">
            <span className="text-[11px] font-semibold text-purple-700 uppercase tracking-wider block truncate">
              {copy.kpiUnmatchedMovements}
            </span>
            <span className="text-lg font-bold text-purple-800 block font-mono">
              {summary.unmatchedMovements}
            </span>
          </div>

          <div className="bg-white p-3.5 rounded-xl border border-gray-200 shadow-xs space-y-1">
            <span className="text-[11px] font-semibold text-red-600 uppercase tracking-wider block truncate">
              {copy.kpiDebitTotal}
            </span>
            <span className="text-sm font-bold text-red-700 block font-mono truncate">
              {summary.importedDebitTotal.toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </span>
          </div>

          <div className="bg-white p-3.5 rounded-xl border border-gray-200 shadow-xs space-y-1">
            <span className="text-[11px] font-semibold text-emerald-600 uppercase tracking-wider block truncate">
              {copy.kpiCreditTotal}
            </span>
            <span className="text-sm font-bold text-emerald-700 block font-mono truncate">
              {summary.importedCreditTotal.toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </span>
          </div>

          <div className="bg-white p-3.5 rounded-xl border border-teal-200 bg-teal-50/40 shadow-xs space-y-1">
            <span className="text-[11px] font-semibold text-teal-800 uppercase tracking-wider block truncate">
              {copy.kpiLedgerBalance}
            </span>
            <span className="text-sm font-bold text-[var(--color-brand-primary)] block font-mono truncate">
              {summary.ledgerBalance.toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </span>
          </div>
        </div>
      )}

      {/* Tabs Navigation */}
      <div className="border-b border-gray-200 flex flex-wrap gap-2 text-sm font-medium">
        <button
          type="button"
          onClick={() => setActiveTab('ALL')}
          className={`pb-2.5 px-3 border-b-2 font-semibold transition-colors ${
            activeTab === 'ALL'
              ? 'border-[var(--color-brand-primary)] text-[var(--color-brand-primary)]'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          {copy.tabAll}
          {summary && ` (${summary.totalStatementEntries})`}
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('UNMATCHED')}
          className={`pb-2.5 px-3 border-b-2 font-semibold transition-colors ${
            activeTab === 'UNMATCHED'
              ? 'border-[var(--color-brand-primary)] text-[var(--color-brand-primary)]'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          {copy.tabUnmatched}
          {summary && ` (${summary.unmatchedEntries})`}
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('MATCHED')}
          className={`pb-2.5 px-3 border-b-2 font-semibold transition-colors ${
            activeTab === 'MATCHED'
              ? 'border-[var(--color-brand-primary)] text-[var(--color-brand-primary)]'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          {copy.tabMatched}
          {summary && ` (${summary.matchedEntries})`}
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('EXCLUDED')}
          className={`pb-2.5 px-3 border-b-2 font-semibold transition-colors ${
            activeTab === 'EXCLUDED'
              ? 'border-[var(--color-brand-primary)] text-[var(--color-brand-primary)]'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          {copy.tabExcluded}
          {summary && ` (${summary.excludedEntries})`}
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('UNMATCHED_LEDGER')}
          className={`pb-2.5 px-3 border-b-2 font-semibold transition-colors ${
            activeTab === 'UNMATCHED_LEDGER'
              ? 'border-[var(--color-brand-primary)] text-[var(--color-brand-primary)]'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          {copy.tabUnmatchedLedger}
          {summary && ` (${summary.unmatchedMovements})`}
        </button>
      </div>

      {/* Main Tab Content */}
      {activeTab === 'UNMATCHED_LEDGER' ? (
        <UnmatchedMovementList accountId={selectedAccountId} />
      ) : (
        <div className="space-y-4">
          {entriesError && (
            <div className="p-4 bg-red-50 text-red-700 border border-red-200 rounded-xl text-sm" role="alert">
              {entriesError}
            </div>
          )}

          {loadingEntries ? (
            <div className="p-12 text-center text-sm text-gray-500">
              Loading statement entries...
            </div>
          ) : entries.length === 0 ? (
            <div className="p-12 text-center text-sm text-gray-500 bg-white rounded-2xl border border-gray-200 space-y-3">
              <p>{copy.emptyEntries}</p>
              <button
                type="button"
                onClick={() => setImportModalOpen(true)}
                className="px-4 py-2 text-xs font-semibold text-white bg-[var(--color-brand-primary)] rounded-lg hover:bg-teal-800 transition-colors"
              >
                + {copy.importStatementBtn}
              </button>
            </div>
          ) : (
            <>
              {/* Desktop Table View */}
              <div className="hidden md:block bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
                <table className="w-full text-sm text-left border-collapse">
                  <thead className="bg-gray-50 text-gray-600 border-b border-gray-200 text-xs uppercase tracking-wider">
                    <tr>
                      <th className="p-3 font-semibold">{copy.colDate}</th>
                      <th className="p-3 font-semibold">{copy.colDescription}</th>
                      <th className="p-3 font-semibold">{copy.colReference}</th>
                      <th className="p-3 font-semibold">{copy.colDirection}</th>
                      <th className="p-3 font-semibold">{copy.colAmount}</th>
                      <th className="p-3 font-semibold">{copy.colBalance}</th>
                      <th className="p-3 font-semibold">{copy.colStatus}</th>
                      <th className="p-3 font-semibold text-right">{copy.colActions}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {entries.map((e) => (
                      <tr key={e.id} className="hover:bg-gray-50/80 transition-colors">
                        <td className="p-3 whitespace-nowrap font-mono text-xs">{e.postedDate}</td>
                        <td className="p-3 max-w-xs truncate" title={e.description || ''}>
                          {e.description || '—'}
                          {e.duplicateWarning && (
                            <span className="ml-2 inline-flex items-center px-1.5 py-0.2 rounded text-[10px] font-semibold bg-amber-100 text-amber-800">
                              Duplicate?
                            </span>
                          )}
                        </td>
                        <td className="p-3 whitespace-nowrap font-mono text-xs text-gray-500">
                          {e.bankReference || '—'}
                        </td>
                        <td className="p-3 whitespace-nowrap">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-bold ${
                              e.direction === 'INCREASE'
                                ? 'bg-emerald-100 text-emerald-800'
                                : 'bg-red-100 text-red-800'
                            }`}
                          >
                            {e.direction === 'INCREASE' ? 'CR' : 'DR'}
                          </span>
                        </td>
                        <td className="p-3 whitespace-nowrap font-mono font-bold">
                          {e.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                        </td>
                        <td className="p-3 whitespace-nowrap font-mono text-xs text-gray-500">
                          {e.runningBalance !== null && e.runningBalance !== undefined
                            ? e.runningBalance.toLocaleString('en-US', { minimumFractionDigits: 2 })
                            : '—'}
                        </td>
                        <td className="p-3 whitespace-nowrap">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold ${
                              e.status === 'MATCHED'
                                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                : e.status === 'EXCLUDED'
                                ? 'bg-gray-100 text-gray-700 border border-gray-200'
                                : 'bg-blue-50 text-blue-700 border border-blue-200'
                            }`}
                          >
                            {e.status === 'MATCHED' ? copy.statusMatched : e.status === 'EXCLUDED' ? copy.statusExcluded : copy.statusUnmatched}
                          </span>
                        </td>
                        <td className="p-3 whitespace-nowrap text-right">
                          <button
                            type="button"
                            onClick={() => setReviewEntryId(e.id)}
                            className="text-xs font-semibold px-2.5 py-1 rounded bg-teal-50 text-[var(--color-brand-primary)] hover:bg-teal-100 transition-colors"
                          >
                            {copy.actionReviewMatch}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Mobile Stacked Card View */}
              <div className="md:hidden space-y-3">
                {entries.map((e) => (
                  <div key={e.id} className="p-4 bg-white rounded-xl border border-gray-200 shadow-sm space-y-2">
                    <div className="flex justify-between items-center text-xs">
                      <span className="font-mono text-gray-500">{e.postedDate}</span>
                      <div className="flex gap-1.5 items-center">
                        <span
                          className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                            e.direction === 'INCREASE'
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-red-100 text-red-800'
                          }`}
                        >
                          {e.direction === 'INCREASE' ? 'CR' : 'DR'}
                        </span>
                        <span
                          className={`px-2 py-0.5 rounded text-xs font-semibold ${
                            e.status === 'MATCHED'
                              ? 'bg-emerald-50 text-emerald-700'
                              : e.status === 'EXCLUDED'
                              ? 'bg-gray-100 text-gray-700'
                              : 'bg-blue-50 text-blue-700'
                          }`}
                        >
                          {e.status === 'MATCHED' ? copy.statusMatched : e.status === 'EXCLUDED' ? copy.statusExcluded : copy.statusUnmatched}
                        </span>
                      </div>
                    </div>

                    <div className="flex justify-between items-baseline">
                      <span className="font-semibold text-sm truncate max-w-[200px]">{e.description || 'Entry'}</span>
                      <span className="font-mono font-bold text-sm">
                        {e.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })} PKR
                      </span>
                    </div>

                    {e.bankReference && (
                      <p className="text-xs text-gray-500 font-mono">Ref: {e.bankReference}</p>
                    )}

                    {e.duplicateWarning && (
                      <p className="text-[11px] text-amber-700 font-medium">⚠️ {copy.duplicateWarningBadge}</p>
                    )}

                    <div className="pt-2 flex justify-end">
                      <button
                        type="button"
                        onClick={() => setReviewEntryId(e.id)}
                        className="w-full text-xs font-semibold py-2 rounded bg-teal-50 text-[var(--color-brand-primary)] hover:bg-teal-100 transition-colors text-center"
                      >
                        {copy.actionReviewMatch}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      )}

      {/* Import Statement Modal */}
      {selectedAccount && (
        <StatementImportModal
          accountId={selectedAccount.id}
          accountName={selectedAccount.accountName}
          isOpen={importModalOpen}
          onClose={() => setImportModalOpen(false)}
          onImportSuccess={reloadData}
        />
      )}

      {/* Match Review Sheet */}
      <MatchReviewSheet
        entryId={reviewEntryId}
        isOpen={!!reviewEntryId}
        onClose={() => setReviewEntryId(null)}
        onMatchChanged={reloadData}
      />
    </div>
  );
}
