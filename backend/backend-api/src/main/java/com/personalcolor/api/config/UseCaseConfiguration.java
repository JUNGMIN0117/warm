package com.personalcolor.api.config;

import com.personalcolor.domain.analysis.AnalyzeImage;
import com.personalcolor.domain.analysis.ViewAnalysisHistory;
import com.personalcolor.domain.analysis.port.AnalysisRepository;
import com.personalcolor.domain.analysis.port.PersonalColorAnalyzer;
import com.personalcolor.domain.season.BrowseSeasonCatalog;
import com.personalcolor.domain.season.UpdateSeasonCuration;
import com.personalcolor.domain.season.port.SeasonProfileRepository;
import com.personalcolor.domain.user.AuthenticateUser;
import com.personalcolor.domain.user.RegisterUser;
import com.personalcolor.domain.user.port.PasswordHasher;
import com.personalcolor.domain.user.port.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 도메인 유스케이스를 빈으로 등록한다.
 *
 * <p>유스케이스 클래스에 {@code @Service}를 붙이지 않은 결과가 이 파일이다.
 * 애너테이션 하나면 끝날 일을 굳이 이렇게 하는 이유는 도메인 모듈이
 * Spring을 의존하지 않게 하기 위해서다 — 그 규칙을 ArchUnit이 검사하고,
 * 모듈 경계가 컴파일 단계에서 강제한다 (ADR-006).
 *
 * <p>대가는 이 파일 하나이고, 얻는 것은 스프링 없이 도는 도메인 테스트다.
 */
@Configuration
public class UseCaseConfiguration {

    /**
     * 시각 공급자.
     *
     * <p>{@code Instant.now()}를 직접 부르지 않고 주입하는 이유는 테스트다.
     * 저장 시각이 결과에 들어가므로 고정하지 못하면 시간에 의존하는 단언을
     * 쓸 수 없다.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AnalyzeImage analyzeImage(
            PersonalColorAnalyzer analyzer,
            AnalysisRepository analyses,
            SeasonProfileRepository profiles,
            Clock clock) {
        return new AnalyzeImage(analyzer, analyses, profiles, clock);
    }

    @Bean
    public BrowseSeasonCatalog browseSeasonCatalog(SeasonProfileRepository profiles) {
        return new BrowseSeasonCatalog(profiles);
    }

    @Bean
    public UpdateSeasonCuration updateSeasonCuration(SeasonProfileRepository profiles) {
        return new UpdateSeasonCuration(profiles);
    }

    @Bean
    public ViewAnalysisHistory viewAnalysisHistory(
            AnalysisRepository analyses, BrowseSeasonCatalog catalog) {
        return new ViewAnalysisHistory(analyses, catalog);
    }

    @Bean
    public RegisterUser registerUser(UserRepository users, PasswordHasher hasher, Clock clock) {
        return new RegisterUser(users, hasher, clock);
    }

    @Bean
    public AuthenticateUser authenticateUser(UserRepository users, PasswordHasher hasher) {
        return new AuthenticateUser(users, hasher);
    }
}
