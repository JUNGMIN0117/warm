"""파이프라인 예외를 HTTP 응답으로 번역한다.

## 왜 매핑 테이블을 따로 두는가

파이프라인은 HTTP를 몰라야 한다 (도메인이 이미지 I/O를 모르는 것과
같은 이유). 그래서 예외에 상태 코드를 붙이지 않고, 이 계층이 번역을
맡는다. 파이프라인을 나중에 배치 작업이나 CLI로 재사용해도 HTTP
어휘가 따라붙지 않는다.

## 4xx와 5xx를 가르는 기준

**사용자가 다시 시도해서 고칠 수 있는가**가 기준이다.
- 얼굴이 없다 / 여러 명이다 / 사진이 깨졌다 → 사진을 바꾸면 된다 (4xx)
- 모델 파일이 없다 → 사용자가 할 수 있는 게 없다 (5xx)

이 구분이 중요한 이유는 Spring의 Resilience4j 때문이다. 서킷 브레이커는
5xx를 장애 신호로 세는데, "얼굴 없는 사진"을 5xx로 내보내면 사용자가
잘못된 사진을 여러 장 올린 것만으로 회로가 열려 정상 요청까지 막힌다.
"""

from __future__ import annotations

from dataclasses import dataclass
from http import HTTPStatus

from ..pipeline.errors import (
    ImageDecodeError,
    InsufficientSkinPixelsError,
    ModelNotFoundError,
    MultipleFacesError,
    NoFaceDetectedError,
    PipelineError,
)


@dataclass(frozen=True, slots=True)
class ErrorMapping:
    code: str
    status: int


# 예외 타입 → (오류 코드, HTTP 상태). 서브클래스보다 정확한 매치를
# 우선하기 위해 순서대로 isinstance를 검사한다.
_MAPPINGS: tuple[tuple[type[PipelineError], ErrorMapping], ...] = (
    (
        NoFaceDetectedError,
        ErrorMapping("NO_FACE_DETECTED", HTTPStatus.UNPROCESSABLE_ENTITY),
    ),
    (
        MultipleFacesError,
        ErrorMapping("MULTIPLE_FACES", HTTPStatus.UNPROCESSABLE_ENTITY),
    ),
    (
        InsufficientSkinPixelsError,
        ErrorMapping("INSUFFICIENT_SKIN_PIXELS", HTTPStatus.UNPROCESSABLE_ENTITY),
    ),
    (
        ImageDecodeError,
        ErrorMapping("IMAGE_DECODE_FAILED", HTTPStatus.BAD_REQUEST),
    ),
    (
        ModelNotFoundError,
        ErrorMapping("MODEL_NOT_AVAILABLE", HTTPStatus.SERVICE_UNAVAILABLE),
    ),
)

_FALLBACK = ErrorMapping("PIPELINE_ERROR", HTTPStatus.UNPROCESSABLE_ENTITY)


def map_error(error: PipelineError) -> ErrorMapping:
    """예외 인스턴스에 대응하는 코드와 상태를 찾는다."""
    for error_type, mapping in _MAPPINGS:
        if isinstance(error, error_type):
            return mapping
    return _FALLBACK


def error_detail(error: PipelineError) -> dict[str, int | str] | None:
    """코드별 부가 정보. 소비자가 문자열 파싱 없이 쓸 수 있게 구조화한다."""
    if isinstance(error, MultipleFacesError):
        return {"face_count": error.face_count}
    if isinstance(error, InsufficientSkinPixelsError):
        return {"pixel_count": error.pixel_count, "minimum": error.minimum}
    return None
