"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRegisterMutation } from "@/lib/hooks/use-auth-mutations";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";

/** 비밀번호 최소 길이 — 백엔드 Bean Validation(@Size(min = 10))과 같은 값. */
const PASSWORD_MIN_LENGTH = 10;

export default function RegisterPage() {
  const router = useRouter();
  const mutation = useRegisterMutation();
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");

  const passwordTooShort = password.length > 0 && password.length < PASSWORD_MIN_LENGTH;

  return (
    <div className="mx-auto max-w-sm pt-8">
      <Card className="relative overflow-hidden">
        <span className="bg-gradient-brand absolute inset-x-0 top-0 h-1" aria-hidden />
        <CardHeader>
          <CardTitle>회원가입</CardTitle>
          <CardDescription>
            계정은 분석 이력 저장에만 쓰입니다. 원본 사진은 계정이 있어도 저장되지 않습니다.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (passwordTooShort) return;
              mutation.mutate(
                { email, displayName, password },
                { onSuccess: () => router.push("/") },
              );
            }}
          >
            {mutation.isError && (
              <Alert variant="destructive">
                <AlertDescription>{mutation.error.message}</AlertDescription>
              </Alert>
            )}
            <div className="space-y-2">
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
            <div className="space-y-2">
              <Label htmlFor="displayName">표시 이름</Label>
              <Input
                id="displayName"
                autoComplete="nickname"
                required
                maxLength={50}
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">비밀번호</Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                required
                minLength={PASSWORD_MIN_LENGTH}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                aria-invalid={passwordTooShort}
              />
              <p className={passwordTooShort ? "text-xs text-destructive" : "text-xs text-muted-foreground"}>
                {PASSWORD_MIN_LENGTH}자 이상이어야 합니다
              </p>
            </div>
            <Button type="submit" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending ? "가입 중…" : "가입하고 시작하기"}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            이미 계정이 있나요?{" "}
            <Link href="/login" className="underline underline-offset-2 hover:text-foreground">
              로그인
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
