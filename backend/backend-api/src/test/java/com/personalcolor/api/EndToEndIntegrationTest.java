package com.personalcolor.api;

import com.personalcolor.domain.analysis.port.AnalysisRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 종단 통합 테스트 — 전체 스택을 실제로 관통시킨다.
 *
 * <p>진짜인 것: PostgreSQL, Redis, Spring 전체 컨텍스트, 내장 웹서버,
 * 그리고 HTTP 클라이언트. 가짜인 것은 ml-service 하나뿐이고 그것도
 * 진짜 HTTP 서버로 세운다(JDK 내장 {@code HttpServer}).
 *
 * <p>ml-service를 컨테이너로 띄우지 않은 이유는 이미지가 아직 없기 때문이다
 * (Step 6에서 만든다). 그때 이 스텁을 실제 컨테이너로 바꾸는 것이 자연스러운
 * 다음 단계다. 지금도 <b>HTTP 경계는 진짜</b>이므로 직렬화·오류 번역 같은
 * 실제 실패 지점은 검증된다.
 *
 * <p>단위 테스트가 각 조각을 보는 반면 여기서 보는 것은 <b>조각들이 실제로
 * 맞물리는가</b>다. 특히 계절 코드가 Python → Java → DB 세 곳에서 일치하는지는
 * 여기서만 확인된다.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("종단 통합")
class EndToEndIntegrationTest {

    private static final String JWT_SECRET =
            "integration-test-secret-key-long-enough-for-hs256";
    private static final String PASSWORD = "correct horse battery staple";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** ml-service 스텁. 응답을 테스트마다 바꿔 끼운다. */
    private static HttpServer mlService;
    private static final AtomicReference<StubResponse> NEXT_RESPONSE = new AtomicReference<>();
    private static final AtomicInteger CALL_COUNT = new AtomicInteger();

    private record StubResponse(int status, String body) {}

    @BeforeAll
    static void startStub() throws IOException {
        mlService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mlService.createContext("/v1/analyze", exchange -> {
            CALL_COUNT.incrementAndGet();
            try (InputStream in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            StubResponse response = NEXT_RESPONSE.get();
            byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(response.status(), payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        mlService.start();
    }

    @AfterAll
    static void stopStub() {
        mlService.stop(0);
    }

    /**
     * 컨테이너 좌표를 스프링 환경에 주입한다.
     *
     * <p>{@code @TestConfiguration} 내부 클래스로 만들었다가 되돌렸다.
     * 그 방식은 {@code @Nested} 클래스가 상속하지 못해 중첩 클래스마다 설정이
     * 빠진 별도 컨텍스트가 뜬다. {@code @DynamicPropertySource}는 상속되므로
     * 하나의 컨텍스트를 공유한다.
     */
    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("ml-service.base-url",
                () -> "http://127.0.0.1:" + mlService.getAddress().getPort());
        registry.add("jwt.secret", () -> JWT_SECRET);
        // 운영 기본값을 테스트에서도 그대로 쓴다 — 마이그레이션과 스키마 검증이
        // 실제로 도는지 보는 것이 이 테스트의 목적이다.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private AnalysisRepository analyses;

    @LocalServerPort
    private int port;

    private HttpTestClient client;

    @BeforeEach
    void createClient() {
        client = new HttpTestClient(port);
    }

    // --- 헬퍼 -------------------------------------------------------------

    private HttpTestClient.Response analyze(String token, byte[] image) {
        return client.postImage("/api/v1/analyses", image, token);
    }

    private String registerAndGetToken(String email) {
        HttpTestClient.Response response = client.postJson("/api/v1/auth/register",
                Map.of("email", email, "displayName", "테스터", "password", PASSWORD), null);

        assertThat(response.status()).isEqualTo(201);
        return (String) client.asMap(response).get("accessToken");
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    /** 매번 다른 이미지 — 캐시가 테스트끼리 간섭하지 않게 한다. */
    private static byte[] uniqueImage() {
        return UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void stubSuccess() {
        NEXT_RESPONSE.set(new StubResponse(200, MlResponses.AUTUMN_WARM));
    }

    @Nested
    @DisplayName("익명 흐름")
    class AnonymousFlow {

        @Test
        @DisplayName("로그인 없이 분석하면 결과는 나오지만 저장되지 않는다")
        void analyzesWithoutSaving() {
            stubSuccess();

            HttpTestClient.Response response = analyze(null, uniqueImage());

            assertThat(response.status()).isEqualTo(200);
            Map<String, Object> body = client.asMap(response);
            assertThat(body.get("saved")).isEqualTo(false);

            // 응답 필드만 믿지 않고 DB로 확인한다.
            UUID id = UUID.fromString((String) body.get("id"));
            assertThat(analyses.findById(id)).isEmpty();
        }

        @Test
        @DisplayName("Python 측정값과 DB 팔레트가 한 응답에 합쳐진다")
        void joinsMeasurementWithCatalog() {
            // ADR-005의 경계가 실제로 성립하는지 보는 테스트.
            // 계절 코드가 Python → Java → DB 세 곳에서 일치해야만 통과한다.
            stubSuccess();

            Map<String, Object> body = client.asMap(analyze(null, uniqueImage()));

            Map<String, Object> season = HttpTestClient.nested(body, "season");
            assertThat(season.get("code")).isEqualTo("autumn_warm");
            assertThat(season.get("labelKo")).isEqualTo("가을 웜");            // DB 시드에서
            assertThat(HttpTestClient.nestedList(season, "bestColors"))
                    .hasSizeGreaterThanOrEqualTo(6);

            Map<String, Object> features = HttpTestClient.nested(body, "features");
            assertThat(features.get("medianRgbHex")).isEqualTo("#C68642");    // ml-service에서
        }

        @Test
        @DisplayName("판정 근거와 보정량을 함께 보고한다")
        void reportsEvidenceAndPreprocessing() {
            stubSuccess();

            Map<String, Object> body = client.asMap(analyze(null, uniqueImage()));

            assertThat(HttpTestClient.nestedList(body, "axes")).hasSize(1);
            assertThat(HttpTestClient.nested(body, "preprocessing"))
                    .containsKeys("whiteBalanceMethod", "gains", "castStrength");
            assertThat(HttpTestClient.nested(body, "probabilities"))
                    .containsKeys("spring_warm", "summer_cool", "autumn_warm", "winter_cool");
        }
    }

    @Nested
    @DisplayName("로그인 흐름")
    class AuthenticatedFlow {

        @Test
        @DisplayName("가입 → 분석 → 이력 조회가 이어진다")
        void registerAnalyzeAndSeeHistory() {
            String token = registerAndGetToken(uniqueEmail("flow"));
            stubSuccess();

            HttpTestClient.Response analysis = analyze(token, uniqueImage());
            assertThat(analysis.status()).isEqualTo(201);
            assertThat(client.asMap(analysis).get("saved")).isEqualTo(true);

            HttpTestClient.Response history = client.get("/api/v1/analyses", token);
            assertThat(history.status()).isEqualTo(200);

            List<Map<String, Object>> items = client.asList(history);
            assertThat(items).hasSize(1);
            assertThat(items.getFirst().get("seasonCode")).isEqualTo("autumn_warm");
            assertThat(items.getFirst().get("medianRgbHex")).isEqualTo("#C68642");
            // 이력에는 대표 색과 수치만 있다 — 원본 이미지를 저장하지 않기 때문이다.
            assertThat(items.getFirst()).doesNotContainKeys("image", "imageUrl");
        }

        @Test
        @DisplayName("다른 사용자의 이력은 보이지 않는다")
        void historyIsIsolatedPerUser() {
            String mine = registerAndGetToken(uniqueEmail("mine"));
            String theirs = registerAndGetToken(uniqueEmail("theirs"));
            stubSuccess();
            analyze(theirs, uniqueImage());

            assertThat(client.asList(client.get("/api/v1/analyses", mine))).isEmpty();
        }

        @Test
        @DisplayName("같은 이메일로 두 번 가입하면 409")
        void rejectsDuplicateRegistration() {
            String email = uniqueEmail("dup");
            registerAndGetToken(email);

            // 대소문자만 다른 주소도 같은 사서함이므로 막혀야 한다.
            HttpTestClient.Response second = client.postJson("/api/v1/auth/register",
                    Map.of("email", email.toUpperCase(Locale.ROOT),
                            "displayName", "두번째", "password", "another passphrase here"),
                    null);

            assertThat(second.status()).isEqualTo(409);
            assertThat(client.asMap(second).get("code")).isEqualTo("EMAIL_ALREADY_USED");
        }

        @Test
        @DisplayName("틀린 비밀번호로 로그인하면 401")
        void rejectsWrongPassword() {
            String email = uniqueEmail("login");
            registerAndGetToken(email);

            HttpTestClient.Response response = client.postJson("/api/v1/auth/login",
                    Map.of("email", email, "password", "wrong password entirely"), null);

            assertThat(response.status()).isEqualTo(401);
            assertThat(client.asMap(response).get("code")).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        @DisplayName("로그인하면 같은 계정으로 이력을 이어 볼 수 있다")
        void loginReturnsUsableToken() {
            String email = uniqueEmail("relogin");
            String firstToken = registerAndGetToken(email);
            stubSuccess();
            analyze(firstToken, uniqueImage());

            HttpTestClient.Response login = client.postJson("/api/v1/auth/login",
                    Map.of("email", email, "password", PASSWORD), null);
            String secondToken = (String) client.asMap(login).get("accessToken");

            assertThat(client.asList(client.get("/api/v1/analyses", secondToken))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("캐시")
    class Caching {

        @Test
        @DisplayName("같은 이미지를 두 번 올리면 ml-service를 한 번만 부른다")
        void cachesByImageHash() {
            // ml-service가 무상태·결정론적이라 성립하는 최적화다.
            stubSuccess();
            byte[] image = uniqueImage();

            int before = CALL_COUNT.get();
            analyze(null, image);
            analyze(null, image);

            assertThat(CALL_COUNT.get() - before).isEqualTo(1);
        }

        @Test
        @DisplayName("include_stages가 다르면 캐시를 공유하지 않는다")
        void stageFlagIsPartOfCacheKey() {
            // 이미지 해시만으로 키를 만들면 단계 이미지 없이 캐시된 응답이
            // 시각화 요청에 반환되어 프론트가 조용히 빈 화면을 띄운다.
            stubSuccess();
            byte[] image = uniqueImage();

            int before = CALL_COUNT.get();
            client.postImage("/api/v1/analyses?includeStages=false", image, null);
            client.postImage("/api/v1/analyses?includeStages=true", image, null);

            assertThat(CALL_COUNT.get() - before).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("오류 전파")
    class ErrorPropagation {

        @Test
        @DisplayName("ml-service의 422가 그대로 422로 나간다")
        void propagatesImageRejection() {
            NEXT_RESPONSE.set(new StubResponse(422, """
                {"code": "NO_FACE_DETECTED",
                 "message": "얼굴을 찾지 못했습니다. 정면 얼굴이 잘 나온 사진을 사용해 주세요.",
                 "detail": null}
                """));

            HttpTestClient.Response response = analyze(null, uniqueImage());

            assertThat(response.status()).isEqualTo(422);
            Map<String, Object> body = client.asMap(response);
            assertThat(body.get("code")).isEqualTo("NO_FACE_DETECTED");
            // 메시지를 게이트웨이가 다시 쓰지 않는다 — 실패 원인을 가장 잘 아는
            // 쪽이 측정기이므로 안내도 그쪽이 더 구체적이다.
            assertThat((String) body.get("message")).contains("얼굴을 찾지 못했");
        }

        @Test
        @DisplayName("ml-service 장애는 503으로 나간다")
        void propagatesAnalyzerFailure() {
            NEXT_RESPONSE.set(new StubResponse(503, """
                {"code": "MODEL_NOT_AVAILABLE", "message": "모델 미로드", "detail": null}
                """));

            HttpTestClient.Response response = analyze(null, uniqueImage());

            assertThat(response.status()).isEqualTo(503);
            assertThat(client.asMap(response).get("code")).isEqualTo("ANALYZER_UNAVAILABLE");
        }

        @Test
        @DisplayName("토큰 없이 이력을 요청하면 401")
        void historyRequiresAuth() {
            HttpTestClient.Response response = client.get("/api/v1/analyses", null);

            assertThat(response.status()).isEqualTo(401);
            assertThat(client.asMap(response).get("code")).isEqualTo("UNAUTHORIZED");
        }
    }

    @Nested
    @DisplayName("공개 카탈로그")
    class PublicCatalog {

        @Test
        @DisplayName("로그인 없이 네 계절을 모두 조회할 수 있다")
        void listsAllSeasons() {
            HttpTestClient.Response response = client.get("/api/v1/seasons", null);

            assertThat(response.status()).isEqualTo(200);
            assertThat(client.asList(response)).hasSize(4);
        }

        @Test
        @DisplayName("모르는 계절 코드는 400")
        void rejectsUnknownSeasonCode() {
            assertThat(client.get("/api/v1/seasons/autumn_cool", null).status()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("health는 인증 없이 열려 있다")
    void healthIsPublic() {
        assertThat(client.get("/actuator/health", null).status()).isEqualTo(200);
    }

    /** ml-service 응답 픽스처. */
    static final class MlResponses {
        private MlResponses() {}

        static final String AUTUMN_WARM = """
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
    }
}
