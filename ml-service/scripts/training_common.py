"""Step 5 학습·평가 스크립트의 공통 코드 (torch 의존).

데이터셋 형식(라벨 어휘·CSV 스키마·분할 규칙)은 dataset_common에 있다 —
torch가 필요 없는 소비자(라벨링 도구)가 그쪽만 임포트한다.

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

from typing import TYPE_CHECKING

import numpy as np
import torch
from torch import Tensor, nn

if TYPE_CHECKING:
    from pathlib import Path

    from numpy.typing import NDArray


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


def resolve_device(requested: str) -> torch.device:
    """auto면 CUDA 가용 시 GPU, 아니면 CPU."""
    if requested != "auto":
        return torch.device(requested)
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")
