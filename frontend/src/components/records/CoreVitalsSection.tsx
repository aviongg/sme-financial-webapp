"use client";

import React from "react";
import { CurrencyInput } from "@/components/ui/CurrencyInput";
import { useLanguage } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";

export interface CoreVitalsValues {
  cashInflow: number | null;
  cashOutflow: number | null;
  revenue: number | null;
  operatingExpenses: number | null;
  cashBalanceEom: number | null;
}

export type CoreVitalsErrors = Partial<Record<keyof CoreVitalsValues, string>>;

export interface CoreVitalsSectionProps {
  values: CoreVitalsValues;
  onChange: <K extends keyof CoreVitalsValues>(field: K, value: number | null) => void;
  errors: CoreVitalsErrors;
  disabled?: boolean;
  className?: string;
}

/**
 * CoreVitalsSection
 * The primary focal section of the manual financial record form.
 * Visually dominant, restrained borders, comfortable reading layout.
 * Contains the 5 mandatory vitals required for cashflow stability and operating margins.
 */
export function CoreVitalsSection({
  values,
  onChange,
  errors,
  disabled = false,
  className,
}: CoreVitalsSectionProps) {
  const { t } = useLanguage();

  return (
    <section className={cn("space-y-6 text-start", className)}>
      {/* Section Header */}
      <div>
        <h2 className="text-[17px] sm:text-[18px] font-bold text-[var(--color-text-primary)] font-heading tracking-tight">
          {t.records.coreVitalsTitle}
        </h2>
        <p className="text-[13px] text-[var(--color-text-muted)] mt-1">
          {t.records.coreVitalsSubtitle}
        </p>
      </div>

      {/* Grid of 5 Core Vitals */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-5">
        {/* 1. Total Cash Received (cashInflow) */}
        <div>
          <CurrencyInput
            id="field-cashInflow"
            label={t.fields.cashInflow}
            helperText={t.fields.cashInflowHelp}
            value={values.cashInflow}
            onValueChange={(val) => onChange("cashInflow", val)}
            error={errors.cashInflow}
            required
            disabled={disabled}
          />
        </div>

        {/* 2. Total Cash Spent (cashOutflow) */}
        <div>
          <CurrencyInput
            id="field-cashOutflow"
            label={t.fields.cashOutflow}
            helperText={t.fields.cashOutflowHelp}
            value={values.cashOutflow}
            onValueChange={(val) => onChange("cashOutflow", val)}
            error={errors.cashOutflow}
            required
            disabled={disabled}
          />
        </div>

        {/* 3. Total Sales & Billings (revenue) */}
        <div>
          <CurrencyInput
            id="field-revenue"
            label={t.fields.revenue}
            helperText={t.fields.revenueHelp}
            value={values.revenue}
            onValueChange={(val) => onChange("revenue", val)}
            error={errors.revenue}
            required
            disabled={disabled}
          />
        </div>

        {/* 4. Running Business Costs (operatingExpenses) */}
        <div>
          <CurrencyInput
            id="field-operatingExpenses"
            label={t.fields.operatingExpenses}
            helperText={t.fields.operatingExpensesHelp}
            value={values.operatingExpenses}
            onValueChange={(val) => onChange("operatingExpenses", val)}
            error={errors.operatingExpenses}
            required
            disabled={disabled}
          />
        </div>

        {/* 5. Cash Left at Month End (cashBalanceEom) - Spans full row on md+ for visual anchoring */}
        <div className="md:col-span-2">
          <CurrencyInput
            id="field-cashBalanceEom"
            label={t.fields.cashBalanceEom}
            helperText={t.fields.cashBalanceEomHelp}
            value={values.cashBalanceEom}
            onValueChange={(val) => onChange("cashBalanceEom", val)}
            error={errors.cashBalanceEom}
            required
            disabled={disabled}
          />
        </div>
      </div>
    </section>
  );
}
