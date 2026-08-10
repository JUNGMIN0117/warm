package com.personalcolor.domain.user;

import com.personalcolor.domain.user.port.PasswordHasher;
import com.personalcolor.domain.user.port.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** 회원가입 유스케이스. */
public final class RegisterUser {

    /**
     * 비밀번호 최소 길이.
     *
     * <p>대문자·특수문자 같은 구성 규칙은 두지 않았다. NIST SP 800-63B가
     * 권고를 철회한 지 오래고, 실제로는 {@code Password1!} 같은 예측 가능한
     * 패턴을 유도해 오히려 약해진다. 길이만 요구하고 나머지는 사용자에게 맡긴다.
     */
    public static final int MIN_PASSWORD_LENGTH = 10;

    /** bcrypt는 72바이트를 넘는 입력을 조용히 잘라낸다. 그 전에 거절한다. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private static final int MAX_DISPLAY_NAME_LENGTH = 50;

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    public RegisterUser(UserRepository users, PasswordHasher hasher, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
    }

    /**
     * 계정을 만든다.
     *
     * @throws IllegalArgumentException 입력이 규칙에 맞지 않는 경우
     * @throws EmailAlreadyUsedException 이미 가입된 이메일인 경우
     */
    public User execute(String email, String displayName, String rawPassword) {
        String normalizedEmail = Email.normalize(email);
        validatePassword(rawPassword);
        String name = validateDisplayName(displayName);

        // 경쟁 조건이 남는다 — 두 요청이 동시에 통과할 수 있다. DB의
        // LOWER(email) 유니크 인덱스가 최종 방어선이고, 이 검사는 흔한
        // 경우에 친절한 메시지를 주기 위한 것이다.
        if (users.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }

        return users.save(new User(
                UUID.randomUUID(),
                normalizedEmail,
                name,
                hasher.hash(rawPassword),
                Instant.now(clock)));
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        if (rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("비밀번호가 너무 깁니다.");
        }
    }

    private static String validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("표시 이름이 비어 있습니다.");
        }
        String trimmed = displayName.strip();
        if (trimmed.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "표시 이름은 " + MAX_DISPLAY_NAME_LENGTH + "자 이하여야 합니다.");
        }
        return trimmed;
    }
}
