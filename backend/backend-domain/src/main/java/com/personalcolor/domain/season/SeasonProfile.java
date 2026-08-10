package com.personalcolor.domain.season;

import com.personalcolor.domain.analysis.RgbColor;

import java.util.List;

/**
 * 계절별 큐레이션 — 라벨, 추천 팔레트, 스타일링 팁.
 *
 * <p>ADR-005에서 이 데이터의 소유권을 Spring(DB)으로 가져왔다. 측정 결과가
 * 아니라 <b>사람이 정한 것</b>이기 때문이다. "봄 웜에 코랄 대신 살구를
 * 넣자"는 큐레이션 판단인데, 그것이 추론 서비스 소스에 있으면 색 하나
 * 바꾸는 데 모델 로딩이 무거운 서버를 재배포해야 한다.
 *
 * <p>초기 데이터는 ml-service의 {@code scripts/export_palettes.py}가
 * 내보낸 JSON에서 Flyway 시드로 들어온다.
 *
 * @param season 어느 계절인가. ml-service 응답의 코드와 일치해야 조인이 성립한다
 * @param labelKo 한국어 라벨 (예: 봄 웜)
 * @param labelEn 영어 라벨
 * @param emoji UI 표시용
 * @param keywords 성격을 요약하는 낱말들
 * @param description 한 문단 설명
 * @param bestColors 추천 색
 * @param worstColors 기피 색
 * @param stylingTips 스타일링 팁
 */
public record SeasonProfile(
        Season season,
        String labelKo,
        String labelEn,
        String emoji,
        List<String> keywords,
        String description,
        List<PaletteColor> bestColors,
        List<PaletteColor> worstColors,
        List<String> stylingTips) {

    /** 팔레트가 이보다 적으면 UI가 허전해 큐레이션이 덜 된 것으로 본다. */
    private static final int MIN_BEST_COLORS = 6;

    public SeasonProfile {
        if (season == null) {
            throw new IllegalArgumentException("season이 없습니다.");
        }
        requireText(labelKo, "labelKo");
        requireText(labelEn, "labelEn");

        keywords = List.copyOf(keywords == null ? List.of() : keywords);
        bestColors = List.copyOf(bestColors == null ? List.of() : bestColors);
        worstColors = List.copyOf(worstColors == null ? List.of() : worstColors);
        stylingTips = List.copyOf(stylingTips == null ? List.of() : stylingTips);

        if (bestColors.size() < MIN_BEST_COLORS) {
            throw new IllegalArgumentException(
                    season.code() + " 추천 색이 " + bestColors.size()
                            + "개뿐입니다(최소 " + MIN_BEST_COLORS + "개) — 시드가 덜 들어왔습니다.");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "가 비어 있습니다.");
        }
    }

    /** 팔레트 한 칸. 이름 없이 HEX만 있으면 UI가 색 이름을 못 보여준다. */
    public record PaletteColor(String name, RgbColor color) {
        public PaletteColor {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("색 이름이 비어 있습니다.");
            }
            if (color == null) {
                throw new IllegalArgumentException(name + "의 색값이 없습니다.");
            }
        }

        public static PaletteColor of(String name, String hex) {
            return new PaletteColor(name, RgbColor.fromHex(hex));
        }
    }
}
