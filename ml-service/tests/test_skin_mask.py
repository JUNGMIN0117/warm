"""피부 마스킹 테스트.

실제 사진 없이 검증하기 위해 합성 얼굴을 만든다 — 피부색 타원 + 어두운
눈썹 + (일부러) 피부색으로 칠한 눈·입술. 눈·입술을 피부색으로 칠하는
이유: 색으로도 밝기로도 걸러질 수 없게 해서, 오직 랜드마크 폴리곤 제외만이
그 픽셀을 걷어낼 수 있음을 증명하기 위해서다.

가짜 랜드마크는 실제 MediaPipe 인덱스 집합(_region_indices)에 맞춰
배치한다. 인덱스가 라이브러리와 어긋나면 테스트가 깨지도록.
"""

from __future__ import annotations

import numpy as np
import pytest
from numpy.typing import NDArray

from app.pipeline.skin_mask import (
    MaskConfig,
    _region_indices,
    build_face_oval_mask,
    build_feature_exclusion_mask,
    build_otsu_mask,
    build_skin_mask,
    build_ycrcb_mask,
    extract_skin_pixels,
)

_SKIN = (224, 172, 138)
_DARK = (55, 40, 35)
_H, _W = 240, 320
_CENTER = (160.0, 120.0)  # (cx, cy)
_AXES = (75.0, 95.0)  # (가로 반경, 세로 반경)

# 합성 얼굴의 부위 배치 (cx, cy, rx, ry)
_LEFT_EYE = (125.0, 95.0, 14.0, 8.0)
_RIGHT_EYE = (195.0, 95.0, 14.0, 8.0)
_LEFT_BROW = (125.0, 75.0, 18.0, 5.0)
_RIGHT_BROW = (195.0, 75.0, 18.0, 5.0)
_LIPS = (160.0, 170.0, 22.0, 10.0)


def _place_on_ellipse(
    indices: tuple[int, ...],
    landmarks: NDArray[np.float64],
    cx: float,
    cy: float,
    rx: float,
    ry: float,
) -> None:
    angles = np.linspace(0.0, 2.0 * np.pi, num=len(indices), endpoint=False)
    for idx, theta in zip(indices, angles, strict=True):
        landmarks[idx] = (cx + rx * np.cos(theta), cy + ry * np.sin(theta))


def _fake_landmarks() -> NDArray[np.float64]:
    """478개 랜드마크를 합성 얼굴의 부위 위치에 맞춰 배치한다."""
    regions = _region_indices()

    # 기본값: 모든 점을 얼굴 타원 경계에 깔아 볼록 껍질이 타원이 되게 한다.
    landmarks = np.zeros((478, 2), dtype=np.float64)
    all_indices = tuple(range(478))
    _place_on_ellipse(all_indices, landmarks, *_CENTER, *_AXES)

    _place_on_ellipse(regions["face_oval"], landmarks, *_CENTER, *_AXES)
    _place_on_ellipse(regions["left_eye"], landmarks, *_LEFT_EYE)
    _place_on_ellipse(regions["right_eye"], landmarks, *_RIGHT_EYE)
    _place_on_ellipse(regions["left_eyebrow"], landmarks, *_LEFT_BROW)
    _place_on_ellipse(regions["right_eyebrow"], landmarks, *_RIGHT_BROW)
    _place_on_ellipse(regions["lips"], landmarks, *_LIPS)
    return landmarks


def _draw_ellipse(
    image: NDArray[np.float64],
    color: tuple[float, ...],
    cx: float,
    cy: float,
    rx: float,
    ry: float,
) -> None:
    yy, xx = np.mgrid[0 : image.shape[0], 0 : image.shape[1]]
    inside = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1.0
    image[inside] = color


def _synthetic_face(*, background: tuple[int, int, int] = (95, 105, 125)) -> NDArray[np.uint8]:
    """피부색 타원 얼굴. 눈·입술은 피부색(폴리곤 제외 검증용),
    눈썹은 어두움(Otsu 검증용), 배경은 푸른 회색(YCrCb 검증용)."""
    rng = np.random.default_rng(seed=5)
    image = np.zeros((_H, _W, 3), dtype=np.float64)
    image[:] = background

    _draw_ellipse(image, _SKIN, *_CENTER, *_AXES)
    _draw_ellipse(image, _DARK, *_LEFT_BROW)
    _draw_ellipse(image, _DARK, *_RIGHT_BROW)
    # 눈·입술을 일부러 피부색으로 남겨둔다 — docstring 참조.

    image += rng.normal(0.0, 3.0, size=image.shape)
    return np.clip(image, 0, 255).astype(np.uint8)


@pytest.fixture(scope="module")
def landmarks() -> NDArray[np.float64]:
    return _fake_landmarks()


class TestComponentMasks:
    def test_ycrcb_accepts_skin_rejects_background(self, landmarks: NDArray[np.float64]) -> None:
        image = _synthetic_face()
        mask = build_ycrcb_mask(image, MaskConfig())

        assert mask[120, 160]  # 얼굴 중심 = 피부
        assert not mask[10, 10]  # 배경
        assert not mask[230, 300]

    def test_face_oval_contains_center_not_corners(
        self, landmarks: NDArray[np.float64]
    ) -> None:
        oval = build_face_oval_mask(landmarks, (_H, _W), erode_px=3)

        assert oval[120, 160]
        assert not oval[10, 10]

    def test_oval_erosion_shrinks_area(self, landmarks: NDArray[np.float64]) -> None:
        loose = build_face_oval_mask(landmarks, (_H, _W), erode_px=0)
        tight = build_face_oval_mask(landmarks, (_H, _W), erode_px=6)

        assert tight.sum() < loose.sum()
        assert not np.any(tight & ~loose)  # 수축 마스크는 원본의 부분집합

    def test_feature_exclusion_covers_eyes_and_lips(
        self, landmarks: NDArray[np.float64]
    ) -> None:
        exclusion = build_feature_exclusion_mask(landmarks, (_H, _W), dilate_px=2)

        for cx, cy, _, _ in (_LEFT_EYE, _RIGHT_EYE, _LIPS, _LEFT_BROW, _RIGHT_BROW):
            assert exclusion[int(cy), int(cx)], f"({cx}, {cy})가 제외되지 않음"
        assert not exclusion[120, 160]  # 이마·볼은 제외 대상이 아님

    def test_otsu_threshold_is_computed_inside_face_only(
        self, landmarks: NDArray[np.float64]
    ) -> None:
        """순백 배경(그레이 ≈250)이 임계값을 끌어올리면 안 된다.

        이미지 전체로 Otsu를 계산하면 임계값이 피부(≈180)와 배경(250)
        사이(≈215)에 잡혀 얼굴 전체가 '어두운 쪽'으로 버려진다.
        얼굴 내부만 쓰면 어두운 눈썹과 피부 사이에 잡혀야 한다.
        """
        image = _synthetic_face(background=(250, 250, 250))
        oval = build_face_oval_mask(landmarks, (_H, _W))

        _, threshold = build_otsu_mask(image, oval)

        assert 40.0 < threshold < 170.0  # 눈썹(~45)과 피부(~180) 사이

    def test_otsu_with_empty_oval_returns_empty(self) -> None:
        image = _synthetic_face()
        empty_oval = np.zeros((_H, _W), dtype=np.bool_)

        mask, threshold = build_otsu_mask(image, empty_oval)

        assert not mask.any()
        assert threshold == 0.0


class TestComposedMask:
    def test_final_mask_stays_inside_face(self, landmarks: NDArray[np.float64]) -> None:
        image = _synthetic_face()
        result = build_skin_mask(image, landmarks)

        oval_loose = build_face_oval_mask(landmarks, (_H, _W), erode_px=0)
        assert not np.any(result.mask & ~oval_loose)

    def test_skin_colored_eyes_are_removed_by_polygon_only(
        self, landmarks: NDArray[np.float64]
    ) -> None:
        """눈·입술이 피부와 같은 색이어도 폴리곤 제외가 걷어내야 한다.

        원본 파이프라인(색·밝기 마스크만)은 이 픽셀들을 통과시켰다 —
        홍채·입술이 피부 통계에 섞이는 것이 P2와 함께 원본 판정을
        오염시킨 요인이다.
        """
        image = _synthetic_face()
        result = build_skin_mask(image, landmarks)

        for cx, cy, _, _ in (_LEFT_EYE, _RIGHT_EYE, _LIPS):
            assert not result.mask[int(cy), int(cx)], f"({cx}, {cy})가 마스크에 남음"

    def test_dark_brows_are_removed(self, landmarks: NDArray[np.float64]) -> None:
        image = _synthetic_face()
        result = build_skin_mask(image, landmarks)

        for cx, cy, _, _ in (_LEFT_BROW, _RIGHT_BROW):
            assert not result.mask[int(cy), int(cx)]

    def test_coverage_ratio_is_sane(self, landmarks: NDArray[np.float64]) -> None:
        image = _synthetic_face()
        result = build_skin_mask(image, landmarks)

        assert 0.3 < result.coverage_ratio < 0.98

    def test_extracted_pixels_match_skin_color(
        self, landmarks: NDArray[np.float64]
    ) -> None:
        """추출된 픽셀의 중앙값이 칠해둔 피부색과 일치해야 한다 —
        파이프라인 전체의 목적에 대한 종단 검증."""
        image = _synthetic_face()
        result = build_skin_mask(image, landmarks)
        pixels = extract_skin_pixels(image, result)

        assert pixels.ndim == 2 and pixels.shape[1] == 3
        assert pixels.shape[0] > 5_000

        median = np.median(pixels, axis=0)
        np.testing.assert_allclose(median, _SKIN, atol=4.0)

    def test_all_stage_masks_are_reported(self, landmarks: NDArray[np.float64]) -> None:
        """프론트 시각화가 의존하는 구조 계약."""
        image = _synthetic_face()
        result = build_skin_mask(image, landmarks)

        for stage in (result.face_oval, result.feature_exclusion, result.ycrcb, result.otsu):
            assert stage.shape == (_H, _W)
            assert stage.dtype == np.bool_
        assert result.otsu_threshold > 0.0
