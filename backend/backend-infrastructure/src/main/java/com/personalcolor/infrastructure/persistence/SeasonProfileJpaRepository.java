package com.personalcolor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 계절 카탈로그 Spring Data 리포지토리.
 *
 * <p>어댑터 안쪽에 중첩 인터페이스로 숨겨두고 싶었지만 그렇게 하면
 * Spring Data의 리포지토리 스캔이 찾지 못한다. 스캔은 최상위 인터페이스를
 * 대상으로 하므로 파일을 따로 둔다 — package-private으로 선언해
 * 패키지 밖에서는 여전히 보이지 않게 했다.
 */
interface SeasonProfileJpaRepository extends JpaRepository<SeasonProfileEntity, String> {}
