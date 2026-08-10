"use client";

import { motion } from "motion/react";

import type { Probabilities, SeasonCode } from "@/lib/api/types";
import { SEASON_FALLBACK_LABELS, SEASON_ORDER, SEASON_THEMES } from "@/lib/season";

/**
 * 4계절 확률 분포 — top-1만 보여주지 않는다는 도메인 불변식의 UI 대응물.
 *
 * "62% 봄 / 35% 여름"과 "97% 겨울"은 다른 결과다. 분포 전체를 보여야
 * 사용자가 그 차이를 안다.
 */
export function ProbabilityBars({
  probabilities,
  top,
}: {
  probabilities: Probabilities;
  top: SeasonCode;
}) {
  return (
    <div className="space-y-2.5">
      {SEASON_ORDER.map((code) => {
        const percent = Math.round(probabilities[code] * 100);
        const isTop = code === top;
        return (
          <div key={code} className="flex items-center gap-3">
            <span
              className={`w-16 shrink-0 text-sm ${isTop ? "font-bold" : "text-muted-foreground"}`}
            >
              {SEASON_FALLBACK_LABELS[code]}
            </span>
            <div className="h-3 flex-1 overflow-hidden rounded-full bg-muted">
              <motion.div
                className="h-full rounded-full"
                style={{
                  backgroundColor: SEASON_THEMES[code].accent,
                  opacity: isTop ? 1 : 0.45,
                }}
                initial={{ width: 0 }}
                animate={{ width: `${percent}%` }}
                transition={{ duration: 0.6, ease: "easeOut" }}
              />
            </div>
            <span
              className={`w-10 shrink-0 text-right font-mono text-sm ${
                isTop ? "font-bold" : "text-muted-foreground"
              }`}
            >
              {percent}%
            </span>
          </div>
        );
      })}
    </div>
  );
}
