# web — Personal Color AI 프론트엔드

Next.js 15 · React 19 · TypeScript(strict) · Tailwind v4 · shadcn/ui(Base UI) · TanStack Query · Vitest

**설계 의도와 검토했다 기각한 대안은 [docs/06-frontend.md](../docs/06-frontend.md)에 있다.**

```bash
pnpm install
pnpm dev          # http://localhost:3000 (게이트웨이 기본값 localhost:8080)
pnpm test && pnpm typecheck && pnpm lint
```

게이트웨이 주소가 다르면 `NEXT_PUBLIC_API_BASE_URL` 환경변수로 지정한다.

```
src/
├── lib/
│   ├── api/        게이트웨이 계약 타입 · HTTP 클라이언트 · 세션 · Query 훅
│   ├── verdict.ts  판정 해석 규칙 (경계 케이스 병기, 정확도 주장 금지)
│   ├── errors.ts   오류 code → 화면 안내 매핑
│   └── season.ts   계절 코드 → 시각 테마 (표현은 프론트 소유)
├── components/
│   ├── upload/     드롭존 · 웹캠 캡처
│   ├── pipeline/   전처리 5단계 시각화
│   └── result/     결과 카드 · 3축 게이지 · 확률 분포 · 팔레트
└── app/            / (분석 흐름) · /login · /register · /history
```
