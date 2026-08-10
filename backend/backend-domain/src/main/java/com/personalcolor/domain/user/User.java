package com.personalcolor.domain.user;

import java.time.Instant;
import java.util.UUID;

/**
 * 가입한 사용자.
 *
 * <p>이 서비스에서 계정의 유일한 용도는 <b>분석 이력을 소유하는 것</b>이다.
 * 분석 자체는 로그인 없이 되므로(익명 허용), 계정은 "결과를 나중에 다시
 * 보고 싶다"는 사람만 만든다. 그래서 수집하는 정보가 최소한이다 —
 * 이메일, 표시 이름, 비밀번호 해시가 전부다.
 *
 * <p>{@code passwordHash}가 record 필드로 노출되어 있는 것은 의도적이다.
 * 숨기려고 별도 타입을 만들면 저장·조회 경로에서 계속 꺼내야 해서 오히려
 * 다루는 곳이 늘어난다. 대신 {@link #toString()}을 재정의해 로그에 해시가
 * 찍히는 사고를 막는다.
 */
public record User(
        UUID id,
        String email,
        String displayName,
        String passwordHash,
        Instant createdAt) {

    public User {
        if (id == null) {
            throw new IllegalArgumentException("id가 없습니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt이 없습니다.");
        }
        email = Email.normalize(email);
        requireText(displayName, "displayName");
        requireText(passwordHash, "passwordHash");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "가 비어 있습니다.");
        }
    }

    /**
     * 비밀번호 해시를 문자열 표현에서 제외한다.
     *
     * <p>record의 기본 toString은 모든 필드를 찍는다. 예외 메시지나 디버그
     * 로그에 User가 들어가는 순간 해시가 로그 수집기까지 흘러간다.
     */
    @Override
    public String toString() {
        return "User[id=" + id + ", email=" + email + ", displayName=" + displayName + "]";
    }
}
