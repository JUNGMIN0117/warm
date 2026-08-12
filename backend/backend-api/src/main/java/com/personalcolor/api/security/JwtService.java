package com.personalcolor.api.security;

import com.personalcolor.domain.user.Role;
import com.personalcolor.domain.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * 액세스 토큰 발급과 검증.
 *
 * <p>리프레시 토큰은 두지 않았다. 리프레시를 제대로 하려면 저장소와 폐기
 * 목록이 필요한데, 이 서비스에서 로그인의 유일한 용도가 이력 조회라 그
 * 복잡도를 감당할 이유가 없다. 12시간짜리 액세스 토큰 하나로 충분하고,
 * 만료되면 다시 로그인한다.
 *
 * <p>같은 이유로 토큰 폐기(로그아웃 시 무효화)도 없다. 무상태 JWT의 값은
 * 서버가 아무것도 기억하지 않는 데 있고, 폐기 목록을 두는 순간 그 값이
 * 사라진다. 필요해지면 그때 세션으로 바꾸는 것이 정직하다.
 */
@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** 사용자에게 액세스 토큰을 발급한다. */
    public IssuedToken issue(User user) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        String token = Jwts.builder()
                .issuer(properties.issuer())
                // subject는 이메일이 아니라 id다. 이메일은 바뀔 수 있고,
                // 토큰에 담긴 값이 개인정보인 것도 피하고 싶다.
                .subject(user.id().toString())
                .claim("name", user.displayName())
                // 역할을 토큰에 싣는다 — 요청마다 DB를 보지 않는 무상태
                // 원칙의 연장이다. 대가: 승격/강등이 다음 로그인부터
                // 반영된다. 폐기 목록을 안 두는 것과 같은 트레이드오프다.
                .claim("role", user.role().code())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();

        return new IssuedToken(token, expiresAt);
    }

    /**
     * 토큰을 검증하고 사용자 id를 꺼낸다.
     *
     * <p>서명 불일치·만료·형식 오류를 구분하지 않고 모두 빈 값으로 돌려준다.
     * 호출부(필터)는 어느 쪽이든 "인증 안 됨"으로 처리하므로 구분이 필요 없고,
     * 구분해서 응답에 담으면 공격자에게 힌트가 된다.
     */
    public Optional<UUID> extractUserId(String token) {
        return extractPrincipal(token).map(TokenPrincipal::userId);
    }

    /**
     * 토큰에서 사용자 id와 역할을 꺼낸다.
     *
     * <p>role 클레임이 없는 토큰(역할 도입 전 발급분)은 USER로 취급한다 —
     * 오래된 토큰이 관리자 권한을 얻는 방향의 실수가 구조적으로 불가능하다.
     */
    public Optional<TokenPrincipal> extractPrincipal(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .clock(() -> Date.from(Instant.now(clock)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String roleClaim = claims.get("role", String.class);
            Role role = roleClaim == null ? Role.USER : Role.fromCode(roleClaim);
            return Optional.of(new TokenPrincipal(UUID.fromString(claims.getSubject()), role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 토큰이 증명하는 주체 — id와 역할. */
    public record TokenPrincipal(UUID userId, Role role) {}

    /** 발급된 토큰과 만료 시각. 클라이언트가 재로그인 시점을 알 수 있게 함께 준다. */
    public record IssuedToken(String value, Instant expiresAt) {}
}
