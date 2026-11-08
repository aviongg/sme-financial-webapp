"use client";

import React, { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import type { SalesAssignment } from '@/lib/api/dev-a/commission';
import { Trash2 } from 'lucide-react';

interface CustomerAssignmentsViewProps {
  assignments: SalesAssignment[];
  loading: boolean;
  canManage: boolean;
  onTerminate: (salespersonId: string, assignmentId: string) => Promise<void>;
  locale: string;
}

export function CustomerAssignmentsView({
  assignments,
  loading,
  canManage,
  onTerminate,
  locale,
}: CustomerAssignmentsViewProps) {
  const copy = commissionCopy(locale);
  const [terminatingId, setTerminatingId] = useState<string | null>(null);

  const handleTerminate = async (salespersonId: string, assignmentId: string) => {
    if (!window.confirm(copy.assignment.unassignConfirm)) {
      return;
    }
    setTerminatingId(assignmentId);
    try {
      await onTerminate(salespersonId, assignmentId);
    } finally {
      setTerminatingId(null);
    }
  };

  if (loading) {
    return (
      <Card className="p-8 text-center text-slate-500 dark:text-slate-400">
        <div className="animate-pulse space-y-3">
          <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-1/3 mx-auto" />
          <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-1/2 mx-auto" />
        </div>
      </Card>
    );
  }

  if (assignments.length === 0) {
    return (
      <Card className="p-8 text-center text-slate-500 dark:text-slate-400">
        <p className="text-sm">{copy.assignment.empty}</p>
      </Card>
    );
  }

  return (
    <Card className="overflow-hidden p-0 border border-slate-200 dark:border-slate-800 shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left rtl:text-right">
          <thead className="bg-slate-50 dark:bg-slate-900/50 text-xs font-semibold text-slate-600 dark:text-slate-400 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800">
            <tr>
              <th scope="col" className="px-4 py-3">{copy.assignment.salespersonName}</th>
              <th scope="col" className="px-4 py-3">{copy.assignment.customerName}</th>
              <th scope="col" className="px-4 py-3">{copy.assignment.effectiveDates}</th>
              <th scope="col" className="px-4 py-3">{copy.salesperson.status}</th>
              <th scope="col" className="px-4 py-3">{copy.assignment.notes}</th>
              {canManage && <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.salesperson.actions}</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
            {assignments.map((assignment) => {
              const isActive = assignment.status === 'ACTIVE';
              return (
                <tr key={assignment.id} className="hover:bg-slate-50/60 dark:hover:bg-slate-800/40 transition-colors">
                  <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100">
                    {assignment.salespersonName}
                  </td>
                  <td className="px-4 py-3 font-medium text-indigo-600 dark:text-indigo-400">
                    {assignment.counterpartyName}
                  </td>
                  <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                    {assignment.effectiveStartDate}
                    {assignment.effectiveEndDate ? ` — ${assignment.effectiveEndDate}` : ' — Present'}
                  </td>
                  <td className="px-4 py-3">
                    {isActive ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
                        <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                        {copy.common.active}
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-400 border border-slate-200 dark:border-slate-700">
                        <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
                        {copy.common.inactive}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-slate-500 dark:text-slate-400 text-xs">
                    {assignment.notes || '—'}
                  </td>
                  {canManage && (
                    <td className="px-4 py-3 text-right rtl:text-left">
                      {isActive && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => handleTerminate(assignment.salespersonId, assignment.id)}
                          disabled={terminatingId === assignment.id}
                          className="text-red-500 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-950/30"
                          title={copy.assignment.unassign}
                        >
                          <Trash2 className="h-3.5 w-3.5 mr-1 rtl:ml-1" />
                          {copy.assignment.unassign}
                        </Button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
