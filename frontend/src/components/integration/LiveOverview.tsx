"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { ErrorState } from "@/components/ui/ErrorState";
import { phaseOneApi } from "@/lib/api/phase-one";
import { useLanguage } from "@/lib/i18n/context";

export function LiveOverview({ userId }: { userId: string }) {
  const { locale, setLocale } = useLanguage();
  const ur = locale === "ur";
  const [result, setResult] = useState<{ count: number; latest?: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    Promise.all([phaseOneApi.getProfile(userId), phaseOneApi.getMonthlyRecords(userId)])
      .then(([profile, records]) => {
        if (!active) return;
        setLocale(profile.languagePreference);
        setResult({ count: records.length, latest: records.map((r) => r.month).sort().at(-1) });
      })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : "Could not load your records."); });
    return () => { active = false; };
  }, [userId, attempt, setLocale]);

  return <AppShell title={ur ? "کاروباری جائزہ" : "Business overview"}>
    <Container width="reading" className="space-y-6">
      {error ? <ErrorState title={ur ? "ریکارڈ لوڈ نہیں ہوئے" : "Could not load your records"} description={error} onRetry={() => { setError(null); setAttempt((n) => n + 1); }} /> : !result ? <p role="status">{ur ? "لوڈ ہو رہا ہے…" : "Loading your records…"}</p> : <Card padding="lg" className="space-y-4">
        <h2 className="text-2xl font-semibold">{ur ? "آپ کے ماہانہ ریکارڈ" : "Your monthly records"}</h2>
        <p>{ur ? `محفوظ مہینے: ${result.count}` : `${result.count} saved ${result.count === 1 ? "month" : "months"}`}</p>
        {result.latest && <p>{ur ? "تازہ ترین مہینہ: " : "Latest month: "}<span dir="ltr">{result.latest}</span></p>}
        <div className="flex flex-wrap gap-5 text-[var(--color-brand-primary)] font-semibold">
          <Link href="/records/new" className="underline">{ur ? "ماہانہ ریکارڈ شامل کریں" : "Add monthly figures"}</Link>
          <Link href="/records" className="underline">{ur ? "ریکارڈ دیکھیں" : "View records"}</Link>
        </div>
      </Card>}
      <Card padding="lg" className="space-y-2">
        <h2 className="font-semibold">{ur ? "مالی صحت کی رپورٹ" : "Financial health report"}</h2>
        <p className="text-sm text-[var(--color-text-muted)]">{ur ? "اسکور اور مشورے ابھی اس ویو میں دستیاب نہیں۔ محفوظ ریکارڈ ماہانہ ریکارڈز میں دیکھے جا سکتے ہیں۔" : "Scores and advice are not available in this view yet. Your saved figures are available in Monthly Records."}</p>
      </Card>
      <Link href="/onboarding" className="text-sm underline">{ur ? "دوسری پروفائل کھولیں" : "Open another profile"}</Link>
    </Container>
  </AppShell>;
}
