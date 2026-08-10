"use client";

import { motion } from "motion/react";

import type { AnalysisAxis } from "@/lib/api/types";

/**
 * 판정 3축 게이지 — "왜 이 계절인가"의 시각화.
 *
 * normalized(0..1)를 좌우 라벨 사이의 마커 위치로 표현한다.
 * 막대 채우기가 아니라 위치 마커인 이유: 이 축들은 "많고 적음"이 아니라
 * "어느 쪽에 가까운가"이므로, 절반쯤 찬 막대는 오독을 부른다.
 */
export function AxisGauge({ axis, accent }: { axis: AnalysisAxis; accent: string }) {
  const percent = Math.round(axis.normalized * 100);

  return (
    <div>
      <div className="mb-1 flex items-baseline justify-between text-sm">
        <span className="text-muted-foreground">{axis.lowLabel}</span>
        <span className="text-muted-foreground">{axis.highLabel}</span>
      </div>
      <div
        className="relative h-2 rounded-full bg-muted"
        role="meter"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-label={`${axis.lowLabel} — ${axis.highLabel}`}
      >
        <motion.span
          className="absolute top-1/2 h-4 w-4 rounded-full border-2 border-background shadow"
          style={{ backgroundColor: accent, y: "-50%", x: "-50%" }}
          initial={{ left: "50%" }}
          animate={{ left: `${percent}%` }}
          transition={{ type: "spring", stiffness: 120, damping: 18 }}
        />
      </div>
      <p className="mt-1.5 text-sm">
        {axis.interpretation}
        <span className="ml-2 font-mono text-xs text-muted-foreground">
          측정값 {axis.rawValue.toFixed(2)}
        </span>
      </p>
    </div>
  );
}
