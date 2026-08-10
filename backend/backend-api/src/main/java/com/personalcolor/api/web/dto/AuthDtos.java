package com.personalcolor.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 인증 관련 요청·응답.
 *
 * <p>Bean Validation 애너테이션이 도메인 검증과 겹치는 것은 의도적이다.
 * 여기서는 <b>형식</b>을 걸러 400을 빠르게 돌려주고, 도메인은 <b>규칙</b>을
 * 지킨다. API 계층 검증이 없으면 모든 잘못된 입력이 도메인 예외로 올라와
 * 오류 코드가 뭉개진다.
 */
public final class AuthDtos {

    private AuthDtos() {}

    /** 회원가입 요청. */
    public record RegisterRequest(
            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "이메일 형식이 아닙니다.")
            String email,

            @NotBlank(message = "표시 이름을 입력해 주세요.")
            @Size(max = 50, message = "표시 이름은 50자 이하여야 합니다.")
            String displayName,

            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(min = 10, message = "비밀번호는 10자 이상이어야 합니다.")
            String password) {}

    /** 로그인 요청. */
    public record LoginRequest(
            @NotBlank(message = "이메일을 입력해 주세요.")
            String email,

            @NotBlank(message = "비밀번호를 입력해 주세요.")
            String password) {}

    /**
     * 인증 성공 응답.
     *
     * <p>{@code expiresAt}을 함께 주는 이유: 클라이언트가 토큰을 디코드하지
     * 않고도 재로그인 시점을 알 수 있다. JWT payload를 프론트가 파싱하게
     * 만들면 토큰 구조가 사실상 공개 계약이 된다.
     */
    public record AuthResponse(
            String accessToken,
            Instant expiresAt,
            String userId,
            String displayName) {}
}
