package com.personalcolor.infrastructure.persistence;

import com.personalcolor.domain.analysis.RgbColor;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.SeasonProfile;
import com.personalcolor.domain.season.SeasonProfileMissingException;
import com.personalcolor.domain.season.port.SeasonProfileRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 계절 프로필 조회의 JPA 구현.
 *
 * <p>{@code @Transactional(readOnly = true)}가 필수다. 컬렉션이 LAZY라
 * 트랜잭션 밖에서 접근하면 {@code LazyInitializationException}이 난다.
 * 이 메서드 안에서 도메인 record로 완전히 변환하므로, 밖으로 나가는
 * 값에는 지연 로딩 프록시가 없다.
 *
 * <p>캐시는 두지 않았다. 카탈로그는 마이그레이션으로만 바뀌므로 캐시하기
 * 좋은 대상이지만, 분석 한 건의 비용이 ml-service 추론(수백 ms)에 지배되어
 * 몇 개의 작은 쿼리는 묻힌다. 필요가 측정되면 그때 넣는다.
 */
@Repository
public class JpaSeasonProfileRepository implements SeasonProfileRepository {

    private final SeasonProfileJpaRepository jpa;

    public JpaSeasonProfileRepository(SeasonProfileJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public SeasonProfile findBySeason(Season season) {
        return jpa.findById(season.code())
                .map(JpaSeasonProfileRepository::toDomain)
                .orElseThrow(() -> new SeasonProfileMissingException(season));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeasonProfile> findAll() {
        return jpa.findAll().stream().map(JpaSeasonProfileRepository::toDomain).toList();
    }

    private static SeasonProfile toDomain(SeasonProfileEntity entity) {
        return new SeasonProfile(
                Season.fromCode(entity.getCode()),
                entity.getLabelKo(),
                entity.getLabelEn(),
                entity.getEmoji(),
                entity.getKeywords().stream().map(SeasonKeywordEntity::getKeyword).toList(),
                entity.getDescription(),
                colorsOfKind(entity, PaletteColorEntity.Kind.BEST),
                colorsOfKind(entity, PaletteColorEntity.Kind.WORST),
                entity.getStylingTips().stream().map(StylingTipEntity::getTip).toList());
    }

    private static List<SeasonProfile.PaletteColor> colorsOfKind(
            SeasonProfileEntity entity, PaletteColorEntity.Kind kind) {
        return entity.getColors().stream()
                .filter(color -> color.kind() == kind)
                .map(color -> new SeasonProfile.PaletteColor(
                        color.getName(), RgbColor.fromHex(color.getHex())))
                .toList();
    }
}
