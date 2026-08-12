"""pseudo-label 데이터셋으로 대조군 CNN을 학습한다 (ADR-002 Phase 2).

    uv run --group train python scripts/train_cnn.py --data data/pseudo --target undertone

출력: models/cnn_{target}_{variant}.pt (가중치 + 메타) / 같은 이름의 .onnx

기본 target은 undertone(웜/쿨 2분류)이다 — 원본(2022)이 푼 문제와 같아
비교가 공정하고, 4계절보다 pseudo-label의 잡음이 적다. season(4분류)도
--target으로 선택할 수 있다.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path

import torch
from torch import Tensor, nn
from torch.utils.data import DataLoader, Dataset

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from scripts.dataset_common import (
    CROP_DIR_NAMES,
    CROP_SIZE,
    LabeledCrop,
    classes_for_target,
    force_utf8_stdout,
    is_validation,
    read_labels,
)
from scripts.training_common import SmallCnn, load_crop_tensor, resolve_device


@dataclass(frozen=True, slots=True)
class Sample:
    path: Path
    label: int


class CropDataset(Dataset[tuple[Tensor, int]]):
    """크롭 PNG + 정수 라벨. 증강은 하지 않는다 — 색이 신호인 문제에서
    color jitter는 라벨을 파괴하고, 기하 증강(flip)만으로는 이 규모에서
    체감 이득이 없다. 대조군은 단순할수록 해석이 쉽다."""

    def __init__(self, samples: list[Sample]) -> None:
        self._samples = samples

    def __len__(self) -> int:
        return len(self._samples)

    def __getitem__(self, index: int) -> tuple[Tensor, int]:
        sample = self._samples[index]
        return load_crop_tensor(sample.path), sample.label


def build_samples(
    data_dir: Path, rows: list[LabeledCrop], variant: str, target: str,
    min_confidence: float, validation: bool,
) -> list[Sample]:
    """분할·신뢰도 필터를 적용해 샘플 목록을 만든다.

    min_confidence로 pseudo-label의 저신뢰 판정(경계 케이스)을 학습에서
    뺄 수 있다 — 잡음 라벨을 줄이는 대신 경계 영역의 데이터가 사라지는
    교환이므로 기본값은 0(전부 사용)이다.
    """
    classes = classes_for_target(target)
    crop_dir = data_dir / CROP_DIR_NAMES[variant]
    samples: list[Sample] = []
    for row in rows:
        if is_validation(row.filename) != validation:
            continue
        if row.confidence < min_confidence:
            continue
        value = row.undertone if target == "undertone" else row.season
        samples.append(Sample(path=crop_dir / row.filename, label=classes.index(value)))
    return samples


def class_weights(samples: list[Sample], num_classes: int) -> Tensor:
    """역빈도 가중치. pseudo-label 분포는 데이터셋의 인구 구성을 따라
    치우치기 쉬운데, 소수 클래스를 포기하는 모델이 "잘하는 것처럼"
    보이는 것을 막는다."""
    counts = torch.zeros(num_classes)
    for sample in samples:
        counts[sample.label] += 1
    weights = counts.sum() / (num_classes * counts.clamp(min=1))
    return weights


def run_epoch(
    model: SmallCnn, loader: DataLoader[tuple[Tensor, int]], device: torch.device,
    criterion: nn.Module, optimizer: torch.optim.Optimizer | None,
) -> tuple[float, float]:
    """한 epoch. optimizer가 None이면 평가 모드."""
    training = optimizer is not None
    model.train(training)
    total_loss = 0.0
    correct = 0
    seen = 0
    with torch.set_grad_enabled(training):
        for inputs, labels in loader:
            inputs = inputs.to(device)
            labels = labels.to(device)
            logits = model(inputs)
            loss = criterion(logits, labels)
            if optimizer is not None:
                optimizer.zero_grad()
                loss.backward()
                optimizer.step()
            total_loss += float(loss) * len(labels)
            correct += int((logits.argmax(dim=1) == labels).sum())
            seen += len(labels)
    return total_loss / max(seen, 1), correct / max(seen, 1)


def main() -> int:
    force_utf8_stdout()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True, help="generate_pseudo_labels 출력 폴더")
    parser.add_argument("--target", choices=["undertone", "season"], default="undertone")
    parser.add_argument("--variant", choices=["crop", "masked"], default="crop")
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--min-confidence", type=float, default=0.0)
    parser.add_argument("--device", default="auto")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out-dir", type=Path, default=Path("models"))
    args = parser.parse_args()

    torch.manual_seed(args.seed)
    device = resolve_device(args.device)
    classes = classes_for_target(args.target)

    rows = read_labels(args.data)
    train_samples = build_samples(
        args.data, rows, args.variant, args.target, args.min_confidence, validation=False
    )
    val_samples = build_samples(
        args.data, rows, args.variant, args.target, min_confidence=0.0, validation=True
    )
    print(f"train {len(train_samples)} / val {len(val_samples)} (device={device.type})")
    if len(train_samples) < len(classes) * 10:
        print("경고: 학습 표본이 매우 적습니다. 결과는 파이프라인 점검용으로만 보세요.")

    train_loader: DataLoader[tuple[Tensor, int]] = DataLoader(
        CropDataset(train_samples), batch_size=args.batch_size, shuffle=True
    )
    val_loader: DataLoader[tuple[Tensor, int]] = DataLoader(
        CropDataset(val_samples), batch_size=args.batch_size
    )

    model = SmallCnn(num_classes=len(classes)).to(device)
    criterion = nn.CrossEntropyLoss(weight=class_weights(train_samples, len(classes)).to(device))
    optimizer = torch.optim.Adam(model.parameters(), lr=args.lr)

    best_val_acc = 0.0
    args.out_dir.mkdir(parents=True, exist_ok=True)
    stem = f"cnn_{args.target}_{args.variant}"
    weight_path = args.out_dir / f"{stem}.pt"

    for epoch in range(1, args.epochs + 1):
        train_loss, train_acc = run_epoch(model, train_loader, device, criterion, optimizer)
        _, val_acc = run_epoch(model, val_loader, device, criterion, optimizer=None)
        marker = ""
        if val_acc >= best_val_acc:
            best_val_acc = val_acc
            torch.save(model.state_dict(), weight_path)
            marker = "  ← 저장"
        print(
            f"epoch {epoch:2d}/{args.epochs}  loss {train_loss:.4f}  "
            f"train {train_acc:.3f}  val(pseudo 일치율) {val_acc:.3f}{marker}"
        )

    # 메타를 가중치 옆에 남긴다 — 평가 스크립트가 target/variant를 다시
    # 묻지 않아도 되고, "이 모델이 무슨 조건으로 학습됐나"가 파일로 남는다.
    meta = {
        "target": args.target, "variant": args.variant, "classes": list(classes),
        "crop_size": CROP_SIZE, "epochs": args.epochs, "seed": args.seed,
        "min_confidence": args.min_confidence,
        "train_size": len(train_samples), "val_size": len(val_samples),
        "best_val_pseudo_agreement": round(best_val_acc, 4),
        "label_source": "rule-engine pseudo-label (절대 정확도 아님, ADR-002)",
    }
    (args.out_dir / f"{stem}.meta.json").write_text(
        json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    # ONNX — 서빙이 결정되면(Phase 3 이후) Java(DJL)든 어디든 가져갈 수
    # 있는 중립 포맷을 미리 만들어 둔다.
    model.load_state_dict(torch.load(weight_path, weights_only=True))
    model.eval().to("cpu")
    dummy = torch.zeros(1, 3, CROP_SIZE, CROP_SIZE)
    torch.onnx.export(
        model, (dummy,), str(args.out_dir / f"{stem}.onnx"),
        input_names=["image"], output_names=["logits"],
        dynamic_axes={"image": {0: "batch"}, "logits": {0: "batch"}},
    )
    print(f"저장: {weight_path} / .meta.json / .onnx  (best val 일치율 {best_val_acc:.3f})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
