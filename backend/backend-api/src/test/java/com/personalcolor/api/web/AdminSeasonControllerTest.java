package com.personalcolor.api.web;

import com.personalcolor.api.security.JwtService;
import com.personalcolor.api.security.SecurityConfig;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.SeasonProfile;
import com.personalcolor.domain.season.UpdateSeasonCuration;
import com.personalcolor.domain.user.Role;
import com.personalcolor.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 큐레이션 편집의 인가 경계 테스트.
 *
 * <p>이 파일의 핵심은 200이 아니라 <b>401과 403</b>이다 — "관리자만"이라는
 * 규칙은 SecurityConfig 한 줄로 선언되는데, 그 한 줄이 조용히 지워져도
 * 컴파일은 통과한다. 익명·일반 사용자가 거절되는 것을 여기서 고정한다.
 */
@WebMvcTest(controllers = AdminSeasonController.class)
@Import({SecurityConfig.class, AdminSeasonControllerTest.TestBeans.class})
@DisplayName("관리자 큐레이션 편집")
class AdminSeasonControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-12T06:00:00Z");
    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256!!";

    private static final String VALID_BODY = """
        {
          "keywords": ["깊은", "따뜻한"],
          "description": "황금빛이 도는 깊은 피부톤입니다.",
          "bestColors": [
            {"name": "머스타드", "hex": "#D4A017"},
            {"name": "테라코타", "hex": "#C56A3E"},
            {"name": "올리브", "hex": "#6B7A3A"},
            {"name": "카멜", "hex": "#B5813F"},
            {"name": "브릭", "hex": "#9C4A2F"},
            {"name": "살구", "hex": "#F5B183"}
          ],
          "worstColors": [
            {"name": "형광 핑크", "hex": "#FF69B4"},
            {"name": "퓨어 화이트", "hex": "#FFFFFF"},
            {"name": "실버", "hex": "#C0C0C0"}
          ],
          "stylingTips": ["금색 액세서리가 어울립니다."]
        }
        """;

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
    private UpdateSeasonCuration updateSeasonCuration;

    private String tokenWithRole(Role role) {
        return jwtService.issue(new User(
                UUID.randomUUID(), "who@example.com", "누군가", "hash", NOW, role)).value();
    }

    @Test
    @DisplayName("익명 요청은 401 — 편집 시도는 인증부터 막힌다")
    void anonymousIsRejected() throws Exception {
        mvc.perform(put("/api/v1/admin/seasons/autumn_warm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(updateSeasonCuration, never()).execute(any(), any());
    }

    @Test
    @DisplayName("일반 사용자는 403 — 로그인했어도 관리자가 아니면 거절")
    void plainUserIsForbidden() throws Exception {
        mvc.perform(put("/api/v1/admin/seasons/autumn_warm")
                        .header("Authorization", "Bearer " + tokenWithRole(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(updateSeasonCuration, never()).execute(any(), any());
    }

    private static SeasonProfile autumnProfile() {
        return new SeasonProfile(
                Season.AUTUMN_WARM, "가을 웜", "Autumn Warm", "🍂",
                List.of("깊은", "따뜻한"),
                "황금빛이 도는 깊은 피부톤입니다.",
                List.of(
                        SeasonProfile.PaletteColor.of("머스타드", "#D4A017"),
                        SeasonProfile.PaletteColor.of("테라코타", "#C56A3E"),
                        SeasonProfile.PaletteColor.of("올리브", "#6B7A3A"),
                        SeasonProfile.PaletteColor.of("카멜", "#B5813F"),
                        SeasonProfile.PaletteColor.of("브릭", "#9C4A2F"),
                        SeasonProfile.PaletteColor.of("살구", "#F5B183")),
                List.of(
                        SeasonProfile.PaletteColor.of("형광 핑크", "#FF69B4"),
                        SeasonProfile.PaletteColor.of("퓨어 화이트", "#FFFFFF"),
                        SeasonProfile.PaletteColor.of("실버", "#C0C0C0")),
                List.of("금색 액세서리가 어울립니다."));
    }

    @Test
    @DisplayName("관리자는 편집할 수 있고, 갱신된 큐레이션을 돌려받는다")
    void adminCanUpdateCuration() throws Exception {
        given(updateSeasonCuration.execute(eq(Season.AUTUMN_WARM), any()))
                .willReturn(autumnProfile());

        mvc.perform(put("/api/v1/admin/seasons/autumn_warm")
                        .header("Authorization", "Bearer " + tokenWithRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("autumn_warm"))
                .andExpect(jsonPath("$.bestColors[0].name").value("머스타드"));
    }

    @Test
    @DisplayName("잘못된 hex는 400 — 형식 검증이 도메인보다 먼저 거절한다")
    void malformedHexIsRejected() throws Exception {
        String badBody = VALID_BODY.replace("#D4A017", "not-a-hex");

        mvc.perform(put("/api/v1/admin/seasons/autumn_warm")
                        .header("Authorization", "Bearer " + tokenWithRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(updateSeasonCuration, never()).execute(any(), any());
    }

    @Test
    @DisplayName("역할 클레임이 없는 옛 토큰은 USER로 취급되어 403")
    void legacyTokenWithoutRoleIsTreatedAsUser() throws Exception {
        // 역할 도입 전 발급된 토큰을 흉내 낸다 — role 클레임 없이 직접 서명.
        String legacy = io.jsonwebtoken.Jwts.builder()
                .issuer("personal-color-ai")
                .subject(UUID.randomUUID().toString())
                .issuedAt(java.util.Date.from(NOW))
                .expiration(java.util.Date.from(NOW.plusSeconds(3600)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mvc.perform(put("/api/v1/admin/seasons/autumn_warm")
                        .header("Authorization", "Bearer " + legacy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("키워드가 비면 400")
    void emptyKeywordsAreRejected() throws Exception {
        String badBody = VALID_BODY.replace("[\"깊은\", \"따뜻한\"]", "[]");

        mvc.perform(put("/api/v1/admin/seasons/autumn_warm")
                        .header("Authorization", "Bearer " + tokenWithRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("모르는 계절 코드는 400")
    void unknownSeasonCodeIsRejected() throws Exception {
        mvc.perform(put("/api/v1/admin/seasons/mystery_season")
                        .header("Authorization", "Bearer " + tokenWithRole(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }
}
