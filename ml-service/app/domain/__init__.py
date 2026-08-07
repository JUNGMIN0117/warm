"""퍼스널 컬러 분석 도메인 로직.

이 패키지는 이미지 I/O, 웹 프레임워크, 모델 서빙 어느 것에도 의존하지 않는다.
순수하게 색채 이론과 수치 연산만으로 구성되어 있어 단위 테스트가 빠르고,
나중에 Java(DJL)로 이식할 때도 이 경계가 그대로 명세 역할을 한다.
"""

from .classifier import (
    AxisReading,
    CalibrationConfig,
    ClassificationResult,
    classify,
)
from .color_space import lab_to_lch, rgb_to_lab, rgb_to_ycrcb
from .features import SkinFeatures, compute_ita, extract_features
from .seasons import SEASON_PROFILES, Season, SeasonProfile, Undertone, get_profile

__all__ = [
    "SEASON_PROFILES",
    "AxisReading",
    "CalibrationConfig",
    "ClassificationResult",
    "Season",
    "SeasonProfile",
    "SkinFeatures",
    "Undertone",
    "classify",
    "compute_ita",
    "extract_features",
    "get_profile",
    "lab_to_lch",
    "rgb_to_lab",
    "rgb_to_ycrcb",
]
