'use client';

import React, { useState, useEffect } from 'react';
import { Dialog } from '../../ui/Dialog';
import { Button } from '../../ui/Button';
import { Input } from '../../ui/Input';
import { AlertBanner } from '../../ui/AlertBanner';
import {
  recurringApi,
  RecurringRule,
  RecurringFrequency,
  OccurrencePreviewItem
} from '../../../lib/api/dev-a/recurring';
import { accountsApi } from '../../../lib/api/dev-a/accounts';
import { counterpartiesApi, CounterpartySummaryItem, CounterpartyListResponse } from '../../../lib/api/dev-a/counterparties';
import { useLanguage } from '@/lib/i18n/context';
import { getRecurringCopy } from './recurring-copy';

interface AccountOption {
  id: string;
  name: string;
  type: string;
}

interface RecurringRuleModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  ruleToEdit?: RecurringRule | null;
  locale?: string;
}

export function RecurringRuleModal({
  isOpen,
  onClose,
  onSuccess,
  ruleToEdit,
  locale: propLocale
}: RecurringRuleModalProps) {
  const { locale: ctxLocale } = useLanguage();
  const locale = propLocale || ctxLocale || 'en';
  const t = getRecurringCopy(locale);

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title={ruleToEdit ? t.edit : t.createRuleButton}
      maxWidth="lg"
    >
      {isOpen && (
        <RecurringRuleForm
          key={ruleToEdit ? ruleToEdit.id : 'new'}
          onClose={onClose}
          onSuccess={onSuccess}
          ruleToEdit={ruleToEdit}
          locale={locale}
        />
      )}
    </Dialog>
  );
}

interface RecurringRuleFormProps {
  onClose: () => void;
  onSuccess: () => void;
  ruleToEdit?: RecurringRule | null;
  locale: string;
}

function RecurringRuleForm({
  onClose,
  onSuccess,
  ruleToEdit,
  locale
}: RecurringRuleFormProps) {
  const t = getRecurringCopy(locale);
  const isUr = locale === 'ur';

  const [name, setName] = useState(() => ruleToEdit?.name || '');
  const [description, setDescription] = useState(() => ruleToEdit?.description || '');
  const [eventType, setEventType] = useState(() => ruleToEdit?.eventType || 'OPERATING_EXPENSE_PAYMENT');
  const [accountId, setAccountId] = useState(() => ruleToEdit?.accountId || '');
  const [counterpartyId, setCounterpartyId] = useState(() => ruleToEdit?.counterpartyId || '');
  const [amount, setAmount] = useState(() => ruleToEdit?.amount || '');
  const [frequency, setFrequency] = useState<RecurringFrequency>(() => ruleToEdit?.frequency || 'MONTHLY');
  const [startDate, setStartDate] = useState(() => ruleToEdit?.startDate || new Date().toISOString().split('T')[0]);
  const [endDate, setEndDate] = useState(() => ruleToEdit?.endDate || '');

  const [accounts, setAccounts] = useState<AccountOption[]>([]);
  const [counterparties, setCounterparties] = useState<CounterpartySummaryItem[]>([]);
  const [previewDates, setPreviewDates] = useState<OccurrencePreviewItem[]>([]);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Load active accounts and counterparties
  useEffect(() => {
    let cancelled = false;

    accountsApi.list({ status: 'ACTIVE', size: 100 })
      .then(res => {
        if (cancelled) return;
        const opts: AccountOption[] = res.items.map(a => ({
          id: a.id,
          name: a.accountName,
          type: a.accountType
        }));
        setAccounts(opts);
        if (!ruleToEdit && opts.length > 0) {
          setAccountId(prev => prev || opts[0].id);
        }
      })
      .catch(() => {});

    counterpartiesApi.list({ status: 'ACTIVE', size: 100 })
      .then((res: CounterpartyListResponse) => {
        if (!cancelled) setCounterparties(res.content);
      })
      .catch(() => {});

    return () => { cancelled = true; };
  }, [ruleToEdit]);

  // Load preview when requested for an existing rule
  const handleLoadPreview = async () => {
    if (ruleToEdit) {
      setLoadingPreview(true);
      try {
        const preview = await recurringApi.previewSchedule(ruleToEdit.id, 6);
        setPreviewDates(preview);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to load preview';
        setErrorMessage(msg);
      } finally {
        setLoadingPreview(false);
      }
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!name.trim()) {
      setErrorMessage(isUr ? 'قاعدے کا نام درکار ہے' : 'Rule name is required');
      return;
    }
    if (!accountId) {
      setErrorMessage(isUr ? 'مالیاتی کھاتہ منتخب کریں' : 'Please select an account');
      return;
    }
    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      setErrorMessage(isUr ? 'درست مثبت رقم درج کریں' : 'Please enter a valid positive amount');
      return;
    }
    if (!startDate) {
      setErrorMessage(isUr ? 'شروع کی تاریخ درکار ہے' : 'Start date is required');
      return;
    }

    setSubmitting(true);
    try {
      if (ruleToEdit) {
        // Edit existing rule
        await recurringApi.updateRule(ruleToEdit.id, {
          name: name.trim(),
          description: description.trim() || null,
          eventType,
          accountId,
          counterpartyId: counterpartyId || null,
          amount,
          frequency,
          endDate: endDate || null,
          version: ruleToEdit.version
        });
      } else {
        // Create new rule
        await recurringApi.createRule({
          name: name.trim(),
          description: description.trim() || null,
          eventType,
          accountId,
          counterpartyId: counterpartyId || null,
          amount,
          frequency,
          startDate,
          endDate: endDate || null
        });
      }
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save recurring rule';
      setErrorMessage(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const filteredCounterparties = counterparties.filter(cp => {
    if (eventType === 'DIRECT_SALE_RECEIPT') {
      return cp.type === 'CUSTOMER' || cp.type === 'BOTH';
    }
    if (eventType === 'OPERATING_EXPENSE_PAYMENT') {
      return cp.type === 'SUPPLIER' || cp.type === 'BOTH';
    }
    return true;
  });

  return (
    <form onSubmit={handleSubmit} className="space-y-4" dir={isUr ? 'rtl' : 'ltr'}>
      {/* Fundamental Review Policy Alert Banner */}
      <div className="p-3 bg-teal-50 dark:bg-teal-950/40 border border-teal-200 dark:border-teal-800 rounded-lg text-teal-900 dark:text-teal-100 text-sm flex items-start gap-2">
        <svg className="w-5 h-5 text-teal-600 dark:text-teal-400 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span className="font-medium">{t.notice}</span>
      </div>

      {errorMessage && (
        <AlertBanner variant="error" title={isUr ? 'خرابی' : 'Error'} onDismiss={() => setErrorMessage(null)}>
          {errorMessage}
        </AlertBanner>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <Input
            id="recurring-rule-name-input"
            label={`${t.ruleName} *`}
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder={isUr ? 'مثال: دفتر کا کرایہ' : 'e.g. Office Rent'}
            required
          />
        </div>

        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1">
            {t.eventType} *
          </label>
          <select
            id="recurring-event-type-select"
            value={eventType}
            onChange={e => {
              setEventType(e.target.value);
              setCounterpartyId('');
            }}
            className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-teal-600"
          >
            <option value="OPERATING_EXPENSE_PAYMENT">{t.eventTypes.OPERATING_EXPENSE_PAYMENT}</option>
            <option value="DIRECT_SALE_RECEIPT">{t.eventTypes.DIRECT_SALE_RECEIPT}</option>
          </select>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1">
            {t.account} *
          </label>
          <select
            id="recurring-account-select"
            value={accountId}
            onChange={e => setAccountId(e.target.value)}
            required
            className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-teal-600"
          >
            <option value="">{isUr ? '-- کھاتہ منتخب کریں --' : '-- Select Account --'}</option>
            {accounts.map(acc => (
              <option key={acc.id} value={acc.id}>
                {acc.name} ({acc.type})
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1">
            {t.counterparty}
          </label>
          <select
            id="recurring-counterparty-select"
            value={counterpartyId}
            onChange={e => setCounterpartyId(e.target.value)}
            className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-teal-600"
          >
            <option value="">{isUr ? '-- کوئی فریق نہیں --' : '-- None --'}</option>
            {filteredCounterparties.map(cp => (
              <option key={cp.id} value={cp.id}>
                {cp.displayName} ({cp.type})
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <Input
            id="recurring-amount-input"
            label={`${t.amount} *`}
            type="text"
            value={amount}
            onChange={e => setAmount(e.target.value)}
            placeholder="50000.00"
            required
          />
        </div>

        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1">
            {t.frequency} *
          </label>
          <select
            id="recurring-frequency-select"
            value={frequency}
            onChange={e => setFrequency(e.target.value as RecurringFrequency)}
            className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-teal-600"
          >
            <option value="WEEKLY">{t.frequencies.WEEKLY}</option>
            <option value="BIWEEKLY">{t.frequencies.BIWEEKLY}</option>
            <option value="MONTHLY">{t.frequencies.MONTHLY}</option>
            <option value="QUARTERLY">{t.frequencies.QUARTERLY}</option>
            <option value="ANNUALLY">{t.frequencies.ANNUALLY}</option>
          </select>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <Input
            id="recurring-start-date-input"
            label={`${t.startDate} *`}
            type="date"
            value={startDate}
            onChange={e => setStartDate(e.target.value)}
            disabled={!!ruleToEdit}
            required
          />
        </div>

        <div>
          <Input
            id="recurring-end-date-input"
            label={t.endDate}
            type="date"
            value={endDate}
            onChange={e => setEndDate(e.target.value)}
          />
        </div>
      </div>

      <div>
        <Input
          id="recurring-description-input"
          label={isUr ? 'وضاحت (اختیاری)' : 'Description (Optional)'}
          value={description}
          onChange={e => setDescription(e.target.value)}
          placeholder={isUr ? 'اضافی معلومات...' : 'Additional notes...'}
        />
      </div>

      {/* Existing Rule Preview Section */}
      {ruleToEdit && (
        <div className="pt-2 border-t border-slate-200 dark:border-slate-800">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-bold text-slate-600 dark:text-slate-400 uppercase tracking-wider">
              {t.nextOccurrences}
            </span>
            <Button
              id="recurring-load-preview-btn"
              type="button"
              variant="secondary"
              size="sm"
              onClick={handleLoadPreview}
              disabled={loadingPreview}
            >
              {loadingPreview ? (isUr ? 'لوڈ ہو رہا ہے...' : 'Loading...') : t.preview}
            </Button>
          </div>

          {previewDates.length > 0 && (
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 mt-2">
              {previewDates.map((item, idx) => (
                <div
                  key={idx}
                  className="p-2 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded text-xs text-center"
                >
                  <div className="font-semibold text-slate-900 dark:text-slate-100">{item.scheduledDate}</div>
                  <div className="text-teal-700 dark:text-teal-400 font-mono mt-0.5">{item.amount} PKR</div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="flex justify-end gap-3 pt-3 border-t border-slate-200 dark:border-slate-800">
        <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
          {t.cancel}
        </Button>
        <Button id="recurring-submit-btn" type="submit" variant="primary" disabled={submitting}>
          {submitting ? (isUr ? 'محفوظ ہو رہا ہے...' : 'Saving...') : t.saveRule}
        </Button>
      </div>
    </form>
  );
}
