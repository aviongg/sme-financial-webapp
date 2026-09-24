"use client";

import { useState } from "react";
import { Globe } from "lucide-react";
import { useLanguage } from "@/lib/i18n/context";
import { useToast } from "@/components/ui/Toast";
import { isDemoMode } from "@/lib/api/config";
import { getUserId } from "@/lib/api/session";
import { phaseOneApi } from "@/lib/api/phase-one";

export function LanguageControl() {
  const { locale, setLocale } = useLanguage();
  const { toast } = useToast();
  const [busy, setBusy] = useState(false);
  const toggle = async () => {
    const next = locale === "en" ? "ur" : "en";
    setBusy(true);
    try {
      const userId = getUserId();
      if (!isDemoMode && userId) await phaseOneApi.updateLanguage(userId, next);
      setLocale(next);
    } catch {
      toast(locale === "ur" ? "زبان محفوظ نہیں ہوئی۔ دوبارہ کوشش کریں۔" : "Could not save your language. Please try again.", "error");
    } finally { setBusy(false); }
  };
  return <button type="button" onClick={toggle} disabled={busy} aria-label="Switch interface language" className="inline-flex items-center gap-2 rounded-md border border-[var(--color-border-default)] px-3 py-2 text-sm font-semibold text-[var(--color-brand-primary)] disabled:opacity-50">
    <Globe className="h-4 w-4" />{busy ? "…" : locale === "en" ? "اردو" : "English"}
  </button>;
}
