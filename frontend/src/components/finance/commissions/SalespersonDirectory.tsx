"use client";

import React from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import type { Salesperson, SalespersonStatus } from '@/lib/api/dev-a/commission';
import { UserCheck, UserX, AlertCircle, UserPlus } from 'lucide-react';

interface SalespersonDirectoryProps {
  salespersons: Salesperson[];
  loading: boolean;
  canManage: boolean;
  onAssignCustomer: (salesperson: Salesperson) => void;
  onStatusChange: (salesperson: Salesperson, status: SalespersonStatus) => Promise<void>;
  locale: string;
}

export function SalespersonDirectory({
  salespersons,
  loading,
  canManage,
  onAssignCustomer,
  onStatusChange,
  locale,
}: SalespersonDirectoryProps) {
  const copy = commissionCopy(locale);

  const getStatusBadge = (status: SalespersonStatus) => {
    switch (status) {
      case 'ACTIVE':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            {copy.common.active}
          </span>
        );
      case 'INACTIVE':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-400 border border-slate-200 dark:border-slate-700">
            <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
            {copy.common.inactive}
          </span>
        );
      case 'SUSPENDED':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-950/40 dark:text-amber-400 border border-amber-200 dark:border-amber-800">
            <AlertCircle className="h-3 w-3" />
            {copy.common.suspended}
          </span>
        );
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

  if (salespersons.length === 0) {
    return (
      <Card className="p-8 text-center text-slate-500 dark:text-slate-400">
        <p className="text-sm">{copy.salesperson.empty}</p>
      </Card>
    );
  }

  return (
    <Card className="overflow-hidden p-0 border border-slate-200 dark:border-slate-800 shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left rtl:text-right">
          <thead className="bg-slate-50 dark:bg-slate-900/50 text-xs font-semibold text-slate-600 dark:text-slate-400 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800">
            <tr>
              <th scope="col" className="px-4 py-3">{copy.salesperson.displayName}</th>
              <th scope="col" className="px-4 py-3">{copy.salesperson.email}</th>
              <th scope="col" className="px-4 py-3">{copy.salesperson.phone}</th>
              <th scope="col" className="px-4 py-3">{copy.salesperson.status}</th>
              <th scope="col" className="px-4 py-3 text-center">{copy.salesperson.activeAssignments}</th>
              {canManage && <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.salesperson.actions}</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
            {salespersons.map((person) => (
              <tr key={person.id} className="hover:bg-slate-50/60 dark:hover:bg-slate-800/40 transition-colors">
                <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100">
                  {person.displayName}
                  {person.notes && (
                    <span className="block text-xs font-normal text-slate-400 dark:text-slate-500">
                      {person.notes}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-slate-600 dark:text-slate-400">{person.email}</td>
                <td className="px-4 py-3 text-slate-600 dark:text-slate-400">{person.phone || '—'}</td>
                <td className="px-4 py-3">{getStatusBadge(person.status)}</td>
                <td className="px-4 py-3 text-center">
                  <span className="inline-flex items-center justify-center min-w-[1.5rem] px-2 py-0.5 rounded-full text-xs font-semibold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
                    {person.activeAssignmentsCount}
                  </span>
                </td>
                {canManage && (
                  <td className="px-4 py-3 text-right rtl:text-left space-x-2 rtl:space-x-reverse whitespace-nowrap">
                    {person.status === 'ACTIVE' && (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => onAssignCustomer(person)}
                        title={copy.salesperson.assignCustomer}
                      >
                        <UserPlus className="h-3.5 w-3.5 mr-1 rtl:ml-1" />
                        {copy.salesperson.assignCustomer}
                      </Button>
                    )}
                    {person.status === 'ACTIVE' ? (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => onStatusChange(person, 'INACTIVE')}
                        className="text-slate-500 hover:text-slate-700"
                        title={copy.salesperson.deactivate}
                      >
                        {copy.salesperson.deactivate}
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => onStatusChange(person, 'ACTIVE')}
                        className="text-emerald-600 hover:text-emerald-700 dark:text-emerald-400"
                        title={copy.salesperson.activate}
                      >
                        {copy.salesperson.activate}
                      </Button>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
