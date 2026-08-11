"use client";

import { motion } from "motion/react";
import type { AnalysisResponse, SeasonCode } from "@/lib/api/types";
import { SEASON_FALLBACK_LABELS, SEASON_THEMES } from "@/lib/season-theme";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

/**
 * 결과 히어로 카드.
 *
 * 확률·마진을 함께 보여주는 이유: "가을 웜입니다"라고 단정하는 대신
 * 판정의 확실성을 그대로 전달한다. topTwoMargin이 작으면 "두 계절
 * 사이"임을 명시한다 — 55% vs 44%는 사실상 동점이다 (05-api-spec.md §10).
 */

/** 이 값보다 마진이 작으면 경계 판정 안내를 띄운다. 큐레이션이 아니라 표현 기준이라 프론트 소유. */
const BOUNDARY_MARGIN = 0.15;

export function SeasonCard({ result }: { result: AnalysisResponse }) {
  const { season, confidence, undertone, undertoneConfidence, topTwoMargin, probabilities } =
    result;
  const theme = SEASON_THEMES[season.code];

  const runnerUp = (Object.entries(probabilities) as [SeasonCode, number][])
    .filter(([code]) => code !== season.code)
    .sort((a, b) => b[1] - a[1])[0];

  return (
    <motion.section
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className={cn("rounded-2xl border bg-gradient-to-br p-6 sm:p-8", theme.gradient)}
    >
      <div className="flex flex-col items-center gap-3 text-center">
        <motion.span
          className="text-5xl"
          initial={{ scale: 0.5, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ delay: 0.15, type: "spring", bounce: 0.4 }}
          aria-hidden
        >
          {season.emoji}
        </motion.span>
        <div>
          <h2 className="text-3xl font-bold tracking-tight">{season.labelKo}</h2>
          <p className={cn("mt-0.5 text-sm font-medium", theme.text)}>{season.labelEn}</p>
        </div>
        <div className="flex flex-wrap justify-center gap-1.5">
          {season.keywords.map((keyword) => (
            <Badge key={keyword} variant="secondary" className={theme.badge}>
              {keyword}
            </Badge>
          ))}
        </div>
        <p className="max-w-xl text-sm leading-relaxed text-foreground/80">{season.description}</p>

        <div className="mt-2 flex flex-wrap items-center justify-center gap-x-6 gap-y-1 text-sm">
          <span>
            판정 신뢰도 <strong className="tabular-nums">{(confidence * 100).toFixed(1)}%</strong>
          </span>
          <span>
            언더톤 <strong>{undertone === "warm" ? "웜" : "쿨"}</strong>{" "}
            <span className="text-muted-foreground tabular-nums">
              ({(undertoneConfidence * 100).toFixed(1)}%)
            </span>
          </span>
        </div>

        {topTwoMargin < BOUNDARY_MARGIN && runnerUp !== undefined && (
          <p className="mt-1 rounded-lg bg-background/60 px-3 py-2 text-xs text-foreground/75">
            {SEASON_FALLBACK_LABELS[runnerUp[0]]}({(runnerUp[1] * 100).toFixed(1)}%)와의 차이가
            작아 <strong>두 계절 사이의 경계 판정</strong>입니다. 다른 조명에서 다시 찍으면
            결과가 달라질 수 있어요.
          </p>
        )}
      </div>
    </motion.section>
  );
}
