package com.personalcolor.domain.season.port;

import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.SeasonProfile;

import java.util.List;

/**
 * 계절 프로필 조회 — 바깥으로 나가는 포트.
 *
 * <p>구현체는 DB에서 읽는다. 프로필은 거의 바뀌지 않으므로 어댑터가
 * 캐시하기 좋은 대상이지만, 그 판단은 인프라의 몫이라 여기 드러나지 않는다.
 */
public interface SeasonProfileRepository {

    /**
     * 계절 프로필을 가져온다.
     *
     * @throws com.personalcolor.domain.season.SeasonProfileMissingException
     *     시드가 들어오지 않았거나 코드가 어긋난 경우
     */
    SeasonProfile findBySeason(Season season);

    /** 네 계절 전부. 팔레트 둘러보기 화면이 쓴다. */
    List<SeasonProfile> findAll();
}
