"""관측성 계약 테스트 — 상관관계 ID와 구조화 로그.

게이트웨이(Spring)의 RequestObservabilityFilter와 대칭인 계약을 고정한다:
받은 ID는 되돌려주고, 형식이 틀리면 새로 발급하며, 요청 완료 로그에
ID가 실린다. 이 계약이 깨지면 "세 서비스 로그를 요청 하나로 꿰기"라는
Step 6의 목적 자체가 무너진다 (ADR-008).
"""

from __future__ import annotations

import json
import logging
from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.api.main import ServiceState, create_app
from app.api.observability import HEADER_NAME, JsonLogFormatter, current_request_id
from app.api.settings import Settings


def _no_model_state(_: Settings) -> ServiceState:
    """관측성 검증에는 파이프라인이 필요 없다 — /health만 부른다."""
    return ServiceState(pipeline=None)


@pytest.fixture
def client() -> Iterator[TestClient]:
    with TestClient(create_app(state_builder=_no_model_state)) as test_client:
        yield test_client


class TestCorrelationId:
    def test_echoes_valid_incoming_id(self, client: TestClient) -> None:
        """게이트웨이가 보낸 ID는 그대로 응답 헤더로 돌아온다."""
        response = client.get("/health", headers={HEADER_NAME: "gateway-abc-123"})

        assert response.headers[HEADER_NAME] == "gateway-abc-123"

    def test_generates_id_when_absent(self, client: TestClient) -> None:
        """직접 호출(헤더 없음)에도 ID가 발급된다 — 예외 경로의 안전망."""
        response = client.get("/health")

        issued = response.headers[HEADER_NAME]
        assert len(issued) >= 8

    def test_replaces_malformed_id(self, client: TestClient) -> None:
        """개행이 섞인 헤더는 버린다 — 로그 인젝션 방어."""
        response = client.get("/health", headers={HEADER_NAME: "bad id\nx"})

        issued = response.headers[HEADER_NAME]
        assert issued != "bad id\nx"
        assert "\n" not in issued

    def test_request_log_carries_id_and_status(
        self, client: TestClient, caplog: pytest.LogCaptureFixture
    ) -> None:
        """완료 로그 한 줄에 request_id와 상태 코드가 실린다."""
        with caplog.at_level(logging.DEBUG, logger="http.request"):
            client.get("/health", headers={HEADER_NAME: "log-check-001"})

        records = [r for r in caplog.records if r.name == "http.request"]
        assert len(records) == 1
        assert getattr(records[0], "request_id", None) == "log-check-001"
        assert getattr(records[0], "status", None) == 200

    def test_context_is_cleared_between_requests(self, client: TestClient) -> None:
        """요청 밖에서는 컨텍스트가 비어 있다 — 요청 스코프 보장."""
        client.get("/health", headers={HEADER_NAME: "scope-check-01"})

        assert current_request_id() is None


class TestJsonLogFormatter:
    def test_produces_one_json_object_per_line(self) -> None:
        formatter = JsonLogFormatter()
        record = logging.LogRecord(
            name="http.request",
            level=logging.INFO,
            pathname=__file__,
            lineno=1,
            msg="GET /health -> %d (%.0fms)",
            args=(200, 3.0),
            exc_info=None,
        )
        record.request_id = "json-check-01"
        record.status = 200

        parsed = json.loads(formatter.format(record))

        assert parsed["level"] == "INFO"
        assert parsed["logger"] == "http.request"
        assert parsed["request_id"] == "json-check-01"
        assert parsed["status"] == 200
        assert "GET /health" in parsed["message"]

    def test_omits_request_id_outside_request(self) -> None:
        """요청 밖 로그(기동 등)에는 request_id 키 자체가 없다 — null보다 없음."""
        formatter = JsonLogFormatter()
        record = logging.LogRecord(
            name="app", level=logging.INFO, pathname=__file__, lineno=1,
            msg="startup", args=(), exc_info=None,
        )

        parsed = json.loads(formatter.format(record))

        assert "request_id" not in parsed
