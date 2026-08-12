package com.personalcolor.api.security;

import com.personalcolor.domain.user.RegisterUser;
import com.personalcolor.domain.user.Role;
import com.personalcolor.domain.user.User;
import com.personalcolor.domain.user.port.PasswordHasher;
import com.personalcolor.domain.user.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 부트스트랩 테스트.
 *
 * <p>가장 중요한 계약은 마지막 테스트다 — <b>기존 계정의 비밀번호를
 * 덮어쓰지 않는다.</b> 환경변수에 남은 옛 비밀번호가 매 기동마다 현재
 * 비밀번호를 되돌리면, 비밀번호 변경이 조용히 무력화된다.
 */
@DisplayName("AdminAccountBootstrap")
class AdminAccountBootstrapTest {

    private static final Instant NOW = Instant.parse("2026-08-12T06:00:00Z");

    private static final class InMemoryUsers implements UserRepository {
        private final Map<String, User> byEmail = new HashMap<>();

        @Override
        public User save(User user) {
            byEmail.put(user.email(), user);
            return user;
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public Optional<User> findById(UUID id) {
            return byEmail.values().stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public boolean existsByEmail(String email) {
            return byEmail.containsKey(email);
        }
    }

    /** 테스트에서 BCrypt를 돌릴 이유가 없다 — 포트를 둔 이유 그대로. */
    private static final PasswordHasher FAKE_HASHER = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String hashedPassword) {
            return hashedPassword.equals("hashed:" + rawPassword);
        }
    };

    private final InMemoryUsers users = new InMemoryUsers();
    private final RegisterUser registerUser =
            new RegisterUser(users, FAKE_HASHER, Clock.fixed(NOW, ZoneOffset.UTC));

    private AdminAccountBootstrap bootstrap(String email, String password) {
        return new AdminAccountBootstrap(users, registerUser, email, password);
    }

    @Test
    @DisplayName("환경변수가 없으면 아무것도 하지 않는다 — 기동은 계속된다")
    void doesNothingWhenUnconfigured() {
        bootstrap("", "").run(new DefaultApplicationArguments());

        assertThat(users.byEmail).isEmpty();
    }

    @Test
    @DisplayName("계정이 없으면 만들고 ADMIN으로 승격한다")
    void createsAdminWhenAbsent() {
        bootstrap("admin@sagye.local", "long-enough-password").run(new DefaultApplicationArguments());

        User admin = users.findByEmail("admin@sagye.local").orElseThrow();
        assertThat(admin.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("가입 규칙(비밀번호 10자+)은 관리자에게도 적용된다")
    void enforcesPasswordRulesForAdmin() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        bootstrap("admin@sagye.local", "short").run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기존 계정은 승격만 하고 비밀번호는 건드리지 않는다")
    void promotesExistingAccountWithoutTouchingPassword() {
        User existing = registerUser.execute("me@example.com", "정민", "original-password!");
        String originalHash = existing.passwordHash();

        bootstrap("me@example.com", "different-env-password").run(new DefaultApplicationArguments());

        User promoted = users.findByEmail("me@example.com").orElseThrow();
        assertThat(promoted.role()).isEqualTo(Role.ADMIN);
        assertThat(promoted.passwordHash())
                .as("환경변수의 비밀번호가 기존 비밀번호를 덮어쓰면 안 된다")
                .isEqualTo(originalHash);
    }

    @Test
    @DisplayName("이미 관리자면 아무것도 바꾸지 않는다 — 멱등")
    void isIdempotentForExistingAdmin() {
        bootstrap("admin@sagye.local", "long-enough-password").run(new DefaultApplicationArguments());
        User first = users.findByEmail("admin@sagye.local").orElseThrow();

        bootstrap("admin@sagye.local", "long-enough-password").run(new DefaultApplicationArguments());

        assertThat(users.findByEmail("admin@sagye.local").orElseThrow()).isEqualTo(first);
    }
}
