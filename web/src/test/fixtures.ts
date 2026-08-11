import type { AnalysisResponse } from "@/lib/api/types";

/** docs/05-api-spec.md의 합성 얼굴(#C68642) 예시 응답을 축약한 픽스처. */
export function analysisFixture(overrides: Partial<AnalysisResponse> = {}): AnalysisResponse {
  return {
    id: "test-id",
    analyzedAt: "2026-08-10T09:00:00Z",
    saved: false,
    season: {
      code: "autumn_warm",
      labelKo: "가을 웜",
      labelEn: "Autumn Warm",
      emoji: "🍂",
      keywords: ["깊은", "따뜻한"],
      description: "설명",
      bestColors: [{ name: "머스타드", hex: "#D4A017" }],
      worstColors: [{ name: "퓨어 화이트", hex: "#FFFFFF" }],
      stylingTips: ["팁"],
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
