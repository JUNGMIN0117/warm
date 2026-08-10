"""HTTP 계층 테스트.

MediaPipe 모델 없이 돈다. `create_app`의 `state_builder` 주입 지점에
가짜 검출기를 넣은 파이프라인을 밀어 넣으면, 라우팅·검증·예외 매핑·
직렬화 같은 HTTP 계층의 책임을 모델과 무관하게 검증할 수 있다.

검증하는 것은 **계약**이다. Spring과 프론트가 이 응답 형태에 결합되므로,
필드가 사라지거나 타입이 바뀌면 여기서 깨져야 한다.
"""

from __future__ import annotations

import base64
import io
from collections.abc import Iterator

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.api.main import ServiceState, create_app
from app.api.settings import Settings, get_settings
from app.pipeline.pipeline import PipelineConfig, PreprocessPipeline
from tests.synthetic import SKIN, fake_landmarks, synthetic_face
from tests.test_pipeline import StubDetector

_NEUTRAL_BACKGROUND = (128, 128, 128)


def _png_bytes(image: np.ndarray) -> bytes:
    buffer = io.BytesIO()
    Image.fromarray(image).save(buffer, format="PNG")
    return buffer.getvalue()


def _face_png() -> bytes:
    return _png_bytes(synthetic_face(background=_NEUTRAL_BACKGROUND))


def _stub_state(_: Settings) -> ServiceState:
    pipeline = PreprocessPipeline(detector=StubDetector(fake_landmarks()))
    return ServiceState(pipeline=pipeline, pool=None, pool_size=2)


def _unavailable_state(_: Settings) -> ServiceState:
    """모델이 없는 서버를 흉내 낸다."""
    return ServiceState(pipeline=None)


@pytest.fixture(scope="module")
def client() -> Iterator[TestClient]:
    with TestClient(create_app(state_builder=_stub_state)) as test_client:
        yield test_client


@pytest.fixture
def degraded_client() -> Iterator[TestClient]:
    with TestClient(create_app(state_builder=_unavailable_state)) as test_client:
        yield test_client


class TestHealth:
    def test_reports_ok_when_model_loaded(self, client: TestClient) -> None:
        response = client.get("/health")

        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "ok"
        assert body["model_loaded"] is True
        assert body["detector_pool_size"] == 2

    def test_reports_degraded_without_model(self, degraded_client: TestClient) -> None:
        """프로세스가 살아 있어도 모델이 없으면 정직하게 degraded여야 한다.

        기동을 실패시키지 않는 것이 의도다 — 크래시 루프에 빠지면
        /health로 원인을 알릴 방법조차 없어진다.
        """
        response = degraded_client.get("/health")

        assert response.status_code == 200
        assert response.json()["status"] == "degraded"
        assert response.json()["model_loaded"] is False


class TestAnalyzeSuccess:
    def test_returns_full_probability_distribution(self, client: TestClient) -> None:
        """top-1만 주면 경계 케이스와 확실한 케이스를 구분할 수 없다
        (도메인 불변식 4). 계약으로 고정한다."""
        response = client.post("/v1/analyze", files={"image": ("f.png", _face_png())})

        assert response.status_code == 200
        body = response.json()

        assert set(body["probabilities"]) == {
            "spring_warm",
            "summer_cool",
            "autumn_warm",
            "winter_cool",
        }
        assert sum(body["probabilities"].values()) == pytest.approx(1.0)
        assert body["season"] == max(
            body["probabilities"], key=lambda k: body["probabilities"][k]
        )

    def test_reports_measurement_evidence(self, client: TestClient) -> None:
        """판정 근거 수치가 응답에 있어야 한다 — 블랙박스 탈출이 목표다."""
        body = client.post(
            "/v1/analyze", files={"image": ("f.png", _face_png())}
        ).json()

        features = body["features"]
        assert features["pixel_count"] > 5_000
        assert tuple(features["median_rgb"]) == pytest.approx(SKIN, abs=5)
        assert features["ita_category"]

        assert {axis["name"] for axis in body["axes"]} == {
            "undertone",
            "depth",
            "clarity",
        }
        for axis in body["axes"]:
            assert 0.0 <= axis["normalized"] <= 1.0
            assert axis["interpretation"]

    def test_reports_preprocessing_transparency(self, client: TestClient) -> None:
        """보정량과 마스킹 품질을 공개한다 — 무엇을 얼마나 건드렸는지."""
        body = client.post(
            "/v1/analyze", files={"image": ("f.png", _face_png())}
        ).json()

        assert len(body["white_balance"]["gains"]) == 3
        assert body["white_balance"]["method"] == "gray_world"
        assert body["white_balance"]["cast_strength"] >= 0.0
        assert 0.0 < body["mask_quality"]["coverage_ratio"] <= 1.0
        assert body["mask_quality"]["otsu_threshold"] > 0.0

    def test_undertone_is_reported_separately(self, client: TestClient) -> None:
        """언더톤은 4계절을 병합한 것이라 항상 더 견고하다 — 따로 보고한다."""
        body = client.post(
            "/v1/analyze", files={"image": ("f.png", _face_png())}
        ).json()

        assert body["undertone"] == "warm"
        assert body["undertone_confidence"] >= body["confidence"] - 1e-9

    def test_does_not_leak_presentation_concerns(self, client: TestClient) -> None:
        """팔레트·한국어 라벨은 Spring 소유다 (ADR-005).

        이 서비스가 그것들을 반환하기 시작하면 경계가 조용히 무너지므로
        계약으로 못 박는다.
        """
        body = client.post(
            "/v1/analyze", files={"image": ("f.png", _face_png())}
        ).json()

        for leaked in ("best_colors", "worst_colors", "styling_tips", "label_ko"):
            assert leaked not in body

    def test_stages_omitted_by_default(self, client: TestClient) -> None:
        body = client.post(
            "/v1/analyze", files={"image": ("f.png", _face_png())}
        ).json()

        assert body["stages"] is None


class TestStageImages:
    def test_include_stages_returns_all_five(self, client: TestClient) -> None:
        body = client.post(
            "/v1/analyze",
            files={"image": ("f.png", _face_png())},
            params={"include_stages": "true"},
        ).json()

        stages = body["stages"]
        expected = {
            "original",
            "white_balanced",
            "face_crop",
            "skin_mask",
            "measured_pixels",
        }
        assert set(stages) == expected

        for name, data_uri in stages.items():
            assert data_uri.startswith("data:image/webp;base64,"), name
            payload = base64.b64decode(data_uri.split(",", 1)[1])
            with Image.open(io.BytesIO(payload)) as decoded:
                assert max(decoded.size) <= 512, name

    def test_stage_payload_stays_bounded(self, client: TestClient) -> None:
        """응답 크기를 계약으로 고정한다. 인라인 base64를 택한 대가가
        무제한 응답이 되면 그 결정이 틀린 것이 된다 (ADR-005)."""
        response = client.post(
            "/v1/analyze",
            files={"image": ("f.png", _face_png())},
            params={"include_stages": "true"},
        )

        assert len(response.content) < 400_000

    def test_stages_reflect_measured_pixels(self, client: TestClient) -> None:
        """마스크 이미지는 무손실이어야 한다 — 손실 압축은 '어디까지가
        측정 범위인가'를 흐린다."""
        stages = client.post(
            "/v1/analyze",
            files={"image": ("f.png", _face_png())},
            params={"include_stages": "true"},
        ).json()["stages"]

        payload = base64.b64decode(stages["skin_mask"].split(",", 1)[1])
        with Image.open(io.BytesIO(payload)) as mask:
            values = set(np.asarray(mask.convert("L")).flatten().tolist())

        assert values <= {0, 255}


class TestAnalyzeErrors:
    def test_garbage_bytes_yield_decode_error(self, client: TestClient) -> None:
        response = client.post(
            "/v1/analyze", files={"image": ("x.png", b"not an image")}
        )

        assert response.status_code == 400
        assert response.json()["code"] == "IMAGE_DECODE_FAILED"

    def test_empty_file_is_rejected(self, client: TestClient) -> None:
        response = client.post("/v1/analyze", files={"image": ("x.png", b"")})

        assert response.status_code == 400
        assert response.json()["code"] == "IMAGE_DECODE_FAILED"

    def test_insufficient_skin_pixels_is_unprocessable(self) -> None:
        """유채색 배경이 Gray-World 가정을 깨 마스킹이 실패하는 경로.

        조용히 소수 픽셀로 판정을 지어내는 대신 구조화된 422를 낸다.
        """

        def state(_: Settings) -> ServiceState:
            pipeline = PreprocessPipeline(
                detector=StubDetector(fake_landmarks()),
                config=PipelineConfig(min_skin_pixels=10**9),
            )
            return ServiceState(pipeline=pipeline, pool_size=1)

        with TestClient(create_app(state_builder=state)) as client:
            response = client.post(
                "/v1/analyze", files={"image": ("f.png", _face_png())}
            )

        assert response.status_code == 422
        body = response.json()
        assert body["code"] == "INSUFFICIENT_SKIN_PIXELS"
        assert body["detail"]["minimum"] == 10**9

    def test_missing_model_yields_503_not_422(
        self, degraded_client: TestClient
    ) -> None:
        """서버 구성 문제는 5xx여야 한다.

        Resilience4j 서킷 브레이커가 5xx를 장애로 센다. 반대로 '얼굴 없는
        사진'을 5xx로 내보내면 사용자가 잘못된 사진을 몇 장 올린 것만으로
        회로가 열려 정상 요청까지 막힌다.
        """
        response = degraded_client.post(
            "/v1/analyze", files={"image": ("f.png", _face_png())}
        )

        assert response.status_code == 503
        assert response.json()["code"] == "MODEL_NOT_AVAILABLE"

    def test_oversized_upload_is_rejected_before_decoding(self) -> None:
        def state(_: Settings) -> ServiceState:
            pipeline = PreprocessPipeline(detector=StubDetector(fake_landmarks()))
            return ServiceState(pipeline=pipeline, pool_size=1)

        app = create_app(state_builder=state)
        app.dependency_overrides[get_settings] = lambda: Settings(
            max_upload_bytes=1_000
        )

        with TestClient(app) as client:
            response = client.post(
                "/v1/analyze", files={"image": ("f.png", _face_png())}
            )

        assert response.status_code == 413
        assert response.json()["code"] == "FILE_TOO_LARGE"

    def test_missing_file_is_validation_error(self, client: TestClient) -> None:
        """FastAPI 기본 검증. 파일 자체가 없으면 422."""
        assert client.post("/v1/analyze").status_code == 422


class TestContract:
    def test_openapi_schema_is_served(self, client: TestClient) -> None:
        schema = client.get("/openapi.json").json()

        assert "/v1/analyze" in schema["paths"]
        assert "/health" in schema["paths"]

        documented = schema["paths"]["/v1/analyze"]["post"]["responses"]
        for status in ("400", "413", "422", "503"):
            assert status in documented, f"{status} 응답이 문서화되지 않음"

    def test_path_is_versioned(self, client: TestClient) -> None:
        """Spring이 스키마에 강하게 결합되므로 버전 경로를 유지한다."""
        assert client.post("/analyze", files={"image": ("f.png", b"x")}).status_code == 404

    def test_season_codes_match_palette_export(self, client: TestClient) -> None:
        """API가 내보내는 season 값과 Spring이 시드할 팔레트의 code가
        일치해야 조인이 성립한다 (ADR-005). 두 곳이 어긋나면 런타임에
        '팔레트를 찾을 수 없음'이 되므로 여기서 잡는다."""
        from scripts.export_palettes import build_payload

        body = client.post(
            "/v1/analyze", files={"image": ("f.png", _face_png())}
        ).json()
        exported = {season["code"] for season in build_payload()["seasons"]}

        assert set(body["probabilities"]) == exported
        assert body["season"] in exported
