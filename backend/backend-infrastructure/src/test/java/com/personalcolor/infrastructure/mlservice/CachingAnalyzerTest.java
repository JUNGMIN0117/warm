package com.personalcolor.infrastructure.mlservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 캐시 키 규칙 검증.
 *
 * <p>Redis 연동 자체는 Testcontainers 통합 테스트가 맡고, 여기서는 키
 * 생성 규칙만 본다 — 버그가 나기 쉬운 곳이 연동이 아니라 키이기 때문이다.
 */
@DisplayName("CachingAnalyzer 캐시 키")
class CachingAnalyzerTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("같은 이미지·같은 옵션이면 같은 키다")
    void sameInputSameKey() {
        assertThat(CachingAnalyzer.cacheKey(bytes("photo"), false))
                .isEqualTo(CachingAnalyzer.cacheKey(bytes("photo"), false));
    }

    @Test
    @DisplayName("다른 이미지는 다른 키다")
    void differentImageDifferentKey() {
        assertThat(CachingAnalyzer.cacheKey(bytes("photo-a"), false))
                .isNotEqualTo(CachingAnalyzer.cacheKey(bytes("photo-b"), false));
    }

    @Test
    @DisplayName("include_stages가 키에 반영된다")
    void stageFlagIsPartOfKey() {
        // docs/05-api-spec §9가 지적한 함정. 이미지 해시만으로 키를 만들면
        // 단계 이미지 없이 캐시된 응답이 시각화 요청에 반환되어,
        // 프론트가 조용히 빈 화면을 띄운다.
        assertThat(CachingAnalyzer.cacheKey(bytes("photo"), true))
                .isNotEqualTo(CachingAnalyzer.cacheKey(bytes("photo"), false));
    }

    @Test
    @DisplayName("키에 네임스페이스 접두사가 붙는다")
    void keyIsNamespaced() {
        // Redis를 다른 용도와 공유할 때 키 충돌을 막는다.
        assertThat(CachingAnalyzer.cacheKey(bytes("photo"), false))
                .startsWith("pcai:analysis:");
    }
}
