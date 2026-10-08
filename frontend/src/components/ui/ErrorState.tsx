"use client";

import React from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { Button } from "./Button";
import { cn } from "@/lib/utils/cn";

export interface ErrorStateProps extends React.HTMLAttributes<HTMLDivElement> {
  title?: string;
  description?: string;
  onRetry?: () => void;
  retryLabel?: string;
  showSafeMessage?: boolean;
}

/**
 * ErrorState Primitive
 * Grounded in Section 11.3:
 * - Plain human-readable language
 * - Explains what is safe
 * - Provides actionable recovery
 */
export function ErrorState({
  title = "Something went wrong",
  description = "We were unable to complete this action. Your records remain safe and saved.",
  onRetry,
  retryLabel = "Try Again",
  showSafeMessage = true,
  className,
  ...props
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center justify-center text-center p-8 sm:p-12 border border-[var(--color-status-error-border)] rounded-[var(--radius-xl)] bg-[var(--color-status-error-bg)]/40",
        className
      )}
      {...props}
    >
      <div className="w-12 h-12 rounded-[var(--radius-full)] bg-[var(--color-status-error-bg)] border border-[var(--color-status-error-border)] flex items-center justify-center text-[var(--color-status-error)] mb-4">
        <AlertTriangle className="w-6 h-6" />
      </div>

      <h3 className="text-[17px] font-semibold text-[var(--color-text-primary)] font-heading">
        {title}
      </h3>

      <p className="text-[14px] text-[var(--color-text-secondary)] max-w-[440px] mt-1.5 mb-2 leading-relaxed">
        {description}
      </p>

      {showSafeMessage && (
        <p className="text-[12px] text-[var(--color-text-muted)] mb-6">
          No data was lost or overwritten during this request.
        </p>
      )}

      {onRetry && (
        <Button
          variant="secondary"
          onClick={onRetry}
          leftIcon={<RefreshCw className="w-4 h-4" />}
        >
          {retryLabel}
        </Button>
      )}
    </div>
  );
}
