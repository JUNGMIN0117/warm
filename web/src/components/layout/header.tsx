"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSyncExternalStore } from "react";

import { Button } from "@/components/ui/button";
import { clearSession, getSession, subscribeSession } from "@/lib/api/token";

/**
 * 세션 상태를 useSyncExternalStore로 구독한다 — 로그인/로그아웃이
 * 어느 컴포넌트에서 일어나든 헤더가 즉시 따라온다.
 * 서버 스냅샷은 항상 null: 토큰은 브라우저에만 존재한다.
 */
function useSessionName(): string | null {
  return useSyncExternalStore(
    subscribeSession,
    () => getSession()?.displayName ?? null,
    () => null,
  );
}

export function Header() {
  const displayName = useSessionName();
  const router = useRouter();

  return (
    <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-5xl items-center justify-between px-4">
        <Link href="/" className="font-bold tracking-tight">
          Personal Color <span className="text-muted-foreground">AI</span>
        </Link>

        <nav className="flex items-center gap-2">
          {displayName ? (
            <>
              <Button variant="ghost" size="sm" nativeButton={false} render={<Link href="/history" />}>
                내 이력
              </Button>
              <span className="hidden text-sm text-muted-foreground sm:inline">
                {displayName}님
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  clearSession();
                  router.push("/");
                }}
              >
                로그아웃
              </Button>
            </>
          ) : (
            <>
              <Button variant="ghost" size="sm" nativeButton={false} render={<Link href="/login" />}>
                로그인
              </Button>
              <Button size="sm" nativeButton={false} render={<Link href="/register" />}>
                가입
              </Button>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
