"use client";

import Link from "next/link";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Button } from "@/components/ui/Button";
import { Compass, ArrowLeft, PlusCircle, ScanLine } from "lucide-react";
import { useLanguage } from "@/lib/i18n/context";


export default function NotFound() {
  const { direction } = useLanguage();
  const isRTL = direction === "rtl";

  return (
    <AppShell title="Page Not Found">
      <Container width="reading" className="py-16 text-center space-y-6">
        <div className="w-14 h-14 rounded-full bg-[var(--color-surface-subtle)] border border-[var(--color-border-default)] flex items-center justify-center mx-auto text-[var(--color-text-muted)]">
          <Compass className="w-7 h-7" />
        </div>

        <div className="space-y-2">
          <h1 className="text-[22px] sm:text-[24px] font-bold text-[var(--color-text-primary)] font-heading">
            Page Not Found (404)
          </h1>
          <p className="text-[14px] text-[var(--color-text-muted)] max-w-md mx-auto leading-relaxed">
            The destination you requested does not exist or has moved. Your recorded figures and financial health data remain safe and sound.
          </p>
        </div>

        <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
          <Link href="/">
            <Button
              variant="primary"
              size="md"
              leftIcon={<ArrowLeft className={isRTL ? "rotate-180" : ""} />}
            >
              Return to Dashboard
            </Button>
          </Link>
          <Link href="/records/new">
            <Button
              variant="secondary"
              size="md"
              leftIcon={<PlusCircle />}
            >
              Add Monthly Figures
            </Button>
          </Link>
          <Link href="/scan">
            <Button
              variant="ghost"
              size="md"
              leftIcon={<ScanLine />}
            >
              Scan Documents
            </Button>
          </Link>
        </div>
      </Container>
    </AppShell>
  );
}
