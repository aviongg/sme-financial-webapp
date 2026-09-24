"use client";

import React from "react";
import { Check } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { useLanguage, type Locale } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";

export interface StepLanguageProps {
  onContinue: () => void;
}

export function StepLanguage({ onContinue }: StepLanguageProps) {
  const { locale, setLocale, t } = useLanguage();

  const handleSelectLanguage = (selected: Locale) => {
    setLocale(selected);
  };

  return (
    <div className="w-full space-y-6 text-start">
      {/* Step Header */}
      <div>
        <h1 className="text-[26px] sm:text-[30px] font-bold text-[var(--color-text-primary)] font-heading tracking-tight">
          {t.onboarding.step1Title}
        </h1>
        <p className="text-[14px] sm:text-[15px] text-[var(--color-text-muted)] mt-1.5 leading-relaxed">
          {t.onboarding.step1Subtitle}
        </p>
      </div>

      {/* Language Selection Cards */}
      <div className="grid grid-cols-1 gap-3.5 pt-2" role="radiogroup" aria-label="Language selection">
        {/* English Card */}
        <button
          type="button"
          role="radio"
          aria-checked={locale === "en"}
          onClick={() => handleSelectLanguage("en")}
          className={cn(
            "w-full flex items-start justify-between p-4 sm:p-5 rounded-[var(--radius-xl)] border transition-all text-start select-none focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]",
            locale === "en"
              ? "border-[var(--color-brand-primary)] bg-[var(--color-brand-surface)] shadow-[var(--elevation-1)] ring-1 ring-[var(--color-brand-primary)]"
              : "border-[var(--color-border-default)] bg-[var(--color-surface-card)] hover:border-[var(--color-border-strong)] hover:bg-[var(--color-surface-hover)]"
          )}
        >
          <div className="space-y-1 pe-4">
            <p className="text-[16px] font-bold text-[var(--color-text-primary)] font-heading">
              {t.onboarding.langEnTitle}
            </p>
            <p className="text-[13px] text-[var(--color-text-secondary)] leading-normal">
              {t.onboarding.langEnDesc}
            </p>
          </div>

          <div
            className={cn(
              "w-5 h-5 rounded-full border flex items-center justify-center shrink-0 mt-0.5 transition-colors",
              locale === "en"
                ? "border-[var(--color-brand-primary)] bg-[var(--color-brand-primary)] text-white"
                : "border-[var(--color-border-strong)] bg-white"
            )}
            aria-hidden="true"
          >
            {locale === "en" && <Check className="w-3.5 h-3.5 stroke-[3]" />}
          </div>
        </button>

        {/* Urdu Card */}
        <button
          type="button"
          role="radio"
          aria-checked={locale === "ur"}
          onClick={() => handleSelectLanguage("ur")}
          className={cn(
            "w-full flex items-start justify-between p-4 sm:p-5 rounded-[var(--radius-xl)] border transition-all text-start select-none focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]",
            locale === "ur"
              ? "border-[var(--color-brand-primary)] bg-[var(--color-brand-surface)] shadow-[var(--elevation-1)] ring-1 ring-[var(--color-brand-primary)]"
              : "border-[var(--color-border-default)] bg-[var(--color-surface-card)] hover:border-[var(--color-border-strong)] hover:bg-[var(--color-surface-hover)]"
          )}
        >
          <div className="space-y-1 pe-4">
            <p className="text-[16px] font-bold text-[var(--color-text-primary)] font-heading">
              {t.onboarding.langUrTitle}
            </p>
            <p className="text-[13px] text-[var(--color-text-secondary)] leading-[1.8] font-urdu">
              {t.onboarding.langUrDesc}
            </p>

          </div>

          <div
            className={cn(
              "w-5 h-5 rounded-full border flex items-center justify-center shrink-0 mt-0.5 transition-colors",
              locale === "ur"
                ? "border-[var(--color-brand-primary)] bg-[var(--color-brand-primary)] text-white"
                : "border-[var(--color-border-strong)] bg-white"
            )}
            aria-hidden="true"
          >
            {locale === "ur" && <Check className="w-3.5 h-3.5 stroke-[3]" />}
          </div>
        </button>
      </div>

      {/* Action CTA */}
      <div className="pt-4 border-t border-[var(--color-border-subtle)] flex justify-end">
        <Button variant="primary" size="lg" onClick={onContinue} className="w-full sm:w-auto min-w-[140px]">
          {t.onboarding.continue}
        </Button>
      </div>
    </div>
  );
}
