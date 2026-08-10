package com.personalcolor.api.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.personalcolor.domain.analysis.AnalysisRecord;
import com.personalcolor.domain.analysis.AnalysisView;
import com.personalcolor.domain.analysis.AxisReading;
import com.personalcolor.domain.analysis.Measurement;
import com.personalcolor.domain.analysis.SkinFeatures;
import com.personalcolor.domain.analysis.StageImages;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.SeasonProfile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 분석 응답.
 *
 * <p>ml-service 응답을 그대로 중계하지 않고 다시 조립한다. 측정값(Python)과
 * 큐레이션(DB)이 합쳐진 형태가 프론트가 실제로 필요로 하는 것이고,
 * 그 조립이 이 게이트웨이의 존재 이유다 (ADR-005).
 */
public final class AnalysisDtos {

    private AnalysisDtos() {}

    /** 분석 결과 전체. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnalysisResponse(
            String id,
            Instant analyzedAt,
            boolean saved,
            SeasonView season,
            double confidence,
            Map<String, Double> probabilities,
            String undertone,
            double undertoneConfidence,
            double topTwoMargin,
            List<AxisView> axes,
            FeaturesView features,
            PreprocessingView preprocessing,
            double qualityFactor,
            List<String> warnings,
            StagesView stages) {

        public static AnalysisResponse from(AnalysisView view) {
            AnalysisRecord record = view.record();
            Measurement m = record.measurement();

            return new AnalysisResponse(
                    record.id().toString(),
                    record.analyzedAt(),
                    // 익명 분석은 저장되지 않는다. 프론트가 "이력에 담겼습니다"를
                    // 잘못 안내하지 않도록 사실을 그대로 알려준다.
                    !record.isAnonymous(),
                    SeasonView.from(view.profile()),
                    m.confidence(),
                    toProbabilityMap(m.probabilities()),
                    m.undertone().code(),
                    m.undertoneConfidence(),
                    m.topTwoMargin(),
                    m.axes().stream().map(AxisView::from).toList(),
                    FeaturesView.from(m.features()),
                    PreprocessingView.from(m),
                    m.qualityFactor(),
                    m.warnings(),
                    view.stages().map(StagesView::from).orElse(null));
        }

        private static Map<String, Double> toProbabilityMap(Map<Season, Double> source) {
            return source.entrySet().stream().collect(
                    java.util.stream.Collectors.toMap(e -> e.getKey().code(), Map.Entry::getValue));
        }
    }

    /** 이력 목록의 한 줄. 원본 이미지가 없으므로 대표 색과 수치로 표현한다. */
    public record HistoryItem(
            String id,
            Instant analyzedAt,
            String seasonCode,
            String seasonLabelKo,
            String emoji,
            double confidence,
            String medianRgbHex) {

        public static HistoryItem from(AnalysisRecord record, SeasonProfile profile) {
            Measurement m = record.measurement();
            return new HistoryItem(
                    record.id().toString(),
                    record.analyzedAt(),
                    m.season().code(),
                    profile.labelKo(),
                    profile.emoji(),
                    m.confidence(),
                    m.features().medianRgb().toHex());
        }
    }

    /** 계절 큐레이션. DB가 소유하는 부분이다. */
    public record SeasonView(
            String code,
            String labelKo,
            String labelEn,
            String emoji,
            List<String> keywords,
            String description,
            List<ColorView> bestColors,
            List<ColorView> worstColors,
            List<String> stylingTips) {

        public static SeasonView from(SeasonProfile profile) {
            return new SeasonView(
                    profile.season().code(),
                    profile.labelKo(),
                    profile.labelEn(),
                    profile.emoji(),
                    profile.keywords(),
                    profile.description(),
                    profile.bestColors().stream().map(ColorView::from).toList(),
                    profile.worstColors().stream().map(ColorView::from).toList(),
                    profile.stylingTips());
        }
    }

    public record ColorView(String name, String hex) {
        public static ColorView from(SeasonProfile.PaletteColor color) {
            return new ColorView(color.name(), color.color().toHex());
        }
    }

    public record AxisView(
            String name, double rawValue, double normalized,
            String lowLabel, String highLabel, String interpretation) {

        public static AxisView from(AxisReading axis) {
            return new AxisView(axis.name(), axis.rawValue(), axis.normalized(),
                    axis.lowLabel(), axis.highLabel(), axis.interpretation());
        }
    }

    public record FeaturesView(
            double lightness, double aStar, double bStar, double chroma,
            double hueAngle, double ita, String itaCategory,
            int pixelCount, String medianRgbHex) {

        public static FeaturesView from(SkinFeatures f) {
            return new FeaturesView(
                    f.lightness(), f.aStar(), f.bStar(), f.chroma(), f.hueAngle(),
                    f.ita(), f.itaCategory(), f.pixelCount(), f.medianRgb().toHex());
        }
    }

    /** 전처리가 사진을 얼마나 건드렸는지. 보정하고 침묵하지 않는다. */
    public record PreprocessingView(
            String whiteBalanceMethod,
            List<Double> gains,
            double castStrength,
            double maskCoverageRatio) {

        public static PreprocessingView from(Measurement m) {
            var p = m.preprocessing();
            return new PreprocessingView(
                    p.whiteBalanceMethod(),
                    List.of(p.gainRed(), p.gainGreen(), p.gainBlue()),
                    p.castStrength(),
                    p.maskCoverageRatio());
        }
    }

    public record StagesView(
            String original, String whiteBalanced, String faceCrop,
            String skinMask, String measuredPixels) {

        public static StagesView from(StageImages stages) {
            return new StagesView(stages.original(), stages.whiteBalanced(),
                    stages.faceCrop(), stages.skinMask(), stages.measuredPixels());
        }
    }
}
