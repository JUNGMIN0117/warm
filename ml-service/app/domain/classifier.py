"""색채학 규칙 기반 4계절 퍼스널 컬러 분류기.

## 왜 규칙 엔진이 먼저인가

원본 프로젝트(2022)는 크롤링한 이미지로 CNN을 바로 학습시켰고,
결과 보고서에서 스스로 이렇게 적었다 —
*"환경·조도·카메라·각도 등 영향을 끼칠 수 있는 변수가 상당히 많아
정밀한 이미지 데이터셋을 만들 수 없다."*

라벨 자체가 오염된 상태에서 학습한 모델은 오차의 출처를 알 수 없다.
전처리가 문제인지, 라벨이 문제인지, 모델 용량이 문제인지 구분이 안 된다.

이 규칙 엔진은 그 기준선을 제공한다. 학습 데이터가 0건이어도 동작하고,
결과의 근거를 CIELab 좌표 세 개로 완전히 설명할 수 있으며,
나중에 학습한 CNN이 *정말로* 더 나은지 비교할 대조군이 된다.

## 분류 방식: 3축 프로토타입 거리

각 계절을 정규화된 3차원 특징 공간의 한 점(프로토타입)으로 정의하고,
입력 피부의 좌표에서 각 프로토타입까지의 가중 거리를 잰 뒤
소프트맥스로 확률화한다.

    축 1  warm    ← h°   (CIELab 색상각)  : 노란기 ↔ 푸른기
    축 2  light   ← ITA° (개인 유형 각도) : 밝음 ↔ 깊음
    축 3  clear   ← C*   (채도)          : 선명 ↔ 뮤트

if-else 트리 대신 이 방식을 쓴 이유는 세 가지다.
1. 경계에서 확률이 부드럽게 변한다 → 신뢰도를 정직하게 표현할 수 있다.
2. 8세부 타입으로 확장할 때 프로토타입만 추가하면 된다.
3. 프로토타입 좌표가 곧 캘리브레이션 파라미터라, 나중에 실측 데이터로
   튜닝하는 경로가 열려 있다.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from .features import SkinFeatures
from .seasons import Season, Undertone


@dataclass(frozen=True, slots=True)
class CalibrationConfig:
    """규칙 엔진의 캘리브레이션 파라미터.

    모든 임계값을 이 한 곳에 모아둔 것이 요점이다. 실측 데이터가 쌓이면
    코드를 건드리지 않고 이 값만 최적화하면 된다.

    초기값은 아시아인 피부의 CIELab 분포에 관한 공개 문헌 범위를 근거로
    잡았다. 아직 실측 캘리브레이션을 거치지 않았으므로 잠정값이다.
    """

    hue_center: float = 62.0
    """h° 중립점(도). 이보다 크면 노란기(warm), 작으면 푸른기(cool).

    주의 — 사람 피부는 멜라닌과 카로틴 때문에 b*가 **항상 양수**다.
    즉 쿨톤 피부라고 해서 h°가 0에 가까워지지 않는다. 실제 변별은
    55~70° 사이 좁은 구간에서 일어나므로 중립점을 이 대역 한가운데 둔다.
    """
    hue_scale: float = 5.0
    """h° 로지스틱 기울기. 작을수록 경계가 날카로워진다."""

    ita_center: float = 48.0
    """ITA° 중립점(도). light 계열과 deep 계열을 가르는 지점.

    Chardon 표준 구간의 'light/intermediate' 경계는 41°지만, 그 값은
    피부과적 광손상 위험도 분류용이라 퍼스널 컬러의 밝기 감각과 어긋난다.
    동아시아인 피부 ITA° 분포의 중앙값 대역인 45~50°에 맞춰 조금 올렸다.
    """
    ita_scale: float = 12.0

    chroma_center: float = 22.0
    """C* 중립점. 이보다 높으면 clear, 낮으면 muted."""
    chroma_scale: float = 7.0

    axis_weights: tuple[float, float, float] = (2.0, 1.0, 0.7)
    """(warm, light, clear) 축 가중치.

    언더톤에 가장 큰 가중치를 준다. 퍼스널 컬러 이론에서 웜/쿨은
    1차 분기이고 나머지는 그 안의 세부 조정이기 때문이다.
    """

    softmax_temperature: float = 0.18
    """거리→확률 변환 온도. 낮을수록 결과가 단정적이 된다."""

    min_reliable_pixels: int = 2_000
    """이보다 픽셀이 적으면 신뢰도를 감쇠시킨다."""

    max_reliable_spread: float = 18.0
    """L* 사분위 범위가 이보다 크면 조명이 불균일한 것으로 간주한다."""


# 각 계절의 프로토타입 좌표 (warm, light, clear), 각 축 0.0~1.0
SEASON_PROTOTYPES: dict[Season, tuple[float, float, float]] = {
    Season.SPRING_WARM: (1.00, 0.90, 0.85),  # 웜 · 밝음 · 선명
    Season.SUMMER_COOL: (0.00, 0.88, 0.15),  # 쿨 · 밝음 · 뮤트
    Season.AUTUMN_WARM: (1.00, 0.12, 0.40),  # 웜 · 깊음 · 중간
    Season.WINTER_COOL: (0.00, 0.15, 0.90),  # 쿨 · 깊음 · 선명
}


@dataclass(frozen=True, slots=True)
class AxisReading:
    """한 축의 측정 결과. UI가 게이지로 그대로 렌더링한다."""

    name: str
    raw_value: float
    """원본 측정값 (h°, ITA°, C*)."""
    normalized: float
    """0.0~1.0으로 정규화된 좌표."""
    low_label: str
    high_label: str
    interpretation: str


@dataclass(frozen=True, slots=True)
class ClassificationResult:
    """분류 결과 전체.

    확률 분포를 통째로 반환하는 게 핵심이다. 최상위 하나만 주면
    "62% 봄 / 35% 여름"인 경계 케이스와 "97% 겨울"인 확실한 케이스를
    사용자가 구분할 수 없다.
    """

    season: Season
    confidence: float
    """0.0~1.0. 확률 최댓값에 품질 계수를 곱한 값."""
    probabilities: dict[Season, float]
    undertone: Undertone
    undertone_confidence: float
    """웜/쿨 2분류만 봤을 때의 신뢰도. 4분류보다 항상 높거나 같다."""
    axes: tuple[AxisReading, ...]
    quality_factor: float
    """입력 품질 계수(0.0~1.0). 픽셀 수와 조명 균일도로 산출."""
    warnings: tuple[str, ...] = field(default=())


def _logistic(value: float, center: float, scale: float) -> float:
    """로지스틱 함수로 임의의 측정값을 0.0~1.0 구간에 매핑한다."""
    return float(1.0 / (1.0 + np.exp(-(value - center) / scale)))


def _compute_quality(features: SkinFeatures, config: CalibrationConfig) -> tuple[float, list[str]]:
    """입력 품질을 평가한다.

    신뢰도를 확률값 하나로만 내보내면 거짓말이 된다. 피부 픽셀이 200개뿐인
    저해상도 사진과 5만 개가 잡힌 정면 사진이 같은 확신을 가질 수는 없다.
    """
    warnings: list[str] = []
    quality = 1.0

    if features.pixel_count < config.min_reliable_pixels:
        ratio = features.pixel_count / config.min_reliable_pixels
        quality *= 0.55 + 0.45 * min(ratio, 1.0)
        warnings.append(
            f"피부 영역이 작습니다({features.pixel_count:,}px). "
            "얼굴이 더 크게 나온 사진을 쓰면 정확도가 올라갑니다."
        )

    if features.lightness_spread > config.max_reliable_spread:
        excess = features.lightness_spread - config.max_reliable_spread
        quality *= max(0.5, 1.0 - excess / 40.0)
        warnings.append(
            "얼굴에 조명이 고르지 않습니다. 그림자나 역광이 없는 "
            "자연광에서 촬영한 사진을 권장합니다."
        )

    return float(np.clip(quality, 0.0, 1.0)), warnings


def _build_axes(
    warm: float, light: float, clear: float, features: SkinFeatures
) -> tuple[AxisReading, ...]:
    """정규화된 축 값에 사람이 읽을 해석을 붙인다."""

    def describe(value: float, low: str, high: str) -> str:
        if value >= 0.72:
            return f"{high} 성향이 뚜렷합니다"
        if value >= 0.56:
            return f"{high} 쪽에 가깝습니다"
        if value > 0.44:
            return "중립에 가까워 판정이 미묘합니다"
        if value > 0.28:
            return f"{low} 쪽에 가깝습니다"
        return f"{low} 성향이 뚜렷합니다"

    return (
        AxisReading(
            name="undertone",
            raw_value=features.hue_angle,
            normalized=warm,
            low_label="쿨(푸른기)",
            high_label="웜(노란기)",
            interpretation=describe(warm, "쿨", "웜"),
        ),
        AxisReading(
            name="depth",
            raw_value=features.ita,
            normalized=light,
            low_label="딥(깊은)",
            high_label="라이트(밝은)",
            interpretation=describe(light, "딥", "라이트"),
        ),
        AxisReading(
            name="clarity",
            raw_value=features.chroma,
            normalized=clear,
            low_label="뮤트(부드러운)",
            high_label="클리어(선명한)",
            interpretation=describe(clear, "뮤트", "클리어"),
        ),
    )


def classify(
    features: SkinFeatures,
    config: CalibrationConfig | None = None,
) -> ClassificationResult:
    """피부 특징으로부터 4계절 퍼스널 컬러를 판정한다.

    Args:
        features: `extract_features`가 반환한 특징 벡터.
        config: 캘리브레이션 파라미터. 생략하면 기본값을 쓴다.

    Returns:
        확률 분포와 판정 근거를 모두 담은 ClassificationResult.
    """
    cfg = config or CalibrationConfig()

    warm = _logistic(features.hue_angle, cfg.hue_center, cfg.hue_scale)
    light = _logistic(features.ita, cfg.ita_center, cfg.ita_scale)
    clear = _logistic(features.chroma, cfg.chroma_center, cfg.chroma_scale)

    point = np.array([warm, light, clear], dtype=np.float64)
    weights = np.array(cfg.axis_weights, dtype=np.float64)

    seasons = list(SEASON_PROTOTYPES.keys())
    prototypes = np.array([SEASON_PROTOTYPES[s] for s in seasons], dtype=np.float64)

    # 가중 유클리드 거리. 축 가중치는 제곱 항에 곱한다.
    diff = prototypes - point
    distances = np.sqrt((weights * diff**2).sum(axis=1))

    # 거리가 짧을수록 높은 점수 → 소프트맥스로 확률화.
    logits = -distances / cfg.softmax_temperature
    logits -= logits.max()  # 오버플로 방지
    exp_scores = np.exp(logits)
    probs = exp_scores / exp_scores.sum()

    probabilities = {season: float(p) for season, p in zip(seasons, probs)}
    best_index = int(np.argmax(probs))
    best_season = seasons[best_index]

    quality, warnings = _compute_quality(features, cfg)

    # 웜/쿨 2분류는 4분류보다 훨씬 견고하다. 4계절 판정이 애매해도
    # 언더톤만큼은 자신 있게 말할 수 있는 경우가 많으므로 따로 보고한다.
    warm_prob = sum(
        p for s, p in probabilities.items() if s.undertone is Undertone.WARM
    )
    undertone = Undertone.WARM if warm_prob >= 0.5 else Undertone.COOL
    undertone_confidence = max(warm_prob, 1.0 - warm_prob) * quality

    # 경계 판정은 절대 확률이 아니라 1위와 2위의 격차로 본다.
    # 4분류에서 "55%"는 나머지가 15%씩 흩어졌으면 확실한 결과지만,
    # 2위가 44%라면 사실상 동점이다. 절대값만 보면 이 둘을 구분할 수 없다.
    top_two = np.sort(probs)[-2:]
    if float(top_two[1] - top_two[0]) < 0.15:
        warnings.append(
            "두 계절 사이 경계에 있습니다. 조명이 다른 사진으로 한 번 더 "
            "측정해 보시길 권합니다."
        )

    return ClassificationResult(
        season=best_season,
        confidence=float(probs[best_index]) * quality,
        probabilities=probabilities,
        undertone=undertone,
        undertone_confidence=float(undertone_confidence),
        axes=_build_axes(warm, light, clear, features),
        quality_factor=quality,
        warnings=tuple(warnings),
    )
