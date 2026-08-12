"""규칙 엔진 대비 CNN 평가 + Grad-CAM P2 검증 (ADR-002 Phase 2~3).

    uv run --group train python scripts/evaluate_models.py \\
        --data data/pseudo --model models/cnn_undertone_crop.pt \\
        [--manual data/manual_labels.csv] --gradcam 12

세 가지를 잰다.

1. **pseudo 일치율** — val 분할에서 CNN이 규칙 엔진 판정과 얼마나 일치하나.
   높다고 좋은 게 아니라 "규칙 엔진을 얼마나 복제했나"의 지표다.
2. **ECE (기대 캘리브레이션 오차)** — 모델이 80%라고 말할 때 실제로
   80% 맞는가. 확률을 그대로 노출하는 서비스라 정확도만큼 중요하다.
3. **Grad-CAM** — 모델이 판정할 때 어디를 봤는가. 피부가 아니라 윤곽·배경이
   달아오르면 원본(2022)의 우려(P2)가 재현된 것이다.

--manual로 수동 라벨 CSV(filename,label)를 주면 규칙 엔진과 CNN을 같은
검증셋에서 비교한다 — **절대 정확도는 이때만 말할 수 있다** (Phase 3).
"""

from __future__ import annotations

import argparse
import csv
import itertools
import json
import sys
from pathlib import Path

import cv2
import numpy as np
import torch
from torch import Tensor

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from scripts.training_common import (
    CROP_DIR_NAMES,
    LabeledCrop,
    SmallCnn,
    force_utf8_stdout,
    is_validation,
    load_crop_tensor,
    read_labels,
    resolve_device,
)


def expected_calibration_error(
    confidences: list[float], hits: list[bool], bins: int = 10
) -> float:
    """ECE — 신뢰도 구간별 |평균 신뢰도 - 실제 적중률|의 가중 평균."""
    edges = np.linspace(0.0, 1.0, bins + 1)
    conf = np.asarray(confidences)
    hit = np.asarray(hits, dtype=np.float64)
    ece = 0.0
    for low, high in itertools.pairwise(edges):
        mask = (conf > low) & (conf <= high)
        if not mask.any():
            continue
        ece += (mask.mean()) * abs(conf[mask].mean() - hit[mask].mean())
    return float(ece)


def gradcam_heatmap(model: SmallCnn, image: Tensor, class_index: int) -> np.ndarray:
    """Grad-CAM — 마지막 합성곱 활성에 클래스 그래디언트를 가중해 평균.

    라이브러리를 쓰지 않는 이유: 필요한 것이 훅 두 개와 가중 평균뿐이고,
    "모델이 어디를 봤는가"를 주장하는 코드는 안이 보여야 신뢰할 수 있다.
    """
    activations: list[Tensor] = []
    gradients: list[Tensor] = []

    def forward_hook(_m: torch.nn.Module, _i: tuple[Tensor, ...], output: Tensor) -> None:
        activations.append(output.detach())

    def backward_hook(
        _m: torch.nn.Module, _gi: tuple[Tensor, ...], grad_output: tuple[Tensor, ...]
    ) -> None:
        gradients.append(grad_output[0].detach())

    handle_f = model.last_conv.register_forward_hook(forward_hook)
    handle_b = model.last_conv.register_full_backward_hook(backward_hook)
    try:
        model.eval()
        logits = model(image.unsqueeze(0))
        model.zero_grad()
        logits[0, class_index].backward()
    finally:
        handle_f.remove()
        handle_b.remove()

    weights = gradients[0].mean(dim=(2, 3), keepdim=True)      # (1, C, 1, 1)
    cam = torch.relu((weights * activations[0]).sum(dim=1))[0]  # (h, w)
    cam_np = np.asarray(cam.numpy(), dtype=np.float32)
    if cam_np.max() > 0:
        cam_np = cam_np / cam_np.max()
    return cam_np


def save_gradcam_overlay(crop_path: Path, cam: np.ndarray, out_path: Path) -> None:
    bgr = cv2.imread(str(crop_path))
    if bgr is None:
        raise FileNotFoundError(f"크롭을 읽을 수 없습니다: {crop_path}")
    heat = cv2.resize(cam, (bgr.shape[1], bgr.shape[0]))
    colored = cv2.applyColorMap((heat * 255).astype(np.uint8), cv2.COLORMAP_JET)
    overlay = cv2.addWeighted(bgr, 0.55, colored, 0.45, 0)
    cv2.imwrite(str(out_path), overlay)


def read_manual_labels(path: Path) -> dict[str, str]:
    """수동 검증 라벨 (filename,label). Phase 3의 절대 기준."""
    labels: dict[str, str] = {}
    with path.open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            labels[row["filename"]] = row["label"].strip()
    return labels


def main() -> int:
    force_utf8_stdout()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--model", type=Path, required=True, help=".pt 가중치 경로")
    parser.add_argument("--manual", type=Path, default=None, help="수동 라벨 CSV (Phase 3)")
    parser.add_argument("--gradcam", type=int, default=8, help="히트맵 저장 장수")
    parser.add_argument("--out", type=Path, default=Path("reports"))
    parser.add_argument("--device", default="cpu")
    args = parser.parse_args()

    meta = json.loads(
        args.model.with_suffix("").with_suffix(".meta.json").read_text(encoding="utf-8")
    )
    classes: list[str] = meta["classes"]
    target: str = meta["target"]
    variant: str = meta["variant"]
    crop_dir = args.data / CROP_DIR_NAMES[variant]

    device = resolve_device(args.device)
    model = SmallCnn(num_classes=len(classes))
    model.load_state_dict(torch.load(args.model, weights_only=True, map_location=device))
    model.eval()

    rows = [r for r in read_labels(args.data) if is_validation(r.filename)]
    if not rows:
        print("val 분할에 표본이 없습니다.", file=sys.stderr)
        return 1

    def rule_label(row: LabeledCrop) -> str:
        return row.undertone if target == "undertone" else row.season

    # ── CNN 추론 (val 전체) ─────────────────────────────────────
    predictions: dict[str, tuple[str, float]] = {}
    with torch.no_grad():
        for row in rows:
            tensor = load_crop_tensor(crop_dir / row.filename)
            probs = torch.softmax(model(tensor.unsqueeze(0))[0], dim=0)
            top = int(probs.argmax())
            predictions[row.filename] = (classes[top], float(probs[top]))

    # ── 1) pseudo 일치율 + 혼동 행렬 ───────────────────────────
    agree = [predictions[r.filename][0] == rule_label(r) for r in rows]
    confusion: dict[str, dict[str, int]] = {c: dict.fromkeys(classes, 0) for c in classes}
    for row in rows:
        confusion[rule_label(row)][predictions[row.filename][0]] += 1

    # ── 2) ECE (pseudo 기준) ────────────────────────────────────
    ece = expected_calibration_error(
        [predictions[r.filename][1] for r in rows], agree
    )

    report: dict[str, object] = {
        "model": args.model.name,
        "target": target,
        "variant": variant,
        "val_size": len(rows),
        "pseudo_agreement": round(sum(agree) / len(agree), 4),
        "ece_vs_pseudo": round(ece, 4),
        "confusion_rule_vs_cnn": confusion,
        "note": "pseudo_agreement는 규칙 엔진과의 일치율이지 절대 정확도가 아니다 (ADR-002)",
    }

    # ── 3) Phase 3 — 수동 라벨이 있을 때만 절대 정확도 ─────────
    if args.manual is not None:
        manual = read_manual_labels(args.manual)
        judged = [r for r in rows if r.filename in manual]
        if judged:
            rule_acc = sum(rule_label(r) == manual[r.filename] for r in judged) / len(judged)
            cnn_acc = sum(
                predictions[r.filename][0] == manual[r.filename] for r in judged
            ) / len(judged)
            report["manual"] = {
                "size": len(judged),
                "rule_engine_accuracy": round(rule_acc, 4),
                "cnn_accuracy": round(cnn_acc, 4),
            }
        else:
            report["manual"] = {"size": 0}

    # ── 4) Grad-CAM ────────────────────────────────────────────
    gradcam_dir = args.out / "gradcam"
    gradcam_dir.mkdir(parents=True, exist_ok=True)
    for row in rows[: args.gradcam]:
        tensor = load_crop_tensor(crop_dir / row.filename)
        predicted = classes.index(predictions[row.filename][0])
        cam = gradcam_heatmap(model, tensor, predicted)
        save_gradcam_overlay(
            crop_dir / row.filename,
            cam,
            gradcam_dir / f"{Path(row.filename).stem}_{predictions[row.filename][0]}.png",
        )

    args.out.mkdir(parents=True, exist_ok=True)
    report_path = args.out / f"eval_{args.model.stem}.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(json.dumps(report, ensure_ascii=False, indent=2))
    print(f"\n리포트: {report_path} / Grad-CAM: {gradcam_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
