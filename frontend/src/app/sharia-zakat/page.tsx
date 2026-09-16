"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import {
  ShieldCheck,
  AlertTriangle,
  Info,
  ArrowLeft,
  CheckCircle2,
  Wallet,
  Package,
  ArrowDownRight,
  TrendingDown,
} from "lucide-react";

import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";
import { cn } from "@/lib/utils/cn";
import { formatCurrency } from "@/lib/utils/currency";
import type { ZakatData } from "@/types/financial";

export default function ShariaZakatPage() {
  const { t, direction } = useLanguage();
  const [data, setData] = useState<ZakatData | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;
    mockApi
      .getZakatData()
      .then((res) => {
        if (!isCancelled) {
          setData(res);
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

  const isRTL = direction === "rtl";

  return (
    <AppShell
      title={t.zakat.title}
      subtitle={t.zakat.subtitle}
      headerActions={
        <Link href="/">
          <Button
            variant="secondary"
            size="sm"
            leftIcon={<ArrowLeft className={isRTL ? "rotate-180" : ""} />}
          >
            {t.componentBreakdown.returnToDashboard}
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

        {/* Top Sharia Financing Status Card */}
        <Card elevation={0} padding="lg" className="border-[var(--color-border-default)]">
          {isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-6 w-48" />
              <Skeleton className="h-4 w-3/4" />
            </div>
          ) : (
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="flex items-start gap-3.5">
                <div
                  className={cn(
                    "w-11 h-11 rounded-[var(--radius-md)] flex items-center justify-center shrink-0",
                    data?.shariaFinancingStatus === "compliant"
                      ? "bg-[var(--color-success-surface)] text-[var(--color-status-success)]"
                      : "bg-[var(--color-warning-surface)] text-[var(--color-status-warning)]"
                  )}
                >
                  {data?.shariaFinancingStatus === "compliant" ? (
                    <ShieldCheck className="w-6 h-6" />
                  ) : (
                    <AlertTriangle className="w-6 h-6" />
                  )}
                </div>
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <h2 className="text-[17px] font-semibold text-[var(--color-text-primary)] font-heading">
                      {data?.shariaFinancingStatus === "compliant"
                        ? t.zakat.compliantStatus
                        : t.zakat.conventionalNotice}
                    </h2>
                    <Badge
                      variant={data?.shariaFinancingStatus === "compliant" ? "success" : "warning"}
                      size="sm"
                    >
                      {data?.shariaFinancingStatus === "compliant" ? "Verified Islamic" : "Conventional"}
                    </Badge>
                  </div>
                  <p className="text-[13px] text-[var(--color-text-secondary)] mt-1 max-w-2xl">
                    {data?.financingNote}
                  </p>
                </div>
              </div>
            </div>
          )}
        </Card>

        {/* Zakat Asset Pool Calculation Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Main 2-Column: Breakdown of Assets & Liabilities */}
          <div className="lg:col-span-2 space-y-6">
            <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-5">
              <div className="border-b border-[var(--color-border-subtle)] pb-3">
                <h3 className="text-[16px] font-semibold text-[var(--color-text-primary)] font-heading">
                  {t.zakat.zakatTitle}
                </h3>
                <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5">
                  {t.zakat.zakatSubtitle}
                </p>
              </div>

              {isLoading ? (
                <div className="space-y-4">
                  <Skeleton className="h-12 w-full" />
                  <Skeleton className="h-12 w-full" />
                  <Skeleton className="h-12 w-full" />
                </div>
              ) : (
                <div className="space-y-4 text-start">
                  {/* Zakatable Assets Group */}
                  <div className="space-y-2">
                    <span className="text-[11px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider block">
                      {t.zakat.zakatableAssets}
                    </span>
                    <div className="space-y-2">
                      <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] flex items-center justify-between">
                        <div className="flex items-center gap-2 text-[13px] text-[var(--color-text-primary)]">
                          <Wallet className="w-4 h-4 text-[var(--color-brand-primary)]" />
                          <span>{t.zakat.cashBalance}</span>
                        </div>
                        <span className="font-semibold text-[14px] text-[var(--color-text-primary)] font-heading">
                          {formatCurrency(data?.zakatableCash ?? 0)}
                        </span>
                      </div>

                      <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] flex items-center justify-between">
                        <div className="flex items-center gap-2 text-[13px] text-[var(--color-text-primary)]">
                          <Package className="w-4 h-4 text-[var(--color-brand-primary)]" />
                          <span>{t.zakat.inventory}</span>
                        </div>
                        <span className="font-semibold text-[14px] text-[var(--color-text-primary)] font-heading">
                          {formatCurrency(data?.zakatableInventory ?? 0)}
                        </span>
                      </div>

                      <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] flex items-center justify-between">
                        <div className="flex items-center gap-2 text-[13px] text-[var(--color-text-primary)]">
                          <ArrowDownRight className="w-4 h-4 text-[var(--color-brand-primary)]" />
                          <span>{t.zakat.receivables}</span>
                        </div>
                        <span className="font-semibold text-[14px] text-[var(--color-text-primary)] font-heading">
                          {formatCurrency(data?.zakatableReceivables ?? 0)}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Deductible Liabilities Group */}
                  <div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
                    <span className="text-[11px] font-bold text-[var(--color-status-error)] uppercase tracking-wider block">
                      {t.zakat.deductibleLiabilities}
                    </span>
                    <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] flex items-center justify-between">
                      <div className="flex items-center gap-2 text-[13px] text-[var(--color-text-primary)]">
                        <TrendingDown className="w-4 h-4 text-[var(--color-status-error)]" />
                        <span>{t.zakat.payables}</span>
                      </div>
                      <span className="font-semibold text-[14px] text-[var(--color-status-error)] font-heading">
                        - {formatCurrency(data?.deductiblePayables ?? 0)}
                      </span>
                    </div>
                  </div>

                  {/* Net Pool Total */}
                  <div className="p-4 rounded-[var(--radius-md)] bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)] flex items-center justify-between mt-4">
                    <div>
                      <span className="text-[13px] font-bold text-[var(--color-brand-primary)] block">
                        {t.zakat.netPool}
                      </span>
                      <span className="text-[11px] text-[var(--color-text-muted)]">
                        Assets minus Deductible Payables
                      </span>
                    </div>
                    <span className="text-[20px] font-extrabold text-[var(--color-brand-primary)] font-heading">
                      {formatCurrency(data?.netZakatablePool ?? 0)}
                    </span>
                  </div>
                </div>
              )}
            </Card>
          </div>

          {/* Right Column: Nisab Evaluation & Estimated Due */}
          <div className="space-y-6">
            <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
              <div className="border-b border-[var(--color-border-subtle)] pb-3">
                <h3 className="text-[15px] font-semibold text-[var(--color-text-primary)] font-heading">
                  {t.zakat.nisabStandard}
                </h3>
                <p className="text-[11px] text-[var(--color-text-muted)] mt-0.5">
                  {data?.nisabStandardDate}
                </p>
              </div>

              {isLoading ? (
                <Skeleton className="h-24 w-full" />
              ) : (
                <div className="space-y-3">
                  <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] flex justify-between items-center">
                    <span className="text-[12px] text-[var(--color-text-secondary)]">Nisab Threshold:</span>
                    <span className="text-[14px] font-bold text-[var(--color-text-primary)] font-heading">
                      {formatCurrency(data?.nisabSilverThresholdPkr ?? 285000)}
                    </span>
                  </div>

                  <div
                    className={cn(
                      "p-3.5 rounded-[var(--radius-md)] flex items-center gap-2.5 text-[12px] font-medium",
                      data?.isEligible
                        ? "bg-[var(--color-success-surface)] text-[var(--color-status-success)]"
                        : "bg-[var(--color-surface-subtle)] text-[var(--color-text-muted)]"
                    )}
                  >
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>{data?.isEligible ? t.zakat.nisabMet : t.zakat.nisabNotMet}</span>
                  </div>

                  {/* Estimated Zakat Due Hero Card */}
                  <div className="p-4 rounded-[var(--radius-lg)] bg-[var(--color-surface-card)] border-2 border-[var(--color-brand-primary)] text-center mt-4">
                    <span className="text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider block">
                      {t.zakat.estimatedDue}
                    </span>
                    <span className="text-[28px] font-extrabold text-[var(--color-brand-primary)] font-heading block mt-1">
                      {formatCurrency(data?.estimatedZakatDue ?? 0)}
                    </span>
                    <span className="text-[11px] text-[var(--color-text-muted)] mt-1 block">
                      Applicable at 2.5% per lunar bookkeeping year
                    </span>
                  </div>
                </div>
              )}
            </Card>

            {/* Data Completeness Card */}
            <Card elevation={0} padding="md" className="border-[var(--color-border-default)]">
              <div className="flex items-center justify-between text-[12px] font-semibold mb-2">
                <span className="text-[var(--color-text-secondary)]">{t.zakat.completeness}</span>
                <span className="text-[var(--color-brand-primary)]">{data?.dataCompleteness ?? 90}%</span>
              </div>
              <div className="w-full h-2 rounded-full bg-[var(--color-surface-subtle)] overflow-hidden">
                <div
                  className="h-full bg-[var(--color-brand-primary)] rounded-full"
                  style={{ width: `${data?.dataCompleteness ?? 90}%` }}
                />
              </div>
            </Card>
          </div>
        </div>

        {/* Mandatory Religious Disclaimer Box */}
        <div className="p-4 rounded-[var(--radius-lg)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-default)] flex items-start gap-3">
          <Info className="w-5 h-5 text-[var(--color-brand-primary)] shrink-0 mt-0.5" />
          <div className="space-y-1 text-start">
            <h4 className="text-[13px] font-semibold text-[var(--color-text-primary)]">
              {t.zakat.disclaimerTitle}
            </h4>
            <p className="text-[12px] text-[var(--color-text-secondary)] leading-relaxed">
              {t.zakat.disclaimerText}
            </p>
          </div>
        </div>
      </Container>
    </AppShell>
  );
}
