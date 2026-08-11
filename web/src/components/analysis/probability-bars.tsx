"use client";

import { motion } from "motion/react";
import type { SeasonCode } from "@/lib/api/types";
import { SEASON_FALLBACK_LABELS, SEASON_THEMES } from "@/lib/season-theme";
import { cn } from "@/lib/utils";

/**
 * 4계절 확률 분포.
 *
 * top-1만 보여주지 않는 것이 도메인 불변식 4다 — "62% 봄 / 35% 여름"인
 * 경계 케이스와 "97% 겨울"인 확실한 케이스를 사용자가 구분할 수 있어야 한다.
 */

interface ProbabilityBarsProps {
  probabilities: Record<SeasonCode, number>;
  winner: SeasonCode;
}

export function ProbabilityBars({ probabilities, winner }: ProbabilityBarsProps) {
  const sorted = (Object.entries(probabilities) as [SeasonCode, number][]).sort(
    (a, b) => b[1] - a[1],
  );

  return (
    <div className="space-y-2.5">
      {sorted.map(([code, probability], index) => (
        <div key={code} className="flex items-center gap-3 text-sm">
          <span className={cn("w-16 shrink-0", code === winner && "font-semibold")}>
            {SEASON_FALLBACK_LABELS[code]}
          </span>
          <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-muted">
            <motion.div
              className={cn("h-full rounded-full", SEASON_THEMES[code].bar)}
              initial={{ width: 0 }}
              animate={{ width: `${Math.round(probability * 100)}%` }}
              transition={{ duration: 0.7, delay: index * 0.1, ease: "easeOut" }}
            />
          </div>
          <span
            className={cn(
              "w-12 shrink-0 text-right tabular-nums",
              code === winner ? "font-semibold" : "text-muted-foreground",
            )}
          >
            {(probability * 100).toFixed(1)}%
          </span>
        </div>
      ))}
    </div>
  );
}
