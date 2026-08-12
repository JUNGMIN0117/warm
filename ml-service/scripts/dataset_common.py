"""pseudo-label 데이터셋의 형식 정의 — torch 없이 쓰는 공통 코드.

training_common(torch 의존)에서 분리한 이유: 라벨링 도구처럼 학습과
무관한 소비자가 데이터셋 형식만 필요할 때 torch 설치를 요구하지 않기
위해서다. 데이터셋의 "형식"과 학습의 "도구"는 다른 수명을 가진다.
"""

from __future__ import annotations

import csv
import hashlib
import io
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Final

# 라벨 어휘 — 도메인의 Season/Undertone enum 코드와 일치해야 한다.
# (scripts는 도메인을 임포트할 수 있지만, CSV를 거치며 문자열이 되므로
#  여기서 순서를 고정해 클래스 인덱스의 단일 출처로 삼는다)
UNDERTONE_CLASSES: Final = ("warm", "cool")
SEASON_CLASSES: Final = ("spring_warm", "summer_cool", "autumn_warm", "winter_cool")

CROP_SIZE: Final = 128
"""학습 입력 한 변. 원본 프로젝트의 128×128을 그대로 따른다 —
대조군은 조건을 원본과 비슷하게 맞출수록 비교가 공정하다."""

LABELS_FILENAME: Final = "labels.csv"
CROP_DIR_NAMES: Final = {"crop": "crops", "masked": "masked"}
"""입력 변형 두 가지. crop = 얼굴 크롭 그대로(윤곽·배경 포함),
masked = 피부 외 픽셀을 어둡게 한 원본 방식. Grad-CAM으로 P2를 검증할
때 crop 학습 모델이 윤곽에 주목하는지가 핵심 관찰 대상이다."""


@dataclass(frozen=True, slots=True)
class LabeledCrop:
    """labels.csv 한 줄 — 크롭 파일과 pseudo-label의 쌍."""

    filename: str
    season: str
    undertone: str
    confidence: float
    undertone_confidence: float
    quality_factor: float


def read_labels(data_dir: Path) -> list[LabeledCrop]:
    """labels.csv를 읽는다. generate_pseudo_labels.py의 출력 형식."""
    rows: list[LabeledCrop] = []
    with (data_dir / LABELS_FILENAME).open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            rows.append(
                LabeledCrop(
                    filename=row["filename"],
                    season=row["season"],
                    undertone=row["undertone"],
                    confidence=float(row["confidence"]),
                    undertone_confidence=float(row["undertone_confidence"]),
                    quality_factor=float(row["quality_factor"]),
                )
            )
    return rows


def is_validation(filename: str, val_ratio: float = 0.2) -> bool:
    """파일명 해시로 결정하는 train/val 분할.

    난수 분할 대신 해시를 쓰는 이유: 스크립트를 언제 다시 돌려도, 데이터가
    늘어나도, 같은 파일은 항상 같은 쪽에 속한다. 평가 스크립트가 학습과
    독립적으로 같은 분할을 재현할 수 있어야 "val에서 쟀다"가 성립한다.
    """
    digest = hashlib.sha256(filename.encode("utf-8")).digest()
    return (digest[0] / 255.0) < val_ratio


def classes_for_target(target: str) -> tuple[str, ...]:
    if target == "undertone":
        return UNDERTONE_CLASSES
    if target == "season":
        return SEASON_CLASSES
    raise ValueError(f"알 수 없는 target: {target!r} (undertone | season)")


def force_utf8_stdout() -> None:
    """Windows 콘솔(cp949) 대응.

    torch의 ONNX 익스포터가 이모지를 print해서 cp949 인코딩으로는
    UnicodeEncodeError로 죽고, 우리 한국어 출력도 깨진다. 스크립트
    진입 시 한 번 호출한다.
    """
    for stream in (sys.stdout, sys.stderr):
        if isinstance(stream, io.TextIOWrapper) and stream.encoding.lower() != "utf-8":
            stream.reconfigure(encoding="utf-8")
