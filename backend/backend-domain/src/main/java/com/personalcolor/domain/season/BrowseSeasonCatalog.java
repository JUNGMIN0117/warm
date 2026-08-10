package com.personalcolor.domain.season;

import com.personalcolor.domain.season.port.SeasonProfileRepository;

import java.util.List;

/**
 * 계절 카탈로그 조회 유스케이스.
 *
 * <p>리포지토리를 그대로 감싸기만 하는 것처럼 보이지만, 이 한 겹이
 * 컨트롤러가 포트를 직접 잡는 것을 막는다. 그 규칙을 ArchUnit이 검사하고,
 * 실제로 이 클래스는 규칙 위반이 감지된 뒤에 생겼다 — 컨트롤러 두 개가
 * {@code SeasonProfileRepository}를 주입받고 있었다.
 *
 * <p>규칙을 느슨하게 푸는 선택지도 있었지만 그러지 않았다. 지금은 읽기
 * 전용이라 무해해 보여도, 예외를 한 번 열면 다음 컨트롤러가 쓰기 리포지토리를
 * 직접 잡을 때 막을 근거가 사라진다.
 */
public final class BrowseSeasonCatalog {

    private final SeasonProfileRepository profiles;

    public BrowseSeasonCatalog(SeasonProfileRepository profiles) {
        this.profiles = profiles;
    }

    public List<SeasonProfile> all() {
        return profiles.findAll();
    }

    /**
     * 코드로 하나 가져온다.
     *
     * @throws IllegalArgumentException 모르는 계절 코드
     * @throws SeasonProfileMissingException 코드는 맞지만 시드가 없는 경우
     */
    public SeasonProfile byCode(String code) {
        return profiles.findBySeason(Season.fromCode(code));
    }

    public SeasonProfile of(Season season) {
        return profiles.findBySeason(season);
    }
}
