package com.personalcolor.infrastructure.persistence;

import com.personalcolor.domain.analysis.AnalysisRecord;
import com.personalcolor.domain.analysis.AxisReading;
import com.personalcolor.domain.analysis.Measurement;
import com.personalcolor.domain.analysis.PreprocessingReport;
import com.personalcolor.domain.analysis.RgbColor;
import com.personalcolor.domain.analysis.SkinFeatures;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.Undertone;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 도메인 ↔ 엔티티 변환.
 *
 * <p>양방향 변환을 한 클래스에 모아두면 필드를 추가할 때 빠뜨리기 어렵다.
 * 변환이 흩어져 있으면 저장은 되는데 조회에서 null이 되는 종류의 버그가 난다.
 *
 * <p>JSONB 컬럼(축·경고)은 여기서 직렬화한다. 엔티티가 Jackson을 모르게
 * 두어, 저장 형식 변경이 엔티티 선언을 건드리지 않게 했다.
 */
@Component
public class AnalysisEntityMapper {

    private static final TypeReference<List<AxisReading>> AXES_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> WARNINGS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public AnalysisEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisEntity toEntity(AnalysisRecord record) {
        Measurement m = record.measurement();
        SkinFeatures f = m.features();
        PreprocessingReport p = m.preprocessing();

        AnalysisEntity entity = new AnalysisEntity(
                record.id(), record.userId().orElse(null),
                record.imageHash(), record.analyzedAt());

        entity.setVerdict(m.season().code(), m.confidence(), m.undertone().code(),
                m.undertoneConfidence(), m.qualityFactor());
        entity.setProbabilities(
                m.probabilities().get(Season.SPRING_WARM),
                m.probabilities().get(Season.SUMMER_COOL),
                m.probabilities().get(Season.AUTUMN_WARM),
                m.probabilities().get(Season.WINTER_COOL));
        entity.setFeatures(f.lightness(), f.aStar(), f.bStar(), f.chroma(), f.hueAngle(),
                f.ita(), f.itaCategory(), f.lightnessSpread(), f.pixelCount(),
                f.medianRgb().toHex());
        entity.setPreprocessing(p.whiteBalanceMethod(), p.gainRed(), p.gainGreen(),
                p.gainBlue(), p.castStrength(), p.maskCoverageRatio(), p.otsuThreshold());
        entity.setJsonColumns(
                objectMapper.writeValueAsString(m.axes()),
                objectMapper.writeValueAsString(m.warnings()));

        return entity;
    }

    public AnalysisRecord toDomain(AnalysisEntity entity) {
        Map<Season, Double> probabilities = new EnumMap<>(Season.class);
        probabilities.put(Season.SPRING_WARM, entity.getProbSpringWarm());
        probabilities.put(Season.SUMMER_COOL, entity.getProbSummerCool());
        probabilities.put(Season.AUTUMN_WARM, entity.getProbAutumnWarm());
        probabilities.put(Season.WINTER_COOL, entity.getProbWinterCool());

        Measurement measurement = new Measurement(
                Season.fromCode(entity.getSeason()),
                entity.getConfidence(),
                probabilities,
                Undertone.fromCode(entity.getUndertone()),
                entity.getUndertoneConfidence(),
                objectMapper.readValue(entity.getAxes(), AXES_TYPE),
                new SkinFeatures(
                        entity.getLightness(), entity.getAStar(), entity.getBStar(),
                        entity.getChroma(), entity.getHueAngle(), entity.getIta(),
                        entity.getItaCategory(), entity.getLightnessSpread(),
                        entity.getPixelCount(), RgbColor.fromHex(entity.getMedianRgb())),
                new PreprocessingReport(
                        entity.getWbMethod(), entity.getWbGainRed(), entity.getWbGainGreen(),
                        entity.getWbGainBlue(), entity.getWbCastStrength(),
                        entity.getMaskCoverageRatio(), entity.getOtsuThreshold()),
                entity.getQualityFactor(),
                objectMapper.readValue(entity.getWarnings(), WARNINGS_TYPE));

        return new AnalysisRecord(
                entity.getId(),
                entity.getImageHash(),
                Optional.ofNullable(entity.getUserId()),
                measurement,
                entity.getAnalyzedAt());
    }
}
