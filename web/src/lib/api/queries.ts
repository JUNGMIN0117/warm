/**
 * TanStack Query 훅 — 서버 상태의 단일 진입점.
 *
 * 컴포넌트는 fetch를 직접 부르지 않고 이 훅들만 쓴다.
 * 캐시 키 규칙: ["history"], ["analysis", id]. 분석 실행은 mutation이다 —
 * 같은 사진을 다시 올려도 서버(Redis)가 캐시하므로 프론트가 중복 제거를
 * 흉내 낼 필요가 없다.
 */

"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { request } from "./client";
import { saveSession } from "./token";
import type {
  AnalysisResponse,
  AuthResponse,
  HistoryItem,
  LoginRequest,
  RegisterRequest,
} from "./types";

/**
 * 분석 실행. includeStages=true를 항상 붙인다 — 전처리 시각화가
 * 이 프론트의 존재 이유 중 하나이고, 단계 이미지의 응답 크기는
 * 서버 계약(400KB 상한)이 이미 통제하고 있다.
 */
export function useAnalyze() {
  return useMutation({
    mutationFn: async (image: Blob) => {
      const form = new FormData();
      form.append("image", image, "photo.jpg");
      return request<AnalysisResponse>("/api/v1/analyses?includeStages=true", {
        method: "POST",
        body: form,
      });
    },
  });
}

export function useHistory(enabled: boolean) {
  return useQuery({
    queryKey: ["history"],
    queryFn: () => request<HistoryItem[]>("/api/v1/analyses?limit=50"),
    enabled,
  });
}

export function useAnalysis(id: string) {
  return useQuery({
    queryKey: ["analysis", id],
    queryFn: () => request<AnalysisResponse>(`/api/v1/analyses/${id}`),
  });
}

/** 가입 성공 = 로그인 성공. 응답의 토큰을 바로 저장한다 (재로그인 마찰 제거). */
export function useRegister() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: RegisterRequest) =>
      request<AuthResponse>("/api/v1/auth/register", { method: "POST", json: body }),
    onSuccess: (auth) => {
      saveSession({
        accessToken: auth.accessToken,
        expiresAt: auth.expiresAt,
        displayName: auth.displayName,
      });
      // 계정이 바뀌면 이전 계정의 이력 캐시는 무효다.
      void queryClient.invalidateQueries({ queryKey: ["history"] });
    },
  });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: LoginRequest) =>
      request<AuthResponse>("/api/v1/auth/login", { method: "POST", json: body }),
    onSuccess: (auth) => {
      saveSession({
        accessToken: auth.accessToken,
        expiresAt: auth.expiresAt,
        displayName: auth.displayName,
      });
      void queryClient.invalidateQueries({ queryKey: ["history"] });
    },
  });
}
