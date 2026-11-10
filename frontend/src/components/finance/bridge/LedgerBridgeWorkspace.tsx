"use client";

import React, { useState, useEffect, useCallback } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { AlertBanner } from '@/components/ui/AlertBanner';
import { formatPKR } from '@/lib/utils/currency';
import { useLanguage } from '@/lib/i18n/context';
import { useSession } from '@/components/auth/SessionProvider';
import {
  ledgerBridgeApi,
  type LedgerMonthlyProjectionResponse,
  type LedgerCutoverRequest,
} from '@/lib/api/ledgerBridge';
import { ArrowRight, CheckCircle2, AlertTriangle, RefreshCw } from 'lucide-react';

interface LedgerBridgeWorkspaceProps {
  initialYearMonth?: string;
  onCutoverSuccess?: () => void;
}

export function LedgerBridgeWorkspace({
  initialYearMonth = new Date().toISOString().substring(0, 7),
  onCutoverSuccess,
}: LedgerBridgeWorkspaceProps) {
  const { locale, direction } = useLanguage();
  const { can } = useSession();
  const canCutover = can('RECORD_CREATE_UPDATE');

  const [yearMonth, setYearMonth] = useState<string>(initialYearMonth);
  const [projection, setProjection] = useState<LedgerMonthlyProjectionResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Manual adjustment inputs
  const [manualCogs, setManualCogs] = useState<string>('');
  const [manualInventory, setManualInventory] = useState<string>('');
  const [manualLoan, setManualLoan] = useState<string>('');
  const [manualInterest, setManualInterest] = useState<string>('');
  const [financingType, setFinancingType] = useState<string>('none');
  const [confirmationNote, setConfirmationNote] = useState<string>('');

  // Confirmation dialog state
  const [isConfirmOpen, setIsConfirmOpen] = useState<boolean>(false);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const fetchProjection = useCallback(async (period: string) => {
    setLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const data = await ledgerBridgeApi.getMonthlyProjection(period);
      setProjection(data);
      if (data.existingMonthlyRecord) {
        setManualCogs(data.existingMonthlyRecord.cogs ? String(data.existingMonthlyRecord.cogs) : '');
        setManualInventory(data.existingMonthlyRecord.inventoryValue ? String(data.existingMonthlyRecord.inventoryValue) : '');
        setManualLoan(data.existingMonthlyRecord.loanOutstanding ? String(data.existingMonthlyRecord.loanOutstanding) : '');
        setManualInterest(data.existingMonthlyRecord.interestExpense ? String(data.existingMonthlyRecord.interestExpense) : '');
        setFinancingType(data.existingMonthlyRecord.financingType || 'none');
      } else {
        setManualCogs('');
        setManualInventory('');
        setManualLoan('');
        setManualInterest('');
        setFinancingType('none');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load ledger projection';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProjection(yearMonth);
  }, [yearMonth, fetchProjection]);

  const handleApplyCutover = async () => {
    if (!projection) return;
    setSubmitting(true);
    setError(null);
    try {
      const req: LedgerCutoverRequest = {
        yearMonth,
        manualCogs: manualCogs ? Number(manualCogs) : null,
        manualInventoryValue: manualInventory ? Number(manualInventory) : null,
        manualLoanOutstanding: manualLoan ? Number(manualLoan) : null,
        manualInterestExpense: manualInterest ? Number(manualInterest) : null,
        financingType: financingType || 'none',
        expectedVersion: projection.existingMonthlyRecord ? projection.existingMonthlyRecord.version : null,
        confirmationNote: confirmationNote || 'Authoritative cutover applied via Ledger Bridge',
      };
      const res = await ledgerBridgeApi.applyLedgerProjection(req);
      setSuccessMessage(res.message);
      setIsConfirmOpen(false);
      await fetchProjection(yearMonth);
      if (onCutoverSuccess) onCutoverSuccess();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Cutover failed';
      setError(msg);
      setIsConfirmOpen(false);
    } finally {
      setSubmitting(false);
    }
  };

  const isUrdu = locale === 'ur';

  return (
    <div dir={direction} className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white p-4 rounded-lg border border-gray-200 shadow-sm">
        <div>
          <h2 className="text-xl font-bold text-gray-900">
            {isUrdu ? 'لیجر ٹو ہیلتھ ریکارڈ برج' : 'Ledger-to-Health Record Bridge'}
          </h2>
          <p className="text-sm text-gray-600">
            {isUrdu
              ? 'پوسٹ شدہ ٹرانزیکشنز سے مستند ماہانہ مالیاتی سمری اور ہیلتھ اسکور اخذ کریں۔'
              : 'Derive authoritative monthly financial summaries and health scores from posted ledger transactions.'}
          </p>
        </div>

        <div className="flex items-center gap-3">
          <label htmlFor="bridge-period" className="text-sm font-medium text-gray-700">
            {isUrdu ? 'مہینہ:' : 'Period:'}
          </label>
          <input
            id="bridge-period"
            type="month"
            value={yearMonth}
            onChange={(e) => setYearMonth(e.target.value)}
            className="px-3 py-1.5 border border-gray-300 rounded text-sm focus:ring-2 focus:ring-blue-500"
          />
          <Button
            variant="secondary"
            size="sm"
            onClick={() => fetchProjection(yearMonth)}
            disabled={loading}
            aria-label={isUrdu ? 'دوبارہ لوڈ کریں' : 'Refresh'}
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      {/* Notifications */}
      {error && (
        <AlertBanner variant="error">
          <p>{error}</p>
        </AlertBanner>
      )}

      {successMessage && (
        <AlertBanner variant="success">
          <p>{successMessage}</p>
        </AlertBanner>
      )}

      {/* Readiness & Completeness Banner */}
      {projection && (
        <Card className="p-4 bg-slate-50 border border-slate-200">
          <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-3">
            <div className="flex items-center gap-2">
              <span
                className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                  projection.completeness.readinessStatus === 'READY_FOR_PREVIEW'
                    ? 'bg-green-100 text-green-800'
                    : projection.completeness.readinessStatus === 'GAPS_DETECTED'
                    ? 'bg-amber-100 text-amber-800'
                    : 'bg-gray-100 text-gray-800'
                }`}
              >
                {projection.completeness.readinessStatus}
              </span>
              <span className="text-sm font-medium text-gray-700">
                {projection.explanation}
              </span>
            </div>
            <span className="text-xs text-gray-500">
              Engine: {projection.calculationVersion}
            </span>
          </div>

          {projection.completeness.warnings && projection.completeness.warnings.length > 0 && (
            <div className="mt-3 pt-3 border-t border-slate-200 space-y-1">
              {projection.completeness.warnings.map((w, i) => (
                <p key={i} className="text-xs text-amber-700 flex items-center gap-1">
                  <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                  {w}
                </p>
              ))}
            </div>
          )}
        </Card>
      )}

      {/* Comparison Grid */}
      {projection && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Ledger Derived Figures */}
          <Card className="p-5 border-blue-200 bg-blue-50/30">
            <h3 className="text-base font-semibold text-blue-900 mb-4 pb-2 border-b border-blue-200 flex items-center justify-between">
              <span>{isUrdu ? 'لیجر سے اخذ شدہ اعدادوشمار' : 'Ledger Derived Figures'}</span>
              <span className="text-xs px-2 py-0.5 bg-blue-100 text-blue-800 rounded">
                {isUrdu ? 'پوسٹ شدہ' : 'Posted'}
              </span>
            </h3>
            <dl className="space-y-3 text-sm">
              <div className="flex justify-between py-1 border-b border-blue-100">
                <dt className="text-gray-600">{isUrdu ? 'کیش آمد (ان فلو):' : 'Cash Inflow:'}</dt>
                <dd className="font-semibold text-gray-900">{formatPKR(projection.ledgerCashInflow)}</dd>
              </div>
              <div className="flex justify-between py-1 border-b border-blue-100">
                <dt className="text-gray-600">{isUrdu ? 'کیش اخراج (آؤٹ فلو):' : 'Cash Outflow:'}</dt>
                <dd className="font-semibold text-gray-900">{formatPKR(projection.ledgerCashOutflow)}</dd>
              </div>
              <div className="flex justify-between py-1 border-b border-blue-100">
                <dt className="text-gray-600">{isUrdu ? 'نیٹ کیش فلو:' : 'Net Cash Flow:'}</dt>
                <dd className="font-semibold text-gray-900">{formatPKR(projection.ledgerNetCashFlow)}</dd>
              </div>
              <div className="flex justify-between py-1 border-b border-blue-100">
                <dt className="text-gray-600">{isUrdu ? 'آپریٹنگ ریونیو:' : 'Operating Revenue:'}</dt>
                <dd className="font-semibold text-gray-900">{formatPKR(projection.ledgerOperatingRevenue)}</dd>
              </div>
              <div className="flex justify-between py-1 border-b border-blue-100">
                <dt className="text-gray-600">{isUrdu ? 'آپریٹنگ اخراجات:' : 'Operating Expenses:'}</dt>
                <dd className="font-semibold text-gray-900">{formatPKR(projection.ledgerOperatingExpenses)}</dd>
              </div>
              <div className="flex justify-between py-1 border-b border-blue-100">
                <dt className="text-gray-600">{isUrdu ? 'اوپن وصولیاں (AR):' : 'Open Receivables (AR):'}</dt>
                <dd className="font-semibold text-gray-900">{formatPKR(projection.asOfReceivablesOutstanding)}</dd>
              </div>
              <div className="flex justify-between py-1 border-b border-blue-100">
                <dt className="text-gray-600">{isUrdu ? 'اوپن ادائیگیاں (AP):' : 'Open Payables (AP):'}</dt>
                <dd className="font-semibold text-gray-900">{formatPKR(projection.asOfPayablesOutstanding)}</dd>
              </div>
              <div className="flex justify-between py-1">
                <dt className="text-gray-600">{isUrdu ? 'اختتامی کیش بیلنس:' : 'End Cash Balance:'}</dt>
                <dd className="font-semibold text-gray-900">{formatPKR(projection.currentCashBalanceEom)}</dd>
              </div>
            </dl>
          </Card>

          {/* Current Saved Monthly Record Comparison */}
          <Card className="p-5 border-gray-200">
            <h3 className="text-base font-semibold text-gray-900 mb-4 pb-2 border-b border-gray-200 flex items-center justify-between">
              <span>{isUrdu ? 'موجودہ محفوظ شدہ ریکارڈ' : 'Current Saved Monthly Record'}</span>
              <span className={`text-xs px-2 py-0.5 rounded ${projection.hasExistingMonthlyRecord ? 'bg-amber-100 text-amber-800' : 'bg-gray-100 text-gray-700'}`}>
                {projection.hasExistingMonthlyRecord ? (isUrdu ? 'موجود ہے' : 'Existing') : (isUrdu ? 'نیا مہینہ' : 'Not Saved')}
              </span>
            </h3>

            {projection.existingMonthlyRecord ? (
              <dl className="space-y-3 text-sm">
                <div className="flex justify-between py-1 border-b border-gray-100">
                  <dt className="text-gray-600">{isUrdu ? 'کیش آمد:' : 'Cash Inflow:'}</dt>
                  <dd className="font-medium text-gray-800">{formatPKR(projection.existingMonthlyRecord.cashInflow)}</dd>
                </div>
                <div className="flex justify-between py-1 border-b border-gray-100">
                  <dt className="text-gray-600">{isUrdu ? 'کیش اخراج:' : 'Cash Outflow:'}</dt>
                  <dd className="font-medium text-gray-800">{formatPKR(projection.existingMonthlyRecord.cashOutflow)}</dd>
                </div>
                <div className="flex justify-between py-1 border-b border-gray-100">
                  <dt className="text-gray-600">{isUrdu ? 'ریونیو:' : 'Revenue:'}</dt>
                  <dd className="font-medium text-gray-800">{formatPKR(projection.existingMonthlyRecord.revenue)}</dd>
                </div>
                <div className="flex justify-between py-1 border-b border-gray-100">
                  <dt className="text-gray-600">{isUrdu ? 'آپریٹنگ اخراجات:' : 'Operating Expenses:'}</dt>
                  <dd className="font-medium text-gray-800">{formatPKR(projection.existingMonthlyRecord.operatingExpenses)}</dd>
                </div>
                <div className="flex justify-between py-1 border-b border-gray-100">
                  <dt className="text-gray-600">{isUrdu ? 'فروخت شدہ مال کی لاگت (COGS):' : 'COGS:'}</dt>
                  <dd className="font-medium text-gray-800">
                    {projection.existingMonthlyRecord.cogs != null ? formatPKR(projection.existingMonthlyRecord.cogs) : '—'}
                  </dd>
                </div>
                <div className="flex justify-between py-1 border-b border-gray-100">
                  <dt className="text-gray-600">{isUrdu ? 'انوینٹری ویلیو:' : 'Inventory Value:'}</dt>
                  <dd className="font-medium text-gray-800">
                    {projection.existingMonthlyRecord.inventoryValue != null ? formatPKR(projection.existingMonthlyRecord.inventoryValue) : '—'}
                  </dd>
                </div>
                <div className="flex justify-between py-1 border-b border-gray-100">
                  <dt className="text-gray-600">{isUrdu ? 'قرضہ بقایا:' : 'Loan Outstanding:'}</dt>
                  <dd className="font-medium text-gray-800">
                    {projection.existingMonthlyRecord.loanOutstanding != null ? formatPKR(projection.existingMonthlyRecord.loanOutstanding) : '—'}
                  </dd>
                </div>
                <div className="flex justify-between py-1">
                  <dt className="text-gray-600">{isUrdu ? 'ورژن نمبر:' : 'Version:'}</dt>
                  <dd className="font-mono text-gray-700">v{projection.existingMonthlyRecord.version}</dd>
                </div>
              </dl>
            ) : (
              <div className="text-center py-10 text-gray-500">
                <p>{isUrdu ? 'اس مہینے کا کوئی سابقہ دستی ریکارڈ موجود نہیں ہے۔' : 'No existing manual record for this month.'}</p>
                <p className="text-xs mt-1 text-gray-400">
                  {isUrdu ? 'کٹ اوور کرنے سے ایک نیا آفیشل ہیلتھ ریکارڈ بن جائے گا۔' : 'Applying cutover will create a fresh official monthly record.'}
                </p>
              </div>
            )}
          </Card>
        </div>
      )}

      {/* Manual Adjustments / Preserved Non-Ledger Fields */}
      <Card className="p-5 border-gray-200">
        <h3 className="text-base font-semibold text-gray-900 mb-3">
          {isUrdu ? 'اضافی / غیر لیجر فیلڈز کی تصدیق' : 'Manual Non-Ledger Fields & Preservations'}
        </h3>
        <p className="text-xs text-gray-500 mb-4">
          {isUrdu
            ? 'اگر یہ فیلڈز خالی چھوڑی جائیں تو پہلے سے موجود دستی ویلیوز برقرار رکھی جائیں گی۔'
            : 'If left blank, existing manual values for COGS, Inventory, and Financing will be preserved.'}
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">
              {isUrdu ? 'فروخت شدہ مال کی لاگت (COGS)' : 'Manual COGS (PKR)'}
            </label>
            <input
              type="number"
              step="0.01"
              value={manualCogs}
              onChange={(e) => setManualCogs(e.target.value)}
              placeholder="0.00"
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">
              {isUrdu ? 'ماہ کے اختتام پر انوینٹری' : 'Ending Inventory Value (PKR)'}
            </label>
            <input
              type="number"
              step="0.01"
              value={manualInventory}
              onChange={(e) => setManualInventory(e.target.value)}
              placeholder="0.00"
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">
              {isUrdu ? 'قرضہ یا فنانسنگ بقایا' : 'Loan Outstanding (PKR)'}
            </label>
            <input
              type="number"
              step="0.01"
              value={manualLoan}
              onChange={(e) => setManualLoan(e.target.value)}
              placeholder="0.00"
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">
              {isUrdu ? 'مارک اپ یا انٹرسٹ خرچ' : 'Interest / Markup Expense (PKR)'}
            </label>
            <input
              type="number"
              step="0.01"
              value={manualInterest}
              onChange={(e) => setManualInterest(e.target.value)}
              placeholder="0.00"
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">
              {isUrdu ? 'فنانسنگ کی قسم' : 'Financing Type'}
            </label>
            <select
              value={financingType}
              onChange={(e) => setFinancingType(e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm bg-white"
            >
              <option value="none">{isUrdu ? 'کوئی نہیں (None)' : 'None'}</option>
              <option value="islamic">{isUrdu ? 'اسلامی فنانسنگ (Islamic)' : 'Islamic'}</option>
              <option value="conventional">{isUrdu ? 'روایتی فنانسنگ (Conventional)' : 'Conventional'}</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">
              {isUrdu ? 'کٹ اوور کا نوٹ / حوالہ' : 'Confirmation Note / Reason'}
            </label>
            <input
              type="text"
              value={confirmationNote}
              onChange={(e) => setConfirmationNote(e.target.value)}
              placeholder={isUrdu ? 'مثلاً: نومبر کا آفیشل کٹ اوور' : 'e.g. November Ledger Cutover'}
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm"
            />
          </div>
        </div>

        {/* Action Button */}
        <div className="mt-6 flex justify-end">
          <Button
            variant="primary"
            disabled={!projection || !canCutover || submitting}
            onClick={() => setIsConfirmOpen(true)}
            rightIcon={<ArrowRight className="w-4 h-4" />}
          >
            {isUrdu ? 'ماہانہ ہیلتھ ریکارڈ میں کٹ اوور کا نفاذ کریں' : 'Apply Authoritative Cutover to Health Record'}
          </Button>
        </div>
      </Card>

      {/* Confirmation Dialog */}
      <Dialog
        isOpen={isConfirmOpen}
        onClose={() => setIsConfirmOpen(false)}
        title={isUrdu ? 'کٹ اوور کی حتمی تصدیق' : 'Confirm Authoritative Ledger Cutover'}
      >
        <div className="space-y-4">
          <p className="text-sm text-gray-700">
            {isUrdu
              ? `کیا آپ واقعی ${yearMonth} کے لیجر اعدادوشمار کو آفیشل ماہانہ ریکارڈ میں منتقل کرنا چاہتے ہیں؟ اس عمل کے نتیجے میں ہیلتھ اسکور v1 دوبارہ خودکار طور پر کیلکولیٹ ہوگا۔`
              : `Are you sure you want to cut over ledger transactions for ${yearMonth} into the official Monthly Record? This will trigger an automatic health-score-v1 recalculation.`}
          </p>

          <div className="bg-amber-50 p-3 rounded border border-amber-200 text-xs text-amber-800">
            <p className="font-semibold mb-1">
              {isUrdu ? 'اہم انتباہ:' : 'Important Notice:'}
            </p>
            <p>
              {isUrdu
                ? 'موجودہ کیش فلو، آمدنی اور اخراجات لیجر کی اصل پوسٹنگز سے تبدیل ہو جائیں گے۔ اگر پہلے سے کوئی ریکارڈ موجود ہے تو اس کا آڈٹ ٹریل محفوظ رہے گا۔'
                : 'Ledger cash flow, revenue, and operating expense totals will replace existing summaries. Historical versions remain strictly preserved in the audit log.'}
            </p>
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-gray-200">
            <Button
              variant="secondary"
              onClick={() => setIsConfirmOpen(false)}
              disabled={submitting}
            >
              {isUrdu ? 'منسوخ کریں' : 'Cancel'}
            </Button>
            <Button
              variant="primary"
              onClick={handleApplyCutover}
              disabled={submitting}
              leftIcon={<CheckCircle2 className="w-4 h-4" />}
            >
              {submitting
                ? (isUrdu ? 'لاگو ہو رہا ہے...' : 'Applying...')
                : (isUrdu ? 'ہاں، کٹ اوور کریں' : 'Confirm Cutover')}
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
