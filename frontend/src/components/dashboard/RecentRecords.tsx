"use client";

import React from "react";
import Link from "next/link";
import { ArrowUpRight, ArrowDownRight, ChevronRight, ChevronLeft, CheckCircle2, Edit3 } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { FinancialValue } from "@/components/ui/FinancialValue";
import { useLanguage } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";
import type { MonthlyRecordResponse } from "@/types/financial";

export interface RecentRecordsProps {
  records: MonthlyRecordResponse[];
  className?: string;
}

/**
 * RecentRecords
 * Priority 6: "What happened recently?"
 * Clean, tabular summary of recent monthly financial vitals.
 */
export function RecentRecords({ records, className }: RecentRecordsProps) {
  const { t, direction } = useLanguage();
  const isRTL = direction === "rtl";

  // Take most recent 4 records, sorted descending
  const recent = [...records]
    .sort((a, b) => b.month.localeCompare(a.month))
    .slice(0, 4);

  const formatMonth = (monthStr: string) => {
    const [year, month] = monthStr.split("-");
    const date = new Date(Number(year), Number(month) - 1, 1);
    return date.toLocaleDateString("en-US", { month: "long", year: "numeric" });
  };

  return (
    <Card elevation={0} padding="lg" className={cn("border-[var(--color-border-default)] text-start", className)}>
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-4 border-b border-[var(--color-border-subtle)]">
        <div>
          <h3 className="text-[16px] sm:text-[18px] font-semibold text-[var(--color-text-primary)] font-heading">
            {t.dashboard.recentRecordsTitle}
          </h3>
          <p className="text-[13px] text-[var(--color-text-muted)] mt-0.5">
            {t.dashboard.recentRecordsSubtitle}
          </p>
        </div>
        <Link href="/records/new" className="self-start sm:self-auto">
          <Button
            variant="ghost"
            size="sm"
            className="text-[var(--color-brand-primary)] hover:bg-[var(--color-brand-surface)]"
            rightIcon={
              isRTL ? <ChevronLeft className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />
            }
          >
            {t.dashboard.addMonthlyRecord}
          </Button>
        </Link>
      </div>

      {/* Desktop Table */}
      <div className="hidden md:block overflow-x-auto">
        <table className="w-full text-start text-[13px] border-collapse">
          <thead>
            <tr className="border-b border-[var(--color-border-subtle)] text-[11px] font-bold uppercase tracking-wider text-[var(--color-text-muted)]">
              <th className="py-3 px-2 text-start">{t.dashboard.monthCol}</th>
              <th className="py-3 px-2 text-end">{t.dashboard.inflowCol}</th>
              <th className="py-3 px-2 text-end">{t.dashboard.outflowCol}</th>
              <th className="py-3 px-2 text-end">{t.dashboard.netCol}</th>
              <th className="py-3 px-2 text-end">{t.dashboard.endingCol}</th>
              <th className="py-3 px-2 text-center">{t.dashboard.statusCol}</th>
              <th className="py-3 px-2 text-end">{t.common.edit}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--color-border-subtle)]">
            {recent.map((rec) => {
              const net = rec.cashInflow - rec.cashOutflow;
              const isSurplus = net >= 0;
              const recordId = rec.id || rec.month;
              return (
                <tr
                  key={recordId}
                  className="hover:bg-[var(--color-surface-hover)] transition-colors"
                >
                  <td className="py-3.5 px-2 font-semibold text-[var(--color-text-primary)] whitespace-nowrap">
                    {formatMonth(rec.month)}
                  </td>
                  <td className="py-3.5 px-2 text-end tabular-nums">
                    <FinancialValue value={rec.cashInflow} size="sm" />
                  </td>
                  <td className="py-3.5 px-2 text-end tabular-nums">
                    <FinancialValue value={rec.cashOutflow} size="sm" />
                  </td>
                  <td className="py-3.5 px-2 text-end tabular-nums whitespace-nowrap">
                    <div className="inline-flex items-center gap-1 justify-end">
                      {isSurplus ? (
                        <ArrowUpRight className="w-3.5 h-3.5 text-[var(--color-health-strong)] shrink-0" />
                      ) : (
                        <ArrowDownRight className="w-3.5 h-3.5 text-[var(--color-health-risk)] shrink-0" />
                      )}
                      <FinancialValue value={net} size="sm" colorIntent="semantic" showSign />
                    </div>
                  </td>
                  <td className="py-3.5 px-2 text-end tabular-nums font-semibold">
                    <FinancialValue value={rec.cashBalanceEom} size="sm" />
                  </td>
                  <td className="py-3.5 px-2 text-center">
                    <span className="inline-flex items-center gap-1 text-[11px] font-medium text-[var(--color-health-strong)] bg-[var(--color-health-strong-bg)] border border-[var(--color-health-strong-border)] px-2 py-0.5 rounded-[var(--radius-sm)]">
                      <CheckCircle2 className="w-3 h-3 shrink-0" />
                      {t.dashboard.statusVerified}
                    </span>
                  </td>
                  <td className="py-3.5 px-2 text-end">
                    <Link href={`/records/${recordId}`}>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-8 px-2.5 text-[12px] text-[var(--color-brand-primary)] hover:bg-[var(--color-brand-surface)]"
                        leftIcon={<Edit3 className="w-3.5 h-3.5" />}
                      >
                        {t.common.edit}
                      </Button>
                    </Link>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Mobile Stacked List */}
      <div className="block md:hidden divide-y divide-[var(--color-border-subtle)]">
        {recent.map((rec) => {
          const net = rec.cashInflow - rec.cashOutflow;
          const isSurplus = net >= 0;
          const recordId = rec.id || rec.month;
          return (
            <div key={recordId} className="py-3.5 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-[14px] font-semibold text-[var(--color-text-primary)]">
                  {formatMonth(rec.month)}
                </span>
                <div className="flex items-center gap-2">
                  <span className="inline-flex items-center gap-1 text-[11px] font-medium text-[var(--color-health-strong)] bg-[var(--color-health-strong-bg)] px-2 py-0.5 rounded-[var(--radius-sm)]">
                    <CheckCircle2 className="w-3 h-3 shrink-0" />
                    {t.dashboard.statusVerified}
                  </span>
                  <Link href={`/records/${recordId}`}>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-7 px-2 text-[11px] text-[var(--color-brand-primary)]"
                      leftIcon={<Edit3 className="w-3 h-3" />}
                    >
                      {t.common.edit}
                    </Button>
                  </Link>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2 text-[12px]">
                <div>
                  <span className="text-[var(--color-text-muted)] block text-[11px]">
                    {t.dashboard.inflowCol}
                  </span>
                  <FinancialValue value={rec.cashInflow} size="sm" />
                </div>
                <div>
                  <span className="text-[var(--color-text-muted)] block text-[11px]">
                    {t.dashboard.outflowCol}
                  </span>
                  <FinancialValue value={rec.cashOutflow} size="sm" />
                </div>
                <div>
                  <span className="text-[var(--color-text-muted)] block text-[11px]">
                    {t.dashboard.netCol}
                  </span>
                  <div className="inline-flex items-center gap-1">
                    {isSurplus ? (
                      <ArrowUpRight className="w-3.5 h-3.5 text-[var(--color-health-strong)] shrink-0" />
                    ) : (
                      <ArrowDownRight className="w-3.5 h-3.5 text-[var(--color-health-risk)] shrink-0" />
                    )}
                    <FinancialValue value={net} size="sm" colorIntent="semantic" showSign />
                  </div>
                </div>

                <div>
                  <span className="text-[var(--color-text-muted)] block text-[11px]">
                    {t.dashboard.endingCol}
                  </span>
                  <FinancialValue value={rec.cashBalanceEom} size="sm" className="font-semibold" />
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}
