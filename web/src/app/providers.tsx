"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

/**
 * TanStack Query 클라이언트.
 *
 * useState 초기화 함수로 만드는 이유: 모듈 스코프에 두면 SSR 시
 * 요청 간에 캐시가 공유될 수 있다. 클라이언트 컴포넌트라도 관례를 따른다.
 */
export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // 이력은 자주 안 바뀐다. 분석 직후의 갱신은 invalidate로 명시한다.
            staleTime: 60_000,
            retry: 1,
          },
          mutations: {
            // 분석 요청 자동 재시도 금지 — 서킷 브레이커가 열려 있을 때
            // 클라이언트까지 재시도를 보태면 복구를 방해한다.
            retry: false,
          },
        },
      }),
  );

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
