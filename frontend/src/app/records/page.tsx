"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import {
  CalendarCheck,
  Plus,
  ArrowLeft,
  CheckCircle2,
  Edit3,
} from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";

import { EmptyState } from "@/components/ui/EmptyState";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";
import { cn } from "@/lib/utils/cn";
import { formatCurrency } from "@/lib/utils/currency";
import type { MonthlyRecordResponse } from "@/types/financial";

export default function RecordsListPage() {
  const { t, direction } = useLanguage();
  const [records, setRecords] = useState<MonthlyRecordResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;
    mockApi
      .getMonthlyRecords()
      .then((data) => {
        if (!isCancelled) {
          setRecords(data);
          setIsLoading(false);
        }
      })
      .catch(() => {
        if (!isCancelled) setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, []);

  const formatMonth = (monthStr: string) => {
    const [year, month] = monthStr.split("-");
    const date = new Date(Number(year), Number(month) - 1, 1);
    return date.toLocaleDateString("en-US", { month: "long", year: "numeric" });
  };

  const isRTL = direction === "rtl";

  return (
    <AppShell
      title={t.nav.monthlyRecords}
      subtitle={t.records.historySubtitle}
      headerActions={
        <Link href="/records/new">
          <Button variant="primary" size="sm" leftIcon={<Plus />}>
            {t.dashboard.addMonthlyRecord}
          </Button>
        </Link>
      }
    >

      <Container width="dashboard" className="py-6 sm:py-8 space-y-6">
        {/* Navigation Breadcrumb */}
        <div className="flex items-center justify-between">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-[13px] font-medium text-[var(--color-brand-primary)] hover:underline focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] rounded-[var(--radius-sm)]"
          >
            <ArrowLeft className={cn("w-4 h-4", isRTL && "rotate-180")} />
            <span>{t.componentBreakdown.returnToDashboard}</span>
          </Link>
        </div>

        {isLoading ? (
          <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
            <Skeleton className="h-6 w-1/4" />
            <div className="space-y-3 pt-2">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          </Card>
        ) : records.length === 0 ? (
          <EmptyState
            icon={<CalendarCheck className="w-8 h-8" />}
            title={t.dashboard.emptyTitle}
            description={t.dashboard.emptyDescription}
            actionLabel={t.dashboard.emptyAction}
            actionHref="/records/new"
          />
        ) : (
          <Card elevation={0} padding="none" className="border-[var(--color-border-default)] overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-start border-collapse">
                <thead>
                  <tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-surface-subtle)]">
                    <th scope="col" className="py-3.5 px-4 text-start text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider">
                      {t.dashboard.monthCol}
                    </th>
                    <th scope="col" className="py-3.5 px-4 text-start text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider">
                      {t.dashboard.inflowCol}
                    </th>
                    <th scope="col" className="py-3.5 px-4 text-start text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider">
                      {t.dashboard.outflowCol}
                    </th>
                    <th scope="col" className="py-3.5 px-4 text-start text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider">
                      {t.dashboard.netCol}
                    </th>
                    <th scope="col" className="py-3.5 px-4 text-start text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider">
                      {t.dashboard.endingCol}
                    </th>
                    <th scope="col" className="py-3.5 px-4 text-start text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider">
                      {t.dashboard.statusCol}
                    </th>
                    <th scope="col" className="py-3.5 px-4 text-end text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider">
                      {t.records.actionsCol}
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--color-border-subtle)]">
                  {records.map((rec) => {
                    const netMovement = rec.cashInflow - rec.cashOutflow;
                    const isPositive = netMovement >= 0;

                    return (
                      <tr
                        key={rec.id}
                        className="hover:bg-[var(--color-surface-hover)] transition-colors group"
                      >
                        <td className="py-3.5 px-4 text-[13px] font-semibold text-[var(--color-text-primary)] font-heading">
                          <Link href={`/records/${rec.id}`} className="hover:text-[var(--color-brand-primary)]">
                            {formatMonth(rec.month)}
                          </Link>
                        </td>
                        <td className="py-3.5 px-4 text-[13px] text-[var(--color-text-primary)]">
                          {formatCurrency(rec.cashInflow)}
                        </td>
                        <td className="py-3.5 px-4 text-[13px] text-[var(--color-text-primary)]">
                          {formatCurrency(rec.cashOutflow)}
                        </td>
                        <td className="py-3.5 px-4 text-[13px] font-medium">
                          <span className={cn(isPositive ? "text-[var(--color-status-success)]" : "text-[var(--color-status-error)]")}>
                            {isPositive ? "+" : ""}{formatCurrency(netMovement)}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 text-[13px] font-semibold text-[var(--color-text-primary)]">
                          {formatCurrency(rec.cashBalanceEom)}
                        </td>
                        <td className="py-3.5 px-4">
                          <span className="inline-flex items-center gap-1 text-[11px] font-medium text-[var(--color-status-success)] bg-[var(--color-success-surface)] px-2 py-0.5 rounded-full border border-[var(--color-success-border)]">
                            <CheckCircle2 className="w-3 h-3" />
                            {t.dashboard.statusVerified}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 text-end">
                          <Link href={`/records/${rec.id}`}>
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-[12px] text-[var(--color-brand-primary)]"
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
          </Card>
        )}
      </Container>
    </AppShell>
  );
}
