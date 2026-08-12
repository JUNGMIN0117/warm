package com.personalcolor.api.security;

import com.personalcolor.api.web.dto.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 보안 설정.
 *
 * <p>핵심 결정은 <b>분석을 익명으로 허용</b>하는 것이다. 첫 사용에 회원가입을
 * 요구하면 "사진 한 장 올려보고 싶은" 사람이 대부분 떠난다. 계정은 결과를
 * 나중에 다시 보고 싶은 사람만 만들면 되고, 그래서 인증이 필요한 것은
 * 이력 관련 엔드포인트뿐이다.
 *
 * <p>세션을 만들지 않는다({@code STATELESS}). JWT를 쓰는 이유가 서버가
 * 아무것도 기억하지 않는 것인데 세션이 생기면 그 이점이 사라진다.
 *
 * <p>CSRF를 끈 것은 쿠키를 쓰지 않기 때문이다. 토큰이 Authorization 헤더로
 * 오므로 브라우저가 자동으로 붙여주는 자격 증명이 없고, CSRF의 전제가
 * 성립하지 않는다. <b>쿠키 인증으로 바꾼다면 반드시 다시 켜야 한다.</b>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 익명 허용 — 서비스의 본체
                        .requestMatchers(HttpMethod.POST, "/api/v1/analyses").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/seasons/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // 관리자 전용 — 큐레이션 편집 (ADR-011)
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 나머지(이력 조회 등)는 인증 필요
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden));

        return http.build();
    }

    /**
     * 인증 실패 응답.
     *
     * <p>Spring Security 기본 동작은 빈 401이거나 HTML 로그인 폼인데,
     * 둘 다 API 클라이언트에게 쓸모없다. 나머지 오류와 같은
     * {@link ErrorResponse} 형태로 통일해 프론트가 한 가지 파서만 쓰게 한다.
     */
    private void writeUnauthorized(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.core.AuthenticationException exception)
            throws java.io.IOException {
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "로그인이 필요합니다.");
    }

    private void writeForbidden(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException exception)
            throws java.io.IOException {
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다.");
    }

    private void write(
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status, String code, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(new ErrorResponse(code, message, null)));
    }
}
