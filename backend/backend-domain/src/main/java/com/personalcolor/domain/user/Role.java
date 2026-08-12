package com.personalcolor.domain.user;

/**
 * 사용자 역할.
 *
 * <p>둘뿐이다. ADMIN의 유일한 용도는 계절 큐레이션 편집(팔레트·팁)이며,
 * 관리자 계정은 가입 화면이 아니라 기동 시 환경변수 부트스트랩으로만
 * 만들어진다 (ADR-011). 세분화된 권한 체계(RBAC 테이블 등)를 두지 않은
 * 이유: 보호할 관리 기능이 하나뿐인 서비스에서 그것은 구조의 과잉이다.
 */
public enum Role {
    USER,
    ADMIN;

    /** 저장·전송용 코드 문자열. */
    public String code() {
        return name();
    }

    public static Role fromCode(String code) {
        return Role.valueOf(code);
    }
}
