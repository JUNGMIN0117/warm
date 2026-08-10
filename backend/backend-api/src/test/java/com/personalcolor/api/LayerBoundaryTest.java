package com.personalcolor.api;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 계층 경계 검증 — 모듈 분리가 잡지 못하는 규칙들.
 *
 * <p>모듈 경계는 "api가 domain을 본다"까지만 강제한다. 그 안에서
 * 컨트롤러가 리포지토리를 직접 호출하는 것은 컴파일이 막지 못한다.
 * 그런 규칙을 여기서 잡는다.
 *
 * <p>이 테스트는 클래스패스 전체(세 모듈의 컴파일 결과)를 대상으로 하므로
 * backend-api에 둔다 — 여기가 셋 모두를 볼 수 있는 유일한 모듈이다.
 *
 * <p><b>allowEmptyShould에 대하여.</b> ArchUnit은 대상 클래스가 0개인 규칙을
 * 기본적으로 실패로 처리한다. 좋은 기본값이다 — 오타 난 패키지명으로 아무것도
 * 검사하지 않으면서 통과하는 규칙을 막아준다. 지금은 인프라·컨트롤러가 아직
 * 비어 있어 일부 규칙이 여기 걸리므로 명시적으로 허용해 두었다.
 * 각 계층에 코드가 들어오는 대로 이 플래그를 하나씩 걷어내는 것이 목표다.
 * 그때까지 이 규칙들은 "지켜졌다"가 아니라 "아직 검사할 대상이 없다"는 뜻이다.
 */
@DisplayName("계층 경계")
class LayerBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        // DO_NOT_INCLUDE_JARS를 쓰지 않는다. 멀티모듈에서 backend-domain과
        // backend-infrastructure는 backend-api 입장에서 jar 의존성이므로,
        // 그 옵션을 켜면 검사 대상이 backend-api 자기 클래스만 남는다.
        // 계층 간 규칙을 검사하겠다면서 다른 계층을 빼버리는 셈이다.
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.personalcolor");
    }

    @Test
    @DisplayName("도메인은 인프라를 모른다")
    void domainDoesNotSeeInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.personalcolor.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.personalcolor.infrastructure..")
                .because("의존은 안쪽(도메인)을 향해야 한다 — 반대로 흐르면 "
                        + "DB 스키마 변경이 도메인 모델을 끌고 다닌다");

        rule.check(classes);
    }

    @Test
    @DisplayName("도메인은 API 계층을 모른다")
    void domainDoesNotSeeApi() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.personalcolor.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.personalcolor.api..")
                .because("도메인이 HTTP 표현을 알면 응답 형식을 바꿀 때마다 도메인이 흔들린다");

        rule.check(classes);
    }

    @Test
    @DisplayName("컨트롤러는 리포지토리를 직접 호출하지 않는다")
    void controllersDoNotTouchRepositories() {
        // allowEmptyShould를 걷어냈다 — 이제 컨트롤러가 존재하므로 규칙이
        // 실제로 무언가를 검사한다. 그리고 실제로 잡았다: 초안의 컨트롤러 둘이
        // SeasonProfileRepository를 직접 주입받고 있었고, 규칙을 푸는 대신
        // BrowseSeasonCatalog 유스케이스를 만들어 고쳤다.
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .because("트랜잭션 경계와 도메인 규칙을 건너뛰게 된다 — "
                        + "유스케이스를 거쳐야 한다");

        rule.check(classes);
    }
}
