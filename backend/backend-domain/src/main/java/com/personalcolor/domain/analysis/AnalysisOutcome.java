package com.personalcolor.domain.analysis;

import java.util.Optional;

/**
 * 측정기 호출 한 번의 산출물.
 *
 * <p>{@link Measurement}는 저장되고 {@link StageImages}는 저장되지 않는다.
 * 수명이 다른 두 값을 한 레코드로 묶되 타입으로 구분해 두면, 영속화
 * 코드가 실수로 단계 이미지까지 들고 가는 일이 생기기 어렵다.
 */
public record AnalysisOutcome(Measurement measurement, Optional<StageImages> stages) {

    public AnalysisOutcome {
        if (measurement == null) {
            throw new IllegalArgumentException("measurement가 없습니다.");
        }
        stages = stages == null ? Optional.empty() : stages;
    }

    public static AnalysisOutcome of(Measurement measurement) {
        return new AnalysisOutcome(measurement, Optional.empty());
    }

    public static AnalysisOutcome of(Measurement measurement, StageImages stages) {
        return new AnalysisOutcome(measurement, Optional.of(stages));
    }
}
