package com.personalcolor.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더 배선.
 *
 * <p>API 계층이 아니라 여기 두는 이유: 해싱은 저장 형식에 관한 결정이고,
 * 그 형식을 아는 것은 영속화를 책임지는 인프라다. API는 "로그인이 된다"만
 * 알면 된다.
 */
@Configuration
public class PasswordEncoderConfiguration {

    /**
     * BCrypt strength 12.
     *
     * <p>Spring 기본값은 10이다. 12로 올린 것은 해시 한 번에 약 4배의 시간을
     * 쓰겠다는 뜻이고(현대 CPU에서 대략 200~300ms), 그만큼 오프라인 대입
     * 공격의 비용도 올라간다. 로그인은 드문 연산이라 이 지연이 사용자
     * 경험을 해치지 않는다.
     *
     * <p>이 값을 바꾸면 기존 해시는 그대로 유효하다 — strength가 해시
     * 문자열 안에 기록되므로 대조 시 저장된 값을 따른다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
