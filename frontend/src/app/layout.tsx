import type { Metadata } from "next";
import { Plus_Jakarta_Sans, Inter, Noto_Nastaliq_Urdu } from "next/font/google";
import "./globals.css";
import { LanguageProvider } from "@/lib/i18n/context";
import { ToastProvider } from "@/components/ui/Toast";
import { IntegrationBoundary } from "@/components/layout/IntegrationBoundary";

const plusJakartaSans = Plus_Jakarta_Sans({
  subsets: ["latin"],
  variable: "--font-heading",
  weight: ["500", "600", "700", "800"],
  display: "swap",
});

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-sans",
  weight: ["400", "500", "600", "700"],
  display: "swap",
});

const notoNastaliqUrdu = Noto_Nastaliq_Urdu({
  subsets: ["arabic"],
  variable: "--font-urdu",
  weight: ["400", "500", "600", "700"],
  display: "swap",
});


export const metadata: Metadata = {
  title: "FinSight — SME Financial Health Platform",
  description:
    "Comprehensive financial health scoring, liquidity forecasting, and AI document extraction for growing businesses.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
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
            <IntegrationBoundary>{children}</IntegrationBoundary>
          </ToastProvider>
        </LanguageProvider>
      </body>
    </html>
  );
}
