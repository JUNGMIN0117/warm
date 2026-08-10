package com.personalcolor.infrastructure.persistence;

import com.personalcolor.domain.analysis.AnalysisRecord;
import com.personalcolor.domain.analysis.Measurement;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.SeasonProfile;
import com.personalcolor.domain.season.SeasonProfileMissingException;
import com.personalcolor.infrastructure.mlservice.Measurements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 영속화 통합 테스트 — 진짜 PostgreSQL에서 돈다.
 *
 * <p>H2 같은 인메모리 DB를 쓰지 않는 이유가 분명하다. 이 스키마는
 * {@code jsonb}, 정규식 {@code CHECK} 제약, {@code LOWER()} 함수 인덱스처럼
 * PostgreSQL 고유 기능에 의존한다. H2 호환 모드로는 마이그레이션이
 * 통과하는지조차 확인할 수 없고, 그러면 통합 테스트가 확인하려던 바로
 * 그것을 확인하지 못한다.
 *
 * <p>Docker가 없으면 이 클래스는 실패한다. CI에서는 Docker가 항상 있고,
 * 로컬에서 없이 돌리려면 {@code -DskipITs} 대신 {@code -Dtest='!*Integration*'}로
 * 제외한다 — 조용히 skip되어 "통과했다"고 착각하는 것보다 낫다.
 */
@Testcontainers
@SpringBootTest(classes = PersistenceIntegrationTest.TestApp.class)
@DisplayName("영속화 통합 (PostgreSQL)")
class PersistenceIntegrationTest {

    @Container
    @SuppressWarnings("resource") // Testcontainers가 JVM 종료 시 정리한다
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * 이 테스트만을 위한 최소 부트 설정.
     *
     * <p>{@code @TestConfiguration}이 아니라 {@code @SpringBootConfiguration}인
     * 이유: 전자는 부트가 "설정 클래스"로 인정하지 않아
     * {@code @SpringBootTest(classes = ...)}의 대상이 되지 못한다.
     *
     * <p>실제 애플리케이션 클래스(backend-api의 BackendApplication)를 쓰지
     * 않는 것은 모듈 방향 때문이다 — 인프라는 api를 모른다.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = "com.personalcolor.infrastructure.persistence",
            // ml-service 어댑터 설정까지 끌어오면 Redis 연결을 요구한다.
            // 여기서 검증하려는 것은 영속화뿐이다.
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX, pattern = "com\\.personalcolor\\.infrastructure\\.mlservice\\..*"))
    static class TestApp {

        @Bean
        DynamicPropertyRegistrar postgresProperties() {
            return registry -> {
                registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES::getUsername);
                registry.add("spring.datasource.password", POSTGRES::getPassword);
                registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
                registry.add("spring.flyway.enabled", () -> "true");
            };
        }
    }

    @Autowired
    private JpaAnalysisRepository analyses;

    @Autowired
    private JpaSeasonProfileRepository profiles;

    @Autowired
    private JdbcTemplate jdbc;

    private static AnalysisRecord recordFor(UUID userId) {
        return new AnalysisRecord(
                UUID.randomUUID(),
                "a".repeat(64),
                Optional.ofNullable(userId),
                Measurements.autumnWarm(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, display_name, created_at) "
                        + "VALUES (?, ?, 'x', 'tester', now())",
                id, id + "@example.com");
        return id;
    }

    @Nested
    @DisplayName("스키마와 시드")
    class SchemaAndSeed {

        @Test
        @DisplayName("Flyway 마이그레이션이 전부 적용된다")
        void migrationsApply() {
            Integer applied = jdbc.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);

            assertThat(applied).isEqualTo(3);
        }

        @Test
        @DisplayName("analyses 테이블에 이미지를 담는 컬럼이 없다")
        void schemaCannotStoreImages() {
            // 정책을 문서가 아니라 스키마로 강제한다. 누군가 이미지 컬럼을
            // 추가하면 이 테스트가 막는다.
            List<String> columns = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_name = 'analyses'", String.class);

            assertThat(columns)
                    .noneMatch(c -> c.contains("image_data"))
                    .noneMatch(c -> c.contains("image_blob"))
                    .noneMatch(c -> c.contains("photo"))
                    .contains("image_hash");
        }

        @ParameterizedTest
        @EnumSource(Season.class)
        @DisplayName("네 계절 프로필이 모두 시드되어 있다")
        void allSeasonsSeeded(Season season) {
            SeasonProfile profile = profiles.findBySeason(season);

            assertThat(profile.labelKo()).isNotBlank();
            assertThat(profile.labelEn()).isNotBlank();
            assertThat(profile.emoji()).isNotBlank();
            assertThat(profile.description()).isNotBlank();
            assertThat(profile.keywords()).isNotEmpty();
            assertThat(profile.bestColors()).hasSizeGreaterThanOrEqualTo(6);
            assertThat(profile.worstColors()).isNotEmpty();
            assertThat(profile.stylingTips()).isNotEmpty();
        }

        @Test
        @DisplayName("팔레트 색상이 순서대로 나온다")
        void paletteColorsAreOrdered() {
            SeasonProfile spring = profiles.findBySeason(Season.SPRING_WARM);

            // seasons.py의 첫 번째 추천색. 순서가 뒤집히면 UI의 대표 색이 바뀐다.
            assertThat(spring.bestColors().getFirst().name()).isEqualTo("코랄");
            assertThat(spring.bestColors().getFirst().color().toHex()).isEqualTo("#FF7F50");
        }

        @Test
        @DisplayName("계절 코드가 도메인 enum과 일치한다")
        void seededCodesMatchDomainEnum() {
            // ml-service·DB·Java 세 곳의 코드가 일치해야 조인이 성립한다.
            List<String> seeded = jdbc.queryForList(
                    "SELECT code FROM season_profiles ORDER BY code", String.class);

            assertThat(seeded).containsExactlyInAnyOrder(
                    Season.SPRING_WARM.code(), Season.SUMMER_COOL.code(),
                    Season.AUTUMN_WARM.code(), Season.WINTER_COOL.code());
        }

        @Test
        @DisplayName("HEX 제약이 소문자를 거부한다")
        void hexConstraintRejectsLowercase() {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) "
                            + "VALUES ('spring_warm', 'BEST', 999, '테스트', '#ff7f50')"))
                    .hasMessageContaining("ck_palette_hex");
        }
    }

    @Nested
    @DisplayName("분석 이력")
    class AnalysisPersistence {

        @Test
        @DisplayName("저장한 값이 그대로 돌아온다")
        void roundTripsMeasurement() {
            AnalysisRecord saved = analyses.save(recordFor(insertUser()));

            AnalysisRecord loaded = analyses.findById(saved.id()).orElseThrow();
            Measurement m = loaded.measurement();

            assertThat(m.season()).isEqualTo(Season.AUTUMN_WARM);
            assertThat(m.confidence()).isCloseTo(0.822, within(1e-9));
            assertThat(m.features().medianRgb().toHex()).isEqualTo("#C68642");
            assertThat(m.preprocessing().maskCoverageRatio()).isCloseTo(0.764, within(1e-9));
            assertThat(loaded.imageHash()).isEqualTo(saved.imageHash());
        }

        @Test
        @DisplayName("JSONB에 담긴 축 해석이 보존된다")
        void preservesJsonbAxes() {
            AnalysisRecord saved = analyses.save(recordFor(insertUser()));

            Measurement loaded = analyses.findById(saved.id()).orElseThrow().measurement();

            assertThat(loaded.axes()).hasSize(1);
            assertThat(loaded.axes().getFirst().name()).isEqualTo("undertone");
            assertThat(loaded.axes().getFirst().interpretation()).isEqualTo("웜 성향이 뚜렷합니다");
            assertThat(loaded.axes().getFirst().lowLabel()).isEqualTo("쿨(푸른기)");
        }

        @Test
        @DisplayName("확률 분포 네 값이 모두 보존된다")
        void preservesFullDistribution() {
            AnalysisRecord saved = analyses.save(recordFor(insertUser()));

            Measurement loaded = analyses.findById(saved.id()).orElseThrow().measurement();

            assertThat(loaded.probabilities())
                    .containsEntry(Season.SPRING_WARM, 0.132)
                    .containsEntry(Season.SUMMER_COOL, 0.004)
                    .containsEntry(Season.AUTUMN_WARM, 0.822)
                    .containsEntry(Season.WINTER_COOL, 0.042);
        }

        @Test
        @DisplayName("사용자 이력을 최신순으로 가져온다")
        void findsUserHistoryNewestFirst() {
            UUID userId = insertUser();
            AnalysisRecord older = analyses.save(new AnalysisRecord(
                    UUID.randomUUID(), "b".repeat(64), Optional.of(userId),
                    Measurements.autumnWarm(),
                    Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS)));
            AnalysisRecord newer = analyses.save(recordFor(userId));

            List<AnalysisRecord> history = analyses.findByUserId(userId, 10);

            assertThat(history).extracting(AnalysisRecord::id)
                    .containsExactly(newer.id(), older.id());
        }

        @Test
        @DisplayName("limit이 지켜진다")
        void respectsLimit() {
            UUID userId = insertUser();
            analyses.save(recordFor(userId));
            analyses.save(recordFor(userId));
            analyses.save(recordFor(userId));

            assertThat(analyses.findByUserId(userId, 2)).hasSize(2);
        }

        @Test
        @DisplayName("다른 사용자의 이력은 보이지 않는다")
        void isolatesUsers() {
            UUID mine = insertUser();
            UUID theirs = insertUser();
            analyses.save(recordFor(theirs));

            assertThat(analyses.findByUserId(mine, 10)).isEmpty();
        }

        @Test
        @DisplayName("사용자를 지우면 이력도 지워진다")
        void cascadesUserDeletion() {
            UUID userId = insertUser();
            AnalysisRecord saved = analyses.save(recordFor(userId));

            jdbc.update("DELETE FROM users WHERE id = ?", userId);

            assertThat(analyses.findById(saved.id())).isEmpty();
        }
    }

    @Test
    @Transactional
    @DisplayName("시드가 없는 계절을 찾으면 명시적으로 실패한다")
    void missingProfileFailsLoudly() {
        // @Transactional로 롤백시킨다. 처음에는 finally에서 되돌리려 했는데,
        // season_profiles 삭제가 CASCADE로 팔레트까지 지우기 때문에 프로필
        // 행만 복구하면 다른 테스트가 "추천 색이 0개"로 깨진다. 실제로 그렇게
        // 깨졌고, 수동 복구보다 롤백이 안전하다는 것을 확인한 뒤 바꿨다.
        jdbc.update("DELETE FROM season_profiles WHERE code = ?", Season.WINTER_COOL.code());

        assertThatThrownBy(() -> profiles.findBySeason(Season.WINTER_COOL))
                .isInstanceOf(SeasonProfileMissingException.class)
                .hasMessageContaining("Flyway 시드");
    }
}
