package com.personalcolor.api.security;

import com.personalcolor.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T06:00:00Z");
    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256!!";
    private static final User USER = new User(
            UUID.randomUUID(), "me@example.com", "정민", "hash", NOW);

    private static JwtService serviceAt(Instant now, Duration ttl) {
        return new JwtService(
                new JwtProperties(SECRET, ttl, "personal-color-ai"),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("발급한 토큰에서 사용자 id를 되찾는다")
    void issuesAndParses() {
        JwtService service = serviceAt(NOW, Duration.ofHours(12));

        String token = service.issue(USER).value();

        assertThat(service.extractUserId(token)).contains(USER.id());
    }

    @Test
    @DisplayName("만료 시각을 함께 알려준다")
    void reportsExpiry() {
        // 클라이언트가 JWT를 디코드하지 않고도 재로그인 시점을 알 수 있어야 한다.
        JwtService service = serviceAt(NOW, Duration.ofHours(3));

        assertThat(service.issue(USER).expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(3)));
    }

    @Test
    @DisplayName("만료된 토큰을 거부한다")
    void rejectsExpiredToken() {
        String token = serviceAt(NOW, Duration.ofMinutes(30)).issue(USER).value();

        JwtService later = serviceAt(NOW.plus(Duration.ofHours(1)), Duration.ofMinutes(30));

        assertThat(later.extractUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰을 거부한다")
    void rejectsForeignSignature() {
        String token = serviceAt(NOW, Duration.ofHours(1)).issue(USER).value();

        JwtService other = new JwtService(
                new JwtProperties("a-completely-different-secret-key-32bytes!!",
                        Duration.ofHours(1), "personal-color-ai"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(other.extractUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("발급자가 다른 토큰을 거부한다")
    void rejectsForeignIssuer() {
        String token = new JwtService(
                new JwtProperties(SECRET, Duration.ofHours(1), "someone-else"),
                Clock.fixed(NOW, ZoneOffset.UTC)).issue(USER).value();

        assertThat(serviceAt(NOW, Duration.ofHours(1)).extractUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("변조된 토큰을 거부한다")
    void rejectsTamperedToken() {
        String token = serviceAt(NOW, Duration.ofHours(1)).issue(USER).value();
        String tampered = token.substring(0, token.length() - 3) + "aaa";

        assertThat(serviceAt(NOW, Duration.ofHours(1)).extractUserId(tampered)).isEmpty();
    }

    @Test
    @DisplayName("쓰레기 문자열에도 예외를 던지지 않는다")
    void survivesGarbage() {
        JwtService service = serviceAt(NOW, Duration.ofHours(1));

        assertThat(service.extractUserId("not.a.jwt")).isEmpty();
        assertThat(service.extractUserId("")).isEmpty();
    }

    @Test
    @DisplayName("토큰에 이메일을 담지 않는다")
    void doesNotEmbedEmail() {
        // subject는 id다. 이메일은 바뀔 수 있고 개인정보이기도 하다.
        String token = serviceAt(NOW, Duration.ofHours(1)).issue(USER).value();

        assertThat(token).doesNotContain("me@example.com");
    }

    @Test
    @DisplayName("서명 키가 없으면 기동을 막는다")
    void refusesMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(1), "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PCAI_JWT_SECRET");
    }

    @Test
    @DisplayName("서명 키가 짧으면 기동을 막는다")
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofHours(1), "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("짧습니다");
    }
}
