"""MediaPipe 모델 가중치 다운로드 스크립트.

모델 가중치는 저장소에 커밋하지 않는다 (CLAUDE.md 금지사항). 대신 이
스크립트가 재현성을 보장한다 — URL은 'latest'가 아니라 **버전 고정**이고,
SHA-256 해시를 검증하므로 언제 실행해도 같은 바이트를 받는다.

사용법:
    cd ml-service && uv run python scripts/download_models.py
"""

from __future__ import annotations

import hashlib
import sys
import urllib.request
from dataclasses import dataclass
from pathlib import Path

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"


@dataclass(frozen=True, slots=True)
class ModelSpec:
    filename: str
    url: str
    sha256: str
    size_bytes: int


MODELS: tuple[ModelSpec, ...] = (
    ModelSpec(
        filename="face_landmarker.task",
        # float16/1 — 'latest' 대신 버전을 고정해 재현성을 지킨다.
        url=(
            "https://storage.googleapis.com/mediapipe-models/"
            "face_landmarker/face_landmarker/float16/1/face_landmarker.task"
        ),
        sha256="64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
        size_bytes=3_758_596,
    ),
    ModelSpec(
        # 원본 프로젝트(2022)가 쓰던 Haar Cascade. 검출기 비교
        # (scripts/compare_face_detectors.py)에만 쓰이고 서비스 경로에는
        # 들어가지 않는다.
        #
        # OpenCV 5.0부터 이 XML들이 파이썬 휠에서 제거됐다 —
        # cv2.data.haarcascades 디렉터리가 비어 있다. 그래서 업스트림
        # 저장소의 4.10.0 태그에서 직접 받는다 (브랜치가 아니라 태그로
        # 고정해야 재현성이 유지된다).
        filename="haarcascade_frontalface_default.xml",
        url=(
            "https://raw.githubusercontent.com/opencv/opencv/4.10.0/"
            "data/haarcascades/haarcascade_frontalface_default.xml"
        ),
        sha256="0f7d4527844eb514d4a4948e822da90fbb16a34a0bbbbc6adc6498747a5aafb0",
        size_bytes=930_127,
    ),
)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download(spec: ModelSpec) -> None:
    dest = MODELS_DIR / spec.filename

    if dest.exists() and _sha256(dest) == spec.sha256:
        print(f"[skip] {spec.filename} — 이미 존재, 해시 일치")
        return

    print(f"[down] {spec.filename} ← {spec.url} ({spec.size_bytes:,} bytes)")
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(spec.url, dest)

    actual = _sha256(dest)
    if actual != spec.sha256:
        dest.unlink()
        raise RuntimeError(
            f"{spec.filename} 해시 불일치 — 예상 {spec.sha256}, 실제 {actual}. "
            "업스트림이 바뀌었거나 다운로드가 손상됐습니다."
        )
    print(f"[ ok ] {spec.filename} — SHA-256 검증 통과")


def main() -> int:
    for spec in MODELS:
        download(spec)
    return 0


if __name__ == "__main__":
    sys.exit(main())
