"use client";

import React from "react";
import { Skeleton } from "@/components/ui/Skeleton";
import { Divider } from "@/components/ui/Divider";

export function RecordFormSkeleton() {
  return (
    <div className="max-w-[860px] mx-auto space-y-8 py-4 text-start">
      {/* Back button & Header Skeleton */}
      <div className="space-y-3">
        <Skeleton className="w-[140px] h-[18px]" />
        <Skeleton className="w-[280px] h-[32px]" />
        <Skeleton className="w-[420px] h-[20px]" />
      </div>

      <Divider />

      {/* Month Selector Skeleton */}
      <div className="space-y-2">
        <Skeleton className="w-[120px] h-[16px]" />
        <Skeleton className="w-full max-w-[360px] h-[42px]" />
      </div>

      <Divider />

      {/* Core Vitals Section Skeleton */}
      <div className="space-y-6">
        <div className="space-y-1.5">
          <Skeleton className="w-[180px] h-[22px]" />
          <Skeleton className="w-[340px] h-[16px]" />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="space-y-2">
            <Skeleton className="w-[140px] h-[16px]" />
            <Skeleton className="w-full h-[40px]" />
            <Skeleton className="w-[200px] h-[14px]" />
          </div>
          <div className="space-y-2">
            <Skeleton className="w-[140px] h-[16px]" />
            <Skeleton className="w-full h-[40px]" />
            <Skeleton className="w-[200px] h-[14px]" />
          </div>
          <div className="space-y-2">
            <Skeleton className="w-[140px] h-[16px]" />
            <Skeleton className="w-full h-[40px]" />
            <Skeleton className="w-[200px] h-[14px]" />
          </div>
          <div className="space-y-2">
            <Skeleton className="w-[140px] h-[16px]" />
            <Skeleton className="w-full h-[40px]" />
            <Skeleton className="w-[200px] h-[14px]" />
          </div>
          <div className="md:col-span-2 space-y-2">
            <Skeleton className="w-[160px] h-[16px]" />
            <Skeleton className="w-full h-[40px]" />
            <Skeleton className="w-[240px] h-[14px]" />
          </div>
        </div>
      </div>

      <Divider />

      {/* Precision Boosters Box Skeleton */}
      <div className="rounded-[var(--radius-xl)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] p-6 space-y-6">
        <div className="space-y-1.5">
          <Skeleton className="w-[220px] h-[20px]" />
          <Skeleton className="w-[380px] h-[16px]" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Skeleton className="w-full h-[40px]" />
          <Skeleton className="w-full h-[40px]" />
          <Skeleton className="w-full h-[40px]" />
          <Skeleton className="w-full h-[40px]" />
        </div>
      </div>

      {/* Actions Skeleton */}
      <div className="flex items-center justify-end gap-3 pt-4">
        <Skeleton className="w-[100px] h-[40px]" />
        <Skeleton className="w-[160px] h-[40px]" />
      </div>
    </div>
  );
}
