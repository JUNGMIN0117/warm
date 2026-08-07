"""화이트밸런스 정규화 — 원본 프로젝트의 한계 P1(조도 의존성)에 대한 답.

2022년 원본 보고서는 스스로 이렇게 적었다 — *"환경·조도·카메라·각도 등
영향을 끼칠 수 있는 변수가 상당히 많아 정밀한 이미지 데이터셋을 만들 수
없다."* 카메라가 기록하는 픽셀은

    관측값 = 피부의 분광 반사율 × 조명의 분광 분포 × 카메라 응답

의 곱이므로, 백열등(주황빛) 아래에서는 쿨톤 피부도 웜톤으로 찍힌다.
화이트밸런스는 조명 항을 추정해 나눠 없애는 절차이고, 색이 이미 왜곡된
상태에서는 마스킹도 특징 추출도 의미가 없으므로 **파이프라인의 첫 단계**여야 한다.

## 왜 Gray-World가 기본인가

Gray-World(Buchsbaum, 1980)는 "장면 전체의 평균 반사율은 무채색"이라는
가정 하나로 조명색을 추정한다.

- 파라미터가 없다 → 튜닝할 것도, 데이터셋에 과적합될 것도 없다.
- O(N) 단일 패스라 수 ms에 끝난다 → 실시간 웹캠 경로에도 부담이 없다.
- 얼굴+실내 배경이 섞인 셀피류 입력에서 가정이 크게 어긋나지 않는다.

한계: 화면 대부분이 단색(초록 벽지 등)이면 그 색을 조명으로 오인해
과보정한다. 그래서 White-Patch(Retinex 계열)를 비교 구현으로 함께 두고,
게인을 [MIN_GAIN, MAX_GAIN]으로 제한해 과보정의 피해 상한을 고정한다.

학습 기반 AWB(FC4 등)는 기각했다. 모델 가중치·학습 데이터가 필요해
"학습 데이터 0건으로 동작하는 기준선"이라는 프로젝트 전략(ADR-002)과
충돌하고, 실패 시 원인을 설명할 수 없기 때문이다.

## 왜 선형 공간에서 하는가

조명은 물리적으로 반사율에 *곱해지는* 항이다. 곱셈 관계는 선형 RGB에서만
성립하고, sRGB에 저장된 값은 이미 감마 인코딩(≈x^(1/2.2))된 상태다.
감마 인코딩된 값에 게인을 곱하면 밝은 영역과 어두운 영역이 서로 다른
비율로 보정되어 색조가 뒤틀린다. 그래서 감마 제거 → 게인 적용 → 감마
재인코딩 순서를 지킨다. 도메인 계층이 Lab 변환에서 감마 제거를 필수로
두는 것과 정확히 같은 이유다 (docs/03-color-theory.md).
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

import numpy as np
from numpy.typing import NDArray

from ..domain.color_space import linear_to_srgb, srgb_to_linear

# 채널 게인의 허용 범위. Gray-World 가정이 심하게 깨진 입력(단색 배경 등)에서
# 조명 추정이 폭주해도 이미지를 망가뜨리는 상한을 고정한다.
# 실제 촬영 조명(백열등 2700K ~ 흐린 하늘 7500K)이 만드는 캐스트는
# 채널비 2배 이내이므로, 그 밖의 게인은 추정 실패로 간주해 잘라낸다.
MIN_GAIN = 0.5
MAX_GAIN = 2.0

# White-Patch에서 "가장 밝은 픽셀" 대신 쓰는 분위수. 진짜 최댓값(100%)은
# 센서 노이즈나 스펙큘러 하이라이트 픽셀 하나에 통째로 끌려가므로
# 상위 1%를 흰색 기준으로 삼는다.
_WHITE_PATCH_PERCENTILE = 99.0

# estimation_mask가 이 비율보다 적은 픽셀만 남기면 전체 이미지로 폴백한다.
# 표본이 너무 작으면 조명 추정치가 노이즈에 지배되기 때문이다.
_MIN_MASK_FRACTION = 0.01


class WhiteBalanceMethod(StrEnum):
    """화이트밸런스 알고리즘 선택지."""

    GRAY_WORLD = "gray_world"
    """기본값. 장면 평균이 무채색이라고 가정한다."""

    WHITE_PATCH = "white_patch"
    """Retinex 계열. 장면에서 가장 밝은 표면이 흰색이라고 가정한다."""


@dataclass(frozen=True, slots=True)
class WhiteBalanceResult:
    """화이트밸런스 적용 결과.

    보정된 이미지뿐 아니라 추정된 게인을 함께 반환한다. 게인은
    "조명이 얼마나 치우쳐 있었는가"의 수치 증거로, 프론트엔드의
    파이프라인 시각화 UI에 그대로 표시된다.
    """

    image: NDArray[np.uint8]
    """보정된 RGB 이미지 (H, W, 3), uint8."""

    gains: tuple[float, float, float]
    """선형 공간에서 각 채널(R, G, B)에 곱한 게인. 1.0이면 무보정."""

    method: WhiteBalanceMethod
    """사용한 알고리즘."""

    @property
    def cast_strength(self) -> float:
        """입력에 있던 색 캐스트의 세기. max(gains)/min(gains) - 1.

        0이면 이미 중립, 클수록 조명이 치우쳐 있었다는 뜻이다.
        UI 경고("조명 색이 강했습니다")의 근거 수치로 쓴다.
        """
        return float(max(self.gains) / min(self.gains) - 1.0)


def _validate_image(image_rgb: NDArray[np.uint8]) -> NDArray[np.uint8]:
    """(H, W, 3) uint8 이미지인지 검증한다. 아니면 즉시 ValueError.

    파이프라인 깊숙한 곳에서 dtype 불일치로 이상한 결과가 나오는 것보다
    입구에서 시끄럽게 실패하는 편이 디버깅 비용이 압도적으로 싸다.
    """
    arr = np.asarray(image_rgb)
    if arr.ndim != 3 or arr.shape[2] != 3:
        raise ValueError(f"(H, W, 3) RGB 이미지가 필요하지만 {arr.shape}를 받았습니다.")
    if arr.dtype != np.uint8:
        raise ValueError(f"uint8 이미지가 필요하지만 {arr.dtype}를 받았습니다.")
    return arr


def _estimate_gains(
    flat: NDArray[np.float64], method: WhiteBalanceMethod
) -> NDArray[np.float64]:
    """선형 RGB 픽셀 목록 (N, 3)에서 채널별 게인을 추정한다.

    두 방법 모두 "채널별 기준값이 서로 같아지도록" 게인을 정한다.
    기준값의 평균을 목표로 삼기 때문에 전체 노출(밝기)은 보존되고
    채널 간 비율만 교정된다 — 노출까지 바꿔버리면 L*이 변해서
    명도 기반 판정(ITA°)을 오히려 오염시키기 때문이다.
    """
    if method is WhiteBalanceMethod.GRAY_WORLD:
        reference = flat.mean(axis=0)
    else:  # WHITE_PATCH
        reference = np.percentile(flat, _WHITE_PATCH_PERCENTILE, axis=0)

    target = float(reference.mean())
    if target < 1e-6:
        # 사실상 검은 이미지. 추정할 조명이 없으므로 무보정으로 통과시킨다.
        return np.ones(3, dtype=np.float64)

    # 채널 기준값이 0에 가까우면(순수 단색 이미지 등) 게인이 발산하므로
    # 나눗셈 전에 하한을 두고, 결과도 물리적으로 그럴듯한 범위로 제한한다.
    safe_reference = np.maximum(reference, 1e-6)
    return np.clip(target / safe_reference, MIN_GAIN, MAX_GAIN)


def apply_white_balance(
    image_rgb: NDArray[np.uint8],
    method: WhiteBalanceMethod = WhiteBalanceMethod.GRAY_WORLD,
    *,
    estimation_mask: NDArray[np.bool_] | None = None,
) -> WhiteBalanceResult:
    """이미지의 색 캐스트를 제거한다. 파이프라인의 첫 단계.

    Args:
        image_rgb: (H, W, 3) uint8 RGB 이미지. (OpenCV 기본인 BGR이 아니라
                   RGB임에 주의 — 파이프라인 전체가 RGB로 통일되어 있다.)
        method: 추정 알고리즘. 기본은 Gray-World.
        estimation_mask: (H, W) bool. 주어지면 True인 픽셀에서만 조명을
            추정한다 (게인은 이미지 전체에 적용). Gray-World의 아킬레스건은
            "측정 대상인 피부가 프레임을 채울수록 피부의 웜기를 조명으로
            오인해 지워버린다"는 것인데, 얼굴 영역을 추정에서 제외하면
            이 왜곡을 차단할 수 있다. 마스크가 전체의 1% 미만이면 표본
            부족으로 보고 전체 이미지 추정으로 폴백한다.

    Returns:
        보정 이미지와 추정 게인을 담은 WhiteBalanceResult.

    Raises:
        ValueError: 입력이 (H, W, 3) uint8이 아니거나 마스크 크기가 다른 경우.
    """
    arr = _validate_image(image_rgb)

    # 감마 제거 → 선형 공간에서 게인 적용 → 감마 재인코딩.
    linear = srgb_to_linear(arr.astype(np.float64) / 255.0)

    sample = linear.reshape(-1, 3)
    if estimation_mask is not None:
        if estimation_mask.shape != arr.shape[:2]:
            raise ValueError(
                f"마스크 {estimation_mask.shape}가 이미지 {arr.shape[:2]}와 다릅니다."
            )
        flat_mask = estimation_mask.reshape(-1)
        if flat_mask.sum() >= flat_mask.size * _MIN_MASK_FRACTION:
            sample = sample[flat_mask]

    gains = _estimate_gains(sample, method)

    balanced_linear = np.clip(linear * gains, 0.0, 1.0)
    balanced_srgb = np.clip(linear_to_srgb(balanced_linear), 0.0, 1.0)

    return WhiteBalanceResult(
        image=(balanced_srgb * 255.0 + 0.5).astype(np.uint8),
        gains=(float(gains[0]), float(gains[1]), float(gains[2])),
        method=method,
    )
