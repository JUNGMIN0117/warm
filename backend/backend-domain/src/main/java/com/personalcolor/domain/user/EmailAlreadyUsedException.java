package com.personalcolor.domain.user;

/**
 * 이미 가입된 이메일.
 *
 * <p>가입 화면에서 이걸 그대로 알려주면 "이 이메일이 가입되어 있다"는
 * 사실이 노출된다. 엄밀히는 계정 열거(enumeration)이지만, 가입 폼에서
 * 이를 숨기면 사용자가 왜 실패하는지 알 수 없어 사용성이 크게 나빠진다.
 * 로그인 쪽({@link InvalidCredentialsException})은 숨기고 가입 쪽은
 * 알려주는 절충을 택했다.
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("이미 가입된 이메일입니다: " + email);
    }
}
