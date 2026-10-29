"use client";

import React, { useState } from 'react';
import { DailyForecastRow } from '@/lib/api/dev-a/cash-forecast';
import { ForecastCopy } from './forecast-copy';
import { formatPkr } from '@/lib/utils/decimal-display';

interface DailyForecastChartProps {
  rows: DailyForecastRow[];
  copy: ForecastCopy;
  currency: string;
}

export function DailyForecastChart({ rows, copy, currency }: DailyForecastChartProps) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  if (!rows || rows.length === 0) {
    return null;
  }

  // Parse values
  const numericData = rows.map((r, idx) => ({
    idx,
    date: r.date,
    closing: parseFloat(r.projectedClosingCash),
    inflows: parseFloat(r.totalExpectedInflows),
    outflows: parseFloat(r.totalExpectedOutflows),
    isDeficit: r.isDeficit,
    rawRow: r,
  }));

  const allClosing = numericData.map((d) => d.closing);
  const allFlows = numericData.flatMap((d) => [d.inflows, d.outflows]);

  const minVal = Math.min(0, ...allClosing);
  const maxVal = Math.max(1000, ...allClosing, ...allFlows);
  const range = maxVal - minVal || 1;

  const svgWidth = 800;
  const svgHeight = 260;
  const padLeft = 60;
  const padRight = 30;
  const padTop = 30;
  const padBottom = 40;

  const chartWidth = svgWidth - padLeft - padRight;
  const chartHeight = svgHeight - padTop - padBottom;

  const getX = (idx: number) => {
    if (numericData.length <= 1) return padLeft + chartWidth / 2;
    return padLeft + (idx / (numericData.length - 1)) * chartWidth;
  };

  const getY = (val: number) => {
    return padTop + chartHeight - ((val - minVal) / range) * chartHeight;
  };

  const zeroY = getY(0);

  // Line path for closing balance
  const linePoints = numericData.map((d) => `${getX(d.idx)},${getY(d.closing)}`).join(' ');

  // Area path
  const areaPoints = `${getX(0)},${zeroY} ` + linePoints + ` ${getX(numericData.length - 1)},${zeroY}`;

  const hoveredItem = hoveredIndex !== null && hoveredIndex >= 0 && hoveredIndex < numericData.length
    ? numericData[hoveredIndex]
    : null;

  return (
    <div className="bg-[var(--color-surface-card)] rounded-[var(--radius-lg)] border border-[var(--color-border-default)] p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="font-semibold text-base text-[var(--color-text-primary)]">
            {copy.chartTitle}
          </h3>
          <p className="text-xs text-[var(--color-text-muted)]">
            {rows.length} {copy.tableDate.toLowerCase()} ({rows[0].date} &rarr; {rows[rows.length - 1].date})
          </p>
        </div>

        {/* Legend */}
        <div className="flex items-center gap-4 text-xs select-none">
          <div className="flex items-center gap-1.5">
            <span className="w-3 h-0.5 bg-teal-600 dark:bg-teal-400 rounded" />
            <span className="text-[var(--color-text-secondary)]">{copy.chartLegendBalance}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 bg-emerald-500 rounded-sm" />
            <span className="text-[var(--color-text-secondary)]">{copy.chartLegendInflow}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 bg-rose-500 rounded-sm" />
            <span className="text-[var(--color-text-secondary)]">{copy.chartLegendOutflow}</span>
          </div>
        </div>
      </div>

      {/* SVG Chart - LTR layout */}
      <div dir="ltr" className="relative w-full overflow-x-auto">
        <svg
          viewBox={`0 0 ${svgWidth} ${svgHeight}`}
          className="w-full h-auto min-w-[600px] select-none"
          role="img"
          aria-label={copy.chartTitle}
        >
          {/* Background Grid Lines */}
          <line
            x1={padLeft}
            y1={padTop}
            x2={svgWidth - padRight}
            y2={padTop}
            stroke="var(--color-border-subtle)"
            strokeDasharray="3 3"
          />
          <line
            x1={padLeft}
            y1={getY(maxVal / 2)}
            x2={svgWidth - padRight}
            y2={getY(maxVal / 2)}
            stroke="var(--color-border-subtle)"
            strokeDasharray="3 3"
          />

          {/* Zero Line */}
          <line
            x1={padLeft}
            y1={zeroY}
            x2={svgWidth - padRight}
            y2={zeroY}
            stroke={minVal < 0 ? '#ef4444' : 'var(--color-border-default)'}
            strokeWidth={minVal < 0 ? '1.5' : '1'}
            strokeDasharray={minVal < 0 ? '4 2' : undefined}
          />
          <text
            x={padLeft - 8}
            y={zeroY + 4}
            textAnchor="end"
            fontSize="10"
            className="fill-neutral-400 font-mono"
          >
            0
          </text>
          <text
            x={padLeft - 8}
            y={padTop + 4}
            textAnchor="end"
            fontSize="10"
            className="fill-neutral-400 font-mono"
          >
            {formatPkr(maxVal)}
          </text>

          {/* Shaded Area */}
          <polygon
            points={areaPoints}
            fill="rgba(13, 148, 136, 0.08)"
          />

          {/* Daily Inflow / Outflow Bars */}
          {numericData.map((d) => {
            const cx = getX(d.idx);
            const barW = Math.max(2, Math.min(10, chartWidth / (numericData.length * 2.5)));
            const inflowH = (d.inflows / range) * chartHeight;
            const outflowH = (d.outflows / range) * chartHeight;

            return (
              <g key={`bar-${d.idx}`}>
                {d.inflows > 0 && (
                  <rect
                    x={cx - barW - 1}
                    y={zeroY - inflowH}
                    width={barW}
                    height={inflowH}
                    fill="#10b981"
                    opacity={0.8}
                    rx={1}
                  />
                )}
                {d.outflows > 0 && (
                  <rect
                    x={cx + 1}
                    y={zeroY}
                    width={barW}
                    height={outflowH}
                    fill="#f43f5e"
                    opacity={0.8}
                    rx={1}
                  />
                )}
              </g>
            );
          })}

          {/* Closing Cash Balance Curve */}
          <polyline
            fill="none"
            stroke="#0d9488"
            strokeWidth="2.5"
            strokeLinejoin="round"
            strokeLinecap="round"
            points={linePoints}
          />

          {/* Data Points and Interaction Circles */}
          {numericData.map((d) => {
            const cx = getX(d.idx);
            const cy = getY(d.closing);
            const isHovered = hoveredIndex === d.idx;

            return (
              <g
                key={`point-${d.idx}`}
                onMouseEnter={() => setHoveredIndex(d.idx)}
                onMouseLeave={() => setHoveredIndex(null)}
                className="cursor-pointer"
              >
                <circle
                  cx={cx}
                  cy={cy}
                  r={isHovered ? 5 : d.isDeficit ? 3.5 : 2}
                  fill={d.isDeficit ? '#ef4444' : '#0d9488'}
                  stroke="#ffffff"
                  strokeWidth="1.5"
                />
                {/* Transparent hit target */}
                <rect
                  x={cx - 10}
                  y={padTop}
                  width="20"
                  height={chartHeight}
                  fill="transparent"
                />
              </g>
            );
          })}

          {/* X Axis Labels */}
          {numericData
            .filter((_, i) => i === 0 || i === numericData.length - 1 || i % Math.ceil(numericData.length / 6) === 0)
            .map((d) => (
              <text
                key={`label-${d.idx}`}
                x={getX(d.idx)}
                y={svgHeight - 12}
                textAnchor="middle"
                fontSize="10"
                className="fill-[var(--color-text-muted)] font-mono"
              >
                {d.date.substring(5)}
              </text>
            ))}
        </svg>

        {/* Hover Tooltip Box */}
        {hoveredItem && (
          <div
            className="absolute top-2 z-10 bg-neutral-900 text-white text-xs rounded-md shadow-lg p-2.5 pointer-events-none space-y-1 font-mono"
            style={{
              left: `${numericData.length > 1 ? Math.min(Math.max(5, (hoveredItem.idx / (numericData.length - 1)) * 80 + 5), 80) : 50}%`,
            }}
          >
            <div className="font-bold border-b border-neutral-700 pb-1 text-neutral-200">
              {hoveredItem.date} (Day {hoveredItem.idx})
            </div>
            <div className="flex justify-between gap-3 text-emerald-400">
              <span>{copy.tableInflows}:</span>
              <span>+{currency} {formatPkr(hoveredItem.inflows)}</span>
            </div>
            <div className="flex justify-between gap-3 text-rose-400">
              <span>{copy.tableOutflows}:</span>
              <span>-{currency} {formatPkr(hoveredItem.outflows)}</span>
            </div>
            <div className="flex justify-between gap-3 font-bold border-t border-neutral-700 pt-1 text-teal-300">
              <span>{copy.tableClosing}:</span>
              <span>{currency} {formatPkr(hoveredItem.closing)}</span>
            </div>
            {hoveredItem.isDeficit && (
              <div className="text-red-400 font-bold text-[11px] pt-0.5">
                &iexcl; {copy.statusDeficit}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

