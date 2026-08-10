"use client";

import Link from "next/link";
import { useSyncExternalStore } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useHistory } from "@/lib/api/queries";
import { getSession, subscribeSession } from "@/lib/api/token";
import { toGuidance } from "@/lib/errors";

/**
 * 분석 이력.
 *
 * 썸네일이 없다 — 원본 이미지를 저장하지 않는 것이 서버 스키마 차원의
 * 결정이므로(이미지 컬럼 자체가 없다), 목록은 대표 피부색 칩과 수치로
 * 구성된다. 이것은 구현의 한계가 아니라 이 서비스의 프라이버시 약속이고,
 * 화면에도 그렇게 설명한다.
 */
export default function HistoryPage() {
  const loggedIn = useSyncExternalStore(
    subscribeSession,
    () => getSession() !== null,
    () => false,
  );
  const history = useHistory(loggedIn);

  if (!loggedIn) {
    return (
      <div className="mx-auto mt-16 max-w-sm space-y-4 text-center">
        <h1 className="text-xl font-bold">로그인이 필요합니다</h1>
        <p className="text-sm text-muted-foreground">
          이력은 계정에만 저장됩니다. 익명으로 분석한 결과는 어디에도 남지
          않으므로 여기서 볼 수 없습니다.
        </p>
        <Button nativeButton={false} render={<Link href="/login" />}>로그인</Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">내 분석 이력</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          원본 사진은 저장되지 않습니다 — 각 항목은 그때 측정된 대표 피부색과
          판정 수치입니다.
        </p>
      </div>

      {history.isPending && (
        <div className="space-y-3">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-20 w-full rounded-xl" />
          ))}
        </div>
      )}

      {history.isError && (
        <p className="text-sm text-destructive">{toGuidance(history.error).body}</p>
      )}

      {history.isSuccess && history.data.length === 0 && (
        <Card>
          <CardContent className="py-10 text-center text-sm text-muted-foreground">
            아직 이력이 없습니다.{" "}
            <Link href="/" className="underline">
              첫 분석을 시작
            </Link>
            해 보세요.
          </CardContent>
        </Card>
      )}

      {history.isSuccess && history.data.length > 0 && (
        <ul className="space-y-3">
          {history.data.map((item) => (
            <li key={item.id}>
              <Link
                href={`/history/${item.id}`}
                className="flex items-center gap-4 rounded-xl border p-4 transition-colors hover:bg-muted/50"
              >
                <span
                  className="h-12 w-12 shrink-0 rounded-full border"
                  style={{ backgroundColor: item.medianRgbHex }}
                  title={`대표 피부색 ${item.medianRgbHex}`}
                  aria-hidden
                />
                <div className="min-w-0 flex-1">
                  <p className="font-medium">
                    {item.emoji} {item.seasonLabelKo}
                  </p>
                  <p className="text-sm text-muted-foreground">
                    {new Date(item.analyzedAt).toLocaleString("ko-KR")}
                  </p>
                </div>
                <span className="font-mono text-sm text-muted-foreground">
                  신뢰도 {(item.confidence * 100).toFixed(0)}%
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
