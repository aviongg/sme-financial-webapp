"use client";

import React from "react";
import {
  CheckCircle2,
  ShieldCheck,
  AlertCircle,
  XCircle,
  Clock,
  Loader2,
} from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { useLanguage } from "@/lib/i18n/context";
import type { HealthBand, UploadStatus } from "@/types/financial";

export type StatusBadgeType =
  | { kind: "health"; status: HealthBand }
  | { kind: "upload"; status: UploadStatus };

export interface StatusBadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  kind: "health" | "upload";
  status: HealthBand | UploadStatus;
  customLabel?: string;
  size?: "sm" | "md";
}

/**
 * StatusBadge
 * Strictly adheres to FinSight rule: Color + Icon + Explicit text (Triple-coding).
 * Color is NEVER the sole indicator of status.
 */
export function StatusBadge({
  kind,
  status,
  customLabel,
  size = "md",
  className,
  ...props
}: StatusBadgeProps) {
  const { t } = useLanguage();

  let label = customLabel;
  let icon = null;
  let colorClasses = "";

  if (kind === "health") {
    switch (status) {
      case "strong":
        label = label || t.healthStates.strong;
        icon = <CheckCircle2 className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />;
        colorClasses =
          "bg-[var(--color-health-strong-bg)] text-[var(--color-health-strong)] border-[var(--color-health-strong-border)]";
        break;
      case "stable":
        label = label || t.healthStates.stable;
        icon = <ShieldCheck className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />;
        colorClasses =
          "bg-[var(--color-health-stable-bg)] text-[var(--color-health-stable)] border-[var(--color-health-stable-border)]";
        break;
      case "attention":
        label = label || t.healthStates.attention;
        icon = <AlertCircle className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />;
        colorClasses =
          "bg-[var(--color-health-attention-bg)] text-[var(--color-health-attention)] border-[var(--color-health-attention-border)]";
        break;
      case "risk":
        label = label || t.healthStates.risk;
        icon = <XCircle className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />;
        colorClasses =
          "bg-[var(--color-health-risk-bg)] text-[var(--color-health-risk)] border-[var(--color-health-risk-border)]";
        break;
    }
  } else {
    // Upload lifecycle status
    switch (status) {
      case "pending":
        label = label || t.uploadStates.pending;
        icon = <Clock className="w-3.5 h-3.5 shrink-0 text-[var(--color-text-muted)]" aria-hidden="true" />;
        colorClasses =
          "bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)] border-[var(--color-border-default)]";
        break;
      case "processing":
        label = label || t.uploadStates.processing;
        icon = <Loader2 className="w-3.5 h-3.5 shrink-0 animate-spin text-[var(--color-brand-primary)]" aria-hidden="true" />;
        colorClasses =
          "bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)] border-[var(--color-brand-border)]";
        break;
      case "extracted":
        label = label || t.uploadStates.extracted;
        icon = <CheckCircle2 className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />;
        colorClasses =
          "bg-[var(--color-status-info-bg)] text-[var(--color-status-info)] border-[var(--color-status-info-border)]";
        break;
      case "needs_review":
        label = label || t.uploadStates.needs_review;
        icon = <AlertCircle className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />;
        colorClasses =
          "bg-[var(--color-status-warning-bg)] text-[var(--color-status-warning)] border-[var(--color-status-warning-border)]";
        break;
      case "confirmed":
        label = label || t.uploadStates.confirmed;
        icon = <ShieldCheck className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />;
        colorClasses =
          "bg-[var(--color-status-success-bg)] text-[var(--color-status-success)] border-[var(--color-status-success-border)]";
        break;
      case "failed":
        label = label || t.uploadStates.failed;
        icon = <XCircle className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />;
        colorClasses =
          "bg-[var(--color-status-error-bg)] text-[var(--color-status-error)] border-[var(--color-status-error-border)]";
        break;
    }
  }

  const sizeStyles = {
    sm: "px-2 py-0.5 text-[11px] gap-1",
    md: "px-2.5 py-1 text-[12px] gap-1.5",
  };

  return (
    <span
      className={cn(
        "inline-flex items-center font-medium rounded-[var(--radius-full)] border select-none",
        sizeStyles[size],
        colorClasses,
        className
      )}
      {...props}
    >
      {icon}
      <span>{label}</span>
    </span>
  );
}
