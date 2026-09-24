"use client";

import React from "react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { MonthlyRecordForm } from "@/components/records/MonthlyRecordForm";
import { useLanguage } from "@/lib/i18n/context";

export default function NewMonthlyRecordPage() {
  const { t } = useLanguage();

  return (
    <AppShell
      title={t.records.newTitle}
      subtitle={t.records.newSubtitle}
    >
      <Container width="dashboard" className="py-6">
        <div className="max-w-[860px] mx-auto">
          <MonthlyRecordForm />
        </div>
      </Container>
    </AppShell>
  );
}
