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
import type { MonthlyRecordResponse } from "@/types/financial";

export default function EditMonthlyRecordPage() {
  const params = useParams();
  const router = useRouter();
  const { t } = useLanguage();

  const recordId = typeof params.id === "string" ? params.id : Array.isArray(params.id) ? params.id[0] : "";

  const [record, setRecord] = useState<MonthlyRecordResponse | null>(null);
  const [isLoading, setIsLoading] = useState(Boolean(recordId));
  const [isNotFound, setIsNotFound] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const notFound = !recordId || isNotFound;

  useEffect(() => {
    if (!recordId) return;

    let isCancelled = false;

    mockApi
      .getMonthlyRecord(recordId)
      .then((res) => {
        if (isCancelled) return;
        if (res) {
          setRecord(res);
        } else {
          setIsNotFound(true);
        }
        setIsLoading(false);
      })
      .catch(() => {
        if (isCancelled) return;
        setError(t.records.loadError);
        setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, [recordId, t.records.loadError]);

  return (
    <AppShell
      title={t.records.editTitle}
      subtitle={t.records.editSubtitle}
    >
      <Container width="dashboard" className="py-6">
        <div className="max-w-[860px] mx-auto">
          {/* Loading state: Geometry-matched structural skeleton */}
          {isLoading && <RecordFormSkeleton />}

          {/* Record not found state */}
          {!isLoading && notFound && (
            <ErrorState
              title={t.records.notFoundTitle}
              description={t.records.notFoundDescription}
              retryLabel={t.records.backToDashboard}
              onRetry={() => router.push("/")}
            />
          )}

          {/* Load error state with retry */}
          {!isLoading && !notFound && error && (
            <ErrorState
              title={t.records.notFoundTitle}
              description={error}
              retryLabel={t.common.tryAgain}
              onRetry={() => {
                setIsLoading(true);
                setError(null);
                mockApi
                  .getMonthlyRecord(recordId)
                  .then((res) => {
                    if (res) setRecord(res);
                    else setIsNotFound(true);
                    setIsLoading(false);
                  })
                  .catch(() => {
                    setError(t.records.loadError);
                    setIsLoading(false);
                  });
              }}
            />
          )}

          {/* Successfully loaded record */}
          {!isLoading && !notFound && !error && record && (
            <MonthlyRecordForm
              initialData={record}
              isEditMode
            />
          )}
        </div>
      </Container>
    </AppShell>
  );
}
