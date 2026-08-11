# syntax=docker/dockerfile:1
# 프론트엔드 이미지 — 3단계 멀티스테이지.
#
# 레이어 캐시 전략은 다른 서비스와 동일하다: 락파일을 소스보다 먼저
# 복사해서, 코드만 바뀐 빌드에서 의존성 설치 레이어가 재사용되게 한다.

# ── 1) 의존성 ────────────────────────────────────────────────
FROM node:22-alpine AS deps
WORKDIR /app
# corepack이 package.json의 packageManager(pnpm@11.x)를 읽어 정확한
# 버전을 내려받는다 — 이미지에 pnpm을 따로 굽지 않아도 버전이 고정된다.
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

# ── 2) 빌드 ──────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# ⚠️ rewrites는 **빌드 타임**에 고정된다 — next.config.ts가 standalone
# 출력에 직렬화되므로 런타임 환경변수로는 프록시 대상을 바꿀 수 없다.
# 그래서 여기서 빌드 인자로 굽는다. 기본값은 Compose의 서비스 이름이고,
# 다른 배포 대상이 필요하면 --build-arg로 재빌드한다 (ADR-007 결과 참조).
ARG API_PROXY_TARGET=http://backend:8080
# standalone 출력은 Docker에서만 켠다 — Windows 로컬 빌드는 심볼릭 링크
# 권한(EPERM) 때문에 실패한다 (next.config.ts 주석 참조).
ENV BUILD_STANDALONE=1 \
    NEXT_TELEMETRY_DISABLED=1 \
    API_PROXY_TARGET=$API_PROXY_TARGET
RUN pnpm build

# ── 3) 런타임 ────────────────────────────────────────────────
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    HOSTNAME=0.0.0.0 \
    PORT=3000
# 루트로 서비스를 돌리지 않는다.
RUN addgroup -S nodejs && adduser -S nextjs -G nodejs
# standalone에는 서버 실행에 필요한 node_modules 부분집합이 포함된다.
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=build --chown=nextjs:nodejs /app/public ./public
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
