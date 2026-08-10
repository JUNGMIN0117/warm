"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api/client";
import { useLogin, useRegister } from "@/lib/api/queries";

/**
 * 로그인/가입 공용 폼.
 *
 * 한 컴포넌트로 합친 이유: 두 폼의 차이는 displayName 필드 하나와
 * 오류 처리(EMAIL_ALREADY_USED vs INVALID_CREDENTIALS)뿐이고,
 * 가입 성공이 곧 로그인(응답에 토큰 포함)이라 성공 후 흐름도 같다.
 */
export function AuthForm({ mode }: { mode: "login" | "register" }) {
  const router = useRouter();
  const login = useLogin();
  const register = useRegister();
  const mutation = mode === "login" ? login : register;

  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (mode === "login") {
      login.mutate({ email, password }, { onSuccess: () => router.push("/") });
    } else {
      register.mutate(
        { email, displayName, password },
        { onSuccess: () => router.push("/") },
      );
    }
  };

  const errorMessage = describeError(mutation.error, mode);

  return (
    <Card className="mx-auto mt-12 w-full max-w-sm">
      <CardHeader>
        <CardTitle>{mode === "login" ? "로그인" : "가입"}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="email">이메일</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          {mode === "register" && (
            <div className="space-y-1.5">
              <Label htmlFor="displayName">표시 이름</Label>
              <Input
                id="displayName"
                required
                maxLength={50}
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
              />
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="password">비밀번호</Label>
            <Input
              id="password"
              type="password"
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              required
              minLength={mode === "register" ? 10 : undefined}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            {mode === "register" && (
              <p className="text-xs text-muted-foreground">10자 이상</p>
            )}
          </div>

          {errorMessage && (
            <p role="alert" className="text-sm text-destructive">
              {errorMessage}
            </p>
          )}

          <Button type="submit" className="w-full" disabled={mutation.isPending}>
            {mutation.isPending
              ? "처리 중…"
              : mode === "login"
                ? "로그인"
                : "가입하고 시작하기"}
          </Button>

          <p className="text-center text-xs text-muted-foreground">
            분석 자체는 로그인 없이도 됩니다 — 계정은 이력을 남기고 싶을 때만
            필요합니다.
          </p>
        </form>
      </CardContent>
    </Card>
  );
}

function describeError(error: unknown, mode: "login" | "register"): string | null {
  if (!error) return null;
  if (error instanceof ApiError) {
    // 분기는 code로만 한다 — message 문자열 매칭 금지.
    if (mode === "login" && error.code === "INVALID_CREDENTIALS") {
      return "이메일 또는 비밀번호가 맞지 않습니다.";
    }
    if (mode === "register" && error.code === "EMAIL_ALREADY_USED") {
      return "이미 가입된 이메일입니다. 로그인해 주세요.";
    }
    if (error.code === "VALIDATION_FAILED") {
      return error.message;
    }
    return error.message;
  }
  return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}
