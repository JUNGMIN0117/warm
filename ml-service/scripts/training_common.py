"""Step 5 학습·평가 스크립트의 공통 코드.

## 왜 app/ 이 아니라 scripts/ 인가

CNN은 **대조군**이다 (ADR-002). 서비스 런타임은 규칙 엔진으로 동작하며,
torch는 'train' 의존성 그룹에만 있어 배포 이미지에 들어가지 않는다.
Phase 3 평가에서 CNN이 이기기 전까지 서빙 코드에 모델이 등장할 이유가
없고, 그 경계를 디렉터리로 강제한다.

## 라벨의 출처

여기서 다루는 라벨은 규칙 엔진이 생성한 **pseudo-label**이다. 즉 이
데이터로 학습한 CNN의 "정확도"는 어디까지나 "규칙 엔진과의 일치율"이며,
절대 정확도는 Phase 3의 수동 검증셋에서만 말할 수 있다.
"""

from __future__ import annotations

import csv
import hashlib
import io
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING, Final

import numpy as np
import torch
from torch import Tensor, nn

if TYPE_CHECKING:
    from numpy.typing import NDArray

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


def load_crop_tensor(path: Path) -> Tensor:
    """크롭 PNG → (3, H, W) float32 [0, 1] 텐서.

    torchvision을 쓰지 않는 이유: 필요한 변환이 "읽고 0~1로 나누기"뿐이라
    의존성 하나를 더 얹을 이유가 없다. 정규화(mean/std)도 안 한다 —
    입력이 이미 화이트밸런스를 거친 데다, 색 자체가 신호인 문제에서
    채널 평균을 빼는 것은 신호를 지우는 쪽에 가깝다.
    """
    from PIL import Image

    with Image.open(path) as img:
        rgb = img.convert("RGB")
        array: NDArray[np.uint8] = np.asarray(rgb, dtype=np.uint8)
    return torch.from_numpy(array).permute(2, 0, 1).float() / 255.0


class SmallCnn(nn.Module):
    """원본 프로젝트와 같은 급의 소형 CNN.

    Conv 64 → 128 → 256 → 256 + Dense 128 → 32 → num_classes.
    원본(2022) 구조를 의도적으로 따른다 — 이 모델은 성능 경쟁이 아니라
    "원본 방식이 무엇을 학습하는가"를 재현·관찰하는 대조군이므로,
    현대적 아키텍처로 바꾸면 실험의 의미가 흐려진다. BatchNorm만
    추가했는데, 없으면 CPU 소규모 학습에서 수렴 자체가 불안정해
    관찰 이전에 학습이 안 된다.
    """

    def __init__(self, num_classes: int) -> None:
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(3, 64, 3, padding=1), nn.BatchNorm2d(64), nn.ReLU(), nn.MaxPool2d(2),
            nn.Conv2d(64, 128, 3, padding=1), nn.BatchNorm2d(128), nn.ReLU(), nn.MaxPool2d(2),
            nn.Conv2d(128, 256, 3, padding=1), nn.BatchNorm2d(256), nn.ReLU(), nn.MaxPool2d(2),
            nn.Conv2d(256, 256, 3, padding=1), nn.BatchNorm2d(256), nn.ReLU(), nn.MaxPool2d(2),
        )
        self.classifier = nn.Sequential(
            nn.AdaptiveAvgPool2d(1),
            nn.Flatten(),
            nn.Linear(256, 128), nn.ReLU(), nn.Dropout(0.3),
            nn.Linear(128, 32), nn.ReLU(),
            nn.Linear(32, num_classes),
        )

    def forward(self, x: Tensor) -> Tensor:
        # nn.Sequential.__call__은 Any를 반환하므로 타입을 명시한다.
        out: Tensor = self.classifier(self.features(x))
        return out

    @property
    def last_conv(self) -> nn.Module:
        """Grad-CAM이 훅을 걸 마지막 합성곱 블록의 ReLU."""
        return self.features[-2]


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


def resolve_device(requested: str) -> torch.device:
    """auto면 CUDA 가용 시 GPU, 아니면 CPU."""
    if requested != "auto":
        return torch.device(requested)
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")
