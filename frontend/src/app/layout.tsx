import type { Metadata } from "next";
import localFont from "next/font/local";
import { headers } from "next/headers";
import { SessionProvider } from "@/components/auth/SessionProvider";
import { IntegrationBoundary } from "@/components/layout/IntegrationBoundary";
import "./globals.css";
import { LanguageProvider } from "@/lib/i18n/context";
import { ToastProvider } from "@/components/ui/Toast";

const plusJakartaSans = localFont({
  src: "./fonts/PlusJakartaSans.ttf",
  variable: "--font-heading",
  weight: "500 800",
  display: "swap",
});

const inter = localFont({
  src: "./fonts/Inter.ttf",
  variable: "--font-sans",
  weight: "400 700",
  display: "swap",
});

const notoNastaliqUrdu = localFont({
  src: "./fonts/NotoNastaliqUrdu.ttf",
  variable: "--font-urdu",
  weight: "400 700",
  display: "swap",
});


export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "FinSight — SME Financial Health Platform",
  description:
    "Comprehensive financial health scoring, liquidity forecasting, and AI document extraction for growing businesses.",
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const headersList = await headers();
  void headersList.get("x-nonce");
  return (
    <html
      lang="en"
      dir="ltr"
      className={`${plusJakartaSans.variable} ${inter.variable} ${notoNastaliqUrdu.variable}`}
      suppressHydrationWarning
    >

      <body className="antialiased bg-[var(--color-surface-canvas)] text-[var(--color-text-primary)]">
        <LanguageProvider>
          <ToastProvider>
            <SessionProvider><IntegrationBoundary>{children}</IntegrationBoundary></SessionProvider>
          </ToastProvider>
        </LanguageProvider>
      </body>
    </html>
  );
}
