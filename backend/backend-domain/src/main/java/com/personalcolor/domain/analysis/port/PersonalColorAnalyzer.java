package com.personalcolor.domain.analysis.port;

import com.personalcolor.domain.analysis.AnalysisOutcome;

/**
 * 퍼스널 컬러 측정기 — 바깥으로 나가는 포트.
 *
 * <p>구현체는 인프라 계층에서 ml-service를 HTTP로 부른다. 도메인이 이
 * 인터페이스만 알기 때문에 다음이 가능해진다.
 *
 * <ul>
 *   <li>유스케이스를 HTTP·스프링 없이 단위 테스트한다
 *   <li>캐시와 서킷 브레이커를 <b>데코레이터</b>로 끼운다. 유스케이스는
 *       자기가 캐시된 결과를 받았는지 모른다
 *   <li>나중에 추론을 Java(DJL)로 이식해도 이 시그니처가 명세가 된다
 * </ul>
 *
 * <p>이미지 바이트를 그대로 받는 것도 의도적이다. 파일 경로나 스트림을
 * 받으면 도메인이 파일시스템·서블릿 수명주기를 알게 된다.
 */
public interface PersonalColorAnalyzer {

    /**
     * 이미지를 분석한다.
     *
     * @param image 원본 이미지 바이트
     * @param includeStages 전처리 단계 이미지를 함께 받을지. 응답이 커지므로
     *     시각화가 필요할 때만 켠다
     * @return 측정 결과와 (요청했다면) 단계 이미지
     * @throws com.personalcolor.domain.analysis.ImageRejectedException 사용자가
     *     사진을 바꿔 해결할 수 있는 문제 (얼굴 없음, 여러 명, 디코딩 실패 등)
     * @throws com.personalcolor.domain.analysis.AnalyzerUnavailableException
     *     측정기 자체를 쓸 수 없는 문제 (장애, 타임아웃, 서킷 오픈)
     */
    AnalysisOutcome analyze(byte[] image, boolean includeStages);
}
