"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/** 네 계절 점 로고 — 브랜드 색은 globals.css의 --season-* 변수가 단일 출처. */
function SeasonDots() {
  return (
    <span className="flex items-center gap-0.5" aria-hidden>
      {["--season-spring", "--season-summer", "--season-autumn", "--season-winter"].map(
        (varName) => (
          <span
            key={varName}
            className="size-2 rounded-full"
            style={{ backgroundColor: `var(${varName})` }}
          />
        ),
      )}
    </span>
  );
}

export function SiteHeader() {
  const { status, auth, signOut } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  return (
    <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
        <div className="flex items-center gap-6">
          <Link href="/" className="flex items-center gap-2">
            <SeasonDots />
            <span className="text-gradient-brand text-lg font-bold tracking-tight">사계</span>
            <span className="hidden text-[11px] font-medium tracking-[0.2em] text-muted-foreground uppercase sm:inline">
              sagye
            </span>
          </Link>
          <nav className="flex items-center gap-1 text-sm">
            <Link
              href="/"
              className={cn(
                "rounded-md px-3 py-1.5 hover:bg-muted",
                pathname === "/" && "bg-muted font-medium",
              )}
            >
              분석
            </Link>
            <Link
              href="/history"
              className={cn(
                "rounded-md px-3 py-1.5 hover:bg-muted",
                pathname.startsWith("/history") && "bg-muted font-medium",
              )}
            >
              이력
            </Link>
            {auth?.role === "ADMIN" && (
              <Link
                href="/admin"
                className={cn(
                  "rounded-md px-3 py-1.5 hover:bg-muted",
                  pathname.startsWith("/admin") && "bg-muted font-medium",
                )}
              >
                관리
              </Link>
            )}
          </nav>
        </div>
        <div className="flex items-center gap-2">
          {status === "authenticated" && auth !== null ? (
            <>
              <span className="hidden text-sm text-muted-foreground sm:inline">
                {auth.displayName}님
              </span>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => {
                  signOut();
                  router.push("/");
                }}
              >
                로그아웃
              </Button>
            </>
          ) : (
            <Button render={<Link href="/login" />} size="sm" variant="outline">
              로그인
            </Button>
          )}
        </div>
      </div>
    </header>
  );
}
