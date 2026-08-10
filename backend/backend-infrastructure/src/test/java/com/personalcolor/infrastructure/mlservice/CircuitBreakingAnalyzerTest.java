package com.personalcolor.infrastructure.mlservice;

import com.personalcolor.domain.analysis.AnalysisOutcome;
import com.personalcolor.domain.analysis.AnalyzerUnavailableException;
import com.personalcolor.domain.analysis.ImageRejectedException;
import com.personalcolor.domain.analysis.port.PersonalColorAnalyzer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 서킷 브레이커 데코레이터 테스트.
 *
 * <p>스프링 컨텍스트가 없다. 애너테이션 대신 데코레이터를 택한 이점이
 * 여기서 드러난다 — 회로가 열리는 조건을 순수 자바로 검증한다.
 */
@DisplayName("CircuitBreakingAnalyzer")
class CircuitBreakingAnalyzerTest {

    private static final int WINDOW = 4;

    private static CircuitBreaker breaker() {
        return CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .failureRateThreshold(50f)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(WINDOW)
                .minimumNumberOfCalls(WINDOW)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .ignoreExceptions(ImageRejectedException.class)
                .build());
    }

    private static PersonalColorAnalyzer alwaysThrows(RuntimeException e) {
        return (image, includeStages) -> {
            throw e;
        };
    }

    @Test
    @DisplayName("측정기 장애가 반복되면 회로가 열린다")
    void opensAfterRepeatedFailures() {
        CircuitBreaker cb = breaker();
        PersonalColorAnalyzer analyzer = new CircuitBreakingAnalyzer(
                alwaysThrows(new AnalyzerUnavailableException("down")), cb);

        for (int i = 0; i < WINDOW; i++) {
            assertThatThrownBy(() -> analyzer.analyze(new byte[]{1}, false))
                    .isInstanceOf(AnalyzerUnavailableException.class);
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("회로가 열리면 위임 대상을 부르지 않는다")
    void shortCircuitsWhenOpen() {
        CircuitBreaker cb = breaker();
        int[] calls = {0};
        PersonalColorAnalyzer counting = (image, includeStages) -> {
            calls[0]++;
            throw new AnalyzerUnavailableException("down");
        };
        PersonalColorAnalyzer analyzer = new CircuitBreakingAnalyzer(counting, cb);

        for (int i = 0; i < WINDOW; i++) {
            assertThatThrownBy(() -> analyzer.analyze(new byte[]{1}, false))
                    .isInstanceOf(AnalyzerUnavailableException.class);
        }
        int callsBefore = calls[0];

        assertThatThrownBy(() -> analyzer.analyze(new byte[]{1}, false))
                .isInstanceOf(AnalyzerUnavailableException.class)
                .hasMessageContaining("일시적으로 불안정");

        assertThat(calls[0]).isEqualTo(callsBefore);
    }

    @Test
    @DisplayName("사진 문제는 아무리 반복돼도 회로를 열지 않는다")
    void imageRejectionsDoNotOpenCircuit() {
        // 이 테스트가 이 클래스의 존재 이유다. 얼굴 없는 사진을 실패로 세면
        // 사용자가 잘못된 사진 몇 장을 올린 것만으로 정상 요청까지 막힌다.
        CircuitBreaker cb = breaker();
        PersonalColorAnalyzer analyzer = new CircuitBreakingAnalyzer(
                alwaysThrows(new ImageRejectedException(
                        ImageRejectedException.Reason.NO_FACE_DETECTED, "얼굴 없음")),
                cb);

        for (int i = 0; i < WINDOW * 3; i++) {
            assertThatThrownBy(() -> analyzer.analyze(new byte[]{1}, false))
                    .isInstanceOf(ImageRejectedException.class);
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("정상 응답은 그대로 통과시킨다")
    void passesThroughSuccess() {
        AnalysisOutcome expected = AnalysisOutcome.of(Measurements.autumnWarm());
        PersonalColorAnalyzer analyzer = new CircuitBreakingAnalyzer(
                (image, includeStages) -> expected, breaker());

        assertThat(analyzer.analyze(new byte[]{1}, false)).isSameAs(expected);
    }
}
