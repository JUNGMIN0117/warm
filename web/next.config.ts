import type { NextConfig } from "next";

/**
 * 게이트웨이 프록시.
 *
 * 브라우저가 Spring 게이트웨이를 직접 호출하면 오리진이 달라 CORS 설정이
 * 백엔드에 필요해진다. 대신 Next 서버가 /api/* 를 게이트웨이로 중계해
 * 브라우저에게는 항상 같은 오리진만 보이게 한다 — 백엔드는 프론트의
 * 배포 위치를 몰라도 되고, CORS 헤더도 필요 없다 (docs/06-frontend.md).
 *
 * API_PROXY_TARGET: 로컬 개발은 127.0.0.1:8080, Compose에서는 backend:8080.
 */
const nextConfig: NextConfig = {
  // standalone은 Docker 이미지를 위한 출력이다(런타임에 node_modules 불필요).
  // 항상 켜지 않는 이유: Windows 로컬에서는 pnpm의 심볼릭 링크를 복사하는
  // 과정에서 EPERM(관리자/개발자 모드 필요)으로 빌드가 깨진다.
  // Dockerfile이 BUILD_STANDALONE=1을 설정한다.
  output: process.env.BUILD_STANDALONE === "1" ? "standalone" : undefined,
  async rewrites() {
    const target = process.env.API_PROXY_TARGET ?? "http://127.0.0.1:8080";
    return [
      {
        source: "/api/:path*",
        destination: `${target}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
