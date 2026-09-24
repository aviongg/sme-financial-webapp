"use client";

import React, { forwardRef, useId } from "react";
import { Check } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface CheckboxProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "type"> {
  label: string;
  description?: string;
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  ({ className, label, description, id: customId, disabled, checked, ...props }, ref) => {
    const generatedId = useId();
    const id = customId || generatedId;

    return (
      <div className={cn("flex items-start gap-2.5 text-start select-none", className)}>
        <div className="relative flex items-center mt-0.5">
          <input
            ref={ref}
            id={id}
            type="checkbox"
            checked={checked}
            disabled={disabled}
            className="peer sr-only"
            {...props}
          />
          <div
            className={cn(
              "w-4 h-4 rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] transition-colors flex items-center justify-center",
              "peer-checked:bg-[var(--color-brand-primary)] peer-checked:border-[var(--color-brand-primary)]",
              "peer-focus-visible:ring-2 peer-focus-visible:ring-[var(--color-brand-primary)] peer-focus-visible:ring-offset-2",
              disabled && "opacity-50 cursor-not-allowed bg-[var(--color-surface-subtle)]"
            )}
          >
            <Check className="w-3 h-3 text-white opacity-0 peer-checked:opacity-100 transition-opacity" strokeWidth={3} />
          </div>
        </div>

        <label
          htmlFor={id}
          className={cn(
            "text-[14px] leading-tight text-[var(--color-text-primary)] cursor-pointer",
            disabled && "cursor-not-allowed opacity-60"
          )}
        >
          <span className="font-medium">{label}</span>
          {description && (
            <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5">{description}</p>
          )}
        </label>
      </div>
    );
  }
);

Checkbox.displayName = "Checkbox";
