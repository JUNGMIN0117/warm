# Claude Code 킥오프 프롬프트

아래 구분선 사이 내용을 Claude Code 첫 메시지로 그대로 붙여넣으세요.
(프로젝트 폴더 `personal-color-ai/`를 작업 디렉토리로 열고 시작해야 합니다 — CLAUDE.md가 자동으로 읽힙니다.)

---

이 프로젝트는 유실된 2022년 팀 프로젝트(딥러닝 기반 퍼스널 컬러 분석)를 결과보고서와 발표자료만 근거로 재구축하는 작업이다. 전체 컨텍스트·아키텍처·규칙은 루트의 CLAUDE.md에 있고, Step 0(도메인 코어)은 이미 완료된 상태로 이 저장소에 들어 있다. CLAUDE.md와 docs/ 전체, ml-service/app/domain/ 코드를 먼저 읽고 시작해라.

## 지금 세션에서 할 일

### 0. 초기화 및 검증
1. `git init` 후 현재 상태를 첫 커밋으로 만들어라. 커밋 메시지: `feat: 도메인 코어 — 색공간 변환, 특징 추출, 규칙 기반 4계절 분류기`
2. `cd ml-service && python -m pytest tests/ -q` 실행해서 34 passed 확인. 실패하면 진행하지 말고 원인부터 보고해라.
3. ruff와 mypy를 돌려보고, 위반이 있으면 수정하되 CLAUDE.md의 설계 불변식 5개는 절대 깨지 마라.

### 1. Step 1 — 얼굴 검출 + 피부 마스킹 파이프라인
`ml-service/app/pipeline/` 에 구현한다. 목표: 이미지 바이트 입력 → 도메인 계층의 `extract_features()`에 넘길 (N,3) 피부 픽셀 배열 출력.

파이프라인 순서 (CLAUDE.md 로드맵과 동일):
1. **화이트밸런스 정규화** — Gray-World를 기본으로 구현. 원본 프로젝트가 보고서에서 스스로 지적한 P1(조도 문제)에 대한 답이므로, 왜 이 방법인지 docstring에 남겨라.
2. **얼굴 검출** — 방식은 아래 "먼저 물어볼 것" 1번의 답을 받은 뒤 진행.
3. **랜드마크 기반 제외** — 눈·눈썹·입술 영역을 폴리곤으로 마스크에서 제거.
4. **피부 마스킹** — 원본 방식 계승: YCrCb `inRange` 마스크와 Otsu 이진화 마스크의 `bitwise_and`. 이 교집합 아이디어는 원본이 잘한 부분이므로 유지한다 (근거는 docs/00-overview.md의 "원본이 잘한 것" 참조).
5. **결과 구조화** — 각 중간 단계(원본/WB보정/얼굴크롭/마스크/최종피부)를 담는 dataclass를 정의해라. Step 4의 프론트엔드가 "전처리 파이프라인 시각화" UI로 이걸 그대로 소비할 예정이다.

요구사항:
- 파이프라인 코드도 도메인 계층처럼 한국어 docstring으로 "왜"를 설명한다.
- 얼굴이 안 잡히는 경우, 여러 명이 잡히는 경우의 처리 방침을 정해서 명시적 예외 타입으로 표현해라.
- 합성 이미지(단색 피부 패치 + 도형)로라도 단위 테스트를 만들어라. 실제 사람 사진을 저장소에 넣지 마라 (CLAUDE.md 금지사항).
- 완료 시 `docs/04-preprocessing.md`를 작성한다. 톤은 docs/03-color-theory.md를 기준으로: 무엇이 아니라 왜, 기각한 대안 포함.
- 커밋은 Conventional Commits로 의미 단위마다 쪼개라.

## 먼저 물어볼 것 (구현 시작 전에 나에게 질문해라)

1. **얼굴 검출 방식** — 다음 중 무엇으로 할지:
   - (a) MediaPipe Face Landmarker만 사용 (깔끔한 모던 구현)
   - (b) MediaPipe + 원본의 Haar Cascade 둘 다 구현하고 성능 비교를 문서화 (포트폴리오 서사 강화, 공수 증가)
   - (c) MediaPipe 기본 + Haar를 폴백으로만
2. **중간 결과 확인 방식** — 파이프라인 단계별 이미지를 (a) 그때그때 PNG로 저장해 보여주며 진행할지, (b) 일단 구현하고 마지막에 모아서 보여줄지.

이 두 답을 받기 전에는 Step 1의 2번(얼굴 검출) 이후 코드를 작성하지 마라. 0번(초기화)과 1번(화이트밸런스)은 답과 무관하므로 먼저 진행해도 된다.

## 진행 중 지켜야 할 것

- 결정이 갈리는 지점(라이브러리 선택, 예외 정책, 임계값 등)은 선택지와 트레이드오프를 제시하고 내 답을 기다려라. 단, 사소한 구현 디테일까지 묻지는 마라 — ADR에 남길 수준의 결정만.
- 기능 구현과 docs/ 문서 갱신은 같은 흐름에서 한다. 나중에 몰아 쓰지 않는다.
- Step 1이 끝나면 README의 진행 상황 체크박스를 갱신하고, Step 2(FastAPI) 착수 전에 나에게 요약 보고를 해라.

---

## 참고: 프로젝트 구조 (현재 상태)

```
personal-color-ai/
├ CLAUDE.md                  ← 프로젝트 규칙 (자동 로드)
├ README.md                  ← 포트폴리오 대문
├ .gitignore
├ docs/
│  ├ 00-overview.md          원본 분석 + P1/P2/P3 문제 정의
│  ├ 03-color-theory.md      색채 이론 + 분류 알고리즘 근거
│  └ 07-decisions/
│     ├ ADR-001-tech-stack.md          Spring+FastAPI 폴리글랏 결정
│     ├ ADR-002-data-strategy.md       규칙엔진 우선 3단계 전략
│     └ ADR-003-classification-scope.md 4계절 채택, 8타입 비목표
└ ml-service/
   ├ pyproject.toml
   ├ app/domain/             ← 순수 도메인 (I/O 의존 금지)
   │  ├ color_space.py       sRGB→CIELab, LCh, YCrCb
   │  ├ features.py          SkinFeatures, ITA°, 중앙값 통계
   │  ├ seasons.py           4계절 프로필 + 팔레트
   │  └ classifier.py        CalibrationConfig + 프로토타입 분류
   └ tests/test_domain.py    34 tests
```
