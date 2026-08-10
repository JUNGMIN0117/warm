package com.personalcolor.domain.analysis;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 저장된 분석 한 건.
 *
 * <p><b>원본 이미지는 여기 없다.</b> 얼굴 사진은 개인정보이고, 보관하기
 * 시작하면 보관 기간·삭제 요청·암호화 정책이 전부 따라온다. 이 서비스는
 * 그것을 감당하지 않기로 했다 — 남기는 것은 이미지 해시(캐시 키이자
 * 중복 판별자), 측정 수치, 대표 피부색뿐이다.
 *
 * <p>대가로 "이력에서 원본 다시 보기"는 불가능하다. 이력 화면은 색 칩과
 * 3축 게이지로 구성된다. 프라이버시를 위해 기능을 포기한 것이므로
 * 숨기지 않고 UI에 명시한다.
 *
 * @param id 분석 식별자
 * @param imageHash 원본 이미지의 SHA-256. 이미지 자체는 저장하지 않는다
 * @param userId 소유자. 익명 분석이면 비어 있다
 * @param measurement 측정·판정 결과
 * @param analyzedAt 분석 시각 (UTC)
 */
public record AnalysisRecord(
        UUID id,
        String imageHash,
        Optional<UUID> userId,
        Measurement measurement,
        Instant analyzedAt) {

    /** SHA-256 16진 표현의 길이. */
    private static final int SHA256_HEX_LENGTH = 64;

    public AnalysisRecord {
        if (id == null) {
            throw new IllegalArgumentException("id가 없습니다.");
        }
        if (measurement == null) {
            throw new IllegalArgumentException("measurement가 없습니다.");
        }
        if (analyzedAt == null) {
            throw new IllegalArgumentException("analyzedAt이 없습니다.");
        }
        if (imageHash == null || imageHash.length() != SHA256_HEX_LENGTH) {
            throw new IllegalArgumentException(
                    "imageHash는 SHA-256 16진 문자열이어야 합니다: " + imageHash);
        }
        userId = userId == null ? Optional.empty() : userId;
    }

    /** 로그인 사용자에게 귀속된 분석인가. 익명 분석은 이력 조회 대상이 아니다. */
    public boolean isAnonymous() {
        return userId.isEmpty();
    }
}
