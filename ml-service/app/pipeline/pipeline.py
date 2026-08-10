"""전처리 파이프라인 조립 — 이미지 바이트 → (N, 3) 피부 픽셀.

## 단계 순서 (ADR-004)

    디코딩 → ① 얼굴 검출 → ② 배경 기반 화이트밸런스 → ③ 피부 마스킹 → 픽셀 추출

로드맵 초안은 "WB → 검출" 순서였지만 뒤집었다. Gray-World를 전체
프레임으로 추정하면 측정 대상인 피부의 웜기가 '주황 조명'으로 오인되어
제거되고, 그 훼손 정도가 얼굴이 프레임에서 차지하는 비율에 비례한다 —
P1이 제거하려던 바로 그 촬영 조건 의존성이다. 얼굴을 먼저 찾고 얼굴
제외 배경에서만 조명을 추정하면 이 왜곡이 차단된다 (합성 실험 수치는
docs/04-preprocessing.md §1 참조).

이 순서가 기대는 가정: **MediaPipe 랜드마크 검출은 색 캐스트에 강건하다.**
랜드마크는 기하 구조(명암 경계·형태)에서 나오지 색 절대값에서 나오지
않으므로 합리적인 가정이며, 캐스트 때문에 검출 자체가 실패하는 극단
입력은 NoFaceDetectedError로 정직하게 실패한다.

랜드마크 좌표는 검출 시점 이미지 기준이지만 WB는 색만 바꾸고 기하는
바꾸지 않으므로, 같은 좌표를 보정 후 이미지에 그대로 쓸 수 있다.

## 중간 단계 보존

각 단계의 이미지를 PipelineStages로 구조화해 반환한다. Step 4 프론트엔드의
"전처리 파이프라인 시각화" UI가 이것을 그대로 소비한다 — 사용자에게
"당신 사진이 이렇게 처리됐다"를 보여주는 것이 블랙박스 탈출이라는
프로젝트 목표의 일부이기 때문이다.
"""

from __future__ import annotations

import io
from dataclasses import dataclass, field
from typing import Protocol

import cv2
import numpy as np
from numpy.typing import NDArray
from PIL import Image, ImageOps, UnidentifiedImageError

from .errors import ImageDecodeError, InsufficientSkinPixelsError
from .face_detector import DetectedFace
from .skin_mask import MaskConfig, SkinMask, build_skin_mask, extract_skin_pixels
from .white_balance import WhiteBalanceMethod, WhiteBalanceResult, apply_white_balance


class SupportsFaceDetection(Protocol):
    """얼굴 검출기 계약. 파이프라인은 구체 구현이 아니라 이 계약에 의존한다.

    테스트가 MediaPipe 없이 가짜 검출기를 주입할 수 있고, 나중에 검출기를
    교체(예: 다른 모델)해도 파이프라인 코드는 변하지 않는다.
    """

    def detect(self, image_rgb: NDArray[np.uint8]) -> DetectedFace: ...


@dataclass(frozen=True, slots=True)
class PipelineConfig:
    """파이프라인 임계값 — 매직 넘버를 한 곳에 모은다 (CalibrationConfig 원칙)."""

    wb_method: WhiteBalanceMethod = WhiteBalanceMethod.GRAY_WORLD

    mask: MaskConfig = field(default_factory=MaskConfig)

    min_skin_pixels: int = 100
    """하드 플로어. 이 미만이면 중앙값 통계 자체가 무의미해 즉시 실패한다.
    도메인의 min_reliable_pixels(2,000)는 신뢰도를 감쇠시키는 소프트
    기준이라는 점에서 역할이 다르다."""

    wb_exclusion_margin_ratio: float = 0.15
    """조명 추정에서 제외할 영역 = 얼굴 박스 + 이 비율만큼의 여유.
    박스 바로 밖 픽셀은 머리카락·목이라 배경이라 부르기 어렵다."""

    crop_margin_ratio: float = 0.12
    """시각화용 얼굴 crop에 두는 여유. 턱·이마가 잘려 보이지 않을 정도."""

    max_input_edge: int = 1_600
    """입력 이미지의 최대 변 길이. 넘으면 축소한 뒤 파이프라인을 태운다.

    속도만의 문제가 아니다. 4000px 사진은 피부 픽셀이 수백만 개인데
    중앙값 통계는 수만 개면 이미 수렴하므로 나머지는 순수한 비용이다.
    면적 평균(INTER_AREA)으로 줄이므로 색 통계는 거의 보존된다.
    """


@dataclass(frozen=True, slots=True)
class PipelineStages:
    """전처리 각 단계의 이미지. 프론트엔드 시각화 UI의 데이터 소스.

    마스크류는 원본 크기의 bool 배열로 두고 crop을 따로 준다 —
    프론트가 "원본 위에 박스 그리기"와 "단계별 카드 뷰"를 모두
    만들 수 있도록.
    """

    original: NDArray[np.uint8]
    """디코딩 직후 (EXIF 회전 보정 완료). (H, W, 3)."""

    white_balanced: NDArray[np.uint8]
    """배경 기반 화이트밸런스 적용 후. (H, W, 3)."""

    face_box: tuple[int, int, int, int]
    """검출된 얼굴 박스 (x0, y0, x1, y1). 원본 좌표계."""

    face_crop: NDArray[np.uint8]
    """WB 이미지에서 얼굴 박스(+여유)를 잘라낸 부분."""

    skin_mask: NDArray[np.bool_]
    """최종 피부 마스크. (H, W), 원본 좌표계."""

    masked_skin: NDArray[np.uint8]
    """face_crop에서 피부가 아닌 픽셀을 어둡게 처리한 이미지 —
    "실제로 측정에 쓰인 픽셀"을 사용자에게 그대로 보여준다."""


@dataclass(frozen=True, slots=True)
class PipelineResult:
    """파이프라인 최종 출력.

    skin_pixels가 도메인 계층(extract_features)으로 넘어가는 유일한
    데이터다 — 여기에는 공간 정보가 없다 (P2 대응). 나머지 필드는 전부
    시각화·품질 보고용이다.
    """

    skin_pixels: NDArray[np.uint8]
    """(N, 3) RGB. extract_features()의 입력."""

    stages: PipelineStages
    white_balance: WhiteBalanceResult
    face: DetectedFace
    mask_detail: SkinMask
    """중간 마스크 4종과 Otsu 임계값, coverage_ratio."""


def decode_image(image_bytes: bytes) -> NDArray[np.uint8]:
    """이미지 바이트를 (H, W, 3) uint8 RGB로 디코딩한다.

    cv2.imdecode가 아니라 Pillow를 쓰는 이유: cv2는 EXIF Orientation
    태그를 무시한다. 폰 카메라 세로 사진은 픽셀이 가로로 저장되고
    EXIF로 "90° 돌려라"만 기록되는 경우가 대부분이라, 이걸 무시하면
    얼굴이 누워서 검출률이 급락한다.
    """
    try:
        with Image.open(io.BytesIO(image_bytes)) as img:
            upright = ImageOps.exif_transpose(img)
            # exif_transpose는 회전이 불필요하면 원본을 그대로 돌려줄 수 있다.
            rgb = (upright if upright is not None else img).convert("RGB")
            return np.asarray(rgb, dtype=np.uint8)
    except (UnidentifiedImageError, Image.DecompressionBombError, OSError, ValueError) as exc:
        raise ImageDecodeError(
            "이미지를 해석할 수 없습니다. JPEG/PNG 형식의 손상되지 않은 "
            "파일인지 확인해 주세요."
        ) from exc


def downscale_to_fit(
    image: NDArray[np.uint8], max_edge: int
) -> NDArray[np.uint8]:
    """긴 변이 max_edge를 넘으면 비율을 유지한 채 축소한다.

    INTER_AREA를 쓰는 이유: 축소에서 이것만이 원본 픽셀의 면적 평균을
    낸다. INTER_LINEAR 같은 보간은 원본 픽셀을 '샘플링'하므로 축소
    배율이 크면 에일리어싱이 생기고, 그 결과 색 통계가 흔들린다.
    우리는 색을 재는 파이프라인이므로 이 차이가 중요하다.
    """
    height, width = image.shape[:2]
    longest = max(height, width)
    if longest <= max_edge:
        return image

    scale = max_edge / longest
    target = (max(1, round(width * scale)), max(1, round(height * scale)))
    resized = cv2.resize(image, target, interpolation=cv2.INTER_AREA)
    return np.asarray(resized, dtype=np.uint8)


def _expand_box(
    box: tuple[int, int, int, int],
    margin_ratio: float,
    width: int,
    height: int,
) -> tuple[int, int, int, int]:
    """박스를 비율만큼 넓히고 이미지 경계로 클리핑한다."""
    x0, y0, x1, y1 = box
    margin = round(max(x1 - x0, y1 - y0) * margin_ratio)
    return (
        max(0, x0 - margin),
        max(0, y0 - margin),
        min(width, x1 + margin),
        min(height, y1 + margin),
    )


class PreprocessPipeline:
    """전처리 파이프라인. 검출기는 생성 시 한 번 주입받아 재사용한다."""

    def __init__(
        self,
        detector: SupportsFaceDetection,
        config: PipelineConfig | None = None,
    ) -> None:
        self._detector = detector
        self._config = config or PipelineConfig()

    def run(self, image_bytes: bytes) -> PipelineResult:
        """이미지 바이트에서 피부 픽셀까지 전 단계를 수행한다.

        Raises:
            ImageDecodeError: 디코딩 불가.
            NoFaceDetectedError / MultipleFacesError: 검출 정책 위반.
            InsufficientSkinPixelsError: 마스킹 후 픽셀이 하드 플로어 미만.
        """
        cfg = self._config

        # 축소는 검출보다 먼저 한다. 이후 모든 좌표(랜드마크·박스·마스크)가
        # 같은 좌표계 위에 있어야 시각화 단계에서 재변환이 필요 없다.
        original = downscale_to_fit(decode_image(image_bytes), cfg.max_input_edge)
        height, width = original.shape[:2]

        # ① 검출 — 보정 전 이미지에서. 랜드마크는 기하 정보라 캐스트에 둔감하다.
        face = self._detector.detect(original)

        # ② 배경 기반 화이트밸런스 — 얼굴(+여유)을 조명 추정에서 제외.
        #    배경이 너무 작으면 apply_white_balance가 전체 프레임으로 폴백한다.
        exclusion_box = _expand_box(
            face.bounding_box, cfg.wb_exclusion_margin_ratio, width, height
        )
        background_mask = np.ones((height, width), dtype=np.bool_)
        ex0, ey0, ex1, ey1 = exclusion_box
        background_mask[ey0:ey1, ex0:ex1] = False

        wb = apply_white_balance(
            original, cfg.wb_method, estimation_mask=background_mask
        )

        # ③ 마스킹 — 보정된 색 위에서. 랜드마크 좌표는 그대로 유효하다(기하 불변).
        mask_detail = build_skin_mask(wb.image, face.landmarks, cfg.mask)
        skin_pixels = extract_skin_pixels(wb.image, mask_detail)

        if skin_pixels.shape[0] < cfg.min_skin_pixels:
            raise InsufficientSkinPixelsError(
                pixel_count=int(skin_pixels.shape[0]), minimum=cfg.min_skin_pixels
            )

        # 시각화 스테이지 구성
        crop_box = _expand_box(
            face.bounding_box, cfg.crop_margin_ratio, width, height
        )
        cx0, cy0, cx1, cy1 = crop_box
        face_crop = wb.image[cy0:cy1, cx0:cx1].copy()

        masked_skin = face_crop.copy()
        crop_mask = mask_detail.mask[cy0:cy1, cx0:cx1]
        masked_skin[~crop_mask] //= 6  # 비피부 영역을 어둡게 — 완전 검정보다 맥락이 보인다

        stages = PipelineStages(
            original=original,
            white_balanced=wb.image,
            face_box=face.bounding_box,
            face_crop=face_crop,
            skin_mask=mask_detail.mask,
            masked_skin=masked_skin,
        )

        return PipelineResult(
            skin_pixels=skin_pixels,
            stages=stages,
            white_balance=wb,
            face=face,
            mask_detail=mask_detail,
        )
