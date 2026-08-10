package com.personalcolor.domain.user;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 이메일 정규화와 검증.
 *
 * <p>정규화가 별도로 존재하는 이유는 <b>중복 가입</b> 때문이다.
 * {@code Foo@Example.com}과 {@code foo@example.com}은 같은 사서함인데,
 * 정규화 없이 저장하면 서로 다른 계정이 만들어진다. DB에도
 * {@code LOWER(email)} 유니크 인덱스를 걸어 두 겹으로 막는다.
 *
 * <p>검증은 느슨하게 한다. RFC 5322를 완전히 따르는 정규식은 읽을 수
 * 없을 만큼 길고, 그렇게 해도 "실제로 받는 주소인가"는 알 수 없다.
 * 명백한 오타만 걸러내고 진짜 확인은 메일 발송에 맡기는 것이 실용적이다.
 */
public final class Email {

    /** 앞뒤에 @가 하나 있고 도메인에 점이 있는 정도만 본다. */
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final int MAX_LENGTH = 254;

    private Email() {}

    /**
     * 소문자로 정규화하고 형식을 검증한다.
     *
     * @throws IllegalArgumentException 형식이 아니거나 너무 긴 경우
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("이메일이 비어 있습니다.");
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT);

        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("이메일이 너무 깁니다: " + normalized.length() + "자");
        }
        if (!SHAPE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("이메일 형식이 아닙니다: " + raw);
        }
        return normalized;
    }
}
