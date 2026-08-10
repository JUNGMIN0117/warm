package com.personalcolor.infrastructure.mlservice;

import com.personalcolor.domain.analysis.AnalysisOutcome;
import com.personalcolor.domain.analysis.AnalyzerUnavailableException;
import com.personalcolor.domain.analysis.ImageRejectedException;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.Undertone;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * HTTP 어댑터 테스트 — JDK 내장 HttpServer로 ml-service를 흉내 낸다.
 *
 * <p>MockWebServer 같은 라이브러리를 추가하지 않은 이유: 필요한 것이
 * "정해진 상태 코드와 본문을 돌려주는 서버" 하나뿐이라, 의존성을 늘리는
 * 것보다 JDK에 있는 것을 쓰는 편이 낫다.
 *
 * <p>여기서 검증하는 핵심은 <b>오류 번역</b>이다. 4xx가
 * {@link ImageRejectedException}이 되고 5xx가
 * {@link AnalyzerUnavailableException}이 되어야, 서킷 브레이커가
 * 사진 문제와 장애를 구분할 수 있다.
 */
@DisplayName("WebClientPersonalColorAnalyzer")
class WebClientPersonalColorAnalyzerTest {

    private static final String SUCCESS_BODY = """
        {
          "season": "autumn_warm",
          "confidence": 0.822,
          "probabilities": {
            "spring_warm": 0.132, "summer_cool": 0.004,
            "autumn_warm": 0.822, "winter_cool": 0.042
          },
          "undertone": "warm",
          "undertone_confidence": 0.954,
          "axes": [
            {"name": "undertone", "raw_value": 68.42, "normalized": 0.783,
             "low_label": "쿨(푸른기)", "high_label": "웜(노란기)",
             "interpretation": "웜 성향이 뚜렷합니다"}
          ],
          "features": {
            "lightness": 61.0, "a_star": 18.05, "b_star": 45.64,
            "chroma": 49.08, "hue_angle": 68.42, "ita": 13.55,
            "ita_category": "tan", "lightness_spread": 0.85,
            "pixel_count": 12453, "median_rgb": [198, 134, 66]
          },
          "white_balance": {
            "method": "gray_world", "gains": [1.0, 1.0, 1.0], "cast_strength": 0.0002
          },
          "mask_quality": {"coverage_ratio": 0.764, "otsu_threshold": 50.0},
          "quality_factor": 1.0,
          "warnings": [],
          "stages": null
        }
        """;

    private HttpServer server;
    private final AtomicReference<Response> nextResponse = new AtomicReference<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();

    private record Response(int status, String body, Duration delay) {
        static Response of(int status, String body) {
            return new Response(status, body, Duration.ZERO);
        }
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/analyze", exchange -> {
            lastQuery.set(exchange.getRequestURI().getQuery());
            try (InputStream in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            Response response = nextResponse.get();
            if (!response.delay().isZero()) {
                try {
                    Thread.sleep(response.delay().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(response.status(), payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private WebClientPersonalColorAnalyzer analyzer() {
        return analyzer(Duration.ofSeconds(5));
    }

    private WebClientPersonalColorAnalyzer analyzer(Duration timeout) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new WebClientPersonalColorAnalyzer(
                WebClient.builder().baseUrl(baseUrl).build(), timeout);
    }

    @Test
    @DisplayName("성공 응답을 도메인 모델로 변환한다")
    void mapsSuccessfulResponse() {
        nextResponse.set(Response.of(200, SUCCESS_BODY));

        AnalysisOutcome outcome = analyzer().analyze(new byte[]{1, 2, 3}, false);

        assertThat(outcome.measurement().season()).isEqualTo(Season.AUTUMN_WARM);
        assertThat(outcome.measurement().undertone()).isEqualTo(Undertone.WARM);
        assertThat(outcome.measurement().features().hueAngle()).isCloseTo(68.42, within(1e-9));
        assertThat(outcome.measurement().features().medianRgb().toHex()).isEqualTo("#C68642");
        assertThat(outcome.measurement().preprocessing().maskCoverageRatio())
                .isCloseTo(0.764, within(1e-9));
        assertThat(outcome.stages()).isEmpty();
    }

    @Test
    @DisplayName("include_stages를 쿼리 파라미터로 전달한다")
    void sendsIncludeStagesFlag() {
        nextResponse.set(Response.of(200, SUCCESS_BODY));

        analyzer().analyze(new byte[]{1}, true);

        assertThat(lastQuery.get()).contains("include_stages=true");
    }

    @Test
    @DisplayName("모르는 필드가 있어도 깨지지 않는다")
    void toleratesUnknownFields() {
        // ml-service가 응답에 필드를 더하는 것은 하위 호환 변경이다.
        // 이게 깨지면 상대가 무해한 변경을 할 때마다 게이트웨이가 죽는다.
        String withExtra = SUCCESS_BODY.replace(
                "\"quality_factor\": 1.0", "\"quality_factor\": 1.0, \"future_field\": 42");
        nextResponse.set(Response.of(200, withExtra));

        assertThat(analyzer().analyze(new byte[]{1}, false)).isNotNull();
    }

    @Test
    @DisplayName("422 얼굴 없음을 사진 문제로 번역한다")
    void translatesNoFaceTo422() {
        nextResponse.set(Response.of(422, """
            {"code": "NO_FACE_DETECTED",
             "message": "얼굴을 찾지 못했습니다. 정면 얼굴이 잘 나온 사진을 사용해 주세요.",
             "detail": null}
            """));

        assertThatThrownBy(() -> analyzer().analyze(new byte[]{1}, false))
                .isInstanceOf(ImageRejectedException.class)
                .hasMessageContaining("얼굴을 찾지 못했습니다")
                .extracting(e -> ((ImageRejectedException) e).reason())
                .isEqualTo(ImageRejectedException.Reason.NO_FACE_DETECTED);
    }

    @Test
    @DisplayName("400 디코딩 실패도 사진 문제다")
    void translatesDecodeFailure() {
        nextResponse.set(Response.of(400, """
            {"code": "IMAGE_DECODE_FAILED", "message": "이미지를 해석할 수 없습니다.",
             "detail": null}
            """));

        assertThatThrownBy(() -> analyzer().analyze(new byte[]{1}, false))
                .isInstanceOf(ImageRejectedException.class)
                .extracting(e -> ((ImageRejectedException) e).reason())
                .isEqualTo(ImageRejectedException.Reason.IMAGE_DECODE_FAILED);
    }

    @Test
    @DisplayName("모르는 오류 코드는 UNKNOWN으로 흡수한다")
    void absorbsUnknownErrorCode() {
        // ml-service가 새 4xx 코드를 추가했다고 게이트웨이가 500을 낼 이유는 없다.
        nextResponse.set(Response.of(422, """
            {"code": "SOMETHING_NEW", "message": "새로운 거절 사유", "detail": null}
            """));

        assertThatThrownBy(() -> analyzer().analyze(new byte[]{1}, false))
                .isInstanceOf(ImageRejectedException.class)
                .extracting(e -> ((ImageRejectedException) e).reason())
                .isEqualTo(ImageRejectedException.Reason.UNKNOWN);
    }

    @Test
    @DisplayName("503 모델 미로드는 측정기 문제다")
    void translatesServerErrorToUnavailable() {
        nextResponse.set(Response.of(503, """
            {"code": "MODEL_NOT_AVAILABLE", "message": "모델이 로드되지 않았습니다.",
             "detail": null}
            """));

        assertThatThrownBy(() -> analyzer().analyze(new byte[]{1}, false))
                .isInstanceOf(AnalyzerUnavailableException.class);
    }

    @Test
    @DisplayName("구조화되지 않은 오류 본문도 견딘다")
    void survivesUnstructuredErrorBody() {
        // 프록시나 로드밸런서가 만든 502는 우리 스키마를 따르지 않는다.
        nextResponse.set(Response.of(502, "<html>Bad Gateway</html>"));

        assertThatThrownBy(() -> analyzer().analyze(new byte[]{1}, false))
                .isInstanceOf(AnalyzerUnavailableException.class)
                .hasMessageContaining("502");
    }

    @Test
    @DisplayName("타임아웃은 측정기 문제로 번역한다")
    void translatesTimeout() {
        nextResponse.set(new Response(200, SUCCESS_BODY, Duration.ofSeconds(2)));

        assertThatThrownBy(() ->
                analyzer(Duration.ofMillis(300)).analyze(new byte[]{1}, false))
                .isInstanceOf(AnalyzerUnavailableException.class)
                .hasMessageContaining("초 안에 오지 않았습니다");
    }

    @Test
    @DisplayName("연결 자체가 안 되면 측정기 문제다")
    void translatesConnectionFailure() {
        server.stop(0);

        assertThatThrownBy(() -> analyzer().analyze(new byte[]{1}, false))
                .isInstanceOf(AnalyzerUnavailableException.class)
                .hasMessageContaining("연결할 수 없습니다");
    }
}
