package com.personalcolor.infrastructure.observability;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebClient 전파 필터 검증 — 실제 HTTP 왕복으로 헤더가 실리는지 본다.
 *
 * <p>어댑터 테스트와 같은 이유로 JDK 내장 HttpServer를 쓴다. 필요한 것은
 * "받은 헤더를 기억하는 서버" 하나뿐이라 목 라이브러리가 과하다.
 */
@DisplayName("CorrelationIdRelay")
class CorrelationIdRelayTest {

    private HttpServer server;
    private WebClient client;
    private final AtomicReference<String> receivedHeader = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedHeader.set(exchange.getRequestHeaders().getFirst(CorrelationId.HEADER));
            byte[] body = "{}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        client = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .filter(new CorrelationIdRelay())
                .build();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        MDC.clear();
    }

    @Test
    @DisplayName("MDC에 ID가 있으면 X-Request-Id 헤더로 전파한다")
    void relaysIdFromMdc() {
        MDC.put(CorrelationId.MDC_KEY, "relay-test-1234");

        client.get().retrieve().toBodilessEntity().block();

        assertThat(receivedHeader.get()).isEqualTo("relay-test-1234");
    }

    @Test
    @DisplayName("MDC가 비어 있으면 헤더를 붙이지 않는다 — 빈 값보다 없는 편이 낫다")
    void omitsHeaderWhenMdcEmpty() {
        MDC.clear();

        client.get().retrieve().toBodilessEntity().block();

        assertThat(receivedHeader.get()).isNull();
    }
}
