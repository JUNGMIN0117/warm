package com.personalcolor.domain.analysis;

/**
 * 측정기 자체를 쓸 수 없는 상태.
 *
 * <p>ml-service 장애, 타임아웃, 서킷 브레이커 오픈이 여기 해당한다.
 * 사용자가 사진을 바꿔도 해결되지 않으므로 {@link ImageRejectedException}과
 * 구분한다 (그쪽 javadoc에 기준이 있다).
 *
 * <p>API 계층에서는 503으로 매핑되고, 서킷 브레이커는 이 부류만 실패로 센다.
 */
public class AnalyzerUnavailableException extends RuntimeException {

    public AnalyzerUnavailableException(String message) {
        super(message);
    }

    public AnalyzerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
