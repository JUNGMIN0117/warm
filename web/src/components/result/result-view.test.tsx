import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { AnalysisResponse } from "@/lib/api/types";

import { ResultView } from "./result-view";

/**
 * 결과 화면의 계약 테스트 — 백엔드 없이 API 명세의 예시 응답으로 렌더링을 검증한다.
 *
 * 지키는 것:
 * 1. 확률 분포는 4계절 전부 표시된다 (top-1만 보여주기 금지)
 * 2. saved 여부가 사실대로 안내된다
 * 3. stages가 null이면(이력 상세) 해당 섹션이 조용히 빠진다
 * 4. 측정 수치(h° 등 판정 근거)가 화면에 실린다
 */

/** docs/05-api-spec.md §10의 예시 응답을 그대로 옮긴 픽스처. */
function fixture(overrides: Partial<AnalysisResponse> = {}): AnalysisResponse {
  return {
    id: "test-id",
    analyzedAt: "2026-08-10T09:00:00Z",
    saved: false,
    season: {
      code: "autumn_warm",
      labelKo: "가을 웜",
      labelEn: "Autumn Warm",
      emoji: "🍂",
      keywords: ["깊은", "따뜻한", "차분한"],
      description: "차분하고 깊이 있는 톤이 어울립니다.",
      bestColors: [{ name: "머스타드", hex: "#D4A017" }],
      worstColors: [{ name: "네온핑크", hex: "#FF6EC7" }],
      stylingTips: ["골드 액세서리가 잘 어울립니다."],
    },
    confidence: 0.822,
    probabilities: {
      spring_warm: 0.132,
      summer_cool: 0.004,
      autumn_warm: 0.822,
      winter_cool: 0.042,
    },
    undertone: "warm",
    undertoneConfidence: 0.954,
    topTwoMargin: 0.69,
    axes: [
      {
        name: "undertone",
        rawValue: 68.42,
        normalized: 0.783,
        lowLabel: "쿨(푸른기)",
        highLabel: "웜(노란기)",
        interpretation: "웜 성향이 뚜렷합니다",
      },
    ],
    features: {
      lightness: 61.0,
      aStar: 18.05,
      bStar: 45.64,
      chroma: 49.08,
      hueAngle: 68.42,
      ita: 13.55,
      itaCategory: "tan",
      lightnessSpread: 0.85,
      pixelCount: 12453,
      medianRgbHex: "#C68642",
    },
    preprocessing: {
      whiteBalanceMethod: "gray_world",
      gains: [1.0, 1.0, 1.0],
      castStrength: 0.0002,
      maskCoverageRatio: 0.764,
    },
    qualityFactor: 1.0,
    warnings: [],
    stages: null,
    ...overrides,
  };
}

describe("ResultView", () => {
  it("4계절 확률이 전부 표시된다 — 분포 전체 공개가 계약이다", () => {
    render(<ResultView result={fixture()} onRestart={() => {}} />);
    for (const label of ["봄 웜", "여름 쿨", "가을 웜", "겨울 쿨"]) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    }
    expect(screen.getByText("82%")).toBeInTheDocument();
  });

  it("익명 분석(saved=false)은 '저장되지 않았습니다'를 안내한다", () => {
    render(<ResultView result={fixture({ saved: false })} onRestart={() => {}} />);
    expect(screen.getAllByText(/저장되지 않았습니다/).length).toBeGreaterThan(0);
  });

  it("로그인 분석(saved=true)은 이력 저장을 안내하되 원본 미보관을 함께 알린다", () => {
    render(<ResultView result={fixture({ saved: true })} onRestart={() => {}} />);
    expect(screen.getByText(/이력에 저장되었습니다/)).toBeInTheDocument();
    expect(screen.getByText(/원본 사진이 아니라 측정 수치만/)).toBeInTheDocument();
  });

  it("stages가 null이면 전처리 섹션이 렌더링되지 않는다 (이력 상세 케이스)", () => {
    render(<ResultView result={fixture({ stages: null })} onRestart={() => {}} />);
    expect(screen.queryByText(/다섯 단계/)).not.toBeInTheDocument();
  });

  it("stages가 있으면 다섯 단계가 모두 보인다", () => {
    const dataUri = "data:image/webp;base64,AAAA";
    render(
      <ResultView
        result={fixture({
          stages: {
            original: dataUri,
            whiteBalanced: dataUri,
            faceCrop: dataUri,
            skinMask: dataUri,
            measuredPixels: dataUri,
          },
        })}
        onRestart={() => {}}
      />,
    );
    expect(screen.getAllByRole("img")).toHaveLength(5);
  });

  it("판정 근거 수치(색상각 h°)가 화면에 실린다 — 블랙박스 금지", () => {
    render(<ResultView result={fixture()} onRestart={() => {}} />);
    expect(screen.getAllByText("색상각 h°").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/68\.42/).length).toBeGreaterThan(0);
  });

  it("경고가 있으면 숨기지 않고 보여준다", () => {
    render(
      <ResultView
        result={fixture({ warnings: ["조명이 한쪽으로 치우쳐 있습니다."] })}
        onRestart={() => {}}
      />,
    );
    expect(screen.getByText(/조명이 한쪽으로/)).toBeInTheDocument();
  });
});
