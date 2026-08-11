import type { SeasonCode } from "@/lib/api/types";

/**
 * 계절별 UI 액센트.
 *
 * 여기 있는 색은 **판정 결과의 시각적 분위기**를 위한 프론트 소유 값이다.
 * 추천 팔레트(bestColors)는 DB가 소유하며 API로 내려온다 — 혼동 금지 (ADR-005).
 * 라벨·이모지도 API의 SeasonView를 쓰고, 여기는 API 응답이 오기 전(진행 화면,
 * 이력 색칩 테두리 등) 스타일링에만 쓴다.
 */

export interface SeasonTheme {
  /** 결과 카드 상단 그라데이션 (Tailwind 클래스). */
  gradient: string;
  /** 확률 막대 색. */
  bar: string;
  /** 강조 텍스트 색. */
  text: string;
  /** 배지 배경. */
  badge: string;
}

export const SEASON_THEMES: Record<SeasonCode, SeasonTheme> = {
  spring_warm: {
    gradient: "from-amber-100 via-orange-50 to-rose-50 dark:from-amber-950/40 dark:via-orange-950/30 dark:to-rose-950/20",
    bar: "bg-amber-500",
    text: "text-amber-700 dark:text-amber-400",
    badge: "bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-200",
  },
  summer_cool: {
    gradient: "from-sky-100 via-indigo-50 to-pink-50 dark:from-sky-950/40 dark:via-indigo-950/30 dark:to-pink-950/20",
    bar: "bg-sky-500",
    text: "text-sky-700 dark:text-sky-400",
    badge: "bg-sky-100 text-sky-800 dark:bg-sky-900/50 dark:text-sky-200",
  },
  autumn_warm: {
    gradient: "from-orange-100 via-amber-50 to-yellow-50 dark:from-orange-950/40 dark:via-amber-950/30 dark:to-yellow-950/20",
    bar: "bg-orange-600",
    text: "text-orange-700 dark:text-orange-400",
    badge: "bg-orange-100 text-orange-800 dark:bg-orange-900/50 dark:text-orange-200",
  },
  winter_cool: {
    gradient: "from-indigo-100 via-violet-50 to-slate-50 dark:from-indigo-950/40 dark:via-violet-950/30 dark:to-slate-950/20",
    bar: "bg-indigo-600",
    text: "text-indigo-700 dark:text-indigo-400",
    badge: "bg-indigo-100 text-indigo-800 dark:bg-indigo-900/50 dark:text-indigo-200",
  },
};

/** 이력 등에서 API 큐레이션 없이 코드만으로 짧은 라벨이 필요할 때의 폴백. */
export const SEASON_FALLBACK_LABELS: Record<SeasonCode, string> = {
  spring_warm: "봄 웜",
  summer_cool: "여름 쿨",
  autumn_warm: "가을 웜",
  winter_cool: "겨울 쿨",
};
