package com.personalcolor.domain.analysis;

/**
 * 전처리 단계 이미지 (base64 data URI).
 *
 * <p>요청이 시각화를 원할 때만 채워진다. <b>저장되지 않는다</b> —
 * 얼굴이 담긴 이미지이므로 {@link AnalysisRecord}와 같은 이유로
 * 응답에 실려 나갈 뿐 DB에는 남기지 않는다.
 *
 * <p>그래서 이 타입은 {@code AnalysisRecord}의 필드가 아니라
 * 분석 결과와 나란히 전달되는 별도 값이다. 저장되는 것과 저장되지 않는
 * 것을 타입으로 갈라두면 실수로 영속화 경로에 흘러들어가기 어렵다.
 */
public record StageImages(
        String original,
        String whiteBalanced,
        String faceCrop,
        String skinMask,
        String measuredPixels) {

    public StageImages {
        requirePresent(original, "original");
        requirePresent(whiteBalanced, "whiteBalanced");
        requirePresent(faceCrop, "faceCrop");
        requirePresent(skinMask, "skinMask");
        requirePresent(measuredPixels, "measuredPixels");
    }

    private static void requirePresent(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 단계 이미지가 비어 있습니다.");
        }
    }
}
