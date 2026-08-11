"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import type { ReactNode } from "react";
import { AuthProvider } from "@/lib/auth/auth-context";
import { Toaster } from "@/components/ui/sonner";
import { ApiError } from "@/lib/api/errors";

/**
 * 클라이언트 전역 프로바이더.
 *
 * QueryClient를 useState로 만드는 이유: 모듈 스코프에 두면 SSR에서
 * 요청 간에 캐시가 공유된다. 컴포넌트 상태로 두면 브라우저 세션당 하나다.
 */
export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            // 4xx는 재시도해도 같은 답이다. 네트워크성 실패만 한 번 더 본다.
            retry: (failureCount, error) => {
              if (error instanceof ApiError && error.status < 500) return false;
              return failureCount < 1;
            },
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        {children}
        <Toaster position="top-center" richColors />
      </AuthProvider>
    </QueryClientProvider>
  );
}
