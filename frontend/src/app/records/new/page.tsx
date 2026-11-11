"use client";

import React from "react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { MonthlyAutomationPanel } from "@/components/product/MonthlyFinancials";
import { useLanguage } from "@/lib/i18n/context";

/** Monthly figures are derived (monthly-projection-v1); this route explains that and holds supplemental inputs only. */
export default function NewMonthlyRecordPage() {
  const { t } = useLanguage();

  return (
    <AppShell
      title={t.records.newTitle}
      subtitle={t.records.newSubtitle}
    >
      <Container width="dashboard" className="py-6">
        <div className="max-w-[860px] mx-auto">
          <MonthlyAutomationPanel />
        </div>
      </Container>
    </AppShell>
  );
}
