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
    <div className="relative mx-auto max-w-2xl space-y-8">
      {/* 계절색 블롭 — 히어로 영역의 배경 분위기. 색은 --season-* 변수가 출처 */}
      <div className="absolute inset-x-0 -top-8 -z-10 h-80 overflow-visible" aria-hidden>
        <span
          className="season-blob left-[-10%] top-0 size-52"
          style={{ backgroundColor: "var(--season-spring)" }}
        />
        <span
          className="season-blob right-[-8%] top-4 size-48"
          style={{ backgroundColor: "var(--season-summer)" }}
        />
        <span
          className="season-blob left-[22%] top-28 size-44"
          style={{ backgroundColor: "var(--season-autumn)" }}
        />
        <span
          className="season-blob right-[18%] top-36 size-40"
          style={{ backgroundColor: "var(--season-winter)" }}
        />
      </div>

      <section className="space-y-3 pt-6 text-center">
        <p className="text-sm font-semibold tracking-[0.25em] text-muted-foreground uppercase">
          봄 · 여름 · 가을 · 겨울
        </p>
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
          사진 한 장으로 만나는
          <br />
          <span className="text-gradient-brand">나의 계절</span>
        </h1>
        <p className="text-muted-foreground">
          판정만 주지 않습니다 — 피부색 측정값과
          <br className="hidden sm:block" />
          판정 근거까지 수치로 보여드려요.
        </p>
      </section>

      {mutation.isError && <AnalyzeError error={mutation.error} onDismiss={() => mutation.reset()} />}

      <ImagePicker onAnalyze={handleAnalyze} />

      <section className="grid gap-3 text-center text-xs text-muted-foreground sm:grid-cols-3">
        <div className="rounded-xl border border-rose-200/60 bg-rose-50/50 p-3 dark:border-rose-900/40 dark:bg-rose-950/20">
          <p className="font-medium text-rose-900 dark:text-rose-200">원본 미저장</p>
          사진은 분석 즉시 버려지고 측정 수치만 남습니다
        </div>
        <div className="rounded-xl border border-sky-200/60 bg-sky-50/50 p-3 dark:border-sky-900/40 dark:bg-sky-950/20">
          <p className="font-medium text-sky-900 dark:text-sky-200">근거 공개</p>
          노란기·명도·채도 3축 측정값을 그대로 보여줍니다
        </div>
        <div className="rounded-xl border border-indigo-200/60 bg-indigo-50/50 p-3 dark:border-indigo-900/40 dark:bg-indigo-950/20">
          <p className="font-medium text-indigo-900 dark:text-indigo-200">로그인 불필요</p>
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
        {/* 사진 문제가 아닌 실패에만 문의 코드를 보여준다 — 서버 로그의
            상관관계 ID와 같은 값이라 이 코드 하나로 해당 요청을 찾을 수 있다 */}
        {!isPhotoProblem && error instanceof ApiError && error.requestId !== null && (
          <p className="text-xs text-muted-foreground">
            문의 코드: <code className="font-mono">{error.requestId}</code>
          </p>
        )}
        <Button type="button" size="sm" variant="outline" onClick={onDismiss}>
          확인
        </Button>
      </AlertDescription>
    </Alert>
  );
}
