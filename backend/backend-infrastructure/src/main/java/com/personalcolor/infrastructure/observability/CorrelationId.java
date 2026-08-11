package com.personalcolor.infrastructure.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 상관관계 ID의 단일 정의.
 *
 * <p>한 사용자 요청이 프론트 → 게이트웨이 → ml-service로 흐를 때, 세 서비스의
 * 로그를 하나로 꿰는 실이 이 ID다. 헤더 이름·MDC 키·형식 규칙이 여기 한 곳에만
 * 있어야 발급하는 쪽(서블릿 필터)과 전파하는 쪽(WebClient)이 어긋나지 않는다.
 *
 * <p>infrastructure 모듈에 두는 이유: api(발급)와 infrastructure(전파)가 모두
 * 써야 하는데, 의존 방향이 api → infrastructure이므로 아래층인 이쪽이 소유한다.
 * 도메인에 두지 않는 것도 의도다 — 이것은 HTTP 운영 관심사이지 도메인 개념이
 * 아니다.
 */
public final class CorrelationId {

    /** 표준은 없지만 사실상 관례인 헤더. 프록시·게이트웨이 도구들이 대부분 인식한다. */
    public static final String HEADER = "X-Request-Id";

    /** MDC 키. 구조화 로그(ECS)에서 이 이름의 필드로 출력된다. */
    public static final String MDC_KEY = "correlationId";

    /**
     * 외부에서 온 ID를 수용할 때의 형식 제한.
     *
     * <p>로그 인젝션 방어다 — 헤더는 클라이언트가 통제하는 값이므로 개행이나
     * 제어문자가 섞인 채 로그에 실리면 로그 파싱이 오염된다. 형식에 맞지 않으면
     * 버리고 새로 발급한다.
     */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private CorrelationId() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    /** 현재 스레드에 바인딩된 ID. 필터 밖(스케줄러 등)에서는 null이다. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
