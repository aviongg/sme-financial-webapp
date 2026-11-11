"use client";

import React, { Suspense } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { MonthlyAutomationPanel } from "@/components/product/MonthlyFinancials";
import { useLanguage } from "@/lib/i18n/context";

export default function LedgerBridgePage() {
  return (
    <Suspense>
      <BridgeContent />
    </Suspense>
  );
}

function BridgeContent() {
  const { locale } = useLanguage();
  return (
    <AppShell
      title={locale === 'ur' ? 'خودکار ماہانہ مالی اعداد' : 'Automatic monthly financials'}
      subtitle={locale === 'ur' ? 'ہر مہینہ اور صحت اسکور روزانہ ریکارڈ سے خودبخود اخذ ہوتے ہیں۔' : 'Every month and its health score are derived automatically from your daily records.'}
    >
      <Container width="dashboard" className="py-6 space-y-6">
        <MonthlyAutomationPanel />
      </Container>
    </AppShell>
  );
}
