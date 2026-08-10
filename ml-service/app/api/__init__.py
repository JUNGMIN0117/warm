"""HTTP 계층 — FastAPI 추론 서비스.

계층 경계는 이 프로젝트의 일관된 규칙을 따른다.
- `domain/`은 이미지도 HTTP도 모른다.
- `pipeline/`은 이미지를 알지만 HTTP는 모른다.
- `api/`만 HTTP를 안다. 예외를 상태 코드로 번역하고, 배열을 JSON으로
  바꾸는 일이 전부 여기서 일어난다.

덕분에 파이프라인을 배치 작업이나 CLI로 재사용해도 HTTP 어휘가
따라붙지 않는다.
"""

from .main import app, create_app
from .settings import Settings, get_settings

__all__ = ["Settings", "app", "create_app", "get_settings"]
