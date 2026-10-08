"use client";

import React from "react";
import Link from "next/link";
import { FolderOpen } from "lucide-react";
import { Button } from "./Button";
import { cn } from "@/lib/utils/cn";

export interface EmptyStateProps extends React.HTMLAttributes<HTMLDivElement> {
  icon?: React.ReactNode;
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  actionHref?: string;
  secondaryActionLabel?: string;
  onSecondaryAction?: () => void;
  secondaryActionHref?: string;
}

/**
 * EmptyState Primitive
 * Grounded in Section 11.2:
 * 1. Explains what belongs here
 * 2. Explains why it matters
 * 3. Provides easiest next action
 */
export function EmptyState({
  icon,
  title,
  description,
  actionLabel,
  onAction,
  actionHref,
  secondaryActionLabel,
  onSecondaryAction,
  secondaryActionHref,
  className,
  ...props
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center text-center p-8 sm:p-12 border border-dashed border-[var(--color-border-default)] rounded-[var(--radius-xl)] bg-[var(--color-surface-subtle)]/50",
        className
      )}
      {...props}
    >
      <div className="w-12 h-12 rounded-[var(--radius-full)] bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] flex items-center justify-center text-[var(--color-text-secondary)] mb-4">
        {icon || <FolderOpen className="w-6 h-6" />}
      </div>

      <h3 className="text-[17px] font-semibold text-[var(--color-text-primary)] font-heading">
        {title}
      </h3>

      <p className="text-[14px] text-[var(--color-text-muted)] max-w-[440px] mt-1.5 mb-6 leading-relaxed">
        {description}
      </p>

      {(actionLabel || secondaryActionLabel) && (
        <div className="flex flex-wrap items-center justify-center gap-3">
          {actionLabel &&
            (actionHref ? (
              <Link href={actionHref}>
                <Button variant="primary">{actionLabel}</Button>
              </Link>
            ) : (
              <Button variant="primary" onClick={onAction}>
                {actionLabel}
              </Button>
            ))}

          {secondaryActionLabel &&
            (secondaryActionHref ? (
              <Link href={secondaryActionHref}>
                <Button variant="secondary">{secondaryActionLabel}</Button>
              </Link>
            ) : (
              <Button variant="secondary" onClick={onSecondaryAction}>
                {secondaryActionLabel}
              </Button>
            ))}
        </div>
      )}
    </div>
  );
}
