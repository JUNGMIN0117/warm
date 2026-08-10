package com.personalcolor.domain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 도메인 순수성 검증.
 *
 * <p>모듈 분리만으로도 컴파일 단계에서 대부분 막히지만, 이 테스트는 두 가지를
 * 더 한다. 첫째, 누군가 backend-domain의 pom에 의존성을 추가하는 순간 —
 * 컴파일은 통과해도 — 여기서 깨진다. 둘째, 위반이 발생했을 때 "왜 안 되는지"를
 * 규칙 이름으로 설명한다. 빌드 에러 메시지는 그 설명을 하지 못한다.
 *
 * <p>ml-service의 도메인 계층이 cv2·fastapi를 임포트하지 않는 규칙과 같다.
 */
@DisplayName("도메인 계층은 프레임워크를 모른다")
class DomainPurityTest {

    private static JavaClasses domainClasses;

    @BeforeAll
    static void importDomain() {
        domainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.personalcolor.domain");
    }

    @Test
    @DisplayName("Spring에 의존하지 않는다")
    void doesNotDependOnSpring() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .because("도메인은 어떤 프레임워크에서도 재사용 가능해야 한다");

        rule.check(domainClasses);
    }

    @Test
    @DisplayName("JPA·Jakarta 영속화에 의존하지 않는다")
    void doesNotDependOnPersistence() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..")
                .because("엔티티는 'DB에 이렇게 저장한다'는 인프라 결정이지 도메인 개념이 아니다");

        rule.check(domainClasses);
    }

    @Test
    @DisplayName("직렬화 라이브러리에 의존하지 않는다")
    void doesNotDependOnJackson() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson..")
                .because("JSON 표현은 API 계층의 관심사다 — 도메인 모델이 전송 형식을 알면 "
                        + "응답 스키마를 바꿀 때마다 도메인이 흔들린다");

        rule.check(domainClasses);
    }
}
