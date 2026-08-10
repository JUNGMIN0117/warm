package com.personalcolor.infrastructure.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data 리포지토리.
 *
 * <p>도메인의 {@code AnalysisRepository} 포트와 별개다. 이 인터페이스는
 * Spring Data의 어휘(JpaRepository, Limit)를 쓰고, 어댑터가 둘 사이를
 * 번역한다. 도메인이 이걸 직접 상속하면 Spring Data에 묶인다.
 */
public interface AnalysisJpaRepository extends JpaRepository<AnalysisEntity, UUID> {

    List<AnalysisEntity> findByUserIdOrderByAnalyzedAtDesc(UUID userId, Limit limit);
}
