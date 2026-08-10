# CLAUDE.md — Personal Color AI

이 파일은 Claude Code를 위한 프로젝트 컨텍스트입니다. 모든 세션에서 이 규칙을 따릅니다.

---

## 프로젝트 정체성

**얼굴 사진 한 장으로 4계절 퍼스널 컬러(봄웜·여름쿨·가을웜·겨울쿨)를 판정하고, 판정 근거까지 수치로 보여주는 웹 서비스.**

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
Next.js 15 (web/)  ──▶  Spring Boot 3.4 / Java 21 (backend/)  ──▶  FastAPI / Python 3.12 (ml-service/)
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

- 빌드: Gradle Kotlin DSL (Java) / uv (Python) / pnpm (Node)
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
- [ ] **Step 3** — Spring Boot 게이트웨이 (업로드 → WebClient로 ml-service 호출 → 결과 저장/조회, JWT 인증, Redis 캐시, Resilience4j)
- [ ] **Step 4** — Next.js 프론트 (업로드/웹캠 → 전처리 단계 시각화 → 결과 카드 + 3축 게이지 + 팔레트)
- [ ] **Step 5** — CNN 학습(pseudo-label) + 규칙 엔진 대비 평가 + Grad-CAM으로 P2 검증
- [ ] **Step 6** — Docker Compose 통합, CI, 배포

각 Step 완료 시 README의 진행 상황 체크박스를 갱신한다.

---

## 작업 방식 (사용자와의 약속)

1. **결정 지점에서는 반드시 사용자에게 물어본다.** 아키텍처·범위·트레이드오프가 갈리는 선택은 임의로 정하지 말고 선택지를 제시하고 답을 기다린다. 단, **프론트엔드 UX/UI는 예외** — 사용자가 "최적의 형태로 뽑아낼 것"을 위임했으므로 디자인은 Claude가 결정하고 근거를 문서로 남긴다.
2. **기능 구현과 문서 작성은 동시에.** 코드를 만들면 같은 커밋 안에서 해당 `docs/` 문서를 만들거나 갱신한다. 문서는 나중에 몰아 쓰지 않는다.
3. **테스트 없는 도메인 로직은 미완성으로 취급한다.**

## 문서 규칙

`docs/` 구조 (00, 03, 07은 이미 존재):

```
docs/
 ├ 00-overview.md        원본 분석 + 재구축 목표 (완료)
 ├ 01-architecture.md    시스템 구조 (Step 3 시점에 작성)
 ├ 02-data-pipeline.md   데이터 수집·라벨링 (Step 5)
 ├ 03-color-theory.md    색채 이론·분류 알고리즘 (완료 — 도메인 변경 시 함께 갱신)
 ├ 04-preprocessing.md   마스킹 파이프라인 (완료 — 파이프라인 변경 시 함께 갱신)
 ├ 05-api-spec.md        엔드포인트 명세 (ML 서비스 완료 — Spring 추가 시 갱신)
 ├ 06-frontend.md        UX 설계 의도 (Step 4)
 ├ 07-decisions/         ADR (완료: 001 스택, 002 데이터, 003 분류범위,
 │                            004 파이프라인 순서, 005 서비스 경계)
 └ 08-retrospective.md   원본 대비 개선점 (Step 6)
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
cd ml-service && uv run pytest -q                                  # 80 passed 여야 정상
cd ml-service && uv run ruff check . && uv run mypy app/ tests/ scripts/
```

모델 파일이 없으면 MediaPipe 실추론 테스트는 skip되고 나머지는 통과한다 — CI에서 모델 없이도 대부분의 회귀를 잡을 수 있게 의도한 설계다.
