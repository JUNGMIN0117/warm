package com.personalcolor.infrastructure.persistence;

import com.personalcolor.domain.analysis.RgbColor;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.SeasonProfile;
import com.personalcolor.domain.season.SeasonProfileMissingException;
import com.personalcolor.domain.season.port.SeasonProfileRepository;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public JpaSeasonProfileRepository(
            SeasonProfileJpaRepository jpa, JdbcTemplate jdbc, EntityManager entityManager) {
        this.jpa = jpa;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
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

    /**
     * 큐레이션 교체 저장.
     *
     * <p>JPA 캐스케이드 대신 <b>시드(V3)와 같은 형태의 명시적 SQL</b>로
     * 자식 행을 지우고 다시 넣는다. 이유가 둘이다. 첫째, 자식 엔티티가
     * 복합키(@IdClass)와 부모 측 @JoinColumn을 함께 쓰는 조회 전용 매핑이라,
     * 쓰기까지 지원하도록 바꾸면 insertable/updatable 미묘함이 따라온다 —
     * 갱신 한 경로 때문에 잘 동작하는 조회 매핑을 흔들 이유가 없다. 둘째,
     * "통째로 교체"라는 의도가 DELETE+INSERT로 그대로 드러난다.
     *
     * <p>영속성 컨텍스트가 이 계절의 엔티티를 이미 들고 있으면 JDBC로 바꾼
     * 내용과 어긋나므로, 쓰기 전에 clear로 1차 캐시를 비운다.
     */
    @Override
    @Transactional
    public SeasonProfile save(SeasonProfile profile) {
        String code = profile.season().code();
        entityManager.clear();

        int updated = jdbc.update(
                "UPDATE season_profiles SET description = ? WHERE code = ?",
                profile.description(), code);
        if (updated == 0) {
            throw new SeasonProfileMissingException(profile.season());
        }

        jdbc.update("DELETE FROM season_keywords WHERE season_code = ?", code);
        List<String> keywords = profile.keywords();
        for (int i = 0; i < keywords.size(); i++) {
            jdbc.update(
                    "INSERT INTO season_keywords (season_code, display_order, keyword) "
                            + "VALUES (?, ?, ?)",
                    code, i, keywords.get(i));
        }

        jdbc.update("DELETE FROM palette_colors WHERE season_code = ?", code);
        insertColors(code, "BEST", profile.bestColors());
        insertColors(code, "WORST", profile.worstColors());

        jdbc.update("DELETE FROM styling_tips WHERE season_code = ?", code);
        List<String> tips = profile.stylingTips();
        for (int i = 0; i < tips.size(); i++) {
            jdbc.update(
                    "INSERT INTO styling_tips (season_code, display_order, tip) VALUES (?, ?, ?)",
                    code, i, tips.get(i));
        }

        return findBySeason(profile.season());
    }

    private void insertColors(String code, String kind, List<SeasonProfile.PaletteColor> colors) {
        for (int i = 0; i < colors.size(); i++) {
            SeasonProfile.PaletteColor color = colors.get(i);
            jdbc.update(
                    "INSERT INTO palette_colors (season_code, palette_kind, display_order, "
                            + "name, hex) VALUES (?, ?, ?, ?, ?)",
                    code, kind, i, color.name(), color.color().toHex());
        }
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
