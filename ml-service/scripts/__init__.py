"""운영·검증 스크립트.

패키지로 만든 이유는 테스트가 `scripts.export_palettes`를 임포트해
API 응답의 계절 코드와 팔레트 시드의 코드가 일치하는지 검증하기
때문이다. 스크립트 안의 `sys.path` 조작은 `python scripts/xxx.py`로
직접 실행하는 경로를 위해 남겨둔다.
"""
