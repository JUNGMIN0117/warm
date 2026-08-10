"""검출기 풀 — MediaPipe의 스레드 안전성 문제에 대한 대응.

## 왜 풀이 필요한가

FastAPI는 `def`(비동기가 아닌) 엔드포인트를 스레드풀에서 실행한다.
우리 추론은 CPU 바운드라 `async def`로 만들면 이벤트 루프를 막으므로
동기 엔드포인트가 맞는 선택이다. 그런데 그러면 여러 요청이 **동시에
같은 FaceLandmarker 인스턴스를 건드릴 수 있다.**

MediaPipe Tasks의 Python 래퍼는 스레드 안전을 보장하지 않는다. 내부
그래프가 상태를 들고 있어서 동시 호출 시 결과가 섞이거나 죽을 수 있다.

## 검토한 선택지

**전역 락** — 가장 단순하지만 처리량이 인스턴스 하나로 고정된다.
요청이 완전히 직렬화되어 두 번째 요청은 첫 번째가 끝날 때까지 통째로
기다린다.

**요청마다 새 인스턴스** — 모델 로딩이 수백 ms라 요청당 그 비용을
치르는 것은 말이 안 된다.

**스레드 로컬** — 스레드풀이 스레드를 재사용하므로 그럴듯하지만,
스레드 수를 우리가 통제하지 못해 인스턴스가 몇 개나 생길지 알 수 없다.
메모리 사용량이 예측 불가능해진다.

**고정 크기 풀 ✅** — 인스턴스 수를 설정으로 못 박고 큐로 빌린다.
메모리 상한이 명확하고, 풀이 비면 대기한다(거절이 아니라 지연).
"""

from __future__ import annotations

import queue
from collections.abc import Callable, Iterator
from contextlib import contextmanager
from types import TracebackType

import numpy as np
from numpy.typing import NDArray

from ..pipeline.face_detector import DetectedFace, FaceDetector


class DetectorPool:
    """FaceDetector 인스턴스의 고정 크기 풀.

    `SupportsFaceDetection` 프로토콜을 직접 구현하므로 `PreprocessPipeline`
    하나에 이 풀을 통째로 주입할 수 있다 — 파이프라인은 자기가 풀을
    쓰는지 단일 검출기를 쓰는지 알 필요가 없다.
    """

    def __init__(
        self,
        size: int,
        factory: Callable[[], FaceDetector],
    ) -> None:
        if size < 1:
            raise ValueError(f"풀 크기는 1 이상이어야 합니다: {size}")

        self._pool: queue.Queue[FaceDetector] = queue.Queue(maxsize=size)
        self._created: list[FaceDetector] = []

        for _ in range(size):
            detector = factory()
            self._created.append(detector)
            self._pool.put(detector)

    @property
    def size(self) -> int:
        return len(self._created)

    @contextmanager
    def _borrow(self) -> Iterator[FaceDetector]:
        """검출기 하나를 빌린다. 풀이 비어 있으면 반납될 때까지 기다린다.

        타임아웃을 두지 않는 이유: 여기서 타임아웃을 걸면 부하 상황에서
        503을 뱉게 되는데, 그 판단은 서비스 경계(Spring의 Resilience4j
        타임아웃)에서 하는 편이 낫다. 이 계층은 순서만 지킨다.
        """
        detector = self._pool.get()
        try:
            yield detector
        finally:
            self._pool.put(detector)

    def detect(self, image_rgb: NDArray[np.uint8]) -> DetectedFace:
        """SupportsFaceDetection 구현. 풀에서 빌려 추론하고 반납한다."""
        with self._borrow() as detector:
            return detector.detect(image_rgb)

    def close(self) -> None:
        """모든 인스턴스의 네이티브 리소스를 해제한다."""
        for detector in self._created:
            detector.close()
        self._created.clear()

    def __enter__(self) -> DetectorPool:
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        tb: TracebackType | None,
    ) -> None:
        self.close()
