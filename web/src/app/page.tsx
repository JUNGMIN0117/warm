"use client";

import { AnimatePresence, motion } from "motion/react";

import { Dropzone } from "@/components/upload/dropzone";
import { WebcamCapture } from "@/components/upload/webcam-capture";
import { AnalyzingIndicator } from "@/components/pipeline/analyzing-indicator";
import { ResultView } from "@/components/result/result-view";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useAnalyze } from "@/lib/api/queries";
import { toGuidance } from "@/lib/errors";

/**
 * 홈 = 분석 흐름 전체.
 *
 * 랜딩과 업로드를 분리하지 않는다 — 이 서비스가 하는 일이 곧 분석이므로,
 * 첫 화면에서 사진을 놓는 순간 시작되는 것이 가장 짧은 경로다.
 * 결과도 같은 페이지에서 이어진다: 업로드 → 분석 중 → 근거와 함께 결과.
 */
export default function Home() {
  const analyze = useAnalyze();

  const onImage = (blob: Blob) => {
    analyze.mutate(blob);
  };

  return (
    <div className="flex flex-col gap-8">
      <AnimatePresence mode="wait">
        {analyze.isIdle && (
          <motion.section
            key="idle"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            className="flex flex-col items-center gap-8 pt-8 text-center"
          >
            <div className="space-y-3">
              <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
                사진 한 장으로 알아보는
                <br />내 퍼스널 컬러
              </h1>
              <p className="mx-auto max-w-md text-muted-foreground">
                판정 결과만 던지지 않습니다. 어떤 전처리를 거쳐 어떤 수치가
                측정됐고, 왜 그 계절인지 <strong>근거까지</strong> 보여드립니다.
              </p>
            </div>

            <Dropzone onImage={onImage} />

            <div className="flex items-center gap-3 text-sm text-muted-foreground">
              <span>또는</span>
              <WebcamCapture onImage={onImage} />
            </div>

            <p className="max-w-sm text-xs text-muted-foreground">
              사진은 분석에만 쓰이고 서버에 저장되지 않습니다. 로그인 상태라면
              측정 수치만 이력에 남습니다.
            </p>
          </motion.section>
        )}

        {analyze.isPending && (
          <motion.section
            key="pending"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <AnalyzingIndicator />
          </motion.section>
        )}

        {analyze.isError && (
          <motion.section
            key="error"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="mx-auto flex w-full max-w-md flex-col items-center gap-4 pt-16"
          >
            {(() => {
              const guidance = toGuidance(analyze.error);
              return (
                <>
                  <Alert variant="destructive">
                    <AlertTitle>{guidance.title}</AlertTitle>
                    <AlertDescription>{guidance.body}</AlertDescription>
                  </Alert>
                  <Button onClick={() => analyze.reset()}>
                    {guidance.fixableByUser ? "다른 사진 선택" : "처음으로"}
                  </Button>
                </>
              );
            })()}
          </motion.section>
        )}

        {analyze.isSuccess && (
          <motion.section
            key="result"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
          >
            <ResultView result={analyze.data} onRestart={() => analyze.reset()} />
          </motion.section>
        )}
      </AnimatePresence>
    </div>
  );
}
