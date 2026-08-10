package com.personalcolor.domain.user;

import com.personalcolor.domain.user.port.PasswordHasher;
import com.personalcolor.domain.user.port.UserRepository;

import java.util.Optional;

/** 로그인 유스케이스. */
public final class AuthenticateUser {

    /**
     * 존재하지 않는 계정에 대조할 더미 해시.
     *
     * <p>계정이 없을 때 즉시 반환하면 응답 시간이 눈에 띄게 짧아져,
     * 공격자가 <b>어떤 이메일이 가입되어 있는지</b> 타이밍만으로 알아낼 수
     * 있다. 실제 bcrypt 대조 한 번을 똑같이 수행해 시간 차이를 없앤다.
     *
     * <p>값은 bcrypt 형식이기만 하면 되고 어떤 비밀번호와도 일치하지 않는다.
     */
    private static final String DUMMY_HASH =
            "$2a$10$ZZZZZZZZZZZZZZZZZZZZZeZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ";

    private final UserRepository users;
    private final PasswordHasher hasher;

    public AuthenticateUser(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    /**
     * 이메일과 비밀번호를 대조한다.
     *
     * @throws InvalidCredentialsException 이메일이 없거나 비밀번호가 틀린 경우.
     *     둘을 구분하지 않는 것이 의도다 — "그런 계정 없음"과 "비밀번호 틀림"을
     *     나누면 가입 여부를 알려주는 셈이 된다
     */
    public User execute(String email, String rawPassword) {
        String normalizedEmail;
        try {
            normalizedEmail = Email.normalize(email);
        } catch (IllegalArgumentException e) {
            // 형식이 틀려도 "형식 오류"라고 알려주지 않는다. 로그인 실패는
            // 한 가지 메시지로 통일하는 편이 정보 노출이 적다.
            throw new InvalidCredentialsException();
        }

        Optional<User> found = users.findByEmail(normalizedEmail);
        String hashToCheck = found.map(User::passwordHash).orElse(DUMMY_HASH);
        boolean matched = hasher.matches(
                rawPassword == null ? "" : rawPassword, hashToCheck);

        if (found.isEmpty() || !matched) {
            throw new InvalidCredentialsException();
        }
        return found.get();
    }
}
