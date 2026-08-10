"""파이프라인 조립 테스트 — 이미지 바이트에서 판정까지의 종단 검증.

MediaPipe 대신 가짜 검출기(StubDetector)를 주입한다. 파이프라인이
SupportsFaceDetection 프로토콜에만 의존하기 때문에 가능한 구조이며,
검출기 자체의 동작은 test_face_detector.py가 따로 검증한다.
"""

from __future__ import annotations

import io

import numpy as np
import pytest
from numpy.typing import NDArray
from PIL import Image

from app.domain import Season, classify, extract_features
from app.pipeline import (
    ImageDecodeError,
    InsufficientSkinPixelsError,
    PipelineConfig,
    PipelineResult,
    PreprocessPipeline,
    decode_image,
)
from app.pipeline.face_detector import DetectedFace, _bounding_box_of
from tests.synthetic import HEIGHT, SKIN, WIDTH, fake_landmarks, synthetic_face


def _to_png_bytes(image: NDArray[np.uint8]) -> bytes:
    buffer = io.BytesIO()
    Image.fromarray(image).save(buffer, format="PNG")
    return buffer.getvalue()


class StubDetector:
    """항상 같은 랜드마크를 돌려주는 가짜 검출기."""

    def __init__(self, landmarks: NDArray[np.float64]) -> None:
        self._landmarks = landmarks

    def detect(self, image_rgb: NDArray[np.uint8]) -> DetectedFace:
        height, width = image_rgb.shape[:2]
        return DetectedFace(
            landmarks=self._landmarks,
            bounding_box=_bounding_box_of(self._landmarks, width, height),
            image_size=(width, height),
        )


@pytest.fixture(scope="module")
def pipeline() -> PreprocessPipeline:
    return PreprocessPipeline(detector=StubDetector(fake_landmarks()))


def _neutral_face() -> NDArray[np.uint8]:
    """중립 회색 배경의 합성 얼굴.

    synthetic.py 기본 배경은 YCrCb 거부를 검증하려고 일부러 푸른색인데,
    푸른 배경은 Gray-World에게 '파란 조명'으로 읽혀 피부를 붉게 밀어버린다
    (문서화된 가정 위반 — docs/04 §1). WB 정확성을 검증하는 파이프라인
    테스트에서는 중립 배경이 올바른 픽스처다.
    """
    return synthetic_face(background=(128, 128, 128))


class TestDecodeImage:
    def test_garbage_bytes_raise_decode_error(self) -> None:
        with pytest.raises(ImageDecodeError):
            decode_image(b"this is not an image at all")

    def test_png_roundtrip_preserves_pixels(self) -> None:
        image = synthetic_face()
        decoded = decode_image(_to_png_bytes(image))

        np.testing.assert_array_equal(decoded, image)

    def test_exif_orientation_is_applied(self) -> None:
        """폰 세로 사진(EXIF Orientation=6)이 바로 서야 한다.

        cv2.imdecode는 EXIF를 무시한다 — Pillow를 쓰는 이유이자,
        이 동작이 깨지면 세로 셀피의 얼굴 검출률이 급락한다.
        """
        landscape = np.zeros((50, 100, 3), dtype=np.uint8)
        landscape[:, :50] = (255, 0, 0)  # 왼쪽 절반 빨강

        exif = Image.Exif()
        exif[0x0112] = 6  # 시계방향 90° 회전 필요
        buffer = io.BytesIO()
        Image.fromarray(landscape).save(buffer, format="JPEG", exif=exif)

        decoded = decode_image(buffer.getvalue())

        assert decoded.shape[:2] == (100, 50)  # 가로세로가 바뀌어야 함


class TestPipelineRun:
    def test_end_to_end_bytes_to_classification(
        self, pipeline: PreprocessPipeline
    ) -> None:
        """이미지 바이트 → 파이프라인 → 도메인 → 4계절 판정까지 한 번에.

        이 테스트가 Step 1의 완료 조건이다: 파이프라인의 출력이 도메인
        계층의 입력 계약과 실제로 맞물리는지 종단으로 확인한다.
        """
        result = pipeline.run(_to_png_bytes(_neutral_face()))

        features = extract_features(result.skin_pixels)
        classification = classify(features)

        assert isinstance(result, PipelineResult)
        assert sum(classification.probabilities.values()) == pytest.approx(1.0)
        assert classification.season in set(Season)
        # 합성 피부(198,134,66)는 h°=68.4로 명백한 웜톤 값이다
        assert classification.undertone.value == "warm"

    def test_skin_pixels_match_painted_color(self, pipeline: PreprocessPipeline) -> None:
        result = pipeline.run(_to_png_bytes(_neutral_face()))

        assert result.skin_pixels.shape[0] > 5_000
        median = np.median(result.skin_pixels, axis=0)
        np.testing.assert_allclose(median, SKIN, atol=5.0)

    def test_chromatic_background_fails_loudly_not_silently(self) -> None:
        """강한 유채색 배경(Gray-World 가정 위반)은 조용한 왜곡이 아니라
        시끄러운 실패로 이어져야 한다.

        푸른 배경 + 딥 웜 피부의 캐스케이드: 배경을 '파란 조명'으로
        오인해 R을 과증폭 → 피부가 YCrCb 피부 범위 밖으로 밀림 →
        하드 플로어 발동. 이때 파이프라인이 남은 소수 픽셀로 그럴듯한
        판정을 지어내는 대신 InsufficientSkinPixelsError를 던지는 것이
        이 프로젝트의 정직성 원칙이다 (docs/04 §1 한계 참조).
        """
        pipeline = PreprocessPipeline(detector=StubDetector(fake_landmarks()))

        with pytest.raises(InsufficientSkinPixelsError):
            pipeline.run(_to_png_bytes(synthetic_face()))  # 기본 배경 = 푸른 회색

    def test_stages_are_structured_for_visualization(
        self, pipeline: PreprocessPipeline
    ) -> None:
        """프론트엔드 시각화 UI가 의존하는 구조 계약."""
        result = pipeline.run(_to_png_bytes(_neutral_face()))
        stages = result.stages

        assert stages.original.shape == (HEIGHT, WIDTH, 3)
        assert stages.white_balanced.shape == (HEIGHT, WIDTH, 3)
        assert stages.skin_mask.shape == (HEIGHT, WIDTH)

        x0, y0, x1, y1 = stages.face_box
        assert 0 <= x0 < x1 <= WIDTH and 0 <= y0 < y1 <= HEIGHT

        # crop은 박스보다 크거나 같고(여유 마진) 원본보다 작다
        crop_h, crop_w = stages.face_crop.shape[:2]
        assert (y1 - y0) <= crop_h < HEIGHT or (x1 - x0) <= crop_w < WIDTH
        assert stages.masked_skin.shape == stages.face_crop.shape

    def test_white_balance_undoes_color_cast(
        self, pipeline: PreprocessPipeline
    ) -> None:
        """웜 캐스트를 입힌 사진과 원본 사진의 피부 측정값이 수렴해야 한다.

        P1(조도 의존성)에 대한 종단 회귀 테스트. 배경 기반 조명 추정이
        동작하지 않으면 두 실행의 피부 중앙값이 크게 벌어진다.
        """
        from app.domain.color_space import linear_to_srgb, srgb_to_linear

        clean = _neutral_face()
        linear = srgb_to_linear(clean.astype(np.float64) / 255.0)
        casted_linear = np.clip(linear * np.array([1.18, 1.0, 0.82]), 0.0, 1.0)
        casted = (np.clip(linear_to_srgb(casted_linear), 0, 1) * 255 + 0.5).astype(
            np.uint8
        )

        clean_median = np.median(
            pipeline.run(_to_png_bytes(clean)).skin_pixels, axis=0
        )
        casted_median = np.median(
            pipeline.run(_to_png_bytes(casted)).skin_pixels, axis=0
        )

        np.testing.assert_allclose(casted_median, clean_median, atol=6.0)

    def test_insufficient_pixels_raise_explicit_error(self) -> None:
        pipeline = PreprocessPipeline(
            detector=StubDetector(fake_landmarks()),
            config=PipelineConfig(min_skin_pixels=10**9),
        )

        with pytest.raises(InsufficientSkinPixelsError) as exc_info:
            pipeline.run(_to_png_bytes(_neutral_face()))

        assert exc_info.value.pixel_count > 0
        assert exc_info.value.minimum == 10**9

    def test_wb_gains_are_reported_for_ui(self, pipeline: PreprocessPipeline) -> None:
        result = pipeline.run(_to_png_bytes(_neutral_face()))

        assert len(result.white_balance.gains) == 3
        assert result.white_balance.cast_strength >= 0.0
        assert 0.0 < result.mask_detail.coverage_ratio <= 1.0
