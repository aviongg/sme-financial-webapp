"use client";

import React, { forwardRef, useId } from "react";
import { cn } from "@/lib/utils/cn";

export interface InputProps
  extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  helperText?: string;
  isOptional?: boolean;
  leftAddon?: React.ReactNode;
  rightAddon?: React.ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    {
      className,
      label,
      error,
      helperText,
      isOptional = false,
      leftAddon,
      rightAddon,
      id: customId,
      disabled,
      required,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const id = customId || generatedId;
    const errorId = `${id}-error`;
    const helperId = `${id}-helper`;

    const hasError = Boolean(error);

    return (
      <div className="w-full flex flex-col gap-1.5 text-start">
        <div className="flex items-center justify-between">
          <label
            htmlFor={id}
            className="text-[13px] font-semibold text-[var(--color-text-secondary)] select-none"
          >
            {label}
            {required && <span className="text-[var(--color-status-error)] ms-1">*</span>}
          </label>
          {isOptional && (
            <span className="text-[12px] text-[var(--color-text-muted)] font-normal">
              (Optional)
            </span>
          )}
        </div>

        <div
          className={cn(
            "relative flex items-center w-full rounded-[var(--radius-md)] border transition-colors bg-[var(--color-surface-card)]",
            hasError
              ? "border-[var(--color-status-error)] focus-within:ring-2 focus-within:ring-[var(--color-status-error)]/20"
              : "border-[var(--color-border-strong)] focus-within:border-[var(--color-brand-primary)] focus-within:ring-2 focus-within:ring-[var(--color-brand-primary)]/15",
            disabled && "bg-[var(--color-surface-subtle)] opacity-60 cursor-not-allowed"
          )}
        >
          {leftAddon && (
            <div className="flex items-center ps-3 pe-2 text-[var(--color-text-muted)] text-[14px] select-none pointer-events-none">
              {leftAddon}
            </div>
          )}

          <input
            ref={ref}
            id={id}
            disabled={disabled}
            aria-invalid={hasError}
            aria-describedby={
              hasError ? errorId : helperText ? helperId : undefined
            }
            className={cn(
              "w-full h-10 px-3 bg-transparent text-[14px] text-[var(--color-text-primary)] placeholder:text-[var(--color-text-muted)] focus:outline-none disabled:cursor-not-allowed",
              leftAddon && "ps-0",
              rightAddon && "pe-0",
              className
            )}
            {...props}
          />

          {rightAddon && (
            <div className="flex items-center pe-3 ps-2 text-[var(--color-text-muted)] text-[14px] select-none">
              {rightAddon}
            </div>
          )}
        </div>

        {hasError ? (
          <p
            id={errorId}
            className="text-[12px] font-medium text-[var(--color-status-error)] flex items-center gap-1"
            role="alert"
          >
            {error}
          </p>
        ) : helperText ? (
          <p id={helperId} className="text-[12px] text-[var(--color-text-muted)]">
            {helperText}
          </p>
        ) : null}
      </div>
    );
  }
);

Input.displayName = "Input";
