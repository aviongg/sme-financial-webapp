"use client";

import React from "react";
import Link from "next/link";
import { ArrowRight, ArrowLeft, CheckCircle2 } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { useLanguage } from "@/lib/i18n/context";
import type { Recommendation } from "@/types/financial";
import { cn } from "@/lib/utils/cn";

export interface NextStepProps {
  recommendation: Recommendation;
  actionHref?: string;
  onActionClick?: () => void;
  className?: string;
}

/**
 * NextStep
 * Priority 3: "What should I do?"
 * Displays exactly ONE prioritized, actionable recommendation.
 */
export function NextStep({
  recommendation,
  actionHref = "/records",
  onActionClick,
  className,
}: NextStepProps) {
  const { t, direction } = useLanguage();
  const isRTL = direction === "rtl";

  return (
    <Card
      elevation={0}
      padding="md"
      className={cn(
        "border-[var(--color-border-default)] bg-[var(--color-surface-card)] text-start relative flex flex-col justify-between space-y-4",
        className
      )}
    >
      <div className="space-y-3">
        {/* Header Label & Priority Badge */}
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[var(--color-brand-primary)]" />
            <h3 className="text-[12px] uppercase font-bold tracking-wider text-[var(--color-text-muted)]">
              {t.dashboard.nextStepTitle}
            </h3>
          </div>
          <Badge variant="brand" size="sm">
            {recommendation.priority === "high" ? "High Priority" : "Suggested"}
          </Badge>
        </div>

        {/* Action Title */}
        <h4 className="text-[16px] font-semibold text-[var(--color-text-primary)] font-heading leading-snug">
          {recommendation.title}
        </h4>

        {/* Action Instruction */}
        <p className="text-[13px] text-[var(--color-text-secondary)] leading-relaxed">
          {recommendation.action}
        </p>

        {/* Projected Impact */}
        {recommendation.impact && (
          <div className="flex items-start gap-2 text-[12px] text-[var(--color-brand-primary)] font-medium pt-1">
            <CheckCircle2 className="w-3.5 h-3.5 shrink-0 mt-0.5" />
            <span>{recommendation.impact}</span>
          </div>
        )}
      </div>

      {/* Action Button */}
      <div className="pt-2">
        {actionHref ? (
          <Link href={actionHref} className="block w-full">
            <Button
              variant="primary"
              size="sm"
              className="w-full justify-center"
              rightIcon={
                isRTL ? (
                  <ArrowLeft className="w-3.5 h-3.5" />
                ) : (
                  <ArrowRight className="w-3.5 h-3.5" />
                )
              }
              onClick={onActionClick}
            >
              {t.dashboard.nextStepAction}
            </Button>
          </Link>
        ) : (
          <Button
            variant="primary"
            size="sm"
            className="w-full justify-center"
            rightIcon={
              isRTL ? (
                <ArrowLeft className="w-3.5 h-3.5" />
              ) : (
                <ArrowRight className="w-3.5 h-3.5" />
              )
            }
            onClick={onActionClick}
          >
            {t.dashboard.nextStepAction}
          </Button>
        )}
      </div>
    </Card>
  );
}
