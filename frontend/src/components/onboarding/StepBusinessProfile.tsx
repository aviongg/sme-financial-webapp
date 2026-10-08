"use client";

import React, { useState } from "react";
import { ArrowLeft, ArrowRight, Store, Factory, Briefcase, ShoppingCart } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useLanguage } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";

export type BusinessType = "trade" | "manufacturing" | "services" | "retail";

export interface StepBusinessProfileProps {
  businessName: string;
  businessType: BusinessType | "";
  onNameChange: (name: string) => void;
  onTypeChange: (type: BusinessType) => void;
  onContinue: () => void;
  onBack: () => void;
}

export function StepBusinessProfile({
  businessName,
  businessType,
  onNameChange,
  onTypeChange,
  onContinue,
  onBack,
}: StepBusinessProfileProps) {
  const { t, direction } = useLanguage();
  const [nameError, setNameError] = useState("");
  const [typeError, setTypeError] = useState("");

  const businessTypes: Array<{
    id: BusinessType;
    title: string;
    description: string;
    icon: React.ComponentType<{ className?: string }>;
  }> = [
    {
      id: "trade",
      title: t.onboarding.typeTradeTitle,
      description: t.onboarding.typeTradeDesc,
      icon: Store,
    },
    {
      id: "manufacturing",
      title: t.onboarding.typeMfgTitle,
      description: t.onboarding.typeMfgDesc,
      icon: Factory,
    },
    {
      id: "services",
      title: t.onboarding.typeServicesTitle,
      description: t.onboarding.typeServicesDesc,
      icon: Briefcase,
    },
    {
      id: "retail",
      title: t.onboarding.typeRetailTitle,
      description: t.onboarding.typeRetailDesc,
      icon: ShoppingCart,
    },
  ];

  const handleValidateAndContinue = () => {
    let isValid = true;

    if (!businessName.trim()) {
      setNameError(t.onboarding.businessNameError);
      isValid = false;
    } else {
      setNameError("");
    }

    if (!businessType) {
      setTypeError(t.onboarding.businessTypeError);
      isValid = false;
    } else {
      setTypeError("");
    }

    if (isValid) {
      onContinue();
    }
  };

  const isRTL = direction === "rtl";

  return (
    <div className="w-full space-y-6 text-start">
      {/* Step Header */}
      <div>
        <h1 className="text-[26px] sm:text-[30px] font-bold text-[var(--color-text-primary)] font-heading tracking-tight">
          {t.onboarding.step2Title}
        </h1>
        <p className="text-[14px] sm:text-[15px] text-[var(--color-text-muted)] mt-1.5 leading-relaxed">
          {t.onboarding.step2Subtitle}
        </p>
      </div>

      {/* Business Name Input */}
      <div>
        <Input
          label={t.onboarding.businessNameLabel}
          placeholder={t.onboarding.businessNamePlaceholder}
          value={businessName}
          onChange={(e) => {
            onNameChange(e.target.value);
            if (nameError) setNameError("");
          }}
          error={nameError}
          required
        />
      </div>

      {/* Business Type Selection */}
      <div className="space-y-2">
        <label className="text-[13px] font-semibold text-[var(--color-text-secondary)] select-none">
          {t.onboarding.businessTypeLabel}
          <span className="text-[var(--color-status-error)] ms-1">*</span>
        </label>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3" role="radiogroup" aria-label={t.onboarding.businessTypeLabel}>
          {businessTypes.map((item) => {
            const isSelected = businessType === item.id;
            const Icon = item.icon;

            return (
              <button
                key={item.id}
                type="button"
                role="radio"
                aria-checked={isSelected}
                onClick={() => {
                  onTypeChange(item.id);
                  if (typeError) setTypeError("");
                }}
                className={cn(
                  "flex items-start gap-3 p-3.5 rounded-[var(--radius-lg)] border text-start transition-all select-none focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]",
                  isSelected
                    ? "border-[var(--color-brand-primary)] bg-[var(--color-brand-surface)] shadow-[var(--elevation-1)] ring-1 ring-[var(--color-brand-primary)]"
                    : "border-[var(--color-border-default)] bg-[var(--color-surface-card)] hover:border-[var(--color-border-strong)] hover:bg-[var(--color-surface-hover)]"
                )}
              >
                <div
                  className={cn(
                    "w-8 h-8 rounded-[var(--radius-md)] flex items-center justify-center shrink-0 mt-0.5 transition-colors",
                    isSelected
                      ? "bg-[var(--color-brand-primary)] text-white"
                      : "bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)]"
                  )}
                >
                  <Icon className="w-4 h-4" />
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <p className="text-[14px] font-bold text-[var(--color-text-primary)] font-heading">
                      {item.title}
                    </p>
                  </div>
                  <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5 leading-snug">
                    {item.description}
                  </p>
                </div>
              </button>
            );
          })}
        </div>

        {typeError && (
          <p className="text-[12px] font-medium text-[var(--color-status-error)] mt-1" role="alert">
            {typeError}
          </p>
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
