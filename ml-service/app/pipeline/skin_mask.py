"""피부 마스킹 — 랜드마크 폴리곤 제외 + YCrCb ∧ Otsu 이중 마스크.

## 원본에서 계승한 것

YCrCb `inRange` 마스크와 Otsu 이진화 마스크의 **교집합**은 2022년 원본이
잘한 부분이다 (docs/00-overview.md "원본이 잘한 것"). YCrCb만 쓰면 배경의
나무·벽돌 같은 피부 유사색이 통과하고, Otsu만 쓰면 밝기만 보므로 밝은
옷이 통과한다. 둘을 AND로 묶으면 "피부색 범위 안에 있으면서 동시에
얼굴 안에서 밝은 쪽"만 남는다.

## 원본에 추가한 것

1. **얼굴 타원(face oval) 경계** — 원본은 Haar의 사각형 crop을 그대로
   썼기 때문에 모서리의 배경·머리카락이 마스크에 섞였다. 랜드마크가
   그려주는 얼굴 윤곽 안쪽만 후보로 삼는다.
2. **눈·눈썹·입술 폴리곤 제외** — 원본이 하지 못해 P2의 원인이 된 부분.
   랜드마크 좌표로 각 부위의 볼록 껍질을 만들어 마스크에서 뺀다.
3. **Otsu 임계값을 얼굴 내부에서만 계산** — 이미지 전체로 계산하면
   배경 밝기가 임계값을 지배해 얼굴 안에서의 명암 분리가 무의미해진다.

## 검토했다 기각한 것 — 모폴로지 후처리

open/close 연산으로 마스크의 점 노이즈를 제거하는 것이 관행이지만
넣지 않았다. 커널 크기라는 튜닝 파라미터가 추가되는 데 비해, 하류의
특징 추출이 중앙값 + 절사 통계(features.py)라 흩어진 소수 오염 픽셀에
이미 견고하기 때문이다. 실측에서 필요가 증명되면 그때 추가한다.
"""

from __future__ import annotations

import functools
from dataclasses import dataclass

import cv2
import numpy as np
from numpy.typing import NDArray


@dataclass(frozen=True, slots=True)
class MaskConfig:
    """마스킹 임계값. 도메인의 CalibrationConfig와 같은 원칙 —
    매직 넘버를 코드에 흩뿌리지 않고 한 곳에 모아 튜닝 가능하게 한다."""

    ycrcb_lower: tuple[int, int, int] = (0, 133, 77)
    """YCrCb 피부 범위 하한 (Y, Cr, Cb).

    Cr 133~173, Cb 77~127은 Chai & Ngan(1999) 이래 피부 검출 문헌에서
    반복 검증된 범위로, 원본 프로젝트가 쓰던 값과 같은 계열이다.
    Y(휘도)는 제한하지 않는다 — 밝기 선별은 Otsu가 맡는 것이 원본
    이중 마스크 설계의 요점이기 때문이다.
    """

    ycrcb_upper: tuple[int, int, int] = (255, 173, 127)
    """YCrCb 피부 범위 상한."""

    exclusion_dilate_ratio: float = 0.04
    """눈·눈썹·입술 제외 영역을 얼굴 폭 대비 몇 배만큼 팽창시킬지.

    폴리곤 경계를 그대로 쓰면 속눈썹 그림자·아이라인·입술 경계의
    혼합 픽셀이 마스크에 남는다. 얼굴 폭의 4%(대략 눈썹 두께 정도)를
    바깥으로 밀어 안전 여유를 확보한다.
    """

    oval_erode_ratio: float = 0.03
    """얼굴 타원 경계를 안쪽으로 수축시키는 비율 (얼굴 폭 대비).

    타원 경계선 위 픽셀은 머리카락·배경과의 혼합 픽셀이다.
    경계를 3% 안쪽으로 당겨 헤어라인·턱선 오염을 걷어낸다.
    """


@dataclass(frozen=True, slots=True)
class SkinMask:
    """마스킹 결과. 최종 마스크뿐 아니라 중간 마스크를 전부 보존한다.

    프론트엔드의 "전처리 파이프라인 시각화"가 각 단계를 그대로 그리고,
    디버깅 시 어느 단계가 픽셀을 잘라냈는지 즉시 볼 수 있다.
    """

    mask: NDArray[np.bool_]
    """(H, W) 최종 피부 마스크 = oval ∧ ¬features ∧ ycrcb ∧ otsu."""

    face_oval: NDArray[np.bool_]
    """얼굴 타원 내부 (경계 수축 적용 후)."""

    feature_exclusion: NDArray[np.bool_]
    """제외된 눈·눈썹·입술 영역 (팽창 적용 후). True = 제외."""

    ycrcb: NDArray[np.bool_]
    """YCrCb 피부색 범위 마스크 (이미지 전체 기준)."""

    otsu: NDArray[np.bool_]
    """Otsu 밝은 쪽 마스크 (임계값은 얼굴 타원 내부에서 계산)."""

    otsu_threshold: float
    """얼굴 내부에서 계산된 Otsu 임계값 (0~255 그레이스케일)."""

    @property
    def coverage_ratio(self) -> float:
        """얼굴 타원 대비 최종 마스크의 비율. 입력 품질의 대리 지표.

        정상 정면 사진이면 0.4~0.8 수준이다. 이보다 훨씬 낮으면 조명이
        극단적이거나 마스킹이 실패한 것이므로 상위 계층이 경고를 띄운다.
        """
        oval_area = int(self.face_oval.sum())
        if oval_area == 0:
            return 0.0
        return float(self.mask.sum() / oval_area)


@functools.cache
def _region_indices() -> dict[str, tuple[int, ...]]:
    """MediaPipe 478-랜드마크 메시에서 부위별 인덱스 집합을 파생한다.

    인덱스를 하드코딩하지 않는 이유: 값이 (2026년 기준) 수년째 안정적이긴
    하지만, 라이브러리 상수에서 파생하면 "우리가 쓰는 모델"과 "우리가 쓰는
    인덱스"가 어긋날 가능성이 원천적으로 없다. 함수 안에서 임포트하는
    이유는 mediapipe 임포트가 무겁기 때문 — 순수 기하 테스트가 이 비용을
    치르지 않아도 된다 (결과는 캐시된다).
    """
    from mediapipe.tasks.python.vision.face_landmarker import (
        FaceLandmarksConnections as Connections,
    )

    def indices(connections: list[object]) -> tuple[int, ...]:
        points: set[int] = set()
        for conn in connections:
            points.add(int(conn.start))  # type: ignore[attr-defined]
            points.add(int(conn.end))  # type: ignore[attr-defined]
        return tuple(sorted(points))

    return {
        "face_oval": indices(Connections.FACE_LANDMARKS_FACE_OVAL),
        "left_eye": indices(Connections.FACE_LANDMARKS_LEFT_EYE),
        "right_eye": indices(Connections.FACE_LANDMARKS_RIGHT_EYE),
        "left_eyebrow": indices(Connections.FACE_LANDMARKS_LEFT_EYEBROW),
        "right_eyebrow": indices(Connections.FACE_LANDMARKS_RIGHT_EYEBROW),
        "lips": indices(Connections.FACE_LANDMARKS_LIPS),
    }


# 마스크에서 제외할 부위. 코는 제외하지 않는다 — 콧등·콧볼은 피부이고,
# 어두운 콧구멍은 Otsu 마스크가 걸러낸다.
_EXCLUDED_REGIONS: tuple[str, ...] = (
    "left_eye",
    "right_eye",
    "left_eyebrow",
    "right_eyebrow",
    "lips",
)


def _fill_convex_hull(
    canvas: NDArray[np.uint8], points: NDArray[np.float64]
) -> NDArray[np.uint8]:
    """점 집합의 볼록 껍질을 캔버스에 255로 채운다.

    랜드마크 연결선은 순서가 보장되지 않으므로 다각형으로 바로 그릴 수
    없다. 눈·입·얼굴 윤곽은 모두 볼록에 가까운 형태라 볼록 껍질이
    안전한 근사가 된다.
    """
    hull = cv2.convexHull(points.astype(np.int32))
    cv2.fillConvexPoly(canvas, hull, color=255)
    return canvas


def _ellipse_kernel(size_px: int) -> NDArray[np.uint8]:
    side = max(3, size_px * 2 + 1)  # 홀수 보장
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (side, side))
    return np.asarray(kernel, dtype=np.uint8)


def build_face_oval_mask(
    landmarks: NDArray[np.float64],
    image_shape: tuple[int, int],
    *,
    erode_px: int = 0,
) -> NDArray[np.bool_]:
    """얼굴 윤곽 랜드마크로 얼굴 타원 마스크를 만든다."""
    height, width = image_shape
    oval_points = landmarks[list(_region_indices()["face_oval"])]

    canvas = np.zeros((height, width), dtype=np.uint8)
    _fill_convex_hull(canvas, oval_points)

    if erode_px > 0:
        canvas = np.asarray(cv2.erode(canvas, _ellipse_kernel(erode_px)), dtype=np.uint8)
    return canvas > 0


def build_feature_exclusion_mask(
    landmarks: NDArray[np.float64],
    image_shape: tuple[int, int],
    *,
    dilate_px: int = 0,
) -> NDArray[np.bool_]:
    """눈·눈썹·입술을 덮는 제외 마스크를 만든다. True = 제외 대상."""
    height, width = image_shape
    regions = _region_indices()

    canvas = np.zeros((height, width), dtype=np.uint8)
    for name in _EXCLUDED_REGIONS:
        _fill_convex_hull(canvas, landmarks[list(regions[name])])

    if dilate_px > 0:
        canvas = np.asarray(cv2.dilate(canvas, _ellipse_kernel(dilate_px)), dtype=np.uint8)
    return canvas > 0


def build_ycrcb_mask(
    image_rgb: NDArray[np.uint8], config: MaskConfig
) -> NDArray[np.bool_]:
    """YCrCb 색공간에서 피부색 범위 마스크를 만든다 (원본 방식 계승)."""
    ycrcb = cv2.cvtColor(image_rgb, cv2.COLOR_RGB2YCrCb)
    in_range = cv2.inRange(
        ycrcb, np.array(config.ycrcb_lower), np.array(config.ycrcb_upper)
    )
    return in_range > 0


def build_otsu_mask(
    image_rgb: NDArray[np.uint8], face_oval: NDArray[np.bool_]
) -> tuple[NDArray[np.bool_], float]:
    """Otsu 이진화로 '얼굴 안에서 밝은 쪽' 마스크를 만든다 (원본 방식 계승).

    원본과의 차이: 임계값을 이미지 전체가 아니라 **얼굴 타원 내부
    픽셀만으로** 계산한다. 전체로 계산하면 배경의 밝기 분포가 임계값을
    지배해서, 어두운 배경 앞 얼굴은 통째로 '밝은 쪽'이 되고 밝은 배경
    앞 얼굴은 통째로 '어두운 쪽'이 된다 — 얼굴 안의 명암 분리라는
    본래 목적이 무의미해진다.

    Returns:
        (마스크, 사용된 임계값). 얼굴 픽셀이 없으면 빈 마스크와 0.0.
    """
    gray = cv2.cvtColor(image_rgb, cv2.COLOR_RGB2GRAY)

    face_pixels = gray[face_oval]
    if face_pixels.size == 0:
        return np.zeros_like(face_oval), 0.0

    threshold, _ = cv2.threshold(
        face_pixels.reshape(-1, 1), 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU
    )
    return gray >= threshold, float(threshold)


def build_skin_mask(
    image_rgb: NDArray[np.uint8],
    landmarks: NDArray[np.float64],
    config: MaskConfig | None = None,
) -> SkinMask:
    """네 개의 마스크를 조합해 최종 피부 마스크를 만든다.

        skin = face_oval ∧ ¬features ∧ ycrcb ∧ otsu

    Args:
        image_rgb: (H, W, 3) uint8 RGB (화이트밸런스 보정 후).
        landmarks: (478, 2) 픽셀 좌표 — DetectedFace.landmarks.
        config: 마스킹 임계값. 생략하면 기본값.

    Returns:
        중간 마스크를 전부 보존한 SkinMask.
    """
    cfg = config or MaskConfig()
    height, width = image_rgb.shape[:2]

    # 제외/수축 크기는 절대 픽셀이 아니라 얼굴 폭에 비례시킨다 —
    # 같은 사진을 리사이즈해도 마스킹 결과가 (비율적으로) 같아야 한다.
    face_width = float(np.ptp(landmarks[:, 0]))
    dilate_px = max(1, round(face_width * cfg.exclusion_dilate_ratio))
    erode_px = max(1, round(face_width * cfg.oval_erode_ratio))

    face_oval = build_face_oval_mask(
        landmarks, (height, width), erode_px=erode_px
    )
    feature_exclusion = build_feature_exclusion_mask(
        landmarks, (height, width), dilate_px=dilate_px
    )
    ycrcb = build_ycrcb_mask(image_rgb, cfg)
    otsu, otsu_threshold = build_otsu_mask(image_rgb, face_oval)

    mask = face_oval & ~feature_exclusion & ycrcb & otsu

    return SkinMask(
        mask=mask,
        face_oval=face_oval,
        feature_exclusion=feature_exclusion,
        ycrcb=ycrcb,
        otsu=otsu,
        otsu_threshold=otsu_threshold,
    )


def extract_skin_pixels(
    image_rgb: NDArray[np.uint8], skin_mask: SkinMask
) -> NDArray[np.uint8]:
    """마스크가 True인 픽셀만 (N, 3) 배열로 뽑는다.

    여기가 공간 정보가 사라지는 지점이다. 이 함수의 출력부터는
    얼굴형·윤곽이 존재하지 않는다 (P2 대응의 핵심).
    """
    return image_rgb[skin_mask.mask]
