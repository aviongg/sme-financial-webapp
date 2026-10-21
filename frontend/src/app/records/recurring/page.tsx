"use client";

import React, { Suspense } from "react";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Button } from "@/components/ui/Button";
import { useLanguage } from "@/lib/i18n/context";
import { recurringEn, recurringUr } from "@/components/records/recurring/recurring-copy";
import { RecurringWorkspace } from "@/components/records/recurring/RecurringWorkspace";
import { cn } from "@/lib/utils/cn";

export default function RecurringPage() {
  return (
    <Suspense>
      <RecurringPageContent />
    </Suspense>
  );
}

function RecurringPageContent() {
  const { locale, direction } = useLanguage();
  const copy = locale === "ur" ? recurringUr : recurringEn;
  const isRTL = direction === "rtl";

  return (
    <AppShell
      title={copy.title}
      subtitle={copy.subtitle}
      headerActions={
        <Link href="/records?view=recurring">
          <Button
            variant="secondary"
            size="sm"
            leftIcon={<ArrowLeft className={cn("w-4 h-4", isRTL && "rotate-180")} />}
          >
            {locale === "ur" ? "ریکارڈز پر واپس" : "Back to Records"}
          </Button>
        </Link>
      }
    >
      <Container width="dashboard" className="py-6 sm:py-8">
        <RecurringWorkspace locale={locale} />
      </Container>
    </AppShell>
  );
}
