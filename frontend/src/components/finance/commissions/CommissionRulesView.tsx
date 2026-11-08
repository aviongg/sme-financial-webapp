"use client";

import React from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import type { CommissionRule, CommissionRuleStatus } from '@/lib/api/dev-a/commission';
import { Plus, Archive, CheckCircle2, ShieldCheck } from 'lucide-react';

interface CommissionRulesViewProps {
  rules: CommissionRule[];
  loading: boolean;
  canManage: boolean;
  onCreateRule: () => void;
  onStatusChange: (rule: CommissionRule, status: CommissionRuleStatus) => Promise<void>;
  locale: string;
}

export function CommissionRulesView({
  rules,
  loading,
  canManage,
  onCreateRule,
  onStatusChange,
  locale,
}: CommissionRulesViewProps) {
  const copy = commissionCopy(locale);

  const getStatusBadge = (status: CommissionRuleStatus) => {
    switch (status) {
      case 'ACTIVE':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            {copy.common.active}
          </span>
        );
      case 'ARCHIVED':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-400 border border-slate-200 dark:border-slate-700">
            <Archive className="h-3 w-3" />
            {copy.common.archived}
          </span>
        );
    }
  };

  const formatRate = (rule: CommissionRule) => {
    if (rule.calculationType === 'PERCENTAGE') {
      return `${Number(rule.ratePercentage).toFixed(2)}%`;
    }
    return `PKR ${Number(rule.fixedAmount).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
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

  return (
    <Card className="overflow-hidden">
      <div className="p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50">
        <div>
          <h3 className="text-base font-semibold text-slate-900 dark:text-white">
            {copy.rules.createTitle}
          </h3>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            {copy.rules.createIntro}
          </p>
        </div>
        {canManage && (
          <Button onClick={onCreateRule} variant="primary" size="sm" className="gap-1.5 self-start sm:self-auto">
            <Plus className="h-4 w-4" />
            {copy.rules.createTitle}
          </Button>
        )}
      </div>

      {rules.length === 0 ? (
        <div className="p-8 text-center text-slate-500 dark:text-slate-400 text-sm">
          {copy.rules.empty}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs sm:text-sm">
            <thead className="bg-slate-50 dark:bg-slate-900/80 text-slate-600 dark:text-slate-400 font-medium border-b border-slate-200 dark:border-slate-800">
              <tr>
                <th className="py-3 px-4">{copy.rules.name}</th>
                <th className="py-3 px-4">{copy.rules.salesperson}</th>
                <th className="py-3 px-4">{copy.rules.calculationType}</th>
                <th className="py-3 px-4">{copy.rules.earningBasis}</th>
                <th className="py-3 px-4">{copy.rules.startDate}</th>
                <th className="py-3 px-4">{copy.rules.status}</th>
                {canManage && <th className="py-3 px-4 text-right">{copy.rules.actions}</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-slate-800 dark:text-slate-200">
              {rules.map((rule) => (
                <tr key={rule.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/50 transition-colors">
                  <td className="py-3 px-4 font-medium text-slate-900 dark:text-white">
                    {rule.ruleName || rule.name}
                    {rule.notes && (
                      <div className="text-xs text-slate-400 dark:text-slate-500 font-normal mt-0.5">
                        {rule.notes}
                      </div>
                    )}
                  </td>
                  <td className="py-3 px-4">
                    {rule.salespersonId ? (
                      <span className="font-medium text-indigo-600 dark:text-indigo-400">
                        {rule.salespersonId}
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 text-slate-500 dark:text-slate-400 font-medium">
                        <ShieldCheck className="h-3.5 w-3.5 text-indigo-500" />
                        {copy.rules.allSalespersons}
                      </span>
                    )}
                  </td>
                  <td className="py-3 px-4 font-semibold text-slate-900 dark:text-white">
                    {formatRate(rule)}
                  </td>
                  <td className="py-3 px-4 text-slate-600 dark:text-slate-400">
                    {copy.rules.collectionBased}
                  </td>
                  <td className="py-3 px-4 text-slate-500 dark:text-slate-400 whitespace-nowrap">
                    {rule.effectiveStartDate} {rule.effectiveEndDate ? `→ ${rule.effectiveEndDate}` : '→ Present'}
                  </td>
                  <td className="py-3 px-4">{getStatusBadge(rule.status)}</td>
                  {canManage && (
                    <td className="py-3 px-4 text-right">
                      {rule.status === 'ACTIVE' ? (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => onStatusChange(rule, 'ARCHIVED')}
                          className="text-xs text-slate-600 hover:text-slate-900"
                        >
                          {copy.rules.archive}
                        </Button>
                      ) : (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => onStatusChange(rule, 'ACTIVE')}
                          className="text-xs text-emerald-600 hover:text-emerald-700"
                        >
                          {copy.rules.activate}
                        </Button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
