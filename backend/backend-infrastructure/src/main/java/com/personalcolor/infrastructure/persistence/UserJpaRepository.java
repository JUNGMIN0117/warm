package com.personalcolor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 Spring Data 리포지토리.
 *
 * <p>이메일 조회를 {@code findByEmail}로 두는 것은 <b>정규화된 값이
 * 들어온다는 전제</b>에 기댄다. 도메인의 {@code Email.normalize}가 항상
 * 소문자를 보장하고, DB에도 {@code LOWER(email)} 유니크 인덱스가 있어
 * 대문자로 저장되는 경로 자체가 없다.
 */
interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
