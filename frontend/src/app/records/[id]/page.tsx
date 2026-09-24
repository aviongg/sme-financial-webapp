"use client";

import React, { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { ErrorState } from "@/components/ui/ErrorState";
import { MonthlyRecordForm } from "@/components/records/MonthlyRecordForm";
import { RecordFormSkeleton } from "@/components/records/RecordFormSkeleton";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";
import { ApiError } from "@/lib/api/client";
import { isDemoMode } from "@/lib/api/config";
import { phaseOneApi } from "@/lib/api/phase-one";
import { getUserId } from "@/lib/api/session";
import type { MonthlyRecordResponse } from "@/types/financial";

export default function EditMonthlyRecordPage() {
  const params = useParams();
  const recordId = typeof params.id === "string" ? params.id : Array.isArray(params.id) ? params.id[0] : "";
  return <RecordEditor key={recordId} recordId={recordId} />;
}

function RecordEditor({ recordId }: { recordId: string }) {
  const router = useRouter();
  const { t, locale } = useLanguage();

  const [record, setRecord] = useState<MonthlyRecordResponse | null>(null);
  const [isLoading, setIsLoading] = useState(Boolean(recordId));
  const [isNotFound, setIsNotFound] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  const invalidMonth = !isDemoMode && !/^\d{4}-(0[1-9]|1[0-2])$/.test(recordId);
  const notFound = !recordId || invalidMonth || isNotFound;

  useEffect(() => {
    if (!recordId || invalidMonth) return;

    let isCancelled = false;
    const userId = getUserId();
    if (!isDemoMode && !userId) {
      router.replace("/onboarding");
      return;
    }
    const request = isDemoMode ? mockApi.getMonthlyRecord(recordId) : phaseOneApi.getMonthlyRecord(userId!, recordId);
    request
      .then((res) => {
        if (isCancelled) return;
        if (res) {
          setRecord(res);
          setIsNotFound(false);
          setError(null);
        } else {
          setIsNotFound(true);
        }
        setIsLoading(false);
      })
      .catch((cause) => {
        if (isCancelled) return;
        if (cause instanceof ApiError && cause.status === 404) setIsNotFound(true);
        else setError(t.records.loadError);
        setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, [recordId, invalidMonth, t.records.loadError, retry, router]);

  return (
    <AppShell
      title={t.records.editTitle}
      subtitle={t.records.editSubtitle}
    >
      <Container width="dashboard" className="py-6">
        <div className="max-w-[860px] mx-auto">
          {/* Loading state: Geometry-matched structural skeleton */}
          {isLoading && !notFound && <RecordFormSkeleton />}

          {/* Record not found state */}
          {notFound && (
            <ErrorState
              title={t.records.notFoundTitle}
              description={t.records.notFoundDescription}
              showSafeMessage={false}
              retryLabel={isDemoMode ? t.records.backToDashboard : (locale === "ur" ? "ماہانہ ریکارڈ" : "Monthly records")}
              onRetry={() => router.push(isDemoMode ? "/" : "/records")}
            />
          )}

          {/* Load error state with retry */}
          {!isLoading && !notFound && error && (
            <ErrorState
              title={locale === "ur" ? "ریکارڈ لوڈ نہیں ہو سکا" : "Could not load this record"}
              description={error}
              showSafeMessage={false}
              retryLabel={t.common.tryAgain}
              onRetry={() => {
                setIsLoading(true);
                setError(null);
                setRetry((value) => value + 1);
              }}
            />
          )}

          {/* Successfully loaded record */}
          {!isLoading && !notFound && !error && record && (
            <MonthlyRecordForm
              key={`${record.userId}:${record.month}`}
              initialData={record}
              isEditMode
            />
          )}
        </div>
      </Container>
    </AppShell>
  );
}
