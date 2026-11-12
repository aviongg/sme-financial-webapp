"use client";

import React, { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { isDemoMode } from "@/lib/api/config";
import { useSession } from "@/components/auth/SessionProvider";
import { AuthScreen } from "@/components/auth/AuthScreen";
import { BusinessSetup } from "@/components/auth/BusinessSetup";
import { ConfigurationProvider } from "@/components/configuration/ConfigurationProvider";
import { AiProvider } from "@/components/ai/AiProvider";
import { LiveDashboard } from "@/components/integration/LiveDashboard";
import { DashboardPage } from "@/components/dashboard/DashboardPage";
import { LandingPage } from "@/components/marketing/LandingPage";

/**
 * Root Application Entry (Phase 2 & Phase 3)
 * Route: /
 * Anonymous visitors see the public FinSight PK landing page.
 * Authenticated users see the canonical FinSight Financial Health Dashboard Hub.
 */
export default function Page() {
  return (
    <Suspense fallback={<main className="p-10" role="status">Loading...</main>}>
      <RootPageContent />
    </Suspense>
  );
}

function RootPageContent() {
  const session = useSession();
  const searchParams = useSearchParams();
  const authModeParam = searchParams?.get('auth');
  const [authRequested, setAuthRequested] = useState<boolean>(() => authModeParam === 'login' || authModeParam === 'register');

  useEffect(() => {
    if (authModeParam === 'login' || authModeParam === 'register') {
      setAuthRequested(true);
    }
  }, [authModeParam]);

  if (isDemoMode) {
    return <DemoPage />;
  }

  // Authenticated user: show dashboard inside configuration and AI providers
  if (session.status === 'authenticated') {
    if (!session.business) {
      return <BusinessSetup />;
    }
    return (
      <div key={`${session.user?.id}:${session.business.businessId}:${session.epoch}`}>
        <ConfigurationProvider>
          <AiProvider>
            <LiveDashboard/>
          </AiProvider>
        </ConfigurationProvider>
      </div>
    );
  }

  // MFA or challenge required, or user requested login/register
  if (authRequested || session.status === 'challenge') {
    return <AuthScreen key={session.user?.authStage || 'auth'} />;
  }

  // Anonymous visitor: public marketing landing page
  return <LandingPage />;
}

function DemoPage() {
  return <DashboardPage defaultMode="mature" />;
}
