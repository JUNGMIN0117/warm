# 문서 안내

이 프로젝트를 처음 보신다면 **아래 순서대로** 읽으시면 됩니다. 각 문서는 "무엇을 만들었는가"가 아니라 **"왜 그렇게 만들었는가"** 를 씁니다 — 검토했다 기각한 대안과 그 이유가 함께 있습니다.

---

## 목적별 안내

### 🎯 "이 프로젝트가 뭔지 5분 안에 알고 싶다"

**[../README.md](../README.md)** → 개요, 아키텍처, 기술 스택, ERD, 실행 방법

### 🏗 "백엔드 설계를 평가하고 싶다"

1. **[01-architecture.md](01-architecture.md)** — 경계·계층·데이터 흐름·오류 처리 전략
2. **[09-data-model.md](09-data-model.md)** — 스키마 설계 근거, 인덱스, 제약조건
3. **[07-decisions/](07-decisions/)** — ADR 6건. 각 결정의 검토 과정
4. **[05-api-spec.md](05-api-spec.md)** — API 계약과 오류 코드

### 📚 "이 프로젝트를 공부하고 싶다 / 면접을 준비한다"

**[10-engineering-notes.md](10-engineering-notes.md)** — 개념 설명(헥사고날·서킷 브레이커·JWT·트랜잭션 등), 예상 질문 20개, 스스로 점검 체크리스트, **아직 답할 수 없는 것들**

### 🔬 "도메인(퍼스널 컬러)이 궁금하다"

1. **[00-overview.md](00-overview.md)** — 원본 프로젝트 분석, 무엇이 문제였나
2. **[03-color-theory.md](03-color-theory.md)** — 색채 이론, 왜 CIELab인가, 분류 알고리즘
3. **[04-preprocessing.md](04-preprocessing.md)** — 화이트밸런스·얼굴 검출·피부 마스킹

---

## 전체 목록

| 문서 | 내용 | 상태 |
|---|---|---|
| [00-overview.md](00-overview.md) | 원본 프로젝트 분석과 재구축 목표 (P1·P2·P3) | ✅ |
| [01-architecture.md](01-architecture.md) | 시스템 구조 — 경계·계층·데이터·오류 흐름 | ✅ |
| 02-data-pipeline.md | 데이터 수집·라벨링 전략 | Step 5 |
| [03-color-theory.md](03-color-theory.md) | 색채 이론과 분류 알고리즘 | ✅ |
| [04-preprocessing.md](04-preprocessing.md) | 전처리 파이프라인 | ✅ |
| [05-api-spec.md](05-api-spec.md) | API 명세 (게이트웨이 + ML 서비스) | ✅ |
| 06-frontend.md | UX 설계 의도 | Step 4 |
| [07-decisions/](07-decisions/) | ADR 6건 | ✅ |
| 08-retrospective.md | 원본 대비 개선점 회고 | Step 6 |
| [09-data-model.md](09-data-model.md) | 데이터 모델 상세 | ✅ |
| [10-engineering-notes.md](10-engineering-notes.md) | 개념 정리 + 면접 대비 | ✅ |

---

## ADR (Architecture Decision Record)

기술 결정을 기록한 문서입니다. **상태·맥락·검토한 선택지·결정·결과·재검토 조건** 형식으로 씁니다. 이미 내린 결정을 나중에 바꾸더라도 **원문은 고치지 않고** 상단에 갱신 표시만 답니다 — ADR은 "그때 무엇을 알고 어떻게 판단했는가"의 기록이기 때문입니다.

| # | 제목 | 한 줄 요약 |
|---|---|---|
| [001](07-decisions/ADR-001-tech-stack.md) | Spring + FastAPI 폴리글랏 | MediaPipe 서버사이드 Java 지원이 빈약해서 나눔 |
| [002](07-decisions/ADR-002-data-strategy.md) | 규칙 엔진 우선, 학습은 대조군 | 크롤링 라벨이 오염되어 오차 출처를 분리할 수 없음 |
| [003](07-decisions/ADR-003-classification-scope.md) | 4계절 채택, 8타입 비목표 | 검증 불가능한 세분화를 하지 않음 |
| [004](07-decisions/ADR-004-pipeline-order.md) | 검출을 화이트밸런스보다 먼저 | 전체 프레임 Gray-World가 피부의 웜기를 지움 |
| [005](07-decisions/ADR-005-service-boundary.md) | 측정=Python, 해석=Spring | 팔레트 변경에 추론 서버 재배포가 필요한 구조 회피 |
| [006](07-decisions/ADR-006-build-and-modules.md) | Maven · 3모듈 · Boot 4.1 | 의존 방향을 컴파일이 강제 |

---

## 문서 작성 규칙

이 저장소의 문서는 다음을 지킵니다. 새 문서를 추가하실 때도 같은 기준을 따라 주세요.

**"무엇을"이 아니라 "왜"를 씁니다.** 코드를 읽으면 아는 것은 반복하지 않습니다.

**기각한 대안을 남깁니다.** "A를 골랐다"보다 "B와 C를 검토했고 이런 이유로 A"가 훨씬 유용합니다. 나중에 상황이 바뀌었을 때 재검토 지점이 명확해집니다.

**한계와 미검증 사항을 숨기지 않습니다.** "아직 측정하지 않았습니다"를 명시합니다. 이 프로젝트는 **Phase 3(수동 검증셋 평가) 전까지 정확도 수치를 주장하지 않습니다.**

**측정이 주장을 반증하면 주장을 고칩니다.** 실제로 한 번 있었습니다 ([04-preprocessing §5](04-preprocessing.md)).

**불릿 남발을 피하고 서술형으로 씁니다.** 표는 비교할 때만 씁니다.
