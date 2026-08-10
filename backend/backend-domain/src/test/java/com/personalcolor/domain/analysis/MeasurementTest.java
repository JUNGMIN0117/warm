package com.personalcolor.domain.analysis;

import com.personalcolor.domain.Fixtures;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.Undertone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Measurement — ml-service 계약 방어")
class MeasurementTest {

    private static Map<Season, Double> distribution(double spring, double summer,
                                                    double autumn, double winter) {
        Map<Season, Double> map = new EnumMap<>(Season.class);
        map.put(Season.SPRING_WARM, spring);
        map.put(Season.SUMMER_COOL, summer);
        map.put(Season.AUTUMN_WARM, autumn);
        map.put(Season.WINTER_COOL, winter);
        return map;
    }

    private static Measurement withDistribution(Season declaredTop, Map<Season, Double> probs) {
        return new Measurement(
                declaredTop, 0.5, probs, Undertone.WARM, 0.6, List.of(),
                Fixtures.autumnWarmFeatures(), Fixtures.neutralPreprocessing(), 1.0, List.of());
    }

    @Test
    @DisplayName("정상 응답을 받아들인다")
    void acceptsValidMeasurement() {
        Measurement measurement = Fixtures.autumnWarmMeasurement();

        assertThat(measurement.season()).isEqualTo(Season.AUTUMN_WARM);
        assertThat(measurement.probabilities()).hasSize(4);
        assertThat(measurement.axes()).hasSize(3);
    }

    @Nested
    @DisplayName("확률 분포 검증")
    class Probabilities {

        @Test
        @DisplayName("계절이 하나라도 빠지면 거부한다")
        void rejectsIncompleteDistribution() {
            Map<Season, Double> partial = new EnumMap<>(Season.class);
            partial.put(Season.AUTUMN_WARM, 1.0);

            assertThatThrownBy(() -> withDistribution(Season.AUTUMN_WARM, partial))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("4계절");
        }

        @Test
        @DisplayName("합이 1이 아니면 거부한다")
        void rejectsNonNormalizedDistribution() {
            assertThatThrownBy(() ->
                    withDistribution(Season.AUTUMN_WARM, distribution(0.3, 0.3, 0.3, 0.3)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("합이 1이 아닙니다");
        }

        @Test
        @DisplayName("선언된 계절이 확률 1위와 다르면 거부한다")
        void rejectsInconsistentTopSeason() {
            // 1위는 겨울인데 봄이라고 선언한 경우 — 이걸 통과시키면
            // 확률 1위가 아닌 계절이 DB에 저장되고 한참 뒤에 발견된다.
            assertThatThrownBy(() ->
                    withDistribution(Season.SPRING_WARM, distribution(0.1, 0.1, 0.1, 0.7)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("확률 1위");
        }

        @Test
        @DisplayName("부동소수 오차는 허용한다")
        void toleratesFloatingPointDrift() {
            // 직렬화·반올림으로 합이 정확히 1이 되지 않는 것은 정상이다.
            assertThat(withDistribution(Season.AUTUMN_WARM,
                    distribution(0.1, 0.1, 0.7, 0.0999999)))
                    .isNotNull();
        }

        @Test
        @DisplayName("반환된 분포는 불변이다")
        void distributionIsImmutable() {
            Measurement measurement = Fixtures.autumnWarmMeasurement();

            assertThatThrownBy(() -> measurement.probabilities().put(Season.SPRING_WARM, 1.0))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    @DisplayName("1·2위 격차로 경계 판정을 잰다")
    void reportsTopTwoMargin() {
        // 절대 확률만 보면 55%는 확실해 보이지만 2위가 44%면 사실상 동점이다.
        Measurement tie = withDistribution(Season.AUTUMN_WARM,
                distribution(0.01, 0.0, 0.55, 0.44));
        Measurement clear = withDistribution(Season.AUTUMN_WARM,
                distribution(0.05, 0.05, 0.85, 0.05));

        assertThat(tie.topTwoMargin()).isCloseTo(0.11, within(1e-9));
        assertThat(clear.topTwoMargin()).isCloseTo(0.80, within(1e-9));
    }

    @Test
    @DisplayName("신뢰도가 0~1을 벗어나면 거부한다")
    void rejectsOutOfRangeConfidence() {
        assertThatThrownBy(() -> new Measurement(
                Season.AUTUMN_WARM, 1.5, distribution(0.1, 0.1, 0.7, 0.1),
                Undertone.WARM, 0.6, List.of(), Fixtures.autumnWarmFeatures(),
                Fixtures.neutralPreprocessing(), 1.0, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }
}
