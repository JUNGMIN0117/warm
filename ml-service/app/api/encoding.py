"""단계 이미지 인코딩 — numpy 배열을 base64 data URI로.

## 왜 base64 인라인인가 (ADR-005)

대안은 이미지를 서버에 저장하고 URL을 주는 것이었다. 훨씬 작은 응답을
만들 수 있지만 **FastAPI 무상태 불변식을 깬다** — 토큰과 이미지를
어딘가에 보관해야 하고, 그 순간 이 서비스는 저장소·만료 정책·정리
작업을 갖게 된다. 아키텍처의 핵심 경계(상태는 Spring에)를 전처리
시각화 편의를 위해 무너뜨릴 이유가 없다.

그래서 인라인으로 보내되 크기를 통제한다.
- 기본은 미포함. `include_stages=true`일 때만 채운다.
- 최대 변 512px로 축소 — 시각화용이라 원본 해상도가 무의미하다.
- WebP 손실 압축 — 같은 품질에서 PNG의 1/5~1/10, JPEG보다도 작다.

마스크만 예외로 무손실 WebP를 쓴다. 이진 이미지를 손실 압축하면
경계에 링잉이 생겨서 "어디까지가 마스크인가"가 흐려지는데, 그건
이 이미지의 존재 이유(측정 범위를 정확히 보여주기)와 정면으로 충돌한다.
"""

from __future__ import annotations

import base64
import io

import numpy as np
from numpy.typing import NDArray
from PIL import Image

from ..pipeline.pipeline import PipelineStages
from .schemas import StageImagesSchema


def _resize_to_fit(image: Image.Image, max_edge: int) -> Image.Image:
    """긴 변이 max_edge를 넘으면 비율을 유지한 채 축소한다."""
    longest = max(image.size)
    if longest <= max_edge:
        return image

    scale = max_edge / longest
    target = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    # LANCZOS: 축소 시 에일리어싱이 가장 적다. 시각화 품질에 직결된다.
    return image.resize(target, Image.Resampling.LANCZOS)


def _to_data_uri(image: Image.Image, *, quality: int, lossless: bool) -> str:
    buffer = io.BytesIO()
    image.save(buffer, format="WEBP", quality=quality, lossless=lossless, method=4)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"data:image/webp;base64,{encoded}"


def encode_rgb(
    array: NDArray[np.uint8], *, max_edge: int, quality: int
) -> str:
    """(H, W, 3) uint8 RGB 배열을 base64 WebP data URI로."""
    image = _resize_to_fit(Image.fromarray(array, mode="RGB"), max_edge)
    return _to_data_uri(image, quality=quality, lossless=False)


def encode_mask(array: NDArray[np.bool_], *, max_edge: int) -> str:
    """(H, W) bool 마스크를 흑백 무손실 WebP data URI로.

    NEAREST로 축소하는 이유: 이진 마스크를 보간하면 0도 255도 아닌
    중간값이 생겨 "마스크에 포함된 픽셀"의 정의가 흐려진다.
    """
    grayscale = Image.fromarray((array.astype(np.uint8) * 255), mode="L")

    longest = max(grayscale.size)
    if longest > max_edge:
        scale = max_edge / longest
        target = (
            max(1, round(grayscale.width * scale)),
            max(1, round(grayscale.height * scale)),
        )
        grayscale = grayscale.resize(target, Image.Resampling.NEAREST)

    return _to_data_uri(grayscale, quality=100, lossless=True)


def encode_stages(
    stages: PipelineStages, *, max_edge: int, quality: int
) -> StageImagesSchema:
    """파이프라인 단계 전체를 응답 스키마로 인코딩한다."""
    return StageImagesSchema(
        original=encode_rgb(stages.original, max_edge=max_edge, quality=quality),
        white_balanced=encode_rgb(
            stages.white_balanced, max_edge=max_edge, quality=quality
        ),
        face_crop=encode_rgb(stages.face_crop, max_edge=max_edge, quality=quality),
        skin_mask=encode_mask(stages.skin_mask, max_edge=max_edge),
        measured_pixels=encode_rgb(
            stages.masked_skin, max_edge=max_edge, quality=quality
        ),
    )
