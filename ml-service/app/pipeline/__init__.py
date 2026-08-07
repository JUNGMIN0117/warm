"""이미지 전처리 파이프라인.

이미지 바이트를 받아 도메인 계층의 `extract_features()`가 소비할
(N, 3) 피부 픽셀 배열을 만들어내는 계층이다.

도메인 계층과의 경계가 이 프로젝트의 핵심 설계다.
- 이 패키지는 cv2·mediapipe에 의존한다. 도메인은 절대 하지 않는다.
- 이 패키지의 출력(픽셀 배열)에는 공간 정보가 없다. 얼굴형·윤곽이
  분류기에 흘러들어갈 물리적 경로 자체가 없다 (P2 대응).

파이프라인 순서와 각 단계의 이유는 docs/04-preprocessing.md 참조.
"""

from .errors import (
    ImageDecodeError,
    ModelNotFoundError,
    MultipleFacesError,
    NoFaceDetectedError,
    PipelineError,
)
from .face_detector import DetectedFace, FaceDetector
from .skin_mask import MaskConfig, SkinMask, build_skin_mask, extract_skin_pixels
from .white_balance import WhiteBalanceMethod, WhiteBalanceResult, apply_white_balance

__all__ = [
    "DetectedFace",
    "FaceDetector",
    "ImageDecodeError",
    "MaskConfig",
    "ModelNotFoundError",
    "MultipleFacesError",
    "NoFaceDetectedError",
    "PipelineError",
    "SkinMask",
    "WhiteBalanceMethod",
    "WhiteBalanceResult",
    "apply_white_balance",
    "build_skin_mask",
    "extract_skin_pixels",
]
