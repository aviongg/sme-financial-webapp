'use client';

import React from 'react';
import { CategoryCostSummary } from '@/lib/api/projectActuals';
import { ProjectActualsTranslations } from '@/lib/i18n/translations/projectActuals';

interface Props {
  categories: CategoryCostSummary[];
  baseCurrency: string;
  hasApprovedBudget: boolean;
  copy: ProjectActualsTranslations;
}

export function BudgetVsActualCard({ categories, baseCurrency, hasApprovedBudget, copy }: Props) {
  const formatMoney = (val?: number | null) => {
    if (val === undefined || val === null) return '—';
    return `${baseCurrency} ${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  return (
    <div
      data-testid="budget-vs-actual-card"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <div>
          <h3 className="text-base font-bold text-foreground">{copy.tabs.budgetVsActual}</h3>
          <p className="text-xs text-muted-foreground">{copy.budgetVsActual.comparisonNote}</p>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs border-collapse">
          <thead>
            <tr className="border-b border-border bg-muted/30 text-muted-foreground font-semibold">
              <th className="py-2.5 px-3">{copy.budgetVsActual.category}</th>
              <th className="py-2.5 px-3 text-right">{copy.budgetVsActual.planned}</th>
              <th className="py-2.5 px-3 text-right">{copy.budgetVsActual.actualCash}</th>
              <th className="py-2.5 px-3 text-right">{copy.budgetVsActual.headroom}</th>
              <th className="py-2.5 px-3 text-right">{copy.budgetVsActual.recognizedCost}</th>
              <th className="py-2.5 px-3 text-right">{copy.budgetVsActual.nonCashEvidence}</th>
              <th className="py-2.5 px-3 text-center">{copy.budgetVsActual.status}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/60">
            {categories.map((c) => {
              const label = copy.categories[c.category] || c.category;
              const hasPlan = hasApprovedBudget && c.plannedCost > 0;
              const isOverBudget = c.cashHeadroom !== null && c.cashHeadroom < 0;

              return (
                <tr
                  key={c.category}
                  data-testid={`row-category-${c.category.toLowerCase()}`}
                  className="hover:bg-muted/20 transition-colors"
                >
                  <td className="py-2.5 px-3 font-semibold text-foreground">
                    {label}
                  </td>
                  <td
                    data-testid={`planned-${c.category.toLowerCase()}`}
                    className="py-2.5 px-3 text-right font-mono"
                  >
                    {formatMoney(c.plannedCost)}
                  </td>
                  <td
                    data-testid={`actual-cash-${c.category.toLowerCase()}`}
                    className="py-2.5 px-3 text-right font-mono font-bold text-foreground"
                  >
                    {formatMoney(c.actualCashOutflow)}
                  </td>
                  <td
                    data-testid={`headroom-${c.category.toLowerCase()}`}
                    className={`py-2.5 px-3 text-right font-mono font-semibold ${
                      c.cashHeadroom === null
                        ? 'text-muted-foreground'
                        : isOverBudget
                        ? 'text-rose-600 dark:text-rose-400'
                        : 'text-emerald-600 dark:text-emerald-400'
                    }`}
                  >
                    {formatMoney(c.cashHeadroom)}
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono text-muted-foreground">
                    {formatMoney(c.recognizedCost)}
                  </td>
                  <td className="py-2.5 px-3 text-right font-mono text-muted-foreground">
                    {formatMoney(c.nonCashEvidence)}
                  </td>
                  <td className="py-2.5 px-3 text-center">
                    {!hasPlan ? (
                      <span className="rounded bg-muted px-2 py-0.5 text-[10px] text-muted-foreground">
                        No Plan
                      </span>
                    ) : isOverBudget ? (
                      <span className="rounded bg-rose-500/10 px-2 py-0.5 text-[10px] font-semibold text-rose-600 dark:text-rose-400 border border-rose-500/20">
                        Over Budget
                      </span>
                    ) : (
                      <span className="rounded bg-emerald-500/10 px-2 py-0.5 text-[10px] font-semibold text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                        Within Headroom
                      </span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
