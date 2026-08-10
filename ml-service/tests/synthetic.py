"""테스트 공용 합성 얼굴 픽스처.

실제 사람 사진을 저장소에 넣을 수 없으므로 (CLAUDE.md 금지사항)
피부색 타원 + 부위 도형으로 얼굴을 합성하고, MediaPipe의 실제 부위
인덱스 집합에 맞춰 478개 가짜 랜드마크를 배치한다.

눈·입술을 일부러 피부색으로 칠하는 이유: 색으로도 밝기로도 걸러질 수
없게 해서, 오직 랜드마크 폴리곤 제외만이 그 픽셀을 걷어낼 수 있음을
증명하기 위해서다.
"""

from __future__ import annotations

import numpy as np
from numpy.typing import NDArray

from app.pipeline.skin_mask import _region_indices

# 딥 웜 계열 (h°=68.4 — 중립점 62°에서 확실히 웜 쪽). 경계 색을 픽스처로
# 쓰면 분류 단정이 흔들리므로, 판정이 명백한 색을 고른다.
# README 실측 예시의 가을 웜 행과 같은 값이다.
SKIN = (198, 134, 66)
DARK = (55, 40, 35)
HEIGHT, WIDTH = 240, 320
CENTER = (160.0, 120.0)  # (cx, cy)
AXES = (75.0, 95.0)  # (가로 반경, 세로 반경)

# 합성 얼굴의 부위 배치 (cx, cy, rx, ry)
LEFT_EYE = (125.0, 95.0, 14.0, 8.0)
RIGHT_EYE = (195.0, 95.0, 14.0, 8.0)
LEFT_BROW = (125.0, 75.0, 18.0, 5.0)
RIGHT_BROW = (195.0, 75.0, 18.0, 5.0)
LIPS = (160.0, 170.0, 22.0, 10.0)


def place_on_ellipse(
    indices: tuple[int, ...],
    landmarks: NDArray[np.float64],
    cx: float,
    cy: float,
    rx: float,
    ry: float,
) -> None:
    """지정된 인덱스의 랜드마크를 타원 둘레에 균등 배치한다."""
    angles = np.linspace(0.0, 2.0 * np.pi, num=len(indices), endpoint=False)
    for idx, theta in zip(indices, angles, strict=True):
        landmarks[idx] = (cx + rx * np.cos(theta), cy + ry * np.sin(theta))


def fake_landmarks() -> NDArray[np.float64]:
    """478개 랜드마크를 합성 얼굴의 부위 위치에 맞춰 배치한다."""
    regions = _region_indices()

    # 기본값: 모든 점을 얼굴 타원 경계에 깔아 볼록 껍질이 타원이 되게 한다.
    landmarks = np.zeros((478, 2), dtype=np.float64)
    place_on_ellipse(tuple(range(478)), landmarks, *CENTER, *AXES)

    place_on_ellipse(regions["face_oval"], landmarks, *CENTER, *AXES)
    place_on_ellipse(regions["left_eye"], landmarks, *LEFT_EYE)
    place_on_ellipse(regions["right_eye"], landmarks, *RIGHT_EYE)
    place_on_ellipse(regions["left_eyebrow"], landmarks, *LEFT_BROW)
    place_on_ellipse(regions["right_eyebrow"], landmarks, *RIGHT_BROW)
    place_on_ellipse(regions["lips"], landmarks, *LIPS)
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


def synthetic_face(
    *, background: tuple[int, int, int] = (95, 105, 125)
) -> NDArray[np.uint8]:
    """피부색 타원 얼굴. 눈·입술은 피부색(폴리곤 제외 검증용),
    눈썹은 어두움(Otsu 검증용), 배경은 푸른 회색(YCrCb 검증용)."""
    rng = np.random.default_rng(seed=5)
    image = np.zeros((HEIGHT, WIDTH, 3), dtype=np.float64)
    image[:] = background

    _draw_ellipse(image, SKIN, *CENTER, *AXES)
    _draw_ellipse(image, DARK, *LEFT_BROW)
    _draw_ellipse(image, DARK, *RIGHT_BROW)
    # 눈·입술을 일부러 피부색으로 남겨둔다 — 모듈 docstring 참조.

    image += rng.normal(0.0, 3.0, size=image.shape)
    return np.clip(image, 0, 255).astype(np.uint8)
