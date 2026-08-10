"""계절 프로필·팔레트를 Spring이 소비할 JSON으로 내보낸다.

## 왜 이 스크립트가 필요한가

ADR-005에서 팔레트·한국어 라벨·스타일링 팁의 소유권을 Spring으로
넘겼다. 그렇다고 `seasons.py`에 큐레이션해 둔 내용을 버릴 이유는 없다 —
이 스크립트가 그것을 Step 3의 Flyway 시드로 옮기는 다리다.

`seasons.py`를 남겨두는 이유도 분명하다. 규칙 엔진은 ADR-002의
**영구 폴백**이고, 폴백이 동작하려면 계절 정의가 Python 쪽에도 있어야
한다. 다만 그것이 HTTP 응답으로 나가지는 않는다.

산출물은 저장소에 커밋하지 않는다(생성물이므로). Step 3에서 Flyway
마이그레이션을 쓸 때 이 출력을 붙여 넣거나 빌드 단계에서 생성한다.

사용법:
    uv run python scripts/export_palettes.py                     # JSON을 stdout으로
    uv run python scripts/export_palettes.py -o out.json
    uv run python scripts/export_palettes.py --format sql \\
        -o ../backend/backend-infrastructure/src/main/resources/db/migration/V3__seed_season_catalog.sql

SQL 출력이 Flyway 마이그레이션이 된다. 손으로 타이핑하지 않는 것이 요점이다 —
48개 색상 코드를 사람이 옮겨 적으면 오타가 나고, 그 오타는 UI에 이상한 색이
뜰 때까지 발견되지 않는다.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.domain.seasons import SEASON_PROFILES, Season  # noqa: E402


def _profile_to_dict(season: Season) -> dict[str, Any]:
    profile = SEASON_PROFILES[season]
    return {
        # DB의 자연키. AnalysisResponse.season이 내보내는 값과 같아야
        # Spring이 조인할 수 있다 — 이 일치가 계약의 핵심이다.
        "code": season.value,
        "undertone": season.undertone.value,
        "label_ko": profile.label_ko,
        "label_en": profile.label_en,
        "emoji": profile.emoji,
        "keywords": list(profile.keywords),
        "description": profile.description,
        "best_colors": [
            {"name": name, "hex": hex_code, "display_order": i}
            for i, (name, hex_code) in enumerate(profile.best_colors)
        ],
        "worst_colors": [
            {"name": name, "hex": hex_code, "display_order": i}
            for i, (name, hex_code) in enumerate(profile.worst_colors)
        ],
        "styling_tips": [
            {"text": tip, "display_order": i}
            for i, tip in enumerate(profile.styling_tips)
        ],
    }


def build_payload() -> dict[str, Any]:
    return {
        "_comment": (
            "app/domain/seasons.py에서 생성됨. 직접 편집하지 말 것 — "
            "scripts/export_palettes.py를 다시 실행하거나, Step 3 이후에는 "
            "DB를 진실의 원천으로 삼을 것."
        ),
        "seasons": [_profile_to_dict(season) for season in Season],
    }


def _sql_quote(value: str) -> str:
    """SQL 문자열 리터럴로 감싼다. 작은따옴표는 두 번 써서 이스케이프한다."""
    return "'" + value.replace("'", "''") + "'"


def build_sql() -> str:
    """Flyway 마이그레이션용 INSERT 문을 만든다.

    멱등하게 쓰지 않는다 — Flyway가 버전별로 한 번만 실행하는 것을 보장하므로
    ON CONFLICT 방어는 중복이고, 오히려 "왜 안 들어갔지"를 감춘다.
    """
    lines: list[str] = [
        "-- 이 파일은 생성물입니다. 직접 편집하지 마세요.",
        "-- 원본: ml-service/app/domain/seasons.py",
        "-- 재생성: uv run python scripts/export_palettes.py --format sql -o <이 파일>",
        "--",
        "-- 팔레트의 소유권이 Spring(DB)에 있는 이유는 ADR-005 참조.",
        "-- 요약하면 큐레이션은 측정이 아니므로, 색 하나 바꾸는 데 추론 서버를",
        "-- 재배포해야 하는 구조를 피하려는 것이다.",
        "",
    ]

    for season in Season:
        profile = SEASON_PROFILES[season]
        lines.append(f"-- {profile.emoji} {profile.label_ko}")
        lines.append(
            "INSERT INTO season_profiles "
            "(code, undertone, label_ko, label_en, emoji, description) VALUES ("
            f"{_sql_quote(season.value)}, {_sql_quote(season.undertone.value)}, "
            f"{_sql_quote(profile.label_ko)}, {_sql_quote(profile.label_en)}, "
            f"{_sql_quote(profile.emoji)}, {_sql_quote(profile.description)});"
        )

        for i, keyword in enumerate(profile.keywords):
            lines.append(
                "INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ("
                f"{_sql_quote(season.value)}, {i}, {_sql_quote(keyword)});"
            )

        for kind, colors in (("BEST", profile.best_colors), ("WORST", profile.worst_colors)):
            for i, (name, hex_code) in enumerate(colors):
                lines.append(
                    "INSERT INTO palette_colors "
                    "(season_code, palette_kind, display_order, name, hex) VALUES ("
                    f"{_sql_quote(season.value)}, {_sql_quote(kind)}, {i}, "
                    f"{_sql_quote(name)}, {_sql_quote(hex_code.upper())});"
                )

        for i, tip in enumerate(profile.styling_tips):
            lines.append(
                "INSERT INTO styling_tips (season_code, display_order, tip) VALUES ("
                f"{_sql_quote(season.value)}, {i}, {_sql_quote(tip)});"
            )

        lines.append("")

    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "-o", "--output", type=Path, default=None, help="출력 파일 (생략 시 stdout)"
    )
    parser.add_argument(
        "--format", choices=("json", "sql"), default="json", help="출력 형식"
    )
    args = parser.parse_args()

    if args.format == "sql":
        text = build_sql()
    else:
        text = json.dumps(build_payload(), ensure_ascii=False, indent=2)

    if args.output is None:
        print(text)
    else:
        args.output.write_text(text + "\n", encoding="utf-8")
        print(f"[ ok ] {args.output} ({len(text):,} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
