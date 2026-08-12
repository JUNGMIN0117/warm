"use client";

import { motion } from "motion/react";
import type { AxisView } from "@/lib/api/types";

/**
 * 3축 판정 근거 게이지.
 *
 * 이 화면이 이 프로젝트의 존재 이유다 — 원본은 CNN이 뱉은 계절 하나가
 * 전부였고 사용자가 검증할 방법이 없었다. "h°가 68.4라서 웜"까지
 * 보여주는 것이 블랙박스 탈출이다 (docs/00-overview.md).
 */

const AXIS_TITLES: Record<string, string> = {
  undertone: "언더톤",
  depth: "명도",
  clarity: "청탁",
};

const AXIS_UNITS: Record<string, string> = {
  // undertone 원시값은 h°가 아니라 명도 보정 노란기다 (ADR-010)
  undertone: "보정 b*",
  depth: "ITA°",
  clarity: "C*",
};

export function AxisGauges({ axes }: { axes: AxisView[] }) {
  return (
    <div className="space-y-5">
      {axes.map((axis, index) => {
        const percent = Math.round(axis.normalized * 100);
        return (
          <div key={axis.name} className="space-y-1.5">
            <div className="flex items-baseline justify-between">
              <span className="text-sm font-medium">
                {AXIS_TITLES[axis.name] ?? axis.name}
                <span className="ml-2 text-xs text-muted-foreground tabular-nums">
                  {AXIS_UNITS[axis.name] ?? ""} {axis.rawValue.toFixed(1)}
                </span>
              </span>
              <span className="text-xs text-muted-foreground">{axis.interpretation}</span>
            </div>
            <div className="relative h-3 rounded-full bg-gradient-to-r from-muted via-muted/60 to-muted">
              {/* 축 트랙 — 마커 위치가 normalized 0~1 */}
              <motion.div
                className="absolute top-1/2 size-4 -translate-y-1/2 rounded-full border-2 border-background bg-primary shadow"
                initial={{ left: "50%" }}
                animate={{ left: `calc(${percent}% - 8px)` }}
                transition={{ duration: 0.8, delay: 0.2 + index * 0.15, type: "spring", bounce: 0.2 }}
              />
            </div>
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>{axis.lowLabel}</span>
              <span>{axis.highLabel}</span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
