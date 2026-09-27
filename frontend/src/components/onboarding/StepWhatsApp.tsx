"use client";

import React, { useId, useState } from "react";
import { Check, MessageSquare, ShieldCheck, ArrowLeft, ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useLanguage } from "@/lib/i18n/context";

export interface StepWhatsAppProps {
  optIn: boolean;
  phoneNumber: string;
  onOptInChange: (optIn: boolean) => void;
  onPhoneNumberChange: (phone: string) => void;
  onContinue: () => void;
  onBack: () => void;
}

export function StepWhatsApp({
  optIn,
  phoneNumber,
  onOptInChange,
  onPhoneNumberChange,
  onContinue,
  onBack,
}: StepWhatsAppProps) {
  const { t, direction } = useLanguage();
  const [phoneError, setPhoneError] = useState("");
  const phoneEntryId = useId();

  const handleValidateAndContinue = () => {
    if (optIn) {
      const sanitized = phoneNumber.replace(/[\s-]/g, "");
      // Validates Pakistani mobile numbers (03XXXXXXXXX or +923XXXXXXXXX or 923XXXXXXXXX)
      const pakPhoneRegex = /^((\+92)|(92)|(0))?3[0-9]{9}$/;
      if (!sanitized || !pakPhoneRegex.test(sanitized)) {
        setPhoneError(t.onboarding.whatsappPhoneError);
        return;
      }
    }
    setPhoneError("");
    onContinue();
  };

  const isRTL = direction === "rtl";

  return (
    <div className="w-full space-y-6 text-start">
      {/* Step Header */}
      <div>
        <h1 className="text-[26px] sm:text-[30px] font-bold text-[var(--color-text-primary)] font-heading tracking-tight">
          {t.onboarding.step3Title}
        </h1>
        <p className="text-[14px] sm:text-[15px] text-[var(--color-text-muted)] mt-1.5 leading-relaxed">
          {t.onboarding.step3Subtitle}
        </p>
      </div>

      {/* Feature Value Box */}
      <div className="p-4 rounded-[var(--radius-xl)] bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] space-y-3">
        <div className="flex items-start gap-3">
          <div className="w-8 h-8 rounded-[var(--radius-md)] bg-[#25D366]/10 text-[#128C7E] flex items-center justify-center shrink-0 mt-0.5">
            <MessageSquare className="w-4 h-4" />
          </div>
          <div className="text-[13px] text-[var(--color-text-secondary)] space-y-1">
            <p className="font-semibold text-[var(--color-text-primary)]">
              {direction === "rtl" ? "آپ کو کیا موصول ہوگا:" : "What you will receive:"}
            </p>
            <ul className="list-disc ps-4 space-y-0.5 text-[12px] text-[var(--color-text-muted)]">
              <li>{t.onboarding.whatsAppBenefit1}</li>
              <li>{t.onboarding.whatsAppBenefit2}</li>
              <li>{t.onboarding.whatsAppBenefit3}</li>
            </ul>
          </div>
        </div>

        <div className="flex items-center gap-2 pt-2 border-t border-[var(--color-border-subtle)] text-[12px] text-[var(--color-text-muted)]">
          <ShieldCheck className="w-4 h-4 text-[var(--color-brand-primary)] shrink-0" />
          <span>{t.onboarding.whatsAppPrivacyNote}</span>
        </div>
      </div>

      {/* Explicit Opt-In Checkbox (Not Pre-checked per Section 6 & 10) */}
      <div className="rounded-[var(--radius-lg)] border border-[var(--color-border-default)] bg-[var(--color-surface-card)]">
        <label className="flex items-start gap-2.5 p-4 cursor-pointer rounded-[var(--radius-lg)] select-none focus-within:ring-2 focus-within:ring-[var(--color-brand-primary)] focus-within:ring-offset-2">
          <input
            type="checkbox"
            className="sr-only"
            checked={optIn}
            aria-controls={phoneEntryId}
            onChange={(e) => {
              onOptInChange(e.target.checked);
              if (!e.target.checked) setPhoneError("");
            }}
          />
          <span
            aria-hidden="true"
            className={`w-4 h-4 mt-0.5 shrink-0 rounded-[var(--radius-sm)] border flex items-center justify-center ${optIn ? "bg-[var(--color-brand-primary)] border-[var(--color-brand-primary)]" : "bg-[var(--color-surface-card)] border-[var(--color-border-strong)]"}`}
          >
            {optIn && <Check className="w-3 h-3 text-white" strokeWidth={3} />}
          </span>
          <span className="text-[14px] leading-tight text-[var(--color-text-primary)]">
            <span className="font-medium">{t.onboarding.whatsappOptInLabel}</span>
            <span className="block text-[12px] text-[var(--color-text-muted)] mt-0.5">
              {t.onboarding.whatsAppOptInDescription}
            </span>
          </span>
        </label>

        {/* Conditional Phone Field when Opted In */}
        {optIn && (
          <div id={phoneEntryId} className="mx-4 mb-4 pt-3 border-t border-[var(--color-border-subtle)] space-y-2">
            <Input
              label={t.onboarding.whatsappPhoneLabel}
              placeholder={t.onboarding.whatsappPhonePlaceholder}
              helperText={t.onboarding.whatsappPhoneHelp}
              value={phoneNumber}
              onChange={(e) => {
                onPhoneNumberChange(e.target.value);
                if (phoneError) setPhoneError("");
              }}
              error={phoneError}
              inputMode="tel"
              dir="ltr"
              required
            />
          </div>
        )}
      </div>

      {/* Action Buttons */}
      <div className="pt-4 border-t border-[var(--color-border-subtle)] flex items-center justify-between gap-3">
        <Button
          variant="ghost"
          size="lg"
          onClick={onBack}
          leftIcon={isRTL ? <ArrowRight className="w-4 h-4" /> : <ArrowLeft className="w-4 h-4" />}
        >
          {t.onboarding.back}
        </Button>

        <Button
          variant="primary"
          size="lg"
          onClick={handleValidateAndContinue}
          className="min-w-[140px]"
        >
          {t.onboarding.continue}
        </Button>
      </div>
    </div>
  );
}
