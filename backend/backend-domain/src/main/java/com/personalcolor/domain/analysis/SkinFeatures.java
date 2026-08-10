package com.personalcolor.domain.analysis;

/**
 * 측정된 피부 색채 통계 — 판정의 근거.
 *
 * <p>ml-service가 이미지에서 뽑아낸 값을 그대로 받는다. Java가 다시 계산하지
 * 않는 이유는 ADR-005에 있다 — 측정은 Python이 소유하고 Java는 그 결과를
 * 해석·저장한다.
 *
 * <p>이 값들이 응답에 실리는 것 자체가 제품 요구사항이다. 원본 프로젝트의
 * 결과는 CNN이 뱉은 숫자 하나였고 사용자가 검증할 방법이 없었다.
 * "h°가 68.4라서 웜"까지 보여주는 것이 블랙박스 탈출의 실체다.
 *
 * @param lightness L* — 명도 (0~100)
 * @param aStar a* — 녹(-) ↔ 적(+)
 * @param bStar b* — 청(-) ↔ 황(+). 언더톤의 1차 신호
 * @param chroma C* — 채도. 클리어/뮤트를 가른다
 * @param hueAngle h° — 색상각(도). 피부는 대개 30~80° 사이
 * @param ita ITA° — 개인 유형 각도. 피부 명도의 국제 표준 지표
 * @param itaCategory ITA° 표준 6단계 구간명 (very_light ~ dark)
 * @param lightnessSpread L*의 사분위 범위. 조명 균일도의 대리 지표
 * @param pixelCount 통계에 실제로 사용된 피부 픽셀 수
 * @param medianRgb 대표 피부색(중앙값). UI가 색상 칩으로 그대로 표시한다
 */
public record SkinFeatures(
        double lightness,
        double aStar,
        double bStar,
        double chroma,
        double hueAngle,
        double ita,
        String itaCategory,
        double lightnessSpread,
        int pixelCount,
        RgbColor medianRgb) {

    public SkinFeatures {
        if (itaCategory == null || itaCategory.isBlank()) {
            throw new IllegalArgumentException("itaCategory가 비어 있습니다.");
        }
        if (medianRgb == null) {
            throw new IllegalArgumentException("medianRgb가 없습니다.");
        }
        if (pixelCount <= 0) {
            throw new IllegalArgumentException(
                    "픽셀 수는 양수여야 합니다: " + pixelCount
                            + " — ml-service가 하드 플로어를 통과시켰다면 계약 위반이다.");
        }
        if (hueAngle < 0.0 || hueAngle >= 360.0) {
            throw new IllegalArgumentException("색상각은 0 이상 360 미만이어야 합니다: " + hueAngle);
        }
    }
}
