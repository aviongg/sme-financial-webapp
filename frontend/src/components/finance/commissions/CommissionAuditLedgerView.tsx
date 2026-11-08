"use client";

import React, { useState, useEffect, useCallback } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import {
  getCommissionAuditLedger,
  type CommissionAuditEvent,
  type Salesperson,
} from '@/lib/api/dev-a/commission';
import {
  History,
  Filter,
  RotateCcw,
  CheckCircle2,
  DollarSign,
  CreditCard,
  Banknote,
  RotateCcw as ReversalIcon,
  Sparkles,
} from 'lucide-react';

interface CommissionAuditLedgerViewProps {
  salespersons: Salesperson[];
  locale: string;
}

export function CommissionAuditLedgerView({ salespersons, locale }: CommissionAuditLedgerViewProps) {
  const copy = commissionCopy(locale);

  const [selectedSalespersonId, setSelectedSalespersonId] = useState<string>('');
  const [eventTypeFilter, setEventTypeFilter] = useState<string>('');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');

  const [events, setEvents] = useState<CommissionAuditEvent[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAuditEvents = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getCommissionAuditLedger({
        salespersonId: selectedSalespersonId || undefined,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      });

      // Filter locally by event type if selected
      const filtered = eventTypeFilter
        ? data.filter((e) => e.eventType === eventTypeFilter)
        : data;

      setEvents(filtered);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : copy.common.error);
    } finally {
      setLoading(false);
    }
  }, [selectedSalespersonId, startDate, endDate, eventTypeFilter, copy.common.error]);

  useEffect(() => {
    fetchAuditEvents();
  }, [fetchAuditEvents]);

  const handleReset = () => {
    setSelectedSalespersonId('');
    setEventTypeFilter('');
    setStartDate('');
    setEndDate('');
  };

  const formatMoney = (val?: number) => {
    if (val === undefined || val === null) return '0.00';
    return Number(val).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  const getEventBadge = (type: CommissionAuditEvent['eventType']) => {
    switch (type) {
      case 'EARNING_RECOGNIZED':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300 border border-blue-200 dark:border-blue-800">
            <Sparkles className="h-3 w-3" />
            {type}
          </span>
        );
      case 'EARNING_APPROVED':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700 dark:bg-indigo-950/40 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-800">
            <CheckCircle2 className="h-3 w-3" />
            {type}
          </span>
        );
      case 'ADVANCE_ISSUED':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300 border border-amber-200 dark:border-amber-800">
            <CreditCard className="h-3 w-3" />
            {type}
          </span>
        );
      case 'ADVANCE_APPLIED':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-purple-50 text-purple-700 dark:bg-purple-950/40 dark:text-purple-300 border border-purple-200 dark:border-purple-800">
            <DollarSign className="h-3 w-3" />
            {type}
          </span>
        );
      case 'PAYOUT_SETTLED':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
            <Banknote className="h-3 w-3" />
            {type}
          </span>
        );
      case 'PAYOUT_REVERSED':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300 border border-rose-200 dark:border-rose-800">
            <ReversalIcon className="h-3 w-3" />
            {type}
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300">
            {type}
          </span>
        );
    }
  };

  return (
    <div className="space-y-6" data-testid="commission-audit-ledger-view">
      {/* Filters Card */}
      <Card className="p-4 border border-slate-200 dark:border-slate-800 shadow-sm">
        <div className="flex flex-col md:flex-row items-end gap-3">
          <div className="w-full md:w-1/4">
            <label className="block text-xs font-semibold text-slate-600 dark:text-slate-400 mb-1">
              {copy.audit.salesperson}
            </label>
            <select
              value={selectedSalespersonId}
              onChange={(e) => setSelectedSalespersonId(e.target.value)}
              className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              data-testid="audit-salesperson-select"
            >
              <option value="">{copy.analytics.allSalespersons}</option>
              {salespersons.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.displayName}
                </option>
              ))}
            </select>
          </div>

          <div className="w-full md:w-1/4">
            <label className="block text-xs font-semibold text-slate-600 dark:text-slate-400 mb-1">
              {copy.audit.eventType}
            </label>
            <select
              value={eventTypeFilter}
              onChange={(e) => setEventTypeFilter(e.target.value)}
              className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              data-testid="audit-event-type-select"
            >
              <option value="">All Event Types</option>
              <option value="EARNING_RECOGNIZED">EARNING_RECOGNIZED</option>
              <option value="EARNING_APPROVED">EARNING_APPROVED</option>
              <option value="ADVANCE_ISSUED">ADVANCE_ISSUED</option>
              <option value="ADVANCE_APPLIED">ADVANCE_APPLIED</option>
              <option value="PAYOUT_SETTLED">PAYOUT_SETTLED</option>
              <option value="PAYOUT_REVERSED">PAYOUT_REVERSED</option>
            </select>
          </div>

          <div className="w-full md:w-1/6">
            <label className="block text-xs font-semibold text-slate-600 dark:text-slate-400 mb-1">
              {copy.analytics.startDate}
            </label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div className="w-full md:w-1/6">
            <label className="block text-xs font-semibold text-slate-600 dark:text-slate-400 mb-1">
              {copy.analytics.endDate}
            </label>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div className="flex items-center gap-2 w-full md:w-auto">
            <Button
              variant="primary"
              onClick={fetchAuditEvents}
              disabled={loading}
              className="flex-1 md:flex-initial"
              data-testid="audit-filter-btn"
            >
              <Filter className="h-4 w-4 mr-1.5 rtl:ml-1.5" />
              {copy.analytics.filter}
            </Button>
            <Button
              variant="secondary"
              onClick={handleReset}
              disabled={loading || (!selectedSalespersonId && !eventTypeFilter && !startDate && !endDate)}
              className="flex-1 md:flex-initial"
              data-testid="audit-reset-btn"
            >
              <RotateCcw className="h-4 w-4 mr-1.5 rtl:ml-1.5" />
              {copy.analytics.reset}
            </Button>
          </div>
        </div>
      </Card>

      {error && (
        <div
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400"
        >
          {error}
        </div>
      )}

      {/* Events Table */}
      {events.length === 0 ? (
        <Card className="p-8 text-center text-slate-500 dark:text-slate-400">
          <p className="text-sm">{copy.audit.empty}</p>
        </Card>
      ) : (
        <Card className="overflow-hidden p-0 border border-slate-200 dark:border-slate-800 shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left rtl:text-right" data-testid="audit-table">
              <thead className="bg-slate-50 dark:bg-slate-900/50 text-slate-600 dark:text-slate-400 uppercase font-semibold border-b border-slate-200 dark:border-slate-800">
                <tr>
                  <th scope="col" className="px-4 py-3">{copy.audit.timestamp}</th>
                  <th scope="col" className="px-4 py-3">{copy.audit.eventType}</th>
                  <th scope="col" className="px-4 py-3">{copy.audit.salesperson}</th>
                  <th scope="col" className="px-4 py-3">{copy.audit.customer}</th>
                  <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.audit.amount}</th>
                  <th scope="col" className="px-4 py-3">{copy.audit.status}</th>
                  <th scope="col" className="px-4 py-3">{copy.audit.linkedTx}</th>
                  <th scope="col" className="px-4 py-3">{copy.audit.notes}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {events.map((ev) => (
                  <tr
                    key={ev.eventId}
                    className="hover:bg-slate-50/60 dark:hover:bg-slate-800/40 transition-colors"
                    data-testid={`audit-row-${ev.eventId}`}
                  >
                    <td className="px-4 py-3 font-mono text-slate-600 dark:text-slate-400 whitespace-nowrap">
                      {new Date(ev.timestamp).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {getEventBadge(ev.eventType)}
                    </td>
                    <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100 whitespace-nowrap">
                      {ev.salespersonName}
                    </td>
                    <td className="px-4 py-3 text-slate-700 dark:text-slate-300 whitespace-nowrap">
                      {ev.counterpartyName || '—'}
                    </td>
                    <td className="px-4 py-3 text-right rtl:text-left font-bold text-slate-900 dark:text-slate-100 whitespace-nowrap">
                      {ev.currency} {formatMoney(ev.amount)}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
                        {ev.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-mono text-[11px] text-slate-500 dark:text-slate-400 whitespace-nowrap">
                      {ev.financialTransactionId || ev.referenceId || '—'}
                    </td>
                    <td className="px-4 py-3 text-slate-500 dark:text-slate-400 max-w-xs truncate">
                      {ev.notes || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
