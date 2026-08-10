/**
 * 계절 코드 → 시각 테마 매핑.
 *
 * 라벨·팔레트·스타일링 팁은 서버(DB)가 소유하지만(ADR-005), 화면의
 * 그라디언트·강조색 같은 **표현 그 자체**는 프론트의 것이다.
 * 여기 있는 색은 데이터가 아니라 디자인이다 — 서버 팔레트와 무관하게
 * 각 계절의 인상을 전달하는 UI 장식용 값이다.
 */

import type { SeasonCode } from "./api/types";

export interface SeasonTheme {
  /** 결과 카드 배경 그라디언트 (Tailwind 임의 값으로 사용) */
  gradient: string;
  /** 게이지·강조 요소 색 */
  accent: string;
  /** 어두운 텍스트가 필요한 밝은 테마인가 */
  lightSurface: boolean;
}

export const SEASON_THEMES: Record<SeasonCode, SeasonTheme> = {
  spring_warm: {
    gradient: "linear-gradient(135deg, #FFF3E0 0%, #FFE0B2 55%, #FFCC80 100%)",
    accent: "#E8833A",
    lightSurface: true,
  },
  summer_cool: {
    gradient: "linear-gradient(135deg, #E8EAF6 0%, #C5CAE9 55%, #B3C7E6 100%)",
    accent: "#5C6BC0",
    lightSurface: true,
  },
  autumn_warm: {
    gradient: "linear-gradient(135deg, #EFEBE9 0%, #D7CCC8 45%, #BCAAA4 100%)",
    accent: "#8D5524",
    lightSurface: true,
  },
  winter_cool: {
    gradient: "linear-gradient(135deg, #ECEFF1 0%, #CFD8DC 45%, #B0BEC5 100%)",
    accent: "#37474F",
    lightSurface: true,
  },
};

/** 확률 막대의 고정 표시 순서 — 응답 객체의 키 순서에 의존하지 않는다. */
export const SEASON_ORDER: SeasonCode[] = [
  "spring_warm",
  "summer_cool",
  "autumn_warm",
  "winter_cool",
];

/** 서버 큐레이션을 못 받는 화면(이력 목록 등)에서 쓰는 최소 라벨. */
export const SEASON_FALLBACK_LABELS: Record<SeasonCode, string> = {
  spring_warm: "봄 웜",
  summer_cool: "여름 쿨",
  autumn_warm: "가을 웜",
  winter_cool: "겨울 쿨",
};
