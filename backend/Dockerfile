# Spring Boot 게이트웨이 이미지.
#
# 멀티모듈이라 레이어 캐시 설계가 단일 모듈보다 까다롭다. POM 넷을
# 소스보다 먼저 복사하는 것이 핵심이다 — 그러면 코드를 고쳐도 의존성
# 다운로드(수백 MB, 수 분)를 다시 하지 않는다.

# ---------------------------------------------------------------------------
# 1단계: 빌드
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /build

# Maven Wrapper 먼저. 배포판 버전이 저장소에 고정되어 있어 이미지 안에서도
# 같은 Maven이 돈다.
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd ./
RUN chmod +x mvnw

# POM만 먼저 복사해 의존성 레이어를 분리한다. 소스가 바뀌어도 POM이
# 그대로면 이 아래 RUN은 캐시에서 나온다.
COPY pom.xml ./
COPY backend-domain/pom.xml         backend-domain/
COPY backend-infrastructure/pom.xml backend-infrastructure/
COPY backend-api/pom.xml            backend-api/

# go-offline은 멀티모듈에서 형제 모듈을 찾지 못해 실패할 수 있다.
# -Dsilent로 출력을 줄이고, 실패해도 빌드를 막지 않는다(|| true) —
# 목적이 "완벽한 오프라인화"가 아니라 "캐시 워밍"이기 때문이다.
RUN ./mvnw -B -q dependency:go-offline -DexcludeGroupIds=com.personalcolor || true

COPY backend-domain/src         backend-domain/src
COPY backend-infrastructure/src backend-infrastructure/src
COPY backend-api/src            backend-api/src

# 테스트는 여기서 돌리지 않는다. Testcontainers가 Docker 데몬을 요구하는데
# 이미지 빌드 중에 그것을 쓰는 것(DinD)은 복잡도에 비해 얻는 게 없다.
# 검증은 CI가 한다.
RUN ./mvnw -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# 2단계: 런타임
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-noble AS runtime

# 루트로 실행하지 않는다.
RUN useradd --create-home --uid 10001 appuser

WORKDIR /app

# 실행 가능한 jar는 backend-api 하나뿐이다(spring-boot-maven-plugin이
# 붙은 유일한 모듈). 나머지 둘은 그 안에 라이브러리로 들어 있다.
COPY --from=build --chown=appuser:appuser \
     /build/backend-api/target/backend-api-*.jar app.jar

USER appuser
EXPOSE 8080

# 컨테이너 메모리 한도를 인식하게 한다. 이 옵션이 없으면 JVM이 호스트
# 전체 메모리를 기준으로 힙을 잡아 OOMKilled를 맞는다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD ["sh", "-c", "curl -fsS http://127.0.0.1:8080/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
