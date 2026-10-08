"use client";

import React, { forwardRef, useId } from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps
  extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  options: SelectOption[];
  error?: string;
  helperText?: string;
  isOptional?: boolean;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  (
    {
      className,
      label,
      options,
      error,
      helperText,
      isOptional = false,
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
          <select
            ref={ref}
            id={id}
            disabled={disabled}
            aria-invalid={hasError}
            aria-describedby={hasError ? errorId : helperText ? helperId : undefined}
            className={cn(
              "w-full h-10 px-3 pe-9 bg-transparent text-[14px] text-[var(--color-text-primary)] appearance-none focus:outline-none disabled:cursor-not-allowed cursor-pointer",
              className
            )}
            {...props}
          >
            {options.map((opt) => (
              <option key={opt.value} value={opt.value} disabled={opt.disabled}>
                {opt.label}
              </option>
            ))}
          </select>

          <ChevronDown
            className="absolute end-3 w-4 h-4 text-[var(--color-text-muted)] pointer-events-none"
            aria-hidden="true"
          />
        </div>

        {hasError ? (
          <p id={errorId} className="text-[12px] font-medium text-[var(--color-status-error)]" role="alert">
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

Select.displayName = "Select";
