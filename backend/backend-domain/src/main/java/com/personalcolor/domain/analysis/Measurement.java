package com.personalcolor.domain.analysis;

import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.Undertone;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ml-service가 돌려준 측정·판정 결과 한 벌.
 *
 * <p>확률 분포를 통째로 담는 것이 핵심이다. 최상위 계절만 들고 다니면
 * "62% 봄 / 35% 여름"인 경계 케이스와 "97% 겨울"인 확실한 케이스를
 * 구분할 수 없다. 이 규칙은 Python 도메인의 불변식 4와 같고, 경계를
 * 넘어와도 유지된다.
 *
 * @param season 최상위 계절
 * @param confidence 확률 최댓값 × 품질 계수
 * @param probabilities 4계절 전체 확률 분포. 합은 1.0
 * @param undertone 웜/쿨 2분류
 * @param undertoneConfidence 언더톤 신뢰도. 4분류를 병합한 것이라 항상 더 높거나 같다
 * @param axes 3축 판정 근거
 * @param features 측정된 색채 통계
 * @param preprocessing 전처리 보고
 * @param qualityFactor 입력 품질 계수 (0~1)
 * @param warnings 사용자에게 보여줄 경고 (한국어)
 */
public record Measurement(
        Season season,
        double confidence,
        Map<Season, Double> probabilities,
        Undertone undertone,
        double undertoneConfidence,
        List<AxisReading> axes,
        SkinFeatures features,
        PreprocessingReport preprocessing,
        double qualityFactor,
        List<String> warnings) {

    /** 확률 합이 1.0에서 벗어나도 허용하는 폭. 부동소수 오차와 직렬화 반올림을 흡수한다. */
    private static final double PROBABILITY_SUM_TOLERANCE = 1e-6;

    public Measurement {
        if (season == null || undertone == null) {
            throw new IllegalArgumentException("계절과 언더톤은 필수입니다.");
        }
        if (features == null || preprocessing == null) {
            throw new IllegalArgumentException("측정값과 전처리 보고는 필수입니다.");
        }
        requireRatio(confidence, "confidence");
        requireRatio(undertoneConfidence, "undertoneConfidence");
        requireRatio(qualityFactor, "qualityFactor");

        probabilities = validatedProbabilities(probabilities, season);
        axes = List.copyOf(axes == null ? List.of() : axes);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    private static void requireRatio(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + "는 0~1이어야 합니다: " + value);
        }
    }

    /**
     * 확률 분포가 계약을 지키는지 확인하고 불변 복사본을 만든다.
     *
     * <p>네 계절이 모두 있어야 하고, 합이 1이어야 하며, 최상위가 실제로
     * {@code season}이어야 한다. 셋 중 하나라도 어긋나면 ml-service와의
     * 계약이 깨진 것이므로 조용히 넘기지 않는다 — 여기서 막지 않으면
     * "확률 1위가 아닌 계절"이 DB에 저장되고 한참 뒤에 발견된다.
     */
    private static Map<Season, Double> validatedProbabilities(
            Map<Season, Double> source, Season declaredTop) {
        if (source == null || source.size() != Season.values().length) {
            throw new IllegalArgumentException(
                    "4계절 확률이 모두 필요합니다: " + (source == null ? "null" : source.keySet()));
        }

        Map<Season, Double> copy = new EnumMap<>(Season.class);
        double sum = 0.0;
        Season top = null;
        double topValue = -1.0;

        for (Season s : Season.values()) {
            Double p = source.get(s);
            if (p == null) {
                throw new IllegalArgumentException(s.code() + " 확률이 없습니다.");
            }
            requireRatio(p, s.code() + " 확률");
            copy.put(s, p);
            sum += p;
            if (p > topValue) {
                topValue = p;
                top = s;
            }
        }

        if (Math.abs(sum - 1.0) > PROBABILITY_SUM_TOLERANCE) {
            throw new IllegalArgumentException("확률의 합이 1이 아닙니다: " + sum);
        }
        if (top != declaredTop) {
            throw new IllegalArgumentException(
                    "선언된 계절(" + declaredTop.code() + ")이 확률 1위("
                            + top.code() + ")와 다릅니다 — ml-service 계약 위반입니다.");
        }
        return Map.copyOf(copy);
    }

    /**
     * 1위와 2위의 확률 격차.
     *
     * <p>경계 판정을 절대 확률로 보면 안 되는 이유가 여기 있다. 4분류에서
     * "55%"는 나머지가 15%씩 흩어졌으면 확실한 결과지만 2위가 44%라면
     * 사실상 동점이다. 이 격차가 작으면 UI가 "두 계절 사이"라고 알린다.
     */
    public double topTwoMargin() {
        return probabilities.values().stream()
                .sorted((a, b) -> Double.compare(b, a))
                .limit(2)
                .reduce((first, second) -> first - second)
                .orElse(0.0);
    }
}
