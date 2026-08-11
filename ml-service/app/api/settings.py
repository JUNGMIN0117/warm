"""ML 서비스 런타임 설정.

CalibrationConfig(도메인)·MaskConfig(파이프라인)와 같은 원칙이다 —
운영 중 바뀔 수 있는 값을 코드에 흩뿌리지 않고 한 곳에 모은다.
차이는 이쪽이 **환경변수로 주입 가능**하다는 점이다. 알고리즘 상수는
코드와 함께 버전 관리되어야 하지만, 워커 수나 업로드 한도 같은 운영
파라미터는 배포 환경마다 달라야 하기 때문이다.

접두사 `PCAI_`를 붙여 다른 프로세스의 환경변수와 충돌하지 않게 한다.
"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

from ..pipeline.face_detector import DEFAULT_MODEL_PATH


class Settings(BaseSettings):
    """환경변수로 덮어쓸 수 있는 런타임 설정."""

    model_config = SettingsConfigDict(
        env_prefix="PCAI_",
        env_file=".env",
        extra="ignore",
    )

    model_path: Path = DEFAULT_MODEL_PATH
    """MediaPipe Face Landmarker 가중치 경로."""

    detector_pool_size: int = Field(default=2, ge=1, le=16)
    """동시에 유지할 검출기 인스턴스 수.

    MediaPipe FaceLandmarker는 스레드 안전이 보장되지 않는다. FastAPI가
    동기 엔드포인트를 스레드풀에서 돌리므로 인스턴스를 공유하면 경합이
    난다. 인스턴스당 모델을 따로 로드하므로 메모리와 동시성의 교환이며,
    기본 2는 "단일 컨테이너에서 요청 하나가 다른 하나를 완전히 막지는
    않는" 최소값이다. 처리량이 필요하면 프로세스를 늘리는 쪽이 낫다.
    """

    max_upload_bytes: int = Field(default=12 * 1024 * 1024, ge=1)
    """업로드 허용 크기. 최신 폰의 고화질 JPEG가 8~10MB까지 나온다."""

    max_input_edge: int = Field(default=1600, ge=256)
    """입력 이미지의 최대 변 길이. 이보다 크면 축소해서 처리한다.

    축소하는 이유는 속도만이 아니다. 4000px 사진은 피부 픽셀이 수백만
    개가 되는데, 중앙값 통계는 수만 개면 이미 수렴하므로 나머지는
    순수한 비용이다. 축소는 화이트밸런스보다 먼저 적용되며, 색 통계에는
    영향이 거의 없다(면적 평균이므로).
    """

    stage_image_edge: int = Field(default=512, ge=64)
    """응답에 실을 단계 이미지의 최대 변 길이."""

    stage_image_quality: int = Field(default=80, ge=1, le=100)
    """단계 이미지 WebP 품질. 시각화용이라 원본 충실도가 필요 없다."""

    log_format: str = Field(default="plain", pattern="^(plain|json)$")
    """로그 출력 형식. 컨테이너는 json(수집기가 필드 검색), 로컬은 plain."""


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """설정 싱글턴. FastAPI 의존성으로 주입하고 테스트에서 오버라이드한다."""
    return Settings()
