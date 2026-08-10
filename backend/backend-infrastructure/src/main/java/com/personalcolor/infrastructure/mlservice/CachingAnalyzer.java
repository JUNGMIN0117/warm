package com.personalcolor.infrastructure.mlservice;

import com.personalcolor.domain.analysis.AnalysisOutcome;
import com.personalcolor.domain.analysis.port.PersonalColorAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Redis 캐시 데코레이터.
 *
 * <p>ml-service가 무상태이고 결정론적이기 때문에 성립하는 최적화다 —
 * 같은 이미지는 항상 같은 결과를 낸다. 같은 사진을 다시 올리거나 사용자가
 * 새로고침해도 추론이 다시 돌지 않는다.
 *
 * <p><b>캐시 키에 {@code includeStages}가 들어간다.</b> docs/05-api-spec §9가
 * 지적한 함정이다. 이미지 해시만으로 키를 만들면 {@code false}로 캐시된
 * 응답(단계 이미지 없음)이 {@code true} 요청에 반환되어, 시각화 화면이
 * 조용히 빈 채로 뜬다.
 *
 * <p>캐시 장애는 요청을 실패시키지 않는다. Redis가 죽었을 때 분석까지
 * 못 하게 되면 캐시가 가용성을 <b>낮추는</b> 셈이라, 읽기·쓰기 모두
 * 실패를 삼키고 원본 호출로 넘어간다.
 */
public class CachingAnalyzer implements PersonalColorAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CachingAnalyzer.class);
    private static final String KEY_PREFIX = "pcai:analysis:";

    private final PersonalColorAnalyzer delegate;
    private final RedisTemplate<String, AnalysisOutcome> redis;
    private final Duration ttl;

    public CachingAnalyzer(
            PersonalColorAnalyzer delegate,
            RedisTemplate<String, AnalysisOutcome> redis,
            Duration ttl) {
        this.delegate = delegate;
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public AnalysisOutcome analyze(byte[] image, boolean includeStages) {
        String key = cacheKey(image, includeStages);

        AnalysisOutcome cached = readQuietly(key);
        if (cached != null) {
            return cached;
        }

        AnalysisOutcome fresh = delegate.analyze(image, includeStages);
        writeQuietly(key, fresh);
        return fresh;
    }

    private AnalysisOutcome readQuietly(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("캐시 조회 실패, 원본 호출로 진행합니다: {}", e.getMessage());
            return null;
        }
    }

    private void writeQuietly(String key, AnalysisOutcome outcome) {
        try {
            redis.opsForValue().set(key, outcome, ttl);
        } catch (RuntimeException e) {
            log.warn("캐시 저장 실패, 결과는 정상 반환합니다: {}", e.getMessage());
        }
    }

    static String cacheKey(byte[] image, boolean includeStages) {
        return KEY_PREFIX + sha256Hex(image) + ":" + (includeStages ? "full" : "lean");
    }

    private static String sha256Hex(byte[] image) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
