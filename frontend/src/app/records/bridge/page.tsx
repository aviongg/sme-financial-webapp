"use client";

import React, { Suspense } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { LedgerBridgeWorkspace } from "@/components/finance/bridge/LedgerBridgeWorkspace";
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
      title={locale === 'ur' ? 'لیجر ٹو ہیلتھ کٹ اوور برج' : 'Ledger-to-Health Cutover Bridge'}
      subtitle={locale === 'ur' ? 'پوسٹ شدہ ٹرانزیکشنز سے مستند ماہانہ مالیاتی سمری اور ہیلتھ اسکور اخذ کریں۔' : 'Derive authoritative monthly financial summaries and health scores from posted ledger transactions.'}
    >
      <Container width="dashboard" className="py-6 space-y-6">
        <LedgerBridgeWorkspace />
      </Container>
    </AppShell>
  );
}
