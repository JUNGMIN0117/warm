package com.personalcolor.infrastructure.persistence;

import com.personalcolor.domain.analysis.AnalysisRecord;
import com.personalcolor.domain.analysis.port.AnalysisRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 도메인 포트의 JPA 구현.
 *
 * <p>Spring Data 인터페이스와 도메인 포트 사이의 번역만 한다. 얇지만
 * 이 얇음이 요점이다 — 도메인은 {@code Limit}이나 {@code JpaRepository} 같은
 * 어휘를 모른 채로 남는다.
 */
@Repository
public class JpaAnalysisRepository implements AnalysisRepository {

    private final AnalysisJpaRepository jpa;
    private final AnalysisEntityMapper mapper;

    public JpaAnalysisRepository(AnalysisJpaRepository jpa, AnalysisEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AnalysisRecord save(AnalysisRecord record) {
        return mapper.toDomain(jpa.save(mapper.toEntity(record)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisRecord> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisRecord> findByUserId(UUID userId, int limit) {
        return jpa.findByUserIdOrderByAnalyzedAtDesc(userId, Limit.of(limit)).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
