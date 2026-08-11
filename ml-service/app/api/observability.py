"""요청 관측성 — 상관관계 ID 수신과 구조화 로그.

## 이 서비스는 ID를 발급하지 않고 **수신**한다

상관관계 ID의 발급 주체는 게이트웨이(Spring) 하나다 (ADR-008). 이 서비스는
`X-Request-Id` 헤더로 받은 값을 요청 컨텍스트에 바인딩해 모든 로그 줄에
싣고, 응답 헤더로 되돌려준다. 헤더가 없으면(직접 호출, 테스트) 자체
발급하되, 그것은 예외 경로다 — 정상 트래픽은 항상 게이트웨이를 거친다.

## contextvar를 쓰는 이유

이 서비스의 엔드포인트는 동기 함수라 스레드풀에서 돈다. threading.local은
"미들웨어(이벤트 루프 스레드)에서 넣고 핸들러(워커 스레드)에서 읽는" 경로가
끊기지만, `contextvars`는 anyio가 컨텍스트를 복사해 워커 스레드로 넘기므로
끊기지 않는다. 무상태 원칙과도 충돌하지 않는다 — 요청이 끝나면 사라지는
요청 스코프 값이지, 요청 사이에 남는 상태가 아니다.
"""

from __future__ import annotations

import json
import logging
import re
import time
import uuid
from contextvars import ContextVar
from typing import TYPE_CHECKING, Final

from starlette.middleware.base import BaseHTTPMiddleware

if TYPE_CHECKING:
    from collections.abc import Awaitable, Callable

    from starlette.requests import Request
    from starlette.responses import Response

HEADER_NAME: Final = "X-Request-Id"

# 게이트웨이(CorrelationId.java)와 같은 형식 규칙 — 헤더는 외부 입력이므로
# 개행·제어문자가 로그를 오염시키지 못하게 형식을 강제한다.
_VALID_ID: Final = re.compile(r"[A-Za-z0-9._-]{8,64}")

_request_id: ContextVar[str | None] = ContextVar("request_id", default=None)

_request_logger = logging.getLogger("http.request")


def current_request_id() -> str | None:
    """현재 요청의 상관관계 ID. 요청 밖에서는 None."""
    return _request_id.get()


class RequestContextMiddleware(BaseHTTPMiddleware):
    """상관관계 ID 바인딩 + 요청 완료 로그.

    게이트웨이의 RequestObservabilityFilter와 대칭이다: ID를 컨텍스트에
    묶고, 응답 헤더로 돌려주고, 요청당 완료 로그 한 줄을 남긴다.
    /health는 DEBUG로 낮춘다 — 10초마다 오는 프로브가 INFO를 도배하면
    정작 봐야 할 줄이 묻힌다.
    """

    async def dispatch(
        self, request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        incoming = request.headers.get(HEADER_NAME, "")
        request_id = incoming if _VALID_ID.fullmatch(incoming) else uuid.uuid4().hex

        token = _request_id.set(request_id)
        start = time.perf_counter()
        try:
            response = await call_next(request)
        finally:
            _request_id.reset(token)

        elapsed_ms = (time.perf_counter() - start) * 1000
        response.headers[HEADER_NAME] = request_id

        level = logging.DEBUG if request.url.path == "/health" else logging.INFO
        _request_logger.log(
            level,
            "%s %s -> %d (%.0fms)",
            request.method,
            request.url.path,
            response.status_code,
            elapsed_ms,
            extra={"request_id": request_id, "status": response.status_code},
        )
        return response


class _RequestIdInjector(logging.Filter):
    """모든 로그 레코드에 request_id를 주입한다.

    미들웨어가 넘긴 extra가 없어도(파이프라인 내부 로그 등) 컨텍스트에서
    읽어 채우므로, 요청이 남기는 모든 줄이 같은 ID로 묶인다.
    """

    def filter(self, record: logging.LogRecord) -> bool:
        if not hasattr(record, "request_id"):
            record.request_id = current_request_id()
        return True


class JsonLogFormatter(logging.Formatter):
    """최소 JSON 로그 포매터.

    structlog 같은 라이브러리를 넣지 않은 이유: 필요한 것이 "한 줄 = JSON
    객체 하나, request_id 필드 포함"이 전부라 표준 라이브러리로 충분하다.
    키 이름은 게이트웨이의 ECS 출력과 정확히 같출 수 없지만(포맷 자체가
    다르다), 검색에 쓰는 request_id·level·message는 맞춰 둔다.
    """

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, object] = {
            "timestamp": self.formatTime(record, "%Y-%m-%dT%H:%M:%S%z"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        request_id = getattr(record, "request_id", None)
        if request_id is not None:
            payload["request_id"] = request_id
        status = getattr(record, "status", None)
        if status is not None:
            payload["status"] = status
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)


def configure_logging(log_format: str) -> None:
    """루트 로거를 구성한다. uvicorn 기동 전후 언제 불러도 안전하게 멱등.

    json: 컨테이너용 — 수집기가 필드로 검색할 수 있다.
    plain: 로컬 개발용 — 사람이 읽는 텍스트. request_id는 접미로 붙인다.
    """
    handler = logging.StreamHandler()
    if log_format == "json":
        handler.setFormatter(JsonLogFormatter())
    else:
        handler.setFormatter(
            logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s [%(request_id)s]")
        )
    handler.addFilter(_RequestIdInjector())

    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(logging.INFO)
