"use client";

import React from "react";
import Link from "next/link";
import { Plus, Calendar } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { useLanguage } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";
import type { BusinessProfile } from "@/types/financial";

export interface DashboardHeaderProps {
  profile: BusinessProfile;
  currentPeriod: string; // e.g. "2026-08"
  isProvisional: boolean;
  onToggleMode?: () => void;
  className?: string;
}

/**
 * DashboardHeader
 * Section 6 of prompt: Business Context Header.
 * Communicates: Business Name, current financial period, page title.
 * Restrained editorial typography avoiding unnecessary decoration.
 */
export function DashboardHeader({
  profile,
  currentPeriod,
  isProvisional,
  onToggleMode,
  className,
}: DashboardHeaderProps) {
  const { t } = useLanguage();

  const formatPeriod = (periodStr: string) => {
    const [year, month] = periodStr.split("-");
    const date = new Date(Number(year), Number(month) - 1, 1);
    return date.toLocaleDateString("en-US", { month: "long", year: "numeric" });
  };

  // Map business type
  const typeMap: Record<string, string> = {
    trade: t.onboarding.typeTradeTitle,
    manufacturing: t.onboarding.typeMfgTitle,
    services: t.onboarding.typeServicesTitle,
    retail: t.onboarding.typeRetailTitle,
  };
  const typeLabel = typeMap[profile.businessType?.toLowerCase()] || profile.businessType;

  return (
    <div
      className={cn(
        "flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-[var(--color-border-subtle)] text-start",
        className
      )}
    >
      {/* Business & Page Context */}
      <div className="space-y-1.5">
        <div className="flex items-center gap-2.5 flex-wrap">
          <h1 className="text-[24px] sm:text-[28px] font-bold text-[var(--color-text-primary)] font-heading tracking-tight">
            {t.dashboard.pageTitle}
          </h1>
          <Badge variant="neutral" size="sm" className="font-semibold">
            {typeLabel}
          </Badge>
          {isProvisional && (
            <Badge variant="brand" size="sm">
              Provisional
            </Badge>
          )}
        </div>

        <div className="flex items-center gap-3 text-[13px] text-[var(--color-text-muted)]">
          <span className="font-semibold text-[var(--color-text-secondary)]">
            {profile.businessName || "My Business"}
          </span>
          <span>•</span>
          <span className="flex items-center gap-1">
            <Calendar className="w-3.5 h-3.5 shrink-0" />
            <span>{formatPeriod(currentPeriod)}</span>
          </span>
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2.5 self-start md:self-auto flex-wrap">
        {/* Development / Evaluator Mode Toggle */}
        {onToggleMode && (
          <Button
            variant="secondary"
            size="sm"
            onClick={onToggleMode}
            className="text-[12px] h-9"
          >
            {isProvisional
              ? t.dashboard.switchViewMature
              : t.dashboard.switchViewProvisional}
          </Button>
        )}

        {/* Primary Action: Add Record */}
        <Link href="/records/new">
          <Button
            variant="primary"
            size="sm"
            className="h-9"
            leftIcon={<Plus className="w-4 h-4" />}
          >
            {t.dashboard.addMonthlyRecord}
          </Button>
        </Link>
      </div>
    </div>
  );
}
