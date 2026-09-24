"use client";

import React, { useSyncExternalStore } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { isDemoMode } from "@/lib/api/config";
import { getUserId } from "@/lib/api/session";
import { useLanguage } from "@/lib/i18n/context";
import { AppShell } from "./AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { LiveOverview } from "@/components/integration/LiveOverview";
import { LiveSettings } from "@/components/integration/LiveSettings";

const subscribe = (callback: () => void) => {
  window.addEventListener("storage", callback);
  return () => window.removeEventListener("storage", callback);
};
const clientReady = () => true;
const serverReady = () => false;

/** Keeps unfinished demo routes from presenting sample finances in live mode. */
export function IntegrationBoundary({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { locale } = useLanguage();
  const ready = useSyncExternalStore(subscribe, clientReady, serverReady);
  const userId = useSyncExternalStore(subscribe, getUserId, () => null);
  const ur = locale === "ur";

  if (isDemoMode) return <>
    <div className="relative z-50 bg-amber-100 px-4 py-2 text-center text-sm text-amber-950" role="status">
      {ur ? "ڈیمو موڈ — فرضی ڈیٹا، تبدیلیاں سرور پر محفوظ نہیں ہوتیں۔" : "Demo mode — sample data. Changes are not saved to the backend."}
    </div>
    {children}
  </>;

  if (pathname === "/onboarding") return children;
  if (!ready) return <p className="p-8" role="status">{ur ? "لوڈ ہو رہا ہے…" : "Loading…"}</p>;

  if (!userId) return <AppShell title={ur ? "اپنا کاروبار شروع کریں" : "Set up your business"}>
    <Container width="reading"><Card padding="lg" className="space-y-4">
      <h2 className="text-xl font-semibold">{ur ? "آپ کے محفوظ مالی ریکارڈ" : "Your saved financial records"}</h2>
      <p>{ur ? "شروع کرنے کے لیے پروفائل بنائیں یا اپنی موجودہ پروفائل کھولیں۔" : "Create a profile or reopen your existing profile to start recording your monthly figures."}</p>
      <Link className="inline-block font-semibold text-[var(--color-brand-primary)] underline" href="/onboarding">{ur ? "پروفائل کھولیں" : "Set up or open a profile"}</Link>
    </Card></Container>
  </AppShell>;

  if (pathname === "/") return <LiveOverview key={userId} userId={userId} />;
  if (pathname === "/settings") return <LiveSettings key={userId} userId={userId} />;
  if (pathname === "/records" || pathname.startsWith("/records/")) return <React.Fragment key={userId}>{children}</React.Fragment>;

  return <AppShell title={ur ? "جلد دستیاب ہوگا" : "Coming soon"}>
    <Container width="reading"><Card padding="lg" className="space-y-4">
      <h2 className="text-xl font-semibold">{ur ? "یہ فیچر ابھی دستیاب نہیں" : "This feature is not connected yet"}</h2>
      <p>{ur ? "آپ اپنے ماہانہ ریکارڈ محفوظ اور تبدیل کر سکتے ہیں۔ مزید رپورٹس اور دستاویزات کی سہولت جلد شامل ہوگی۔" : "You can save and edit monthly records now. Financial reports and document features will be connected in the next updates."}</p>
      <Link className="font-semibold text-[var(--color-brand-primary)] underline" href="/records">{ur ? "ماہانہ ریکارڈ دیکھیں" : "View monthly records"}</Link>
    </Card></Container>
  </AppShell>;
}
