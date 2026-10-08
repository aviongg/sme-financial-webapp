"use client";

import React from "react";
import Link from "next/link";
import { Globe } from "lucide-react";
import { useLanguage } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";

export interface OnboardingHeaderProps {
  currentStep: number;
  totalSteps: number;
}

export function OnboardingHeader({
  currentStep,
  totalSteps,
}: OnboardingHeaderProps) {
  const { locale, toggleLocale, t } = useLanguage();

  const stepNames = [
    t.nav.settings, // Language/Setup
    "Business",
    "WhatsApp",
    "Choice",
    "Vitals",
    "Result",
  ];

  return (
    <header className="w-full border-b border-[var(--color-border-default)] bg-[var(--color-surface-card)] sticky top-0 z-30">
      <div className="max-w-[800px] mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
        {/* Authoritative FinSight Logo */}
        <Link
          href="/"
          className="flex items-center gap-2.5 focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] rounded-[var(--radius-sm)] select-none shrink-0"
        >
          {/* Blue Growth Bars + Navy Wordmark */}
          <div className="flex items-end gap-[3px] h-6 shrink-0" aria-hidden="true">
            <span className="w-[5px] h-3 bg-[#4F6BF5] rounded-full" />
            <span className="w-[5px] h-4.5 bg-[#3B54E6] rounded-full" />
            <div className="flex flex-col items-center gap-[2px]">
              <span className="w-[5px] h-[5px] bg-[#60A5FA] rounded-full" />
              <span className="w-[5px] h-6 bg-[#253DBA] rounded-full" />
            </div>
          </div>

          <div className="flex flex-col leading-none">
            <span className="font-heading font-extrabold text-[18px] tracking-tight text-[#030E2E]">
              FinSight
            </span>
            <span className="text-[10px] text-[var(--color-text-muted)] font-medium tracking-wide uppercase mt-0.5">
              Setup
            </span>
          </div>
        </Link>

        {/* Subtle Stepped Progress Indicator (Accessible & RTL Friendly) */}
        <div
          className="flex items-center gap-1.5 sm:gap-2 select-none"
          role="progressbar"
          aria-valuenow={currentStep}
          aria-valuemin={1}
          aria-valuemax={totalSteps}
          aria-label={t.onboarding.stepOf
            .replace("{current}", String(currentStep))
            .replace("{total}", String(totalSteps))}
        >
          <div className="flex items-center gap-1">
            {Array.from({ length: totalSteps }, (_, i) => {
              const stepNumber = i + 1;
              const isCompleted = stepNumber < currentStep;
              const isCurrent = stepNumber === currentStep;

              return (
                <div
                  key={stepNumber}
                  className={cn(
                    "h-1.5 rounded-full transition-all duration-200",
                    isCurrent
                      ? "w-6 sm:w-8 bg-[var(--color-brand-primary)]"
                      : isCompleted
                      ? "w-2.5 sm:w-3.5 bg-[var(--color-brand-primary)]/40"
                      : "w-2.5 sm:w-3.5 bg-[var(--color-border-strong)]"
                  )}
                  title={stepNames[i] || `Step ${stepNumber}`}
                />
              );
            })}
          </div>

          <span className="text-[12px] font-medium text-[var(--color-text-secondary)] ms-2 whitespace-nowrap">
            {t.onboarding.stepOf
              .replace("{current}", String(currentStep))
              .replace("{total}", String(totalSteps))}
          </span>
        </div>

        {/* Language Switcher */}
        <button
          type="button"
          onClick={toggleLocale}
          aria-label="Switch language"
          className="flex items-center gap-1.5 h-8 px-2.5 rounded-[var(--radius-md)] border border-[var(--color-border-default)] hover:bg-[var(--color-surface-hover)] text-[12px] font-semibold text-[var(--color-brand-primary)] transition-colors focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] shrink-0"
        >
          <Globe className="w-3.5 h-3.5" />
          <span>{locale === "en" ? "اردو" : "English"}</span>
        </button>
      </div>
    </header>
  );
}
