package com.personalcolor.domain.season;

import com.personalcolor.domain.Fixtures;
import com.personalcolor.domain.season.SeasonProfile.PaletteColor;
import com.personalcolor.domain.season.port.SeasonProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 큐레이션 편집 규칙 테스트.
 *
 * <p>핵심 계약: 라벨은 편집돼도 유지되고, 큐레이션 규칙(개수·빈 값)을
 * 어기는 교체는 저장 경로에 도달하지 못한다.
 */
@DisplayName("UpdateSeasonCuration")
class UpdateSeasonCurationTest {

    /** 저장까지 흉내 내는 인메모리 저장소. */
    private static final class InMemoryProfiles implements SeasonProfileRepository {
        private final Map<Season, SeasonProfile> store = new HashMap<>();

        InMemoryProfiles() {
            for (Season season : Season.values()) {
                store.put(season, Fixtures.profileFor(season));
            }
        }

        @Override
        public SeasonProfile findBySeason(Season season) {
            return store.get(season);
        }

        @Override
        public List<SeasonProfile> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public SeasonProfile save(SeasonProfile profile) {
            store.put(profile.season(), profile);
            return profile;
        }
    }

    private final InMemoryProfiles profiles = new InMemoryProfiles();
    private final UpdateSeasonCuration useCase = new UpdateSeasonCuration(profiles);

    private static UpdateSeasonCuration.Command validCommand() {
        return new UpdateSeasonCuration.Command(
                List.of("산뜻한"),
                "새 설명입니다.",
                List.of(
                        PaletteColor.of("살구", "#F5B183"),
                        PaletteColor.of("코랄", "#FF7F50"),
                        PaletteColor.of("골드", "#D4AF37"),
                        PaletteColor.of("아이보리", "#FFFFF0"),
                        PaletteColor.of("카멜", "#B5813F"),
                        PaletteColor.of("올리브", "#6B7A3A")),
                List.of(
                        PaletteColor.of("실버", "#C0C0C0"),
                        PaletteColor.of("차콜", "#36454F"),
                        PaletteColor.of("퓨어 화이트", "#FFFFFF")),
                List.of("새 팁입니다."));
    }

    @Test
    @DisplayName("큐레이션은 교체되고 라벨·이모지는 유지된다")
    void replacesCurationButKeepsIdentity() {
        SeasonProfile before = profiles.findBySeason(Season.SPRING_WARM);

        SeasonProfile updated = useCase.execute(Season.SPRING_WARM, validCommand());

        assertThat(updated.description()).isEqualTo("새 설명입니다.");
        assertThat(updated.bestColors()).hasSize(6);
        assertThat(updated.labelKo()).isEqualTo(before.labelKo());
        assertThat(updated.emoji()).isEqualTo(before.emoji());
        assertThat(profiles.findBySeason(Season.SPRING_WARM)).isEqualTo(updated);
    }

    @Test
    @DisplayName("추천 색이 6개 미만이면 거절 — SeasonProfile 불변식이 지킨다")
    void rejectsTooFewBestColors() {
        var command = new UpdateSeasonCuration.Command(
                List.of("산뜻한"), "설명",
                List.of(PaletteColor.of("살구", "#F5B183")),
                validCommand().worstColors(),
                List.of("팁"));

        assertThatThrownBy(() -> useCase.execute(Season.SPRING_WARM, command))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(profiles.findBySeason(Season.SPRING_WARM).description())
                .as("실패한 편집은 저장되지 않아야 한다")
                .isNotEqualTo("설명");
    }

    @Test
    @DisplayName("기피 색이 3개 미만이면 거절")
    void rejectsTooFewWorstColors() {
        var command = new UpdateSeasonCuration.Command(
                validCommand().keywords(), validCommand().description(),
                validCommand().bestColors(),
                List.of(PaletteColor.of("실버", "#C0C0C0")),
                validCommand().stylingTips());

        assertThatThrownBy(() -> useCase.execute(Season.SPRING_WARM, command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 설명·빈 팁은 거절")
    void rejectsBlankFields() {
        var blankDescription = new UpdateSeasonCuration.Command(
                validCommand().keywords(), "  ",
                validCommand().bestColors(), validCommand().worstColors(),
                validCommand().stylingTips());
        var blankTip = new UpdateSeasonCuration.Command(
                validCommand().keywords(), validCommand().description(),
                validCommand().bestColors(), validCommand().worstColors(),
                List.of(" "));

        assertThatThrownBy(() -> useCase.execute(Season.SPRING_WARM, blankDescription))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(Season.SPRING_WARM, blankTip))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
