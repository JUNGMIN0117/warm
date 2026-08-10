"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";

import { ResultView } from "@/components/result/result-view";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAnalysis } from "@/lib/api/queries";
import { toGuidance } from "@/lib/errors";

/**
 * 이력 단건 상세 — 결과 화면을 그대로 재사용한다.
 *
 * 저장 시 단계 이미지는 남기지 않으므로(원본 미보관 원칙의 연장)
 * stages는 null로 오고, ResultView가 해당 섹션을 알아서 생략한다.
 */
export default function HistoryDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const analysis = useAnalysis(params.id);

  if (analysis.isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-64 w-full rounded-3xl" />
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    );
  }

  if (analysis.isError) {
    const guidance = toGuidance(analysis.error);
    return (
      <div className="mx-auto mt-16 max-w-sm space-y-4 text-center">
        <h1 className="text-xl font-bold">{guidance.title}</h1>
        <p className="text-sm text-muted-foreground">{guidance.body}</p>
        <Button variant="outline" nativeButton={false} render={<Link href="/history" />}>
          이력으로 돌아가기
        </Button>
      </div>
    );
  }

  return <ResultView result={analysis.data} onRestart={() => router.push("/")} />;
}
