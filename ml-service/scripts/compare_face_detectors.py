"""검출기 비교 — 원본의 Haar Cascade vs 재구축의 MediaPipe.

ADR/문서에 "Haar를 버렸다"고 쓰려면 근거가 있어야 한다. 이 스크립트가
그 근거를 만든다.

## 두 개의 벤치마크로 나눈 이유

실존 인물 사진을 저장소에 넣을 수 없다는 제약(CLAUDE.md 금지사항) 때문에
측정 가능한 것과 그렇지 않은 것을 분리했다.

**벤치마크 A — 오검출률 (사진 불필요, 항상 재현 가능)**
얼굴이 하나도 없는 합성 이미지를 주고 "얼굴을 찾았다"고 주장하는
횟수를 센다. 정답이 자명(0개)하므로 라벨링된 사람 사진이 필요 없다.
Haar가 밝기 대비 패턴에 반응한다는 주장을 직접 검증한다.

**벤치마크 B — 검출률·회전 강건성·속도 (로컬 사진 필요)**
사용자가 지정한 로컬 폴더의 사진으로 측정한다. 사진은 저장소에
들어가지 않으며, 결과 수치만 문서에 옮긴다.

사용법:
    # 벤치마크 A만 (사진 없이)
    uv run python scripts/compare_face_detectors.py

    # A + B
    uv run python scripts/compare_face_detectors.py --photos <폴더경로>
"""

from __future__ import annotations

import argparse
import sys
import time
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
from numpy.typing import NDArray

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.pipeline.errors import MultipleFacesError, NoFaceDetectedError
from app.pipeline.face_detector import DEFAULT_MODEL_PATH, FaceDetector

HAAR_PATH = DEFAULT_MODEL_PATH.parent / "haarcascade_frontalface_default.xml"

# 원본 보고서에 기록된 파라미터를 그대로 쓴다: detectMultiScale(gray, 1.2, 3).
# 비교의 목적은 "원본이 실제로 돌린 설정"과의 대조이므로 튜닝하지 않는다.
HAAR_SCALE_FACTOR = 1.2
HAAR_MIN_NEIGHBORS = 3

IMAGE_SUFFIXES = frozenset({".jpg", ".jpeg", ".png", ".bmp", ".webp"})


@dataclass(frozen=True, slots=True)
class DetectorRun:
    """한 이미지에 대한 한 검출기의 결과."""

    face_count: int
    elapsed_ms: float


class HaarDetector:
    """원본 프로젝트의 검출 방식 재현.

    cv2.CascadeClassifier는 OpenCV 5.0에도 남아 있지만 XML 데이터가
    휠에서 제거됐다 — scripts/download_models.py가 업스트림 태그에서
    받아둔 파일을 쓴다.
    """

    def __init__(self, cascade_path: Path = HAAR_PATH) -> None:
        if not cascade_path.exists():
            raise FileNotFoundError(
                f"Haar cascade가 없습니다: {cascade_path}\n"
                "uv run python scripts/download_models.py 를 먼저 실행하세요."
            )
        self._cascade = cv2.CascadeClassifier(str(cascade_path))
        if self._cascade.empty():
            raise RuntimeError(f"cascade 로딩 실패: {cascade_path}")

    def count_faces(self, image_rgb: NDArray[np.uint8]) -> int:
        gray = cv2.cvtColor(image_rgb, cv2.COLOR_RGB2GRAY)
        faces = self._cascade.detectMultiScale(
            gray, HAAR_SCALE_FACTOR, HAAR_MIN_NEIGHBORS
        )
        return len(faces)


class MediaPipeCounter:
    """FaceDetector를 '개수 세기' 인터페이스로 감싼다.

    FaceDetector는 정확히 1명이 아니면 예외를 던지는 것이 제품 정책이라,
    비교 측정을 위해 예외를 개수로 되돌린다.
    """

    def __init__(self) -> None:
        self._detector = FaceDetector()

    def count_faces(self, image_rgb: NDArray[np.uint8]) -> int:
        try:
            self._detector.detect(image_rgb)
        except NoFaceDetectedError:
            return 0
        except MultipleFacesError as exc:
            return exc.face_count
        return 1

    def close(self) -> None:
        self._detector.close()


def _timed(fn: object, image: NDArray[np.uint8]) -> DetectorRun:
    start = time.perf_counter()
    count = fn.count_faces(image)  # type: ignore[attr-defined]
    return DetectorRun(
        face_count=count, elapsed_ms=(time.perf_counter() - start) * 1000.0
    )


# ---------------------------------------------------------------------------
# 벤치마크 A — 비얼굴 이미지 오검출
# ---------------------------------------------------------------------------


def _non_face_images() -> Iterator[tuple[str, NDArray[np.uint8]]]:
    """얼굴이 없는 합성 이미지들.

    무작위 노이즈만 쓰면 너무 쉬운 문제가 된다. Haar가 반응하기 쉬운
    구조 — 밝기 대비가 규칙적으로 반복되는 패턴 — 을 의도적으로 포함한다.
    실제 사진에서 이런 구조는 창틀, 벽돌, 옷 주름, 나뭇잎 사이 빛이다.
    """
    rng = np.random.default_rng(seed=20260810)
    size = (480, 640)

    def to_rgb(gray: NDArray[np.float64]) -> NDArray[np.uint8]:
        clipped = np.clip(gray, 0, 255).astype(np.uint8)
        return np.repeat(clipped[:, :, None], 3, axis=2)

    yield "uniform_gray", to_rgb(np.full(size, 128.0))

    yield "white_noise", to_rgb(rng.normal(128.0, 60.0, size))

    # 벽돌 — 수평·수직 밝기 경계의 격자
    brick = np.full(size, 150.0)
    for row in range(0, size[0], 32):
        brick[row : row + 4, :] = 90.0
        offset = 0 if (row // 32) % 2 == 0 else 40
        for col in range(offset, size[1], 80):
            brick[row : row + 32, col : col + 4] = 90.0
    yield "brick_wall", to_rgb(brick + rng.normal(0, 6, size))

    # 창틀 — 밝은 사각형 + 어두운 프레임. 눈-코-입의 명암 배치와 유사한
    # 저주파 구조가 생긴다.
    window = np.full(size, 70.0)
    for cy in (120, 340):
        for cx in (150, 330, 510):
            window[cy - 70 : cy + 70, cx - 60 : cx + 60] = 220.0
            window[cy - 6 : cy + 6, cx - 60 : cx + 60] = 70.0
            window[cy - 70 : cy + 70, cx - 5 : cx + 5] = 70.0
    yield "window_grid", to_rgb(window + rng.normal(0, 4, size))

    # 나뭇잎 사이 빛 — 불규칙한 고대비 반점
    foliage = np.full(size, 60.0)
    for _ in range(400):
        cy = int(rng.integers(0, size[0]))
        cx = int(rng.integers(0, size[1]))
        radius = int(rng.integers(4, 18))
        cv2.circle(foliage, (cx, cy), radius, float(rng.uniform(120, 245)), -1)
    blurred = np.asarray(cv2.GaussianBlur(foliage, (5, 5), 0), dtype=np.float64)
    yield "dappled_foliage", to_rgb(blurred)

    # 옷 주름 — 부드러운 사인파 음영
    yy, xx = np.mgrid[0 : size[0], 0 : size[1]]
    fabric = 140.0 + 55.0 * np.sin(xx / 17.0) * np.cos(yy / 29.0)
    yield "fabric_folds", to_rgb(fabric + rng.normal(0, 5, size))

    # 피부색 사각형 — 색만으로는 얼굴이 아님을 확인
    skin = np.zeros((*size, 3), dtype=np.float64)
    skin[:] = (150, 160, 175)
    skin[120:360, 200:440] = (224, 172, 138)
    yield "skin_rectangle", np.clip(skin + rng.normal(0, 3, skin.shape), 0, 255).astype(
        np.uint8
    )

    # 만화 얼굴 — Haar의 알려진 실패 메커니즘을 직접 겨냥한다.
    # Viola-Jones 특징은 "눈 부위가 볼보다 어둡다" 같은 저해상도 명암
    # 배치에 반응하므로, 사람 얼굴의 텍스처 없이 그 배치만 재현했을 때
    # 반응하는지 본다. 사람 얼굴이 아니므로 여기서 검출되면 오검출이다
    # (우리 파이프라인 입장에서도 만화의 픽셀을 피부로 측정하면 안 된다).
    cartoon = np.zeros((*size, 3), dtype=np.float64)
    cartoon[:] = (150, 160, 175)
    cv2.ellipse(cartoon, (320, 240), (110, 145), 0, 0, 360, (224, 172, 138), -1)
    for eye_x in (275, 365):
        cv2.ellipse(cartoon, (eye_x, 200), (26, 14), 0, 0, 360, (55, 40, 35), -1)
        cv2.ellipse(cartoon, (eye_x, 168), (30, 8), 0, 0, 360, (45, 32, 28), -1)
    cv2.ellipse(cartoon, (320, 320), (38, 16), 0, 0, 360, (140, 70, 70), -1)
    yield "cartoon_face", np.clip(
        cartoon + rng.normal(0, 3, cartoon.shape), 0, 255
    ).astype(np.uint8)


def run_false_positive_benchmark(
    haar: HaarDetector, mediapipe: MediaPipeCounter
) -> None:
    print("\n" + "=" * 68)
    print("벤치마크 A — 비얼굴 이미지 오검출 (정답: 모두 0)")
    print("=" * 68)
    print(f"{'이미지':<20}{'Haar':>14}{'MediaPipe':>14}")
    print("-" * 68)

    haar_total = 0
    mp_total = 0
    count = 0

    for name, image in _non_face_images():
        haar_run = _timed(haar, image)
        mp_run = _timed(mediapipe, image)
        haar_total += haar_run.face_count
        mp_total += mp_run.face_count
        count += 1

        def mark(n: int) -> str:
            return f"{n} {'OK' if n == 0 else 'FP'}"

        print(f"{name:<20}{mark(haar_run.face_count):>14}{mark(mp_run.face_count):>14}")

    print("-" * 68)
    print(f"{'합계 오검출':<20}{haar_total:>14}{mp_total:>14}")
    print(f"{'오검출 발생 이미지':<20}{'':>14}{'':>14}  (전체 {count}장)")


# ---------------------------------------------------------------------------
# 벤치마크 B — 로컬 사진 (저장소에 커밋되지 않음)
# ---------------------------------------------------------------------------


def _rotate(image: NDArray[np.uint8], degrees: float) -> NDArray[np.uint8]:
    height, width = image.shape[:2]
    matrix = cv2.getRotationMatrix2D((width / 2, height / 2), degrees, 1.0)
    rotated = cv2.warpAffine(
        image, matrix, (width, height), borderMode=cv2.BORDER_REPLICATE
    )
    return np.asarray(rotated, dtype=np.uint8)


def run_photo_benchmark(
    haar: HaarDetector, mediapipe: MediaPipeCounter, folder: Path
) -> None:
    paths = sorted(p for p in folder.iterdir() if p.suffix.lower() in IMAGE_SUFFIXES)
    if not paths:
        print(f"\n[경고] {folder}에 이미지가 없습니다. 벤치마크 B를 건너뜁니다.")
        return

    print("\n" + "=" * 68)
    print(f"벤치마크 B — 로컬 사진 {len(paths)}장 (1인 정면 가정)")
    print("=" * 68)

    angles = (0.0, 15.0, 30.0)
    hits = {"haar": dict.fromkeys(angles, 0), "mediapipe": dict.fromkeys(angles, 0)}
    times: dict[str, list[float]] = {"haar": [], "mediapipe": []}

    for path in paths:
        data = np.fromfile(path, dtype=np.uint8)  # 한글 경로 대응
        bgr = cv2.imdecode(data, cv2.IMREAD_COLOR)
        if bgr is None:
            print(f"  [skip] 디코딩 실패: {path.name}")
            continue
        rgb = np.asarray(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB), dtype=np.uint8)

        for angle in angles:
            rotated = _rotate(rgb, angle) if angle else rgb
            for label, detector in (("haar", haar), ("mediapipe", mediapipe)):
                run = _timed(detector, rotated)
                if run.face_count == 1:
                    hits[label][angle] += 1
                if angle == 0.0:
                    times[label].append(run.elapsed_ms)

    total = len(paths)
    print(f"{'회전':<12}{'Haar 검출률':>18}{'MediaPipe 검출률':>20}")
    print("-" * 68)
    for angle in angles:
        h = hits["haar"][angle] / total * 100
        m = hits["mediapipe"][angle] / total * 100
        print(f"{f'{angle:.0f}도':<12}{f'{h:.1f}%':>18}{f'{m:.1f}%':>20}")

    print("-" * 68)
    for label in ("haar", "mediapipe"):
        samples = times[label]
        if samples:
            print(
                f"{label:<12} 중앙값 {np.median(samples):6.1f} ms  "
                f"(최소 {min(samples):.1f} / 최대 {max(samples):.1f})"
            )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--photos",
        type=Path,
        default=None,
        help="1인 정면 사진 폴더 (저장소 밖 경로. 커밋 금지)",
    )
    args = parser.parse_args()

    haar = HaarDetector()
    mediapipe = MediaPipeCounter()
    try:
        run_false_positive_benchmark(haar, mediapipe)
        if args.photos is not None:
            run_photo_benchmark(haar, mediapipe, args.photos)
        else:
            print(
                "\n[안내] --photos 를 주면 검출률·회전 강건성·속도(벤치마크 B)도 "
                "측정합니다.\n        사진은 저장소 밖에 두세요 (CLAUDE.md 금지사항)."
            )
    finally:
        mediapipe.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
