"""색공간 변환.

퍼스널 컬러 판정은 "이 피부가 노란기인가 붉은기인가"를 묻는 문제다.
sRGB 좌표계는 이 질문에 답하기에 부적합하다. RGB에서의 유클리드 거리는
사람이 느끼는 색 차이와 비례하지 않기 때문이다.

CIELab은 인간의 색 지각을 근사하도록 설계된 균등 색공간으로,
- L* : 명도 (0=흑, 100=백)
- a* : 녹(-) ↔ 적(+)
- b* : 청(-) ↔ 황(+)
세 축이 서로 독립적이다. 즉 b*축 하나만으로 언더톤(노란기/푸른기)을
읽어낼 수 있다. 이것이 이 프로젝트가 Lab을 1차 좌표계로 삼는 이유다.

변환 경로: sRGB(감마) → 선형 RGB → CIEXYZ → CIELab (D65 백색점)
"""

from __future__ import annotations

import numpy as np
from numpy.typing import NDArray

# CIE 표준광 D65 (주광 6504K) 기준 백색점. sRGB 규격이 채택한 백색점이다.
D65_WHITE_POINT = np.array([95.047, 100.000, 108.883], dtype=np.float64)

# sRGB(IEC 61966-2-1) → CIEXYZ 변환 행렬 (D65)
SRGB_TO_XYZ = np.array(
    [
        [0.4124564, 0.3575761, 0.1804375],
        [0.2126729, 0.7151522, 0.0721750],
        [0.0193339, 0.1191920, 0.9503041],
    ],
    dtype=np.float64,
)

# CIELab 변환에 쓰이는 상수. delta = 6/29
_DELTA = 6.0 / 29.0
_DELTA_CUBED = _DELTA**3
_DELTA_SQ_TIMES_3 = 3.0 * _DELTA**2


def srgb_to_linear(rgb: NDArray[np.floating]) -> NDArray[np.float64]:
    """sRGB 감마 곡선을 제거해 선형 RGB로 되돌린다.

    sRGB에 저장된 값은 이미 감마 인코딩된 상태다. 이걸 풀지 않고
    XYZ 행렬을 곱하면 밝기가 체계적으로 왜곡되고, 결과적으로 L*이 틀어져
    "봄 웜"과 "가을 웜"을 가르는 명도 판정이 통째로 무너진다.

    Args:
        rgb: 0.0~1.0으로 정규화된 sRGB 값. 마지막 축이 채널(3).

    Returns:
        같은 형태의 선형 RGB 배열.
    """
    rgb = np.asarray(rgb, dtype=np.float64)
    return np.where(rgb <= 0.04045, rgb / 12.92, ((rgb + 0.055) / 1.055) ** 2.4)


def linear_to_xyz(linear_rgb: NDArray[np.floating]) -> NDArray[np.float64]:
    """선형 RGB를 CIEXYZ로 변환한다. 결과 스케일은 0~100."""
    linear_rgb = np.asarray(linear_rgb, dtype=np.float64)
    return linear_rgb @ SRGB_TO_XYZ.T * 100.0


def xyz_to_lab(xyz: NDArray[np.floating]) -> NDArray[np.float64]:
    """CIEXYZ를 CIELab으로 변환한다 (D65 기준)."""
    xyz = np.asarray(xyz, dtype=np.float64)
    normalized = xyz / D65_WHITE_POINT

    # f(t): 세제곱근 곡선. 아주 어두운 영역에서 기울기가 발산하는 것을
    # 막기 위해 delta^3 미만에서는 선형 구간으로 대체한다.
    f = np.where(
        normalized > _DELTA_CUBED,
        np.cbrt(normalized),
        normalized / _DELTA_SQ_TIMES_3 + 4.0 / 29.0,
    )

    fx, fy, fz = f[..., 0], f[..., 1], f[..., 2]
    return np.stack(
        [116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)],
        axis=-1,
    )


def rgb_to_lab(rgb_u8: NDArray[np.integer] | NDArray[np.floating]) -> NDArray[np.float64]:
    """8비트 sRGB를 CIELab으로 변환하는 최상위 함수.

    Args:
        rgb_u8: 0~255 범위의 RGB 배열. (..., 3) 형태면 무엇이든 받는다.
                단일 픽셀 (3,), 픽셀 목록 (N, 3), 이미지 (H, W, 3) 모두 가능.

    Returns:
        같은 형태의 Lab 배열. L*은 0~100, a*/b*는 대략 -128~127.
    """
    rgb = np.asarray(rgb_u8, dtype=np.float64) / 255.0
    return xyz_to_lab(linear_to_xyz(srgb_to_linear(rgb)))


def rgb_to_ycrcb(rgb_u8: NDArray[np.integer] | NDArray[np.floating]) -> NDArray[np.float64]:
    """8비트 sRGB를 YCrCb(ITU-R BT.601)로 변환한다.

    원본 프로젝트가 피부 마스킹에 쓰던 색공간이라 호환을 위해 유지한다.
    휘도(Y)와 색차(Cr, Cb)를 분리하기 때문에 조명 변화에 비교적 둔감해
    "피부인가 아닌가"를 가르는 이진 판정에는 여전히 유효하다.
    다만 균등 색공간이 아니므로 언더톤의 '정도'를 재는 데는 쓰지 않는다.
    """
    rgb = np.asarray(rgb_u8, dtype=np.float64)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]

    y = 0.299 * r + 0.587 * g + 0.114 * b
    cr = (r - y) * 0.713 + 128.0
    cb = (b - y) * 0.564 + 128.0
    return np.stack([y, cr, cb], axis=-1)


def lab_to_lch(lab: NDArray[np.floating]) -> NDArray[np.float64]:
    """CIELab 직교좌표를 LCh 극좌표로 변환한다.

    a*/b* 평면을 극좌표로 바꾸면 두 값이 나온다.
    - C* (chroma)  : 원점에서의 거리 = 색의 선명함
    - h° (hue)     : 각도 = 색상 그 자체

    퍼스널 컬러의 두 축이 정확히 이 둘에 대응한다.
    h°는 웜/쿨(언더톤)을, C*는 클리어/뮤트(선명도)를 결정한다.

    Returns:
        (..., 3) 형태의 [L*, C*, h°]. h°는 0~360도.
    """
    lab = np.asarray(lab, dtype=np.float64)
    lightness, a_star, b_star = lab[..., 0], lab[..., 1], lab[..., 2]

    chroma = np.hypot(a_star, b_star)
    hue = np.degrees(np.arctan2(b_star, a_star)) % 360.0
    return np.stack([lightness, chroma, hue], axis=-1)
