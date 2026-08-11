# ML 추론 서비스 이미지.
#
# 멀티스테이지로 나눈 이유는 크기가 아니라 **재현성과 캐시**다. 의존성
# 설치와 모델 다운로드는 소스가 바뀌어도 다시 할 이유가 없는데, 한
# 스테이지에 몰아넣으면 코드 한 줄 고칠 때마다 전부 다시 돈다.

# ---------------------------------------------------------------------------
# 1단계: 의존성 — pyproject/uv.lock이 바뀔 때만 다시 실행된다
# ---------------------------------------------------------------------------
FROM python:3.12-slim-bookworm AS deps

# uv를 공식 이미지에서 바이너리만 복사한다. 설치 스크립트를 curl로 받는
# 방식보다 빠르고, 버전이 태그로 고정되어 재현 가능하다.
COPY --from=ghcr.io/astral-sh/uv:0.12.1 /uv /uvx /bin/

WORKDIR /app

# 락파일 기준으로만 설치한다(--frozen). 빌드 중에 의존성이 조용히
# 갱신되면 "로컬에선 되는데 이미지에선 안 되는" 상황이 생긴다.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

# ---------------------------------------------------------------------------
# 2단계: 모델 — 다운로드 스크립트가 바뀔 때만 다시 실행된다
# ---------------------------------------------------------------------------
FROM deps AS models

# 모델 가중치는 저장소에 없다(CLAUDE.md 금지사항). 스크립트가 버전 고정
# URL + SHA-256 검증으로 받으므로, 언제 빌드해도 같은 바이트가 들어간다.
# 이미지에 구워 넣는 것은 의도적이다 — 컨테이너가 뜰 때마다 3.7MB를
# 내려받으면 기동이 느려지고 네트워크 장애에 취약해진다.
COPY scripts/download_models.py ./scripts/
RUN uv run --no-project python scripts/download_models.py

# ---------------------------------------------------------------------------
# 3단계: 런타임
# ---------------------------------------------------------------------------
FROM python:3.12-slim-bookworm AS runtime

# OpenCV가 요구하는 시스템 라이브러리. slim 이미지에는 없다.
#   libgl1        — cv2의 이미지 표시 모듈이 링크한다 (headless여도 필요)
#   libglib2.0-0  — cv2 내부 의존
# --no-install-recommends로 불필요한 패키지가 딸려오는 것을 막는다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# 루트로 실행하지 않는다. 컨테이너가 뚫렸을 때 호스트로 번지는 경로를 줄인다.
RUN useradd --create-home --uid 10001 appuser

WORKDIR /app

COPY --from=deps  --chown=appuser:appuser /app/.venv  ./.venv
COPY --from=models --chown=appuser:appuser /app/models ./models
COPY --chown=appuser:appuser app     ./app
COPY --chown=appuser:appuser scripts ./scripts

ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

USER appuser
EXPOSE 8000

# curl을 설치하지 않고 파이썬으로 확인한다. 헬스체크 하나 때문에
# 이미지에 패키지를 더할 이유가 없다.
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4).status == 200 else 1)"

# 0.0.0.0에 바인딩해야 컨테이너 밖에서 닿는다. 127.0.0.1로 두면
# 컨테이너 내부에서만 접근 가능해 Compose 네트워크에서 안 보인다.
CMD ["uvicorn", "app.api.main:app", "--host", "0.0.0.0", "--port", "8000"]
