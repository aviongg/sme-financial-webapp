"use client";
import { isDemoMode } from "@/lib/api/config";
import { LiveDashboard } from "@/components/integration/LiveDashboard";
export default function Page(){ return isDemoMode ? <DemoPage/> : <LiveDashboard/>; }

import React from "react";
import { DashboardPage } from "@/components/dashboard/DashboardPage";

/**
 * Root Dashboard Hub Page (Phase 3)
 * Route: /
 * Displays the canonical FinSight Financial Health Dashboard Hub.
 */
function DemoPage() {
  return <DashboardPage defaultMode="mature" />;
}
