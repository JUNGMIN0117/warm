package com.personalcolor.domain.analysis;

/**
 * 한 축의 판정 근거. 프론트가 게이지로 렌더링한다.
 *
 * <p>세 축(undertone / depth / clarity)이 4계절 판정을 구성한다. 축을 따로
 * 내보내는 이유는 "봄 웜 82%"라는 결론보다 "웜기가 뚜렷하고 명도가 낮다"는
 * 설명이 사용자에게 실제로 유용하기 때문이다.
 *
 * @param name undertone | depth | clarity
 * @param rawValue 원본 측정값 (h°, ITA°, C*)
 * @param normalized 0.0~1.0으로 정규화된 좌표
 * @param lowLabel 낮은 쪽 이름 (예: 쿨(푸른기))
 * @param highLabel 높은 쪽 이름 (예: 웜(노란기))
 * @param interpretation 사람이 읽을 해석 문장
 */
public record AxisReading(
        String name,
        double rawValue,
        double normalized,
        String lowLabel,
        String highLabel,
        String interpretation) {

    public AxisReading {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("축 이름이 비어 있습니다.");
        }
        if (normalized < 0.0 || normalized > 1.0) {
            throw new IllegalArgumentException(
                    "정규화 좌표는 0~1이어야 합니다: " + name + "=" + normalized);
        }
    }
}
