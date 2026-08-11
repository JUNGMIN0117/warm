"use client";

import Link from "next/link";
import { ChevronRight, History } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { useHistoryQuery } from "@/lib/hooks/use-analysis";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

/**
 * 분석 이력.
 *
 * 원본 이미지를 저장하지 않으므로 각 줄은 대표 피부색 칩 + 판정 + 수치로
 * 구성된다 — 스키마에 이미지 컬럼 자체가 없다 (01-architecture.md §5).
 */
export default function HistoryPage() {
  const { status } = useAuth();
  const query = useHistoryQuery();

  if (status === "loading") {
    return <HistorySkeleton />;
  }

  if (status === "anonymous") {
    return (
      <div className="mx-auto max-w-md pt-12 text-center">
        <History className="mx-auto mb-4 size-10 text-muted-foreground" aria-hidden />
        <h1 className="text-xl font-semibold">이력은 로그인하면 남습니다</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          로그인 상태에서 분석하면 판정과 측정 수치가 저장됩니다.
          <br />
          원본 사진은 저장되지 않습니다.
        </p>
        <div className="mt-6 flex justify-center gap-3">
          <Button render={<Link href="/login" />}>로그인</Button>
          <Button render={<Link href="/register" />} variant="outline">
            회원가입
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold">내 분석 이력</h1>

      {query.isPending && <HistorySkeleton />}

      {query.isError && (
        <Alert variant="destructive">
          <AlertTitle>이력을 불러오지 못했습니다</AlertTitle>
          <AlertDescription>{query.error.message}</AlertDescription>
        </Alert>
      )}

      {query.isSuccess && query.data.length === 0 && (
        <div className="rounded-xl border border-dashed py-16 text-center text-sm text-muted-foreground">
          아직 저장된 분석이 없습니다.{" "}
          <Link href="/" className="underline underline-offset-2 hover:text-foreground">
            첫 분석을 해보세요
          </Link>
        </div>
      )}

      {query.isSuccess && query.data.length > 0 && (
        <ul className="divide-y rounded-xl border">
          {query.data.map((item) => (
            <li key={item.id}>
              <Link
                href={`/history/${item.id}`}
                className="flex items-center gap-4 px-4 py-3.5 transition-colors hover:bg-muted/50"
              >
                <span
                  className="size-10 shrink-0 rounded-full border shadow-sm"
                  style={{ backgroundColor: item.medianRgbHex }}
                  title={`대표 피부색 ${item.medianRgbHex}`}
                />
                <div className="min-w-0 flex-1">
                  <p className="font-medium">
                    {item.emoji} {item.seasonLabelKo}
                    <span className="ml-2 text-sm font-normal text-muted-foreground tabular-nums">
                      신뢰도 {(item.confidence * 100).toFixed(1)}%
                    </span>
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {new Date(item.analyzedAt).toLocaleString("ko-KR", {
                      dateStyle: "medium",
                      timeStyle: "short",
                    })}
                  </p>
                </div>
                <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function HistorySkeleton() {
  return (
    <div className="mx-auto max-w-2xl space-y-3 pt-2">
      {Array.from({ length: 4 }, (_, i) => (
        <Skeleton key={i} className="h-16 w-full rounded-xl" />
      ))}
    </div>
  );
}
