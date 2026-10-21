'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { Button } from '../../ui/Button';
import { Dialog } from '../../ui/Dialog';
import { AlertBanner } from '../../ui/AlertBanner';
import {
  recurringApi,
  RecurringRule,
  RecurringOccurrence,
  OccurrencePreviewItem
} from '../../../lib/api/dev-a/recurring';
import { useLanguage } from '@/lib/i18n/context';
import { getRecurringCopy } from './recurring-copy';
import { RecurringRuleModal } from './RecurringRuleModal';
import { OccurrenceList } from './OccurrenceList';

interface RecurringWorkspaceProps {
  locale?: string;
}

export function RecurringWorkspace({ locale: propLocale }: RecurringWorkspaceProps) {
  const { locale: ctxLocale } = useLanguage();
  const locale = propLocale || ctxLocale || 'en';
  const t = getRecurringCopy(locale);
  const isUr = locale === 'ur';

  const [activeTab, setActiveTab] = useState<'active' | 'paused' | 'occurrences'>('active');
  const [activeRules, setActiveRules] = useState<RecurringRule[]>([]);
  const [pausedRules, setPausedRules] = useState<RecurringRule[]>([]);
  const [occurrences, setOccurrences] = useState<RecurringOccurrence[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Modals state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [ruleToEdit, setRuleToEdit] = useState<RecurringRule | null>(null);

  // Preview schedule modal
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [previewRuleName, setPreviewRuleName] = useState('');
  const [previewItems, setPreviewItems] = useState<OccurrencePreviewItem[]>([]);
  const [loadingPreview, setLoadingPreview] = useState(false);

  // Generate Due action
  const [generatingDue, setGeneratingDue] = useState(false);

  const [reloadKey, setReloadKey] = useState(0);
  const loadData = useCallback(() => {
    setLoading(true);
    setReloadKey(k => k + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      recurringApi.getRules({ status: 'ACTIVE', size: 100 }),
      recurringApi.getRules({ status: 'PAUSED', size: 100 }),
      recurringApi.getOccurrences({ size: 100 })
    ])
      .then(([activeRes, pausedRes, occRes]) => {
        if (cancelled) return;
        setActiveRules(activeRes.content || []);
        setPausedRules(pausedRes.content || []);
        setOccurrences(occRes.content || []);
        setErrorMsg(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const msg = err instanceof Error ? err.message : 'Failed to load recurring data';
        setErrorMsg(msg);
        setLoading(false);
      });

    return () => { cancelled = true; };
  }, [reloadKey]);

  const handleCreateRule = () => {
    setRuleToEdit(null);
    setIsModalOpen(true);
  };

  const handleEditRule = (rule: RecurringRule) => {
    setRuleToEdit(rule);
    setIsModalOpen(true);
  };

  const handlePauseRule = async (ruleId: string) => {
    try {
      await recurringApi.pauseRule(ruleId);
      setSuccessMsg(t.pauseSuccess);
      loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to pause rule';
      setErrorMsg(msg);
    }
  };

  const handleResumeRule = async (ruleId: string) => {
    try {
      await recurringApi.resumeRule(ruleId);
      setSuccessMsg(t.resumeSuccess);
      loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to resume rule';
      setErrorMsg(msg);
    }
  };

  const handleArchiveRule = async (ruleId: string) => {
    if (!confirm(isUr ? 'کیا آپ واقعی اس قاعدے کو محفوظ کرنا چاہتے ہیں؟' : 'Are you sure you want to archive this rule?')) {
      return;
    }
    try {
      await recurringApi.archiveRule(ruleId);
      setSuccessMsg(t.archiveSuccess);
      loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to archive rule';
      setErrorMsg(msg);
    }
  };

  const handleOpenPreview = async (rule: RecurringRule) => {
    setPreviewRuleName(rule.name);
    setPreviewModalOpen(true);
    setLoadingPreview(true);
    try {
      const items = await recurringApi.previewSchedule(rule.id, 8);
      setPreviewItems(items);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to calculate schedule preview';
      setErrorMsg(msg);
    } finally {
      setLoadingPreview(false);
    }
  };

  const handleGenerateDue = async () => {
    setGeneratingDue(true);
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      const res = await recurringApi.generateDue();
      setSuccessMsg(res.message || t.generateSuccess);
      await loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to process due recurring operations';
      setErrorMsg(msg);
    } finally {
      setGeneratingDue(false);
    }
  };

  const displayedRules = activeTab === 'active' ? activeRules : pausedRules;

  return (
    <div className="space-y-6" dir={isUr ? 'rtl' : 'ltr'}>
      {/* Policy Notification Banner */}
      <div className="p-4 bg-teal-50 dark:bg-teal-950/40 border border-teal-200 dark:border-teal-800 rounded-xl text-teal-900 dark:text-teal-100 flex items-start gap-3 shadow-sm">
        <svg className="w-5 h-5 text-teal-600 dark:text-teal-400 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <div>
          <h4 className="font-semibold text-sm">{isUr ? 'بنیادی اکاؤنٹنگ پالیسی' : 'Accounting Policy Invariant'}</h4>
          <p className="text-xs text-teal-800 dark:text-teal-200 mt-0.5">{t.notice}</p>
        </div>
      </div>

      {errorMsg && (
        <AlertBanner variant="error" title={isUr ? 'خرابی' : 'Error'} onDismiss={() => setErrorMsg(null)}>
          {errorMsg}
        </AlertBanner>
      )}
      {successMsg && (
        <AlertBanner variant="success" title={isUr ? 'کامیابی' : 'Success'} onDismiss={() => setSuccessMsg(null)}>
          {successMsg}
        </AlertBanner>
      )}

      {/* Header and Action Buttons */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">{t.title}</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-0.5">{t.subtitle}</p>
        </div>

        <div className="flex items-center gap-3">
          <Button
            id="recurring-generate-due-btn"
            variant="secondary"
            onClick={handleGenerateDue}
            disabled={generatingDue || loading}
            className="border-teal-300 dark:border-teal-700 text-teal-800 dark:text-teal-200 hover:bg-teal-50 dark:hover:bg-teal-950"
          >
            {generatingDue ? (
              <span className="flex items-center gap-2">
                <svg className="animate-spin h-4 w-4 text-teal-600" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                </svg>
                {t.generateDueLoading}
              </span>
            ) : (
              t.generateDueButton
            )}
          </Button>

          <Button
            id="recurring-create-rule-btn"
            variant="primary"
            onClick={handleCreateRule}
            className="bg-[#0D5C52] hover:bg-[#0A4840] text-white"
          >
            + {t.createRuleButton}
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-200 dark:border-slate-800 gap-2">
        <button
          id="recurring-tab-active"
          onClick={() => setActiveTab('active')}
          className={`px-4 py-2 text-sm font-semibold border-b-2 transition-colors ${
            activeTab === 'active'
              ? 'border-teal-600 text-teal-600 dark:border-teal-400 dark:text-teal-400'
              : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400'
          }`}
        >
          {t.activeRulesTab} ({activeRules.length})
        </button>
        <button
          id="recurring-tab-paused"
          onClick={() => setActiveTab('paused')}
          className={`px-4 py-2 text-sm font-semibold border-b-2 transition-colors ${
            activeTab === 'paused'
              ? 'border-teal-600 text-teal-600 dark:border-teal-400 dark:text-teal-400'
              : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400'
          }`}
        >
          {t.pausedRulesTab} ({pausedRules.length})
        </button>
        <button
          id="recurring-tab-occurrences"
          onClick={() => setActiveTab('occurrences')}
          className={`px-4 py-2 text-sm font-semibold border-b-2 transition-colors ${
            activeTab === 'occurrences'
              ? 'border-teal-600 text-teal-600 dark:border-teal-400 dark:text-teal-400'
              : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400'
          }`}
        >
          {t.occurrencesTab} ({occurrences.length})
        </button>
      </div>

      {/* Tab Content */}
      {loading ? (
        <div className="p-12 text-center text-slate-500">
          <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-teal-600 mb-2"></div>
          <p className="text-sm">{isUr ? 'معلومات لوڈ ہو رہی ہے...' : 'Loading recurring data...'}</p>
        </div>
      ) : activeTab === 'occurrences' ? (
        <OccurrenceList occurrences={occurrences} onRefresh={loadData} locale={locale} />
      ) : displayedRules.length === 0 ? (
        <div className="p-8 text-center bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 text-slate-500">
          <svg className="w-12 h-12 mx-auto mb-3 text-slate-300 dark:text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
          </svg>
          <p className="font-medium text-sm">{t.noRulesFound}</p>
        </div>
      ) : (
        <div className="space-y-3">
          {/* Desktop Table View */}
          <div className="hidden md:block overflow-x-auto rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
            <table className="w-full text-left text-sm" dir={isUr ? 'rtl' : 'ltr'}>
              <thead className="bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-400 font-semibold text-xs uppercase tracking-wider border-b border-slate-200 dark:border-slate-800">
                <tr>
                  <th className="px-4 py-3">{t.ruleName}</th>
                  <th className="px-4 py-3">{t.account}</th>
                  <th className="px-4 py-3">{t.amount}</th>
                  <th className="px-4 py-3">{t.frequency}</th>
                  <th className="px-4 py-3">{t.nextDueDate}</th>
                  <th className="px-4 py-3">{t.status}</th>
                  <th className="px-4 py-3 text-right">{t.actions}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {displayedRules.map(rule => (
                  <tr key={rule.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors">
                    <td className="px-4 py-3">
                      <div className="font-semibold text-slate-900 dark:text-slate-100">{rule.name}</div>
                      <div className="text-xs text-slate-500">
                        {rule.eventType === 'DIRECT_SALE_RECEIPT' ? (isUr ? 'وصولی' : 'Receipt') : (isUr ? 'خرچہ' : 'Expense')}
                        {rule.counterpartyName && ` • ${rule.counterpartyName}`}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-slate-600 dark:text-slate-300">
                      {rule.accountName}
                    </td>
                    <td className="px-4 py-3 font-mono font-medium text-slate-900 dark:text-slate-100 whitespace-nowrap">
                      {rule.amount} <span className="text-xs text-slate-500">PKR</span>
                    </td>
                    <td className="px-4 py-3 text-slate-600 dark:text-slate-300">
                      {t.frequencies[rule.frequency] || rule.frequency}
                    </td>
                    <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100 whitespace-nowrap">
                      {rule.nextDueDate || '—'}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold ${
                        rule.status === 'ACTIVE'
                          ? 'bg-teal-100 text-teal-800 dark:bg-teal-900/50 dark:text-teal-200'
                          : 'bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-200'
                      }`}>
                        {t.ruleStatuses[rule.status]}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right whitespace-nowrap space-x-2">
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => handleOpenPreview(rule)}
                        className="text-xs text-teal-700 hover:text-teal-800"
                      >
                        {t.preview}
                      </Button>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => handleEditRule(rule)}
                        className="text-xs"
                      >
                        {t.edit}
                      </Button>
                      {rule.status === 'ACTIVE' ? (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => handlePauseRule(rule.id)}
                          className="text-xs text-amber-600 hover:text-amber-700"
                        >
                          {t.pause}
                        </Button>
                      ) : (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => handleResumeRule(rule.id)}
                          className="text-xs text-teal-600 hover:text-teal-700"
                        >
                          {t.resume}
                        </Button>
                      )}
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => handleArchiveRule(rule.id)}
                        className="text-xs text-rose-600 hover:text-rose-700"
                      >
                        {t.archive}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile Card View */}
          <div className="md:hidden space-y-3" dir={isUr ? 'rtl' : 'ltr'}>
            {displayedRules.map(rule => (
              <div
                key={rule.id}
                className="p-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm space-y-3"
              >
                <div className="flex items-center justify-between">
                  <div className="font-semibold text-slate-900 dark:text-slate-100 text-base">{rule.name}</div>
                  <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold ${
                    rule.status === 'ACTIVE'
                      ? 'bg-teal-100 text-teal-800 dark:bg-teal-900/50 dark:text-teal-200'
                      : 'bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-200'
                  }`}>
                    {t.ruleStatuses[rule.status]}
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-2 text-xs text-slate-600 dark:text-slate-300">
                  <div>
                    <span className="text-slate-400 block">{t.account}:</span>
                    <span className="font-medium">{rule.accountName}</span>
                  </div>
                  <div>
                    <span className="text-slate-400 block">{t.amount}:</span>
                    <span className="font-mono font-bold text-slate-900 dark:text-slate-100">{rule.amount} PKR</span>
                  </div>
                  <div>
                    <span className="text-slate-400 block">{t.frequency}:</span>
                    <span>{t.frequencies[rule.frequency] || rule.frequency}</span>
                  </div>
                  <div>
                    <span className="text-slate-400 block">{t.nextDueDate}:</span>
                    <span className="font-medium text-teal-700 dark:text-teal-400">{rule.nextDueDate || '—'}</span>
                  </div>
                </div>

                <div className="pt-2 border-t border-slate-100 dark:border-slate-800 flex flex-wrap gap-2 justify-end">
                  <Button variant="secondary" size="sm" onClick={() => handleOpenPreview(rule)} className="text-xs">
                    {t.preview}
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => handleEditRule(rule)} className="text-xs">
                    {t.edit}
                  </Button>
                  {rule.status === 'ACTIVE' ? (
                    <Button variant="secondary" size="sm" onClick={() => handlePauseRule(rule.id)} className="text-xs text-amber-600">
                      {t.pause}
                    </Button>
                  ) : (
                    <Button variant="secondary" size="sm" onClick={() => handleResumeRule(rule.id)} className="text-xs text-teal-600">
                      {t.resume}
                    </Button>
                  )}
                  <Button variant="secondary" size="sm" onClick={() => handleArchiveRule(rule.id)} className="text-xs text-rose-600">
                    {t.archive}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Rule Create/Edit Modal */}
      <RecurringRuleModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSuccess={loadData}
        ruleToEdit={ruleToEdit}
        locale={locale}
      />

      {/* Preview Schedule Modal */}
      <Dialog
        isOpen={previewModalOpen}
        onClose={() => setPreviewModalOpen(false)}
        title={`${t.previewScheduleTitle} — ${previewRuleName}`}
        maxWidth="md"
      >
        <div className="space-y-4" dir={isUr ? 'rtl' : 'ltr'}>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {t.previewDisclaimer}
          </p>

          {loadingPreview ? (
            <div className="p-8 text-center text-slate-500">
              <div className="inline-block animate-spin rounded-full h-6 w-6 border-b-2 border-teal-600 mb-2"></div>
              <p className="text-xs">{isUr ? 'حساب لگایا جا رہا ہے...' : 'Calculating schedule...'}</p>
            </div>
          ) : previewItems.length === 0 ? (
            <p className="text-xs text-slate-500 text-center py-4">{t.emptyPreview}</p>
          ) : (
            <div className="space-y-2">
              {previewItems.map((item, idx) => (
                <div
                  key={idx}
                  className="flex items-center justify-between p-2.5 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg text-sm"
                >
                  <span className="font-semibold text-slate-900 dark:text-slate-100">
                    #{idx + 1} &nbsp; {item.scheduledDate}
                  </span>
                  <span className="font-mono font-medium text-teal-700 dark:text-teal-400">
                    {item.amount} {item.currency}
                  </span>
                </div>
              ))}
            </div>
          )}

          <div className="flex justify-end pt-3 border-t border-slate-200 dark:border-slate-800">
            <Button variant="secondary" onClick={() => setPreviewModalOpen(false)}>
              {t.close}
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
