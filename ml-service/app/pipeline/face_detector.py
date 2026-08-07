"""얼굴 검출 — MediaPipe Face Landmarker 래퍼.

## 왜 Haar Cascade(원본 방식)를 버렸는가

2022년 원본은 `cv2.detectMultiScale`(Haar Cascade, 2001)을 썼다. 당시에도
이미 구식이었고, 이 프로젝트의 요구에는 세 가지가 치명적이다.

1. **바운딩 박스만 준다.** 눈·눈썹·입술을 제외하려면 랜드마크가 필요한데
   Haar는 위치를 모른다. 원본이 눈·입을 제외하지 못하고 사각형 crop을
   통째로 마스킹에 넘긴 이유가 이것이고, 그 결과가 P2(윤곽 학습)였다.
2. **정면 직립 얼굴에만 강하다.** 15° 이상 기울면 검출률이 급락한다 —
   셀피는 기울어져 있기 마련이다.
3. **밝기 대비 기반이라 오검출이 많다.** 창문·옷 주름을 얼굴로 잡는
   경우가 흔하다.

MediaPipe Face Landmarker는 478개 3D 랜드마크를 주므로 (2)와 (3)이
크게 개선되고, 무엇보다 (1)이 해결된다 — 눈·눈썹·입술의 정확한 폴리곤을
그릴 수 있다. 정량 비교는 scripts/compare_face_detectors.py와
docs/04-preprocessing.md 참조.

## 검출 정책

- 얼굴 0개 → `NoFaceDetectedError`. 억지로 진행하면 벽지를 분석한다.
- 얼굴 2개 이상 → `MultipleFacesError`. 자동 선택은 조용히 엉뚱한 사람을
  분석할 위험이 있다. 정책 근거는 errors.py 참조.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from types import TracebackType
from typing import Any

import numpy as np
from numpy.typing import NDArray

from .errors import ModelNotFoundError, MultipleFacesError, NoFaceDetectedError

# 기본 모델 경로. scripts/download_models.py가 받아두는 위치다.
DEFAULT_MODEL_PATH = (
    Path(__file__).resolve().parent.parent.parent / "models" / "face_landmarker.task"
)

# 랜드마커에 요청하는 최대 얼굴 수. "1명 vs 여러 명"을 구분하는 것이
# 목적이므로 2 이상이기만 하면 되지만, 단체 사진에서 정확한 인원수를
# 에러 메시지에 담기 위해 여유를 둔다.
_MAX_FACES = 4

# MediaPipe Face Landmarker의 랜드마크 개수 (홍채 포함).
LANDMARK_COUNT = 478


@dataclass(frozen=True, slots=True)
class DetectedFace:
    """검출된 얼굴 하나.

    landmarks가 정규화 좌표(0~1)가 아니라 **픽셀 좌표**인 이유:
    이후 단계(폴리곤 마스킹)가 전부 픽셀 공간에서 동작하므로 여기서
    한 번만 변환해 두면 소비자들이 이미지 크기를 다시 알 필요가 없다.
    """

    landmarks: NDArray[np.float64]
    """(478, 2) 픽셀 좌표. [i] = (x, y)."""

    bounding_box: tuple[int, int, int, int]
    """랜드마크를 감싸는 (x0, y0, x1, y1). 이미지 경계로 클리핑됨."""

    image_size: tuple[int, int]
    """검출 대상 이미지의 (width, height). 좌표의 기준계."""


def _to_pixel_landmarks(
    normalized_landmarks: list[Any], width: int, height: int
) -> NDArray[np.float64]:
    """MediaPipe 정규화 랜드마크(0~1)를 (N, 2) 픽셀 좌표로 변환한다.

    mediapipe 객체가 아니라 .x/.y 속성만 요구하므로, 테스트에서
    가짜 랜드마크를 주입해 MediaPipe 없이 검증할 수 있다.
    """
    return np.array(
        [(lm.x * width, lm.y * height) for lm in normalized_landmarks],
        dtype=np.float64,
    )


def _bounding_box_of(
    landmarks: NDArray[np.float64], width: int, height: int
) -> tuple[int, int, int, int]:
    """랜드마크 집합을 감싸는 박스를 이미지 경계 안으로 클리핑해 만든다.

    MediaPipe 랜드마크는 얼굴이 프레임 밖으로 잘린 경우 0~1 범위를
    벗어날 수 있으므로 클리핑이 필수다.
    """
    x0 = int(np.clip(np.floor(landmarks[:, 0].min()), 0, width - 1))
    y0 = int(np.clip(np.floor(landmarks[:, 1].min()), 0, height - 1))
    x1 = int(np.clip(np.ceil(landmarks[:, 0].max()) + 1, x0 + 1, width))
    y1 = int(np.clip(np.ceil(landmarks[:, 1].max()) + 1, y0 + 1, height))
    return (x0, y0, x1, y1)


class FaceDetector:
    """MediaPipe Face Landmarker의 얇은 래퍼.

    모델 로딩(수백 ms)이 비싸므로 인스턴스를 한 번 만들어 재사용한다 —
    FastAPI 단계에서 앱 시작 시 싱글턴으로 생성될 예정이다.
    컨텍스트 매니저로도 쓸 수 있다.
    """

    def __init__(
        self,
        model_path: Path = DEFAULT_MODEL_PATH,
        *,
        min_detection_confidence: float = 0.5,
    ) -> None:
        if not model_path.exists():
            raise ModelNotFoundError(
                f"모델 파일이 없습니다: {model_path}\n"
                "다음 명령으로 받아 주세요: "
                "cd ml-service && uv run python scripts/download_models.py"
            )

        # mediapipe 임포트를 모듈 최상단이 아니라 생성자에 두는 이유:
        # 이 모듈의 순수 함수(_to_pixel_landmarks 등)는 mediapipe 없이
        # 테스트 가능해야 한다. 반면 생성자는 서버 기동 시 실행되므로,
        # 임포트 실패(환경 문제)는 여전히 첫 요청이 아니라 기동 시점에 드러난다.
        import mediapipe as mp
        from mediapipe.tasks.python.core.base_options import BaseOptions
        from mediapipe.tasks.python.vision import FaceLandmarker, FaceLandmarkerOptions

        self._mp = mp
        options = FaceLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=str(model_path)),
            num_faces=_MAX_FACES,
            min_face_detection_confidence=min_detection_confidence,
            # 블렌드셰이프(표정)와 변환 행렬은 쓰지 않으므로 끈다 — 추론 비용 절약.
            output_face_blendshapes=False,
            output_facial_transformation_matrixes=False,
        )
        self._landmarker = FaceLandmarker.create_from_options(options)

    def detect(self, image_rgb: NDArray[np.uint8]) -> DetectedFace:
        """이미지에서 정확히 한 명의 얼굴을 찾는다.

        Args:
            image_rgb: (H, W, 3) uint8 RGB 이미지 (화이트밸런스 보정 후).

        Returns:
            픽셀 좌표 랜드마크를 담은 DetectedFace.

        Raises:
            NoFaceDetectedError: 얼굴이 없음.
            MultipleFacesError: 얼굴이 2개 이상.
        """
        arr = np.ascontiguousarray(image_rgb)
        height, width = arr.shape[:2]

        mp_image = self._mp.Image(image_format=self._mp.ImageFormat.SRGB, data=arr)
        result = self._landmarker.detect(mp_image)

        faces: list[list[Any]] = result.face_landmarks
        if not faces:
            raise NoFaceDetectedError(
                "얼굴을 찾지 못했습니다. 정면 얼굴이 잘 나온 사진을 사용해 주세요."
            )
        if len(faces) > 1:
            raise MultipleFacesError(face_count=len(faces))

        landmarks = _to_pixel_landmarks(faces[0], width, height)
        return DetectedFace(
            landmarks=landmarks,
            bounding_box=_bounding_box_of(landmarks, width, height),
            image_size=(width, height),
        )

    def close(self) -> None:
        """네이티브 리소스를 해제한다."""
        self._landmarker.close()

    def __enter__(self) -> FaceDetector:
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        tb: TracebackType | None,
    ) -> None:
        self.close()
