"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { Scale, ShieldCheck, ArrowRight } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";
import { cn } from "@/lib/utils/cn";
import { formatCurrency } from "@/lib/utils/currency";
import type { ZakatData } from "@/types/financial";

export interface ZakatSummaryCardProps {
  className?: string;
}

export function ZakatSummaryCard({ className }: ZakatSummaryCardProps) {
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

  if (isLoading || !data) return null;

  return (
    <Card
      elevation={0}
      padding="lg"
      className={cn("border-[var(--color-border-default)] text-start", className)}
    >
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-[var(--color-border-subtle)]">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-[var(--radius-md)] bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)] flex items-center justify-center shrink-0">
            <Scale className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-[16px] font-semibold text-[var(--color-text-primary)] font-heading">
                {t.zakat.title}
              </h3>
              <Badge variant="success" size="sm" className="gap-1">
                <ShieldCheck className="w-3 h-3" />
                <span>Verified Islamic</span>
              </Badge>
            </div>
            <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5">
              Asset-based 2.5% Zakat calculation aid based on active monthly records
            </p>
          </div>
        </div>

        <Link
          href="/sharia-zakat"
          className="inline-flex items-center gap-1 text-[13px] font-medium text-[var(--color-brand-primary)] hover:underline focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] rounded-[var(--radius-sm)] shrink-0"
        >
          <span>View Zakat Worksheet</span>
          <ArrowRight className={cn("w-3.5 h-3.5", isRTL && "rotate-180")} />
        </Link>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-4">
        <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)]">
          <span className="text-[11px] text-[var(--color-text-muted)] block">
            {t.zakat.netPool}
          </span>
          <span className="text-[16px] font-bold text-[var(--color-text-primary)] font-heading block mt-0.5">
            {formatCurrency(data.netZakatablePool)}
          </span>
        </div>

        <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)]">
          <span className="text-[11px] text-[var(--color-text-muted)] block">
            Nisab Standard (Silver)
          </span>
          <span className="text-[16px] font-bold text-[var(--color-text-primary)] font-heading block mt-0.5">
            {formatCurrency(data.nisabSilverThresholdPkr)}
          </span>
        </div>

        <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)]">
          <span className="text-[11px] font-semibold text-[var(--color-brand-primary)] block">
            {t.zakat.estimatedDue}
          </span>
          <span className="text-[16px] font-extrabold text-[var(--color-brand-primary)] font-heading block mt-0.5">
            {formatCurrency(data.estimatedZakatDue)}
          </span>
        </div>
      </div>
    </Card>
  );
}
