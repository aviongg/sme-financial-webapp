"use client";

import React, { useState, useEffect } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import {
  getSalespersonStatement,
  getMyStatement,
  downloadSalespersonStatementCsv,
  downloadMyStatementCsv,
  type SalespersonStatement,
  type Salesperson,
} from '@/lib/api/dev-a/commission';
import {
  FileText,
  Download,
  Printer,
  Calendar,
  Users,
  Building,
  CheckCircle2,
  Clock,
  AlertCircle,
} from 'lucide-react';

interface SalespersonStatementViewProps {
  salespersons?: Salesperson[];
  initialSalespersonId?: string;
  isSelfPortal?: boolean;
  locale: string;
}

export function SalespersonStatementView({
  salespersons = [],
  initialSalespersonId,
  isSelfPortal = false,
  locale,
}: SalespersonStatementViewProps) {
  const copy = commissionCopy(locale);

  const [selectedSalespersonId, setSelectedSalespersonId] = useState<string>(
    initialSalespersonId || (salespersons.length > 0 ? salespersons[0].id : '')
  );

  // Default dates: current month or last 30 days
  const todayStr = new Date().toISOString().slice(0, 10);
  const thirtyDaysAgoStr = new Date(Date.now() - 30 * 24 * 3600 * 1000).toISOString().slice(0, 10);

  const [startDate, setStartDate] = useState<string>(thirtyDaysAgoStr);
  const [endDate, setEndDate] = useState<string>(todayStr);

  const [statement, setStatement] = useState<SalespersonStatement | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [downloading, setDownloading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const fetchStatement = async () => {
    if (!isSelfPortal && !selectedSalespersonId) return;
    setLoading(true);
    setError(null);
    try {
      const params = {
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      };
      const data = isSelfPortal
        ? await getMyStatement(params)
        : await getSalespersonStatement(selectedSalespersonId, params);
      setStatement(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : copy.common.error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isSelfPortal || selectedSalespersonId) {
      fetchStatement();
    }
  }, [selectedSalespersonId, isSelfPortal]);

  const handleExportCsv = async () => {
    if (!isSelfPortal && !selectedSalespersonId) return;
    setDownloading(true);
    try {
      if (isSelfPortal) {
        await downloadMyStatementCsv(startDate || undefined, endDate || undefined);
      } else {
        await downloadSalespersonStatementCsv(selectedSalespersonId, startDate || undefined, endDate || undefined);
      }
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : copy.common.error);
    } finally {
      setDownloading(false);
    }
  };

  const handlePrint = () => {
    window.print();
  };

  const formatMoney = (val?: number) => {
    if (val === undefined || val === null) return '0.00';
    return Number(val).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  return (
    <div className="space-y-6" data-testid="salesperson-statement-view">
      {/* Controls / Filter Bar (Hidden on print) */}
      <Card className="p-4 border border-slate-200 dark:border-slate-800 shadow-sm print:hidden">
        <div className="flex flex-col lg:flex-row items-end justify-between gap-4">
          <div className="flex flex-col sm:flex-row items-end gap-3 w-full lg:w-auto">
            {!isSelfPortal && (
              <div className="w-full sm:w-64">
                <label className="block text-xs font-semibold text-slate-600 dark:text-slate-400 mb-1">
                  {copy.statements.selectSalesperson}
                </label>
                <select
                  value={selectedSalespersonId}
                  onChange={(e) => setSelectedSalespersonId(e.target.value)}
                  className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  data-testid="statement-salesperson-select"
                >
                  <option value="">{copy.statements.selectSalesperson}…</option>
                  {salespersons.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.displayName}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="w-full sm:w-40">
              <label className="block text-xs font-semibold text-slate-600 dark:text-slate-400 mb-1">
                {copy.analytics.startDate}
              </label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                data-testid="statement-start-date"
              />
            </div>

            <div className="w-full sm:w-40">
              <label className="block text-xs font-semibold text-slate-600 dark:text-slate-400 mb-1">
                {copy.analytics.endDate}
              </label>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                data-testid="statement-end-date"
              />
            </div>

            <Button
              variant="primary"
              onClick={fetchStatement}
              disabled={loading || (!isSelfPortal && !selectedSalespersonId)}
              data-testid="statement-generate-btn"
            >
              {copy.statements.generate}
            </Button>
          </div>

          {statement && (
            <div className="flex items-center gap-2 w-full lg:w-auto justify-end">
              <Button
                variant="secondary"
                onClick={handleExportCsv}
                disabled={downloading}
                data-testid="statement-export-csv-btn"
              >
                <Download className="h-4 w-4 mr-1.5 rtl:ml-1.5" />
                {downloading ? copy.statements.downloading : copy.statements.exportCsv}
              </Button>
              <Button
                variant="secondary"
                onClick={handlePrint}
                data-testid="statement-print-btn"
              >
                <Printer className="h-4 w-4 mr-1.5 rtl:ml-1.5" />
                {copy.statements.print}
              </Button>
            </div>
          )}
        </div>
      </Card>

      {error && (
        <div
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400 print:hidden"
        >
          {error}
        </div>
      )}

      {/* Statement Document View */}
      {statement ? (
        <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-8 shadow-sm space-y-8 print:p-0 print:border-none print:shadow-none" data-testid="statement-content">
          {/* Statement Header */}
          <div className="flex flex-col sm:flex-row justify-between items-start pb-6 border-b border-slate-200 dark:border-slate-800 gap-4">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Building className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
                <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">
                  {statement.businessName}
                </h2>
              </div>
              <p className="text-sm font-semibold text-slate-700 dark:text-slate-300">
                {copy.statements.title}
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                {copy.statements.period}: <span className="font-medium text-slate-700 dark:text-slate-300">{statement.startDate}</span> — <span className="font-medium text-slate-700 dark:text-slate-300">{statement.endDate}</span>
              </p>
            </div>

            <div className="sm:text-right rtl:sm:text-left">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 block mb-1">
                {copy.analytics.salesperson}
              </span>
              <p className="text-base font-bold text-slate-900 dark:text-slate-100">
                {statement.salespersonName}
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {statement.salespersonEmail} {statement.salespersonPhone ? `• ${statement.salespersonPhone}` : ''}
              </p>
              <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
                {copy.audit.timestamp}: {new Date(statement.generatedAt).toLocaleString()}
              </p>
            </div>
          </div>

          {/* Statement Position & Flow Cards */}
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 mb-3">
              {copy.statements.summaryTitle}
            </h3>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              <div className="p-3 bg-slate-50 dark:bg-slate-800/60 rounded-md border border-slate-200 dark:border-slate-700">
                <span className="text-xs text-slate-500 dark:text-slate-400 block">
                  {copy.statements.openingPosition}
                </span>
                <p className="text-base font-bold text-slate-900 dark:text-slate-100 mt-1">
                  {statement.currency} {formatMoney(statement.openingSettlementPosition)}
                </p>
              </div>

              <div className="p-3 bg-slate-50 dark:bg-slate-800/60 rounded-md border border-slate-200 dark:border-slate-700">
                <span className="text-xs text-slate-500 dark:text-slate-400 block">
                  {copy.statements.periodApproved}
                </span>
                <p className="text-base font-bold text-indigo-600 dark:text-indigo-400 mt-1">
                  + {statement.currency} {formatMoney(statement.periodApproved)}
                </p>
              </div>

              <div className="p-3 bg-slate-50 dark:bg-slate-800/60 rounded-md border border-slate-200 dark:border-slate-700">
                <span className="text-xs text-slate-500 dark:text-slate-400 block">
                  {copy.statements.periodTotalSettled}
                </span>
                <p className="text-base font-bold text-emerald-600 dark:text-emerald-400 mt-1">
                  - {statement.currency} {formatMoney(statement.periodTotalSettled)}
                </p>
              </div>

              <div className="p-3 bg-indigo-50 dark:bg-indigo-950/40 rounded-md border border-indigo-200 dark:border-indigo-800">
                <span className="text-xs font-semibold text-indigo-700 dark:text-indigo-300 block">
                  {copy.statements.closingPosition}
                </span>
                <p className="text-lg font-extrabold text-indigo-900 dark:text-indigo-100 mt-1" data-testid="statement-closing-position">
                  {statement.currency} {formatMoney(statement.closingSettlementPosition)}
                </p>
              </div>
            </div>

            {/* Additional flow details */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mt-3 text-xs text-slate-600 dark:text-slate-400">
              <div>
                <span className="font-medium">{copy.statements.periodEarned}:</span> {statement.currency} {formatMoney(statement.periodEarned)}
              </div>
              <div>
                <span className="font-medium">{copy.statements.periodAdvancesIssued}:</span> {statement.currency} {formatMoney(statement.periodAdvancesIssued)}
              </div>
              <div>
                <span className="font-medium">{copy.statements.periodAdvancesApplied}:</span> {statement.currency} {formatMoney(statement.periodAdvancesApplied)}
              </div>
              <div>
                <span className="font-medium">{copy.analytics.advancesUnapplied}:</span> {statement.currency} {formatMoney(statement.unappliedAdvanceBalance)}
              </div>
            </div>
          </div>

          {/* Assigned Customers Section */}
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              {copy.statements.assignedCustomers}
            </h3>
            {statement.assignedCustomers.length === 0 ? (
              <p className="text-xs text-slate-500 dark:text-slate-400 italic">
                {copy.statements.emptyCustomers}
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-xs text-left rtl:text-right border border-slate-200 dark:border-slate-800 rounded">
                  <thead className="bg-slate-50 dark:bg-slate-800/80 uppercase font-semibold text-slate-600 dark:text-slate-400">
                    <tr>
                      <th className="px-3 py-2">{copy.assignment.customerName}</th>
                      <th className="px-3 py-2">{copy.assignment.effectiveDates}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                    {statement.assignedCustomers.map((c) => (
                      <tr key={c.counterpartyId}>
                        <td className="px-3 py-2 font-medium text-slate-900 dark:text-slate-100">
                          {c.counterpartyName}
                        </td>
                        <td className="px-3 py-2 text-slate-600 dark:text-slate-400">
                          {c.effectiveStartDate} {c.effectiveEndDate ? `— ${c.effectiveEndDate}` : '— Present'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Commission Earnings Section */}
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              {copy.statements.earningsTitle}
            </h3>
            {statement.earnings.length === 0 ? (
              <p className="text-xs text-slate-500 dark:text-slate-400 italic">
                {copy.statements.emptyEarnings}
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-xs text-left rtl:text-right border border-slate-200 dark:border-slate-800 rounded" data-testid="statement-earnings-table">
                  <thead className="bg-slate-50 dark:bg-slate-800/80 uppercase font-semibold text-slate-600 dark:text-slate-400">
                    <tr>
                      <th className="px-3 py-2">{copy.statements.date}</th>
                      <th className="px-3 py-2">{copy.statements.customer}</th>
                      <th className="px-3 py-2 text-right rtl:text-left">{copy.statements.collectionAmount}</th>
                      <th className="px-3 py-2">{copy.statements.rate}</th>
                      <th className="px-3 py-2 text-right rtl:text-left">{copy.statements.earned}</th>
                      <th className="px-3 py-2 text-right rtl:text-left">{copy.statements.approved}</th>
                      <th className="px-3 py-2">{copy.statements.status}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                    {statement.earnings.map((e) => (
                      <tr key={e.earningId} data-testid={`statement-earning-${e.earningId}`}>
                        <td className="px-3 py-2 font-medium text-slate-900 dark:text-slate-100">{e.date}</td>
                        <td className="px-3 py-2 text-slate-900 dark:text-slate-100">{e.counterpartyName}</td>
                        <td className="px-3 py-2 text-right rtl:text-left">{statement.currency} {formatMoney(e.collectionAmount)}</td>
                        <td className="px-3 py-2 text-slate-600 dark:text-slate-400">{e.ruleDescription}</td>
                        <td className="px-3 py-2 text-right rtl:text-left font-medium">{statement.currency} {formatMoney(e.earnedAmount)}</td>
                        <td className="px-3 py-2 text-right rtl:text-left font-semibold text-indigo-600 dark:text-indigo-400">
                          {statement.currency} {formatMoney(e.approvedAmount)}
                        </td>
                        <td className="px-3 py-2">
                          <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
                            {e.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Settlements, Advances & Reversals Section */}
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              {copy.statements.settlementsTitle}
            </h3>
            {statement.settlements.length === 0 ? (
              <p className="text-xs text-slate-500 dark:text-slate-400 italic">
                {copy.statements.emptySettlements}
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-xs text-left rtl:text-right border border-slate-200 dark:border-slate-800 rounded" data-testid="statement-settlements-table">
                  <thead className="bg-slate-50 dark:bg-slate-800/80 uppercase font-semibold text-slate-600 dark:text-slate-400">
                    <tr>
                      <th className="px-3 py-2">{copy.statements.date}</th>
                      <th className="px-3 py-2">{copy.statements.type}</th>
                      <th className="px-3 py-2 text-right rtl:text-left">{copy.statements.amount}</th>
                      <th className="px-3 py-2">{copy.statements.status}</th>
                      <th className="px-3 py-2">{copy.statements.reference}</th>
                      <th className="px-3 py-2">{copy.statements.notes}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                    {statement.settlements.map((s) => (
                      <tr key={s.settlementId} data-testid={`statement-settlement-${s.settlementId}`}>
                        <td className="px-3 py-2 font-medium text-slate-900 dark:text-slate-100">{s.date}</td>
                        <td className="px-3 py-2 font-semibold text-slate-900 dark:text-slate-100">{s.settlementType}</td>
                        <td className="px-3 py-2 text-right rtl:text-left font-bold text-slate-900 dark:text-slate-100">
                          {s.currency} {formatMoney(s.amount)}
                        </td>
                        <td className="px-3 py-2">
                          <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
                            {s.status}
                          </span>
                        </td>
                        <td className="px-3 py-2 text-slate-500 dark:text-slate-400 font-mono text-[11px]">
                          {s.linkedTransactionId || '—'}
                        </td>
                        <td className="px-3 py-2 text-slate-500 dark:text-slate-400">{s.notes || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Provenance Disclaimer Footer */}
          <div className="pt-6 border-t border-slate-200 dark:border-slate-800 text-center">
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {statement.provenanceNotes || copy.statements.provenanceDisclaimer}
            </p>
          </div>
        </div>
      ) : (
        <Card className="p-8 text-center text-slate-500 dark:text-slate-400">
          <p className="text-sm">
            {isSelfPortal ? copy.statements.intro : copy.statements.selectSalesperson}
          </p>
        </Card>
      )}
    </div>
  );
}
