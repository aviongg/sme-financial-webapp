"use client";

import React from "react";
import { CheckCircle2, AlertTriangle, XCircle, Info, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface AlertBannerProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: "success" | "warning" | "error" | "info";
  title?: string;
  onDismiss?: () => void;
}

export function AlertBanner({
  variant = "info",
  title,
  children,
  onDismiss,
  className,
  ...props
}: AlertBannerProps) {
  const variantConfig = {
    success: {
      bg: "bg-[var(--color-status-success-bg)]",
      border: "border-[var(--color-status-success-border)]",
      text: "text-[var(--color-status-success)]",
      icon: <CheckCircle2 className="w-5 h-5 shrink-0" />,
    },
    warning: {
      bg: "bg-[var(--color-status-warning-bg)]",
      border: "border-[var(--color-status-warning-border)]",
      text: "text-[var(--color-status-warning)]",
      icon: <AlertTriangle className="w-5 h-5 shrink-0" />,
    },
    error: {
      bg: "bg-[var(--color-status-error-bg)]",
      border: "border-[var(--color-status-error-border)]",
      text: "text-[var(--color-status-error)]",
      icon: <XCircle className="w-5 h-5 shrink-0" />,
    },
    info: {
      bg: "bg-[var(--color-status-info-bg)]",
      border: "border-[var(--color-status-info-border)]",
      text: "text-[var(--color-status-info)]",
      icon: <Info className="w-5 h-5 shrink-0" />,
    },
  };

  const current = variantConfig[variant];

  return (
    <div
      role="alert"
      className={cn(
        "flex items-start gap-3 p-4 rounded-[var(--radius-lg)] border text-start",
        current.bg,
        current.border,
        className
      )}
      {...props}
    >
      <div className={cn("mt-0.5", current.text)}>{current.icon}</div>

      <div className="flex-1 text-[13px] text-[var(--color-text-primary)] leading-normal">
        {title && <p className="font-semibold mb-0.5">{title}</p>}
        <div>{children}</div>
      </div>

      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          className="text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] transition-colors p-1"
          aria-label="Dismiss alert"
        >
          <X className="w-4 h-4" />
        </button>
      )}
    </div>
  );
}
