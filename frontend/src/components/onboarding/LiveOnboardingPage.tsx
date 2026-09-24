"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Building2, Globe } from "lucide-react";
import { AlertBanner } from "@/components/ui/AlertBanner";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Container } from "@/components/ui/Container";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import type { BusinessProfileRequest } from "@/lib/api/contracts";
import { phaseOneApi } from "@/lib/api/phase-one";
import { getOrCreateUserId, setUserId } from "@/lib/api/session";
import { useLanguage } from "@/lib/i18n/context";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function LiveOnboardingPage() {
  const router = useRouter();
  const { locale, setLocale, toggleLocale, t } = useLanguage();
  const { toast } = useToast();
  const ur = locale === "ur";
  const [mode, setMode] = useState<"create" | "existing">("create");
  const [businessType, setBusinessType] = useState("");
  const [existingId, setExistingId] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const pendingId = useRef<string | null>(null);
  const submissionLock = useRef(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submissionLock.current) return;
    setError(null);
    setErrors({});
    if (mode === "create" && !businessType) {
      setErrors({ businessType: t.onboarding.businessTypeError });
      return;
    }
    if (mode === "existing" && !UUID_PATTERN.test(existingId.trim())) {
      setErrors({ userId: ur ? "براہ کرم درست پروفائل آئی ڈی درج کریں۔" : "Enter a valid profile ID." });
      return;
    }

    submissionLock.current = true;
    setSaving(true);
    try {
      if (mode === "existing") {
        const profile = await phaseOneApi.getProfile(existingId.trim());
        setUserId(profile.userId);
        setLocale(profile.languagePreference);
      } else {
        // Reuse the same identity if the first successful response was lost.
        pendingId.current ??= getOrCreateUserId();
        const request: BusinessProfileRequest = {
          userId: pendingId.current,
          businessType: businessType as BusinessProfileRequest["businessType"],
          languagePreference: locale,
          whatsappOptIn: false,
        };
        try {
          const profile = await phaseOneApi.createProfile(request);
          setUserId(profile.userId);
        } catch (cause) {
          if (!(cause instanceof ApiError) || cause.status !== 409) throw cause;
          const profile = await phaseOneApi.getProfile(pendingId.current);
          setUserId(profile.userId);
          setLocale(profile.languagePreference);
        }
      }
      toast(ur ? "پروفائل تیار ہے۔ اب ماہانہ ریکارڈ درج کریں۔" : "Your profile is ready. You can add monthly records.", "success");
      router.replace("/records");
    } catch (cause) {
      if (cause instanceof ApiError) setErrors(cause.fieldErrors ?? {});
      setError(cause instanceof ApiError && cause.status === 404
        ? (ur ? "یہ پروفائل نہیں ملا۔ آئی ڈی چیک کر کے دوبارہ کوشش کریں۔" : "This profile was not found. Check the ID and try again.")
        : (ur ? "پروفائل محفوظ یا کھولا نہیں جا سکا۔ آپ کی درج کردہ معلومات موجود ہیں؛ دوبارہ کوشش کریں۔" : "We could not save or open your profile. Your entries are still here; please try again."));
    } finally {
      submissionLock.current = false;
      setSaving(false);
    }
  }

  function changeMode(next: "create" | "existing") {
    setMode(next);
    setError(null);
    setErrors({});
  }

  return (
    <div className="min-h-screen bg-[var(--color-surface-canvas)] text-[var(--color-text-primary)]">
      <header className="border-b border-[var(--color-border-default)] bg-[var(--color-surface-card)]">
        <Container width="reading" className="h-16 flex items-center justify-between gap-4">
          <span className="font-heading font-extrabold text-xl text-[var(--color-brand-primary)]">FinSight</span>
          <Button variant="ghost" onClick={toggleLocale} disabled={saving} leftIcon={<Globe className="w-4 h-4" />}>
            {ur ? "English" : "اردو"}
          </Button>
        </Container>
      </header>
      <main>
        <Container width="reading" className="max-w-[600px] py-10 sm:py-16">
          <div className="mb-8 space-y-3 text-start">
            <Building2 className="w-9 h-9 text-[var(--color-brand-primary)]" />
            <h1 className="font-heading font-bold text-[28px]">{ur ? "اپنے کاروبار کا ریکارڈ شروع کریں" : "Start your business records"}</h1>
            <p className="text-[15px] leading-relaxed text-[var(--color-text-muted)]">
              {ur ? "اپنا پروفائل بنائیں، پھر آمدن، اخراجات اور نقد بیلنس کے ماہانہ ریکارڈ محفوظ کریں۔" : "Set up your profile, then save monthly income, expenses and cash balances."}
            </p>
          </div>
          <Card padding="lg" className="space-y-6">
            <div className="flex flex-wrap gap-2" aria-label={ur ? "پروفائل کا انتخاب" : "Profile options"}>
              <Button variant={mode === "create" ? "primary" : "secondary"} onClick={() => changeMode("create")} disabled={saving} aria-pressed={mode === "create"}>
                {ur ? "نیا پروفائل" : "New profile"}
              </Button>
              <Button variant={mode === "existing" ? "primary" : "secondary"} onClick={() => changeMode("existing")} disabled={saving} aria-pressed={mode === "existing"}>
                {ur ? "موجودہ پروفائل کھولیں" : "Open existing profile"}
              </Button>
            </div>
            <form onSubmit={handleSubmit} noValidate className="space-y-5">
              {error && <AlertBanner variant="error">{error}</AlertBanner>}
              {mode === "create" ? (
                <>
                  <Select label={t.onboarding.businessTypeLabel} value={businessType} onChange={(event) => { setBusinessType(event.target.value); setErrors({}); }} required disabled={saving} error={errors.businessType} options={[
                    { value: "", label: ur ? "کاروبار کی قسم منتخب کریں" : "Select your business type", disabled: true },
                    { value: "trade", label: t.onboarding.typeTradeTitle },
                    { value: "manufacturing", label: t.onboarding.typeMfgTitle },
                    { value: "services", label: t.onboarding.typeServicesTitle },
                    { value: "retail", label: t.onboarding.typeRetailTitle },
                  ]} />
                  <Select label={ur ? "زبان" : "Language"} value={locale} onChange={(event) => setLocale(event.target.value as "en" | "ur")} disabled={saving} error={errors.languagePreference} options={[
                    { value: "en", label: "English" },
                    { value: "ur", label: "اردو" },
                  ]} />
                </>
              ) : (
                <Input label={ur ? "پروفائل آئی ڈی" : "Profile ID"} value={existingId} onChange={(event) => { setExistingId(event.target.value); setErrors({}); }} error={errors.userId} disabled={saving} required dir="ltr" autoComplete="off" helperText={ur ? "وہ آئی ڈی درج کریں جو آپ نے اپنے پروفائل کی سیٹنگز سے محفوظ کی تھی۔" : "Enter the ID you saved from your profile settings."} />
              )}
              <Button type="submit" className="w-full" size="lg" isLoading={saving} disabled={saving}>
                {mode === "create" ? (ur ? "پروفائل بنائیں" : "Create profile") : (ur ? "پروفائل کھولیں" : "Open profile")}
              </Button>
            </form>
          </Card>
        </Container>
      </main>
    </div>
  );
}
