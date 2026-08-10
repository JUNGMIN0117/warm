package com.personalcolor.api;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 통합 테스트용 HTTP 클라이언트 — JDK 내장 {@link HttpClient} 기반.
 *
 * <p>{@code TestRestTemplate}을 쓰지 않은 것은 선택이 아니라 회피다.
 * Spring Boot 4가 테스트 지원을 잘게 쪼개면서 그 클래스가
 * {@code spring-boot-resttestclient}로 옮겨졌고, 실제로 쓰려면
 * {@code spring-boot-http-client}와 Apache HttpClient 5까지 따라온다.
 * 테스트 하나 돌리자고 의존성을 셋 더 얹는 것보다 JDK에 있는 것을 쓰는
 * 편이 낫다고 판단했다.
 *
 * <p>부수 효과가 오히려 좋다 — 이 클라이언트는 스프링을 전혀 모르므로,
 * 프론트엔드나 외부 소비자가 API를 두드리는 방식과 똑같다. 스프링
 * 테스트 유틸이 조용히 해주던 일(에러 상태에서 예외 대신 응답 반환 등)에
 * 기대지 않는다.
 */
final class HttpTestClient {

    private static final TypeReference<Map<String, Object>> MAP =
            new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new TypeReference<>() {};

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;

    HttpTestClient(int port) {
        this.baseUrl = "http://127.0.0.1:" + port;
    }

    /** 상태 코드와 본문을 함께 담는다. 오류 응답도 예외 없이 그대로 돌려준다. */
    record Response(int status, String body) {

        boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    // --- JSON ------------------------------------------------------------

    Response postJson(String path, Object body, String bearerToken) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(body), StandardCharsets.UTF_8));
        return send(withAuth(request, bearerToken));
    }

    Response get(String path, String bearerToken) {
        return send(withAuth(
                HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), bearerToken));
    }

    // --- multipart --------------------------------------------------------

    /**
     * 파일 하나짜리 multipart POST.
     *
     * <p>라이브러리 없이 손으로 조립한다. 파트가 하나뿐이라 규칙이 단순하고,
     * 여기서 만드는 바이트가 실제 브라우저가 보내는 것과 같은 형태다.
     */
    Response postImage(String path, byte[] image, String bearerToken) {
        String boundary = "----PcaiTestBoundary" + System.identityHashCode(image);
        byte[] body = multipartBody(boundary, image);

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        return send(withAuth(request, bearerToken));
    }

    private static byte[] multipartBody(String boundary, byte[] image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"image\"; "
                    + "filename=\"face.jpg\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(image);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    // --- 본문 해석 ---------------------------------------------------------

    Map<String, Object> asMap(Response response) {
        return json.readValue(response.body(), MAP);
    }

    List<Map<String, Object>> asList(Response response) {
        return json.readValue(response.body(), LIST_OF_MAP);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> nested(Map<String, Object> body, String key) {
        return (Map<String, Object>) body.get(key);
    }

    @SuppressWarnings("unchecked")
    static List<Object> nestedList(Map<String, Object> body, String key) {
        return (List<Object>) body.get(key);
    }

    // --- 내부 -------------------------------------------------------------

    private static HttpRequest.Builder withAuth(HttpRequest.Builder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = http.send(
                    builder.timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("요청이 중단되었습니다.", e);
        }
    }
}
