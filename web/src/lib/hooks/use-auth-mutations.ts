"use client";

import { useMutation } from "@tanstack/react-query";
import { login, register } from "@/lib/api/endpoints";
import { useAuth } from "@/lib/auth/auth-context";
import type { LoginRequest, RegisterRequest } from "@/lib/api/types";

/** 로그인·가입 성공 시 곧바로 세션을 연다 — 가입 후 재로그인은 불필요한 마찰이다. */

export function useLoginMutation() {
  const { signIn } = useAuth();
  return useMutation({
    mutationFn: (body: LoginRequest) => login(body),
    onSuccess: signIn,
  });
}

export function useRegisterMutation() {
  const { signIn } = useAuth();
  return useMutation({
    mutationFn: (body: RegisterRequest) => register(body),
    onSuccess: signIn,
  });
}
