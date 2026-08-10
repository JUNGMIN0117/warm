# Personal Color AI

> 얼굴 사진 한 장으로 4계절 퍼스널 컬러를 판정하고, **왜 그렇게 판정했는지**까지 보여주는 서비스

2022년 대학 팀 프로젝트로 만들었던 **「딥러닝에 기반한 퍼스널 컬러 분석」** 을 4년 뒤 다시 짓는 프로젝트입니다. 원본 소스코드는 유실됐고, 결과보고서와 발표자료만 남아 있습니다.

단순 복원이 아닙니다. 당시 보고서가 결론 항목에 스스로 적어둔 한계를 이번엔 해결하는 것이 목표입니다.

> *"환경·조도·카메라·각도 등 영향을 끼칠 수 있는 변수가 상당히 많아 정밀한 이미지 데이터셋을 만들 수 없다. (…) 얼굴의 윤곽이 학습될 수도 있기 때문에 확실하게 피부색만 추출할 수 있도록 보완할 것이다."*
> — 2022년 결과보고서, 「프로젝트 결과 논의」

---

## 무엇이 달라지는가

| | 2022 원본 | 2026 재구축 |
|---|---|---|
| **분류** | 웜/쿨 2분류 | 4계절 (봄웜·여름쿨·가을웜·겨울쿨) |
| **얼굴 검출** | Haar Cascade | MediaPipe Face Landmarker (478 랜드마크) |
| **조도 보정** | 없음 | Gray-World 화이트밸런스 (배경 기반 조명 추정) |
| **판정 근거** | 없음 (CNN 블랙박스) | CIELab 3축 수치 + Grad-CAM |
| **기준선** | 없음 | 색채학 규칙 엔진 (학습 데이터 0건으로 동작) |
| **신뢰도** | 없음 | 확률 분포 + 입력 품질 계수 |
| **인터페이스** | `plt.show()` | Next.js 웹앱 (전처리 파이프라인 실시간 시각화) |
| **백엔드** | 없음 | Spring Boot + FastAPI 폴리글랏 MSA |
| **버전 관리** | ❌ (그래서 유실됨) | ✅ |

---

## 아키텍처

```
┌──────────────┐      ┌────────────────────┐      ┌──────────────────┐
│   Next.js    │─────▶│   Spring Boot 3.4  │─────▶│  FastAPI         │
│   React 19   │◀─────│   Java 21          │◀─────│  Python 3.12     │
│   (Web)      │      │   API · 인증 · 이력  │      │  CV · ML 추론     │
└──────────────┘      └─────────┬──────────┘      └──────────────────┘
                                ▼
                    PostgreSQL 16  +  Redis
```

Java와 Python 중 하나를 고르지 않고 둘 다 쓰는 이유는 [ADR-001](docs/07-decisions/ADR-001-tech-stack.md)에 정리했습니다. 요약하면, 비즈니스 로직·트랜잭션·인증은 Spring이 압도적으로 낫고 컴퓨터 비전은 Python 생태계가 압도적으로 낫습니다. 억지로 한쪽에 몰아넣는 대신 경계를 명확히 긋고 HTTP로 잇습니다.

---

## 판정 방식

퍼스널 컬러 이론의 두 축을 CIELab 좌표계 위에 올립니다.

```
              Warm (h° 높음)        Cool (h° 낮음)
   Light   │   🌸 봄 웜           │   ☀️ 여름 쿨
   ────────┼─────────────────────┼───────────────────
   Deep    │   🍂 가을 웜         │   ❄️ 겨울 쿨
```

| 축 | 지표 | 의미 |
|---|---|---|
| **언더톤** | `h°` — CIELab 색상각 | 노란기 ↔ 푸른기 |
| **명도** | `ITA°` — Individual Typology Angle | 밝음 ↔ 깊음 |
| **선명도** | `C*` — 채도 | 클리어 ↔ 뮤트 |

세 축을 정규화해 3차원 공간에 놓고, 각 계절의 **프로토타입 좌표**까지의 가중 거리를 소프트맥스로 확률화합니다. `if-else` 트리 대신 이 방식을 쓴 이유는 [03-color-theory.md](docs/03-color-theory.md)에 있습니다.

### 실측 예시

| 입력 피부색 | h° | ITA° | C\* | 판정 | 신뢰도 |
|---|---|---|---|---|---|
| `#F3D5A5` | 82.6 | 53.1 | 27.8 | 🌸 봄 웜 | 77% |
| `#E8C4C0` | 28.9 | 78.2 | 13.9 | ☀️ 여름 쿨 | 99% |
| `#C68642` | 68.5 | 13.8 | 49.0 | 🍂 가을 웜 | 82% |
| `#A0705F` | 45.5 | 5.7 | 23.9 | ❄️ 겨울 쿨 | 97% |

---

## 기술 스택

**Backend (Java)** — Java 21 · Spring Boot 3.4 · Spring Data JPA · Spring Security (JWT) · WebClient · Gradle (Kotlin DSL) · PostgreSQL 16 · Flyway · Redis

**ML Service (Python)** — Python 3.12 · FastAPI · OpenCV · MediaPipe · NumPy · ONNX Runtime · PyTorch

**Frontend** — Next.js 15 (App Router) · React 19 · TypeScript · Tailwind CSS v4 · shadcn/ui · Motion · TanStack Query

**Infra & QA** — Docker Compose · GitHub Actions · JUnit 5 + Testcontainers · pytest · Vitest · Ruff · mypy

---

## 진행 상황

- [x] **Step 0** — 도메인 코어: 색공간 변환 · 특징 추출 · 규칙 기반 분류기 *(34 tests)*
- [x] **Step 1** — 전처리 파이프라인: 얼굴 검출 · 화이트밸런스 · 피부 마스킹 *(80 tests)*
- [x] **Step 2** — FastAPI 무상태 추론 서비스: `POST /v1/analyze` · `GET /health` *(104 tests)*
- [ ] Step 3 — Spring Boot API 게이트웨이
- [ ] Step 4 — Next.js 프론트엔드
- [ ] Step 5 — CNN 학습 및 규칙 엔진 대비 성능 비교
- [ ] Step 6 — Docker Compose 통합 및 배포

---

## 문서

| 문서 | 내용 |
|---|---|
| [00-overview.md](docs/00-overview.md) | 원본 프로젝트 분석과 재구축 목표 |
| [03-color-theory.md](docs/03-color-theory.md) | 색채 이론 및 분류 알고리즘 |
| [04-preprocessing.md](docs/04-preprocessing.md) | 전처리 파이프라인 — 화이트밸런스·검출·마스킹 |
| [05-api-spec.md](docs/05-api-spec.md) | ML 서비스 HTTP 계약과 그 설계 근거 |
| [07-decisions/](docs/07-decisions/) | ADR — 주요 기술 결정의 근거 |

---

## 개발 환경

Python 의존성은 [uv](https://docs.astral.sh/uv/)로 관리합니다. 락파일이 커밋되어 있어 재현 가능합니다.

```bash
cd ml-service && uv sync && uv run python scripts/download_models.py
```

모델 가중치(MediaPipe Face Landmarker, 비교용 Haar cascade)는 저장소에 커밋하지 않고 위 스크립트가 버전 고정 URL과 SHA-256 검증으로 내려받습니다.

```bash
cd ml-service && uv run pytest -q && uv run ruff check . && uv run mypy app/ tests/ scripts/
```
