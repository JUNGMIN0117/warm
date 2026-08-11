package com.personalcolor.api.observability;

import com.personalcolor.infrastructure.observability.CorrelationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관측성 필터의 계약을 고정한다.
 *
 * <p>핵심은 세 가지 — 요청을 처리하는 동안 MDC에 ID가 있어야 하고,
 * 응답 헤더로 같은 ID가 나가야 하며, 요청이 끝나면 MDC가 깨끗해야 한다.
 * 마지막 항목이 가장 잘 깨진다: 서블릿 스레드는 재사용되므로 정리를 빼먹으면
 * 다음 요청 로그가 남의 ID를 달고 나온다.
 */
@DisplayName("RequestObservabilityFilter")
class RequestObservabilityFilterTest {

    private final RequestObservabilityFilter filter = new RequestObservabilityFilter();

    @Test
    @DisplayName("헤더가 없으면 새 ID를 발급해 응답 헤더로 돌려준다")
    void generatesIdWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/seasons");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        String issued = response.getHeader(CorrelationId.HEADER);
        assertThat(issued).isNotBlank();
        assertThat(CorrelationId.isValid(issued)).isTrue();
    }

    @Test
    @DisplayName("유효한 헤더가 오면 그 ID를 그대로 수용한다")
    void acceptsValidIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/seasons");
        request.addHeader(CorrelationId.HEADER, "front-abc-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("front-abc-12345");
    }

    @Test
    @DisplayName("형식이 틀린 헤더는 버리고 새로 발급한다 — 로그 인젝션 방어")
    void rejectsMalformedIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/seasons");
        request.addHeader(CorrelationId.HEADER, "bad\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        String issued = response.getHeader(CorrelationId.HEADER);
        assertThat(issued).isNotEqualTo("bad\nvalue");
        assertThat(CorrelationId.isValid(issued)).isTrue();
    }

    @Test
    @DisplayName("체인 안에서는 MDC로 ID를 읽을 수 있고, 끝나면 정리된다")
    void bindsMdcDuringChainAndCleansUpAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/analyses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInChain.set(CorrelationId.current()));

        assertThat(seenInChain.get())
                .as("체인 실행 중에는 MDC에 ID가 있어야 로그에 실린다")
                .isNotBlank()
                .isEqualTo(response.getHeader(CorrelationId.HEADER));
        assertThat(MDC.get(CorrelationId.MDC_KEY))
                .as("스레드가 재사용되므로 요청이 끝나면 반드시 비워야 한다")
                .isNull();
    }

    @Test
    @DisplayName("체인이 예외를 던져도 MDC는 정리된다")
    void cleansUpMdcEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/seasons");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception expected) {
            // 예외 자체는 관심사가 아니다 — 정리 여부만 본다.
        }

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
}
