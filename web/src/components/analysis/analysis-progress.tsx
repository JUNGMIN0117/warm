"use client";

import { useEffect, useState } from "react";
import { motion } from "motion/react";
import { Check, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * 분석 대기 중 단계 애니메이션 (하이브리드 시각화의 앞부분).
 *
 * 표시하는 단계 이름은 실제 파이프라인 순서 그대로다(ADR-004의 최종 순서).
 * 다만 API는 요청 하나로 끝나므로 단계별 진짜 진행률은 알 수 없다 —
 * 이 컴포넌트는 시간 기반으로 페이스를 맞추되 마지막 단계에서 응답이 올
 * 때까지 머문다. "지금 무슨 일이 일어나는지"의 교육적 표현이지 실측
 * 진행률이 아니며, 그 구분을 docs/06-frontend.md에 남겼다.
 */

const STEPS = [
  "얼굴 검출",
  "배경 기준 화이트밸런스 보정",
  "눈·눈썹·입술 제외 피부 마스킹",
  "피부 픽셀 색 통계 (중앙값)",
  "3축 판정",
] as const;

const STEP_INTERVAL_MS = 650;

export function AnalysisProgress() {
  const [reached, setReached] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setReached((current) => {
        // 마지막 단계는 응답이 올 때까지 "진행 중"으로 머문다.
        if (current >= STEPS.length - 1) {
          clearInterval(timer);
          return current;
        }
        return current + 1;
      });
    }, STEP_INTERVAL_MS);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="mx-auto w-full max-w-md space-y-4 py-8" role="status" aria-live="polite">
      <p className="text-center text-lg font-semibold">사진을 분석하고 있습니다</p>
      <ol className="space-y-3">
        {STEPS.map((label, index) => {
          const done = index < reached;
          const active = index === reached;
          return (
            <motion.li
              key={label}
              initial={{ opacity: 0, x: -12 }}
              animate={{ opacity: done || active ? 1 : 0.35, x: 0 }}
              transition={{ delay: index * 0.08 }}
              className="flex items-center gap-3"
            >
              <span
                className={cn(
                  "flex size-7 shrink-0 items-center justify-center rounded-full border text-xs",
                  done && "border-primary bg-primary text-primary-foreground",
                  active && "border-primary text-primary",
                  !done && !active && "border-muted-foreground/30 text-muted-foreground",
                )}
              >
                {done ? (
                  <Check className="size-4" aria-hidden />
                ) : active ? (
                  <Loader2 className="size-4 animate-spin" aria-hidden />
                ) : (
                  index + 1
                )}
              </span>
              <span className={cn("text-sm", active && "font-medium")}>{label}</span>
            </motion.li>
          );
        })}
      </ol>
      <p className="text-center text-xs text-muted-foreground">
        단계 표시는 처리 순서의 안내이며 실시간 진행률은 아닙니다
      </p>
    </div>
  );
}
