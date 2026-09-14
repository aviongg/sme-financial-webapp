"use client";

import React, { useState } from "react";
import { ArrowUpRight, ArrowDownRight } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { FinancialValue } from "@/components/ui/FinancialValue";
import { useLanguage } from "@/lib/i18n/context";
import { formatPKR } from "@/lib/utils/currency";
import { cn } from "@/lib/utils/cn";
import type { MonthlyRecordResponse } from "@/types/financial";

export interface CashFlowTrendProps {
  records: MonthlyRecordResponse[];
  className?: string;
}

/**
 * CashFlowTrend
 * Section 8.3 of Design System:
 * - Chronology strictly LTR even in Urdu RTL mode.
 * - Tabular numbers with PKR formatting.
 * - Inflow vs Outflow paired bars.
 * - Summary metrics: Inflow, Outflow, Net Surplus/Deficit, Ending Cash.
 */
export function CashFlowTrend({ records, className }: CashFlowTrendProps) {
  const { t } = useLanguage();
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  // Chronological order (oldest to newest)
  const sortedRecords = [...records].sort((a, b) => a.month.localeCompare(b.month));

  // If no records, fallback
  if (sortedRecords.length === 0) {
    return null;
  }

  // Latest month record for summary cards
  const latest = sortedRecords[sortedRecords.length - 1];
  const netLatest = latest.cashInflow - latest.cashOutflow;
  const isSurplus = netLatest >= 0;

  // Calculate scaling for SVG chart
  const maxVal = Math.max(
    ...sortedRecords.map((r) => Math.max(r.cashInflow, r.cashOutflow)),
    100000
  );
  // Nice ceiling rounded up to nearest 500k
  const ceiling = Math.ceil((maxVal * 1.15) / 500000) * 500000;

  // Chart layout dimensions
  const chartHeight = 160;
  const chartWidth = 520;
  const padLeft = 60;
  const padRight = 20;
  const padTop = 15;
  const padBottom = 30;
  const plotWidth = chartWidth - padLeft - padRight;
  const plotHeight = chartHeight - padTop - padBottom;

  const count = sortedRecords.length;
  const groupWidth = plotWidth / count;
  const barWidth = Math.min(22, Math.max(12, groupWidth * 0.32));

  // Format month label (e.g. "2026-08" -> "Aug '26")
  const formatMonthLabel = (m: string) => {
    const [year, month] = m.split("-");
    const date = new Date(Number(year), Number(month) - 1, 1);
    return date.toLocaleDateString("en-US", { month: "short" });
  };

  return (
    <Card elevation={0} padding="lg" className={cn("border-[var(--color-border-default)] text-start", className)}>
      {/* Header & Legend */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-[var(--color-border-subtle)]">
        <div>
          <h3 className="text-[16px] sm:text-[18px] font-semibold text-[var(--color-text-primary)] font-heading">
            {t.dashboard.cashFlowTrendTitle}
          </h3>
          <p className="text-[13px] text-[var(--color-text-muted)] mt-0.5">
            {t.dashboard.cashFlowTrendSubtitle}
          </p>
        </div>

        {/* Legend */}
        <div className="flex items-center gap-4 text-[12px] font-medium text-[var(--color-text-secondary)]">
          <div className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded-sm bg-[var(--color-brand-primary)]" />
            <span>{t.dashboard.inflowLegend}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded-sm bg-[#64748B]" />
            <span>{t.dashboard.outflowLegend}</span>
          </div>
        </div>
      </div>

      {/* SVG Bar Chart (Strictly LTR chronology) */}
      <div className="py-4 overflow-x-auto" dir="ltr">
        <div className="min-w-[480px]">
          <svg
            viewBox={`0 0 ${chartWidth} ${chartHeight}`}
            className="w-full h-auto overflow-visible select-none"
            role="region"
            aria-label="Cash flow trend chart comparing monthly inflows and outflows"
          >
            {/* Horizontal Gridlines & Y-Axis Labels */}
            {[0, 0.5, 1].map((ratio) => {
              const y = padTop + plotHeight * (1 - ratio);
              const labelVal = ceiling * ratio;
              return (
                <g key={ratio}>
                  <line
                    x1={padLeft}
                    y1={y}
                    x2={chartWidth - padRight}
                    y2={y}
                    stroke="var(--color-border-subtle)"
                    strokeDasharray={ratio > 0 ? "3 3" : undefined}
                  />
                  <text
                    x={padLeft - 8}
                    y={y + 3}
                    textAnchor="end"
                    fontSize="10"
                    fill="var(--color-text-muted)"
                    className="tabular-nums font-sans"
                  >
                    {formatPKR(labelVal, { compact: true, locale: "en" })}
                  </text>
                </g>
              );
            })}

            {/* Monthly Bar Groups */}
            {sortedRecords.map((r, i) => {
              const groupCenterX = padLeft + i * groupWidth + groupWidth / 2;
              const inflowH = (r.cashInflow / ceiling) * plotHeight;
              const outflowH = (r.cashOutflow / ceiling) * plotHeight;
              const inflowY = padTop + plotHeight - inflowH;
              const outflowY = padTop + plotHeight - outflowH;
              const isHovered = hoveredIndex === i;

              return (
                <g
                  key={r.id || r.month}
                  onMouseEnter={() => setHoveredIndex(i)}
                  onMouseLeave={() => setHoveredIndex(null)}
                  className="cursor-pointer transition-opacity"
                  tabIndex={0}
                  role="button"
                  aria-label={`${r.month}: Inflow ${formatPKR(r.cashInflow)}, Outflow ${formatPKR(r.cashOutflow)}`}
                >
                  {/* Subtle hover background highlight */}
                  {isHovered && (
                    <rect
                      x={groupCenterX - groupWidth / 2 + 2}
                      y={padTop}
                      width={groupWidth - 4}
                      height={plotHeight}
                      fill="var(--color-surface-hover)"
                      rx="4"
                    />
                  )}

                  {/* Cash Inflow Bar (Brand Primary) */}
                  <rect
                    x={groupCenterX - barWidth - 1.5}
                    y={inflowY}
                    width={barWidth}
                    height={Math.max(2, inflowH)}
                    fill="var(--color-brand-primary)"
                    rx="3"
                    className="transition-all duration-300"
                  />

                  {/* Cash Outflow Bar (Slate) */}
                  <rect
                    x={groupCenterX + 1.5}
                    y={outflowY}
                    width={barWidth}
                    height={Math.max(2, outflowH)}
                    fill="#64748B"
                    rx="3"
                    className="transition-all duration-300"
                  />

                  {/* Month Label */}
                  <text
                    x={groupCenterX}
                    y={chartHeight - 8}
                    textAnchor="middle"
                    fontSize="11"
                    fontWeight={isHovered ? "600" : "400"}
                    fill={isHovered ? "var(--color-text-primary)" : "var(--color-text-secondary)"}
                    className="tabular-nums font-sans"
                  >
                    {formatMonthLabel(r.month)}
                  </text>
                </g>
              );
            })}
          </svg>
        </div>
      </div>

      {/* Active Month Detail Tooltip / Indicator */}
      {hoveredIndex !== null && (
        <div className="mb-4 p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-3 text-[12px]">
          <span className="font-semibold text-[var(--color-text-primary)]">
            {sortedRecords[hoveredIndex].month}
          </span>
          <div className="flex items-center gap-4">
            <span className="text-[var(--color-brand-primary)] font-medium">
              {t.dashboard.inflowCol}:{" "}
              <FinancialValue value={sortedRecords[hoveredIndex].cashInflow} size="sm" />
            </span>
            <span className="text-[var(--color-text-secondary)] font-medium">
              {t.dashboard.outflowCol}:{" "}
              <FinancialValue value={sortedRecords[hoveredIndex].cashOutflow} size="sm" />
            </span>
            <span
              className={cn(
                "font-semibold",
                sortedRecords[hoveredIndex].cashInflow >= sortedRecords[hoveredIndex].cashOutflow
                  ? "text-[var(--color-health-strong)]"
                  : "text-[var(--color-health-risk)]"
              )}
            >
              {sortedRecords[hoveredIndex].cashInflow >= sortedRecords[hoveredIndex].cashOutflow
                ? t.dashboard.netSurplus
                : t.dashboard.netDeficit}
              :{" "}
              <FinancialValue
                value={
                  sortedRecords[hoveredIndex].cashInflow -
                  sortedRecords[hoveredIndex].cashOutflow
                }
                size="sm"
                colorIntent="semantic"
              />
            </span>
          </div>
        </div>
      )}

      {/* Supporting Metric Tiles (Latest Month) */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 pt-3 border-t border-[var(--color-border-subtle)]">
        {/* Total Inflow */}
        <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)]">
          <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-text-muted)] block mb-1">
            {t.dashboard.totalInflow}
          </span>
          <FinancialValue value={latest.cashInflow} size="md" />
        </div>

        {/* Total Outflow */}
        <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)]">
          <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-text-muted)] block mb-1">
            {t.dashboard.totalOutflow}
          </span>
          <FinancialValue value={latest.cashOutflow} size="md" />
        </div>

        {/* Net Movement */}
        <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)]">
          <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-text-muted)] block mb-1">
            {isSurplus ? t.dashboard.netSurplus : t.dashboard.netDeficit}
          </span>
          <div className="flex items-center gap-1.5">
            {isSurplus ? (
              <ArrowUpRight className="w-4 h-4 text-[var(--color-health-strong)] shrink-0" />
            ) : (
              <ArrowDownRight className="w-4 h-4 text-[var(--color-health-risk)] shrink-0" />
            )}
            <FinancialValue value={netLatest} size="md" colorIntent="semantic" showSign />
          </div>
        </div>

        {/* Ending Cash */}
        <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)]">
          <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-brand-navy)] block mb-1">
            {t.dashboard.endingCashBalance}
          </span>
          <FinancialValue value={latest.cashBalanceEom} size="md" className="text-[var(--color-brand-primary)]" />
        </div>
      </div>
    </Card>
  );
}
