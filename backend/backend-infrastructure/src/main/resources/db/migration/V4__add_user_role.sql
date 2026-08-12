-- 사용자 역할 — 관리자 큐레이션 편집(ADR-011)을 위한 최소 확장.
--
-- 별도 역할 테이블을 두지 않는다. 보호할 관리 기능이 큐레이션 편집
-- 하나뿐인 서비스에서 RBAC 테이블은 구조의 과잉이다. 값은 CHECK로
-- 고정하고, 세분화가 필요해지는 날 그 CHECK를 지우는 마이그레이션이
-- 이 결정의 재검토 지점이 된다.
--
-- 관리자 계정은 가입 API로 만들 수 없다 — 기동 시 환경변수 부트스트랩
-- (PCAI_ADMIN_EMAIL/PASSWORD)만이 승격 경로다.

ALTER TABLE users
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER'
        CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));
