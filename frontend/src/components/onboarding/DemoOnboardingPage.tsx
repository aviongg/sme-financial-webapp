"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import { OnboardingHeader } from "@/components/onboarding/OnboardingHeader";
import { StepLanguage } from "@/components/onboarding/StepLanguage";
import { StepBusinessProfile, type BusinessType } from "@/components/onboarding/StepBusinessProfile";
import { StepWhatsApp } from "@/components/onboarding/StepWhatsApp";
import { StepDataChoice, type IngestionChoice } from "@/components/onboarding/StepDataChoice";
import { StepFirstRecord } from "@/components/onboarding/StepFirstRecord";
import { StepFirstResult } from "@/components/onboarding/StepFirstResult";
import { Container } from "@/components/ui/Container";
import { AlertBanner } from "@/components/ui/AlertBanner";
import { mockApi, mockProvisionalScoreResult } from "@/lib/api/adapter";
import { useToast } from "@/components/ui/Toast";
import type { MonthlyRecordRequest, ScoreResult, BusinessProfile } from "@/types/financial";

const TOTAL_STEPS = 6;
const ONBOARDING_COMPLETED_KEY = "finsight_onboarding_completed";

export default function OnboardingPage() {
  const router = useRouter();
  const { toast } = useToast();

  const [step, setStep] = useState<number>(1);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [generalError, setGeneralError] = useState<string | null>(null);

  // Form states preserved across steps
  const [businessName, setBusinessName] = useState<string>("Al-Rehman Textiles");
  const [businessType, setBusinessType] = useState<BusinessType | "">("trade");
  const [whatsappOptIn, setWhatsappOptIn] = useState<boolean>(false);
  const [whatsappNumber, setWhatsappNumber] = useState<string>("");
  const [, setIngestionChoice] = useState<IngestionChoice>("manual");
  const [monthlyRecord, setMonthlyRecord] = useState<Partial<MonthlyRecordRequest>>({
    month: "2026-08",
    cashInflow: 1850000,
    cashOutflow: 1420000,
    revenue: 2100000,
    operatingExpenses: 450000,
    cashBalanceEom: 980000,
  });

  const [scoreResult, setScoreResult] = useState<ScoreResult>(mockProvisionalScoreResult);

  // Navigation handlers
  const handleNext = () => {
    setGeneralError(null);
    setStep((prev) => Math.min(prev + 1, TOTAL_STEPS));
  };

  const handleBack = () => {
    setGeneralError(null);
    setStep((prev) => Math.max(prev - 1, 1));
  };

  // Step 3 completion -> Save profile
  const handleProfileStepComplete = async () => {
    setIsLoading(true);
    setGeneralError(null);
    try {
      const profileData: Partial<BusinessProfile> = {
        businessName,
        businessType: businessType || "trade",
        whatsappOptIn,
        whatsappNumber: whatsappOptIn ? whatsappNumber : undefined,
      };

      await mockApi.saveBusinessProfile(profileData);
      setStep(4);
    } catch {
      setGeneralError("We couldn't save your profile details right now. Please try again.");
    } finally {
      setIsLoading(false);
    }
  };

  // Step 4 choice
  const handleDataChoice = (choice: IngestionChoice) => {
    setIngestionChoice(choice);
    setStep(5);
  };

  // Step 5 completion -> Save record and fetch initial score
  const handleFirstRecordSubmit = async (record: MonthlyRecordRequest) => {
    setIsLoading(true);
    setGeneralError(null);
    try {
      setMonthlyRecord(record);
      await mockApi.saveMonthlyRecord(record);

      // Fetch initial provisional score from backend/mock (Zero client-side computation!)
      const result = await mockApi.getProvisionalScoreResult();
      setScoreResult(result);

      toast("First monthly vitals saved successfully.", "success");
      setStep(6);
    } catch {
      setGeneralError("We were unable to save your monthly record. Your entries remain preserved.");
    } finally {
      setIsLoading(false);
    }
  };

  // Step 6 completion -> Dashboard
  const handleCompleteToDashboard = () => {
    try {
      localStorage.setItem(ONBOARDING_COMPLETED_KEY, "true");
    } catch {
      // Storage fallback
    }
    toast("Welcome to FinSight! Your financial dashboard is ready.", "success");
    router.push("/");
  };

  return (
    <div className="min-h-screen bg-[var(--color-surface-canvas)] flex flex-col text-[var(--color-text-primary)]">
      {/* Distraction-free Header */}
      <OnboardingHeader currentStep={step} totalSteps={TOTAL_STEPS} />

      {/* Main Form Body */}
      <main className="flex-1 py-8 sm:py-12 flex items-start justify-center">
        <Container width="reading" className="max-w-[580px] w-full">
          {generalError && (
            <div className="mb-6">
              <AlertBanner
                variant="error"
                onDismiss={() => setGeneralError(null)}
              >
                {generalError}
              </AlertBanner>
            </div>
          )}

          {step === 1 && <StepLanguage onContinue={handleNext} />}

          {step === 2 && (
            <StepBusinessProfile
              businessName={businessName}
              businessType={businessType}
              onNameChange={setBusinessName}
              onTypeChange={setBusinessType}
              onContinue={handleNext}
              onBack={handleBack}
            />
          )}

          {step === 3 && (
            <StepWhatsApp
              optIn={whatsappOptIn}
              phoneNumber={whatsappNumber}
              onOptInChange={setWhatsappOptIn}
              onPhoneNumberChange={setWhatsappNumber}
              onContinue={handleProfileStepComplete}
              onBack={handleBack}
            />
          )}

          {step === 4 && (
            <StepDataChoice
              onSelectChoice={handleDataChoice}
              onBack={handleBack}
            />
          )}

          {step === 5 && (
            <StepFirstRecord
              initialData={monthlyRecord}
              onSubmit={handleFirstRecordSubmit}
              onBack={handleBack}
              isLoading={isLoading}
            />
          )}

          {step === 6 && (
            <StepFirstResult
              scoreResult={scoreResult}
              profile={{ businessName, businessType }}
              onCompleteToDashboard={handleCompleteToDashboard}
            />
          )}
        </Container>
      </main>
    </div>
  );
}
