package com.personalcolor.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * 계절 카탈로그 매핑.
 *
 * <p>컬렉션을 LAZY로 두고 어댑터의 트랜잭션 안에서 읽는다. EAGER로 하면
 * 여러 컬렉션을 동시에 조인 페치하다 {@code MultipleBagFetchException}이
 * 나기 쉽고, 필요 없는 조회에서도 전부 끌고 온다.
 *
 * <p>이 데이터는 마이그레이션으로만 바뀌므로 런타임 쓰기 경로가 없다.
 * 그래도 cascade를 열어둔 것은 테스트가 픽스처를 심을 수 있게 하기 위해서다.
 */
@Entity
@Table(name = "season_profiles")
public class SeasonProfileEntity {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 10)
    private String undertone;

    @Column(name = "label_ko", nullable = false, length = 30)
    private String labelKo;

    @Column(name = "label_en", nullable = false, length = 30)
    private String labelEn;

    @Column(nullable = false, length = 8)
    private String emoji;

    @Column(nullable = false)
    private String description;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "season_code")
    @OrderBy("displayOrder ASC")
    private List<SeasonKeywordEntity> keywords = new ArrayList<>();

    /**
     * 추천색과 기피색을 한 컬렉션에 담고 {@code paletteKind}로 구분한다.
     * 구조가 같고 항상 함께 조회되므로 나누면 쿼리만 늘어난다.
     */
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "season_code")
    @OrderBy("displayOrder ASC")
    private List<PaletteColorEntity> colors = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "season_code")
    @OrderBy("displayOrder ASC")
    private List<StylingTipEntity> stylingTips = new ArrayList<>();

    protected SeasonProfileEntity() {
        // JPA 전용
    }

    public String getCode() {
        return code;
    }

    public String getUndertone() {
        return undertone;
    }

    public String getLabelKo() {
        return labelKo;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getDescription() {
        return description;
    }

    public List<SeasonKeywordEntity> getKeywords() {
        return keywords;
    }

    public List<PaletteColorEntity> getColors() {
        return colors;
    }

    public List<StylingTipEntity> getStylingTips() {
        return stylingTips;
    }
}
