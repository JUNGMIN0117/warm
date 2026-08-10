package com.personalcolor.domain;

import com.personalcolor.domain.analysis.AxisReading;
import com.personalcolor.domain.analysis.Measurement;
import com.personalcolor.domain.analysis.PreprocessingReport;
import com.personalcolor.domain.analysis.RgbColor;
import com.personalcolor.domain.analysis.SkinFeatures;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.SeasonProfile;
import com.personalcolor.domain.season.Undertone;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 테스트 픽스처.
 *
 * <p>수치는 ml-service의 실제 응답에서 가져왔다 (docs/05-api-spec.md §3의
 * 가을 웜 예시). 임의의 값을 지어내면 "이 조합이 실제로 나올 수 있는가"를
 * 아무도 보장하지 못한다.
 */
public final class Fixtures {

    private Fixtures() {}

    public static Measurement autumnWarmMeasurement() {
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
                List.of(
                        new AxisReading("undertone", 68.42, 0.783, "쿨(푸른기)", "웜(노란기)",
                                "웜 성향이 뚜렷합니다"),
                        new AxisReading("depth", 13.55, 0.054, "딥(깊은)", "라이트(밝은)",
                                "딥 성향이 뚜렷합니다"),
                        new AxisReading("clarity", 49.08, 0.980, "뮤트(부드러운)", "클리어(선명한)",
                                "클리어 성향이 뚜렷합니다")),
                autumnWarmFeatures(),
                neutralPreprocessing(),
                1.0,
                List.of());
    }

    public static SkinFeatures autumnWarmFeatures() {
        return new SkinFeatures(
                61.0, 18.05, 45.64, 49.08, 68.42, 13.55, "tan", 0.85, 12453,
                new RgbColor(198, 134, 66));
    }

    public static PreprocessingReport neutralPreprocessing() {
        return new PreprocessingReport("gray_world", 1.0, 1.0, 1.0, 0.0002, 0.764, 50.0);
    }

    public static SeasonProfile profileFor(Season season) {
        return new SeasonProfile(
                season,
                "가을 웜",
                "Autumn Warm",
                "🍂",
                List.of("깊은", "따뜻한", "차분한"),
                "황금빛이 도는 깊은 피부톤입니다.",
                List.of(
                        SeasonProfile.PaletteColor.of("머스타드", "#D4A017"),
                        SeasonProfile.PaletteColor.of("테라코타", "#C56A3E"),
                        SeasonProfile.PaletteColor.of("올리브", "#6B7A3A"),
                        SeasonProfile.PaletteColor.of("카멜", "#B5813F"),
                        SeasonProfile.PaletteColor.of("브릭", "#9C4A2F"),
                        SeasonProfile.PaletteColor.of("모스그린", "#4F5D3A")),
                List.of(SeasonProfile.PaletteColor.of("형광 핑크", "#FF69B4")),
                List.of("금색 액세서리가 어울립니다."));
    }
}
