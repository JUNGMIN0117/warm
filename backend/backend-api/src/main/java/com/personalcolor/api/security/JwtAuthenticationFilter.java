package com.personalcolor.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization 헤더의 JWT를 읽어 인증 컨텍스트를 채운다.
 *
 * <p><b>토큰이 없거나 잘못돼도 여기서 거절하지 않는다.</b> 인증 없이
 * 통과시키고 판단은 인가 규칙에 맡긴다. 익명 분석을 허용하기로 했으므로
 * (ADR-005 이후의 제품 결정) 토큰 없는 요청도 정상 흐름이고, 필터가
 * 401을 던지면 그 흐름이 막힌다.
 *
 * <p>결과적으로 이 필터의 역할은 "인증을 강제하는 것"이 아니라 "있으면
 * 알아본다"에 가깝다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        bearerToken(request)
                .flatMap(jwtService::extractUserId)
                .ifPresent(userId -> {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, List.of());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });

        chain.doFilter(request, response);
    }

    private static java.util.Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return java.util.Optional.empty();
        }
        String token = header.substring(PREFIX.length()).strip();
        return token.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(token);
    }
}
