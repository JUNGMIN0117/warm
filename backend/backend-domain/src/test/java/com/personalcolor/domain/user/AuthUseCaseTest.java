package com.personalcolor.domain.user;

import com.personalcolor.domain.user.port.PasswordHasher;
import com.personalcolor.domain.user.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 회원가입·로그인 유스케이스 테스트.
 *
 * <p>{@link PasswordHasher} 포트 덕분에 BCrypt를 돌리지 않는다. BCrypt는
 * 의도적으로 느린 알고리즘이라 실제로 쓰면 이 테스트들이 눈에 띄게 느려진다.
 */
@DisplayName("인증 유스케이스")
class AuthUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-10T05:00:00Z");

    private InMemoryUsers users;
    private RegisterUser register;
    private AuthenticateUser authenticate;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        FakeHasher hasher = new FakeHasher();
        register = new RegisterUser(users, hasher, Clock.fixed(NOW, ZoneOffset.UTC));
        authenticate = new AuthenticateUser(users, hasher);
    }

    @Nested
    @DisplayName("회원가입")
    class Registration {

        @Test
        @DisplayName("계정을 만들고 비밀번호를 해싱해 저장한다")
        void createsAccountWithHashedPassword() {
            User user = register.execute("Foo@Example.com", "  정민  ", "correct horse battery");

            assertThat(user.email()).isEqualTo("foo@example.com");
            assertThat(user.displayName()).isEqualTo("정민");
            assertThat(user.passwordHash()).isNotEqualTo("correct horse battery");
            assertThat(user.createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("같은 이메일로 두 번 가입할 수 없다")
        void rejectsDuplicateEmail() {
            register.execute("dup@example.com", "첫번째", "correct horse battery");

            // 대소문자만 다른 주소도 같은 사서함이므로 막혀야 한다.
            assertThatThrownBy(() ->
                    register.execute("DUP@Example.com", "두번째", "another passphrase"))
                    .isInstanceOf(EmailAlreadyUsedException.class);
        }

        @Test
        @DisplayName("짧은 비밀번호를 거부한다")
        void rejectsShortPassword() {
            assertThatThrownBy(() -> register.execute("a@b.com", "이름", "short"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(RegisterUser.MIN_PASSWORD_LENGTH));
        }

        @Test
        @DisplayName("bcrypt 한계(72바이트)를 넘는 비밀번호를 거부한다")
        void rejectsOverlongPassword() {
            // 조용히 잘라내면 뒷부분이 무시되는데 사용자는 그 사실을 모른다.
            assertThatThrownBy(() -> register.execute("a@b.com", "이름", "x".repeat(100)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("깁니다");
        }

        @Test
        @DisplayName("빈 표시 이름을 거부한다")
        void rejectsBlankDisplayName() {
            assertThatThrownBy(() ->
                    register.execute("a@b.com", "   ", "correct horse battery"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("로그인")
    class Authentication {

        @BeforeEach
        void createAccount() {
            register.execute("me@example.com", "정민", "correct horse battery");
        }

        @Test
        @DisplayName("올바른 자격이면 사용자를 돌려준다")
        void succeedsWithCorrectCredentials() {
            User user = authenticate.execute("ME@example.com", "correct horse battery");

            assertThat(user.email()).isEqualTo("me@example.com");
        }

        @Test
        @DisplayName("비밀번호가 틀리면 실패한다")
        void failsWithWrongPassword() {
            assertThatThrownBy(() -> authenticate.execute("me@example.com", "wrong password"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("없는 계정과 틀린 비밀번호의 메시지가 같다")
        void doesNotLeakAccountExistence() {
            // 메시지가 다르면 이메일 목록을 대조해 가입 여부를 수집할 수 있다.
            String missing = catchMessage(() -> authenticate.execute("nobody@example.com", "x"));
            String wrongPassword = catchMessage(() -> authenticate.execute("me@example.com", "x"));

            assertThat(missing).isEqualTo(wrongPassword);
        }

        @Test
        @DisplayName("없는 계정에도 해시 대조를 수행한다")
        void comparesHashEvenWhenAccountMissing() {
            // 즉시 반환하면 응답 시간 차이로 가입 여부가 새어 나간다.
            FakeHasher hasher = new FakeHasher();
            AuthenticateUser auth = new AuthenticateUser(new InMemoryUsers(), hasher);

            assertThatThrownBy(() -> auth.execute("ghost@example.com", "whatever"))
                    .isInstanceOf(InvalidCredentialsException.class);
            assertThat(hasher.matchCalls).isEqualTo(1);
        }

        @Test
        @DisplayName("형식이 틀린 이메일도 같은 실패로 처리한다")
        void malformedEmailFailsIdentically() {
            assertThatThrownBy(() -> authenticate.execute("not-an-email", "x"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Test
    @DisplayName("toString에 비밀번호 해시가 노출되지 않는다")
    void toStringHidesPasswordHash() {
        User user = register.execute("a@b.com", "이름", "correct horse battery");

        assertThat(user.toString())
                .contains("a@b.com")
                .doesNotContain(user.passwordHash());
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("예외가 발생하지 않았습니다.");
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    // --- 가짜 포트 ---------------------------------------------------------

    private static final class FakeHasher implements PasswordHasher {
        int matchCalls;

        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String hashedPassword) {
            matchCalls++;
            return hashedPassword.equals("hashed:" + rawPassword);
        }
    }

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
}
