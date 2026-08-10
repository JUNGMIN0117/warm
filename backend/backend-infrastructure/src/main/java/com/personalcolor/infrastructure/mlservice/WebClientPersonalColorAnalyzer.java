package com.personalcolor.infrastructure.mlservice;

import com.personalcolor.domain.analysis.AnalysisOutcome;
import com.personalcolor.domain.analysis.AnalyzerUnavailableException;
import com.personalcolor.domain.analysis.ImageRejectedException;
import com.personalcolor.domain.analysis.port.PersonalColorAnalyzer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * ml-service HTTP 어댑터 — {@link PersonalColorAnalyzer}의 실제 구현.
 *
 * <p>여기서 하는 일은 세 가지다. multipart 요청을 만들고, 응답을 도메인
 * 모델로 변환하고, <b>오류를 도메인 예외로 번역</b>한다. 세 번째가 가장
 * 중요하다 — 이 클래스가 없으면 HTTP 상태 코드가 유스케이스까지 올라간다.
 *
 * <p>캐시와 서킷 브레이커는 여기 없다. 데코레이터로 이 클래스를 감싸므로
 * (설정은 {@code MlServiceConfiguration}) 이 클래스는 "부르고 번역한다"는
 * 한 가지 책임만 갖는다.
 *
 * <p>WebClient를 블로킹으로 쓰는 것에 대하여 — 이 서비스는 서블릿 스택이고
 * 호출부도 동기다. WebClient를 고른 것은 리액티브 때문이 아니라 타임아웃과
 * 오류 처리 API가 RestClient보다 세밀하기 때문이며, {@code block()}으로
 * 경계에서 동기화한다.
 */
public class WebClientPersonalColorAnalyzer implements PersonalColorAnalyzer {

    private final WebClient webClient;
    private final Duration timeout;

    public WebClientPersonalColorAnalyzer(WebClient webClient, Duration timeout) {
        this.webClient = webClient;
        this.timeout = timeout;
    }

    @Override
    public AnalysisOutcome analyze(byte[] image, boolean includeStages) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        // 파일명은 ml-service가 쓰지 않지만 multipart 파트에는 있어야 한다.
        // 사용자가 올린 원래 이름을 넘기지 않는 것은 의도적이다 — 파일명에
        // 개인정보가 담기는 경우가 흔하고, 우리는 그것을 전달할 이유가 없다.
        body.part("image", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "upload";
            }
        }).contentType(MediaType.APPLICATION_OCTET_STREAM);

        try {
            MlAnalysisResponse response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/analyze")
                            .queryParam("include_stages", includeStages)
                            .build())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body.build()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::translateError)
                    .bodyToMono(MlAnalysisResponse.class)
                    .block(timeout);

            if (response == null) {
                throw new AnalyzerUnavailableException("ml-service가 빈 응답을 보냈습니다.");
            }
            return MlResponseMapper.toDomain(response);

        } catch (WebClientRequestException e) {
            // 연결 거부·DNS 실패 등 요청이 상대에 닿지도 못한 경우.
            throw new AnalyzerUnavailableException(
                    "ml-service에 연결할 수 없습니다: " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            // block(timeout) 초과 시 Reactor가 IllegalStateException을 던진다.
            if (e.getCause() instanceof TimeoutException || isBlockTimeout(e)) {
                throw new AnalyzerUnavailableException(
                        "ml-service 응답이 " + timeout.toSeconds() + "초 안에 오지 않았습니다.", e);
            }
            throw e;
        }
    }

    private static boolean isBlockTimeout(IllegalStateException e) {
        String message = e.getMessage();
        return message != null && message.contains("Timeout on blocking read");
    }

    /**
     * HTTP 오류를 도메인 예외로 번역한다.
     *
     * <p>경계는 4xx/5xx다. 4xx는 사용자가 사진을 바꿔 해결할 수 있으므로
     * {@link ImageRejectedException}, 5xx는 측정기 문제이므로
     * {@link AnalyzerUnavailableException}이다. 이 구분이 그대로 서킷
     * 브레이커의 카운팅 기준이 된다 (ImageRejected는 세지 않는다).
     *
     * <p>ml-service가 구조화된 오류 본문을 주지 않는 경우(프록시가 만든
     * 502 등)도 있으므로 본문 파싱 실패를 견딘다.
     */
    private reactor.core.publisher.Mono<Throwable> translateError(
            org.springframework.web.reactive.function.client.ClientResponse response) {
        HttpStatusCode status = response.statusCode();
        return response.bodyToMono(MlErrorResponse.class)
                .onErrorReturn(new MlErrorResponse(null, null, null))
                .defaultIfEmpty(new MlErrorResponse(null, null, null))
                .map(error -> toException(status, error));
    }

    private static Throwable toException(HttpStatusCode status, MlErrorResponse error) {
        String message = error.message() != null
                ? error.message()
                : "ml-service 오류 (HTTP " + status.value() + ")";

        if (status.is4xxClientError()) {
            return new ImageRejectedException(
                    ImageRejectedException.Reason.fromCode(error.code()), message);
        }
        return new AnalyzerUnavailableException(message);
    }
}
