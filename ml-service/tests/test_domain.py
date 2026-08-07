"""도메인 로직 단위 테스트.

이미지도 모델도 필요 없다. 순수 수치 연산이라 밀리초 단위로 끝난다.
이것이 도메인 계층을 I/O에서 분리한 실질적 대가다.
"""

from __future__ import annotations

import numpy as np
import pytest

from app.domain import (
    CalibrationConfig,
    Season,
    Undertone,
    classify,
    compute_ita,
    extract_features,
    get_profile,
    lab_to_lch,
    linear_to_srgb,
    rgb_to_lab,
    srgb_to_linear,
)


class TestColorSpace:
    def test_white_maps_to_lightness_100(self) -> None:
        lab = rgb_to_lab(np.array([255, 255, 255]))
        assert lab[0] == pytest.approx(100.0, abs=0.1)
        assert lab[1] == pytest.approx(0.0, abs=0.1)
        assert lab[2] == pytest.approx(0.0, abs=0.1)

    def test_black_maps_to_lightness_0(self) -> None:
        lab = rgb_to_lab(np.array([0, 0, 0]))
        assert lab[0] == pytest.approx(0.0, abs=0.1)

    def test_mid_gray_is_achromatic(self) -> None:
        """중간 회색은 a*, b*가 모두 0이어야 한다. 변환 행렬 검증용."""
        lab = rgb_to_lab(np.array([128, 128, 128]))
        assert lab[1] == pytest.approx(0.0, abs=0.5)
        assert lab[2] == pytest.approx(0.0, abs=0.5)

    def test_gamma_removal_is_not_identity(self) -> None:
        """감마를 제거하지 않으면 중간 회색 L*이 50 근처로 잘못 나온다.

        올바르게 선형화하면 sRGB 128의 L*은 약 53.6이다.
        이 테스트가 깨진다면 srgb_to_linear가 우회된 것이다.
        """
        lab = rgb_to_lab(np.array([128, 128, 128]))
        assert 53.0 < lab[0] < 54.5

    def test_lch_hue_of_pure_yellow_direction(self) -> None:
        """b*만 양수인 색은 h°가 90°여야 한다."""
        _, chroma, hue = lab_to_lch(np.array([50.0, 0.0, 30.0]))
        assert hue == pytest.approx(90.0, abs=0.1)
        assert chroma == pytest.approx(30.0, abs=0.1)

    def test_batch_shape_is_preserved(self) -> None:
        image = np.random.randint(0, 256, size=(4, 5, 3), dtype=np.uint8)
        assert rgb_to_lab(image).shape == (4, 5, 3)

    def test_gamma_roundtrip_is_identity(self) -> None:
        """srgb→linear→srgb 왕복이 항등이어야 한다.

        linear_to_srgb는 화이트밸런스(선형 공간 연산)가 결과를 다시
        이미지로 되돌릴 때 쓰는 역함수라, 왕복 오차는 곧 파이프라인의
        누적 색 왜곡이 된다.
        """
        values = np.linspace(0.0, 1.0, 256)
        roundtrip = linear_to_srgb(srgb_to_linear(values))
        np.testing.assert_allclose(roundtrip, values, atol=1e-12)

    def test_linear_to_srgb_clips_negative_input(self) -> None:
        """음수 입력(수치 오차로 발생 가능)이 NaN을 만들면 안 된다."""
        result = linear_to_srgb(np.array([-0.01, 0.0, 0.5]))
        assert np.all(np.isfinite(result))
        assert result[0] == 0.0


class TestITA:
    def test_light_skin_yields_high_ita(self) -> None:
        assert compute_ita(lightness=88.0, b_star=15.0) > 55.0

    def test_dark_skin_yields_negative_ita(self) -> None:
        assert compute_ita(lightness=40.0, b_star=20.0) < 0.0

    def test_zero_b_star_does_not_raise(self) -> None:
        """b*=0은 수학적으로 발산하지만 예외 없이 처리되어야 한다."""
        assert np.isfinite(compute_ita(lightness=70.0, b_star=0.0))


def _uniform_patch(rgb: tuple[int, int, int], count: int = 5_000) -> np.ndarray:
    """단색 피부 패치에 미세한 노이즈를 얹어 만든다.

    노이즈를 넣는 이유는 완전 균일한 입력이 사분위 범위를 0으로 만들어
    현실과 동떨어진 조건에서 테스트하게 되기 때문이다.
    """
    rng = np.random.default_rng(seed=42)
    base = np.tile(np.array(rgb, dtype=np.float64), (count, 1))
    noise = rng.normal(0.0, 3.0, size=base.shape)
    return np.clip(base + noise, 0, 255)


class TestFeatureExtraction:
    def test_empty_input_raises(self) -> None:
        with pytest.raises(ValueError, match="피부 픽셀"):
            extract_features(np.empty((0, 3)))

    def test_wrong_shape_raises(self) -> None:
        with pytest.raises(ValueError, match=r"\(N, 3\)"):
            extract_features(np.zeros((10, 4)))

    def test_median_resists_outliers(self) -> None:
        """전체의 15%를 순백 픽셀로 오염시켜도 결과가 거의 변하지 않아야 한다.

        원본 프로젝트가 겪은 '마스크 가장자리 오염' 문제에 대한 회귀 테스트다.
        """
        clean = _uniform_patch((225, 184, 153), count=5_000)
        contaminated = np.vstack([clean, np.full((880, 3), 255.0)])

        baseline = extract_features(clean)
        polluted = extract_features(contaminated)

        assert abs(baseline.lightness - polluted.lightness) < 2.0
        assert abs(baseline.hue_angle - polluted.hue_angle) < 3.0

    def test_reports_representative_rgb(self) -> None:
        features = extract_features(_uniform_patch((225, 184, 153)))
        r, g, b = features.mean_rgb
        assert abs(r - 225) < 4 and abs(g - 184) < 4 and abs(b - 153) < 4

    def test_ita_category_is_labeled(self) -> None:
        features = extract_features(_uniform_patch((255, 223, 196)))
        assert features.ita_category in {"very_light", "light"}


class TestClassifier:
    @pytest.mark.parametrize(
        ("rgb", "expected_undertone"),
        [
            ((243, 213, 165), Undertone.WARM),  # 골든 계열
            ((198, 134, 66), Undertone.WARM),  # 딥 웜
            ((232, 196, 192), Undertone.COOL),  # 로지 쿨
            ((245, 221, 216), Undertone.COOL),  # 페일 쿨
        ],
    )
    def test_undertone_direction(
        self, rgb: tuple[int, int, int], expected_undertone: Undertone
    ) -> None:
        result = classify(extract_features(_uniform_patch(rgb)))
        assert result.undertone is expected_undertone

    def test_probabilities_sum_to_one(self) -> None:
        result = classify(extract_features(_uniform_patch((225, 184, 153))))
        assert sum(result.probabilities.values()) == pytest.approx(1.0, abs=1e-9)
        assert len(result.probabilities) == 4

    def test_selected_season_has_highest_probability(self) -> None:
        result = classify(extract_features(_uniform_patch((198, 134, 66))))
        best = max(result.probabilities, key=lambda s: result.probabilities[s])
        assert result.season is best

    def test_undertone_confidence_is_at_least_season_confidence(self) -> None:
        """웜/쿨 2분류는 4분류를 병합한 것이므로 항상 더 확신할 수 있다."""
        result = classify(extract_features(_uniform_patch((225, 184, 153))))
        assert result.undertone_confidence >= result.confidence - 1e-9

    def test_small_pixel_count_lowers_quality(self) -> None:
        rgb = (225, 184, 153)
        big = classify(extract_features(_uniform_patch(rgb, count=10_000)))
        small = classify(extract_features(_uniform_patch(rgb, count=300)))

        assert small.quality_factor < big.quality_factor
        assert any("피부 영역이 작습니다" in w for w in small.warnings)

    def test_deep_skin_is_not_classified_as_light_season(self) -> None:
        result = classify(extract_features(_uniform_patch((141, 85, 36))))
        assert result.season in {Season.AUTUMN_WARM, Season.WINTER_COOL}

    def test_calibration_config_shifts_boundary(self) -> None:
        """중립점을 옮기면 판정이 바뀌어야 한다 — 튜닝 가능성 검증."""
        features = extract_features(_uniform_patch((225, 184, 153)))

        warm_biased = classify(features, CalibrationConfig(hue_center=30.0))
        cool_biased = classify(features, CalibrationConfig(hue_center=90.0))

        assert warm_biased.undertone is Undertone.WARM
        assert cool_biased.undertone is Undertone.COOL

    def test_axes_are_reported_for_explainability(self) -> None:
        result = classify(extract_features(_uniform_patch((225, 184, 153))))
        names = {axis.name for axis in result.axes}
        assert names == {"undertone", "depth", "clarity"}
        for axis in result.axes:
            assert 0.0 <= axis.normalized <= 1.0
            assert axis.interpretation


class TestSeasonProfiles:
    @pytest.mark.parametrize("season", list(Season))
    def test_every_season_has_a_complete_profile(self, season: Season) -> None:
        profile = get_profile(season)
        assert profile.label_ko and profile.label_en
        assert len(profile.best_colors) >= 6
        assert len(profile.worst_colors) >= 3
        assert profile.styling_tips

    @pytest.mark.parametrize("season", list(Season))
    def test_palette_hex_codes_are_valid(self, season: Season) -> None:
        profile = get_profile(season)
        for _, hex_code in profile.best_colors + profile.worst_colors:
            assert hex_code.startswith("#") and len(hex_code) == 7
            int(hex_code[1:], 16)  # 파싱 실패 시 ValueError

    def test_undertone_mapping(self) -> None:
        assert Season.SPRING_WARM.undertone is Undertone.WARM
        assert Season.AUTUMN_WARM.undertone is Undertone.WARM
        assert Season.SUMMER_COOL.undertone is Undertone.COOL
        assert Season.WINTER_COOL.undertone is Undertone.COOL
