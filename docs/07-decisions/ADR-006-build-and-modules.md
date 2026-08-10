# ADR-006. 빌드 도구는 Maven, 모듈은 셋, Spring Boot는 4.1

- **상태**: 채택
- **결정일**: 2026-08-10
- **관련**: [ADR-001](ADR-001-tech-stack.md), [ADR-005](ADR-005-service-boundary.md)
- **대체**: ADR-001의 "Gradle Kotlin DSL", CLAUDE.md의 "Spring Boot 3.4"

---

## 맥락

Step 3(Spring 게이트웨이)에 착수하면서 세 가지를 정해야 했습니다. 셋 다 초기 문서에 적혀는 있었지만 **검토를 거친 결정이 아니었습니다** — ADR-001을 다시 읽어보니 폴리글랏 구조(Java + Python)는 길게 논증해 놓고 빌드 도구는 스택 목록에 한 줄 있을 뿐 근거가 없었습니다.

---

## 1. 빌드 도구 — Maven

### 검토한 선택지

**Gradle Kotlin DSL (초기 문서의 선택)** — 멀티모듈 구성이 간결하고, 버전 카탈로그(`libs.versions.toml`)로 의존성을 한 파일에 모을 수 있으며, 증분 빌드와 빌드 캐시가 빠릅니다. Kotlin DSL은 타입 안전과 IDE 자동완성이 됩니다.

**Maven ✅** — Spring Boot BOM(`spring-boot-starter-parent`)과의 궁합이 가장 깔끔합니다. 의존성 관리는 Maven의 원래 강점이고 Spring 생태계가 Maven을 1급으로 대접합니다. XML이 선언적이라 "빌드 스크립트에 로직을 넣는" 유혹 자체가 없어 재현성이 공짜로 따라옵니다.

### 결정

**Maven을 채택합니다.**

두 도구의 기술적 차이는 이 규모에서 크지 않습니다. Java 21, Spring Boot 4, Testcontainers, Flyway, JaCoCo, Docker 레이어 캐싱, CI 캐싱 — 전부 양쪽 1급 지원입니다. 커스텀 빌드 로직이 없는 프로젝트에서 Gradle의 강점(증분 빌드, 스크립트 유연성)은 대부분 발현되지 않고, 반대로 Maven의 약점(느림, 장황함)도 크게 드러나지 않습니다.

결정적 요인은 다른 데 있었습니다. 이 저장소는 **포트폴리오**이고, 빌드 파일은 면접에서 설명해야 하는 산출물입니다. 설명할 수 없는 정교함보다 설명할 수 있는 평범함이 낫습니다.

Maven Wrapper(`mvnw`)를 함께 커밋해 Maven 자체를 설치하지 않아도 빌드되게 했습니다. 배포판 버전(3.9.16)이 저장소에 고정되므로 "내 컴퓨터에서는 되는데" 문제가 한 겹 줄어듭니다.

---

## 2. 모듈 구성 — domain / infrastructure / api

### 검토한 선택지

**단일 모듈 + 패키지 경계** — 클래스 10~20개 규모에 멀티모듈은 의식(ceremony)에 가깝고, ArchUnit으로 패키지 경계를 검증하면 "규모에 맞는 판단을 했다"는 신호가 됩니다.

**멀티모듈 3개 ✅**

### 결정

**셋으로 나눕니다.**

```
api  ──▶  infrastructure  ──▶  domain
 └──────────────────────────▶  domain
```

이유는 검증 강도입니다. 단일 모듈에서 "도메인은 Spring을 모른다"는 **테스트가 잡아주는 규칙**이지만, 모듈을 나누면 **컴파일이 막는 사실**이 됩니다. `backend-domain`의 POM에는 ArchUnit(test 스코프) 외에 아무 의존성도 없어서, 누군가 도메인 클래스에 `@Entity`를 붙이는 순간 빌드가 깨집니다.

이것은 ml-service의 `app/domain/`이 `cv2`·`fastapi`를 임포트하지 않는 규칙과 같은 원칙이고, 두 서비스가 같은 이야기를 하게 됩니다 — 차이는 Python 쪽은 규율로, Java 쪽은 빌드 도구가 지킨다는 점뿐입니다.

JPA 엔티티는 도메인이 아니라 인프라에 둡니다. 엔티티는 "DB에 이렇게 저장한다"는 인프라 결정이지 도메인 개념이 아니기 때문입니다. 매핑 보일러플레이트가 생기는 대신 컬럼 하나 바꾸는 일이 도메인 모델을 건드리지 않습니다.

**ArchUnit은 버리지 않았습니다.** 모듈 경계가 잡는 것은 "api가 domain을 본다"까지이고, 그 안에서 컨트롤러가 리포지토리를 직접 호출하는 것은 컴파일이 막지 못합니다. 그런 규칙을 ArchUnit이 맡습니다. 역할이 겹치지 않습니다.

### 구현 중 발견한 함정

ArchUnit 임포트에 `DO_NOT_INCLUDE_JARS`를 켰더니 계층 규칙이 전부 "검사한 클래스 없음"으로 실패했습니다. 멀티모듈에서 `backend-domain`과 `backend-infrastructure`는 `backend-api` 입장에서 **jar 의존성**이라, 그 옵션이 검사 대상에서 다른 계층을 통째로 빼버린 것입니다. 계층 규칙을 검사하겠다면서 다른 계층을 제외하는 셈이라 옵션을 제거했습니다.

ArchUnit이 이것을 조용히 통과시키지 않고 실패로 알려준 덕분에 발견했습니다 — 빈 규칙을 실패로 처리하는 기본값이 정확히 이런 상황을 위해 있습니다.

---

## 3. Spring Boot 3.4 → 4.1

### 맥락

초기 문서는 Spring Boot 3.4를 적어두었습니다. 그런데 프로젝트를 생성하려고 보니 **start.spring.io가 더 이상 3.x를 제공하지 않습니다.** 선택지가 4.1.0(기본)과 4.0.7뿐이었습니다.

3.4는 2024년 말 릴리스로, 2026년 8월 시점에 OSS 지원이 끝난 버전입니다. 신규 프로젝트에 지원 종료된 버전을 고르는 것은 "왜 그랬나요"에 답하기 어렵습니다.

### 결정

**Spring Boot 4.1.0 + Java 21을 채택합니다.**

### 대가 — 생태계 지연

메이저 버전을 새로 따라가는 비용이 실제로 있었습니다. 다음은 문서나 기억이 아니라 빌드를 돌려가며 확인한 것들입니다.

| 항목 | Boot 3 | Boot 4.1 |
|---|---|---|
| 웹 스타터 | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| 테스트 스타터 | `spring-boot-starter-test` 하나 | 기능별 분리 (`starter-webmvc-test` 등) |
| `@EntityScan` | `boot.autoconfigure.domain` | `boot.persistence.autoconfigure` |
| AOP 스타터 | `spring-boot-starter-aop` | **제거됨** |
| Testcontainers PG | `org.testcontainers:postgresql` | `testcontainers-postgresql` |
| **JSON** | Jackson 2 (`com.fasterxml.jackson.databind`) | **Jackson 3** (`tools.jackson.databind`) |
| Redis JSON 직렬화기 | `Jackson2JsonRedisSerializer` | `JacksonJsonRedisSerializer` |

**Jackson 3 전환이 가장 헷갈리는 항목이었습니다.** `ObjectMapper`의 패키지가 `com.fasterxml.jackson.databind`에서 `tools.jackson.databind`로 바뀌었는데, **애너테이션은 여전히 `com.fasterxml.jackson.annotation`** 입니다(jackson-annotations 2.21). 그래서 `@JsonProperty`가 붙은 클래스는 멀쩡히 컴파일되는데 `ObjectMapper`를 임포트하는 순간 "package does not exist"가 납니다. 절반만 동작하니 원인을 짚기 어렵습니다.

덧붙여 Boot 4의 `spring-boot-starter-webflux`는 JSON 지원을 끌어오지 않습니다. `spring-boot-starter-json`을 따로 선언해야 합니다.

가장 큰 문제는 **Resilience4j**였습니다. Spring Boot 자동설정 모듈이 `resilience4j-spring-boot3`까지만 나와 있고(2.4.0 기준) Boot 4용이 없습니다. 게다가 그 스타터는 `@CircuitBreaker` 애너테이션 처리를 위해 `spring-boot-starter-aop`에 의존하는데, 그 스타터가 Boot 4에서 사라졌습니다.

**대응** — 자동설정 대신 **코어 모듈**(`resilience4j-circuitbreaker`, `resilience4j-timelimiter`)을 직접 씁니다. 코어는 프레임워크에 의존하지 않는 순수 자바라 Spring 버전과 무관합니다. 서킷 브레이커를 `@Configuration`에서 배선하고 호출부를 데코레이트합니다.

결과적으로 손해만은 아닙니다. 애너테이션이 대신 해주던 일이 코드로 드러나 면접에서 설명하기 쉽고, 스프링 컨텍스트 없이 서킷 브레이커 동작을 단위 테스트할 수 있습니다.

확인한 나머지 생태계는 준비되어 있었습니다 — springdoc-openapi 3.1.0이 Boot 4를 지원하고, Flyway·Spring Security·Spring Data는 Boot BOM에 포함됩니다.

---

## 결과

### 얻는 것

- 빌드 파일을 설명할 수 있습니다. 포트폴리오에서 이것이 정교함보다 중요합니다.
- 의존 방향이 빌드 단계에서 강제되어 "도메인은 순수하다"가 주장이 아니라 사실이 됩니다.
- 지원되는 최신 프레임워크 위에 있어 "왜 EOL 버전인가"라는 질문을 피합니다.
- Maven Wrapper로 Maven 설치 없이 빌드됩니다.

### 치르는 비용

- **Boot 4 자료가 아직 적습니다.** 스택오버플로 답변 대부분이 Boot 3 기준이라 아티팩트명·패키지 변경을 매번 실물로 확인해야 합니다. 이번 골격 작업에서도 다섯 군데를 고쳤습니다.
- **Resilience4j 자동설정을 쓰지 못합니다.** 배선 코드를 직접 관리해야 하고, 나중에 Boot 4용 스타터가 나오면 이관을 검토해야 합니다.
- 멀티모듈이라 POM이 넷입니다. 의존성 하나 추가할 때 어느 모듈인지 판단해야 하는데, 이는 경계를 강제한 대가이며 의도적입니다.
- Maven은 Gradle보다 느립니다. 현재 전체 빌드 15초 수준이라 문제되지 않지만, 모듈과 테스트가 늘면 재검토 지점이 생깁니다.

### 재검토 조건

- Resilience4j가 Boot 4용 스타터를 내면 수동 배선 유지 여부를 다시 봅니다.
- 전체 빌드가 2분을 넘으면 Gradle 이관 비용과 비교합니다.
- 모듈이 5개를 넘어가면 Maven 멀티모듈의 장황함이 실제 부담인지 측정합니다.
