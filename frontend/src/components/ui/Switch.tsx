"use client";

import React, { forwardRef, useId } from "react";
import { cn } from "@/lib/utils/cn";

export interface SwitchProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "type"> {
  label: string;
  description?: string;
}

export const Switch = forwardRef<HTMLInputElement, SwitchProps>(
  ({ className, label, description, id: customId, disabled, checked, ...props }, ref) => {
    const generatedId = useId();
    const id = customId || generatedId;

    return (
      <div className="flex items-center justify-between gap-4 select-none">
        <label htmlFor={id} className={cn("cursor-pointer", disabled && "cursor-not-allowed opacity-60")}>
          <span className="text-[14px] font-medium text-[var(--color-text-primary)] block">
            {label}
          </span>
          {description && (
            <span className="text-[12px] text-[var(--color-text-muted)] block mt-0.5">
              {description}
            </span>
          )}
        </label>

        <div className="relative inline-flex items-center">
          <input
            ref={ref}
            id={id}
            type="checkbox"
            role="switch"
            aria-checked={checked}
            checked={checked}
            disabled={disabled}
            className="peer sr-only"
            {...props}
          />
          <div
            className={cn(
              "w-11 h-6 bg-[var(--color-border-strong)] rounded-full transition-colors",
              "peer-checked:bg-[var(--color-brand-primary)]",
              "peer-focus-visible:ring-2 peer-focus-visible:ring-[var(--color-brand-primary)] peer-focus-visible:ring-offset-2",
              disabled && "opacity-50 cursor-not-allowed",
              className
            )}
          >
            <div
              className={cn(
                "w-5 h-5 bg-white rounded-full transition-transform transform mt-0.5 ms-0.5 shadow-sm",
                "peer-checked:translate-x-5 rtl:peer-checked:-translate-x-5"
              )}
            />
          </div>
        </div>
      </div>
    );
  }
);

Switch.displayName = "Switch";
