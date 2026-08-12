# CLAUDE.md — Personal Color AI

이 파일은 Claude Code를 위한 프로젝트 컨텍스트입니다. 모든 세션에서 이 규칙을 따릅니다.

---

## 프로젝트 정체성

**얼굴 사진 한 장으로 4계절 퍼스널 컬러(봄웜·여름쿨·가을웜·겨울쿨)를 판정하고, 판정 근거까지 수치로 보여주는 웹 서비스.** 서비스명은 **사계(SAGYE)** — UI·문서의 사용자 노출 표기에 쓴다. 브랜드 색은 `web/src/app/globals.css`의 `--season-*` 변수가 단일 출처.

2022년 대학 팀 프로젝트 「딥러닝에 기반한 퍼스널 컬러 분석」의 재구축이다. 원본 소스코드는 git 미사용으로 유실됐고, 결과보고서(.hwp)와 발표자료(.pptx)만 남아 그것을 근거로 다시 짓는다. 단순 복원이 아니라 원본 보고서가 스스로 지적한 한계 3가지를 해결하는 것이 목표다:

- **P1** 조도에 따라 판정이 흔들림 → 화이트밸런스 정규화를 전처리 첫 단계로
- **P2** 피부색이 아니라 얼굴 윤곽이 학습됨 → 분류기 입력을 이미지가 아닌 **색채 통계 벡터**로 (공간 정보 원천 차단)
- **P3** 크롤링 라벨("웜톤 연예인" 검색어)이 오염됨 → 규칙 엔진을 기준선으로, 학습 모델은 대조군으로

상세: `docs/00-overview.md`

## 이 프로젝트의 이중 목적

1. 동작하는 서비스
2. **포트폴리오** — 개발자의 주력은 Java/Spring Boot. 아키텍처에서 Spring이 중심에 서야 하고, 모든 결정은 ADR로, 모든 모듈은 "왜"를 설명하는 문서와 함께 남긴다.

---

## 아키텍처 (ADR-001에서 확정)

```
Next.js 15 (web/)  ──▶  Spring Boot 4.1 / Java 21 (backend/)  ──▶  FastAPI / Python 3.12 (ml-service/)
                              │                                          무상태 · CV/ML 추론 전용
                              ▼
                    PostgreSQL 16 + Redis
```

| 책임 | Spring Boot | FastAPI |
|---|---|---|
| 인증(JWT)·DB(JPA)·트랜잭션·이력·팔레트 추천 | ✅ | ❌ |
| OpenCV·MediaPipe·ONNX 추론 | ❌ | ✅ |
| 파일 업로드 수신 | ✅ | ❌ (바이트를 전달받음) |

FastAPI는 **완전 무상태**를 유지한다. DB·인증·세션 금지. 입력은 이미지 바이트, 출력은 JSON. Spring 측은 Resilience4j 서킷 브레이커 + Redis 캐시(이미지 해시 키)로 FastAPI 장애에 대비한다.

- 빌드: **Maven + Wrapper** (Java) / uv (Python) / pnpm (Node) — 근거: ADR-006
- Java 모듈: `backend-domain`(프레임워크 의존 0) → `backend-infrastructure` → `backend-api`.
  의존 방향을 빌드가 강제하고, 모듈이 못 잡는 규칙은 ArchUnit이 잡는다.
- **Spring Boot 4는 Boot 3과 아티팩트명·패키지가 다르다.** `starter-web`이 아니라
  `starter-webmvc`, 테스트 스타터는 기능별 분리, `starter-aop`는 제거됨.
  기억이나 검색 결과에 의존하지 말고 실물로 확인할 것 (ADR-006에 확인된 목록).
- 프론트: Next.js 15 App Router, React 19, TypeScript, Tailwind v4, shadcn/ui, Motion, TanStack Query
- 테스트: JUnit 5 + AssertJ + Testcontainers / pytest / Vitest
- 인프라: Docker Compose, GitHub Actions

---

## 현재 상태 — Step 0 완료

`ml-service/app/domain/` 에 순수 도메인 계층이 완성되어 있다 (34개 테스트 통과, `pytest` 0.2초).

| 파일 | 내용 |
|---|---|
| `color_space.py` | sRGB→선형→XYZ→CIELab (감마 제거 필수!), LCh 극좌표, YCrCb |
| `features.py` | `SkinFeatures` 데이터클래스, ITA° 계산, **중앙값 + 명도기준 10% 절사** 통계 |
| `seasons.py` | `Season` enum, 계절별 추천/기피 팔레트(HEX), 스타일링 팁 |
| `classifier.py` | `CalibrationConfig`(모든 임계값 집결), 3축 프로토타입 거리 + 소프트맥스 분류 |

### 건드리면 안 되는 설계 불변식

1. **도메인 계층은 이미지 I/O·웹 프레임워크·모델에 의존하지 않는다.** cv2, fastapi, torch를 `app/domain/` 안에서 import하지 마라. 이 경계가 Java(DJL) 이식 시 명세가 된다.
2. **분류기 입력에 공간 정보를 넣지 않는다.** (N,3) 픽셀 배열 → `SkinFeatures` → 판정. 이미지 텐서를 직접 분류기에 넣는 순간 P2가 재발한다.
3. **평균 금지, 중앙값 사용.** `test_median_resists_outliers`가 회귀 테스트로 지키고 있다.
4. **확률 분포 전체를 반환한다.** top-1만 내보내는 API를 만들지 마라. 언더톤(웜/쿨) 신뢰도는 4계절과 별도로 보고한다.
5. **임계값은 전부 `CalibrationConfig`로.** 매직 넘버를 코드에 흩뿌리지 마라. 현재 값(hue_center=62°, ita_center=48° 등)은 문헌 기반 잠정값이며 이유가 docstring에 있다 — 바꿀 땐 근거를 함께 수정.

---

## 로드맵

- [x] **Step 0** — 도메인 코어 (색공간·특징추출·규칙 분류기)
- [x] **Step 1** — 얼굴 검출 + 피부 마스킹 파이프라인 (`ml-service/app/pipeline/`)
  - 최종 순서: 얼굴 검출 → 화이트밸런스(얼굴 제외 배경으로 조명 추정) → 랜드마크로 눈·눈썹·입술 폴리곤 제외 → YCrCb inRange ∧ Otsu(원본 방식 계승) → 피부 픽셀 배열
  - **초안의 "WB 먼저" 순서를 뒤집었다** — 전체 프레임 Gray-World가 피부의 웜기를 조명으로 오인해 제거하고, 훼손 정도가 얼굴의 프레임 점유율에 비례한다(P1과 같은 성질의 새 편향). 근거: ADR-004
  - 중간 단계 이미지는 `PipelineStages`로 구조화해 반환 — 프론트 "파이프라인 시각화" UI가 소비한다
- [x] **Step 2** — FastAPI 무상태 추론 서비스 (`ml-service/app/api/`)
  - `POST /v1/analyze` (multipart, `?include_stages`), `GET /health`, OpenAPI 자동 노출
  - **응답 경계**: 측정·판정은 Python이, 팔레트·라벨·스타일링 팁은 Spring(DB)이 소유. 근거: ADR-005
  - 동기 엔드포인트 + `DetectorPool` (MediaPipe는 스레드 안전이 아님)
  - `create_app(state_builder=...)` 주입 지점으로 HTTP 계층을 모델 없이 테스트
- [x] **Step 3** — Spring Boot 게이트웨이 (`backend/`)
  - Maven 멀티모듈(domain/infrastructure/api) + ArchUnit 계층 검증 (ADR-006)
  - ml-service 클라이언트: `Caching → CircuitBreaking → WebClient` 데코레이터 3겹.
    **순서 중요** — 캐시가 바깥이어야 장애 중에도 아는 답을 서빙한다
  - **서킷 브레이커는 `ImageRejectedException`을 실패로 세지 않는다.** 얼굴 없는
    사진은 정상 동작의 결과이지 장애가 아니다
  - JPA + Flyway 3단계, 팔레트 시드는 `export_palettes.py --format sql` 산출물
  - **익명 분석 허용.** JWT 필터는 토큰이 없거나 잘못돼도 거절하지 않는다 —
    거절하면 익명 흐름이 막힌다. 응답의 `saved`로 저장 여부를 알린다
  - **원본 이미지는 저장하지 않는다.** 해시·측정 수치·대표 피부색만.
    스키마에 컬럼 자체가 없고 테스트가 그것을 지킨다
  - JWT 서명 키에 기본값 없음 — 없으면 기동 실패 (`PCAI_JWT_SECRET`)
- [x] **Compose · CI** (Step 6에서 앞당김)
  - 네 서비스 컨테이너화. 멀티스테이지 + 레이어 캐시(POM/락파일 먼저 복사)
  - **ml-service와 Redis는 호스트에 노출하지 않는다.** ml-service는 인증이 없다
  - `depends_on: service_healthy` — `service_started`로는 Flyway가 DB보다 먼저 돈다
  - CI 4단계: Python 검증 ∥ Java 검증 → 이미지 빌드 → **Compose 종단 기동**
  - CI는 모델을 받지 않는다 (모델 없으면 102 passed / 2 skipped)
- [x] **Step 4** — Next.js 프론트 (`web/`)
  - 업로드/웹캠 → **하이브리드 시각화**(대기 중 단계 애니메이션 + 결과 아래 접이식 상세 — 사용자 확정) → 결과 카드 + 3축 게이지 + 팔레트 + 인증/이력
  - `/api/*`는 Next rewrites로 게이트웨이 프록시 — CORS 없음. **rewrites는 빌드 타임 고정**이라 컨테이너는 `--build-arg API_PROXY_TARGET`으로 굽는다 (ADR-007)
  - JWT는 localStorage, **클라이언트에서 디코드 금지** — 만료는 서버가 준 `expiresAt`으로만
  - `topTwoMargin < 0.15`면 경계 판정 안내 — 표현 기준이라 프론트 소유
  - standalone 출력은 `BUILD_STANDALONE=1`일 때만 (Windows+pnpm 심링크 EPERM)
  - Vitest 23개 — 계약 테스트 (오류 매핑·토큰 보관·확률/경계 표시)
- [x] **Step 5 (도구)** — pseudo-label·학습·평가 하네스 (`ml-service/scripts/`)
  - `generate_pseudo_labels.py`(규칙 엔진 → labels.csv + crop/masked 두 변형) → `train_cnn.py`(PyTorch, 원본급 SmallCnn, ONNX 내보내기) → `evaluate_models.py`(pseudo 일치율·ECE·Grad-CAM)
  - **CNN은 대조군** (ADR-002·009). torch는 `train` 그룹 — 배포 이미지 미포함. CI는 `uv sync --group train`(아니면 mypy에서 torch가 Any로 격하되어 로컬·CI가 갈라짐)
  - "정확도"라는 말 금지 — pseudo 기준 수치는 전부 **일치율(agreement)**. 절대 정확도는 Phase 3 수동 검증셋에서만
  - 합성 300장으로 종단 검증 완료. Grad-CAM은 훅 직접 구현
- [x] **Step 5 (실행)** — 2026-08-12 완료 (docs/02 §7, docs/08 §7)
  - FairFace 5,000장 → pseudo 3,643 / 탈락 1,357. 수동 검증셋 **76장 확정**(약 450장 중 확신 17%만 — 라벨링이 어렵다는 것 자체가 발견)
  - **첫 실측 정확도: 규칙 엔진 63.2%** (n=76, 확신 케이스 한정, ±11%p). CNN은 val∩수동 8장이라 측정 불가
  - **P2 재현 확인**: crop 모델 CAM 질량의 피부 내 비율 28% vs masked 38% (피부 점유율 15%). masked가 pseudo 일치율도 더 높음(96.6% vs 94.9%)
  - h° 임계값 역산 최적점 = 현행 62° → 보정이 아니라 특징 판별력의 한계. ITA 관찰(in-sample 72%)은 탐색적
  - **수치 인용 규칙**: 위 정확도는 반드시 n·"확신 케이스 한정"과 함께. UI에는 여전히 정확도 미표기
- [x] **Step 6a** — 관측성
  - 상관관계 ID: **게이트웨이가 발급**(`X-Request-Id`, 유효하면 수용), ml-service는 수신만. 프론트는 실패 화면에 "문의 코드"로 노출 (ADR-008)
  - Java는 MDC(+`finally` 정리 필수 — 스레드 재사용), Python은 contextvars(스레드풀 전파 때문에 threading.local 불가)
  - `CorrelationIdRelay`는 `.block()` 덕에 MDC를 읽을 수 있다 — 완전 리액티브 전환 시 Reactor Context로
  - 구조화 로그는 컨테이너에서만: Spring `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`(Boot 4 내장), Python `PCAI_LOG_FORMAT=json`(표준 lib 포매터)
  - 헬스체크 프로브 로그는 DEBUG로 — INFO 도배 방지
- [x] **Step 6b (릴리스)** — GHCR 이미지 게시. release 잡은 compose 종단 통과 후 main에서만. **실서버 배포는 안 하기로 결정**(사용자, 2026-08-12). 게시 이미지 실행: `docker-compose.ghcr.yml` 오버레이
- [x] **Step 6b (회고)** — 08-retrospective.md 작성됨 (**중간 회고** — §7의 체크리스트가 Step 5 실행 후 갱신 지점)

각 Step 완료 시 README의 진행 상황 체크박스를 갱신한다.

---

## 작업 방식 (사용자와의 약속)

1. **결정 지점에서는 반드시 사용자에게 물어본다.** 아키텍처·범위·트레이드오프가 갈리는 선택은 임의로 정하지 말고 선택지를 제시하고 답을 기다린다. 단, **프론트엔드 UX/UI는 예외** — 사용자가 "최적의 형태로 뽑아낼 것"을 위임했으므로 디자인은 Claude가 결정하고 근거를 문서로 남긴다.
2. **기능 구현과 문서 작성은 동시에.** 코드를 만들면 같은 커밋 안에서 해당 `docs/` 문서를 만들거나 갱신한다. 문서는 나중에 몰아 쓰지 않는다.
3. **테스트 없는 도메인 로직은 미완성으로 취급한다.**

## 포트폴리오 문서 자동 갱신 (상시 규칙)

**이 저장소는 백엔드 포트폴리오다.** 10년차 백엔드 개발자가 평가한다고 가정하고, 사용자가 면접에서 모든 결정을 직접 설명할 수 있어야 한다. 따라서 작업할 때마다 아래를 **묻지 말고 자동으로** 갱신한다.

| 무엇이 생기면 | 어디를 갱신하나 |
|---|---|
| 새 기술 결정 / 트레이드오프 | `07-decisions/ADR-00N-*.md` 추가 |
| 새 개념 사용 (라이브러리·패턴·프로토콜) | `10-engineering-notes.md` §2에 **왜 필요한가 → 무엇인가 → 어떻게 썼나** 추가 |
| 면접에서 물을 만한 지점 | `10-engineering-notes.md` §3에 예상 질문+답변 추가 |
| 스키마 변경 | `09-data-model.md` + README의 ERD |
| 엔드포인트 변경 | `05-api-spec.md` + README의 API 표 |
| 새 의존성 | README 기술 스택 표에 **버전 + 선택 이유** |
| 테스트 수 변경 | README 테스트 표, 배지 |
| 측정 못 한 것 / 한계 발견 | README 「알려진 한계」 + `10-engineering-notes.md` §5 |
| **측정이 기존 주장을 반증** | 주장을 **철회**하고 그 경위를 남긴다 (테스트를 고치지 않는다) |
| 삽질·함정 (버전 이동, 설정 함정 등) | `10-engineering-notes.md` §2.12 또는 해당 개념 절에 ⚠️로 |

**작성 기준**
- 처음 보는 사람이 이해할 수 있게. 개념은 정의부터 쓴다.
- 사용자가 혼자 읽고 학습할 수 있게. "이 코드가 뭘 한다"가 아니라 "왜 이 선택이 옳았나".
- 실측하지 않은 것은 **미측정**이라고 쓴다. Phase 3 전까지 정확도 수치 금지.
- 사용자가 그대로 말할 수 있는 문장으로 쓴다.

## 문서 규칙

`docs/` 구조 (00, 03, 07은 이미 존재):

```
docs/
 ├ 00-overview.md        원본 분석 + 재구축 목표 (완료)
 ├ 01-architecture.md    시스템 구조 (완료 — 경계·계층·데이터·오류 흐름)
 ├ 02-data-pipeline.md   데이터 수집·라벨링 (완료 — Phase 2/3 실행 절차·P2 실험 설계)
 ├ 03-color-theory.md    색채 이론·분류 알고리즘 (완료 — 도메인 변경 시 함께 갱신)
 ├ 04-preprocessing.md   마스킹 파이프라인 (완료 — 파이프라인 변경 시 함께 갱신)
 ├ 05-api-spec.md        엔드포인트 명세 (완료 — ML 서비스 + Spring 게이트웨이)
 ├ 06-frontend.md        UX 설계 의도 (완료 — 하이브리드 시각화·프록시·토큰 결정)
 ├ 07-decisions/         ADR (완료: 001 스택, 002 데이터, 003 분류범위,
 │                            004 파이프라인 순서, 005 서비스 경계,
 │                            006 빌드·모듈·Boot 4, 007 프론트 통합,
 │                            008 관측성, 009 학습 스택,
 │                            010 언더톤 재보정 — 라운드 2 검증 대기)
 └ 08-retrospective.md   원본 대비 회고 (완료 — Step 5 실행 후 §7 갱신)
```

- 문서 톤: 기존 문서(`03-color-theory.md`)를 기준 삼는다. **"무엇을"이 아니라 "왜"를 쓴다.** 검토했다 기각한 대안과 그 이유를 남긴다. 불릿 남발 금지, 서술형 위주.
- 새 기술 결정이 생기면 `07-decisions/ADR-00N-제목.md` 추가 (상태/맥락/검토한 선택지/결정/결과/재검토 조건 형식).
- 한계와 미검증 사항은 숨기지 않고 명시한다.

## 코드 규칙

- **주석·docstring은 한국어**, "왜"를 설명. 식별자는 영어.
- Python: 3.12, 전체 타입 힌트, `ruff` + `mypy --strict` 통과, 라인 100자.
- Java: 21, record 적극 사용, Lombok 지양(record와 생성자 주입으로 충분).
- TypeScript: strict, `any` 금지.
- 커밋: Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`). 커밋 메시지 본문도 한국어 가능.

## 금지 사항

- 크롤링한 이미지·데이터셋·모델 가중치를 git에 커밋하지 않는다 (`.gitignore`에 이미 정의됨).
- Phase 3(수동 검증셋 평가) 전까지 UI·문서에서 **정확도 수치를 주장하지 않는다.** 신뢰도 계수와 확률 분포로만 말한다.
- 실존 인물(연예인) 사진을 저장소·문서·데모에 포함하지 않는다.
- 도메인 판정 결과를 의료·피부과적 조언처럼 표현하지 않는다. "참고용 재미 지표"임을 UI에 명시한다.

## 검증 명령

의존성은 uv로 관리한다. 모델 가중치는 커밋되지 않으므로 새 환경에서는 먼저 받아야 한다.

```bash
cd ml-service && uv sync && uv run python scripts/download_models.py
```

```bash
cd ml-service && uv run pytest -q                                  # 111 passed 여야 정상
cd ml-service && uv run ruff check . && uv run mypy app/ tests/ scripts/
```

모델 파일이 없으면 MediaPipe 실추론 테스트는 skip된다 (109 passed / 2 skipped) — CI에서 모델 없이도 회귀를 잡을 수 있게 의도한 설계다.

백엔드는 Docker가 켜져 있어야 한다 (Testcontainers).

```bash
cd backend && ./mvnw verify                                        # 141 tests
```

프론트:

```bash
cd web && pnpm test && pnpm lint && pnpm typecheck                 # 25 tests
```

전체 스택:

```bash
docker compose up --build --wait
```
