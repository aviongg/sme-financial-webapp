"use client";

import React, { useState } from "react";
import { ArrowLeft, ArrowRight, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { CurrencyInput } from "@/components/ui/CurrencyInput";
import { MonthSelector } from "@/components/records/MonthSelector";
import { useLanguage } from "@/lib/i18n/context";
import type { MonthlyRecordRequest } from "@/types/financial";

export interface StepFirstRecordProps {
  initialData: Partial<MonthlyRecordRequest>;
  onSubmit: (record: MonthlyRecordRequest) => Promise<void>;
  onBack: () => void;
  isLoading: boolean;
}

export function StepFirstRecord({
  initialData,
  onSubmit,
  onBack,
  isLoading,
}: StepFirstRecordProps) {
  const { t, direction } = useLanguage();

  const [month, setMonth] = useState<string>(initialData.month || "2026-08");
  const [cashInflow, setCashInflow] = useState<number | null>(initialData.cashInflow ?? 1850000);
  const [cashOutflow, setCashOutflow] = useState<number | null>(initialData.cashOutflow ?? 1420000);
  const [revenue, setRevenue] = useState<number | null>(initialData.revenue ?? 2100000);
  const [operatingExpenses, setOperatingExpenses] = useState<number | null>(initialData.operatingExpenses ?? 450000);
  const [cashBalanceEom, setCashBalanceEom] = useState<number | null>(initialData.cashBalanceEom ?? 980000);

  const [errors, setErrors] = useState<Record<string, string>>({});

  const validateAndSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const newErrors: Record<string, string> = {};

    if (!month.trim() || !/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
      newErrors.month = t.onboarding.firstRecordMonthError || t.records.validationMonthRequired;
    }

    if (cashInflow === null || cashInflow < 0) {
      newErrors.cashInflow = t.records.validationRequired;
    }

    if (cashOutflow === null || cashOutflow < 0) {
      newErrors.cashOutflow = t.records.validationRequired;
    }

    if (revenue === null || revenue < 0) {
      newErrors.revenue = t.records.validationRequired;
    }

    if (operatingExpenses === null || operatingExpenses < 0) {
      newErrors.operatingExpenses = t.records.validationRequired;
    }

    if (cashBalanceEom === null || cashBalanceEom < 0) {
      newErrors.cashBalanceEom = t.records.validationRequired;
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    setErrors({});

    const recordPayload: MonthlyRecordRequest = {
      month,
      cashInflow: cashInflow ?? 0,
      cashOutflow: cashOutflow ?? 0,
      revenue: revenue ?? 0,
      operatingExpenses: operatingExpenses ?? 0,
      cashBalanceEom: cashBalanceEom ?? 0,
      financingType: "none",
    };

    await onSubmit(recordPayload);
  };

  const isRTL = direction === "rtl";

  return (
    <form onSubmit={validateAndSubmit} className="w-full space-y-6 text-start">
      {/* Step Header */}
      <div>
        <h1 className="text-[26px] sm:text-[30px] font-bold text-[var(--color-text-primary)] font-heading tracking-tight">
          {t.onboarding.step5Title}
        </h1>
        <p className="text-[14px] sm:text-[15px] text-[var(--color-text-muted)] mt-1.5 leading-relaxed">
          {t.onboarding.step5Subtitle}
        </p>
      </div>

      {/* Info notice */}
      <div className="p-3.5 rounded-[var(--radius-lg)] bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] text-[12px] text-[var(--color-text-muted)] flex items-center gap-2">
        <ShieldCheck className="w-4 h-4 text-[var(--color-brand-primary)] shrink-0" />
        <span>{t.onboarding.firstRecordNote}</span>
      </div>

      {/* Month Selector */}
      <div className="max-w-[420px]">
        <MonthSelector
          value={month}
          onChange={(newMonth) => {
            setMonth(newMonth);
            if (errors.month) setErrors((prev) => ({ ...prev, month: "" }));
          }}
          error={errors.month}
          disabled={isLoading}
        />
      </div>

      {/* 5 Core Vitals Fields (Using CurrencyInput with plain-language labels) */}
      <div className="space-y-4 pt-1">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <CurrencyInput
            label={t.fields.cashInflow}
            helperText={t.fields.cashInflowHelp}
            value={cashInflow}
            onValueChange={(val) => {
              setCashInflow(val);
              if (errors.cashInflow) setErrors((prev) => ({ ...prev, cashInflow: "" }));
            }}
            error={errors.cashInflow}
            required
          />

          <CurrencyInput
            label={t.fields.cashOutflow}
            helperText={t.fields.cashOutflowHelp}
            value={cashOutflow}
            onValueChange={(val) => {
              setCashOutflow(val);
              if (errors.cashOutflow) setErrors((prev) => ({ ...prev, cashOutflow: "" }));
            }}
            error={errors.cashOutflow}
            required
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <CurrencyInput
            label={t.fields.revenue}
            helperText={t.fields.revenueHelp}
            value={revenue}
            onValueChange={(val) => {
              setRevenue(val);
              if (errors.revenue) setErrors((prev) => ({ ...prev, revenue: "" }));
            }}
            error={errors.revenue}
            required
          />

          <CurrencyInput
            label={t.fields.operatingExpenses}
            helperText={t.fields.operatingExpensesHelp}
            value={operatingExpenses}
            onValueChange={(val) => {
              setOperatingExpenses(val);
              if (errors.operatingExpenses) setErrors((prev) => ({ ...prev, operatingExpenses: "" }));
            }}
            error={errors.operatingExpenses}
            required
          />
        </div>

        <div className="sm:w-1/2 pe-0 sm:pe-2">
          <CurrencyInput
            label={t.fields.cashBalanceEom}
            helperText={t.fields.cashBalanceEomHelp}
            value={cashBalanceEom}
            onValueChange={(val) => {
              setCashBalanceEom(val);
              if (errors.cashBalanceEom) setErrors((prev) => ({ ...prev, cashBalanceEom: "" }));
            }}
            error={errors.cashBalanceEom}
            required
          />
        </div>
      </div>

      {/* Action Buttons */}
      <div className="pt-4 border-t border-[var(--color-border-subtle)] flex items-center justify-between gap-3">
        <Button
          type="button"
          variant="ghost"
          size="lg"
          onClick={onBack}
          disabled={isLoading}
          leftIcon={isRTL ? <ArrowRight className="w-4 h-4" /> : <ArrowLeft className="w-4 h-4" />}
        >
          {t.onboarding.back}
        </Button>

        <Button
          type="submit"
          variant="primary"
          size="lg"
          isLoading={isLoading}
          className="min-w-[220px]"
        >
          {t.onboarding.saveAndAnalyze}
        </Button>
      </div>
    </form>
  );
}
