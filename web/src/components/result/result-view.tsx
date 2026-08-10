"use client";

import Link from "next/link";

import { StageStrip } from "@/components/pipeline/stage-strip";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import type { AnalysisResponse } from "@/lib/api/types";
import { SEASON_FALLBACK_LABELS, SEASON_THEMES } from "@/lib/season";
import { interpret, undertoneSentence } from "@/lib/verdict";

import { AxisGauge } from "./axis-gauge";
import { PaletteGrid } from "./palette-grid";
import { ProbabilityBars } from "./probability-bars";

/**
 * 결과 화면 — 위에서 아래로 "결론 → 근거 → 과정 → 활용" 순서.
 *
 * 결론(계절)을 먼저 주되, 스크롤 한 번이면 그 결론이 어떤 확률 분포와
 * 측정값에서 나왔는지 보이게 한다. 근거를 접어서 숨기지 않는 것이
 * 이 프로젝트의 태도다 — 블랙박스 탈출이 재구축의 목표였다.
 */
export function ResultView({
  result,
  onRestart,
}: {
  result: AnalysisResponse;
  onRestart: () => void;
}) {
  const theme = SEASON_THEMES[result.season.code];
  const verdict = interpret(result, (code) =>
    code === result.season.code ? result.season.labelKo : SEASON_FALLBACK_LABELS[code],
  );

  return (
    <div className="flex flex-col gap-10">
      {/* ── 결론 카드 ─────────────────────────────────────── */}
      <section
        className="rounded-3xl p-8 text-center sm:p-12"
        style={{ background: theme.gradient }}
      >
        <p className="text-5xl" aria-hidden>
          {result.season.emoji}
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">
          {verdict.headline}
        </h1>
        <p className="mt-2 text-neutral-700">
          {undertoneSentence(result.undertone, result.undertoneConfidence)}
        </p>

        <div className="mt-4 flex flex-wrap justify-center gap-2">
          {result.season.keywords.map((keyword) => (
            <Badge key={keyword} variant="secondary" className="bg-white/60 text-neutral-800">
              {keyword}
            </Badge>
          ))}
        </div>

        {verdict.band === "borderline" && verdict.runnerUp && (
          <p className="mx-auto mt-4 max-w-md rounded-lg bg-white/60 px-4 py-2 text-sm text-neutral-800">
            1위와 2위의 확률 차이가 작아 단정하기 어렵습니다. 아래 확률 분포와
            팔레트를 <strong>{SEASON_FALLBACK_LABELS[verdict.runnerUp]}</strong>과(와)
            함께 참고하세요.
          </p>
        )}

        <p className="mt-6 text-sm text-neutral-600">{result.season.description}</p>
      </section>

      {/* ── 저장 여부 — 사실을 그대로 알린다 ──────────────── */}
      {result.saved ? (
        <Alert>
          <AlertTitle>이력에 저장되었습니다</AlertTitle>
          <AlertDescription>
            원본 사진이 아니라 측정 수치만 저장됩니다.{" "}
            <Link className="underline" href="/history">
              내 이력 보기
            </Link>
          </AlertDescription>
        </Alert>
      ) : (
        <Alert>
          <AlertTitle>이 결과는 저장되지 않았습니다</AlertTitle>
          <AlertDescription>
            익명 분석은 어디에도 남지 않습니다. 이력을 모으고 싶다면{" "}
            <Link className="underline" href="/register">
              가입
            </Link>
            {" 후 다시 분석해 주세요 — 그래도 사진 원본은 저장하지 않습니다."}
          </AlertDescription>
        </Alert>
      )}

      {result.warnings.length > 0 && (
        <Alert variant="destructive">
          <AlertTitle>측정 조건 경고</AlertTitle>
          <AlertDescription>
            <ul className="list-disc pl-4">
              {result.warnings.map((warning) => (
                <li key={warning}>{warning}</li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      {/* ── 근거: 확률 분포 + 3축 ─────────────────────────── */}
      <section className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>4계절 확률 분포</CardTitle>
          </CardHeader>
          <CardContent>
            <ProbabilityBars probabilities={result.probabilities} top={result.season.code} />
            <p className="mt-3 text-xs text-muted-foreground">
              가장 높은 계절만이 아니라 분포 전체를 보여드립니다 — 경계에 있는
              결과인지 확실한 결과인지는 분포가 말해줍니다.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>판정 3축</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            {result.axes.map((axis) => (
              <AxisGauge key={axis.name} axis={axis} accent={theme.accent} />
            ))}
          </CardContent>
        </Card>
      </section>

      {/* ── 과정: 전처리 시각화 ───────────────────────────── */}
      {result.stages && <StageStrip stages={result.stages} />}

      {/* ── 측정 상세 — 표로 전부 공개 ────────────────────── */}
      <MeasurementDetail result={result} />

      {/* ── 활용: 팔레트 + 팁 ─────────────────────────────── */}
      <section className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>어울리는 색 · 피할 색</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            <PaletteGrid title="추천 팔레트" colors={result.season.bestColors} tone="best" />
            <Separator />
            <PaletteGrid title="기피 팔레트" colors={result.season.worstColors} tone="worst" />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>스타일링 팁</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-2 text-sm leading-relaxed">
              {result.season.stylingTips.map((tip) => (
                <li key={tip} className="flex gap-2">
                  <span aria-hidden>{result.season.emoji}</span>
                  <span>{tip}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </section>

      <div className="flex justify-center">
        <Button size="lg" onClick={onRestart}>
          다른 사진으로 다시 분석
        </Button>
      </div>
    </div>
  );
}

/**
 * 측정 수치 전부와 전처리 보고. "h°가 68.4라서 웜"까지 공개하는 자리다.
 * 보정량(white balance gains)을 밝히는 것도 의도 — 보정하고 침묵하지 않는다.
 */
function MeasurementDetail({ result }: { result: AnalysisResponse }) {
  const { features, preprocessing } = result;

  const rows: [string, string][] = [
    ["명도 L*", features.lightness.toFixed(2)],
    ["a* (적-녹)", features.aStar.toFixed(2)],
    ["b* (황-청)", features.bStar.toFixed(2)],
    ["채도 C*", features.chroma.toFixed(2)],
    ["색상각 h°", features.hueAngle.toFixed(2)],
    ["ITA°", `${features.ita.toFixed(2)} (${features.itaCategory})`],
    ["측정 픽셀 수", features.pixelCount.toLocaleString()],
    ["화이트밸런스", preprocessing.whiteBalanceMethod],
    ["보정 게인 (R/G/B)", preprocessing.gains.map((gain) => gain.toFixed(3)).join(" / ")],
    ["색편향 강도", preprocessing.castStrength.toFixed(4)],
    ["마스크 커버리지", `${(preprocessing.maskCoverageRatio * 100).toFixed(1)}%`],
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-3">
          측정 수치
          <span
            className="inline-block h-5 w-5 rounded-full border"
            style={{ backgroundColor: features.medianRgbHex }}
            title={`대표 피부색 ${features.medianRgbHex}`}
          />
          <span className="font-mono text-sm font-normal text-muted-foreground">
            {features.medianRgbHex}
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-2 gap-x-8 gap-y-2 text-sm sm:grid-cols-3">
          {rows.map(([label, value]) => (
            <div key={label} className="flex justify-between gap-2 border-b py-1.5">
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="font-mono">{value}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-3 text-xs text-muted-foreground">
          CIELab 색공간에서 피부 픽셀의 <strong>중앙값</strong>으로 계산합니다.
          보정 게인은 서버가 사진의 조명을 얼마나 교정했는지 그대로 공개하는 값입니다.
        </p>
      </CardContent>
    </Card>
  );
}
