"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Palette } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function SiteHeader() {
  const { status, auth, signOut } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  return (
    <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
        <div className="flex items-center gap-6">
          <Link href="/" className="flex items-center gap-2 font-semibold">
            <Palette className="size-5 text-primary" aria-hidden />
            퍼스널 컬러 AI
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
