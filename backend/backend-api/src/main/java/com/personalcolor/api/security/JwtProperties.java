package com.personalcolor.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT 설정.
 *
 * <p><b>{@code secret}에 기본값을 두지 않았다.</b> 이것이 이 클래스에서
 * 가장 중요한 결정이다. 기본값이 있으면 개발 중엔 편하지만 그대로 배포되고,
 * 소스가 공개된 프로젝트에서 그것은 누구나 토큰을 위조할 수 있다는 뜻이다.
 * 환경변수 {@code PCAI_JWT_SECRET}이 없으면 기동 자체가 실패한다 —
 * 시끄럽게 실패하는 편이 조용히 뚫려 있는 것보다 낫다.
 *
 * @param secret HMAC 서명 키. HS256이므로 최소 32바이트가 필요하다
 * @param accessTokenTtl 액세스 토큰 유효 기간
 * @param issuer 토큰 발급자 식별자
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, String issuer) {

    /** HS256이 요구하는 최소 키 길이(RFC 7518). 짧으면 jjwt가 거부한다. */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 서명 키가 설정되지 않았습니다. 환경변수 PCAI_JWT_SECRET을 지정하세요. "
                            + "기본값을 두지 않는 것은 의도적입니다 — 기본 키는 그대로 배포됩니다.");
        }
        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT 서명 키가 너무 짧습니다(" + length + "바이트). "
                            + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
        accessTokenTtl = accessTokenTtl == null ? Duration.ofHours(12) : accessTokenTtl;
        issuer = issuer == null || issuer.isBlank() ? "personal-color-ai" : issuer;
    }
}
