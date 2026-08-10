package com.personalcolor.infrastructure.security;

import com.personalcolor.domain.user.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * {@link PasswordHasher} 포트의 Spring Security 구현.
 *
 * <p>어댑터가 두 줄짜리 위임인데도 존재하는 이유는 방향 때문이다.
 * 이것이 없으면 도메인이 {@code org.springframework.security}를 임포트하게
 * 되고, ArchUnit 규칙(도메인은 Spring을 모른다)이 깨진다.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
