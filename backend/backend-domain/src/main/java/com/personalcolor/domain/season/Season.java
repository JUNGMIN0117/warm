package com.personalcolor.domain.season;

/**
 * 4계절 퍼스널 컬러 타입.
 *
 * <p>코드 문자열이 ml-service 응답의 {@code season} 값, 그리고 DB의
 * 자연키와 동일해야 한다. 세 곳이 어긋나면 런타임에 "팔레트를 찾을 수 없음"이
 * 되므로 {@link #fromCode(String)}가 미지의 값을 조용히 통과시키지 않는다.
 *
 * <p>ml-service의 {@code app/domain/seasons.py}와 같은 개념이지만 이식이
 * 아니라 계약의 재선언이다. 판정은 Python이 하고(ADR-005) Java는 그 결과를
 * 해석·저장한다.
 */
public enum Season {
    SPRING_WARM("spring_warm", Undertone.WARM),
    SUMMER_COOL("summer_cool", Undertone.COOL),
    AUTUMN_WARM("autumn_warm", Undertone.WARM),
    WINTER_COOL("winter_cool", Undertone.COOL);

    private final String code;
    private final Undertone undertone;

    Season(String code, Undertone undertone) {
        this.code = code;
        this.undertone = undertone;
    }

    public String code() {
        return code;
    }

    public Undertone undertone() {
        return undertone;
    }

    /**
     * ml-service가 보낸 코드 문자열을 enum으로 바꾼다.
     *
     * @throws IllegalArgumentException 모르는 코드인 경우. 조용히 null을
     *     돌려주면 계약 불일치가 한참 뒤 NPE로 나타나므로 즉시 실패시킨다.
     */
    public static Season fromCode(String code) {
        for (Season season : values()) {
            if (season.code.equals(code)) {
                return season;
            }
        }
        throw new IllegalArgumentException(
                "알 수 없는 계절 코드입니다: " + code + " — ml-service와 계약이 어긋났습니다.");
    }
}
