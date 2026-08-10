-- 계절 카탈로그 — 큐레이션된 라벨과 팔레트.
--
-- ADR-005에서 이 데이터의 소유권을 Spring(DB)으로 가져왔다. 측정 결과가
-- 아니라 사람이 정한 것이기 때문이다. DB에 있으면 색 하나 바꾸는 데
-- 모델 로딩이 무거운 추론 서버를 재배포하지 않아도 된다.
--
-- code 값은 ml-service 응답의 season 값과 일치해야 조인이 성립한다.
-- 어긋나면 런타임에 "팔레트를 찾을 수 없음"이 되므로, ml-service 쪽
-- 테스트(test_season_codes_match_palette_export)가 이를 지킨다.

CREATE TABLE season_profiles (
    code        VARCHAR(20) PRIMARY KEY,
    undertone   VARCHAR(10) NOT NULL,
    label_ko    VARCHAR(30) NOT NULL,
    label_en    VARCHAR(30) NOT NULL,
    emoji       VARCHAR(8)  NOT NULL,
    description TEXT        NOT NULL,

    CONSTRAINT ck_season_undertone CHECK (undertone IN ('warm', 'cool'))
);

CREATE TABLE season_keywords (
    season_code   VARCHAR(20) NOT NULL REFERENCES season_profiles (code) ON DELETE CASCADE,
    display_order INTEGER     NOT NULL,
    keyword       VARCHAR(30) NOT NULL,

    PRIMARY KEY (season_code, display_order)
);

CREATE TABLE palette_colors (
    id            BIGSERIAL   PRIMARY KEY,
    season_code   VARCHAR(20) NOT NULL REFERENCES season_profiles (code) ON DELETE CASCADE,
    -- 추천색과 기피색을 별도 테이블로 나누지 않는 이유: 구조가 같고
    -- 함께 조회되므로, 테이블을 쪼개면 UNION만 늘어난다.
    palette_kind  VARCHAR(10) NOT NULL,
    display_order INTEGER     NOT NULL,
    name          VARCHAR(40) NOT NULL,
    hex           VARCHAR(7)  NOT NULL,

    CONSTRAINT ck_palette_kind CHECK (palette_kind IN ('BEST', 'WORST')),
    -- 대문자 HEX로 통일한다. 저장 형식이 갈리면 UI와 DB 비교가 어긋난다.
    CONSTRAINT ck_palette_hex CHECK (hex ~ '^#[0-9A-F]{6}$'),
    CONSTRAINT ux_palette_slot UNIQUE (season_code, palette_kind, display_order)
);

CREATE TABLE styling_tips (
    season_code   VARCHAR(20) NOT NULL REFERENCES season_profiles (code) ON DELETE CASCADE,
    display_order INTEGER     NOT NULL,
    tip           TEXT        NOT NULL,

    PRIMARY KEY (season_code, display_order)
);

CREATE INDEX ix_palette_colors_season ON palette_colors (season_code, palette_kind, display_order);
