/**
 * 판정 결과 해석 — 서버가 준 숫자를 사용자 문장으로 바꾸는 규칙.
 *
 * 이 파일이 지키는 프로젝트 원칙: **정확도를 주장하지 않는다.**
 * "당신은 가을 웜입니다(정확도 95%)"가 아니라 "가을 웜 성향이 우세합니다"
 * 처럼, 확률 분포가 실제로 말해주는 만큼만 말한다.
 */

import type { AnalysisResponse, Probabilities, SeasonCode } from "./api/types";

/**
 * "두 계절 사이"로 안내할 1-2위 확률 차 임계값.
 *
 * 잠정값이다 — 0.15는 "1위 55% / 2위 44%는 사실상 동점"(05-api-spec §10)이라는
 * 서버 문서의 예시가 margin 0.11이라는 점에서 역산해, 그보다 여유 있게 잡았다.
 * 수동 검증셋(Phase 3)에서 경계 케이스 분포를 보고 조정한다.
 */
export const BORDERLINE_MARGIN = 0.15;

export type ConfidenceBand = "strong" | "moderate" | "borderline";

export interface Verdict {
  band: ConfidenceBand;
  /** 1위 계절 코드 */
  top: SeasonCode;
  /** band가 borderline일 때만 채워지는 2위 계절 코드 */
  runnerUp: SeasonCode | null;
  /** 결과 카드 머리에 쓸 한 문장 */
  headline: string;
}

/** 확률 분포에서 내림차순 상위 두 계절을 뽑는다. */
export function topTwo(probabilities: Probabilities): [SeasonCode, SeasonCode] {
  const sorted = (Object.entries(probabilities) as [SeasonCode, number][]).sort(
    (a, b) => b[1] - a[1],
  );
  return [sorted[0][0], sorted[1][0]];
}

/**
 * 신뢰도 구간을 나눈다.
 *
 * - borderline: 1-2위 차가 BORDERLINE_MARGIN 미만 — 단정하지 않고 둘 다 보여준다
 * - strong: 1위가 과반이면서 경계가 아님 — 우세하다고 말할 수 있음
 * - moderate: 그 사이 — "성향이 보인다" 수준으로만
 */
export function interpret(
  result: Pick<AnalysisResponse, "probabilities" | "topTwoMargin" | "season">,
  labelOf: (code: SeasonCode) => string,
): Verdict {
  const [top, runnerUp] = topTwo(result.probabilities);

  if (result.topTwoMargin < BORDERLINE_MARGIN) {
    return {
      band: "borderline",
      top,
      runnerUp,
      headline: `${labelOf(top)}과(와) ${labelOf(runnerUp)} 사이입니다`,
    };
  }
  if (result.probabilities[top] > 0.5) {
    return {
      band: "strong",
      top,
      runnerUp: null,
      headline: `${labelOf(top)} 성향이 우세합니다`,
    };
  }
  return {
    band: "moderate",
    top,
    runnerUp: null,
    headline: `${labelOf(top)} 성향이 보입니다`,
  };
}

/** 언더톤은 4계절 판정보다 항상 견고하다(2분류가 4분류의 병합이므로) — 별도 문장으로. */
export function undertoneSentence(
  undertone: "warm" | "cool",
  confidence: number,
): string {
  const label = undertone === "warm" ? "웜톤" : "쿨톤";
  if (confidence >= 0.9) return `${label}인 것은 뚜렷합니다`;
  if (confidence >= 0.7) return `${label} 성향입니다`;
  return `${label}으로 기울지만 확실하지 않습니다`;
}
