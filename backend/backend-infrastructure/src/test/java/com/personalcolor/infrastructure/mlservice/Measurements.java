package com.personalcolor.infrastructure.mlservice;

import com.personalcolor.domain.analysis.AxisReading;
import com.personalcolor.domain.analysis.Measurement;
import com.personalcolor.domain.analysis.PreprocessingReport;
import com.personalcolor.domain.analysis.RgbColor;
import com.personalcolor.domain.analysis.SkinFeatures;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.Undertone;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 인프라 테스트용 측정값 픽스처.
 *
 * <p>backend-domain의 테스트 픽스처는 그 모듈의 test 산출물이라 여기서
 * 보이지 않는다. test-jar를 만들어 공유할 수도 있지만, 모듈 간 테스트
 * 결합을 만들 만큼 큰 값이 아니라 필요한 최소한만 다시 둔다.
 */
final class Measurements {

    private Measurements() {}

    static Measurement autumnWarm() {
        Map<Season, Double> probabilities = new EnumMap<>(Season.class);
        probabilities.put(Season.SPRING_WARM, 0.132);
        probabilities.put(Season.SUMMER_COOL, 0.004);
        probabilities.put(Season.AUTUMN_WARM, 0.822);
        probabilities.put(Season.WINTER_COOL, 0.042);

        return new Measurement(
                Season.AUTUMN_WARM,
                0.822,
                probabilities,
                Undertone.WARM,
                0.954,
                List.of(new AxisReading("undertone", 68.42, 0.783,
                        "쿨(푸른기)", "웜(노란기)", "웜 성향이 뚜렷합니다")),
                new SkinFeatures(61.0, 18.05, 45.64, 49.08, 68.42, 13.55,
                        "tan", 0.85, 12453, new RgbColor(198, 134, 66)),
                new PreprocessingReport("gray_world", 1.0, 1.0, 1.0, 0.0002, 0.764, 50.0),
                1.0,
                List.of());
    }
}
