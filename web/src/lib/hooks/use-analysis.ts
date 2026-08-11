"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { analyzeImage, fetchAnalysis, fetchHistory } from "@/lib/api/endpoints";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * 분석·이력 서버 상태 훅.
 *
 * 쿼리 키에 userId를 넣는 이유: 로그아웃 → 다른 계정 로그인 시 이전
 * 사용자의 이력이 캐시에서 흘러나오면 안 된다. 키가 바뀌면 캐시가 분리된다.
 */

export function useAnalyzeMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (image: Blob) => analyzeImage(image),
    onSuccess: (result) => {
      // 로그인 상태였다면 이력에 한 줄 늘었다 — 목록 캐시를 무효화한다.
      if (result.saved) {
        void queryClient.invalidateQueries({ queryKey: ["history"] });
      }
    },
  });
}

export function useHistoryQuery(limit = 20) {
  const { status, auth } = useAuth();
  return useQuery({
    queryKey: ["history", auth?.userId, limit],
    queryFn: () => fetchHistory(limit),
    enabled: status === "authenticated",
  });
}

export function useAnalysisQuery(id: string) {
  const { status, auth } = useAuth();
  return useQuery({
    queryKey: ["analysis", auth?.userId, id],
    queryFn: () => fetchAnalysis(id),
    enabled: status === "authenticated" && id.length > 0,
  });
}
