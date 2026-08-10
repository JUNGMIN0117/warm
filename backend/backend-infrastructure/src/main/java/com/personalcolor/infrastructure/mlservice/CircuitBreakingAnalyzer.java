package com.personalcolor.infrastructure.mlservice;

import com.personalcolor.domain.analysis.AnalysisOutcome;
import com.personalcolor.domain.analysis.AnalyzerUnavailableException;
import com.personalcolor.domain.analysis.port.PersonalColorAnalyzer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * 서킷 브레이커 데코레이터.
 *
 * <p>{@code @CircuitBreaker} 애너테이션 대신 데코레이터를 쓰는 이유는
 * 반은 제약이고 반은 선택이다. 제약: Resilience4j의 Spring Boot 자동설정이
 * 아직 Boot 4를 지원하지 않고, 그 스타터가 의존하던
 * {@code spring-boot-starter-aop}는 Boot 4에서 제거됐다 (ADR-006).
 * 선택: 데코레이터는 스프링 컨텍스트 없이 테스트되고, 애너테이션 뒤에
 * 숨은 동작이 코드로 드러난다.
 *
 * <p><b>무엇을 실패로 세는가</b>가 이 클래스의 핵심 결정이다.
 * {@link com.personalcolor.domain.analysis.ImageRejectedException}은
 * 세지 않는다 — 얼굴 없는 사진은 ml-service가 정상 동작한 결과이지
 * 장애가 아니다. 이걸 실패로 세면 사용자가 잘못된 사진 몇 장을 올린
 * 것만으로 회로가 열려 정상 요청까지 막힌다.
 */
public class CircuitBreakingAnalyzer implements PersonalColorAnalyzer {

    private final PersonalColorAnalyzer delegate;
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakingAnalyzer(PersonalColorAnalyzer delegate, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public AnalysisOutcome analyze(byte[] image, boolean includeStages) {
        try {
            return circuitBreaker.executeCallable(() -> delegate.analyze(image, includeStages));
        } catch (CallNotPermittedException e) {
            // 회로가 열려 있어 호출조차 하지 않았다. 폴백으로 그럴듯한
            // 결과를 지어내지 않는다 — 퍼스널 컬러 판정에 기본값이란 없다.
            throw new AnalyzerUnavailableException(
                    "분석 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해 주세요.", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // executeCallable의 검사 예외 시그니처 때문에 필요한 분기.
            // 위임 대상이 검사 예외를 던지지 않으므로 실제로는 도달하지 않는다.
            throw new AnalyzerUnavailableException("분석 중 예상치 못한 오류가 발생했습니다.", e);
        }
    }
}
