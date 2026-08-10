package com.personalcolor.infrastructure.mlservice;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * ml-service 연동 설정.
 *
 * <p>{@code @Value} 대신 타입 안전한 프로퍼티 바인딩을 쓴다. 오타가 나면
 * 기동 시점에 드러나고, 기본값이 한곳에 모이며, IDE가 자동완성해 준다.
 *
 * @param baseUrl ml-service 주소
 * @param timeout 단일 요청 상한. 추론은 수백 ms가 정상이지만 무한 대기는 막는다
 * @param cacheTtl 측정 결과 캐시 유지 시간
 * @param circuitFailureRateThreshold 서킷을 열 실패율(%)
 * @param circuitWaitDuration 서킷이 열린 뒤 반개방까지 기다리는 시간
 * @param circuitSlidingWindowSize 실패율 계산에 쓸 최근 호출 수
 */
@ConfigurationProperties(prefix = "ml-service")
public record MlServiceProperties(
        String baseUrl,
        Duration timeout,
        Duration cacheTtl,
        float circuitFailureRateThreshold,
        Duration circuitWaitDuration,
        int circuitSlidingWindowSize) {

    public MlServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("ml-service.base-url이 필요합니다.");
        }
        timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        cacheTtl = cacheTtl == null ? Duration.ofHours(24) : cacheTtl;
        circuitWaitDuration = circuitWaitDuration == null
                ? Duration.ofSeconds(30) : circuitWaitDuration;
        if (circuitFailureRateThreshold <= 0f) {
            circuitFailureRateThreshold = 50f;
        }
        if (circuitSlidingWindowSize <= 0) {
            circuitSlidingWindowSize = 10;
        }
    }
}
