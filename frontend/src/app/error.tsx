"use client";

import React, { useEffect } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { ErrorState } from "@/components/ui/ErrorState";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Log error in monitoring service if configured
    console.error("FinSight Root Error Boundary caught:", error);
  }, [error]);

  return (
    <AppShell title="System Notice">
      <Container width="reading" className="py-12">
        <ErrorState
          title="An unexpected error occurred"
          description="We encountered an issue displaying this page. Your records and data remain safe and uncorrupted."
          onRetry={reset}
          retryLabel="Reload Screen"
        />
      </Container>
    </AppShell>
  );
}
