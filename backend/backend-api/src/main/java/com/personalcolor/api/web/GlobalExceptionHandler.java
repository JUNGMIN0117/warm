package com.personalcolor.api.web;

import com.personalcolor.api.web.dto.ErrorResponse;
import com.personalcolor.domain.analysis.AnalyzerUnavailableException;
import com.personalcolor.domain.analysis.ImageRejectedException;
import com.personalcolor.domain.season.SeasonProfileMissingException;
import com.personalcolor.domain.user.EmailAlreadyUsedException;
import com.personalcolor.domain.user.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 도메인 예외를 HTTP 응답으로 번역한다.
 *
 * <p>ml-service의 {@code error_mapping.py}와 같은 역할이고 기준도 같다 —
 * <b>사용자가 다시 시도해서 고칠 수 있는가</b>로 4xx와 5xx를 가른다.
 *
 * <p>도메인이 HTTP를 모르게 유지하는 대가로 이 파일이 존재한다. 대신
 * 도메인 예외를 배치 작업이나 CLI에서 재사용해도 HTTP 어휘가 따라오지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 사진 문제 — 사용자가 다른 사진으로 해결할 수 있다. */
    @ExceptionHandler(ImageRejectedException.class)
    public ResponseEntity<ErrorResponse> handleImageRejected(ImageRejectedException e) {
        HttpStatus status = e.reason() == ImageRejectedException.Reason.IMAGE_DECODE_FAILED
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.UNPROCESSABLE_ENTITY;

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(e.reason().code(), e.getMessage()));
    }

    /**
     * 측정기 장애.
     *
     * <p>503으로 내보내는 것이 중요하다. 클라이언트와 로드밸런서가 재시도해도
     * 되는 상태임을 알 수 있고, 사진 문제(4xx)와 통계적으로 구분된다.
     */
    @ExceptionHandler(AnalyzerUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAnalyzerDown(AnalyzerUnavailableException e) {
        log.warn("ml-service 사용 불가: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("ANALYZER_UNAVAILABLE", e.getMessage()));
    }

    /** 시드 누락 — 배포 문제다. 사용자에게는 일반적인 메시지만 준다. */
    @ExceptionHandler(SeasonProfileMissingException.class)
    public ResponseEntity<ErrorResponse> handleMissingProfile(SeasonProfileMissingException e) {
        log.error("계절 프로필 시드 누락", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("CATALOG_UNAVAILABLE",
                        "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(EmailAlreadyUsedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("EMAIL_ALREADY_USED", e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", e.getMessage()));
    }

    /**
     * Bean Validation 실패.
     *
     * <p>필드별 메시지를 detail에 담는다. 프론트가 어느 입력칸에 오류를
     * 표시할지 알아야 하는데, 문장 하나로 뭉뚱그리면 그게 불가능하다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_FAILED", "입력값을 확인해 주세요.", fields));
    }

    /** 도메인 record 생성자가 던지는 검증 실패. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of("FILE_TOO_LARGE",
                        "파일이 너무 큽니다. 12MB 이하로 올려 주세요."));
    }

    /** 컨트롤러가 명시적으로 던진 상태 — 그대로 통과시킨다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(ErrorResponse.of(
                HttpStatus.valueOf(e.getStatusCode().value()).name(), e.getReason()));
    }

    /**
     * 나머지 전부.
     *
     * <p>예외 메시지를 응답에 담지 않는다. 스택 트레이스나 내부 메시지가
     * 그대로 나가면 구현 세부가 노출된다. 진단은 로그에서 한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR",
                        "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }
}
