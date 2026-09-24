"use client";

import React from "react";
import { CurrencyInput } from "@/components/ui/CurrencyInput";
import { Select } from "@/components/ui/Select";
import { Badge } from "@/components/ui/Badge";
import { useLanguage } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";
import type { FinancingType } from "@/types/financial";

export interface PrecisionBoostersValues {
  cogs: number | null;
  receivablesOutstanding: number | null;
  payablesOutstanding: number | null;
  inventoryValue: number | null;
  loanOutstanding: number | null;
  interestExpense: number | null;
  financingType: FinancingType;
}

export type PrecisionBoostersErrors = Partial<
  Record<keyof PrecisionBoostersValues, string>
>;

export interface PrecisionBoostersSectionProps {
  values: PrecisionBoostersValues;
  onChange: <K extends keyof PrecisionBoostersValues>(
    field: K,
    value: PrecisionBoostersValues[K]
  ) => void;
  errors: PrecisionBoostersErrors;
  disabled?: boolean;
  className?: string;
}

/**
 * PrecisionBoostersSection
 * Visually secondary optional section.
 * "Sharpen Your Financial Profile"
 * Missing values remain strictly null/undefined; zero represents explicit 0.
 */
export function PrecisionBoostersSection({
  values,
  onChange,
  errors,
  disabled = false,
  className,
}: PrecisionBoostersSectionProps) {
  const { t } = useLanguage();

  const financingOptions = [
    { value: "none", label: t.records.financingTypeNone },
    { value: "conventional", label: t.records.financingTypeConventional },
    { value: "islamic", label: t.records.financingTypeIslamic },
  ];

  return (
    <section
      className={cn(
        "rounded-[var(--radius-xl)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] p-5 sm:p-6 space-y-6 text-start",
        className
      )}
    >
      {/* Section Header */}
      <div className="space-y-1.5">
        <div className="flex items-center gap-2.5 flex-wrap">
          <h2 className="text-[16px] sm:text-[17px] font-semibold text-[var(--color-text-secondary)] font-heading">
            {t.records.precisionTitle}
          </h2>
          <Badge variant="neutral" size="sm">
            {t.records.precisionOptionalBadge}
          </Badge>
        </div>
        <p className="text-[13px] text-[var(--color-text-muted)] leading-relaxed">
          {t.records.precisionSubtitle}
        </p>
      </div>

      {/* Grid of Optional Fields */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-5">
        {/* 1. Cost of Goods Sold (cogs) */}
        <div>
          <CurrencyInput
            id="field-cogs"
            label={t.fields.cogs}
            helperText={t.fields.cogsHelp}
            value={values.cogs}
            onValueChange={(val) => onChange("cogs", val)}
            error={errors.cogs}
            isOptional
            disabled={disabled}
          />
        </div>

        {/* 2. Inventory Value (inventoryValue) */}
        <div>
          <CurrencyInput
            id="field-inventoryValue"
            label={t.fields.inventoryValue}
            helperText={t.fields.inventoryValueHelp}
            value={values.inventoryValue}
            onValueChange={(val) => onChange("inventoryValue", val)}
            error={errors.inventoryValue}
            isOptional
            disabled={disabled}
          />
        </div>

        {/* 3. Money Customers Still Owe (receivablesOutstanding) */}
        <div>
          <CurrencyInput
            id="field-receivablesOutstanding"
            label={t.fields.receivablesOutstanding}
            helperText={t.fields.receivablesHelp}
            value={values.receivablesOutstanding}
            onValueChange={(val) => onChange("receivablesOutstanding", val)}
            error={errors.receivablesOutstanding}
            isOptional
            disabled={disabled}
          />
        </div>

        {/* 4. Money You Still Owe Suppliers (payablesOutstanding) */}
        <div>
          <CurrencyInput
            id="field-payablesOutstanding"
            label={t.fields.payablesOutstanding}
            helperText={t.fields.payablesHelp}
            value={values.payablesOutstanding}
            onValueChange={(val) => onChange("payablesOutstanding", val)}
            error={errors.payablesOutstanding}
            isOptional
            disabled={disabled}
          />
        </div>

        {/* 5. Outstanding Loan Balance (loanOutstanding) */}
        <div>
          <CurrencyInput
            id="field-loanOutstanding"
            label={t.fields.loanOutstanding}
            helperText={t.fields.loanOutstandingHelp}
            value={values.loanOutstanding}
            onValueChange={(val) => onChange("loanOutstanding", val)}
            error={errors.loanOutstanding}
            isOptional
            disabled={disabled}
          />
        </div>

        {/* 6. Interest Paid (interestExpense) */}
        <div>
          <CurrencyInput
            id="field-interestExpense"
            label={t.fields.interestExpense}
            helperText={t.fields.interestExpenseHelp}
            value={values.interestExpense}
            onValueChange={(val) => onChange("interestExpense", val)}
            error={errors.interestExpense}
            isOptional
            disabled={disabled}
          />
        </div>

        {/* 7. Financing Type (financingType) */}
        <div className="md:col-span-2">
          <Select
            id="field-financingType"
            label={t.fields.financingType}
            helperText={t.fields.financingTypeHelp}
            value={values.financingType}
            onChange={(e) =>
              onChange("financingType", e.target.value as FinancingType)
            }
            options={financingOptions}
            error={errors.financingType}
            isOptional
            disabled={disabled}
          />
        </div>
      </div>
    </section>
  );
}
