package com.personalcolor.domain.analysis;

/**
 * 전처리가 사진을 얼마나 건드렸는지에 대한 보고.
 *
 * <p>보정하고 침묵하는 대신 보정량을 공개한다. {@code castStrength}가 크면
 * 조명 색이 강했다는 뜻이고, {@code coverageRatio}가 낮으면 마스킹이 잘
 * 되지 않았다는 뜻이다. 둘 다 결과를 얼마나 믿을지 판단할 재료다.
 *
 * @param whiteBalanceMethod gray_world | white_patch
 * @param gainRed 선형 공간에서 R에 곱한 게인. 1.0이면 무보정
 * @param gainGreen 선형 공간에서 G에 곱한 게인
 * @param gainBlue 선형 공간에서 B에 곱한 게인
 * @param castStrength 입력에 있던 색 캐스트 세기. max(gains)/min(gains) - 1
 * @param maskCoverageRatio 얼굴 타원 대비 최종 마스크 비율. 정상이면 0.4~0.8
 * @param otsuThreshold 얼굴 내부에서 계산된 Otsu 임계값
 */
public record PreprocessingReport(
        String whiteBalanceMethod,
        double gainRed,
        double gainGreen,
        double gainBlue,
        double castStrength,
        double maskCoverageRatio,
        double otsuThreshold) {

    public PreprocessingReport {
        if (whiteBalanceMethod == null || whiteBalanceMethod.isBlank()) {
            throw new IllegalArgumentException("화이트밸런스 방식이 비어 있습니다.");
        }
        if (maskCoverageRatio < 0.0 || maskCoverageRatio > 1.0) {
            throw new IllegalArgumentException("마스크 비율은 0~1이어야 합니다: " + maskCoverageRatio);
        }
    }
}
