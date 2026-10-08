"use client";

import React from "react";
import { Skeleton } from "@/components/ui/Skeleton";
import { Card } from "@/components/ui/Card";
import { cn } from "@/lib/utils/cn";

export interface DashboardSkeletonProps {
  className?: string;
}

/**
 * DashboardSkeleton
 * Geometry-matched skeletons for the financial dashboard hub.
 * Respects prefers-reduced-motion and avoids generic spinners.
 */
export function DashboardSkeleton({ className }: DashboardSkeletonProps) {
  return (
    <div className={cn("space-y-8 text-start", className)}>
      {/* Header Skeleton */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-[var(--color-border-subtle)]">
        <div className="space-y-2">
          <Skeleton variant="heading" className="w-[220px] h-[28px]" />
          <Skeleton variant="text" className="w-[160px] h-[18px]" />
        </div>
        <div className="flex items-center gap-3">
          <Skeleton variant="rectangular" className="w-[130px] h-[36px]" />
          <Skeleton variant="rectangular" className="w-[110px] h-[36px]" />
        </div>
      </div>

      {/* Top Hero Grid Skeleton */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left: Health Score Hero Skeleton (7 cols) */}
        <div className="lg:col-span-7">
          <Card elevation={1} padding="lg" className="border-[var(--color-border-default)] space-y-5">
            <div className="flex items-center justify-between">
              <Skeleton variant="text" className="w-[140px] h-[16px]" />
              <Skeleton variant="rectangular" className="w-[80px] h-[24px]" />
            </div>
            <div className="flex items-center justify-between py-2">
              <Skeleton variant="metric" className="w-[110px] h-[52px]" />
              <Skeleton variant="circular" className="w-[100px] h-[60px]" />
            </div>
            <div className="pt-4 border-t border-[var(--color-border-subtle)] space-y-2.5">
              <Skeleton variant="heading" className="w-[100px] h-[18px]" />
              <Skeleton variant="text" className="w-[95%] h-[16px]" />
              <Skeleton variant="text" className="w-[80%] h-[16px]" />
            </div>
          </Card>
        </div>

        {/* Right: Next Step & Completeness Skeletons (5 cols) */}
        <div className="lg:col-span-5 space-y-6">
          <Card elevation={0} padding="md" className="border-[var(--color-border-default)] space-y-3">
            <div className="flex items-center justify-between">
              <Skeleton variant="text" className="w-[90px] h-[14px]" />
              <Skeleton variant="text" className="w-[60px] h-[18px]" />
            </div>
            <Skeleton variant="heading" className="w-[85%] h-[20px]" />
            <Skeleton variant="text" className="w-full h-[14px]" />
            <Skeleton variant="text" className="w-[90%] h-[14px]" />
            <Skeleton variant="rectangular" className="w-full h-[36px]" />
          </Card>

          <Card elevation={0} padding="md" className="border-[var(--color-brand-border)] bg-[var(--color-brand-surface)]/40 space-y-3">
            <div className="flex items-center justify-between">
              <Skeleton variant="text" className="w-[110px] h-[14px]" />
              <Skeleton variant="text" className="w-[40px] h-[16px]" />
            </div>
            <Skeleton variant="rectangular" className="w-full h-[6px]" />
            <Skeleton variant="text" className="w-[70%] h-[16px]" />
            <Skeleton variant="text" className="w-full h-[14px]" />
          </Card>
        </div>
      </div>

      {/* Cash Flow Chart Skeleton */}
      <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
        <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)]">
          <Skeleton variant="heading" className="w-[160px] h-[20px]" />
          <Skeleton variant="text" className="w-[140px] h-[16px]" />
        </div>
        <Skeleton variant="rectangular" className="w-full h-[180px]" />
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 pt-3">
          <Skeleton variant="rectangular" className="w-full h-[56px]" />
          <Skeleton variant="rectangular" className="w-full h-[56px]" />
          <Skeleton variant="rectangular" className="w-full h-[56px]" />
          <Skeleton variant="rectangular" className="w-full h-[56px]" />
        </div>
      </Card>

      {/* Bottom Grid Skeleton */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
          <Skeleton variant="heading" className="w-[160px] h-[20px]" />
          <div className="space-y-3">
            {[1, 2, 3, 4, 5].map((i) => (
              <Skeleton key={i} variant="rectangular" className="w-full h-[42px]" />
            ))}
          </div>
        </Card>
        <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
          <Skeleton variant="heading" className="w-[160px] h-[20px]" />
          <Skeleton variant="rectangular" className="w-full h-[220px]" />
        </Card>
      </div>
    </div>
  );
}
