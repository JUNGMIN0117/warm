package com.personalcolor.api.observability;

import com.personalcolor.infrastructure.observability.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청 관측성 필터 — 상관관계 ID 바인딩 + 요청 완료 로그.
 *
 * <p>하는 일은 둘이고 순서가 중요하다.
 *
 * <ol>
 *   <li><b>ID 바인딩.</b> {@code X-Request-Id} 헤더가 유효하면 수용하고,
 *       없거나 형식이 틀리면 새로 발급한다. 수용을 허용하는 이유: 프론트
 *       프록시나 로드밸런서가 이미 ID를 발급했다면 그 흐름을 끊지 않는
 *       편이 추적에 유리하다. MDC에 넣어 이 요청이 남기는 <b>모든</b>
 *       로그 줄에 자동으로 실리게 하고, 응답 헤더로도 돌려줘 클라이언트가
 *       "문의 코드"로 쓸 수 있게 한다.</li>
 *   <li><b>완료 로그.</b> 상태 코드와 소요 시간을 요청당 한 줄 남긴다.
 *       접근 로그의 최소형이다 — 이것만 있어도 "그 시간대에 어떤 요청이
 *       얼마나 걸렸나"를 로그로 답할 수 있다.</li>
 * </ol>
 *
 * <p>필터 순서를 최우선으로 두는 이유: 시큐리티 필터보다 먼저 실행돼야
 * 인증 실패(401) 로그에도 상관관계 ID가 붙는다.
 *
 * <p>MDC 정리는 {@code finally}에서 한다 — 서블릿 컨테이너는 스레드를
 * 재사용하므로, 지우지 않으면 다음 요청이 이전 요청의 ID를 물려받는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestObservabilityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http.request");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String incoming = request.getHeader(CorrelationId.HEADER);
        String correlationId =
                CorrelationId.isValid(incoming) ? incoming : CorrelationId.generate();

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);

        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            logCompletion(request, response.getStatus(), elapsedMs);
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    /**
     * 헬스체크(/actuator)는 DEBUG로 낮춘다 — 10초마다 오는 프로브가
     * INFO 로그를 도배하면 정작 봐야 할 줄이 묻힌다.
     */
    private static void logCompletion(HttpServletRequest request, int status, long elapsedMs) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            log.debug("{} {} -> {} ({}ms)", method, path, status, elapsedMs);
        } else {
            log.info("{} {} -> {} ({}ms)", method, path, status, elapsedMs);
        }
    }
}
