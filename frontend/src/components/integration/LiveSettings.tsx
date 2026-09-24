"use client";

import Link from "next/link";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { useLanguage } from "@/lib/i18n/context";
import { LanguageControl } from "./LanguageControl";

export function LiveSettings({ userId }: { userId: string }) {
  const { locale } = useLanguage();
  const ur = locale === "ur";
  return <AppShell title={ur ? "ترتیبات" : "Settings"}>
    <Container width="reading" className="space-y-6">
      <Card padding="lg" className="space-y-4">
        <h2 className="text-lg font-semibold">{ur ? "زبان" : "Language"}</h2>
        <p>{ur ? "آپ کی زبان پروفائل میں محفوظ ہوتی ہے۔" : "Your language preference is saved with your profile."}</p>
        <LanguageControl />
      </Card>
      <Card padding="lg" className="space-y-3">
        <h2 className="text-lg font-semibold">{ur ? "پروفائل شناخت" : "Profile reference"}</h2>
        <p className="text-sm">{ur ? "یہ شناخت محفوظ رکھیں تاکہ اس ترقیاتی ورژن میں دوسرے براؤزر سے اپنی پروفائل کھول سکیں۔ یہ لاگ اِن نہیں ہے۔" : "Keep this reference to reopen your profile in another browser during development. This is not a login."}</p>
        <p className="break-all font-mono text-sm" dir="ltr">{userId}</p>
        <Link href="/onboarding" className="inline-block text-[var(--color-brand-primary)] underline">{ur ? "دوسری پروفائل کھولیں" : "Open another profile"}</Link>
      </Card>
    </Container>
  </AppShell>;
}
