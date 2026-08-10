package com.personalcolor.domain.analysis;

import com.personalcolor.domain.analysis.port.AnalysisRepository;
import com.personalcolor.domain.analysis.port.PersonalColorAnalyzer;
import com.personalcolor.domain.season.SeasonProfile;
import com.personalcolor.domain.season.port.SeasonProfileRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * 유스케이스 — 이미지 한 장을 분석해 결과를 만들고, 필요하면 이력에 남긴다.
 *
 * <p>이 클래스가 조립하는 것이 ADR-005의 경계 그 자체다. 측정과 판정은
 * {@link PersonalColorAnalyzer}(→ ml-service)가 하고, 팔레트·라벨은
 * {@link SeasonProfileRepository}(→ DB)가 붙인다. 둘을 합쳐 사용자에게
 * 보여줄 한 벌을 만드는 것이 여기의 일이다.
 *
 * <p>Spring 애너테이션이 없는 것은 실수가 아니다. 이 클래스는 생성자로
 * 협력자를 받는 평범한 자바 객체이고, 빈 등록은 인프라 계층의
 * {@code @Configuration}이 한다. 덕분에 스프링 컨텍스트 없이 가짜 포트만
 * 끼워 테스트할 수 있다.
 */
public final class AnalyzeImage {

    private final PersonalColorAnalyzer analyzer;
    private final AnalysisRepository repository;
    private final SeasonProfileRepository profiles;
    private final Clock clock;

    /**
     * @param clock 시각을 주입받는 이유: 저장 시각이 결과에 들어가므로,
     *     테스트가 {@code Clock.fixed}로 고정하지 못하면 시간에 의존하는
     *     단언을 쓸 수 없다
     */
    public AnalyzeImage(
            PersonalColorAnalyzer analyzer,
            AnalysisRepository repository,
            SeasonProfileRepository profiles,
            Clock clock) {
        this.analyzer = analyzer;
        this.repository = repository;
        this.profiles = profiles;
        this.clock = clock;
    }

    /**
     * 분석을 수행한다.
     *
     * @param image 원본 이미지 바이트
     * @param userId 로그인 사용자. 익명 분석이면 비어 있다
     * @param includeStages 전처리 단계 이미지를 함께 받을지
     * @return 저장된(또는 익명이면 저장되지 않은) 결과와 프로필, 단계 이미지
     */
    public AnalysisView execute(byte[] image, Optional<UUID> userId, boolean includeStages) {
        if (image == null || image.length == 0) {
            throw new ImageRejectedException(
                    ImageRejectedException.Reason.IMAGE_DECODE_FAILED,
                    "빈 파일입니다. 이미지를 첨부했는지 확인해 주세요.");
        }

        AnalysisOutcome outcome = analyzer.analyze(image, includeStages);
        Measurement measurement = outcome.measurement();
        SeasonProfile profile = profiles.findBySeason(measurement.season());

        AnalysisRecord record = new AnalysisRecord(
                UUID.randomUUID(),
                sha256Hex(image),
                userId,
                measurement,
                Instant.now(clock));

        // 익명 분석은 저장하지 않는다. 소유자가 없는 행은 아무도 조회할 수
        // 없으면서 개인정보 성격의 측정값만 쌓기 때문이다. 캐시(어댑터 쪽)는
        // 익명이든 아니든 동작하므로 반복 요청 비용은 그쪽에서 흡수된다.
        AnalysisRecord persisted = record.isAnonymous() ? record : repository.save(record);

        return new AnalysisView(persisted, profile, outcome.stages());
    }

    /**
     * 이미지의 SHA-256.
     *
     * <p>원본을 저장하지 않으므로 이 해시가 "같은 사진인가"를 판별하는
     * 유일한 수단이다. 캐시 키로도 쓰인다.
     */
    static String sha256Hex(byte[] image) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM이 제공해야 하는 알고리즘이다. 여기 오면 런타임이 깨진 것이다.
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
