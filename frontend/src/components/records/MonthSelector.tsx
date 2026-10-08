"use client";

import React, { useId } from "react";
import { Calendar } from "lucide-react";
import { useLanguage } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";

export interface MonthSelectorProps {
  value: string; // YYYY-MM
  onChange: (val: string) => void;
  error?: string;
  disabled?: boolean;
  className?: string;
}

/**
 * MonthSelector
 * Human-friendly financial month picker.
 * Displays readable date (e.g. "August 2026" / "اگست 2026"),
 * outputs strict machine-readable "YYYY-MM" format.
 */
export function MonthSelector({
  value,
  onChange,
  error,
  disabled = false,
  className,
}: MonthSelectorProps) {
  const { t, locale } = useLanguage();
  const id = useId();
  const errorId = `${id}-error`;

  // Compute helper quick-picks
  const now = new Date();
  const getMonthStr = (offsetMonths: number) => {
    const d = new Date(now.getFullYear(), now.getMonth() - offsetMonths, 1);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    return `${y}-${m}`;
  };

  const currentMonthStr = getMonthStr(0);
  const prevMonthStr = getMonthStr(1);
  const twoAgoMonthStr = getMonthStr(2);

  // Format the current value nicely
  const formatDisplayMonth = (monthStr: string) => {
    if (!monthStr || !/^\d{4}-\d{2}$/.test(monthStr)) return monthStr;
    const [year, month] = monthStr.split("-");
    const d = new Date(Number(year), Number(month) - 1, 1);
    try {
      return new Intl.DateTimeFormat(locale === "ur" ? "ur-PK" : "en-US", {
        month: "long",
        year: "numeric",
      }).format(d);
    } catch {
      return `${monthStr}`;
    }
  };

  // Generate recent 18 months for dropdown
  const monthOptions: { value: string; label: string }[] = [];
  for (let i = 0; i <= 24; i++) {
    const mStr = getMonthStr(i);
    monthOptions.push({
      value: mStr,
      label: formatDisplayMonth(mStr),
    });
  }

  // Ensure current value is included in options if it's older or in the future
  if (value && !monthOptions.some((opt) => opt.value === value)) {
    monthOptions.unshift({
      value,
      label: formatDisplayMonth(value),
    });
  }

  const hasError = Boolean(error);

  return (
    <div className={cn("w-full flex flex-col gap-2 text-start", className)}>
      <div className="flex items-center justify-between">
        <label
          htmlFor={id}
          className="text-[13px] font-semibold text-[var(--color-text-secondary)] select-none"
        >
          {t.records.monthLabel}
          <span className="text-[var(--color-status-error)] ms-1">*</span>
        </label>
        <span className="text-[12px] font-medium text-[var(--color-brand-primary)]">
          {formatDisplayMonth(value)}
        </span>
      </div>

      <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
        {/* Native / Styled Select for Months */}
        <div
          className={cn(
            "relative flex-1 flex items-center rounded-[var(--radius-md)] border transition-colors bg-[var(--color-surface-card)]",
            hasError
              ? "border-[var(--color-status-error)] focus-within:ring-2 focus-within:ring-[var(--color-status-error)]/20"
              : "border-[var(--color-border-strong)] focus-within:border-[var(--color-brand-primary)] focus-within:ring-2 focus-within:ring-[var(--color-brand-primary)]/15",
            disabled && "bg-[var(--color-surface-subtle)] opacity-60 cursor-not-allowed"
          )}
        >
          <div className="flex items-center ps-3 pe-2 text-[var(--color-text-muted)] pointer-events-none">
            <Calendar className="w-4 h-4" />
          </div>
          <select
            id={id}
            value={value}
            disabled={disabled}
            onChange={(e) => onChange(e.target.value)}
            className="w-full h-10 px-2 pe-8 bg-transparent text-[14px] font-medium text-[var(--color-text-primary)] appearance-none focus:outline-none cursor-pointer disabled:cursor-not-allowed"
          >
            {monthOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label} ({opt.value})
              </option>
            ))}
          </select>
          <div className="absolute end-3 pointer-events-none text-[var(--color-text-muted)] text-[12px]">
            ▼
          </div>
        </div>

        {/* Quick-Pick Period Chips */}
        <div className="flex items-center gap-1.5 flex-wrap">
          <button
            type="button"
            disabled={disabled}
            onClick={() => onChange(currentMonthStr)}
            className={cn(
              "px-2.5 py-1.5 text-[12px] font-medium rounded-[var(--radius-md)] border transition-all select-none",
              value === currentMonthStr
                ? "bg-[var(--color-brand-primary)] text-white border-[var(--color-brand-primary)]"
                : "bg-[var(--color-surface-subtle)] text-[var(--color-text-secondary)] border-[var(--color-border-default)] hover:border-[var(--color-border-strong)]"
            )}
          >
            {t.records.quickMonthCurrent}
          </button>
          <button
            type="button"
            disabled={disabled}
            onClick={() => onChange(prevMonthStr)}
            className={cn(
              "px-2.5 py-1.5 text-[12px] font-medium rounded-[var(--radius-md)] border transition-all select-none",
              value === prevMonthStr
                ? "bg-[var(--color-brand-primary)] text-white border-[var(--color-brand-primary)]"
                : "bg-[var(--color-surface-subtle)] text-[var(--color-text-secondary)] border-[var(--color-border-default)] hover:border-[var(--color-border-strong)]"
            )}
          >
            {t.records.quickMonthPrevious}
          </button>
          <button
            type="button"
            disabled={disabled}
            onClick={() => onChange(twoAgoMonthStr)}
            className={cn(
              "px-2.5 py-1.5 text-[12px] font-medium rounded-[var(--radius-md)] border transition-all select-none",
              value === twoAgoMonthStr
                ? "bg-[var(--color-brand-primary)] text-white border-[var(--color-brand-primary)]"
                : "bg-[var(--color-surface-subtle)] text-[var(--color-text-secondary)] border-[var(--color-border-default)] hover:border-[var(--color-border-strong)]"
            )}
          >
            {t.records.quickMonthTwoAgo}
          </button>
        </div>
      </div>

      {hasError ? (
        <p id={errorId} className="text-[12px] font-medium text-[var(--color-status-error)]" role="alert">
          {error}
        </p>
      ) : (
        <p className="text-[12px] text-[var(--color-text-muted)]">
          {t.records.monthHelp}
        </p>
      )}
    </div>
  );
}
