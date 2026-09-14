"use client";

import React, { forwardRef } from "react";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "ghost" | "destructive";
  size?: "sm" | "md" | "lg";
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = "primary",
      size = "md",
      isLoading = false,
      leftIcon,
      rightIcon,
      disabled,
      children,
      ...props
    },
    ref
  ) => {
    const isInteractive = !disabled && !isLoading;

    const baseStyles =
      "inline-flex items-center justify-center font-medium transition-all duration-150 rounded-[var(--radius-lg)] select-none " +
      "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-primary)]";

    const variantStyles = {
      primary:
        "bg-[var(--color-brand-primary)] text-[var(--color-text-inverse)] " +
        (isInteractive
          ? "hover:bg-[var(--color-brand-hover)] active:bg-[var(--color-brand-active)] active:scale-[0.98]"
          : "opacity-60 cursor-not-allowed"),
      secondary:
        "bg-[var(--color-surface-card)] text-[var(--color-text-primary)] border border-[var(--color-border-default)] shadow-[var(--elevation-1)] " +
        (isInteractive
          ? "hover:bg-[var(--color-surface-hover)] active:bg-[var(--color-border-subtle)] active:scale-[0.98]"
          : "opacity-60 cursor-not-allowed"),
      ghost:
        "bg-transparent text-[var(--color-text-secondary)] " +
        (isInteractive
          ? "hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-text-primary)] active:scale-[0.98]"
          : "opacity-60 cursor-not-allowed"),
      destructive:
        "bg-[var(--color-status-error)] text-[var(--color-text-inverse)] " +
        (isInteractive
          ? "hover:bg-[#B91C1C] active:bg-[#991B1B] active:scale-[0.98]"
          : "opacity-60 cursor-not-allowed"),
    };

    const sizeStyles = {
      sm: "h-8 px-3 text-[13px] gap-1.5",
      md: "h-10 px-4 text-[14px] gap-2",
      lg: "h-12 px-5 text-[15px] gap-2.5 min-h-[48px]", // 48px touch-target friendly
    };

    return (
      <button
        ref={ref}
        disabled={disabled || isLoading}
        aria-busy={isLoading}
        className={cn(baseStyles, variantStyles[variant], sizeStyles[size], className)}
        {...props}
      >
        {isLoading ? (
          <Loader2 className="w-4 h-4 animate-spin shrink-0" aria-hidden="true" />
        ) : (
          leftIcon && <span className="shrink-0">{leftIcon}</span>
        )}
        <span>{children}</span>
        {!isLoading && rightIcon && <span className="shrink-0">{rightIcon}</span>}
      </button>
    );
  }
);

Button.displayName = "Button";
