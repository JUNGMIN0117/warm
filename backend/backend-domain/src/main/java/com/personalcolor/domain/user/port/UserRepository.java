package com.personalcolor.domain.user.port;

import com.personalcolor.domain.user.User;

import java.util.Optional;
import java.util.UUID;

/** 사용자 저장소 — 바깥으로 나가는 포트. */
public interface UserRepository {

    User save(User user);

    /** 이메일로 찾는다. 인자는 정규화된(소문자) 이메일이어야 한다. */
    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID id);

    boolean existsByEmail(String email);
}
