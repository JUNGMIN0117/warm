package com.personalcolor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 분석 이력 테이블 매핑.
 *
 * <p>도메인의 {@code AnalysisRecord}와 별개로 존재하는 이유는 ADR-006에
 * 있다 — 엔티티는 "DB에 이렇게 저장한다"는 인프라 결정이지 도메인
 * 개념이 아니다. 컬럼 하나 바꾸는 일이 도메인 모델을 건드리지 않는다.
 *
 * <p>record가 아니라 클래스인 것은 JPA의 요구다. 기본 생성자와 비-final
 * 필드가 필요하다.
 *
 * <p>중첩 구조(축 해석, 경고)는 JSONB 문자열로 둔다. 길이가 가변이고
 * 조회 조건이 되지 않아 자식 테이블을 만들 값이 아니다.
 */
@Entity
@Table(name = "analyses")
public class AnalysisEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "image_hash", nullable = false, length = 64)
    private String imageHash;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @Column(nullable = false, length = 20)
    private String season;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false, length = 10)
    private String undertone;

    @Column(name = "undertone_confidence", nullable = false)
    private double undertoneConfidence;

    @Column(name = "quality_factor", nullable = false)
    private double qualityFactor;

    @Column(name = "prob_spring_warm", nullable = false)
    private double probSpringWarm;

    @Column(name = "prob_summer_cool", nullable = false)
    private double probSummerCool;

    @Column(name = "prob_autumn_warm", nullable = false)
    private double probAutumnWarm;

    @Column(name = "prob_winter_cool", nullable = false)
    private double probWinterCool;

    @Column(nullable = false)
    private double lightness;

    @Column(name = "a_star", nullable = false)
    private double aStar;

    @Column(name = "b_star", nullable = false)
    private double bStar;

    @Column(nullable = false)
    private double chroma;

    @Column(name = "hue_angle", nullable = false)
    private double hueAngle;

    @Column(nullable = false)
    private double ita;

    @Column(name = "ita_category", nullable = false, length = 20)
    private String itaCategory;

    @Column(name = "lightness_spread", nullable = false)
    private double lightnessSpread;

    @Column(name = "pixel_count", nullable = false)
    private int pixelCount;

    @Column(name = "median_rgb", nullable = false, length = 7)
    private String medianRgb;

    @Column(name = "wb_method", nullable = false, length = 20)
    private String wbMethod;

    @Column(name = "wb_gain_red", nullable = false)
    private double wbGainRed;

    @Column(name = "wb_gain_green", nullable = false)
    private double wbGainGreen;

    @Column(name = "wb_gain_blue", nullable = false)
    private double wbGainBlue;

    @Column(name = "wb_cast_strength", nullable = false)
    private double wbCastStrength;

    @Column(name = "mask_coverage_ratio", nullable = false)
    private double maskCoverageRatio;

    @Column(name = "otsu_threshold", nullable = false)
    private double otsuThreshold;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String axes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String warnings;

    protected AnalysisEntity() {
        // JPA 전용
    }

    AnalysisEntity(UUID id, UUID userId, String imageHash, Instant analyzedAt) {
        this.id = id;
        this.userId = userId;
        this.imageHash = imageHash;
        this.analyzedAt = analyzedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getImageHash() {
        return imageHash;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public String getSeason() {
        return season;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getUndertone() {
        return undertone;
    }

    public double getUndertoneConfidence() {
        return undertoneConfidence;
    }

    public double getQualityFactor() {
        return qualityFactor;
    }

    public double getProbSpringWarm() {
        return probSpringWarm;
    }

    public double getProbSummerCool() {
        return probSummerCool;
    }

    public double getProbAutumnWarm() {
        return probAutumnWarm;
    }

    public double getProbWinterCool() {
        return probWinterCool;
    }

    public double getLightness() {
        return lightness;
    }

    public double getAStar() {
        return aStar;
    }

    public double getBStar() {
        return bStar;
    }

    public double getChroma() {
        return chroma;
    }

    public double getHueAngle() {
        return hueAngle;
    }

    public double getIta() {
        return ita;
    }

    public String getItaCategory() {
        return itaCategory;
    }

    public double getLightnessSpread() {
        return lightnessSpread;
    }

    public int getPixelCount() {
        return pixelCount;
    }

    public String getMedianRgb() {
        return medianRgb;
    }

    public String getWbMethod() {
        return wbMethod;
    }

    public double getWbGainRed() {
        return wbGainRed;
    }

    public double getWbGainGreen() {
        return wbGainGreen;
    }

    public double getWbGainBlue() {
        return wbGainBlue;
    }

    public double getWbCastStrength() {
        return wbCastStrength;
    }

    public double getMaskCoverageRatio() {
        return maskCoverageRatio;
    }

    public double getOtsuThreshold() {
        return otsuThreshold;
    }

    public String getAxes() {
        return axes;
    }

    public String getWarnings() {
        return warnings;
    }

    void setVerdict(String season, double confidence, String undertone,
                    double undertoneConfidence, double qualityFactor) {
        this.season = season;
        this.confidence = confidence;
        this.undertone = undertone;
        this.undertoneConfidence = undertoneConfidence;
        this.qualityFactor = qualityFactor;
    }

    void setProbabilities(double springWarm, double summerCool,
                          double autumnWarm, double winterCool) {
        this.probSpringWarm = springWarm;
        this.probSummerCool = summerCool;
        this.probAutumnWarm = autumnWarm;
        this.probWinterCool = winterCool;
    }

    void setFeatures(double lightness, double aStar, double bStar, double chroma,
                     double hueAngle, double ita, String itaCategory,
                     double lightnessSpread, int pixelCount, String medianRgb) {
        this.lightness = lightness;
        this.aStar = aStar;
        this.bStar = bStar;
        this.chroma = chroma;
        this.hueAngle = hueAngle;
        this.ita = ita;
        this.itaCategory = itaCategory;
        this.lightnessSpread = lightnessSpread;
        this.pixelCount = pixelCount;
        this.medianRgb = medianRgb;
    }

    void setPreprocessing(String wbMethod, double gainRed, double gainGreen, double gainBlue,
                          double castStrength, double coverageRatio, double otsuThreshold) {
        this.wbMethod = wbMethod;
        this.wbGainRed = gainRed;
        this.wbGainGreen = gainGreen;
        this.wbGainBlue = gainBlue;
        this.wbCastStrength = castStrength;
        this.maskCoverageRatio = coverageRatio;
        this.otsuThreshold = otsuThreshold;
    }

    void setJsonColumns(String axes, String warnings) {
        this.axes = axes;
        this.warnings = warnings;
    }
}
