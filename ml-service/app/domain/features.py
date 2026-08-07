"""피부 픽셀 집합에서 퍼스널 컬러 판정용 특징을 뽑아낸다.

핵심 설계 판단이 하나 있다. **평균이 아니라 중앙값을 쓴다.**

마스킹을 아무리 정교하게 해도 피부 영역에는 잔여 오염이 남는다.
- 눈썹/속눈썹 경계의 어두운 픽셀
- 하이라이트로 날아간 흰 픽셀
- 마스크 가장자리의 배경 혼입

평균은 이런 극단값 몇 개에 통째로 끌려간다. 원본 프로젝트가 겪었던
"얼굴 윤곽이 학습되는" 문제도 결국 같은 뿌리다. 중앙값과 사분위 범위는
전체 픽셀의 25%가 오염돼도 흔들리지 않는다.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from numpy.typing import NDArray

from .color_space import lab_to_lch, rgb_to_lab

# ITA° 구간 경계. Chardon et al.(1991)이 제안하고
# 이후 피부과·화장품 업계가 사실상 표준으로 채택한 분류다.
ITA_BOUNDARIES: tuple[tuple[str, float], ...] = (
    ("very_light", 55.0),
    ("light", 41.0),
    ("intermediate", 28.0),
    ("tan", 10.0),
    ("brown", -30.0),
    ("dark", -np.inf),
)


@dataclass(frozen=True, slots=True)
class SkinFeatures:
    """피부 영역에서 추출한 색채 특징 벡터.

    이 객체 하나가 분류기의 유일한 입력이다. 원본 이미지는 여기서 끊긴다.
    덕분에 분류 로직을 이미지 없이 단위 테스트할 수 있고,
    사용자에게 "왜 이 결과인지"를 수치로 그대로 보여줄 수 있다.
    """

    lightness: float
    """L* — 명도. 0(흑) ~ 100(백)."""

    a_star: float
    """a* — 녹(-) ↔ 적(+) 축."""

    b_star: float
    """b* — 청(-) ↔ 황(+) 축. 언더톤의 1차 신호."""

    chroma: float
    """C* — 채도. 클리어/뮤트를 가른다."""

    hue_angle: float
    """h° — 색상각(도). 피부는 대개 30~80° 사이에 분포한다."""

    ita: float
    """ITA° — 개인 유형 각도. 피부 명도의 국제 표준 지표."""

    lightness_spread: float
    """L*의 사분위 범위. 조명 균일도의 대리 지표 — 클수록 신뢰도가 낮다."""

    pixel_count: int
    """특징 계산에 실제로 사용된 피부 픽셀 수."""

    mean_rgb: tuple[int, int, int]
    """대표 피부색(중앙값). UI에 색상 칩으로 그대로 표시한다."""

    @property
    def ita_category(self) -> str:
        """ITA° 값을 표준 6단계 명도 구간명으로 변환한다."""
        for name, lower_bound in ITA_BOUNDARIES:
            if self.ita > lower_bound:
                return name
        return "dark"


def compute_ita(lightness: float, b_star: float) -> float:
    """ITA° (Individual Typology Angle)를 계산한다.

        ITA° = arctan((L* - 50) / b*) × 180/π

    L*=50을 원점으로 삼아 명도와 황색도가 이루는 각도를 잰다.
    단순히 L*만 보는 것보다 나은 이유는, 같은 밝기여도 황색도가 다르면
    사람 눈에 다른 톤으로 읽히는 현상을 각도가 흡수하기 때문이다.

    b*가 0에 수렴하면 각도가 발산하므로 하한을 둔다. 실제 피부는
    b*가 항상 양수(+8 이상)이므로 이 방어는 비정상 입력에만 걸린다.
    """
    safe_b = max(float(b_star), 1e-6)
    return float(np.degrees(np.arctan2(lightness - 50.0, safe_b)))


def extract_features(
    skin_pixels_rgb: NDArray[np.integer] | NDArray[np.floating],
    *,
    trim_percentile: float = 10.0,
) -> SkinFeatures:
    """피부 픽셀 배열에서 특징 벡터를 추출한다.

    Args:
        skin_pixels_rgb: (N, 3) 형태의 0~255 RGB 픽셀 목록.
                         마스킹을 거쳐 피부로 판정된 픽셀만 담겨 있어야 한다.
        trim_percentile: 상·하위 몇 퍼센트를 명도 기준으로 잘라낼지.
                         기본 10%는 그림자와 하이라이트를 걷어내되
                         피부톤의 자연스러운 분산은 보존하는 값이다.

    Returns:
        SkinFeatures 인스턴스.

    Raises:
        ValueError: 픽셀이 없거나 형태가 (N, 3)이 아닌 경우.
    """
    raw = np.asarray(skin_pixels_rgb, dtype=np.float64)

    # reshape보다 먼저 검증한다. (N, 4) 같은 입력을 reshape(-1, 3)에 넘기면
    # numpy가 던지는 메시지는 "size 40 into shape (3)"이라 원인 파악이 어렵다.
    if raw.ndim < 2 or raw.shape[-1] != 3:
        raise ValueError(f"(N, 3) 형태가 필요하지만 {raw.shape}를 받았습니다.")
    if raw.size == 0:
        raise ValueError("피부 픽셀이 하나도 없습니다. 마스킹 단계를 확인하세요.")

    pixels = raw.reshape(-1, 3)

    lab = rgb_to_lab(pixels)

    # 명도 기준 상·하위를 잘라낸다. 정렬 기준을 L*로 두는 이유는
    # 오염 픽셀(그림자·하이라이트)이 색상보다 밝기에서 먼저 튀기 때문이다.
    if trim_percentile > 0 and len(pixels) >= 20:
        lightness_values = lab[:, 0]
        low, high = np.percentile(
            lightness_values, [trim_percentile, 100.0 - trim_percentile]
        )
        keep = (lightness_values >= low) & (lightness_values <= high)
        if keep.sum() >= 10:
            lab = lab[keep]
            pixels = pixels[keep]

    median_lab = np.median(lab, axis=0)
    lightness, a_star, b_star = (float(v) for v in median_lab)

    _, chroma, hue_angle = (float(v) for v in lab_to_lch(median_lab))

    q1, q3 = np.percentile(lab[:, 0], [25.0, 75.0])
    median_rgb = np.clip(np.median(pixels, axis=0), 0.0, 255.0)

    return SkinFeatures(
        lightness=lightness,
        a_star=a_star,
        b_star=b_star,
        chroma=chroma,
        hue_angle=hue_angle,
        ita=compute_ita(lightness, b_star),
        lightness_spread=float(q3 - q1),
        pixel_count=len(pixels),
        # 제너레이터+tuple() 대신 명시적 3-튜플로 만든다. tuple[int, ...]이 아니라
        # tuple[int, int, int]임을 mypy가 증명할 수 있어 type: ignore가 필요 없다.
        mean_rgb=(
            round(float(median_rgb[0])),
            round(float(median_rgb[1])),
            round(float(median_rgb[2])),
        ),
    )
