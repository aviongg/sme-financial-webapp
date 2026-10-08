"use client";

import React, { useState } from "react";
import { Edit3, Camera, ArrowLeft, ArrowRight, Info, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { useLanguage } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";

export type IngestionChoice = "manual" | "upload";

export interface StepDataChoiceProps {
  onSelectChoice: (choice: IngestionChoice) => void;
  onBack: () => void;
}

export function StepDataChoice({ onSelectChoice, onBack }: StepDataChoiceProps) {
  const { t, direction } = useLanguage();
  const [selectedChoice, setSelectedChoice] = useState<IngestionChoice>("manual");
  const [noticeMessage, setNoticeMessage] = useState<string | null>(null);

  const handleChoiceClick = (choice: IngestionChoice) => {
    setSelectedChoice(choice);
    if (choice === "upload") {
      setNoticeMessage(t.onboarding.choiceUploadNotice);
    } else {
      setNoticeMessage(null);
    }
  };

  const handleContinue = () => {
    onSelectChoice(selectedChoice);
  };

  const isRTL = direction === "rtl";

  return (
    <div className="w-full space-y-6 text-start">
      {/* Step Header */}
      <div>
        <h1 className="text-[26px] sm:text-[30px] font-bold text-[var(--color-text-primary)] font-heading tracking-tight">
          {t.onboarding.step4Title}
        </h1>
        <p className="text-[14px] sm:text-[15px] text-[var(--color-text-muted)] mt-1.5 leading-relaxed">
          {t.onboarding.step4Subtitle}
        </p>
      </div>

      {/* Dual Path Decision Cards (Equal Visual Prominence per Section 10.1 & Prompt Section 7) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
        {/* Path A: Manual Entry */}
        <button
          type="button"
          onClick={() => handleChoiceClick("manual")}
          className={cn(
            "flex flex-col justify-between p-5 sm:p-6 rounded-[var(--radius-xl)] border text-start transition-all select-none focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]",
            selectedChoice === "manual"
              ? "border-[var(--color-brand-primary)] bg-[var(--color-brand-surface)] shadow-[var(--elevation-1)] ring-1 ring-[var(--color-brand-primary)]"
              : "border-[var(--color-border-default)] bg-[var(--color-surface-card)] hover:border-[var(--color-border-strong)] hover:bg-[var(--color-surface-hover)]"
          )}
        >
          <div className="space-y-3">
            <div className="w-12 h-12 rounded-[var(--radius-lg)] bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)] flex items-center justify-center text-[var(--color-brand-primary)]">
              <Edit3 className="w-6 h-6" />
            </div>

            <div>
              <p className="text-[16px] font-bold text-[var(--color-text-primary)] font-heading">
                {t.onboarding.choiceManualTitle}
              </p>
              <p className="text-[13px] text-[var(--color-text-secondary)] mt-1.5 leading-relaxed">
                {t.onboarding.choiceManualDesc}
              </p>
            </div>
          </div>

          <div className="pt-4 mt-4 border-t border-[var(--color-border-subtle)] flex items-center justify-between text-[13px] font-semibold text-[var(--color-brand-primary)]">
            <span>{t.onboarding.choiceManualAction}</span>
            {selectedChoice === "manual" && <CheckCircle2 className="w-4 h-4" />}
          </div>
        </button>

        {/* Path B: Camera / Document Upload */}
        <button
          type="button"
          onClick={() => handleChoiceClick("upload")}
          className={cn(
            "flex flex-col justify-between p-5 sm:p-6 rounded-[var(--radius-xl)] border text-start transition-all select-none focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]",
            selectedChoice === "upload"
              ? "border-[var(--color-brand-primary)] bg-[var(--color-brand-surface)] shadow-[var(--elevation-1)] ring-1 ring-[var(--color-brand-primary)]"
              : "border-[var(--color-border-default)] bg-[var(--color-surface-card)] hover:border-[var(--color-border-strong)] hover:bg-[var(--color-surface-hover)]"
          )}
        >
          <div className="space-y-3">
            <div className="w-12 h-12 rounded-[var(--radius-lg)] bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)] flex items-center justify-center text-[var(--color-brand-primary)]">
              <Camera className="w-6 h-6" />
            </div>

            <div>
              <p className="text-[16px] font-bold text-[var(--color-text-primary)] font-heading">
                {t.onboarding.choiceUploadTitle}
              </p>
              <p className="text-[13px] text-[var(--color-text-secondary)] mt-1.5 leading-relaxed">
                {t.onboarding.choiceUploadDesc}
              </p>
            </div>
          </div>

          <div className="pt-4 mt-4 border-t border-[var(--color-border-subtle)] flex items-center justify-between text-[13px] font-semibold text-[var(--color-brand-primary)]">
            <span>{t.onboarding.choiceUploadAction}</span>
            {selectedChoice === "upload" && <CheckCircle2 className="w-4 h-4" />}
          </div>
        </button>
      </div>

      {/* Upload Choice Informational Notice */}
      {noticeMessage && (
        <div className="flex items-start gap-3 p-3.5 rounded-[var(--radius-lg)] bg-[var(--color-status-info-bg)] border border-[var(--color-status-info-border)] text-[13px] text-[var(--color-status-info)]">
          <Info className="w-4 h-4 shrink-0 mt-0.5" />
          <span>{noticeMessage}</span>
        </div>
      )}

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
          onClick={handleContinue}
          className="min-w-[140px]"
        >
          {t.onboarding.continue}
        </Button>
      </div>
    </div>
  );
}
