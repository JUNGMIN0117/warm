package com.personalcolor.domain.analysis;

/**
 * 사용자가 사진을 바꿔 해결할 수 있는 문제.
 *
 * <p>이 예외와 {@link AnalyzerUnavailableException}을 가르는 기준은 하나다 —
 * <b>사용자가 다시 시도해서 고칠 수 있는가.</b> 얼굴이 없거나 여러 명이거나
 * 사진이 깨진 것은 사진을 바꾸면 되지만, 측정기가 죽은 것은 사용자가 할 수
 * 있는 게 없다.
 *
 * <p>이 구분이 형식적이지 않은 이유는 서킷 브레이커 때문이다. 브레이커는
 * 실패를 세어 회로를 여는데, "얼굴 없는 사진"을 실패로 세면 사용자가
 * 잘못된 사진을 몇 장 올린 것만으로 회로가 열려 정상 요청까지 막힌다.
 * 그래서 이 예외는 브레이커가 <b>무시</b>해야 하는 부류다.
 *
 * <p>API 계층에서는 4xx로 매핑된다.
 */
public class ImageRejectedException extends RuntimeException {

    /**
     * 거절 사유.
     *
     * <p>코드 문자열이 ml-service의 오류 코드와 같다. 문자열 매칭 대신
     * 열거형으로 다루기 위한 것이고, 프론트도 이 코드로 분기한다 —
     * 메시지는 문구가 바뀌지만 코드는 계약이다.
     */
    public enum Reason {
        NO_FACE_DETECTED("NO_FACE_DETECTED"),
        MULTIPLE_FACES("MULTIPLE_FACES"),
        INSUFFICIENT_SKIN_PIXELS("INSUFFICIENT_SKIN_PIXELS"),
        IMAGE_DECODE_FAILED("IMAGE_DECODE_FAILED"),
        FILE_TOO_LARGE("FILE_TOO_LARGE"),
        /** ml-service가 우리가 모르는 4xx 코드를 보낸 경우. */
        UNKNOWN("IMAGE_REJECTED");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        /** 모르는 코드는 예외를 던지지 않고 {@link #UNKNOWN}으로 흡수한다.
         *  ml-service가 새 오류 코드를 추가했다고 게이트웨이가 500을 낼
         *  이유는 없다 — 사용자에게는 여전히 "사진 문제"이기 때문이다. */
        public static Reason fromCode(String code) {
            for (Reason reason : values()) {
                if (reason.code.equals(code)) {
                    return reason;
                }
            }
            return UNKNOWN;
        }
    }

    private final transient Reason reason;

    public ImageRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason == null ? Reason.UNKNOWN : reason;
    }

    public Reason reason() {
        return reason;
    }
}
