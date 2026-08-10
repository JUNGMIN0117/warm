"use client";

import { motion } from "motion/react";

/**
 * 분석 대기 화면.
 *
 * 진행률 바를 쓰지 않는다 — 서버는 진행 이벤트를 주지 않는 동기 API이고,
 * 가짜 진행률은 이 프로젝트의 태도(측정하지 않은 것을 주장하지 않는다)와
 * 어긋난다. 대신 실제 파이프라인 순서를 보여주며 기다리게 한다.
 */
const PIPELINE_STEPS = [
  "얼굴 검출",
  "화이트밸런스 정규화",
  "피부 픽셀 추출",
  "색채 통계 측정",
  "계절 판정",
];

export function AnalyzingIndicator() {
  return (
    <div className="flex flex-col items-center gap-8 pt-20" role="status">
      <motion.div
        className="h-14 w-14 rounded-full border-4 border-muted border-t-primary"
        animate={{ rotate: 360 }}
        transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
        aria-hidden
      />
      <div className="space-y-1 text-center">
        <p className="font-medium">분석 중입니다…</p>
        <p className="text-sm text-muted-foreground">
          {PIPELINE_STEPS.join(" → ")}
        </p>
      </div>
    </div>
  );
}
