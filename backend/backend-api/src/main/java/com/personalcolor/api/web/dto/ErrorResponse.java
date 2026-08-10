package com.personalcolor.api.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 오류 응답 — ml-service와 같은 형태로 통일한다.
 *
 * <p>{@code code}가 계약이고 {@code message}는 사람이 읽는 문구다.
 * 프론트는 코드로 분기하고 메시지는 그대로 보여준다.
 *
 * @param code 기계가 읽는 오류 코드
 * @param message 사용자에게 그대로 보여줄 수 있는 한국어 설명
 * @param detail 코드별 부가 정보. 없으면 응답에서 생략된다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Map<String, Object> detail) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }
}
