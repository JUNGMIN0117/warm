"""얼굴 검출 래퍼 테스트.

실제 사람 사진을 저장소에 넣을 수 없으므로 (CLAUDE.md 금지사항)
두 층으로 나눠 검증한다.

1. 좌표 변환·박스 계산 같은 순수 함수 — 가짜 랜드마크 주입으로 항상 실행.
2. MediaPipe 실제 추론 경로 — 모델 파일이 있을 때만 실행 (없으면 skip).
   합성 이미지에는 얼굴이 없다는 사실 자체가 NoFaceDetectedError 정책의
   테스트 픽스처가 된다.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pytest

from app.pipeline.errors import (
    ModelNotFoundError,
    MultipleFacesError,
    NoFaceDetectedError,
    PipelineError,
)
from app.pipeline.face_detector import (
    DEFAULT_MODEL_PATH,
    FaceDetector,
    _bounding_box_of,
    _to_pixel_landmarks,
)

_MODEL_AVAILABLE = DEFAULT_MODEL_PATH.exists()
requires_model = pytest.mark.skipif(
    not _MODEL_AVAILABLE,
    reason="face_landmarker.task 없음 — scripts/download_models.py 실행 필요",
)


@dataclass(frozen=True)
class _FakeLandmark:
    """MediaPipe NormalizedLandmark의 최소 대역 — .x/.y만 흉내 낸다."""

    x: float
    y: float


class TestPixelConversion:
    def test_normalized_to_pixel_scaling(self) -> None:
        fake = [_FakeLandmark(0.0, 0.0), _FakeLandmark(0.5, 0.25), _FakeLandmark(1.0, 1.0)]
        pixels = _to_pixel_landmarks(fake, width=200, height=100)

        np.testing.assert_allclose(pixels, [[0, 0], [100, 25], [200, 100]])

    def test_bounding_box_wraps_landmarks(self) -> None:
        landmarks = np.array([[10.2, 20.7], [50.9, 80.1], [30.0, 40.0]])
        box = _bounding_box_of(landmarks, width=200, height=100)

        assert box == (10, 20, 52, 82)

    def test_bounding_box_clips_to_image(self) -> None:
        """얼굴이 프레임 밖으로 잘리면 랜드마크가 0~1 밖으로 나간다 — 클리핑 필수."""
        landmarks = np.array([[-15.0, -8.0], [250.0, 120.0]])
        box = _bounding_box_of(landmarks, width=200, height=100)

        assert box == (0, 0, 200, 100)


class TestErrorHierarchy:
    """상위 계층(FastAPI)이 의존할 예외 계약."""

    def test_all_pipeline_errors_share_base(self) -> None:
        assert issubclass(NoFaceDetectedError, PipelineError)
        assert issubclass(MultipleFacesError, PipelineError)
        assert issubclass(ModelNotFoundError, PipelineError)

    def test_multiple_faces_error_carries_count(self) -> None:
        """프론트의 '얼굴 선택 UI' 확장이 이 필드에 의존한다."""
        error = MultipleFacesError(face_count=3)
        assert error.face_count == 3
        assert "3" in str(error)

    def test_missing_model_raises_config_error(self) -> None:
        from pathlib import Path

        with pytest.raises(ModelNotFoundError, match="download_models"):
            FaceDetector(model_path=Path("does/not/exist.task"))


@pytest.fixture(scope="module")
def detector() -> FaceDetector:
    """모델 로딩이 비싸므로(수백 ms) 모듈당 한 번만 생성한다."""
    return FaceDetector()


@requires_model
class TestDetectionPolicy:
    """MediaPipe 실제 추론 — 모델 파일이 있을 때만."""

    def test_blank_image_raises_no_face(self, detector: FaceDetector) -> None:
        blank = np.full((240, 320, 3), 128, dtype=np.uint8)
        with pytest.raises(NoFaceDetectedError):
            detector.detect(blank)

    def test_skin_colored_rectangle_is_not_a_face(self, detector: FaceDetector) -> None:
        """피부색 사각형만으로는 얼굴로 검출되면 안 된다 — Haar가 자주
        틀리는 지점이며, 파이프라인이 '피부색 ≠ 얼굴'을 구분해야
        벽지·옷을 분석하는 사고를 막을 수 있다."""
        scene = np.full((240, 320, 3), 128, dtype=np.uint8)
        scene[60:180, 100:220] = (224, 172, 138)
        with pytest.raises(NoFaceDetectedError):
            detector.detect(scene)
