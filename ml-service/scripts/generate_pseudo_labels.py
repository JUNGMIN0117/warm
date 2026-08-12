"""이미지 폴더에 규칙 엔진을 돌려 pseudo-label 데이터셋을 만든다 (ADR-002 Phase 2).

    uv run python scripts/generate_pseudo_labels.py --images <원본 폴더> --out data/pseudo

출력 구조:

    data/pseudo/
     ├ labels.csv      파일명 + 계절/언더톤 pseudo-label + 신뢰도·품질
     ├ skipped.csv     실패 목록과 사유 (얼굴 없음 등) — 침묵하는 탈락은 편향이 된다
     ├ crops/          얼굴 크롭 128×128 (윤곽·배경 포함)
     └ masked/         피부 외 픽셀을 어둡게 한 크롭 (원본 2022 방식)

두 입력 변형을 모두 저장하는 이유: P2 실험의 비교 조건이다. crop으로
학습한 모델이 Grad-CAM에서 윤곽·배경에 주목한다면 원본의 우려가 재현된
것이고, masked 학습 모델과의 차이가 곧 마스킹의 가치다.

라벨은 규칙 엔진의 판정이다 — 이 데이터로 잰 CNN "정확도"는 규칙 엔진과의
일치율이지 절대 정확도가 아니다. 그 구분을 문서와 리포트가 유지한다.
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

import cv2

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.domain.classifier import classify
from app.domain.features import extract_features
from app.pipeline.errors import PipelineError
from app.pipeline.face_detector import FaceDetector
from app.pipeline.pipeline import PreprocessPipeline
from scripts.dataset_common import (
    CROP_DIR_NAMES,
    CROP_SIZE,
    LABELS_FILENAME,
    force_utf8_stdout,
)

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}

LABEL_FIELDS = [
    "filename", "season", "undertone", "confidence", "undertone_confidence",
    "quality_factor", "hue_angle", "ita", "chroma", "pixel_count",
]


def main() -> int:
    force_utf8_stdout()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--images", type=Path, required=True, help="원본 이미지 폴더")
    parser.add_argument("--out", type=Path, required=True, help="출력 폴더")
    parser.add_argument("--limit", type=int, default=0, help="최대 처리 장수 (0 = 전부)")
    args = parser.parse_args()

    files = sorted(
        p for p in args.images.rglob("*") if p.suffix.lower() in IMAGE_SUFFIXES
    )
    if args.limit > 0:
        files = files[: args.limit]
    if not files:
        print(f"이미지가 없습니다: {args.images}", file=sys.stderr)
        return 1

    crops_dir = args.out / CROP_DIR_NAMES["crop"]
    masked_dir = args.out / CROP_DIR_NAMES["masked"]
    crops_dir.mkdir(parents=True, exist_ok=True)
    masked_dir.mkdir(parents=True, exist_ok=True)

    # 풀이 아니라 단일 검출기 — 배치 작업은 순차라 동시성이 필요 없다.
    pipeline = PreprocessPipeline(detector=FaceDetector())

    labeled = 0
    skipped: list[tuple[str, str]] = []

    with (args.out / LABELS_FILENAME).open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=LABEL_FIELDS)
        writer.writeheader()

        for index, path in enumerate(files, start=1):
            try:
                result = pipeline.run(path.read_bytes())
                features = extract_features(result.skin_pixels)
                classification = classify(features)
            except PipelineError as exc:
                # 어떤 사진이 왜 탈락했는지 남긴다. 얼굴 검출이 특정
                # 조건(측면, 저조도)에서 계통적으로 실패하면 데이터셋이
                # 조용히 편향되는데, 이 목록이 그것을 보이게 만든다.
                skipped.append((path.name, type(exc).__name__))
                continue

            out_name = f"{path.stem}.png"
            crop = cv2.resize(
                result.stages.face_crop, (CROP_SIZE, CROP_SIZE), interpolation=cv2.INTER_AREA
            )
            masked = cv2.resize(
                result.stages.masked_skin, (CROP_SIZE, CROP_SIZE), interpolation=cv2.INTER_AREA
            )
            # 파이프라인 배열은 RGB, cv2.imwrite는 BGR을 기대한다.
            cv2.imwrite(str(crops_dir / out_name), cv2.cvtColor(crop, cv2.COLOR_RGB2BGR))
            cv2.imwrite(str(masked_dir / out_name), cv2.cvtColor(masked, cv2.COLOR_RGB2BGR))

            writer.writerow({
                "filename": out_name,
                "season": classification.season.value,
                "undertone": classification.undertone.value,
                "confidence": f"{classification.confidence:.4f}",
                "undertone_confidence": f"{classification.undertone_confidence:.4f}",
                "quality_factor": f"{classification.quality_factor:.4f}",
                "hue_angle": f"{features.hue_angle:.2f}",
                "ita": f"{features.ita:.2f}",
                "chroma": f"{features.chroma:.2f}",
                "pixel_count": features.pixel_count,
            })
            labeled += 1

            if index % 100 == 0:
                print(f"  {index}/{len(files)} 처리 (라벨 {labeled}, 탈락 {len(skipped)})")

    with (args.out / "skipped.csv").open("w", encoding="utf-8", newline="") as f:
        writer_s = csv.writer(f)
        writer_s.writerow(["filename", "reason"])
        writer_s.writerows(skipped)

    print(f"완료: 라벨 {labeled}장, 탈락 {len(skipped)}장 → {args.out}")
    print("주의: 이 라벨은 규칙 엔진의 판정(pseudo-label)입니다. 절대 정확도의 근거가 아닙니다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
