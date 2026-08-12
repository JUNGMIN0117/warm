"""언더톤 구엔진 vs 신엔진 A/B — ADR-010 재보정의 판정 실험.

    uv run python scripts/ab_test_undertone.py --data data/pseudo \\
        --manual data/manual_labels_round2.csv

## 왜 라운드 2 라벨이어야 하는가

신엔진의 파라미터는 라운드 1(76장)에 적합됐다. 같은 76장으로 재면
자기 채점이다 — 이 스크립트는 적합에 쓰지 않은 새 라벨로만 잰다.
라운드 1 파일을 --manual로 주면 신엔진 쪽 숫자는 무효다.

## 두 엔진의 판정을 어디서 얻는가

- **구엔진(h° 62)**: labels.csv의 `undertone` 열 — pseudo-label 생성 시점
  (재보정 전)에 구엔진이 실제로 낸 판정의 기록이다. 코드가 바뀐 지금도
  그 기록은 유효한 구엔진 출력이다.
- **신엔진**: labels.csv에 저장된 특징(h°·ITA·C*)에서 b*·L*을 복원해
  현재 코드의 classify()를 그대로 돌린다. 파이프라인 재실행 없이
  같은 측정값 위에서 규칙만 바뀐 비교가 성립한다.
"""

from __future__ import annotations

import argparse
import csv
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.domain.classifier import classify
from app.domain.features import SkinFeatures
from scripts.dataset_common import LabeledCrop, force_utf8_stdout, read_labels


def reconstruct_features(row: LabeledCrop, hue: float, ita: float, chroma: float) -> SkinFeatures:
    """CSV의 측정값에서 SkinFeatures를 복원한다.

    b* = C*·sin(h°), a* = C*·cos(h°), L*는 ITA 정의(atan((L-50)/b*))의 역산.
    lightness_spread·mean_rgb는 언더톤 판정에 영향이 없어(품질 계수 전용)
    중립값을 넣는다.
    """
    b_star = chroma * math.sin(math.radians(hue))
    a_star = chroma * math.cos(math.radians(hue))
    lightness = 50.0 + b_star * math.tan(math.radians(ita))
    return SkinFeatures(
        lightness=lightness,
        a_star=a_star,
        b_star=b_star,
        chroma=chroma,
        hue_angle=hue,
        ita=ita,
        lightness_spread=0.0,
        pixel_count=10_000,
        mean_rgb=(0, 0, 0),
    )


def main() -> int:
    force_utf8_stdout()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--manual", type=Path, required=True, help="라운드 2 수동 라벨 CSV")
    args = parser.parse_args()

    with args.manual.open(encoding="utf-8", newline="") as f:
        manual = {r["filename"]: r["label"].strip() for r in csv.DictReader(f)}

    raw_rows: dict[str, dict[str, str]] = {}
    with (args.data / "labels.csv").open(encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            raw_rows[r["filename"]] = r

    rows = read_labels(args.data)
    judged = [r for r in rows if r.filename in manual]
    if not judged:
        print("수동 라벨과 겹치는 표본이 없습니다.", file=sys.stderr)
        return 1

    old_hits = 0
    new_hits = 0
    old_right_new_wrong = 0
    old_wrong_new_right = 0
    for row in judged:
        truth = manual[row.filename]
        raw = raw_rows[row.filename]
        old_pred = row.undertone  # 생성 시점 구엔진 판정의 기록
        new_pred = classify(
            reconstruct_features(
                row,
                hue=float(raw["hue_angle"]),
                ita=float(raw["ita"]),
                chroma=float(raw["chroma"]),
            )
        ).undertone.value

        old_ok = old_pred == truth
        new_ok = new_pred == truth
        old_hits += old_ok
        new_hits += new_ok
        old_right_new_wrong += old_ok and not new_ok
        old_wrong_new_right += (not old_ok) and new_ok

    n = len(judged)
    ci = 1.96 * math.sqrt(0.25 / n)  # 보수적(최대분산) 95% 반폭
    print(f"라운드 2 표본: {n}장 (확신 케이스 한정, 95% CI 약 ±{ci * 100:.0f}%p)")
    print(f"  구엔진 (h° 62):          {old_hits}/{n} = {old_hits / n:.3f}")
    print(f"  신엔진 (b*+L*, ADR-010): {new_hits}/{n} = {new_hits / n:.3f}")
    print(
        f"  판정이 갈린 표본 — 신엔진만 정답 {old_wrong_new_right} / "
        f"구엔진만 정답 {old_right_new_wrong}"
    )
    print("주의: 확신 케이스 한정 수치다. 조건 없이 인용하지 말 것.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
