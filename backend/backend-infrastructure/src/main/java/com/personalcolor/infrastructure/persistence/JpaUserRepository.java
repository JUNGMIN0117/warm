package com.personalcolor.infrastructure.persistence;

import com.personalcolor.domain.user.User;
import com.personalcolor.domain.user.port.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** 사용자 포트의 JPA 구현. */
@Repository
public class JpaUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    public JpaUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public User save(User user) {
        return toDomain(jpa.save(new UserEntity(
                user.id(), user.email(), user.passwordHash(),
                user.displayName(), user.createdAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(JpaUserRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(JpaUserRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    private static User toDomain(UserEntity entity) {
        return new User(
                entity.getId(), entity.getEmail(), entity.getDisplayName(),
                entity.getPasswordHash(), entity.getCreatedAt());
    }
}
