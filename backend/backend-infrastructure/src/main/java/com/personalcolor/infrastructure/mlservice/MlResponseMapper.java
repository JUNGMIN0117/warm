package com.personalcolor.infrastructure.mlservice;

import com.personalcolor.domain.analysis.AnalysisOutcome;
import com.personalcolor.domain.analysis.AxisReading;
import com.personalcolor.domain.analysis.Measurement;
import com.personalcolor.domain.analysis.PreprocessingReport;
import com.personalcolor.domain.analysis.RgbColor;
import com.personalcolor.domain.analysis.SkinFeatures;
import com.personalcolor.domain.analysis.StageImages;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.Undertone;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 전송 표현 → 도메인 모델 변환.
 *
 * <p>변환을 한 클래스에 모아둔 이유: ml-service가 스키마를 바꾸면 고칠
 * 곳이 여기 하나다. 그리고 이 클래스는 순수 함수라 HTTP 없이 테스트된다.
 *
 * <p>변환 도중 값이 이상하면 도메인 record의 생성자가 던진다. 그 예외를
 * 여기서 잡아 감추지 않는 것이 의도다 — 계약이 깨졌으면 조용히 넘어가는
 * 것보다 시끄럽게 실패하는 편이 낫다.
 */
final class MlResponseMapper {

    private MlResponseMapper() {}

    static AnalysisOutcome toDomain(MlAnalysisResponse response) {
        Measurement measurement = new Measurement(
                Season.fromCode(response.season()),
                response.confidence(),
                toProbabilities(response.probabilities()),
                Undertone.fromCode(response.undertone()),
                response.undertoneConfidence(),
                toAxes(response.axes()),
                toFeatures(response.features()),
                toPreprocessing(response.whiteBalance(), response.maskQuality()),
                response.qualityFactor(),
                response.warnings() == null ? List.of() : response.warnings());

        MlAnalysisResponse.Stages stages = response.stages();
        return stages == null
                ? AnalysisOutcome.of(measurement)
                : AnalysisOutcome.of(measurement, new StageImages(
                        stages.original(),
                        stages.whiteBalanced(),
                        stages.faceCrop(),
                        stages.skinMask(),
                        stages.measuredPixels()));
    }

    private static Map<Season, Double> toProbabilities(Map<String, Double> source) {
        if (source == null) {
            throw new IllegalArgumentException("확률 분포가 없습니다 — ml-service 계약 위반입니다.");
        }
        Map<Season, Double> result = new EnumMap<>(Season.class);
        source.forEach((code, value) -> result.put(Season.fromCode(code), value));
        return result;
    }

    private static List<AxisReading> toAxes(List<MlAnalysisResponse.Axis> axes) {
        if (axes == null) {
            return List.of();
        }
        return axes.stream()
                .map(a -> new AxisReading(
                        a.name(), a.rawValue(), a.normalized(),
                        a.lowLabel(), a.highLabel(), a.interpretation()))
                .toList();
    }

    private static SkinFeatures toFeatures(MlAnalysisResponse.Features f) {
        if (f == null) {
            throw new IllegalArgumentException("측정값이 없습니다 — ml-service 계약 위반입니다.");
        }
        List<Integer> rgb = f.medianRgb();
        if (rgb == null || rgb.size() != 3) {
            throw new IllegalArgumentException("median_rgb는 값 3개여야 합니다: " + rgb);
        }
        return new SkinFeatures(
                f.lightness(), f.aStar(), f.bStar(), f.chroma(), f.hueAngle(),
                f.ita(), f.itaCategory(), f.lightnessSpread(), f.pixelCount(),
                new RgbColor(rgb.get(0), rgb.get(1), rgb.get(2)));
    }

    private static PreprocessingReport toPreprocessing(
            MlAnalysisResponse.WhiteBalance wb, MlAnalysisResponse.MaskQuality mask) {
        if (wb == null || mask == null) {
            throw new IllegalArgumentException("전처리 보고가 없습니다 — ml-service 계약 위반입니다.");
        }
        List<Double> gains = wb.gains();
        if (gains == null || gains.size() != 3) {
            throw new IllegalArgumentException("gains는 값 3개여야 합니다: " + gains);
        }
        return new PreprocessingReport(
                wb.method(), gains.get(0), gains.get(1), gains.get(2),
                wb.castStrength(), mask.coverageRatio(), mask.otsuThreshold());
    }
}
