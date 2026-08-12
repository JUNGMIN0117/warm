package com.personalcolor.domain.season;

import com.personalcolor.domain.season.SeasonProfile.PaletteColor;
import com.personalcolor.domain.season.port.SeasonProfileRepository;

import java.util.List;

/**
 * 계절 큐레이션 편집 유스케이스 — ADR-005의 약속을 실제 기능으로.
 *
 * <p>"봄 웜에 코랄 대신 살구를 넣자"는 판단이 추론 서버 재배포 없이
 * 반영되도록 큐레이션의 소유권을 DB로 가져왔고(ADR-005), 이 유스케이스가
 * 그 갱신 경로다. 관리자만 부를 수 있다는 것은 API 계층의 인가 규칙이
 * 보장한다 — 도메인은 "무엇이 유효한 큐레이션인가"만 안다.
 *
 * <p>라벨(labelKo/labelEn/emoji)은 편집 대상이 아니다. 그것은 계절의
 * 표기 정체성이라 팔레트처럼 취향으로 바꾸는 값이 아니고, 프론트·문서가
 * 폭넓게 참조하므로 바뀌면 큐레이션이 아니라 리브랜딩이다.
 */
public final class UpdateSeasonCuration {

    /** 기피 색 최소 개수. 추천 색 최소(6)는 SeasonProfile 생성자가 지킨다. */
    public static final int MIN_WORST_COLORS = 3;

    private final SeasonProfileRepository profiles;

    public UpdateSeasonCuration(SeasonProfileRepository profiles) {
        this.profiles = profiles;
    }

    /** 편집 가능한 필드 전부. 부분 수정이 아니라 통째로 교체한다 — 순서가 곧 데이터다. */
    public record Command(
            List<String> keywords,
            String description,
            List<PaletteColor> bestColors,
            List<PaletteColor> worstColors,
            List<String> stylingTips) {}

    /**
     * 큐레이션을 교체한다.
     *
     * @throws IllegalArgumentException 큐레이션 규칙 위반 (개수·빈 값)
     * @throws SeasonProfileMissingException 계절 코드가 어긋난 경우
     */
    public SeasonProfile execute(Season season, Command command) {
        validate(command);
        SeasonProfile existing = profiles.findBySeason(season);

        // SeasonProfile 생성자가 추천 색 최소 개수 등 자체 불변식을 다시
        // 검증한다 — 여기서 만들어지지 않으면 저장 경로에 도달하지 못한다.
        SeasonProfile updated = new SeasonProfile(
                season,
                existing.labelKo(),
                existing.labelEn(),
                existing.emoji(),
                command.keywords(),
                command.description(),
                command.bestColors(),
                command.worstColors(),
                command.stylingTips());

        return profiles.save(updated);
    }

    private static void validate(Command command) {
        if (command.description() == null || command.description().isBlank()) {
            throw new IllegalArgumentException("설명이 비어 있습니다.");
        }
        if (command.keywords() == null || command.keywords().isEmpty()
                || command.keywords().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("키워드는 비어 있지 않게 1개 이상이어야 합니다.");
        }
        if (command.stylingTips() == null || command.stylingTips().isEmpty()
                || command.stylingTips().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("스타일링 팁은 비어 있지 않게 1개 이상이어야 합니다.");
        }
        if (command.worstColors() == null || command.worstColors().size() < MIN_WORST_COLORS) {
            throw new IllegalArgumentException(
                    "기피 색은 최소 " + MIN_WORST_COLORS + "개여야 합니다.");
        }
    }
}
