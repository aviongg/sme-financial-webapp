"use client";

import React from "react";
import { cn } from "@/lib/utils/cn";
import type { HealthBand } from "@/types/financial";

export interface ScoreMeterProps {
  score: number;
  band?: HealthBand;
  isProvisional?: boolean;
  className?: string;
  size?: "md" | "lg";
}

/**
 * ScoreMeter
 * Segmented semi-circle gauge (Section 7.2 of Design System).
 * 4 segments corresponding to health bands:
 * - At Risk (0–39.99): #B91C1C
 * - Needs Attention (40–59.99): #B45309
 * - Stable (60–79.99): #0369A1
 * - Strong (80–100): #047857
 * Supporting visualization, not dominant. Accessible and RTL-independent.
 */
export function ScoreMeter({
  score,
  band = "stable",
  isProvisional = false,
  className,
  size = "md",
}: ScoreMeterProps) {
  const clampedScore = Math.max(0, Math.min(100, score));

  // Determine active band if not provided
  const activeBand: HealthBand =
    band ||
    (clampedScore >= 80
      ? "strong"
      : clampedScore >= 60
      ? "stable"
      : clampedScore >= 40
      ? "attention"
      : "risk");

  // Center (100, 95), Radius 75, StrokeWidth 10
  const cx = 100;
  const cy = 95;
  const r = 72;
  const strokeWidth = 10;

  // Band active colors
  const bandColors = {
    risk: "var(--color-health-risk)",
    attention: "var(--color-health-attention)",
    stable: "var(--color-health-stable)",
    strong: "var(--color-health-strong)",
  };

  // Dimmed background colors for inactive segments
  const inactiveColors = {
    risk: "#FEE2E2",
    attention: "#FEF3C7",
    stable: "#E0F2FE",
    strong: "#D1FAE5",
  };

  // Current marker color
  const markerColor = bandColors[activeBand];

  // Calculate needle/pip position (180 deg = left 0, 0 deg = right 100)
  const angleDeg = 180 - (clampedScore / 100) * 180;
  const angleRad = (angleDeg * Math.PI) / 180;
  const indicatorX = cx + r * Math.cos(angleRad);
  const indicatorY = cy - r * Math.sin(angleRad);

  // Helper to generate SVG arc path for a degree span [startDeg, endDeg]
  // 180 deg is far left, 0 deg is far right
  const describeArc = (startDeg: number, endDeg: number) => {
    const startRad = (startDeg * Math.PI) / 180;
    const endRad = (endDeg * Math.PI) / 180;

    const x1 = cx + r * Math.cos(startRad);
    const y1 = cy - r * Math.sin(startRad);
    const x2 = cx + r * Math.cos(endRad);
    const y2 = cy - r * Math.sin(endRad);

    const largeArcFlag = Math.abs(endDeg - startDeg) > 180 ? 1 : 0;

    return `M ${x1} ${y1} A ${r} ${r} 0 ${largeArcFlag} 1 ${x2} ${y2}`;
  };

  // 4 segments with a 2-degree gap between them:
  // Risk: 0-40% -> 180° down to 108° (with gap: 179° to 110°)
  // Attention: 40-60% -> 108° down to 72° (with gap: 106° to 74°)
  // Stable: 60-80% -> 72° down to 36° (with gap: 70° to 38°)
  // Strong: 80-100% -> 36° down to 0° (with gap: 34° to 1°)
  const segments = [
    {
      band: "risk" as HealthBand,
      start: 179,
      end: 110,
      activeColor: bandColors.risk,
      inactiveColor: inactiveColors.risk,
      isActive: clampedScore < 40,
    },
    {
      band: "attention" as HealthBand,
      start: 106,
      end: 74,
      activeColor: bandColors.attention,
      inactiveColor: inactiveColors.attention,
      isActive: clampedScore >= 40 && clampedScore < 60,
    },
    {
      band: "stable" as HealthBand,
      start: 70,
      end: 38,
      activeColor: bandColors.stable,
      inactiveColor: inactiveColors.stable,
      isActive: clampedScore >= 60 && clampedScore < 80,
    },
    {
      band: "strong" as HealthBand,
      start: 34,
      end: 1,
      activeColor: bandColors.strong,
      inactiveColor: inactiveColors.strong,
      isActive: clampedScore >= 80,
    },
  ];

  const dimensions =
    size === "lg"
      ? "w-[180px] h-[100px] sm:w-[200px] sm:h-[110px]"
      : "w-[140px] h-[80px] sm:w-[160px] sm:h-[90px]";

  return (
    <div
      className={cn("relative inline-flex flex-col items-center select-none", className)}
      role="img"
      aria-label={`Score meter gauge: ${clampedScore} out of 100, health band: ${activeBand}`}
    >
      <svg
        viewBox="0 0 200 115"
        className={cn("overflow-visible transition-all duration-300", dimensions)}
      >
        <defs>
          {/* Subtle drop shadow for indicator pip */}
          <filter id="pip-shadow" x="-50%" y="-50%" width="200%" height="200%">
            <feDropShadow dx="0" dy="1" stdDeviation="1.5" floodOpacity="0.25" />
          </filter>
        </defs>

        {/* 4 Health Band Segments */}
        {segments.map((seg) => (
          <path
            key={seg.band}
            d={describeArc(seg.start, seg.end)}
            fill="none"
            stroke={seg.isActive ? seg.activeColor : seg.inactiveColor}
            strokeWidth={strokeWidth}
            strokeLinecap="round"
            strokeDasharray={isProvisional ? "3 2" : undefined}
            className="transition-colors duration-300"
          />
        ))}

        {/* Baseline Center Arc Notch / Target Tick */}
        <circle cx={cx} cy={cy} r="3" fill="var(--color-text-muted)" opacity="0.4" />

        {/* Score Indicator Pip */}
        <g filter="url(#pip-shadow)">
          {/* Outer Ring */}
          <circle
            cx={indicatorX}
            cy={indicatorY}
            r="7"
            fill="#FFFFFF"
            stroke={markerColor}
            strokeWidth="3.5"
            className="transition-all duration-500 ease-out"
          />
          {/* Inner Dot */}
          <circle
            cx={indicatorX}
            cy={indicatorY}
            r="2.5"
            fill={markerColor}
            className="transition-all duration-500 ease-out"
          />
        </g>
      </svg>
    </div>
  );
}
