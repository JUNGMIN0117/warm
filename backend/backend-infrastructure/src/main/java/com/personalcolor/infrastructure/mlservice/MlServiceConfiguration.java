package com.personalcolor.infrastructure.mlservice;

import com.personalcolor.domain.analysis.AnalysisOutcome;
import com.personalcolor.domain.analysis.ImageRejectedException;
import com.personalcolor.domain.analysis.port.PersonalColorAnalyzer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

/**
 * ml-service 어댑터 배선.
 *
 * <p>세 클래스를 데코레이터로 겹쳐 하나의 {@link PersonalColorAnalyzer}를 만든다.
 *
 * <pre>
 *   CachingAnalyzer          ← 캐시 히트면 아래로 내려가지 않는다
 *     └ CircuitBreakingAnalyzer   ← 회로가 열려 있으면 호출하지 않는다
 *         └ WebClientPersonalColorAnalyzer  ← 실제 HTTP
 * </pre>
 *
 * <p><b>순서에 의미가 있다.</b> 캐시가 바깥이어야 회로가 열려 있어도
 * 캐시된 결과는 계속 서빙된다. 반대로 두면 장애 중에 이미 알고 있는
 * 답조차 내주지 못한다.
 */
@Configuration
@EnableConfigurationProperties(MlServiceProperties.class)
public class MlServiceConfiguration {

    /**
     * ml-service 전용 WebClient.
     *
     * <p>주입받은 {@code WebClient.Builder} 대신 {@code WebClient.builder()}를
     * 직접 부른다. Boot 4에서 그 빈이 항상 자동설정되지는 않아 서블릿 스택
     * 컨텍스트에서 "No qualifying bean of type WebClient$Builder"로 기동이
     * 실패했다. 이 클라이언트는 Boot의 기본 커스터마이저(코덱·메트릭)가
     * 필요할 만큼 복잡하지 않으므로, 자동설정에 기대는 대신 직접 만든다.
     */
    @Bean
    public WebClient mlServiceWebClient(MlServiceProperties props) {
        return WebClient.builder()
                .baseUrl(props.baseUrl())
                // 게이트웨이가 발급한 상관관계 ID를 ml-service까지 전파한다.
                // 세 서비스의 로그를 요청 하나로 꿰는 실이다 (ADR-008).
                .filter(new com.personalcolor.infrastructure.observability.CorrelationIdRelay())
                .build();
    }

    /**
     * 서킷 브레이커.
     *
     * <p>{@code ignoreExceptions}에 {@link ImageRejectedException}을 넣는 것이
     * 이 설정의 핵심이다. 얼굴 없는 사진은 ml-service가 정상 동작한 결과이지
     * 장애가 아니므로 실패로 세지 않는다.
     */
    @Bean
    public CircuitBreaker mlServiceCircuitBreaker(MlServiceProperties props) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(props.circuitFailureRateThreshold())
                .waitDurationInOpenState(props.circuitWaitDuration())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(props.circuitSlidingWindowSize())
                .minimumNumberOfCalls(props.circuitSlidingWindowSize())
                .permittedNumberOfCallsInHalfOpenState(2)
                .ignoreExceptions(ImageRejectedException.class)
                .build();

        return CircuitBreaker.of("ml-service", config);
    }

    /**
     * 캐시용 RedisTemplate.
     *
     * <p>JDK 직렬화 대신 JSON을 쓴다. JDK 직렬화는 클래스 구조가 바뀌면
     * 역직렬화가 깨지는데, record에 필드 하나 추가하는 일이 캐시 전체를
     * 못 읽게 만드는 상황은 피해야 한다. JSON은 알 수 없는 필드를 무시하도록
     * 설정할 수 있어 스키마 진화에 관대하다.
     *
     * <p>Spring Boot 4는 <b>Jackson 3</b>을 쓴다. 패키지가
     * {@code com.fasterxml.jackson.databind}가 아니라
     * {@code tools.jackson.databind}이고, 직렬화기도 {@code Jackson2...}가
     * 아니라 {@link JacksonJsonRedisSerializer}다. 애너테이션만
     * {@code com.fasterxml.jackson.annotation}으로 남아 호환된다 (ADR-006).
     */
    @Bean
    public RedisTemplate<String, AnalysisOutcome> analysisOutcomeRedisTemplate(
            RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, AnalysisOutcome> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(
                new JacksonJsonRedisSerializer<>(objectMapper, AnalysisOutcome.class));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public PersonalColorAnalyzer personalColorAnalyzer(
            WebClient mlServiceWebClient,
            CircuitBreaker mlServiceCircuitBreaker,
            RedisTemplate<String, AnalysisOutcome> analysisOutcomeRedisTemplate,
            MlServiceProperties props) {

        PersonalColorAnalyzer http =
                new WebClientPersonalColorAnalyzer(mlServiceWebClient, props.timeout());
        PersonalColorAnalyzer guarded =
                new CircuitBreakingAnalyzer(http, mlServiceCircuitBreaker);
        return new CachingAnalyzer(guarded, analysisOutcomeRedisTemplate, props.cacheTtl());
    }
}
