package com.personalcolor.infrastructure.mlservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * ml-service 응답의 전송 표현.
 *
 * <p>도메인 모델과 분리해 둔 이유는 대칭이다 — ml-service가 Pydantic
 * 스키마를 두어 자기 도메인과 API를 갈라놓았듯, 이쪽도 전송 형태와
 * 도메인 모델을 갈라 둔다. 상대가 필드를 추가해도 우리 도메인은 그대로다.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}가 그 방어의 핵심이다.
 * ml-service가 응답에 필드를 하나 더하는 것은 하위 호환 변경인데, 이게
 * 없으면 우리 쪽이 역직렬화 실패로 죽는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlAnalysisResponse(
        String season,
        double confidence,
        Map<String, Double> probabilities,
        String undertone,
        @JsonProperty("undertone_confidence") double undertoneConfidence,
        List<Axis> axes,
        Features features,
        @JsonProperty("white_balance") WhiteBalance whiteBalance,
        @JsonProperty("mask_quality") MaskQuality maskQuality,
        @JsonProperty("quality_factor") double qualityFactor,
        List<String> warnings,
        Stages stages) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Axis(
            String name,
            @JsonProperty("raw_value") double rawValue,
            double normalized,
            @JsonProperty("low_label") String lowLabel,
            @JsonProperty("high_label") String highLabel,
            String interpretation) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Features(
            double lightness,
            @JsonProperty("a_star") double aStar,
            @JsonProperty("b_star") double bStar,
            double chroma,
            @JsonProperty("hue_angle") double hueAngle,
            double ita,
            @JsonProperty("ita_category") String itaCategory,
            @JsonProperty("lightness_spread") double lightnessSpread,
            @JsonProperty("pixel_count") int pixelCount,
            @JsonProperty("median_rgb") List<Integer> medianRgb) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WhiteBalance(
            String method,
            List<Double> gains,
            @JsonProperty("cast_strength") double castStrength) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MaskQuality(
            @JsonProperty("coverage_ratio") double coverageRatio,
            @JsonProperty("otsu_threshold") double otsuThreshold) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stages(
            String original,
            @JsonProperty("white_balanced") String whiteBalanced,
            @JsonProperty("face_crop") String faceCrop,
            @JsonProperty("skin_mask") String skinMask,
            @JsonProperty("measured_pixels") String measuredPixels) {}
}
