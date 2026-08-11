"use client";

import { useCallback } from "react";
import { AlertTriangle, ImageOff } from "lucide-react";
import { useAnalyzeMutation } from "@/lib/hooks/use-analysis";
import { downscaleForUpload } from "@/lib/image/downscale";
import { ApiError } from "@/lib/api/errors";
import { ImagePicker } from "@/components/capture/image-picker";
import { AnalysisProgress } from "@/components/analysis/analysis-progress";
import { ResultView } from "@/components/analysis/result-view";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

/**
 * 홈 = 분석 플로우.
 *
 * 상태는 mutation 하나가 전부다: idle(입력) → pending(진행 애니메이션)
 * → success(결과) / error(안내 + 재시도). 별도 상태 머신을 만들지 않고
 * TanStack Query의 상태를 그대로 화면 상태로 쓴다.
 */
export default function HomePage() {
  const mutation = useAnalyzeMutation();

  const handleAnalyze = useCallback(
    (image: Blob) => {
      void (async () => {
        const optimized = await downscaleForUpload(image);
        mutation.mutate(optimized);
      })();
    },
    [mutation],
  );

  if (mutation.isPending) {
    return <AnalysisProgress />;
  }

  if (mutation.isSuccess) {
    return <ResultView result={mutation.data} onReset={() => mutation.reset()} />;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <section className="space-y-3 pt-4 text-center">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
          사진 한 장으로 알아보는
          <br />내 퍼스널 컬러
        </h1>
        <p className="text-muted-foreground">
          봄웜 · 여름쿨 · 가을웜 · 겨울쿨 — 판정만 주지 않습니다.
          <br className="hidden sm:block" />
          피부색 측정값과 판정 근거까지 수치로 보여드려요.
        </p>
      </section>

      {mutation.isError && <AnalyzeError error={mutation.error} onDismiss={() => mutation.reset()} />}

      <ImagePicker onAnalyze={handleAnalyze} />

      <section className="grid gap-3 text-center text-xs text-muted-foreground sm:grid-cols-3">
        <div className="rounded-lg border bg-muted/20 p-3">
          <p className="font-medium text-foreground">원본 미저장</p>
          사진은 분석 즉시 버려지고 측정 수치만 남습니다
        </div>
        <div className="rounded-lg border bg-muted/20 p-3">
          <p className="font-medium text-foreground">근거 공개</p>
          색상각·명도·채도 3축 측정값을 그대로 보여줍니다
        </div>
        <div className="rounded-lg border bg-muted/20 p-3">
          <p className="font-medium text-foreground">로그인 불필요</p>
          이력을 남기고 싶을 때만 계정을 만드세요
        </div>
      </section>
    </div>
  );
}

/**
 * 분석 실패 안내.
 *
 * "사진을 바꾸면 되는 실패"(422 계열)와 "서비스 문제"(503 등)를 다르게
 * 안내한다 — 전자는 사용자의 다음 행동이 명확해야 하고, 후자는
 * 사용자가 할 수 있는 일이 없음을 정직하게 말해야 한다.
 */
function AnalyzeError({ error, onDismiss }: { error: Error; onDismiss: () => void }) {
  const isPhotoProblem = error instanceof ApiError && error.isRetryableWithDifferentPhoto;

  return (
    <Alert variant={isPhotoProblem ? "default" : "destructive"}>
      {isPhotoProblem ? (
        <ImageOff className="size-4" aria-hidden />
      ) : (
        <AlertTriangle className="size-4" aria-hidden />
      )}
      <AlertTitle>{isPhotoProblem ? "이 사진으로는 분석할 수 없었어요" : "분석에 실패했습니다"}</AlertTitle>
      <AlertDescription className="space-y-2">
        <p>{error.message}</p>
        {isPhotoProblem && (
          <p className="text-xs">
            정면을 바라보고, 얼굴이 화면의 1/3 이상 나오는 밝은 사진일수록 잘 됩니다.
          </p>
        )}
        <Button type="button" size="sm" variant="outline" onClick={onDismiss}>
          확인
        </Button>
      </AlertDescription>
    </Alert>
  );
}
