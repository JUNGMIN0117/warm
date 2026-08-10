package com.personalcolor.domain.analysis;

import com.personalcolor.domain.season.SeasonProfile;

import java.util.Optional;

/**
 * 사용자에게 보여줄 한 벌 — 측정 결과 + 큐레이션 + (선택) 단계 이미지.
 *
 * <p>ADR-005가 갈라놓은 두 출처가 여기서 다시 합쳐진다. {@code record}는
 * ml-service가 측정한 것이고 {@code profile}은 우리가 정한 것이다.
 * 둘을 구분해 담아두면 "이 색이 어디서 왔는가"가 타입에 드러난다.
 */
public record AnalysisView(
        AnalysisRecord record, SeasonProfile profile, Optional<StageImages> stages) {

    public AnalysisView {
        if (record == null || profile == null) {
            throw new IllegalArgumentException("record와 profile은 필수입니다.");
        }
        if (record.measurement().season() != profile.season()) {
            throw new IllegalArgumentException(
                    "측정된 계절(" + record.measurement().season().code()
                            + ")과 프로필(" + profile.season().code() + ")이 다릅니다.");
        }
        stages = stages == null ? Optional.empty() : stages;
    }
}
