package com.personalcolor.domain.season;

/**
 * 언더톤 — 4계절 분류의 1차 축.
 *
 * <p>웜/쿨 2분류는 4계절을 병합한 것이라 항상 더 견고하다. 4계절 판정이
 * 애매해도 언더톤만큼은 자신 있게 말할 수 있는 경우가 많아 따로 보고한다.
 */
public enum Undertone {
    WARM("warm"),
    COOL("cool");

    private final String code;

    Undertone(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Undertone fromCode(String code) {
        for (Undertone undertone : values()) {
            if (undertone.code.equals(code)) {
                return undertone;
            }
        }
        throw new IllegalArgumentException("알 수 없는 언더톤 코드입니다: " + code);
    }
}
