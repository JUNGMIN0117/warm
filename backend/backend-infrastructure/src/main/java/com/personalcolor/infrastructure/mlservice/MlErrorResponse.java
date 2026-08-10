package com.personalcolor.infrastructure.mlservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * ml-service의 오류 응답.
 *
 * <p>{@code code}로 분기하고 {@code message}는 그대로 사용자에게 전달한다.
 * 문구를 게이트웨이가 다시 쓰지 않는 이유는, 실패 원인을 가장 잘 아는
 * 쪽이 측정기이기 때문이다 — "얼굴이 더 크게 나온 사진을 쓰세요" 같은
 * 안내는 픽셀 수를 아는 쪽에서 나와야 구체적이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlErrorResponse(String code, String message, Map<String, Object> detail) {}
