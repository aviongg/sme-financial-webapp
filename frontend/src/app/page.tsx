"use client";

import React from "react";
import { DashboardPage } from "@/components/dashboard/DashboardPage";

/**
 * Root Dashboard Hub Page (Phase 3)
 * Route: /
 * Displays the canonical FinSight Financial Health Dashboard Hub.
 */
export default function Page() {
  return <DashboardPage defaultMode="mature" />;
}
