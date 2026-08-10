package com.personalcolor.domain.analysis;

import com.personalcolor.domain.Fixtures;
import com.personalcolor.domain.analysis.port.AnalysisRepository;
import com.personalcolor.domain.analysis.port.PersonalColorAnalyzer;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.SeasonProfile;
import com.personalcolor.domain.season.port.SeasonProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 유스케이스 테스트.
 *
 * <p>스프링도 HTTP도 DB도 없다. 포트가 인터페이스이기 때문에 가짜 구현을
 * 끼우면 끝이고, 그래서 밀리초 단위로 끝난다 — 도메인을 프레임워크에서
 * 떼어낸 실질적 대가다.
 */
@DisplayName("AnalyzeImage 유스케이스")
class AnalyzeImageTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-10T03:00:00Z");
    private static final byte[] IMAGE = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);

    private StubAnalyzer analyzer;
    private RecordingRepository repository;
    private AnalyzeImage useCase;

    @BeforeEach
    void setUp() {
        analyzer = new StubAnalyzer();
        repository = new RecordingRepository();
        useCase = new AnalyzeImage(
                analyzer,
                repository,
                new StubProfiles(),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("측정 결과에 계절 프로필을 붙여 돌려준다")
    void joinsMeasurementWithCuratedProfile() {
        AnalysisView view = useCase.execute(IMAGE, Optional.empty(), false);

        // ADR-005의 두 출처가 여기서 합쳐진다: 측정은 ml-service, 팔레트는 DB.
        assertThat(view.record().measurement().season()).isEqualTo(Season.AUTUMN_WARM);
        assertThat(view.profile().labelKo()).isEqualTo("가을 웜");
        assertThat(view.profile().bestColors()).isNotEmpty();
    }

    @Test
    @DisplayName("로그인 사용자의 분석은 저장한다")
    void persistsForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();

        AnalysisView view = useCase.execute(IMAGE, Optional.of(userId), false);

        assertThat(repository.saved).hasSize(1);
        assertThat(view.record().userId()).contains(userId);
        assertThat(view.record().analyzedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("익명 분석은 저장하지 않는다")
    void doesNotPersistAnonymousAnalysis() {
        // 소유자가 없는 행은 아무도 조회할 수 없으면서 개인정보 성격의
        // 측정값만 쌓는다. 저장하지 않는 것이 정책이다.
        AnalysisView view = useCase.execute(IMAGE, Optional.empty(), false);

        assertThat(repository.saved).isEmpty();
        assertThat(view.record().isAnonymous()).isTrue();
    }

    @Test
    @DisplayName("원본 이미지는 남기지 않고 해시만 남긴다")
    void storesHashNotImage() {
        AnalysisView view = useCase.execute(IMAGE, Optional.of(UUID.randomUUID()), false);

        String hash = view.record().imageHash();
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        // 같은 이미지는 같은 해시 — 캐시 키이자 중복 판별자로 쓸 수 있어야 한다.
        assertThat(AnalyzeImage.sha256Hex(IMAGE)).isEqualTo(hash);
    }

    @Test
    @DisplayName("단계 이미지는 요청했을 때만 전달된다")
    void passesStageFlagThrough() {
        assertThat(useCase.execute(IMAGE, Optional.empty(), false).stages()).isEmpty();
        assertThat(analyzer.lastIncludeStages).isFalse();

        assertThat(useCase.execute(IMAGE, Optional.empty(), true).stages()).isPresent();
        assertThat(analyzer.lastIncludeStages).isTrue();
    }

    @Test
    @DisplayName("빈 파일은 측정기를 부르기 전에 거절한다")
    void rejectsEmptyImageWithoutCallingAnalyzer() {
        assertThatThrownBy(() -> useCase.execute(new byte[0], Optional.empty(), false))
                .isInstanceOf(ImageRejectedException.class)
                .extracting(e -> ((ImageRejectedException) e).reason())
                .isEqualTo(ImageRejectedException.Reason.IMAGE_DECODE_FAILED);

        assertThat(analyzer.callCount).isZero();
    }

    @Test
    @DisplayName("측정기 예외는 그대로 올라간다")
    void propagatesAnalyzerFailure() {
        analyzer.failWith = new AnalyzerUnavailableException("ml-service 응답 없음");

        assertThatThrownBy(() -> useCase.execute(IMAGE, Optional.empty(), false))
                .isInstanceOf(AnalyzerUnavailableException.class);

        assertThat(repository.saved).isEmpty();
    }

    // --- 가짜 포트 구현 ---------------------------------------------------

    private static final class StubAnalyzer implements PersonalColorAnalyzer {
        int callCount;
        boolean lastIncludeStages;
        RuntimeException failWith;

        @Override
        public AnalysisOutcome analyze(byte[] image, boolean includeStages) {
            callCount++;
            lastIncludeStages = includeStages;
            if (failWith != null) {
                throw failWith;
            }
            Measurement measurement = Fixtures.autumnWarmMeasurement();
            return includeStages
                    ? AnalysisOutcome.of(measurement, new StageImages("a", "b", "c", "d", "e"))
                    : AnalysisOutcome.of(measurement);
        }
    }

    private static final class RecordingRepository implements AnalysisRepository {
        final List<AnalysisRecord> saved = new ArrayList<>();

        @Override
        public AnalysisRecord save(AnalysisRecord record) {
            saved.add(record);
            return record;
        }

        @Override
        public Optional<AnalysisRecord> findById(UUID id) {
            return saved.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<AnalysisRecord> findByUserId(UUID userId, int limit) {
            return saved.stream().filter(r -> r.userId().filter(userId::equals).isPresent())
                    .limit(limit).toList();
        }
    }

    private static final class StubProfiles implements SeasonProfileRepository {
        @Override
        public SeasonProfile findBySeason(Season season) {
            return Fixtures.profileFor(season);
        }

        @Override
        public List<SeasonProfile> findAll() {
            return List.of(Fixtures.profileFor(Season.AUTUMN_WARM));
        }
    }
}
