"""FastAPI 애플리케이션 — 무상태 추론 서비스.

## 이 서비스가 하지 않는 것

DB도, 인증도, 세션도 없다 (ADR-001). 입력은 이미지 바이트, 출력은 JSON,
그 사이에 아무것도 남기지 않는다. 이력 저장·사용자 관리·팔레트 추천은
전부 Spring의 몫이다.

이 경계를 지키는 실질적 이유가 있다. 무상태이므로 이 서비스는
- 수평 확장이 자유롭고 (어느 인스턴스로 가든 같은 답),
- 재시작이 무해하며 (잃을 상태가 없다),
- Spring 쪽에서 이미지 해시로 캐시하기 쉽다 (같은 입력 → 같은 출력).

마지막 항목 때문에 응답은 **결정론적**이어야 한다. 난수도, 시각도,
요청 카운터도 응답에 넣지 않는다.

## 왜 동기 엔드포인트인가

추론은 CPU 바운드다. `async def`로 만들면 이벤트 루프를 점유해 다른
요청의 I/O까지 막는다. 동기 `def`로 두면 FastAPI가 스레드풀에서
실행하므로 이벤트 루프가 살아 있다. 대신 검출기 동시 접근 문제가
생기는데, 그건 DetectorPool이 해결한다.
"""

from __future__ import annotations

from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Annotated, Final

from fastapi import Depends, FastAPI, File, Query, Request, UploadFile
from fastapi.responses import JSONResponse

from ..domain.classifier import classify
from ..domain.features import extract_features
from ..pipeline.errors import ImageDecodeError, ModelNotFoundError, PipelineError
from ..pipeline.face_detector import FaceDetector
from ..pipeline.pipeline import PipelineConfig, PreprocessPipeline
from .detector_pool import DetectorPool
from .encoding import encode_stages
from .error_mapping import error_detail, map_error
from .schemas import AnalysisResponse, ErrorResponse, HealthResponse
from .settings import Settings, get_settings

API_VERSION: Final = "0.1.0"

# 경로에 버전을 박는다. Spring이 우리 스키마에 강하게 결합되므로
# 호환 불가 변경이 필요할 때 /v2를 병행 운영할 여지를 미리 둔다.
V1: Final = "/v1"


@dataclass(slots=True)
class ServiceState:
    """앱 수명 동안 유지되는 무거운 객체들.

    전역 변수 대신 app.state에 담는 이유: 테스트가 앱 인스턴스를
    여러 개 만들어도 서로 간섭하지 않는다.
    """

    pipeline: PreprocessPipeline | None
    pool: DetectorPool | None = None
    pool_size: int = 0
    """/health가 보고하는 검출기 수. 풀을 쓰지 않는 구성(테스트)에서도
    값을 명시할 수 있도록 풀에서 파생하지 않고 별도 필드로 둔다."""

    @property
    def ready(self) -> bool:
        return self.pipeline is not None


StateBuilder = Callable[[Settings], ServiceState]


def build_state(settings: Settings) -> ServiceState:
    """검출기 풀과 파이프라인을 구성한다.

    모델이 없으면 예외를 던지지 않고 '준비 안 됨' 상태로 기동한다.
    기동 자체가 실패하면 컨테이너가 크래시 루프에 빠지고 /health로
    원인을 알릴 방법조차 없어진다. 뜨긴 뜨되 상태를 정직하게 보고하는
    편이 운영에서 진단 가능하다.
    """
    try:
        pool = DetectorPool(
            size=settings.detector_pool_size,
            factory=lambda: FaceDetector(model_path=settings.model_path),
        )
    except ModelNotFoundError:
        return ServiceState(pipeline=None)

    pipeline = PreprocessPipeline(
        detector=pool,
        config=PipelineConfig(max_input_edge=settings.max_input_edge),
    )
    return ServiceState(pipeline=pipeline, pool=pool, pool_size=pool.size)


def get_state(request: Request) -> ServiceState:
    return request.app.state.service  # type: ignore[no-any-return]


def create_app(state_builder: StateBuilder = build_state) -> FastAPI:
    """앱을 만든다.

    `state_builder`가 주입 지점인 이유: 테스트는 MediaPipe 모델 없이
    돌아야 한다. 여기를 열어두면 가짜 검출기를 넣은 파이프라인으로
    HTTP 계층 전체(라우팅·검증·예외 매핑·직렬화)를 검증할 수 있고,
    모델 유무와 무관하게 CI가 회귀를 잡는다.
    """

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        """기동 시 모델을 로드하고 종료 시 해제한다.

        첫 요청에 로딩을 미루면 그 요청만 수백 ms 느려지고, 컨테이너
        오케스트레이터의 readiness 판정도 거짓말이 된다.
        """
        state = state_builder(get_settings())
        app.state.service = state
        try:
            yield
        finally:
            if state.pool is not None:
                state.pool.close()

    app = FastAPI(
        title="Personal Color ML Service",
        version=API_VERSION,
        summary="얼굴 사진에서 피부 색채를 측정하고 4계절 퍼스널 컬러를 판정한다.",
        description=(
            "무상태 추론 서비스. 이미지 바이트를 받아 측정값과 판정을 반환한다.\n\n"
            "팔레트·스타일링 추천·한국어 라벨은 이 서비스가 제공하지 않는다 — "
            "큐레이션 결과물이므로 Spring 게이트웨이가 DB에서 소유한다 (ADR-005)."
        ),
        lifespan=lifespan,
    )

    @app.exception_handler(PipelineError)
    async def handle_pipeline_error(
        request: Request, exc: PipelineError
    ) -> JSONResponse:
        mapping = map_error(exc)
        payload = ErrorResponse(
            code=mapping.code, message=str(exc), detail=error_detail(exc)
        )
        return JSONResponse(status_code=mapping.status, content=payload.model_dump())

    @app.get(
        "/health",
        response_model=HealthResponse,
        summary="상태 확인",
        description=(
            "모델 로드 여부를 함께 보고한다. 프로세스가 살아 있어도 모델이 "
            "없으면 분석은 불가능하므로 status는 degraded가 된다."
        ),
    )
    def health(state: Annotated[ServiceState, Depends(get_state)]) -> HealthResponse:
        return HealthResponse(
            status="ok" if state.ready else "degraded",
            model_loaded=state.ready,
            detector_pool_size=state.pool_size,
            version=API_VERSION,
        )

    @app.post(
        f"{V1}/analyze",
        response_model=AnalysisResponse,
        responses={
            400: {"model": ErrorResponse, "description": "이미지 디코딩 실패"},
            413: {"model": ErrorResponse, "description": "업로드 크기 초과"},
            422: {
                "model": ErrorResponse,
                "description": "얼굴 없음 / 여러 명 / 피부 픽셀 부족",
            },
            503: {"model": ErrorResponse, "description": "모델 미로드"},
        },
        summary="퍼스널 컬러 분석",
    )
    def analyze(
        state: Annotated[ServiceState, Depends(get_state)],
        settings: Annotated[Settings, Depends(get_settings)],
        image: Annotated[UploadFile, File(description="분석할 얼굴 사진 (JPEG/PNG)")],
        include_stages: Annotated[
            bool,
            Query(
                description=(
                    "전처리 단계 이미지를 base64로 함께 반환할지. "
                    "응답이 커지므로 시각화가 필요할 때만 켠다."
                )
            ),
        ] = False,
    ) -> AnalysisResponse | JSONResponse:
        if state.pipeline is None:
            raise ModelNotFoundError(
                "얼굴 검출 모델이 로드되지 않았습니다. 서버 구성을 확인해 주세요."
            )

        payload = image.file.read(settings.max_upload_bytes + 1)
        if len(payload) > settings.max_upload_bytes:
            limit_mb = settings.max_upload_bytes / (1024 * 1024)
            return JSONResponse(
                status_code=413,
                content=ErrorResponse(
                    code="FILE_TOO_LARGE",
                    message=f"파일이 너무 큽니다. {limit_mb:.0f}MB 이하로 올려 주세요.",
                    detail={"max_bytes": settings.max_upload_bytes},
                ).model_dump(),
            )
        if not payload:
            raise ImageDecodeError("빈 파일입니다. 이미지를 첨부했는지 확인해 주세요.")

        result = state.pipeline.run(payload)
        features = extract_features(result.skin_pixels)
        classification = classify(features)

        stages = (
            encode_stages(
                result.stages,
                max_edge=settings.stage_image_edge,
                quality=settings.stage_image_quality,
            )
            if include_stages
            else None
        )

        return AnalysisResponse.from_domain(
            pipeline_result=result,
            classification=classification,
            features=features,
            stages=stages,
        )

    return app


app = create_app()
