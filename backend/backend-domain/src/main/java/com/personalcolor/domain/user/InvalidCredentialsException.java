package com.personalcolor.domain.user;

/**
 * 로그인 실패.
 *
 * <p>메시지에 실패 원인을 담지 않는다. "그런 계정 없음"과 "비밀번호 틀림"을
 * 구분해 알려주면 공격자가 이메일 목록을 대조해 가입 여부를 수집할 수 있다.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
