package com.personalcolor.domain.season;

/**
 * 계절 프로필 시드가 없다.
 *
 * <p>사용자 입력 문제가 아니라 배포 문제다 — Flyway 시드가 빠졌거나
 * ml-service의 계절 코드와 DB의 코드가 어긋난 경우다. 조용히 빈 팔레트를
 * 돌려주면 "왜 색이 안 보이지"로 끝나고 원인을 찾기 어려우므로 즉시 실패한다.
 *
 * <p>API 계층에서는 5xx로 매핑된다.
 */
public class SeasonProfileMissingException extends RuntimeException {

    public SeasonProfileMissingException(Season season) {
        super("계절 프로필이 없습니다: " + season.code()
                + " — Flyway 시드가 들어왔는지, ml-service와 코드가 일치하는지 확인하세요.");
    }
}
