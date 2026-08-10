package com.personalcolor.domain.analysis.port;

import com.personalcolor.domain.analysis.AnalysisRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 분석 이력 저장소 — 바깥으로 나가는 포트.
 *
 * <p>Spring Data의 {@code Repository}를 도메인이 직접 쓰지 않는 이유가 있다.
 * 그 인터페이스를 상속하는 순간 도메인이 Spring Data에 묶이고, 페이징
 * 타입·{@code @Query}·엔티티 매핑 같은 인프라 어휘가 도메인 시그니처에
 * 스며든다. 여기서는 우리가 필요한 연산만 우리 타입으로 선언한다.
 */
public interface AnalysisRepository {

    /** 저장하고 저장된 형태를 돌려준다. */
    AnalysisRecord save(AnalysisRecord record);

    Optional<AnalysisRecord> findById(UUID id);

    /**
     * 사용자의 이력을 최신순으로 가져온다.
     *
     * <p>익명 분석은 어떤 사용자에게도 귀속되지 않으므로 여기 나오지 않는다.
     *
     * @param limit 최대 건수. 무제한 조회를 막기 위해 필수로 받는다
     */
    List<AnalysisRecord> findByUserId(UUID userId, int limit);
}
