"""화이트밸런스 단위 테스트.

실제 사진 없이 합성 이미지로 검증한다 (실존 인물 사진은 저장소에 넣지
않는다 — CLAUDE.md 금지사항). 색 캐스트는 선형 공간에서 채널별 게인을
곱해 시뮬레이션한다. 실제 조명 변화가 카메라 센서에 미치는 영향과
동일한 물리 모델이다.
"""

from __future__ import annotations

import numpy as np
import pytest
from numpy.typing import NDArray

from app.domain.color_space import linear_to_srgb, srgb_to_linear
from app.pipeline import WhiteBalanceMethod, WhiteBalanceResult, apply_white_balance

# 합성 장면에 쓰는 대표 피부색 (README 실측 예시와 동일한 값)
_SKIN_TONES: tuple[tuple[int, int, int], ...] = (
    (243, 213, 165),
    (232, 196, 192),
    (198, 134, 66),
    (160, 112, 95),
)


def _make_scene(*, with_white_patch: bool = False) -> NDArray[np.uint8]:
    """피부 패치 + 회색 배경으로 구성된 합성 장면을 만든다.

    Gray-World 가정(장면 평균 ≈ 무채색)이 성립하도록 중립 회색 배경을
    깔고, 그 위에 피부색 사각형들을 얹는다.
    """
    rng = np.random.default_rng(seed=7)
    scene = np.full((120, 160, 3), 128, dtype=np.float64)
    scene += rng.normal(0.0, 4.0, size=scene.shape)  # 완전 균일 입력 방지

    for i, tone in enumerate(_SKIN_TONES):
        x0 = 10 + i * 38
        scene[20:60, x0 : x0 + 30] = tone

    if with_white_patch:
        # 250이 아니라 230인 이유: 테스트가 캐스트(×1.15)를 곱했을 때
        # 선형값이 1.0을 넘어 클리핑되면 캐스트가 비가역이 되기 때문.
        scene[80:110, 20:60] = 230.0

    return np.clip(scene, 0, 255).astype(np.uint8)


def _apply_cast(
    image: NDArray[np.uint8], gains: tuple[float, float, float]
) -> NDArray[np.uint8]:
    """선형 공간에서 채널 게인을 곱해 색 캐스트를 시뮬레이션한다."""
    linear = srgb_to_linear(image.astype(np.float64) / 255.0)
    casted = np.clip(linear * np.array(gains), 0.0, 1.0)
    return (np.clip(linear_to_srgb(casted), 0.0, 1.0) * 255.0 + 0.5).astype(np.uint8)


def _channel_mean_spread(image: NDArray[np.uint8]) -> float:
    """선형 공간 채널 평균의 최대-최소 차. 0에 가까울수록 중립."""
    linear = srgb_to_linear(image.astype(np.float64) / 255.0)
    means = linear.reshape(-1, 3).mean(axis=0)
    return float(means.max() - means.min())


class TestGrayWorld:
    def test_removes_warm_cast(self) -> None:
        """백열등급 웜 캐스트(R↑ B↓)가 제거되어야 한다."""
        scene = _make_scene()
        casted = _apply_cast(scene, (1.25, 1.0, 0.75))

        result = apply_white_balance(casted)

        assert _channel_mean_spread(result.image) < _channel_mean_spread(casted) * 0.2

    def test_removes_cool_cast(self) -> None:
        scene = _make_scene()
        casted = _apply_cast(scene, (0.8, 1.0, 1.2))

        result = apply_white_balance(casted)

        assert _channel_mean_spread(result.image) < _channel_mean_spread(casted) * 0.2

    def test_gain_direction_opposes_cast(self) -> None:
        """R이 부풀려진 입력이면 R 게인은 1보다 작아야 한다."""
        casted = _apply_cast(_make_scene(), (1.3, 1.0, 0.8))
        result = apply_white_balance(casted)

        r_gain, _, b_gain = result.gains
        assert r_gain < 1.0 < b_gain

    def test_neutral_image_is_barely_touched(self) -> None:
        """이미 중립인 이미지는 거의 그대로 나와야 한다 (멱등성에 가까움).

        주의: 피부 패치가 큰 장면은 이 테스트에 쓸 수 없다. Gray-World는
        피부의 웜기 자체를 조명으로 오인하기 때문이다 — 그 한계는
        test_estimation_mask_ignores_excluded_region이 다룬다.
        """
        rng = np.random.default_rng(seed=11)
        neutral = np.clip(
            128.0 + rng.normal(0.0, 12.0, size=(80, 80, 3)), 0, 255
        ).astype(np.uint8)

        result = apply_white_balance(neutral)

        for gain in result.gains:
            assert gain == pytest.approx(1.0, abs=0.05)
        assert result.cast_strength < 0.1

    def test_black_image_does_not_crash(self) -> None:
        """조명을 추정할 수 없는 입력은 무보정으로 통과시킨다."""
        black = np.zeros((32, 32, 3), dtype=np.uint8)
        result = apply_white_balance(black)

        assert result.gains == (1.0, 1.0, 1.0)
        assert np.array_equal(result.image, black)

    def test_gains_are_bounded(self) -> None:
        """단색 이미지처럼 가정이 깨진 입력에서도 게인은 상한을 넘지 않는다."""
        pure_red = np.zeros((32, 32, 3), dtype=np.uint8)
        pure_red[..., 0] = 200

        result = apply_white_balance(pure_red)

        for gain in result.gains:
            assert 0.5 <= gain <= 2.0


class TestWhitePatch:
    def test_restores_cast_when_white_exists(self) -> None:
        """흰 패치가 있는 장면이라면 White-Patch도 캐스트를 제거할 수 있다.

        캐스트 게인을 1.15 이하로 두는 이유: 밝은 패치가 캐스트 단계에서
        클리핑(선형값 1.0 초과)되면 원본 정보가 소실되어 어떤 알고리즘도
        복원할 수 없다. 테스트하려는 것은 '복원 능력'이지 '소실된 정보의
        창조'가 아니다.
        """
        scene = _make_scene(with_white_patch=True)
        casted = _apply_cast(scene, (1.15, 1.0, 0.85))

        result = apply_white_balance(casted, WhiteBalanceMethod.WHITE_PATCH)

        assert _channel_mean_spread(result.image) < _channel_mean_spread(casted) * 0.35
        assert result.method is WhiteBalanceMethod.WHITE_PATCH


class TestEstimationMask:
    def test_estimation_mask_ignores_excluded_region(self) -> None:
        """피부가 프레임 절반을 채워도, 추정을 배경으로 제한하면 흔들리지 않아야 한다.

        Gray-World의 핵심 한계에 대한 회귀 테스트다. 피부(웜 색)가 크게
        잡힌 사진에서 전체 평균으로 조명을 추정하면 피부의 웜기 자체가
        '주황 조명'으로 오인되어 제거된다 — 우리가 측정하려는 신호가
        전처리에서 지워지는 셈이다.
        """
        rng = np.random.default_rng(seed=13)
        scene = np.clip(
            128.0 + rng.normal(0.0, 8.0, size=(100, 100, 3)), 0, 255
        ).astype(np.uint8)
        scene[:, 50:] = (198, 134, 66)  # 오른쪽 절반이 딥 웜 피부

        background_only = np.zeros((100, 100), dtype=np.bool_)
        background_only[:, :50] = True

        full_frame = apply_white_balance(scene)
        masked = apply_white_balance(scene, estimation_mask=background_only)

        # 배경은 중립이므로 마스크 추정 게인은 1 근처여야 하고,
        # 전체 프레임 추정은 피부에 끌려 1에서 멀어진다.
        masked_deviation = max(abs(g - 1.0) for g in masked.gains)
        full_deviation = max(abs(g - 1.0) for g in full_frame.gains)
        assert masked_deviation < 0.05
        assert full_deviation > masked_deviation

    def test_tiny_mask_falls_back_to_full_frame(self) -> None:
        """표본이 1% 미만이면 전체 이미지 추정과 같은 결과여야 한다."""
        scene = _make_scene()
        tiny = np.zeros(scene.shape[:2], dtype=np.bool_)
        tiny[0, 0] = True  # 1픽셀

        assert apply_white_balance(scene, estimation_mask=tiny).gains == (
            apply_white_balance(scene).gains
        )

    def test_mask_shape_mismatch_raises(self) -> None:
        scene = _make_scene()
        with pytest.raises(ValueError, match="마스크"):
            apply_white_balance(
                scene, estimation_mask=np.ones((10, 10), dtype=np.bool_)
            )


class TestContract:
    """파이프라인의 다른 단계가 의존하는 입출력 계약."""

    def test_output_shape_and_dtype_preserved(self) -> None:
        scene = _make_scene()
        result = apply_white_balance(scene)

        assert isinstance(result, WhiteBalanceResult)
        assert result.image.shape == scene.shape
        assert result.image.dtype == np.uint8

    def test_rejects_wrong_shape(self) -> None:
        with pytest.raises(ValueError, match=r"\(H, W, 3\)"):
            apply_white_balance(np.zeros((32, 32), dtype=np.uint8))

    def test_rejects_wrong_dtype(self) -> None:
        with pytest.raises(ValueError, match="uint8"):
            apply_white_balance(np.zeros((32, 32, 3), dtype=np.float64))

    def test_input_is_not_mutated(self) -> None:
        """원본 이미지를 절대 변경하지 않는다 — 단계별 시각화가 원본을 보존해야 한다."""
        scene = _make_scene()
        snapshot = scene.copy()
        casted = _apply_cast(scene, (1.2, 1.0, 0.8))

        apply_white_balance(casted)

        assert np.array_equal(scene, snapshot)
