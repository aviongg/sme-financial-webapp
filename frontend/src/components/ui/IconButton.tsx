"use client";

import React, { forwardRef } from "react";
import { cn } from "@/lib/utils/cn";

export interface IconButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  "aria-label": string; // Explicitly enforce accessible name
  variant?: "ghost" | "secondary" | "primary";
  size?: "sm" | "md" | "lg";
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  (
    {
      className,
      "aria-label": ariaLabel,
      variant = "ghost",
      size = "md",
      disabled,
      children,
      ...props
    },
    ref
  ) => {
    const baseStyles =
      "inline-flex items-center justify-center rounded-[var(--radius-md)] transition-all select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-primary)]";

    const variantStyles = {
      ghost:
        "bg-transparent text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-text-primary)] active:bg-[var(--color-border-subtle)] active:scale-[0.96]",
      secondary:
        "bg-[var(--color-surface-card)] text-[var(--color-text-primary)] border border-[var(--color-border-default)] shadow-[var(--elevation-1)] hover:bg-[var(--color-surface-hover)] active:scale-[0.96]",
      primary:
        "bg-[var(--color-brand-primary)] text-[var(--color-text-inverse)] hover:bg-[var(--color-brand-hover)] active:scale-[0.96]",
    };

    const sizeStyles = {
      sm: "w-8 h-8",
      md: "w-10 h-10 min-w-[40px] min-h-[40px]",
      lg: "w-12 h-12 min-w-[48px] min-h-[48px]", // 48px touch target
    };

    return (
      <button
        ref={ref}
        aria-label={ariaLabel}
        disabled={disabled}
        className={cn(
          baseStyles,
          variantStyles[variant],
          sizeStyles[size],
          disabled && "opacity-50 cursor-not-allowed",
          className
        )}
        {...props}
      >
        {children}
      </button>
    );
  }
);

IconButton.displayName = "IconButton";
