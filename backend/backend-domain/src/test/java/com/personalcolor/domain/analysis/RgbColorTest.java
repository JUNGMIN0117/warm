package com.personalcolor.domain.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RgbColor")
class RgbColorTest {

    @Test
    @DisplayName("HEX 왕복이 항등이다")
    void hexRoundTrip() {
        RgbColor color = new RgbColor(198, 134, 66);

        assertThat(color.toHex()).isEqualTo("#C68642");
        assertThat(RgbColor.fromHex(color.toHex())).isEqualTo(color);
    }

    @Test
    @DisplayName("소문자 HEX도 읽는다")
    void parsesLowercaseHex() {
        // ml-service와 시드 JSON이 대소문자를 통일해 준다는 보장이 없다.
        assertThat(RgbColor.fromHex("#c68642")).isEqualTo(new RgbColor(198, 134, 66));
    }

    @Test
    @DisplayName("한 자리 값도 두 자리로 채운다")
    void padsSingleDigits() {
        assertThat(new RgbColor(0, 5, 16).toHex()).isEqualTo("#000510");
    }

    @ParameterizedTest
    @ValueSource(strings = {"C68642", "#C6864", "#C686422", "#GGGGGG", ""})
    @DisplayName("형식이 어긋나면 거부한다")
    void rejectsMalformedHex(String hex) {
        assertThatThrownBy(() -> RgbColor.fromHex(hex))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("채널 범위를 벗어나면 거부한다")
    void rejectsOutOfRangeChannel() {
        assertThatThrownBy(() -> new RgbColor(256, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("red");
        assertThatThrownBy(() -> new RgbColor(0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("green");
    }
}
