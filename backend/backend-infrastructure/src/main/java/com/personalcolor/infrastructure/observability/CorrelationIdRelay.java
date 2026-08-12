package com.personalcolor.infrastructure.observability;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * WebClient 요청에 현재 스레드의 상관관계 ID를 실어 보낸다.
 *
 * <p>MDC는 스레드 로컬인데 WebClient는 리액티브라 "다른 스레드에서 읽히지
 * 않나?"가 당연한 의문이다. 이 프로젝트에서는 안전하다 — 어댑터가
 * {@code .block()}으로 요청 스레드에서 구독하므로, 이 필터의 실행(요청 조립)도
 * 서블릿 필터가 MDC를 채워둔 바로 그 스레드에서 일어난다. 완전 리액티브로
 * 전환한다면 이 가정이 깨지므로 Reactor Context로 옮겨야 한다 (ADR-008).
 *
 * <p>ID가 없으면(테스트, 배치 등 필터 밖 호출) 헤더를 붙이지 않는다 —
 * 빈 값을 보내는 것보다 없는 편이 수신 측 판단이 단순하다.
 */
public final class CorrelationIdRelay implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String id = CorrelationId.current();
        if (id == null) {
            return next.exchange(request);
        }
        return next.exchange(
                ClientRequest.from(request).header(CorrelationId.HEADER, id).build());
    }
}
