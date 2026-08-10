-- 사용자와 분석 이력.
--
-- 이 스키마에서 가장 중요한 것은 **없는 것**이다: 업로드된 이미지를
-- 담는 컬럼이 없다. 얼굴 사진은 개인정보이고, 보관하기 시작하면 보관
-- 기간·삭제 요청·암호화 정책이 전부 따라온다. 남기는 것은 해시(같은
-- 사진인지 판별하는 용도)와 측정 수치뿐이다.
--
-- 대가로 "이력에서 원본 다시 보기"가 불가능하다. 이력 화면은 대표
-- 피부색 칩과 3축 게이지로 구성된다.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

-- 이메일은 대소문자를 구분하지 않고 유일해야 한다. citext 확장 대신
-- 소문자 정규화한 값에 유니크 인덱스를 걸어, 확장 설치 없이 같은 보장을 얻는다.
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));

CREATE TABLE analyses (
    id                   UUID         PRIMARY KEY,

    -- 익명 분석은 저장되지 않으므로 실제로는 항상 채워지지만, 정책이
    -- 바뀔 여지를 남겨 NULL을 허용한다. 사용자가 탈퇴하면 이력도 사라진다.
    user_id              UUID         REFERENCES users (id) ON DELETE CASCADE,

    -- 원본 이미지의 SHA-256. 이미지 자체는 저장하지 않는다.
    -- CHAR가 아니라 VARCHAR인 이유: PostgreSQL의 CHAR는 공백으로 패딩하고
    -- 비교 시 그 공백을 무시하는데, 해시 문자열에 그런 관용은 필요 없고
    -- Hibernate의 기본 매핑(varchar)과도 어긋나 스키마 검증에 걸린다.
    image_hash           VARCHAR(64)  NOT NULL,
    analyzed_at          TIMESTAMPTZ  NOT NULL,

    -- 판정 결과
    season               VARCHAR(20)      NOT NULL,
    confidence           DOUBLE PRECISION NOT NULL,
    undertone            VARCHAR(10)      NOT NULL,
    undertone_confidence DOUBLE PRECISION NOT NULL,
    quality_factor       DOUBLE PRECISION NOT NULL,

    -- 확률 분포 전체를 남긴다. 최상위 계절만 저장하면 "62% 봄 / 35% 여름"인
    -- 경계 케이스와 "97% 겨울"인 확실한 케이스를 나중에 구분할 수 없다.
    prob_spring_warm     DOUBLE PRECISION NOT NULL,
    prob_summer_cool     DOUBLE PRECISION NOT NULL,
    prob_autumn_warm     DOUBLE PRECISION NOT NULL,
    prob_winter_cool     DOUBLE PRECISION NOT NULL,

    -- 측정된 색채 통계 (판정 근거)
    lightness            DOUBLE PRECISION NOT NULL,
    a_star               DOUBLE PRECISION NOT NULL,
    b_star               DOUBLE PRECISION NOT NULL,
    chroma               DOUBLE PRECISION NOT NULL,
    hue_angle            DOUBLE PRECISION NOT NULL,
    ita                  DOUBLE PRECISION NOT NULL,
    ita_category         VARCHAR(20)      NOT NULL,
    lightness_spread     DOUBLE PRECISION NOT NULL,
    pixel_count          INTEGER          NOT NULL,
    median_rgb           VARCHAR(7)       NOT NULL,

    -- 전처리가 사진을 얼마나 건드렸는지
    wb_method            VARCHAR(20)      NOT NULL,
    wb_gain_red          DOUBLE PRECISION NOT NULL,
    wb_gain_green        DOUBLE PRECISION NOT NULL,
    wb_gain_blue         DOUBLE PRECISION NOT NULL,
    wb_cast_strength     DOUBLE PRECISION NOT NULL,
    mask_coverage_ratio  DOUBLE PRECISION NOT NULL,
    otsu_threshold       DOUBLE PRECISION NOT NULL,

    -- 축 해석과 경고는 길이가 가변이고 조회 조건이 되지 않는다.
    -- 자식 테이블을 만들 만한 값이 아니라 JSONB로 둔다.
    axes                 JSONB            NOT NULL,
    warnings             JSONB            NOT NULL,

    CONSTRAINT ck_analyses_median_rgb CHECK (median_rgb ~ '^#[0-9A-F]{6}$')
);

-- 이력 조회는 항상 "내 것을, 최신순으로"다.
CREATE INDEX ix_analyses_user_recent ON analyses (user_id, analyzed_at DESC);

-- 같은 사진을 다시 올렸는지 확인하는 용도.
CREATE INDEX ix_analyses_image_hash ON analyses (image_hash);
