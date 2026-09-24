"use client";

import React, { useId, useState } from "react";
import { ArrowUpRight, ArrowDownRight, ChevronDown } from "lucide-react";
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
  const { t, locale } = useLanguage();
  const [expanded, setExpanded] = useState(false);
  const [period, setPeriod] = useState("6");
  const detailId = useId();
  const ur = locale === "ur";
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  // Chronological order (oldest to newest)
  const allRecords = [...records].sort((a, b) => a.month.localeCompare(b.month));
  const monthNumber = (month: string) => {
    const [year, number] = month.split("-").map(Number);
    return year * 12 + number - 1;
  };
  const lastMonth = allRecords.at(-1)?.month;
  const sortedRecords = allRecords.filter((record) => period === "all" ||
    (lastMonth && monthNumber(lastMonth) - monthNumber(record.month) < Number(period)));

  // If no records, fallback
  if (sortedRecords.length === 0) {
    return null;
  }

  // Latest month record for summary cards
  const latest = sortedRecords[sortedRecords.length - 1];
  const totalInflow = sortedRecords.reduce((total, record) => total + record.cashInflow, 0);
  const totalOutflow = sortedRecords.reduce((total, record) => total + record.cashOutflow, 0);
  const netLatest = totalInflow - totalOutflow;
  const isSurplus = netLatest >= 0;

  // Calculate scaling for SVG chart
  const maxVal = Math.max(
    ...sortedRecords.map((r) => Math.max(r.cashInflow, r.cashOutflow)),
    1
  );
  // Scale to the supplied values, including small and zero-only records.
  const step = 10 ** Math.floor(Math.log10(maxVal));
  const ceiling = Math.ceil((maxVal * 1.15) / step) * step;

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
    return date.toLocaleDateString("en-US", { month: "short", year: "2-digit" });
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

      <div className="flex flex-wrap items-center justify-between gap-3 py-4">
        <p className="text-xs text-[var(--color-text-muted)]" dir="ltr">{sortedRecords[0].month} – {latest.month}</p>
        <label className="flex items-center gap-2 text-sm">
          {ur ? "مدت" : "Period"}
          <select value={period} onChange={(event) => { setPeriod(event.target.value); setHoveredIndex(null); }} className="rounded-md border border-[var(--color-border-default)] p-2 bg-[var(--color-surface-card)]">
            <option value="3">{ur ? "آخری 3 ماہ" : "Last 3 months"}</option>
            <option value="6">{ur ? "آخری 6 ماہ" : "Last 6 months"}</option>
            <option value="12">{ur ? "آخری 12 ماہ" : "Last 12 months"}</option>
            <option value="all">{ur ? "تمام ریکارڈ" : "All records"}</option>
          </select>
        </label>
      </div>
      {/* Totals for the selected period; ending cash is the last recorded balance. */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 pt-3 border-t border-[var(--color-border-subtle)]">
        {/* Total Inflow */}
        <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)]">
          <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-text-muted)] block mb-1">
            {t.dashboard.totalInflow}
          </span>
          <FinancialValue value={totalInflow} size="md" />
        </div>

        {/* Total Outflow */}
        <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)]">
          <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-text-muted)] block mb-1">
            {t.dashboard.totalOutflow}
          </span>
          <FinancialValue value={totalOutflow} size="md" />
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
      <button type="button" aria-expanded={expanded} aria-controls={detailId} onClick={() => setExpanded((value) => !value)} className="mt-4 w-full flex items-center justify-center gap-2 rounded-md border border-[var(--color-border-default)] px-4 py-2 text-sm font-semibold text-[var(--color-brand-primary)] hover:bg-[var(--color-surface-hover)]">
        {expanded ? (ur ? "تفصیل بند کریں" : "Hide cash-flow details") : (ur ? "نقد بہاؤ کی تفصیل" : "View cash-flow details")}
        <ChevronDown className={cn("h-4 w-4 transition-transform", expanded && "rotate-180")} />
      </button>
      <div id={detailId} hidden={!expanded}>
        <p className="mt-4 text-xs text-[var(--color-text-muted)]">{ur ? "منتخب مدت کے محفوظ مہینے۔ جن مہینوں کا ریکارڈ نہیں، انہیں صفر نہیں سمجھا گیا۔" : "Recorded months in the selected period. Missing months are not treated as zero."}</p>
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
                  onFocus={() => setHoveredIndex(i)}
                  onBlur={() => setHoveredIndex(null)}
                  onClick={() => setHoveredIndex(i)}
                  onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setHoveredIndex(i); } }}
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
                    height={inflowH}
                    fill="var(--color-brand-primary)"
                    rx="3"
                    className="transition-all duration-300"
                  />

                  {/* Cash Outflow Bar (Slate) */}
                  <rect
                    x={groupCenterX + 1.5}
                    y={outflowY}
                    width={barWidth}
                    height={outflowH}
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
      {hoveredIndex !== null && sortedRecords[hoveredIndex] && (
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

        <div className="overflow-x-auto">
          <table className="w-full text-start text-sm">
            <caption className="sr-only">{ur ? "ماہانہ نقد بہاؤ" : "Monthly cash-flow details"}</caption>
            <thead><tr className="border-b border-[var(--color-border-default)]">
              {[t.dashboard.monthCol, t.dashboard.inflowCol, t.dashboard.outflowCol, t.dashboard.netCol, t.dashboard.endingCol].map((label) => <th key={label} scope="col" className="p-2 text-start">{label}</th>)}
            </tr></thead>
            <tbody>{sortedRecords.map((record) => <tr key={record.month} className="border-b border-[var(--color-border-subtle)]">
              <th scope="row" className="p-2 text-start font-medium" dir="ltr">{record.month}</th>
              {[record.cashInflow, record.cashOutflow, record.cashInflow - record.cashOutflow, record.cashBalanceEom].map((value, index) => <td key={index} className="p-2 whitespace-nowrap">{formatPKR(value, { locale })}</td>)}
            </tr>)}</tbody>
          </table>
        </div>
      </div>
    </Card>
  );
}
