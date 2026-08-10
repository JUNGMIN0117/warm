package com.personalcolor.api.web;

import com.personalcolor.api.security.JwtService;
import com.personalcolor.api.security.SecurityConfig;
import com.personalcolor.domain.analysis.AnalysisRecord;
import com.personalcolor.domain.analysis.AnalysisView;
import com.personalcolor.domain.analysis.AnalyzeImage;
import com.personalcolor.domain.analysis.AnalyzerUnavailableException;
import com.personalcolor.domain.analysis.ImageRejectedException;
import com.personalcolor.domain.analysis.ViewAnalysisHistory;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.port.SeasonProfileRepository;
import com.personalcolor.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
// Boot 4에서 @WebMvcTest가 boot.test.autoconfigure.web.servlet →
// boot.webmvc.test.autoconfigure로 옮겨졌다 (자동설정 모듈 분리의 여파).
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 분석 엔드포인트 웹 계층 테스트.
 *
 * <p>DB도 ml-service도 없이 HTTP 계약만 본다 — 상태 코드, 인증 경계,
 * 오류 번역. 유스케이스는 목으로 대체한다.
 */
@WebMvcTest(controllers = AnalysisController.class)
@Import({SecurityConfig.class, AnalysisControllerTest.TestBeans.class})
@DisplayName("분석 엔드포인트")
class AnalysisControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-10T06:00:00Z");
    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256!!";

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        com.personalcolor.api.security.JwtProperties jwtProperties() {
            return new com.personalcolor.api.security.JwtProperties(
                    SECRET, java.time.Duration.ofHours(12), "personal-color-ai");
        }

        @Bean
        JwtService jwtService(
                com.personalcolor.api.security.JwtProperties props, Clock clock) {
            return new JwtService(props, clock);
        }

        @Bean
        com.personalcolor.api.security.JwtAuthenticationFilter jwtFilter(JwtService jwt) {
            return new com.personalcolor.api.security.JwtAuthenticationFilter(jwt);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AnalyzeImage analyzeImage;

    @MockitoBean
    private ViewAnalysisHistory history;

    @MockitoBean
    private SeasonProfileRepository profiles;

    private MockMultipartFile photo;

    @BeforeEach
    void setUp() {
        photo = new MockMultipartFile(
                "image", "face.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});
    }

    private String tokenFor(UUID userId) {
        return jwtService.issue(new User(
                userId, "me@example.com", "정민", "hash", NOW)).value();
    }

    private static AnalysisView viewFor(UUID userId) {
        AnalysisRecord record = new AnalysisRecord(
                UUID.randomUUID(), "a".repeat(64), Optional.ofNullable(userId),
                Fixtures.autumnWarmMeasurement(), NOW);
        return new AnalysisView(record, Fixtures.autumnWarmProfile(), Optional.empty());
    }

    @Nested
    @DisplayName("익명 분석")
    class Anonymous {

        @Test
        @DisplayName("로그인 없이 분석할 수 있다")
        void allowsAnalysisWithoutLogin() throws Exception {
            // 첫 사용에 회원가입을 요구하면 대부분 떠난다. 이 서비스의
            // 본체가 익명으로 동작해야 한다는 것이 제품 결정이다.
            given(analyzeImage.execute(any(), eq(Optional.empty()), anyBoolean()))
                    .willReturn(viewFor(null));

            mvc.perform(multipart("/api/v1/analyses").file(photo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.season.code").value("autumn_warm"))
                    .andExpect(jsonPath("$.saved").value(false));
        }

        @Test
        @DisplayName("익명 결과는 저장되지 않았음을 알린다")
        void reportsNotSaved() throws Exception {
            given(analyzeImage.execute(any(), eq(Optional.empty()), anyBoolean()))
                    .willReturn(viewFor(null));

            // saved=false여야 프론트가 "이력에 담겼습니다"를 잘못 안내하지 않는다.
            mvc.perform(multipart("/api/v1/analyses").file(photo))
                    .andExpect(jsonPath("$.saved").value(false));
        }

        @Test
        @DisplayName("판정 근거 수치를 함께 준다")
        void includesEvidence() throws Exception {
            given(analyzeImage.execute(any(), any(), anyBoolean()))
                    .willReturn(viewFor(null));

            mvc.perform(multipart("/api/v1/analyses").file(photo))
                    .andExpect(jsonPath("$.features.hueAngle").value(68.42))
                    .andExpect(jsonPath("$.features.medianRgbHex").value("#C68642"))
                    .andExpect(jsonPath("$.probabilities.autumn_warm").value(0.822))
                    .andExpect(jsonPath("$.preprocessing.castStrength").exists())
                    .andExpect(jsonPath("$.axes.length()").value(3));
        }
    }

    @Nested
    @DisplayName("로그인 분석")
    class Authenticated {

        @Test
        @DisplayName("토큰이 있으면 저장하고 201을 준다")
        void savesForAuthenticatedUser() throws Exception {
            UUID userId = UUID.randomUUID();
            given(analyzeImage.execute(any(), eq(Optional.of(userId)), anyBoolean()))
                    .willReturn(viewFor(userId));

            mvc.perform(multipart("/api/v1/analyses").file(photo)
                            .header("Authorization", "Bearer " + tokenFor(userId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.saved").value(true));
        }

        @Test
        @DisplayName("잘못된 토큰은 익명으로 취급한다")
        void treatsInvalidTokenAsAnonymous() throws Exception {
            // 필터가 401을 던지면 익명 허용 흐름이 막힌다.
            given(analyzeImage.execute(any(), eq(Optional.empty()), anyBoolean()))
                    .willReturn(viewFor(null));

            mvc.perform(multipart("/api/v1/analyses").file(photo)
                            .header("Authorization", "Bearer garbage.token.here"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("이력 조회는 인증이 필요하다")
    class HistoryRequiresAuth {

        @Test
        @DisplayName("토큰 없이 목록을 요청하면 401")
        void listRequiresToken() throws Exception {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/analyses"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("남의 분석은 404로 숨긴다")
        void hidesOthersAnalysis() throws Exception {
            // 403은 "그 id는 존재한다"를 알려주는 셈이다.
            UUID userId = UUID.randomUUID();
            given(history.findOwned(any(), eq(userId))).willReturn(Optional.empty());

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/analyses/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + tokenFor(userId)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("오류 번역")
    class ErrorTranslation {

        @Test
        @DisplayName("얼굴 없음은 422")
        void noFaceIs422() throws Exception {
            willThrow(new ImageRejectedException(
                    ImageRejectedException.Reason.NO_FACE_DETECTED, "얼굴을 찾지 못했습니다."))
                    .given(analyzeImage).execute(any(), any(), anyBoolean());

            mvc.perform(multipart("/api/v1/analyses").file(photo))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("NO_FACE_DETECTED"));
        }

        @Test
        @DisplayName("디코딩 실패는 400")
        void decodeFailureIs400() throws Exception {
            willThrow(new ImageRejectedException(
                    ImageRejectedException.Reason.IMAGE_DECODE_FAILED, "해석 불가"))
                    .given(analyzeImage).execute(any(), any(), anyBoolean());

            mvc.perform(multipart("/api/v1/analyses").file(photo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IMAGE_DECODE_FAILED"));
        }

        @Test
        @DisplayName("측정기 장애는 503")
        void analyzerDownIs503() throws Exception {
            // 사진 문제(4xx)와 반드시 구분되어야 한다 — 재시도 가능 여부가 다르다.
            willThrow(new AnalyzerUnavailableException("ml-service 응답 없음"))
                    .given(analyzeImage).execute(any(), any(), anyBoolean());

            mvc.perform(multipart("/api/v1/analyses").file(photo))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ANALYZER_UNAVAILABLE"));
        }

        @Test
        @DisplayName("빈 파일은 400")
        void emptyFileIs400() throws Exception {
            MockMultipartFile empty = new MockMultipartFile(
                    "image", "e.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

            mvc.perform(multipart("/api/v1/analyses").file(empty))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IMAGE_DECODE_FAILED"));
        }
    }

    /** 픽스처 — 계절 프로필과 측정값. */
    static final class Fixtures {
        private Fixtures() {}

        static com.personalcolor.domain.analysis.Measurement autumnWarmMeasurement() {
            var probabilities = new java.util.EnumMap<Season, Double>(Season.class);
            probabilities.put(Season.SPRING_WARM, 0.132);
            probabilities.put(Season.SUMMER_COOL, 0.004);
            probabilities.put(Season.AUTUMN_WARM, 0.822);
            probabilities.put(Season.WINTER_COOL, 0.042);

            return new com.personalcolor.domain.analysis.Measurement(
                    Season.AUTUMN_WARM, 0.822, probabilities,
                    com.personalcolor.domain.season.Undertone.WARM, 0.954,
                    java.util.List.of(
                            axis("undertone"), axis("depth"), axis("clarity")),
                    new com.personalcolor.domain.analysis.SkinFeatures(
                            61.0, 18.05, 45.64, 49.08, 68.42, 13.55, "tan", 0.85, 12453,
                            new com.personalcolor.domain.analysis.RgbColor(198, 134, 66)),
                    new com.personalcolor.domain.analysis.PreprocessingReport(
                            "gray_world", 1.0, 1.0, 1.0, 0.0002, 0.764, 50.0),
                    1.0, java.util.List.of());
        }

        private static com.personalcolor.domain.analysis.AxisReading axis(String name) {
            return new com.personalcolor.domain.analysis.AxisReading(
                    name, 68.42, 0.783, "낮음", "높음", "뚜렷합니다");
        }

        static com.personalcolor.domain.season.SeasonProfile autumnWarmProfile() {
            return new com.personalcolor.domain.season.SeasonProfile(
                    Season.AUTUMN_WARM, "가을 웜", "Autumn Warm", "🍂",
                    java.util.List.of("깊은"), "설명",
                    java.util.List.of(
                            color("머스타드", "#D4A017"), color("테라코타", "#C56A3E"),
                            color("올리브", "#6B7A3A"), color("카멜", "#B5813F"),
                            color("브릭", "#9C4A2F"), color("모스그린", "#4F5D3A")),
                    java.util.List.of(color("형광 핑크", "#FF69B4")),
                    java.util.List.of("금색 액세서리"));
        }

        private static com.personalcolor.domain.season.SeasonProfile.PaletteColor color(
                String name, String hex) {
            return com.personalcolor.domain.season.SeasonProfile.PaletteColor.of(name, hex);
        }
    }
}
