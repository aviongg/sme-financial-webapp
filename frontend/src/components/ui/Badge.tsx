import React from "react";
import { cn } from "@/lib/utils/cn";

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: "neutral" | "brand" | "success" | "warning" | "error" | "info";
  size?: "sm" | "md";
}

export function Badge({
  variant = "neutral",
  size = "md",
  className,
  children,
  ...props
}: BadgeProps) {
  const baseStyles =
    "inline-flex items-center gap-1.5 font-medium rounded-[var(--radius-full)] border select-none";

  const variantStyles = {
    neutral:
      "bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)] border-[var(--color-border-default)]",
    brand:
      "bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)] border-[var(--color-brand-border)]",
    success:
      "bg-[var(--color-status-success-bg)] text-[var(--color-status-success)] border-[var(--color-status-success-border)]",
    warning:
      "bg-[var(--color-status-warning-bg)] text-[var(--color-status-warning)] border-[var(--color-status-warning-border)]",
    error:
      "bg-[var(--color-status-error-bg)] text-[var(--color-status-error)] border-[var(--color-status-error-border)]",
    info:
      "bg-[var(--color-status-info-bg)] text-[var(--color-status-info)] border-[var(--color-status-info-border)]",
  };

  const sizeStyles = {
    sm: "px-2 py-0.5 text-[11px]",
    md: "px-2.5 py-1 text-[12px]",
  };

  return (
    <span
      className={cn(baseStyles, variantStyles[variant], sizeStyles[size], className)}
      {...props}
    >
      {children}
    </span>
  );
}
