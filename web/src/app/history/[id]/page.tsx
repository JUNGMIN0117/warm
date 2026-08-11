"use client";

import { use } from "react";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { useAnalysisQuery } from "@/lib/hooks/use-analysis";
import { ResultView } from "@/components/analysis/result-view";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

/**
 * 이력 단건 상세. 저장된 분석에는 단계 이미지(stages)가 없으므로
 * ResultView가 파이프라인 섹션을 자연히 생략한다 — 원본 사진을 저장하지
 * 않는다는 원칙의 결과다.
 */
export default function HistoryDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { status } = useAuth();
  const query = useAnalysisQuery(id);

  if (status === "anonymous") {
    return (
      <div className="pt-12 text-center text-sm text-muted-foreground">
        <p>이 페이지는 로그인이 필요합니다.</p>
        <Button render={<Link href="/login" />} className="mt-4">
          로그인
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <Button render={<Link href="/history" />} variant="ghost" size="sm" className="-ml-2">
        <ArrowLeft aria-hidden /> 이력으로
      </Button>

      {(status === "loading" || query.isPending) && (
        <div className="space-y-4">
          <Skeleton className="h-64 w-full rounded-2xl" />
          <div className="grid gap-4 md:grid-cols-2">
            <Skeleton className="h-48 rounded-xl" />
            <Skeleton className="h-48 rounded-xl" />
          </div>
        </div>
      )}

      {query.isError && (
        <Alert variant="destructive">
          <AlertTitle>분석을 찾을 수 없습니다</AlertTitle>
          <AlertDescription>{query.error.message}</AlertDescription>
        </Alert>
      )}

      {query.isSuccess && <ResultView result={query.data} />}
    </div>
  );
}
