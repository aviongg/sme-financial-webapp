"use client";

import { useEffect, useState, useId } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { reconciliationCopyEn, reconciliationCopyUr } from './reconciliation-copy';
import {
  reconciliationApi,
  StatementEntryDetailResponse,
  ReconciliationMatchCandidate
} from '@/lib/api/dev-a/reconciliation';

interface Props {
  entryId: string | null;
  isOpen: boolean;
  onClose: () => void;
  onMatchChanged: () => void;
}

export function MatchReviewSheet({ entryId, isOpen, onClose, onMatchChanged }: Props) {
  const { locale, direction } = useLanguage();
  const copy = locale === 'ur' ? reconciliationCopyUr : reconciliationCopyEn;
  const modalId = useId();

  const [loading, setLoading] = useState<boolean>(true);
  const [entry, setEntry] = useState<StatementEntryDetailResponse | null>(null);
  const [candidates, setCandidates] = useState<ReconciliationMatchCandidate[]>([]);
  const [selectedCandidateId, setSelectedCandidateId] = useState<string | null>(null);

  const [actionLoading, setActionLoading] = useState<boolean>(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const [showExcludePrompt, setShowExcludePrompt] = useState<boolean>(false);
  const [excludeReason, setExcludeReason] = useState<string>('');
  const [unmatchReason, setUnmatchReason] = useState<string>('');

  useEffect(() => {
    if (!isOpen || !entryId) return;

    let cancelled = false;

    const loadData = async () => {
      try {
        const detail = await reconciliationApi.getEntryDetail(entryId);
        if (cancelled) return;
        setEntry(detail);

        if (detail.status === 'UNMATCHED') {
          const cands = await reconciliationApi.findCandidates(entryId);
          if (cancelled) return;
          setCandidates(cands);
          if (cands.length > 0) {
            setSelectedCandidateId(cands[0].accountMovementId);
          }
        }
        setActionError(null);
        setActionSuccess(null);
        setShowExcludePrompt(false);
        setExcludeReason('');
        setUnmatchReason('');
      } catch (err: unknown) {
        if (cancelled) return;
        setActionError(err instanceof Error ? err.message : 'Failed to load entry details');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    loadData();
    return () => { cancelled = true; };
  }, [isOpen, entryId]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen || !entryId) return null;

  const handleConfirmMatch = async () => {
    if (!selectedCandidateId) return;
    setActionLoading(true);
    setActionError(null);
    try {
      await reconciliationApi.matchEntry(entryId, selectedCandidateId);
      setActionSuccess(copy.matchSuccess);
      onMatchChanged();
      setTimeout(() => {
        onClose();
      }, 700);
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : 'Match failed');
    } finally {
      setActionLoading(false);
    }
  };

  const handleUnmatch = async () => {
    setActionLoading(true);
    setActionError(null);
    try {
      await reconciliationApi.unmatchEntry(entryId, unmatchReason || undefined);
      setActionSuccess(copy.unmatchSuccess);
      onMatchChanged();
      setTimeout(() => {
        onClose();
      }, 700);
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : 'Unmatch failed');
    } finally {
      setActionLoading(false);
    }
  };

  const handleExclude = async () => {
    if (!excludeReason.trim()) {
      setActionError(copy.excludeReasonPrompt);
      return;
    }
    setActionLoading(true);
    setActionError(null);
    try {
      await reconciliationApi.excludeEntry(entryId, excludeReason.trim());
      setActionSuccess(copy.excludeSuccess);
      onMatchChanged();
      setTimeout(() => {
        onClose();
      }, 700);
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : 'Exclude failed');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCreateDraft = async () => {
    setActionLoading(true);
    setActionError(null);
    try {
      await reconciliationApi.createDraft(entryId);
      setActionSuccess(copy.draftCreatedSuccess);
      onMatchChanged();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : 'Failed to create draft');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={`${modalId}-title`}
      className="fixed inset-0 z-50 overflow-y-auto bg-black/50 flex items-center justify-center p-4"
    >
      <div
        dir={direction}
        className="bg-white rounded-xl shadow-2xl max-w-4xl w-full max-h-[90vh] flex flex-col overflow-hidden text-[var(--color-text-primary)]"
      >
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center bg-[var(--color-brand-primary)] text-white">
          <div>
            <h2 id={`${modalId}-title`} className="text-xl font-bold">
              {copy.sheetTitle}
            </h2>
            <p className="text-xs text-teal-100">
              {copy.sheetSubtitle}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-white hover:bg-teal-800 p-1.5 rounded-lg focus-visible:outline-2 focus-visible:outline-white"
            aria-label={copy.closeBtn}
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1">
          {actionError && (
            <div className="p-3 bg-red-50 text-red-700 border border-red-200 rounded-lg text-sm" role="alert">
              {actionError}
            </div>
          )}

          {actionSuccess && (
            <div className="p-3 bg-emerald-50 text-emerald-800 border border-emerald-200 rounded-lg text-sm" role="status">
              {actionSuccess}
            </div>
          )}

          {loading ? (
            <div className="p-8 text-center text-sm text-gray-500">
              Loading reconciliation evidence...
            </div>
          ) : entry ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Left Column: Statement Evidence */}
              <div className="bg-gray-50 rounded-xl p-5 border border-gray-200 space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="font-bold text-sm text-gray-900 uppercase tracking-wider">
                    {copy.statementEvidenceTitle}
                  </h3>
                  <span
                    className={`px-2 py-0.5 rounded text-xs font-semibold ${
                      entry.status === 'MATCHED'
                        ? 'bg-emerald-100 text-emerald-800'
                        : entry.status === 'EXCLUDED'
                        ? 'bg-amber-100 text-amber-800'
                        : 'bg-blue-100 text-blue-800'
                    }`}
                  >
                    {entry.status === 'MATCHED'
                      ? copy.statusMatched
                      : entry.status === 'EXCLUDED'
                      ? copy.statusExcluded
                      : copy.statusUnmatched}
                  </span>
                </div>

                <div className="space-y-2 text-sm">
                  <div className="flex justify-between py-1 border-b border-gray-200">
                    <span className="text-gray-500">{copy.colDate}</span>
                    <span className="font-mono font-medium">{entry.postedDate}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-gray-200">
                    <span className="text-gray-500">{copy.colDirection}</span>
                    <span className={`font-bold ${entry.direction === 'INCREASE' ? 'text-emerald-700' : 'text-red-700'}`}>
                      {entry.direction === 'INCREASE' ? 'Credit (Deposit)' : 'Debit (Withdrawal)'}
                    </span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-gray-200">
                    <span className="text-gray-500">{copy.colAmount}</span>
                    <span className="font-mono font-bold text-base">
                      {entry.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })} PKR
                    </span>
                  </div>
                  <div className="py-1 border-b border-gray-200">
                    <span className="text-gray-500 block text-xs mb-0.5">{copy.colDescription}</span>
                    <span className="font-medium text-gray-900 break-words">{entry.description || '—'}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-gray-200">
                    <span className="text-gray-500">{copy.colReference}</span>
                    <span className="font-mono text-gray-800">{entry.bankReference || '—'}</span>
                  </div>
                  {entry.runningBalance !== null && entry.runningBalance !== undefined && (
                    <div className="flex justify-between py-1 border-b border-gray-200">
                      <span className="text-gray-500">{copy.colBalance}</span>
                      <span className="font-mono text-gray-700">
                        {entry.runningBalance.toLocaleString('en-US', { minimumFractionDigits: 2 })} PKR
                      </span>
                    </div>
                  )}
                  {entry.duplicateWarning && (
                    <div className="p-2 bg-amber-50 border border-amber-200 rounded text-xs text-amber-800 font-medium">
                      ⚠️ {copy.duplicateWarningBadge}: Another entry with identical date, amount, and text exists.
                    </div>
                  )}
                  {entry.linkedDraftId && (
                    <div className="p-2 bg-teal-50 border border-teal-200 rounded text-xs text-teal-900 font-medium">
                      Draft linked: <span className="font-mono">{entry.linkedDraftId.slice(0, 8)}...</span>
                    </div>
                  )}
                  {entry.exclusionReason && (
                    <div className="p-2 bg-gray-100 rounded text-xs text-gray-700">
                      <strong>Exclusion Reason:</strong> {entry.exclusionReason}
                    </div>
                  )}
                </div>
              </div>

              {/* Right Column: FinSight Match Candidates or Active Match Details */}
              <div className="space-y-4">
                {entry.status === 'MATCHED' ? (
                  <div className="bg-emerald-50 rounded-xl p-5 border border-emerald-200 space-y-3">
                    <h3 className="font-bold text-sm text-emerald-950 uppercase tracking-wider">
                      Reconciled FinSight Transaction
                    </h3>
                    <div className="space-y-2 text-sm text-gray-800">
                      <div className="flex justify-between py-1 border-b border-emerald-200">
                        <span className="text-gray-600">Movement ID</span>
                        <span className="font-mono text-xs">{entry.matchedAccountMovementId}</span>
                      </div>
                      <div className="flex justify-between py-1 border-b border-emerald-200">
                        <span className="text-gray-600">Transaction ID</span>
                        <span className="font-mono text-xs">{entry.matchedTransactionId}</span>
                      </div>
                      <div className="flex justify-between py-1 border-b border-emerald-200">
                        <span className="text-gray-600">Match Method</span>
                        <span className="font-semibold text-emerald-800">{entry.matchMethod || 'EXACT'}</span>
                      </div>
                      {entry.matchedAt && (
                        <div className="flex justify-between py-1 border-b border-emerald-200">
                          <span className="text-gray-600">Matched At</span>
                          <span className="font-mono text-xs">{new Date(entry.matchedAt).toLocaleString()}</span>
                        </div>
                      )}
                    </div>

                    <div className="pt-3 border-t border-emerald-200 space-y-2">
                      <label className="block text-xs text-gray-600">{copy.unmatchReasonPrompt}</label>
                      <input
                        type="text"
                        value={unmatchReason}
                        onChange={(e) => setUnmatchReason(e.target.value)}
                        placeholder="e.g., Matched to wrong customer invoice"
                        className="w-full text-xs p-2 border border-emerald-300 rounded bg-white"
                      />
                      <button
                        type="button"
                        disabled={actionLoading}
                        onClick={handleUnmatch}
                        className="w-full py-2 px-3 text-xs font-semibold text-red-700 bg-red-100 hover:bg-red-200 rounded-lg transition-colors"
                      >
                        {actionLoading ? 'Cancelling...' : copy.unmatchBtn}
                      </button>
                    </div>
                  </div>
                ) : (
                  /* UNMATCHED: Candidate suggestions */
                  <div className="space-y-4">
                    <h3 className="font-bold text-sm text-gray-900 uppercase tracking-wider">
                      {copy.candidateMatchTitle}
                    </h3>

                    {candidates.length === 0 ? (
                      <div className="p-4 bg-amber-50 rounded-xl border border-amber-200 space-y-3 text-sm text-amber-900">
                        <p>{copy.noCandidatesFound}</p>
                        <p className="text-xs text-amber-800">
                          You can create an incomplete draft directly from this bank statement entry. Once recorded in FinSight, return here to complete reconciliation.
                        </p>
                        <button
                          type="button"
                          disabled={actionLoading || !!entry.linkedDraftId}
                          onClick={handleCreateDraft}
                          className="px-4 py-2 text-xs font-semibold text-white bg-[var(--color-brand-primary)] hover:bg-teal-800 disabled:opacity-50 rounded-lg shadow-sm transition-colors"
                        >
                          {entry.linkedDraftId ? 'Draft Already Created' : copy.actionCreateDraft}
                        </button>
                      </div>
                    ) : (
                      <div className="space-y-3">
                        {candidates.map((c) => {
                          const isSelected = c.accountMovementId === selectedCandidateId;
                          return (
                            <div
                              key={c.accountMovementId}
                              onClick={() => setSelectedCandidateId(c.accountMovementId)}
                              className={`p-4 rounded-xl border cursor-pointer transition-all ${
                                isSelected
                                  ? 'border-[var(--color-brand-primary)] bg-teal-50/50 shadow-sm ring-1 ring-[var(--color-brand-primary)]'
                                  : 'border-gray-200 hover:border-gray-300 bg-white'
                              }`}
                            >
                              <div className="flex items-center justify-between mb-2">
                                <div className="flex items-center gap-2">
                                  <input
                                    type="radio"
                                    name="candidate"
                                    checked={isSelected}
                                    onChange={() => setSelectedCandidateId(c.accountMovementId)}
                                    className="text-[var(--color-brand-primary)]"
                                  />
                                  <span className="font-bold text-xs uppercase px-2 py-0.5 rounded bg-gray-100 text-gray-800">
                                    {c.eventType}
                                  </span>
                                </div>
                                <span className={`px-2 py-0.5 rounded-full text-xs font-bold ${
                                  c.confidenceScore >= 90
                                    ? 'bg-emerald-100 text-emerald-800'
                                    : c.confidenceScore >= 70
                                    ? 'bg-blue-100 text-blue-800'
                                    : 'bg-amber-100 text-amber-800'
                                }`}>
                                  {c.confidenceScore}% Match
                                </span>
                              </div>

                              <div className="text-xs space-y-1 text-gray-700">
                                <div className="flex justify-between">
                                  <span className="text-gray-500">Date:</span>
                                  <span className="font-mono">{c.effectiveDate}</span>
                                </div>
                                <div className="flex justify-between">
                                  <span className="text-gray-500">Amount:</span>
                                  <span className="font-mono font-semibold">
                                    {c.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })} PKR
                                  </span>
                                </div>
                                {c.counterpartyName && (
                                  <div className="flex justify-between">
                                    <span className="text-gray-500">Counterparty:</span>
                                    <span className="font-medium text-gray-900">{c.counterpartyName}</span>
                                  </div>
                                )}
                                {c.description && (
                                  <p className="text-gray-600 truncate pt-1">{c.description}</p>
                                )}
                              </div>

                              {/* Explainable Reasons */}
                              {c.matchReasons && c.matchReasons.length > 0 && (
                                <div className="mt-2 pt-2 border-t border-gray-100 text-[11px] text-gray-500 space-y-0.5">
                                  {c.matchReasons.map((r, i) => (
                                    <div key={i} className="flex items-center gap-1 text-emerald-800">
                                      <span>✓</span>
                                      <span>{r}</span>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>
                          );
                        })}

                        {/* Actions for Unmatched */}
                        <div className="pt-2 flex flex-col sm:flex-row gap-2">
                          <button
                            type="button"
                            disabled={actionLoading || !selectedCandidateId}
                            onClick={handleConfirmMatch}
                            className="flex-1 py-2.5 px-4 text-sm font-semibold text-white bg-[var(--color-brand-primary)] hover:bg-teal-800 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg shadow-sm transition-colors"
                          >
                            {actionLoading ? 'Confirming...' : copy.confirmMatchBtn}
                          </button>
                          <button
                            type="button"
                            disabled={actionLoading || !!entry.linkedDraftId}
                            onClick={handleCreateDraft}
                            className="py-2.5 px-3 text-xs font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
                          >
                            {entry.linkedDraftId ? 'Draft Created' : copy.actionCreateDraft}
                          </button>
                        </div>
                      </div>
                    )}

                    {/* Exclude Row Toggle */}
                    <div className="pt-4 border-t border-gray-200">
                      {!showExcludePrompt ? (
                        <button
                          type="button"
                          onClick={() => setShowExcludePrompt(true)}
                          className="text-xs text-gray-500 hover:text-red-600 transition-colors"
                        >
                          {copy.actionExclude}
                        </button>
                      ) : (
                        <div className="space-y-2 bg-gray-50 p-3 rounded-lg border border-gray-200">
                          <label className="block text-xs font-semibold text-gray-700">
                            {copy.excludeReasonPrompt}
                          </label>
                          <input
                            type="text"
                            value={excludeReason}
                            onChange={(e) => setExcludeReason(e.target.value)}
                            placeholder={copy.excludeReasonPlaceholder}
                            className="w-full text-xs p-2 border border-gray-300 rounded bg-white"
                          />
                          <div className="flex gap-2">
                            <button
                              type="button"
                              disabled={actionLoading || !excludeReason.trim()}
                              onClick={handleExclude}
                              className="px-3 py-1.5 text-xs font-semibold text-red-700 bg-red-100 hover:bg-red-200 rounded disabled:opacity-50"
                            >
                              {copy.excludeRowBtn}
                            </button>
                            <button
                              type="button"
                              onClick={() => setShowExcludePrompt(false)}
                              className="px-3 py-1.5 text-xs text-gray-600 hover:bg-gray-200 rounded"
                            >
                              {copy.cancelBtn}
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>
            </div>
          ) : null}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-gray-200 flex justify-end bg-gray-50">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200 rounded-lg transition-colors"
          >
            {copy.closeBtn}
          </button>
        </div>
      </div>
    </div>
  );
}
