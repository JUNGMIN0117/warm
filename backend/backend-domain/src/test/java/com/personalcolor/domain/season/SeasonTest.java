package com.personalcolor.domain.season;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Season — ml-service·DB와의 코드 계약")
class SeasonTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "spring_warm, SPRING_WARM",
        "summer_cool, SUMMER_COOL",
        "autumn_warm, AUTUMN_WARM",
        "winter_cool, WINTER_COOL"
    })
    @DisplayName("ml-service가 보내는 코드 문자열을 해석한다")
    void parsesServiceCodes(String code, Season expected) {
        assertThat(Season.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("모르는 코드는 조용히 넘기지 않고 즉시 실패한다")
    void rejectsUnknownCode() {
        assertThatThrownBy(() -> Season.fromCode("autumn_cool"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("autumn_cool")
                .hasMessageContaining("계약");
    }

    @ParameterizedTest
    @EnumSource(Season.class)
    @DisplayName("모든 계절이 언더톤을 가진다")
    void everySeasonHasUndertone(Season season) {
        assertThat(season.undertone()).isNotNull();
        assertThat(season.code()).isNotBlank();
    }

    @Test
    @DisplayName("웜/쿨이 2:2로 갈린다")
    void undertonesAreBalanced() {
        assertThat(Season.values())
                .filteredOn(season -> season.undertone() == Undertone.WARM)
                .containsExactly(Season.SPRING_WARM, Season.AUTUMN_WARM);
    }
}
