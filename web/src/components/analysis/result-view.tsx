"use client";

import Link from "next/link";
import { motion } from "motion/react";
import { AlertTriangle, History, Lightbulb, RotateCcw } from "lucide-react";
import type { AnalysisResponse } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/auth-context";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { SeasonCard } from "./season-card";
import { ProbabilityBars } from "./probability-bars";
import { AxisGauges } from "./axis-gauges";
import { PaletteSection } from "./palette-section";
import { PipelineStages } from "./pipeline-stages";

/**
 * 결과 화면 전체 조립.
 *
 * 순서에 의도가 있다: 판정(카드) → 근거(확률·3축) → 실용 정보(팔레트·팁)
 * → 투명성(파이프라인). 결과를 먼저, 파고들 사람에게는 근거를 —
 * 하이브리드 결정의 뒷부분이다 (docs/06-frontend.md).
 */

interface ResultViewProps {
  result: AnalysisResponse;
  onReset?: () => void;
}

export function ResultView({ result, onReset }: ResultViewProps) {
  const { status } = useAuth();

  return (
    <div className="space-y-5">
      {result.warnings.length > 0 && (
        <Alert>
          <AlertTriangle className="size-4" aria-hidden />
          <AlertTitle>측정 품질 참고</AlertTitle>
          <AlertDescription>
            <ul className="list-disc pl-4">
              {result.warnings.map((warning) => (
                <li key={warning}>{warning}</li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      <SeasonCard result={result} />

      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.25, duration: 0.5 }}
        className="grid gap-5 md:grid-cols-2"
      >
        <Card>
          <CardHeader>
            <CardTitle className="text-base">4계절 확률 분포</CardTitle>
          </CardHeader>
          <CardContent>
            <ProbabilityBars probabilities={result.probabilities} winner={result.season.code} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base">판정 근거 — 3축 측정값</CardTitle>
          </CardHeader>
          <CardContent>
            <AxisGauges axes={result.axes} />
          </CardContent>
        </Card>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.4, duration: 0.5 }}
        className="grid gap-5 md:grid-cols-[3fr_2fr]"
      >
        <Card>
          <CardHeader>
            <CardTitle className="text-base">컬러 팔레트</CardTitle>
          </CardHeader>
          <CardContent>
            <PaletteSection
              bestColors={result.season.bestColors}
              worstColors={result.season.worstColors}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Lightbulb className="size-4" aria-hidden /> 스타일링 팁
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-2 text-sm leading-relaxed">
              {result.season.stylingTips.map((tip) => (
                <li key={tip} className="flex gap-2">
                  <span className="text-muted-foreground">·</span>
                  {tip}
                </li>
              ))}
            </ul>
            <div className="mt-4 flex items-center gap-2 rounded-lg bg-muted/40 p-2.5 text-xs text-muted-foreground">
              <span
                className="size-5 shrink-0 rounded-full border"
                style={{ backgroundColor: result.features.medianRgbHex }}
                aria-hidden
              />
              측정된 대표 피부색 {result.features.medianRgbHex} · 피부 픽셀{" "}
              {result.features.pixelCount.toLocaleString()}개
            </div>
          </CardContent>
        </Card>
      </motion.div>

      {result.stages != null && (
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.55, duration: 0.5 }}
        >
          <PipelineStages stages={result.stages} preprocessing={result.preprocessing} />
        </motion.div>
      )}

      <div className="space-y-3 pt-1">
        {!result.saved && status !== "authenticated" && (
          <p className="text-center text-xs text-muted-foreground">
            이 결과는 저장되지 않았습니다.{" "}
            <Link href="/login" className="underline underline-offset-2 hover:text-foreground">
              로그인
            </Link>
            하고 분석하면 이력에 남아요. 원본 사진은 어느 경우에도 서버에 저장되지 않습니다.
          </p>
        )}
        {result.saved && (
          <p className="text-center text-xs text-muted-foreground">
            이력에 저장되었습니다. 원본 사진은 저장되지 않고 측정 수치만 남습니다.
          </p>
        )}
        <div className="flex justify-center gap-3">
          {onReset !== undefined && (
            <Button type="button" variant="outline" onClick={onReset}>
              <RotateCcw aria-hidden /> 다른 사진으로 분석
            </Button>
          )}
          {result.saved && (
            <Button render={<Link href="/history" />} variant="ghost">
              <History aria-hidden /> 내 이력 보기
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
