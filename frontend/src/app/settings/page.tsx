"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import {
  Building2,
  Globe,
  MessageSquare,
  Save,
  CheckCircle2,
  ArrowLeft,
} from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Switch } from "@/components/ui/Switch";
import { Skeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/Toast";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";
import { cn } from "@/lib/utils/cn";

export default function SettingsPage() {
  const { t, locale, setLocale, direction } = useLanguage();
  const { toast } = useToast();

  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);

  // Form State
  const [businessName, setBusinessName] = useState("");
  const [businessType, setBusinessType] = useState("trade");
  const [industry, setIndustry] = useState("");
  const [languagePref, setLanguagePref] = useState<"en" | "ur">(locale);
  const [whatsappOptIn, setWhatsappOptIn] = useState(true);
  const [whatsappNumber, setWhatsappNumber] = useState("");

  useEffect(() => {
    let isCancelled = false;
    mockApi
      .getBusinessProfile()
      .then((bp) => {
        if (!isCancelled) {
          setBusinessName(bp.businessName);
          setBusinessType(bp.businessType || "trade");
          setIndustry(bp.industry || "Textiles & Apparel");
          setLanguagePref(bp.languagePreference || locale);
          setWhatsappOptIn(bp.whatsappOptIn);
          setWhatsappNumber(bp.whatsappNumber || "+92 300 1234567");
          setIsLoading(false);
        }
      })

      .catch(() => {
        if (!isCancelled) setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, [locale]);

  const handleLanguageChange = (newLang: "en" | "ur") => {
    setLanguagePref(newLang);
    setLocale(newLang);
    setHasUnsavedChanges(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);

    try {
      await mockApi.updateSettings({
        profile: {
          businessName,
          businessType,
          industry,
        },
        language: languagePref,
        whatsappOptIn,
        whatsappNumber,
      });

      setHasUnsavedChanges(false);
      toast(t.settings.saveSuccess, "success");
    } catch {
      toast("Unable to save settings. Please try again.", "error");
    } finally {
      setIsSaving(false);
    }
  };

  const isRTL = direction === "rtl";

  return (
    <AppShell
      title={t.settings.title}
      subtitle={t.settings.subtitle}
      headerActions={
        <Link href="/">
          <Button
            variant="secondary"
            size="sm"
            leftIcon={<ArrowLeft className={isRTL ? "rotate-180" : ""} />}
          >
            {t.componentBreakdown.returnToDashboard}
          </Button>
        </Link>
      }
    >
      <Container width="reading" className="py-6 sm:py-8 space-y-6">
        {/* Navigation Breadcrumb */}
        <div className="flex items-center justify-between">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-[13px] font-medium text-[var(--color-brand-primary)] hover:underline focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] rounded-[var(--radius-sm)]"
          >
            <ArrowLeft className={cn("w-4 h-4", isRTL && "rotate-180")} />
            <span>{t.componentBreakdown.returnToDashboard}</span>
          </Link>
          {hasUnsavedChanges && (
            <span className="text-[12px] font-medium text-[var(--color-status-warning)]">
              {t.settings.unsavedChanges}
            </span>
          )}
        </div>

        {isLoading ? (
          <div className="space-y-6">
            <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
              <Skeleton className="h-6 w-1/3" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </Card>
            <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
              <Skeleton className="h-6 w-1/3" />
              <Skeleton className="h-10 w-full" />
            </Card>
          </div>
        ) : (
          <form onSubmit={handleSave} className="space-y-6">
            {/* 1. Business Profile Section */}
            <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
              <div className="flex items-center gap-2.5 pb-3 border-b border-[var(--color-border-subtle)]">
                <Building2 className="w-5 h-5 text-[var(--color-brand-primary)]" />
                <div>
                  <h2 className="text-[16px] font-semibold text-[var(--color-text-primary)] font-heading">
                    {t.settings.profileSection}
                  </h2>
                </div>
              </div>

              <div className="space-y-4 pt-1">
                <div>
                  <Input
                    label={t.settings.businessName}
                    value={businessName}
                    onChange={(e) => {
                      setBusinessName(e.target.value);
                      setHasUnsavedChanges(true);
                    }}
                    required
                  />
                </div>

                <div>
                  <Select
                    label={t.settings.businessType}
                    value={businessType}
                    onChange={(e) => {
                      setBusinessType(e.target.value);
                      setHasUnsavedChanges(true);
                    }}
                    options={[
                      { value: "trade", label: "Wholesale & Retail Trade" },
                      { value: "manufacturing", label: "Manufacturing & Production" },
                      { value: "services", label: "Commercial Services" },
                      { value: "retail", label: "Direct Retail Outlet" },
                    ]}
                  />
                </div>

                <div>
                  <Input
                    label={t.settings.industry}
                    value={industry}
                    onChange={(e) => {
                      setIndustry(e.target.value);
                      setHasUnsavedChanges(true);
                    }}
                  />
                </div>

                <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-between text-[13px]">
                  <span className="text-[var(--color-text-secondary)]">{t.settings.currency}</span>
                  <span className="font-semibold text-[var(--color-text-primary)] font-heading">
                    {t.settings.currencyFixed}
                  </span>
                </div>
              </div>
            </Card>

            {/* 2. Language & Display Section */}
            <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
              <div className="flex items-center gap-2.5 pb-3 border-b border-[var(--color-border-subtle)]">
                <Globe className="w-5 h-5 text-[var(--color-brand-primary)]" />
                <div>
                  <h2 className="text-[16px] font-semibold text-[var(--color-text-primary)] font-heading">
                    {t.settings.languageSection}
                  </h2>
                  <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5">
                    {t.settings.languageDesc}
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => handleLanguageChange("en")}
                  className={cn(
                    "p-4 rounded-[var(--radius-lg)] border text-start transition-all flex items-center justify-between select-none",
                    languagePref === "en"
                      ? "border-[var(--color-brand-primary)] bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)]"
                      : "border-[var(--color-border-default)] hover:bg-[var(--color-surface-hover)] text-[var(--color-text-primary)]"
                  )}
                >
                  <div>
                    <span className="font-semibold text-[14px] block font-heading">
                      {t.settings.englishOption}
                    </span>
                    <span className="text-[12px] text-[var(--color-text-muted)] block mt-0.5">
                      Left-to-Right layout
                    </span>
                  </div>
                  {languagePref === "en" && (
                    <CheckCircle2 className="w-5 h-5 text-[var(--color-brand-primary)] shrink-0" />
                  )}
                </button>

                <button
                  type="button"
                  onClick={() => handleLanguageChange("ur")}
                  className={cn(
                    "p-4 rounded-[var(--radius-lg)] border text-start transition-all flex items-center justify-between select-none",
                    languagePref === "ur"
                      ? "border-[var(--color-brand-primary)] bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)]"
                      : "border-[var(--color-border-default)] hover:bg-[var(--color-surface-hover)] text-[var(--color-text-primary)]"
                  )}
                >
                  <div>
                    <span className="font-semibold text-[14px] block font-urdu">
                      {t.settings.urduOption}
                    </span>
                    <span className="text-[12px] text-[var(--color-text-muted)] block mt-0.5">
                      دائیں سے بائیں لے آؤٹ
                    </span>
                  </div>
                  {languagePref === "ur" && (
                    <CheckCircle2 className="w-5 h-5 text-[var(--color-brand-primary)] shrink-0" />
                  )}
                </button>
              </div>
            </Card>

            {/* 3. WhatsApp Notification Alerts */}
            <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
              <div className="flex items-center gap-2.5 pb-3 border-b border-[var(--color-border-subtle)]">
                <MessageSquare className="w-5 h-5 text-[var(--color-brand-primary)]" />
                <div>
                  <h2 className="text-[16px] font-semibold text-[var(--color-text-primary)] font-heading">
                    {t.settings.whatsappSection}
                  </h2>
                  <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5">
                    {t.settings.whatsappDesc}
                  </p>
                </div>
              </div>

              <div className="space-y-4 pt-2">
                <div className="flex items-center justify-between p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)]">
                  <span className="text-[13px] font-medium text-[var(--color-text-primary)]">
                    {t.settings.whatsappOptIn}
                  </span>
                  <Switch
                    checked={whatsappOptIn}
                    onChange={(e) => {
                      setWhatsappOptIn(e.target.checked);
                      setHasUnsavedChanges(true);
                    }}
                    label="Toggle WhatsApp monthly digests"
                  />
                </div>

                {whatsappOptIn && (
                  <div>
                    <Input
                      label={t.settings.whatsappNumber}
                      value={whatsappNumber}
                      onChange={(e) => {
                        setWhatsappNumber(e.target.value);
                        setHasUnsavedChanges(true);
                      }}
                      placeholder="+92 300 1234567"
                    />
                  </div>
                )}
              </div>
            </Card>

            {/* Save Action Buttons */}
            <div className="flex items-center justify-end gap-3 pt-2">
              <Link href="/">
                <Button variant="ghost">
                  {t.common.cancel}
                </Button>
              </Link>
              <Button
                type="submit"
                variant="primary"
                isLoading={isSaving}
                leftIcon={<Save />}
              >
                {t.settings.saveSettings}
              </Button>
            </div>
          </form>
        )}
      </Container>
    </AppShell>
  );
}
