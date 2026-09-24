"use client";

import React, { useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, ArrowRight, AlertTriangle, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Divider } from "@/components/ui/Divider";
import { AlertBanner } from "@/components/ui/AlertBanner";
import { Dialog } from "@/components/ui/Dialog";
import { useToast } from "@/components/ui/Toast";
import { useLanguage } from "@/lib/i18n/context";
import { formatPKR } from "@/lib/utils/currency";
import { mockApi } from "@/lib/api/adapter";
import { ApiError } from "@/lib/api/client";
import { isDemoMode } from "@/lib/api/config";
import { phaseOneApi } from "@/lib/api/phase-one";
import { getUserId } from "@/lib/api/session";
import type { MonthlyRecordRequest as BackendMonthlyRecordRequest } from "@/lib/api/contracts";
import { MonthSelector } from "./MonthSelector";
import {
  CoreVitalsSection,
  type CoreVitalsValues,
  type CoreVitalsErrors,
} from "./CoreVitalsSection";
import {
  PrecisionBoostersSection,
  type PrecisionBoostersValues,
  type PrecisionBoostersErrors,
} from "./PrecisionBoostersSection";
import type {
  MonthlyRecordResponse,
  FinancingType,
} from "@/types/financial";

export interface MonthlyRecordFormProps {
  initialData?: Partial<MonthlyRecordResponse>;
  isEditMode?: boolean;
  className?: string;
}

/**
 * MonthlyRecordForm
 * Coherent financial worksheet (~860px max width).
 * Strictly manages form validation, non-blocking outflow warning confirmation flow,
 * preserving missing optional fields as null, and saving via typed API adapter.
 */
export function MonthlyRecordForm({
  initialData,
  isEditMode = false,
  className,
}: MonthlyRecordFormProps) {
  const router = useRouter();
  const { t, direction, locale } = useLanguage();
  const isRTL = direction === "rtl";
  const { toast } = useToast();

  // Helper to format default month (current or initial)
  const defaultMonth = () => {
    if (initialData?.month) return initialData.month;
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, "0");
    return `${y}-${m}`;
  };

  // Form State
  const [month, setMonth] = useState<string>(defaultMonth());

  const [coreValues, setCoreValues] = useState<CoreVitalsValues>({
    cashInflow: initialData?.cashInflow ?? null,
    cashOutflow: initialData?.cashOutflow ?? null,
    revenue: initialData?.revenue ?? null,
    operatingExpenses: initialData?.operatingExpenses ?? null,
    cashBalanceEom: initialData?.cashBalanceEom ?? null,
  });

  const [precisionValues, setPrecisionValues] = useState<PrecisionBoostersValues>({
    cogs: initialData?.cogs ?? null,
    receivablesOutstanding: initialData?.receivablesOutstanding ?? null,
    payablesOutstanding: initialData?.payablesOutstanding ?? null,
    inventoryValue: initialData?.inventoryValue ?? null,
    loanOutstanding: initialData?.loanOutstanding ?? null,
    interestExpense: initialData?.interestExpense ?? null,
    financingType: (initialData?.financingType as FinancingType) || "none",
  });

  // Validation State
  const [coreErrors, setCoreErrors] = useState<CoreVitalsErrors>({});
  const [precisionErrors, setPrecisionErrors] = useState<PrecisionBoostersErrors>({});
  const [monthError, setMonthError] = useState<string | undefined>();
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Submission & Warning State
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isConfirmDialogOpen, setIsConfirmDialogOpen] = useState(false);
  const [hasConfirmedWarning, setHasConfirmedWarning] = useState(false);
  const [existingMonth, setExistingMonth] = useState<string | null>(null);
  const submissionLock = useRef(false);

  // Field change handlers
  const handleCoreChange = <K extends keyof CoreVitalsValues>(
    field: K,
    val: number | null
  ) => {
    setCoreValues((prev) => ({ ...prev, [field]: val }));
    setHasConfirmedWarning(false);
    // Clear error for field on change
    if (coreErrors[field]) {
      setCoreErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  };

  const handlePrecisionChange = <K extends keyof PrecisionBoostersValues>(
    field: K,
    val: PrecisionBoostersValues[K]
  ) => {
    setPrecisionValues((prev) => ({ ...prev, [field]: val }));
    if (precisionErrors[field]) {
      setPrecisionErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  };

  const handleMonthChange = (newMonth: string) => {
    setMonth(newMonth);
    setExistingMonth(null);
    if (monthError) setMonthError(undefined);
  };

  // Check outflow sanity check condition:
  // 1. Non-zero inflow where outflow exceeds 3x inflow, OR
  // 2. Zero inflow with active positive outflow (severe cash burn)
  const hasOutflowWarning =
    coreValues.cashInflow !== null &&
    coreValues.cashOutflow !== null &&
    ((coreValues.cashInflow > 0 && coreValues.cashOutflow > coreValues.cashInflow * 3) ||
     (coreValues.cashInflow === 0 && coreValues.cashOutflow > 0));

  // Validation logic
  const validateForm = (): boolean => {
    let isValid = true;
    const newCoreErrors: CoreVitalsErrors = {};
    const newPrecisionErrors: PrecisionBoostersErrors = {};

    // Validate Month
    if (!month || !/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
      setMonthError(locale === "ur" ? "براہ کرم درست مہینہ منتخب کریں۔" : "Please select a valid month (YYYY-MM).");
      isValid = false;
    } else {
      setMonthError(undefined);
    }

    // Validate 5 Core Vitals (Required, non-negative, zero allowed)
    const requiredFields: (keyof CoreVitalsValues)[] = [
      "cashInflow",
      "cashOutflow",
      "revenue",
      "operatingExpenses",
      "cashBalanceEom",
    ];

    for (const field of requiredFields) {
      const val = coreValues[field];
      if (val === null || val === undefined || !Number.isFinite(val)) {
        newCoreErrors[field] = t.records.validationRequired;
        isValid = false;
      } else if (val < 0) {
        newCoreErrors[field] = t.records.validationNonNegative;
        isValid = false;
      }
    }

    // Validate Precision Boosters (Optional, but if entered must be non-negative)
    const optionalNumericFields: (keyof Omit<PrecisionBoostersValues, "financingType">)[] = [
      "cogs",
      "receivablesOutstanding",
      "payablesOutstanding",
      "inventoryValue",
      "loanOutstanding",
      "interestExpense",
    ];

    for (const field of optionalNumericFields) {
      const val = precisionValues[field];
      if (val !== null && val !== undefined) {
        if (!Number.isFinite(val)) {
          newPrecisionErrors[field] = t.records.validationRequired;
          isValid = false;
        } else if (val < 0) {
          newPrecisionErrors[field] = t.records.validationNonNegative;
          isValid = false;
        }
      }
    }

    setCoreErrors(newCoreErrors);
    setPrecisionErrors(newPrecisionErrors);

    if (!isValid) {
      setSubmitError(t.records.saveError);
    } else {
      setSubmitError(null);
    }

    return isValid;
  };

  // Save implementation
  const executeSave = async () => {
    if (submissionLock.current) return;
    const userId = isDemoMode ? (initialData?.userId || "bp-1001") : getUserId();
    if (!userId) {
      setSubmitError(locale === "ur" ? "پہلے اپنا پروفائل بنائیں یا کھولیں۔" : "Set up or open your profile before saving a record.");
      return;
    }
    submissionLock.current = true;
    setIsSubmitting(true);
    setSubmitError(null);
    setExistingMonth(null);

    const payload: BackendMonthlyRecordRequest = {
      userId,
      month,
      cashInflow: Number(coreValues.cashInflow),
      cashOutflow: Number(coreValues.cashOutflow),
      revenue: Number(coreValues.revenue),
      operatingExpenses: Number(coreValues.operatingExpenses),
      cashBalanceEom: Number(coreValues.cashBalanceEom),

      // Optional fields: preserve null if not provided
      cogs: precisionValues.cogs !== null ? Number(precisionValues.cogs) : null,
      receivablesOutstanding:
        precisionValues.receivablesOutstanding !== null
          ? Number(precisionValues.receivablesOutstanding)
          : null,
      payablesOutstanding:
        precisionValues.payablesOutstanding !== null
          ? Number(precisionValues.payablesOutstanding)
          : null,
      inventoryValue:
        precisionValues.inventoryValue !== null
          ? Number(precisionValues.inventoryValue)
          : null,
      loanOutstanding:
        precisionValues.loanOutstanding !== null
          ? Number(precisionValues.loanOutstanding)
          : null,
      interestExpense:
        precisionValues.interestExpense !== null
          ? Number(precisionValues.interestExpense)
          : null,
      financingType: precisionValues.financingType || "none",
    };

    try {
      if (isDemoMode) {
        await mockApi.saveMonthlyRecord(payload);
      } else if (isEditMode) {
        await phaseOneApi.updateMonthlyRecord(userId, month, payload);
      } else {
        // POST is an upsert on this backend. Direct the user to the edit screen
        // before replacing a month they may have saved in another session.
        const records = await phaseOneApi.getMonthlyRecords(userId);
        if (records.some((record) => record.month === month)) {
          setExistingMonth(month);
          setSubmitError(locale === "ur" ? "اس مہینے کا ریکارڈ پہلے سے موجود ہے۔ اسے کھول کر ترمیم کریں۔" : "A record already exists for this month. Open it to review and edit the saved values.");
          return;
        }
        await phaseOneApi.createMonthlyRecord(payload);
      }
      toast(t.records.saveSuccess, "success");
      router.push(isDemoMode ? "/" : "/records");
    } catch (cause) {
      if (cause instanceof ApiError) {
        const fields = { ...cause.fieldErrors };
        // The standalone Fatima service uses a message-only error for required COGS.
        if (cause.status === 400 && cause.message === "COGS is required") fields.cogs = cause.message;
        setMonthError(fields.month);
        setCoreErrors(Object.fromEntries(Object.keys(coreValues).filter((key) => fields[key]).map((key) => [key, fields[key]])));
        setPrecisionErrors(Object.fromEntries(Object.keys(precisionValues).filter((key) => fields[key]).map((key) => [key, fields[key]])));
        setSubmitError(fields.cogs
          ? (locale === "ur" ? "اس وقت ریکارڈ محفوظ کرنے کے لیے فروخت شدہ مال کی لاگت درج کرنا ضروری ہے۔ نیچے متعلقہ خانہ مکمل کریں۔" : "Saving currently requires cost of goods sold. Enter that amount in the optional details below, then try again.")
          : cause.status === 404
            ? (locale === "ur" ? "پروفائل یا ریکارڈ نہیں ملا۔ اپنا پروفائل دوبارہ کھولیں۔" : "The profile or record was not found. Open your profile again.")
            : t.records.saveError);
      } else {
        setSubmitError(t.records.saveError);
      }
    } finally {
      submissionLock.current = false;
      setIsSubmitting(false);
    }
  };

  // Form submit handler with non-blocking confirmation dialog
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const isValid = validateForm();
    if (!isValid) return;

    // Check if cash outflow warning is active and hasn't been confirmed yet
    if (hasOutflowWarning && !hasConfirmedWarning) {
      setIsConfirmDialogOpen(true);
      return;
    }

    await executeSave();
  };

  // User confirms outflow warning in modal
  const handleConfirmWarning = async () => {
    setIsConfirmDialogOpen(false);
    setHasConfirmedWarning(true);
    await executeSave();
  };

  return (
    <div className={className}>
      <form onSubmit={handleSubmit} noValidate className="space-y-8">
        {/* Navigation & Header */}
        <div className="space-y-4 text-start">
          <Link
            href={isDemoMode ? "/" : "/records"}
            className="inline-flex items-center gap-1.5 text-[13px] font-medium text-[var(--color-brand-primary)] hover:underline"
          >
            {isRTL ? (
              <ArrowRight className="w-4 h-4" />
            ) : (
              <ArrowLeft className="w-4 h-4" />
            )}
            <span>{isDemoMode ? t.records.backToDashboard : (locale === "ur" ? "ماہانہ ریکارڈ پر واپس جائیں" : "Back to monthly records")}</span>
          </Link>

          <div>
            <h1 className="text-[24px] sm:text-[28px] font-bold text-[var(--color-text-primary)] font-heading tracking-tight">
              {isEditMode ? t.records.editTitle : t.records.newTitle}
            </h1>
            <p className="text-[14px] text-[var(--color-text-muted)] mt-1.5 leading-relaxed">
              {isEditMode ? t.records.editSubtitle : t.records.newSubtitle}
            </p>
          </div>
        </div>

        <Divider />

        {/* Month Selector */}
        <MonthSelector
          value={month}
          onChange={handleMonthChange}
          error={monthError}
          disabled={isSubmitting || (!isDemoMode && isEditMode)}
        />

        <Divider />

        {/* Section 1: Core Monthly Vitals (Visually Dominant) */}
        <CoreVitalsSection
          values={coreValues}
          onChange={handleCoreChange}
          errors={coreErrors}
          disabled={isSubmitting}
        />

        <Divider />

        {/* Section 2: Sharpen Your Financial Profile (Visually Secondary) */}
        <PrecisionBoostersSection
          values={precisionValues}
          onChange={handlePrecisionChange}
          errors={precisionErrors}
          disabled={isSubmitting}
        />

        {/* Non-Blocking Cash Outflow Inline Warning Banner */}
        {hasOutflowWarning && (
          <AlertBanner variant="warning" title={t.records.outflowWarningTitle}>
            <p>{t.records.outflowWarning}</p>
          </AlertBanner>
        )}

        {/* Form Submission Error Banner */}
        {submitError && (
          <AlertBanner variant="error">
            <p>{submitError}</p>
            {existingMonth && <Link className="underline font-semibold" href={`/records/${existingMonth}`}>{locale === "ur" ? "موجودہ ریکارڈ کھولیں" : "Open existing record"}</Link>}
          </AlertBanner>
        )}

        {/* Actions Bar */}
        <div className="flex items-center justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
          <Link href={isDemoMode ? "/" : "/records"}>
            <Button
              type="button"
              variant="secondary"
              disabled={isSubmitting}
              className="px-5"
            >
              {t.records.cancelButton}
            </Button>
          </Link>

          <Button
            type="submit"
            variant="primary"
            isLoading={isSubmitting}
            disabled={isSubmitting}
            className="px-6"
            leftIcon={
              !isSubmitting ? <CheckCircle2 className="w-4 h-4" /> : undefined
            }
          >
            {isSubmitting ? t.records.savingButton : t.records.saveButton}
          </Button>
        </div>
      </form>

      {/* Non-Blocking Outflow Warning Confirmation Dialog */}
      <Dialog
        isOpen={isConfirmDialogOpen}
        onClose={() => setIsConfirmDialogOpen(false)}
        title={t.records.outflowWarningTitle}
        maxWidth="md"
      >
        <div className="space-y-4 text-start">
          <div className="flex items-start gap-3 p-3.5 rounded-[var(--radius-lg)] bg-[var(--color-status-warning-bg)] border border-[var(--color-status-warning-border)]">
            <AlertTriangle className="w-5 h-5 text-[var(--color-status-warning)] shrink-0 mt-0.5" />
            <div className="text-[13px] text-[var(--color-text-primary)] space-y-1.5">
              <p className="font-semibold">{t.records.outflowWarningDescription}</p>
              <div className="grid grid-cols-2 gap-2 text-[12px] pt-1 border-t border-[var(--color-status-warning-border)]">
                <div>
                  <span className="text-[var(--color-text-muted)] block">
                    {t.fields.cashInflow}:
                  </span>
                  <span className="font-semibold tabular-nums">
                    {formatPKR(coreValues.cashInflow, { locale })}
                  </span>
                </div>
                <div>
                  <span className="text-[var(--color-text-muted)] block">
                    {t.fields.cashOutflow}:
                  </span>
                  <span className="font-semibold tabular-nums text-[var(--color-status-warning)]">
                    {formatPKR(coreValues.cashOutflow, { locale })}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <p className="text-[13px] text-[var(--color-text-muted)]">
            {t.records.outflowWarning}
          </p>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-[var(--color-border-subtle)]">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setIsConfirmDialogOpen(false)}
            >
              {t.records.outflowWarningReview}
            </Button>
            <Button
              type="button"
              variant="primary"
              onClick={handleConfirmWarning}
            >
              {t.records.outflowWarningConfirm}
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
