"""API 응답 스키마 — Spring이 소비할 계약.

## 왜 도메인 dataclass를 그대로 직렬화하지 않는가

`SkinFeatures`나 `ClassificationResult`를 그대로 JSON으로 뱉으면 편하지만,
그렇게 하면 **도메인 리팩터링이 곧 API 파괴**가 된다. 필드 이름 하나 바꾸면
Spring의 DTO와 프론트의 타입이 동시에 깨진다.

Pydantic 모델을 따로 두고 명시적으로 변환하면 그 결합이 끊긴다.
도메인은 자유롭게 진화하고, 이 계층이 번역을 맡는다. 변환 함수가
깨지면 컴파일(mypy) 단계에서 잡히므로 조용한 불일치도 생기지 않는다.

## 무엇을 담지 않는가 (ADR-005)

**팔레트·한국어 라벨·스타일링 팁은 응답에 없다.** 이것들은 큐레이션
결과물이지 측정 결과가 아니므로 Spring이 DB에서 소유한다. ML 서비스를
재배포하지 않고 팔레트를 갱신할 수 있어야 하고, 다국어 라벨도
프레젠테이션 계층의 몫이다.

여기서 나가는 것은 `"autumn_warm"` 같은 **enum 값**까지다. 그것을
"🍂 가을 웜"으로 만드는 것은 Spring의 일이다.
"""

from __future__ import annotations

from typing import Self

from pydantic import BaseModel, ConfigDict, Field

from ..domain.classifier import ClassificationResult
from ..domain.features import SkinFeatures
from ..domain.seasons import Season, Undertone
from ..pipeline.pipeline import PipelineResult
from ..pipeline.white_balance import WhiteBalanceResult


class SkinFeaturesSchema(BaseModel):
    """측정된 피부 색채 통계. 판정의 근거이자 UI 게이지의 원본 데이터."""

    model_config = ConfigDict(frozen=True)

    lightness: float = Field(description="L* — 명도 (0~100)")
    a_star: float = Field(description="a* — 녹(-)↔적(+)")
    b_star: float = Field(description="b* — 청(-)↔황(+). 언더톤의 1차 신호")
    chroma: float = Field(description="C* — 채도")
    hue_angle: float = Field(description="h° — 색상각(도)")
    ita: float = Field(description="ITA° — 개인 유형 각도")
    ita_category: str = Field(description="ITA° 표준 6단계 구간명")
    lightness_spread: float = Field(description="L* 사분위 범위 — 조명 균일도")
    pixel_count: int = Field(description="통계에 사용된 피부 픽셀 수")
    median_rgb: tuple[int, int, int] = Field(description="대표 피부색(중앙값) RGB")

    @classmethod
    def from_domain(cls, features: SkinFeatures) -> Self:
        return cls(
            lightness=features.lightness,
            a_star=features.a_star,
            b_star=features.b_star,
            chroma=features.chroma,
            hue_angle=features.hue_angle,
            ita=features.ita,
            ita_category=features.ita_category,
            lightness_spread=features.lightness_spread,
            pixel_count=features.pixel_count,
            median_rgb=features.mean_rgb,
        )


class AxisReadingSchema(BaseModel):
    """한 축의 판정 근거. 프론트가 게이지로 렌더링한다."""

    model_config = ConfigDict(frozen=True)

    name: str = Field(description="undertone | depth | clarity")
    raw_value: float = Field(description="원본 측정값 (h°, ITA°, C*)")
    normalized: float = Field(ge=0.0, le=1.0, description="0~1로 정규화된 좌표")
    low_label: str
    high_label: str
    interpretation: str


class WhiteBalanceSchema(BaseModel):
    """화이트밸런스 보정량. "무엇을 얼마나 건드렸는지"의 공개."""

    model_config = ConfigDict(frozen=True)

    method: str = Field(description="gray_world | white_patch")
    gains: tuple[float, float, float] = Field(
        description="선형 공간에서 R·G·B에 곱한 게인. (1,1,1)이면 무보정"
    )
    cast_strength: float = Field(
        description="입력에 있던 색 캐스트 세기. max(gains)/min(gains) - 1"
    )

    @classmethod
    def from_domain(cls, wb: WhiteBalanceResult) -> Self:
        return cls(
            method=wb.method.value, gains=wb.gains, cast_strength=wb.cast_strength
        )


class MaskQualitySchema(BaseModel):
    """마스킹 단계의 품질 지표."""

    model_config = ConfigDict(frozen=True)

    coverage_ratio: float = Field(
        description="얼굴 타원 대비 최종 마스크 비율. 정상 정면 사진이면 0.4~0.8"
    )
    otsu_threshold: float = Field(description="얼굴 내부에서 계산된 Otsu 임계값")


class StageImagesSchema(BaseModel):
    """전처리 단계별 이미지 (base64 data URI).

    `include_stages=true`일 때만 채워진다. 기본적으로 빼는 이유는
    응답 크기 때문이다 — Spring이 이력 저장 목적으로 호출할 때는
    수치만 있으면 되고, 프론트가 시각화할 때만 필요하다.
    """

    model_config = ConfigDict(frozen=True)

    original: str = Field(description="디코딩 직후 (EXIF 회전 보정 완료)")
    white_balanced: str = Field(description="화이트밸런스 적용 후")
    face_crop: str = Field(description="얼굴 영역 크롭")
    skin_mask: str = Field(description="최종 피부 마스크 (흑백)")
    measured_pixels: str = Field(description="실제로 측정에 쓰인 픽셀만 남긴 이미지")


class AnalysisResponse(BaseModel):
    """`POST /v1/analyze` 성공 응답.

    확률 분포를 통째로 담는 것이 설계의 핵심이다. top-1만 주면
    "62% 봄 / 35% 여름"인 경계 케이스와 "97% 겨울"인 확실한 케이스를
    소비자가 구분할 수 없다 (도메인 불변식 4).
    """

    model_config = ConfigDict(frozen=True)

    season: Season = Field(description="최상위 계절 타입")
    confidence: float = Field(
        ge=0.0, le=1.0, description="확률 최댓값 × 품질 계수"
    )
    probabilities: dict[Season, float] = Field(
        description="4계절 전체 확률 분포. 합은 1.0"
    )

    undertone: Undertone = Field(description="웜/쿨 2분류")
    undertone_confidence: float = Field(
        ge=0.0,
        le=1.0,
        description="언더톤 신뢰도. 4분류를 병합한 것이라 항상 더 견고하다",
    )

    axes: tuple[AxisReadingSchema, ...] = Field(description="3축 판정 근거")
    features: SkinFeaturesSchema
    white_balance: WhiteBalanceSchema
    mask_quality: MaskQualitySchema

    quality_factor: float = Field(
        ge=0.0, le=1.0, description="입력 품질 계수. 픽셀 수와 조명 균일도로 산출"
    )
    warnings: tuple[str, ...] = Field(
        default=(), description="사용자에게 보여줄 경고 (한국어)"
    )

    stages: StageImagesSchema | None = Field(
        default=None, description="include_stages=true일 때만 채워진다"
    )

    @classmethod
    def from_domain(
        cls,
        pipeline_result: PipelineResult,
        classification: ClassificationResult,
        features: SkinFeatures,
        stages: StageImagesSchema | None,
    ) -> Self:
        return cls(
            season=classification.season,
            confidence=classification.confidence,
            probabilities=dict(classification.probabilities),
            undertone=classification.undertone,
            undertone_confidence=classification.undertone_confidence,
            axes=tuple(
                AxisReadingSchema(
                    name=axis.name,
                    raw_value=axis.raw_value,
                    normalized=axis.normalized,
                    low_label=axis.low_label,
                    high_label=axis.high_label,
                    interpretation=axis.interpretation,
                )
                for axis in classification.axes
            ),
            features=SkinFeaturesSchema.from_domain(features),
            white_balance=WhiteBalanceSchema.from_domain(pipeline_result.white_balance),
            mask_quality=MaskQualitySchema(
                coverage_ratio=pipeline_result.mask_detail.coverage_ratio,
                otsu_threshold=pipeline_result.mask_detail.otsu_threshold,
            ),
            quality_factor=classification.quality_factor,
            warnings=classification.warnings,
            stages=stages,
        )


class ErrorResponse(BaseModel):
    """실패 응답. FastAPI 기본 `{"detail": "..."}` 대신 이 형태로 통일한다.

    `code`가 있는 이유: Spring과 프론트가 **문자열 매칭 없이** 분기해야
    한다. 메시지는 한국어이고 문구가 바뀔 수 있지만 코드는 계약이다.
    """

    model_config = ConfigDict(frozen=True)

    code: str = Field(description="기계가 읽는 오류 코드. 예: NO_FACE_DETECTED")
    message: str = Field(description="사용자에게 그대로 보여줄 수 있는 한국어 설명")
    detail: dict[str, int | str] | None = Field(
        default=None, description="코드별 부가 정보. 예: 검출된 얼굴 수"
    )


class HealthResponse(BaseModel):
    """`GET /health` 응답."""

    model_config = ConfigDict(frozen=True)

    status: str = Field(description="ok | degraded")
    model_loaded: bool = Field(description="얼굴 검출 모델이 로드되었는가")
    detector_pool_size: int
    version: str
