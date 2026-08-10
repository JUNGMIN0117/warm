package com.personalcolor.domain.analysis;

import com.personalcolor.domain.analysis.port.AnalysisRepository;
import com.personalcolor.domain.season.BrowseSeasonCatalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 이력 조회 유스케이스.
 *
 * <p>단순한 위임처럼 보이지만 세 가지를 강제한다 — 조회 상한, 소유권 확인,
 * 그리고 저장된 측정값에 계절 큐레이션을 다시 붙이는 일. 컨트롤러가
 * 리포지토리를 직접 부르면 이 규칙들이 엔드포인트마다 흩어진다.
 */
public final class ViewAnalysisHistory {

    /** 한 번에 돌려줄 수 있는 최대 건수. 페이지네이션 전까지의 안전장치다. */
    public static final int MAX_LIMIT = 50;

    private static final int DEFAULT_LIMIT = 20;

    private final AnalysisRepository repository;
    private final BrowseSeasonCatalog catalog;

    public ViewAnalysisHistory(AnalysisRepository repository, BrowseSeasonCatalog catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    /**
     * 내 이력을 최신순으로 가져온다.
     *
     * <p>항목마다 계절 프로필을 조회하므로 N+1이다. 카탈로그가 4행뿐이고
     * 상한이 50건이라 현재는 문제되지 않지만, 어댑터에 캐시를 넣을 때
     * 가장 먼저 볼 지점이다.
     *
     * @param limit 요청 건수. 0 이하면 기본값, {@link #MAX_LIMIT} 초과면 잘린다
     */
    public List<AnalysisView> execute(UUID userId, int limit) {
        int effective = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository.findByUserId(userId, effective).stream()
                .map(this::withProfile)
                .toList();
    }

    /**
     * 단건 조회.
     *
     * <p>다른 사람의 분석을 id만 알면 볼 수 있으면 안 되므로 소유권을
     * 확인한다. 남의 것을 요청했을 때 403이 아니라 빈 결과를 주는 것이
     * 의도다 — 403은 "그 id는 존재한다"를 알려준다.
     */
    public Optional<AnalysisView> findOwned(UUID analysisId, UUID userId) {
        return repository.findById(analysisId)
                .filter(record -> record.userId().filter(userId::equals).isPresent())
                .map(this::withProfile);
    }

    private AnalysisView withProfile(AnalysisRecord record) {
        // 이력에는 단계 이미지가 없다 — 저장하지 않기 때문이다.
        return new AnalysisView(
                record, catalog.of(record.measurement().season()), Optional.empty());
    }
}
