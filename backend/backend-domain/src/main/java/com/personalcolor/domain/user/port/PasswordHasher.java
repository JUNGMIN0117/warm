package com.personalcolor.domain.user.port;

/**
 * 비밀번호 해싱 — 바깥으로 나가는 포트.
 *
 * <p>Spring Security의 {@code PasswordEncoder}를 도메인이 직접 쓰지 않는
 * 이유는 계층 규칙 그대로다. 도메인은 "비밀번호를 해싱하고 대조한다"는
 * 개념만 알면 되고, 그것이 BCrypt인지 Argon2인지는 인프라의 결정이다.
 *
 * <p>실질적 이득도 있다. 회원가입 유스케이스를 테스트할 때 BCrypt를
 * 실제로 돌리면 의도적으로 느린 알고리즘이라 테스트가 눈에 띄게 느려진다.
 * 포트를 두면 테스트가 즉시 끝나는 가짜 구현을 끼울 수 있다.
 */
public interface PasswordHasher {

    /** 평문을 해싱한다. 같은 입력이라도 매번 다른 결과가 나온다(솔트). */
    String hash(String rawPassword);

    /** 평문이 해시와 일치하는지 확인한다. */
    boolean matches(String rawPassword, String hashedPassword);
}
